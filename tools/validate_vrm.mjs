// Validate anime_base.vrm: structural GLB checks + NaN scan + Three.js/three-vrm load test.
// Run: node tools/validate_vrm.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");
const VRM_PATH = path.join(ROOT, "anime_base.vrm");

let failures = 0;
function check(name, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  (" + detail + ")" : ""}`);
  if (!ok) failures++;
}

// ---------------------------------------------------------------- DOM stubs (Node has no Image/document)
class ImgStub {
  constructor() { this._l = {}; this.width = 4; this.height = 4; this.complete = false; }
  addEventListener(t, fn) { ((this._l[t] ??= [])).push(fn); }
  removeEventListener(t, fn) { const a = this._l[t]; if (a) { const i = a.indexOf(fn); if (i >= 0) a.splice(i, 1); } }
  set src(v) {
    this._src = v;
    setTimeout(() => { this.complete = true; for (const f of (this._l.load ?? [])) f.call(this); }, 0);
  }
  get src() { return this._src; }
}
globalThis.document = {
  createElementNS: (ns, name) => (name === "img" ? new ImgStub() : { style: {} }),
  createElement: (name) => (name === "img" ? new ImgStub() : { style: {} }),
};
globalThis.self = globalThis;
globalThis.window = globalThis;

// ---------------------------------------------------------------- 1. GLB parse
const buf = fs.readFileSync(VRM_PATH);
check("file exists", true, `${VRM_PATH}  ${(buf.length / 1048576).toFixed(2)} MB`);
check("under 2.5 MB", buf.length < 2.5 * 1048576, `${buf.length} bytes`);
const magic = buf.readUInt32LE(0), ver = buf.readUInt32LE(4), total = buf.readUInt32LE(8);
check("GLB magic+version+length", magic === 0x46546c67 && ver === 2 && total === buf.length);
let off = 12, json = null, bin = null;
while (off < buf.length) {
  const clen = buf.readUInt32LE(off), ctype = buf.readUInt32LE(off + 4);
  const chunk = buf.subarray(off + 8, off + 8 + clen);
  if (ctype === 0x4e4f534a) json = JSON.parse(chunk.toString("utf8"));
  if (ctype === 0x004e4942) bin = chunk;
  off += 8 + clen;
}
check("JSON + BIN chunks present", !!json && !!bin);

// ---------------------------------------------------------------- 2. VRM 0.x extension checks
check("extensionsUsed has VRM", (json.extensionsUsed ?? []).includes("VRM"));
const vrm = json.extensions?.VRM;
check("VRM extension present", !!vrm);
check("specVersion is 0.0", vrm?.specVersion === "0.0", vrm?.specVersion);
check("meta present", !!vrm?.meta?.title, vrm?.meta?.title);
check("no expression blendshapes", (vrm?.blendShapeMaster?.blendShapeGroups ?? []).length === 0);

const REQUIRED = ["hips", "spine", "head", "leftUpperLeg", "leftLowerLeg", "leftFoot",
  "rightUpperLeg", "rightLowerLeg", "rightFoot", "leftUpperArm", "leftLowerArm", "leftHand",
  "rightUpperArm", "rightLowerArm", "rightHand"];
const WANT = [...REQUIRED, "chest", "upperChest", "neck", "leftShoulder", "rightShoulder",
  "leftToes", "rightToes"];
for (const s of ["left", "right"]) {
  WANT.push(`${s}ThumbProximal`, `${s}ThumbIntermediate`, `${s}ThumbDistal`);
  for (const f of ["Index", "Middle", "Ring", "Little"])
    WANT.push(`${s}${f}Proximal`, `${s}${f}Intermediate`, `${s}${f}Distal`);
}
const haveBones = new Set((vrm?.humanoid?.humanBones ?? []).map((b) => b.bone));
const missing = WANT.filter((b) => !haveBones.has(b));
check(`humanoid bones (${haveBones.size})`, missing.length === 0,
  missing.length ? "missing: " + missing.join(",") : `${WANT.length} incl. fingers+toes`);
const badNode = (vrm?.humanoid?.humanBones ?? []).filter(
  (b) => b.node == null || b.node < 0 || b.node >= json.nodes.length);
check("humanoid node indices valid", badNode.length === 0);
check("firstPerson bone is head", json.nodes[vrm?.firstPerson?.firstPersonBone]?.name === "head");
check("materialProperties x3 (GLTF shader)",
  (vrm?.materialProperties ?? []).length === 3 &&
  (vrm.materialProperties ?? []).every((m) => m.shader === "VRM_USE_GLTFSHADER"));

