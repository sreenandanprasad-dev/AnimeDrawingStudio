#!/usr/bin/env python3
"""Software-rasterize anime_base.vrm previews (front / 3-4 / posed).

Parses the GLB directly (no WebGL needed), applies skinning + morphs,
renders with numpy + PIL. Outputs: previews/front.png, three_quarter.png, pose.png
"""
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
VRM_PATH = os.path.join(ROOT, "anime_base.vrm")
OUT_DIR = os.path.join(ROOT, "previews")

COMP = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2),
        5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


class GLB:
    def __init__(self, path):
        with open(path, "rb") as f:
            data = f.read()
        magic, ver, total = struct.unpack("<III", data[:12])
        assert magic == 0x46546C67 and ver == 2
        off = 12
        self.json = None
        self.bin = None
        while off < len(data):
            clen, ctype = struct.unpack("<II", data[off:off + 8])
            chunk = data[off + 8:off + 8 + clen]
            if ctype == 0x4E4F534A:
                self.json = json.loads(chunk.decode("utf-8"))
            elif ctype == 0x004E4942:
                self.bin = chunk
            off += 8 + clen
        assert self.json and self.bin is not None

    def accessor(self, i):
        a = self.json["accessors"][i]
        v = self.json["bufferViews"][a["bufferView"]]
        fmt, sz = COMP[a["componentType"]]
        n = NCOMP[a["type"]]
        start = v.get("byteOffset", 0) + a.get("byteOffset", 0)
        count = a["count"]
        arr = np.frombuffer(self.bin, dtype=np.dtype(fmt).newbyteorder("<"),
                            count=count * n, offset=start)
        arr = np.array(arr, dtype=np.float32).reshape(count, n)
        if a["type"] == "MAT4":
            arr = arr.reshape(count, 4, 4).transpose(0, 2, 1)  # to row-major
        return arr


def load_model():
    g = GLB(VRM_PATH)
    J = g.json
    meshes = []
    for mi, m in enumerate(J["meshes"]):
        prim = m["primitives"][0]
        at = prim["attributes"]
        P = g.accessor(at["POSITION"])
        N = g.accessor(at["NORMAL"])
        UV = g.accessor(at["TEXCOORD_0"])
        JJ = g.accessor(at["JOINTS_0"]).astype(np.int64)
        W = g.accessor(at["WEIGHTS_0"])
        I = g.accessor(prim["indices"]).astype(np.int64).ravel()
        tgts = []
        for t in prim.get("targets", []):
            tgts.append(g.accessor(t["POSITION"]))
        mat = J["materials"][prim["material"]]
        tex_idx = mat["pbrMetallicRoughness"]["baseColorTexture"]["index"]
        img_idx = J["textures"][tex_idx]["source"]
        iv = J["images"][img_idx]["bufferView"]
        v = J["bufferViews"][iv]
        ibytes = g.bin[v.get("byteOffset", 0):v.get("byteOffset", 0) + v["byteLength"]]
        import io as _io
        tex = np.asarray(Image.open(_io.BytesIO(ibytes)).convert("RGB"), dtype=np.float32) / 255.0
        meshes.append(dict(P=P, N=N, UV=UV, J=JJ, W=W, I=I, targets=tgts,
                           tex=tex, name=m["name"]))
    skin = J["skins"][0]
    IBM = g.accessor(skin["inverseBindMatrices"])  # row-major already
    joints = skin["joints"]
    nodes = J["nodes"]
    # local translations
    NJ = len(joints)
    local = []
    parent = []
    node_index_of = {jn: k for k, jn in enumerate(joints)}
    # build parent map from node children
    par_of_node = {}
    for ni, nd in enumerate(nodes):
        for c in nd.get("children", []):
            par_of_node[c] = ni
    for k, jn in enumerate(joints):
        nd = nodes[jn]
        local.append(np.array(nd.get("translation", [0, 0, 0]), dtype=np.float64))
        pn = par_of_node.get(jn, -1)
        parent.append(node_index_of.get(pn, -1))
    return meshes, np.array(IBM, dtype=np.float64), np.array(local), np.array(parent), J


def euler_xyz(dx, dy, dz):
    ax, ay, az = np.radians([dx, dy, dz])
    cx, sx, cy, sy, cz, sz = np.cos(ax), np.sin(ax), np.cos(ay), np.sin(ay), np.cos(az), np.sin(az)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rx @ Ry @ Rz