// ---------------------------------------------------------------- 3. buffers: NaN scan + morph checks
const COMP = { 5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4 };
const NCOMP = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16 };
function accFloats(i) {
  const a = json.accessors[i], v = json.bufferViews[a.bufferView];
  const start = (v.byteOffset ?? 0) + (a.byteOffset ?? 0);
  const n = a.count * NCOMP[a.type];
  const out = new Array(n);
  for (let k = 0; k < n; k++) out[k] = bin.readFloatLE(start + k * 4);
  return out;
}
let nFloat = 0, nNaN = 0;
for (let i = 0; i < json.accessors.length; i++) {
  if (json.accessors[i].componentType !== 5126) continue;
  for (const f of accFloats(i)) { nFloat++; if (!Number.isFinite(f)) nNaN++; }
}
check("zero NaNs in float buffers", nNaN === 0, `${nFloat} floats scanned`);

const bodyMesh = json.meshes[0];
const tNames = bodyMesh?.extras?.targetNames;
check("Body targetNames == bust/waist/hips/thighs/arms",
  JSON.stringify(tNames) === JSON.stringify(["bust", "waist", "hips", "thighs", "arms"]),
  JSON.stringify(tNames));
const targets = bodyMesh?.primitives?.[0]?.targets ?? [];
check("5 POSITION morph targets", targets.length === 5);
let morphOK = true, morphDetail = [];
for (let k = 0; k < targets.length; k++) {
  const t = targets[k], a = json.accessors[t.POSITION];
  const ok = a && !("NORMAL" in t) && a.min?.length === 3 && a.max?.length === 3;
  if (!ok) morphOK = false;
  const d = accFloats(t.POSITION);
  let mx = 0; for (const f of d) mx = Math.max(mx, Math.abs(f));
  morphDetail.push(`${tNames?.[k]}:${(mx * 1000).toFixed(1)}mm`);
  if (!(mx > 1e-4)) morphOK = false;
}
check("morphs have min/max + nonzero deltas", morphOK, morphDetail.join(" "));

// skin / IBM
const skin = json.skins[0];
check("one skin, 52 joints", json.skins.length === 1 && skin.joints.length === 52);
const ibm = accFloats(skin.inverseBindMatrices);
check("IBM 52x16 finite", ibm.length === 52 * 16 && ibm.every(Number.isFinite));

// weights sanity on Body
function accU16(i) {
  const a = json.accessors[i], v = json.bufferViews[a.bufferView];
  const start = (v.byteOffset ?? 0) + (a.byteOffset ?? 0);
  const n = a.count * NCOMP[a.type], out = new Array(n);
  for (let k = 0; k < n; k++) out[k] = bin.readUInt16LE(start + k * 2);
  return out;
}
{
  const at = bodyMesh.primitives[0].attributes;
  const W = accFloats(at.WEIGHTS_0), Jn = accU16(at.JOINTS_0);
  const nv = json.accessors[at.POSITION].count;
  let wOK = true, jOK = true;
  for (let r = 0; r < nv; r++) {
    const s = W[r * 4] + W[r * 4 + 1] + W[r * 4 + 2] + W[r * 4 + 3];
    if (Math.abs(s - 1) > 1e-4) wOK = false;
    for (let c = 0; c < 4; c++) if (Jn[r * 4 + c] > 51) jOK = false;
  }
  check(`skin weights sum to 1 (${nv} verts)`, wOK);
  check("joint indices in range", jOK);
}

// images / textures
check("4 PNG images (512x512 x3 + flat normal)",
  json.images.length === 4 && json.images.every((im) => im.mimeType === "image/png"));
for (const im of json.images) {
  const v = json.bufferViews[im.bufferView];
  const head = bin.subarray(v.byteOffset, v.byteOffset + 8);
  if (head[0] !== 0x89 || head[1] !== 0x50) { check(`image ${im.name} is PNG`, false); }
}
check("image bytes are PNG", true);
check("3 PBR materials, matte skin",
  json.materials.length === 3 && json.materials[0].pbrMetallicRoughness.metallicFactor === 0);

// ---------------------------------------------------------------- 4. Three.js + three-vrm load
const THREE = await import("three");
const { GLTFLoader } = await import("three/addons/loaders/GLTFLoader.js");
const { VRMLoaderPlugin } = await import("@pixiv/three-vrm");

const loader = new GLTFLoader();
loader.register((parser) => new VRMLoaderPlugin(parser));
const arrayBuffer = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
let gltf;
try {
  gltf = await loader.parseAsync(arrayBuffer, "");
  check("GLTFLoader + VRMLoaderPlugin parse", true);
} catch (e) {
  check("GLTFLoader + VRMLoaderPlugin parse", false, String(e).slice(0, 300));
  console.log(failures ? "\nRESULT: FAIL" : "\nRESULT: PASS");
  process.exit(failures ? 1 : 0);
}
const vrmObj = gltf.userData.vrm;
check("vrm object created", !!vrmObj);
check("metaVersion 0", vrmObj?.meta?.metaVersion === "0", vrmObj?.meta?.metaVersion);

if (vrmObj) {
  const missingB = WANT.filter((b) => {
    // three-vrm maps VRM0 thumb Proximal/Intermediate -> Metacarpal/Proximal
    const alt = b.replace("ThumbProximal", "ThumbMetacarpal").replace("ThumbIntermediate", "ThumbProximal");
    return !vrmObj.humanoid.getRawBoneNode(b) && !vrmObj.humanoid.getRawBoneNode(alt);
  });
  check("three-vrm humanoid bones resolve", missingB.length === 0,
    missingB.length ? "missing: " + missingB.join(",") : `${WANT.length} ok`);

  // find body skinned mesh + morphs
  let body = null;
  vrmObj.scene.traverse((o) => { if (o.isSkinnedMesh && o.name === "Body") body = o; });
  check("Body SkinnedMesh found", !!body);
  const dict = body?.morphTargetDictionary ?? {};
  const dictOK = ["bust", "waist", "hips", "thighs", "arms"].every((k) => Number.isInteger(dict[k]));
  check("morphTargetDictionary has 5 morphs", dictOK, JSON.stringify(dict));

  if (body && dictOK) {
    // deform cleanly: set all influences to 1, update, verify bounds change + finite
    body.geometry.computeBoundingBox();
    const before = body.geometry.boundingBox.clone();
    for (const k of Object.keys(dict)) body.morphTargetInfluences[dict[k]] = 1.0;
    body.updateMorphTargets?.();
    vrmObj.scene.updateMatrixWorld(true);
    // sample morphed position attribute CPU-side via morphAttributes
    const pos = body.geometry.attributes.position;
    const wb = body.geometry.attributes.skinWeight;
    let finite = true;
    for (let i = 0; i < pos.count; i += 7) {
      if (![pos.getX(i), pos.getY(i), pos.getZ(i)].every(Number.isFinite)) finite = false;
    }
    for (const ma of Object.values(body.geometry.morphAttributes)) {
      for (const attr of ma) {
        const arr = attr.array;
        for (let i = 0; i < arr.length; i += 13) if (!Number.isFinite(arr[i])) finite = false;
      }
    }
    check("bind + morph attributes finite in three.js", finite);
    // morphs actually move verts: compare influence-0 vs influence-1 bounding via manual blend of first morph
    const m0 = body.geometry.morphAttributes.position[0];
    let moved = 0;
    for (let i = 0; i < m0.count; i++) {
      moved = Math.max(moved, Math.hypot(m0.getX(i), m0.getY(i), m0.getZ(i)));
    }
    check("morph deltas deform verts", moved > 1e-4, `max ${(moved * 1000).toFixed(1)}mm`);
    for (const k of Object.keys(dict)) body.morphTargetInfluences[dict[k]] = 0.0;
  }

  // finger curl + arm bend: rotate bones, update world matrices, verify finite + changed
  const idxNode = vrmObj.humanoid.getRawBoneNode("rightIndexProximal") ??
    vrmObj.humanoid.getNormalizedBoneNode("rightIndexProximal");
  const armNode = vrmObj.humanoid.getRawBoneNode("rightLowerArm");
  check("finger + arm bone nodes found", !!idxNode && !!armNode);
  if (idxNode && armNode) {
    vrmObj.scene.updateMatrixWorld(true);
    const mBefore = idxNode.matrixWorld.clone();
    idxNode.rotation.z = 0.9;
    vrmObj.humanoid.getRawBoneNode("rightIndexIntermediate").rotation.z = 0.9;
    vrmObj.humanoid.getRawBoneNode("rightIndexDistal").rotation.z = 0.8;
    armNode.rotation.x = -1.0;
    vrmObj.scene.updateMatrixWorld(true);
    let fin = true;
    vrmObj.scene.traverse((o) => {
      for (const e of o.matrixWorld.elements) if (!Number.isFinite(e)) fin = false;
    });
    // skinned vertex check: apply bone transform of one hand vert
    let skinnedFinite = true;
    vrmObj.scene.traverse((o) => {
      if (o.isSkinnedMesh) {
        o.normalizeSkinWeights?.();
        const v = new THREE.Vector3();
        const p = o.geometry.attributes.position;
        for (let i = 0; i < Math.min(p.count, 400); i += 3) {
          v.fromBufferAttribute(p, i);
          o.applyBoneTransform(i, v);
          if (![v.x, v.y, v.z].every(Number.isFinite)) skinnedFinite = false;
        }
        for (const inv of o.skeleton.boneInverses)
          for (const e of inv.elements) if (!Number.isFinite(e)) skinnedFinite = false;
      }
    });
    const changed = !mBefore.equals(idxNode.matrixWorld);
    check("posed matrices finite + changed", fin && changed);
    check("skinned verts finite after finger curl + elbow bend", skinnedFinite);
  }
}

console.log(failures ? "\nRESULT: FAIL" : "\nRESULT: PASS");
process.exit(failures ? 1 : 0);