def deform(meshes, IBM, local, parent, J, pose, morph_w):
    NJ = len(local)
    world = [None] * NJ
    jname = [J["nodes"][jn].get("name", "") for jn in J["skins"][0]["joints"]]
    for k in range(NJ):
        R = euler_xyz(*pose.get(jname[k], (0, 0, 0)))
        M = np.eye(4)
        M[:3, :3] = R
        M[:3, 3] = local[k]
        world[k] = M if parent[k] < 0 else world[parent[k]] @ M
    skinM = [world[k] @ IBM[k] for k in range(NJ)]
    skinM = np.array(skinM)  # (NJ,4,4)
    R33 = skinM[:, :3, :3]
    out = []
    for m in meshes:
        P = m["P"].astype(np.float64).copy()
        for w, t in zip(morph_w, m["targets"]):
            if w:
                P += float(w) * t.astype(np.float64)
        homo = np.concatenate([P, np.ones((len(P), 1))], axis=1)  # (V,4)
        JJ, W = m["J"], m["W"].astype(np.float64)
        Pd = np.zeros_like(P)
        Nd = np.zeros_like(m["N"].astype(np.float64))
        for c in range(4):
            w = W[:, c]
            sel = w > 1e-9
            if not np.any(sel):
                continue
            M = skinM[JJ[sel, c]]            # (K,4,4)
            Pd[sel] += w[sel, None] * (M @ homo[sel, :, None])[:, :3, 0]
            Nd[sel] += w[sel, None] * (M[:, :3, :3] @ m["N"][sel].astype(np.float64)[:, :, None])[:, :, 0]
        Nd /= np.linalg.norm(Nd, axis=1, keepdims=True) + 1e-12
        out.append((Pd, Nd, m["UV"], m["I"], m["tex"], m["name"]))
    return out


def render(geoms, yaw_deg=0, pitch_deg=0, W=720, H=960, ss=1.5, title="",
           center=(0, 0.815, 0), win_h=1.78):
    W2, H2 = int(W * ss), int(H * ss)
    # background gradient
    bg_top = np.array([242, 243, 247], np.float32)
    bg_bot = np.array([214, 218, 228], np.float32)
    yy = np.linspace(0, 1, H2)[:, None, None]
    fb = bg_top[None, None, :] * (1 - yy) + bg_bot[None, None, :] * yy
    fb = np.repeat(fb, W2, axis=1)
    zb = np.full((H2, W2), -1e9, np.float64)

    yaw, pitch = np.radians(yaw_deg), np.radians(pitch_deg)
    Ry = np.array([[np.cos(yaw), 0, np.sin(yaw)], [0, 1, 0], [-np.sin(yaw), 0, np.cos(yaw)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(pitch), -np.sin(pitch)], [0, np.sin(pitch), np.cos(pitch)]])
    Rv = Rx @ Ry
    s = H2 * 0.90 / win_h
    Pc = Rv @ np.asarray(center, dtype=np.float64)
    cx, cy = W2 / 2 - Pc[0] * s, H2 / 2 + Pc[1] * s

    L1 = np.array([0.5, 0.8, 0.9]); L1 /= np.linalg.norm(L1)
    L2 = np.array([-0.6, 0.3, 0.4]); L2 /= np.linalg.norm(L2)

    for (P, N, UV, I, tex, name) in geoms:
        Pv = P @ Rv.T
        Nv = N @ Rv.T
        sx = Pv[:, 0] * s + cx
        sy = -Pv[:, 1] * s + cy
        depth = Pv[:, 2]
        TH, TW = tex.shape[:2]
        tris = I.reshape(-1, 3)
        for t in tris:
            xs = sx[t]; ys = sy[t]
            x0, x1 = int(max(0, xs.min())), int(min(W2 - 1, xs.max())) + 1
            y0, y1 = int(max(0, ys.min())), int(min(H2 - 1, ys.max())) + 1
            if x1 <= x0 or y1 <= y0:
                continue
            ax, ay, bx, by,ccx,ccy = xs[0],ys[0],xs[1],ys[1],xs[2],ys[2]
            area = (bx - ax) * (ccy - ay) - (ccx - ax) * (by - ay)
            if abs(area) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            e0 = (bx - ax) * (gy - ay) - (by - ay) * (gx - ax)
            e1 = (ccx - bx) * (gy - by) - (ccy - by) * (gx - bx)
            e2 = (ax - ccx) * (gy - ccy) - (ay - ccy) * (gx - ccx)
            if area > 0:
                inside = (e0 >= 0) & (e1 >= 0) & (e2 >= 0)
            else:
                inside = (e0 <= 0) & (e1 <= 0) & (e2 <= 0)
            if not np.any(inside):
                continue
            # edge-function <-> vertex correspondence: e0 is 1 at C, e1 at A, e2 at B
            w0 = e1 / area; w1 = e2 / area; w2 = e0 / area  # weights for t[0],t[1],t[2]
            zz = w0 * depth[t[0]] + w1 * depth[t[1]] + w2 * depth[t[2]]
            upd = inside & (zz > zb[y0:y1, x0:x1])
            if not np.any(upd):
                continue
            uu = np.clip(w0 * UV[t[0], 0] + w1 * UV[t[1], 0] + w2 * UV[t[2], 0], 0, 1)
            vv = np.clip(w0 * UV[t[0], 1] + w1 * UV[t[1], 1] + w2 * UV[t[2], 1], 0, 1)
            nx = w0 * Nv[t[0], 0] + w1 * Nv[t[1], 0] + w2 * Nv[t[2], 0]
            ny = w0 * Nv[t[0], 1] + w1 * Nv[t[1], 1] + w2 * Nv[t[2], 1]
            nz = w0 * Nv[t[0], 2] + w1 * Nv[t[1], 2] + w2 * Nv[t[2], 2]
            nl = np.sqrt(nx * nx + ny * ny + nz * nz) + 1e-12
            nx, ny, nz = nx / nl, ny / nl, nz / nl
            dif = np.clip(nx * L1[0] + ny * L1[1] + nz * L1[2], 0, 1) * 0.75 + \
                np.clip(nx * L2[0] + ny * L2[1] + nz * L2[2], 0, 1) * 0.30 + 0.42
            # double-sided lighting
            dif = np.where((nx * 0 + nz) < 0, np.clip(-(nx * L1[0] + ny * L1[1] + nz * L1[2]), 0, 1) * 0.5 + 0.35, dif)
            tx = np.clip((uu * TW).astype(np.int64), 0, TW - 1)
            ty = np.clip((vv * TH).astype(np.int64), 0, TH - 1)
            alb = tex[ty, tx]  # (h,w,3)
            col = np.clip(alb * dif[:, :, None], 0, 1) * 255.0
            region = fb[y0:y1, x0:x1]
            region[upd] = col[upd]
            zr = zb[y0:y1, x0:x1]
            zr[upd] = zz[upd]

    img = Image.fromarray(fb.astype(np.uint8)).resize((W, H), Image.LANCZOS)
    if title:
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, W, 30], fill=(20, 22, 30))
        d.text((12, 8), title, fill=(235, 238, 245))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    meshes, IBM, local, parent, J = load_model()
    print(f"loaded {len(meshes)} meshes, {len(local)} joints")

    g_bind = deform(meshes, IBM, local, parent, J, {}, [0] * 5)
    front = render(g_bind, yaw_deg=0, title="anime_base.vrm - front (bind pose)")
    front.save(os.path.join(OUT_DIR, "front.png"))
    print("saved front.png")

    tq = render(g_bind, yaw_deg=32, pitch_deg=-6, title="anime_base.vrm - three-quarter (bind pose)")
    tq.save(os.path.join(OUT_DIR, "three_quarter.png"))
    print("saved three_quarter.png")

    pose = {
        "rightUpperArm": (-55, 0, 0), "rightLowerArm": (-65, 0, 0),
        "rightIndexProximal": (0, 0, 50), "rightIndexIntermediate": (0, 0, 50),
        "rightIndexDistal": (0, 0, 40), "rightMiddleProximal": (0, 0, 55),
        "rightMiddleIntermediate": (0, 0, 55), "rightMiddleDistal": (0, 0, 45),
        "rightRingProximal": (0, 0, 50), "rightRingIntermediate": (0, 0, 50),
        "rightRingDistal": (0, 0, 40), "rightLittleProximal": (0, 0, 45),
        "rightLittleIntermediate": (0, 0, 45), "rightLittleDistal": (0, 0, 35),
        "rightThumbProximal": (-20, 0, 25), "rightThumbIntermediate": (-15, 0, 20),
        "rightThumbDistal": (-10, 0, 15),
        "leftUpperArm": (-8, 0, 14), "leftLowerArm": (-18, 0, 0),
        "leftIndexProximal": (0, 0, -15), "leftMiddleProximal": (0, 0, -15),
        "head": (0, -12, 8), "chest": (0, 5, 0),
    }
    g_pose = deform(meshes, IBM, local, parent, J, pose, [0.6, 0.0, 0.0, 0.0, 0.0])
    pimg = render(g_pose, yaw_deg=32, pitch_deg=-6,
                  title="anime_base.vrm - pose demo (arm bend + finger curl + bust 0.6)")
    pimg.save(os.path.join(OUT_DIR, "pose.png"))
    print("saved pose.png")

    # ---- close-ups ----
    face = render(g_bind, yaw_deg=0, title="close-up: face",
                  center=(0, 1.485, 0.06), win_h=0.36)
    face.save(os.path.join(OUT_DIR, "closeup_face.png"))
    print("saved closeup_face.png")

    fist = {}
    for fn in ("Index", "Middle", "Ring", "Little"):
        fist[f"right{fn}Proximal"] = (0, 0, 70)
        fist[f"right{fn}Intermediate"] = (0, 0, 75)
        fist[f"right{fn}Distal"] = (0, 0, 60)
    fist["rightThumbProximal"] = (-25, 0, 30)
    fist["rightThumbIntermediate"] = (-20, 0, 25)
    fist["rightThumbDistal"] = (-15, 0, 20)
    g_fist = deform(meshes, IBM, local, parent, J, fist, [0] * 5)
    hand = render(g_fist, yaw_deg=-55, pitch_deg=-10, title="close-up: right hand full curl",
                  center=(-0.29, 0.78, 0.02), win_h=0.30)
    hand.save(os.path.join(OUT_DIR, "closeup_hand.png"))
    print("saved closeup_hand.png")

    foot = render(g_bind, yaw_deg=38, pitch_deg=-8, title="close-up: left foot",
                  center=(0.11, 0.07, 0.06), win_h=0.32)
    foot.save(os.path.join(OUT_DIR, "closeup_foot.png"))
    print("saved closeup_foot.png")


if __name__ == "__main__":
    main()
