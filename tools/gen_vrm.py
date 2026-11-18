#!/usr/bin/env python3
"""Generate anime_base.vrm — a mobile-optimized VRM 0.x anime drawing-reference base.

Outputs: anime_base.vrm  (repo root, must stay under 2.5 MB)

Body: ~6.8 heads tall VRoid-style figure, pale-peach matte skin #F9E6D8,
short dark-chestnut scalp hair, anime face (painted + separate eye meshes),
full VRM 0.x humanoid rig incl. 3-segment fingers, 5 POSITION morph targets
(bust/waist/hips/thighs/arms), no expression blendshapes.
"""
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "anime_base.vrm")

SKIN = (249, 230, 216)          # #F9E6D8 pale peach
SKIN_SHADE = (214, 178, 154)    # contour shading tone
SKIN_DEEP = (190, 148, 128)
HAIR_COL = (59, 35, 20)         # dark chestnut brown
HAIR_LITE = (106, 70, 42)
INK = (42, 26, 18)              # eyelash / outline ink
BROW_COL = (74, 44, 26)
LIP_COL = (166, 106, 90)
IRIS_COL = (122, 63, 18)        # warm brown iris
IRIS_DK = (74, 36, 8)
IRIS_LT = (176, 106, 32)

# ---------------------------------------------------------------- joints ---
# Character faces +Z. Character-left is +X.
joint_defs = []  # (name, parent_name, world_xyz)


def J(name, parent, xyz):
    joint_defs.append((name, parent, xyz))


J("hips", None, (0, 0.98, 0))
J("spine", "hips", (0, 1.08, 0))
J("chest", "spine", (0, 1.18, 0))
J("upperChest", "chest", (0, 1.27, 0))
J("neck", "upperChest", (0, 1.345, 0))
J("head", "neck", (0, 1.45, 0.005))
for _side, _sx in (("left", 1.0), ("right", -1.0)):
    J(f"{_side}Shoulder", "upperChest", (_sx * 0.075, 1.315, 0))
    J(f"{_side}UpperArm", f"{_side}Shoulder", (_sx * 0.205, 1.295, 0))
    J(f"{_side}LowerArm", f"{_side}UpperArm", (_sx * 0.258, 1.075, 0.005))
    J(f"{_side}Hand", f"{_side}LowerArm", (_sx * 0.287, 0.845, 0.008))
for _side, _sx in (("left", 1.0), ("right", -1.0)):
    J(f"{_side}UpperLeg", "hips", (_sx * 0.095, 0.96, 0))
    J(f"{_side}LowerLeg", f"{_side}UpperLeg", (_sx * 0.105, 0.53, 0.008))
    J(f"{_side}Foot", f"{_side}LowerLeg", (_sx * 0.11, 0.115, 0.01))
    J(f"{_side}Toes", f"{_side}Foot", (_sx * 0.11, 0.05, 0.135))
for _side, _sx in (("left", 1.0), ("right", -1.0)):
    # VRM 0.x thumb naming: Proximal / Intermediate / Distal
    J(f"{_side}ThumbProximal", f"{_side}Hand", (_sx * 0.290, 0.807, 0.030))
    J(f"{_side}ThumbIntermediate", f"{_side}ThumbProximal", (_sx * 0.293, 0.787, 0.047))
    J(f"{_side}ThumbDistal", f"{_side}ThumbIntermediate", (_sx * 0.295, 0.768, 0.062))
    _fx = _sx * 0.288
    for _fn, _fz, _fl in (("Index", 0.030, 1.0), ("Middle", 0.010, 1.06),
                          ("Ring", -0.010, 1.0), ("Little", -0.029, 0.82)):
        _y0 = 0.760
        J(f"{_side}{_fn}Proximal", f"{_side}Hand", (_fx, _y0, _fz))
        J(f"{_side}{_fn}Intermediate", f"{_side}{_fn}Proximal",
          (_fx - _sx * 0.002 * _fl, _y0 - 0.026 * _fl, _fz))
        J(f"{_side}{_fn}Distal", f"{_side}{_fn}Intermediate",
          (_fx - _sx * 0.005 * _fl, _y0 - 0.050 * _fl, _fz))

NJ = len(joint_defs)
assert NJ == 52, f"expected 52 joints, got {NJ}"
JIDX = {name: i for i, (name, _, _) in enumerate(joint_defs)}
JWORLD = np.array([w for _, _, w in joint_defs], dtype=np.float64)
JPARENT = [JIDX[p] if p else -1 for _, p, _ in joint_defs]


def jw4(pairs):
    """Normalize [(joint, weight)...] into fixed (4,) joints + weights."""
    tot = sum(w for _, w in pairs)
    js = [0, 0, 0, 0]
    ws = [0.0, 0.0, 0.0, 0.0]
    for k, (j, w) in enumerate(pairs[:4]):
        js[k] = j
        ws[k] = w / tot if tot > 0 else 0.0
    return js, ws


def sstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a))) if b != a else 1.0
    return t * t * (3 - 2 * t)


# --------------------------------------------------------------- textures ---
def tex_body():
    W = H = 512
    img = Image.new("RGB", (W, H), SKIN)
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)

    def px(u, v):
        return (u * W, v * H)

    # --- subtle anatomical contour shading (torso region u in 0.02..0.60) ---
    # clavicle L/R
    d.line([px(0.225, 0.898), px(0.285, 0.912)], fill=SKIN_SHADE + (30,), width=6)
    d.line([px(0.395, 0.898), px(0.335, 0.912)], fill=SKIN_SHADE + (30,), width=6)
    # under-bust soft band
    d.ellipse([px(0.175, 0.615), px(0.445, 0.685)], fill=SKIN_SHADE + (16,))
    # ribcage sides
    d.line([px(0.095, 0.60), px(0.105, 0.74)], fill=SKIN_SHADE + (22,), width=9)
    d.line([px(0.525, 0.60), px(0.515, 0.74)], fill=SKIN_SHADE + (22,), width=9)
    # waist side shading
    d.line([px(0.070, 0.44), px(0.085, 0.56)], fill=SKIN_SHADE + (20,), width=10)
    d.line([px(0.550, 0.44), px(0.535, 0.56)], fill=SKIN_SHADE + (20,), width=10)
    # hip creases
    d.arc([px(0.215, 0.105), px(0.300, 0.175)], start=200, end=340, fill=SKIN_DEEP + (28,), width=3)
    d.arc([px(0.320, 0.105), px(0.405, 0.175)], start=200, end=340, fill=SKIN_DEEP + (28,), width=3)
    # navel
    d.ellipse([px(0.303, 0.400), px(0.317, 0.414)], fill=SKIN_DEEP + (55,))
    ov = ov.filter(ImageFilter.GaussianBlur(5))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")

    # --- face quad: u,v in 0.66..0.96 -> px 338..492 ---
    f = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    df = ImageDraw.Draw(f)
    exL, exR, ey = 371, 459, 394  # eye centres (px)
    # upper eyelashes (thin dark arcs above the eye meshes)
    for cx in (exL, exR):
        df.arc([cx - 24, ey - 36, cx + 24, ey + 4], start=200, end=340, fill=INK + (235,), width=3)
        # lower lid hint
        df.arc([cx - 18, ey - 6, cx + 18, ey + 30], start=20, end=160, fill=SKIN_DEEP + (90,), width=2)
    # soft neutral eyebrows
    df.line([(350, 360), (370, 355), (390, 357)], fill=BROW_COL + (220,), width=4, joint="curve")
    df.line([(440, 357), (460, 355), (480, 360)], fill=BROW_COL + (220,), width=4, joint="curve")
    # tiny anime nose
    df.line([(415, 428), (414, 436)], fill=SKIN_DEEP + (150,), width=2)
    df.ellipse([412, 437, 416, 441], fill=SKIN_DEEP + (170,))
    # neutral closed mouth + faint lower lip
    df.line([(403, 465), (427, 465)], fill=LIP_COL + (200,), width=2)
    df.ellipse([405, 469, 425, 476], fill=(239, 185, 168, 90))
    # faint blush
    for cx in (352, 478):
        df.ellipse([cx - 12, 408, cx + 12, 422], fill=(240, 184, 168, 42))
    f = f.filter(ImageFilter.GaussianBlur(0.6))
    img = Image.alpha_composite(img.convert("RGBA"), f).convert("RGB")
    return img


def tex_hair():
    W = H = 512
    img = Image.new("RGB", (W, H), HAIR_COL)
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    rng = np.random.RandomState(7)
    for _ in range(46):
        x = float(rng.uniform(0, W))
        wdt = int(rng.uniform(3, 7))
        tone = HAIR_LITE if rng.uniform() > 0.35 else (30, 18, 10)
        d.line([(x, -10), (x + rng.uniform(-24, 24), H + 10)], fill=tone + (52,), width=wdt)
    # crown highlight
    d.ellipse([116, 30, 396, 220], fill=(107, 74, 46, 70))
    ov = ov.filter(ImageFilter.GaussianBlur(3))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    return img


def tex_eyes():
    W = H = 512
    img = Image.new("RGB", (W, H), SKIN)
    for cx in (128, 384):  # right eye | left eye
        eye = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        de = ImageDraw.Draw(eye)
        # sclera almond (fills nearly the whole quad so edges blend into skin)
        de.ellipse([cx - 106, 56, cx + 106, 456], fill=(255, 255, 255, 255))
        # iris (large, warm brown) with vertical gradient feel
        de.ellipse([cx - 68, 140, cx + 68, 404], fill=IRIS_COL + (255,))
        de.ellipse([cx - 68, 140, cx + 68, 262], fill=IRIS_DK + (255,))   # dark upper
        de.ellipse([cx - 52, 300, cx + 52, 396], fill=IRIS_LT + (255,))   # light lower
        # pupil
        de.ellipse([cx - 26, 220, cx + 26, 330], fill=(26, 13, 5, 255))
        # crisp white highlights
        de.ellipse([cx - 44, 182, cx + 0, 226], fill=(255, 255, 255, 255))
        de.ellipse([cx + 12, 322, cx + 32, 342], fill=(255, 255, 255, 255))
        de.ellipse([cx + 20, 176, cx + 32, 188], fill=(255, 255, 255, 255))
        # upper lash line (thick, slight wing outward)
        wing = -1 if cx < W / 2 else 1
        de.arc([cx - 78, 96, cx + 78, 300], start=190, end=350, fill=INK + (255,), width=11)
        de.line([(cx + wing * 66, 168), (cx + wing * 88, 158)], fill=INK + (255,), width=9)
        # lower lid
        de.arc([cx - 66, 250, cx + 66, 430], start=15, end=165, fill=(138, 90, 72, 255), width=3)
        eye = eye.filter(ImageFilter.GaussianBlur(0.5))
        img = Image.alpha_composite(img.convert("RGBA"), eye).convert("RGB")
    return img


def tex_normal_flat():
    return Image.new("RGB", (16, 16), (128, 128, 255))


def png_bytes(img):
    import io as _io
    b = _io.BytesIO()
    img.save(b, format="PNG", optimize=True)
    return b.getvalue()


# --------------------------------------------------------------- geometry ---
class Geo:
    def __init__(self):
        self.P = []    # positions (x,y,z)
        self.UV = []   # (u,v)
        self.J = []    # [j0..j3]
        self.W = []    # [w0..w3]
        self.I = []    # indices
        self.TAG = []  # part tag per vert (for morphs)

    def vert(self, p, uv, j, w, tag=""):
        self.P.append((float(p[0]), float(p[1]), float(p[2])))
        self.UV.append((float(uv[0]), float(uv[1])))
        self.J.append([int(x) for x in j])
        self.W.append([float(x) for x in w])
        self.TAG.append(tag)
        return len(self.P) - 1

    def tri(self, a, b, c):
        self.I.extend([a, b, c])

    def flip_range(self, start, end):
        for k in range(start, end, 3):
            self.I[k + 1], self.I[k + 2] = self.I[k + 2], self.I[k + 1]

    def fix_winding(self, start, end, outward_fn):
        """Flip [start,end) triangles if their normals oppose outward_fn(centroid)."""
        P = np.asarray(self.P, dtype=np.float64)
        score = 0.0
        n = 0
        for k in range(start, end, 3 * 7):
            a, b, c = self.I[k], self.I[k + 1], self.I[k + 2]
            e1 = P[b] - P[a]
            e2 = P[c] - P[a]
            fn = np.cross(e1, e2)
            cen = (P[a] + P[b] + P[c]) / 3.0
            o = outward_fn(cen)
            if np.linalg.norm(fn) > 1e-12 and np.linalg.norm(o) > 1e-12:
                score += float(np.dot(fn / np.linalg.norm(fn), o / np.linalg.norm(o)))
                n += 1
        if n and score < 0:
            self.flip_range(start, end)
            return True
        return False

    def cap_fan(self, ring, center_pos, uv, j, w, tag, desired_n):
        c = self.vert(center_pos, uv, j, w, tag)
        s = len(self.I)
        for k in range(len(ring)):
            self.tri(c, ring[k], ring[(k + 1) % len(ring)])
        P = np.asarray(self.P, dtype=np.float64)
        a, b, d = self.I[s], self.I[s + 1], self.I[s + 2]
        fn = np.cross(P[b] - P[a], P[d] - P[a])
        dn = np.asarray(desired_n, dtype=np.float64)
        if float(np.dot(fn, dn)) < 0:
            self.flip_range(s, len(self.I))

    def compute_normals(self):
        P = np.asarray(self.P, dtype=np.float64)
        N = np.zeros_like(P)
        I = np.asarray(self.I, dtype=np.int64).reshape(-1, 3)
        e1 = P[I[:, 1]] - P[I[:, 0]]
        e2 = P[I[:, 2]] - P[I[:, 0]]
        fn = np.cross(e1, e2)
        for k in range(3):
            np.add.at(N, I[:, k], fn)
        L = np.linalg.norm(N, axis=1)
        bad = L < 1e-12
        L[bad] = 1.0
        N = N / L[:, None]
        N[bad] = (0.0, 1.0, 0.0)
        # keep float32-clean
        N = np.nan_to_num(N, nan=0.0, posinf=0.0, neginf=0.0)
        L2 = np.linalg.norm(N, axis=1)
        L2[L2 < 1e-9] = 1.0
        return (N / L2[:, None]).astype(np.float32)


def resample_polyline(points, radii, n):
    pts = np.asarray(points, dtype=np.float64)
    seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    cum = np.concatenate([[0.0], np.cumsum(seg)])
    total = cum[-1]
    s_vals = np.linspace(0, total, n)
    out_p = np.zeros((n, 3))
    out_r = np.zeros((n, 2))
    R = np.asarray(radii, dtype=np.float64)
    if R.ndim == 1:
        R = np.stack([R, R], axis=1)
    k = 0
    for i, s in enumerate(s_vals):
        while k < len(cum) - 2 and cum[k + 1] < s:
            k += 1
        t = (s - cum[k]) / max(1e-9, cum[k + 1] - cum[k])
        out_p[i] = pts[k] * (1 - t) + pts[k + 1] * t
        out_r[i] = R[k] * (1 - t) + R[k + 1] * t
    return out_p, out_r, s_vals, total


def frames_for(points, ref=(0, 0, 1)):
    P = np.asarray(points, dtype=np.float64)
    n = len(P)
    T = np.zeros_like(P)
    T[0] = P[1] - P[0]
    T[-1] = P[-1] - P[-2]
    T[1:-1] = P[2:] - P[:-2]
    T /= np.linalg.norm(T, axis=1, keepdims=True) + 1e-12
    r = np.asarray(ref, dtype=np.float64)
    N = np.cross(T, r[None, :].repeat(n, axis=0))
    L = np.linalg.norm(N, axis=1)
    weak = L < 1e-6
    if np.any(weak):
        r2 = np.array([1.0, 0.0, 0.0]) if abs(r[0]) < 0.9 else np.array([0.0, 0.0, 1.0])
        N[weak] = np.cross(T[weak], r2)
        L = np.linalg.norm(N, axis=1)
    N /= (L[:, None] + 1e-12)
    B = np.cross(T, N)
    B /= np.linalg.norm(B, axis=1, keepdims=True) + 1e-12
    return T, N, B


def build_tube(geo, points, radii, rings, radial, uv_fn, jw_fn, tag,
               cap_start=True, cap_end=True, ref=(0, 0, 1), outward_centers=None):
    C, R, S, total = resample_polyline(points, radii, rings)
    T, N, B = frames_for(C, ref)
    grid = []
    for i in range(rings):
        row = []
        for j in range(radial + 1):
            th = 2 * math.pi * j / radial
            p = C[i] + R[i, 0] * math.cos(th) * N[i] + R[i, 1] * math.sin(th) * B[i]
            uv = uv_fn(i / (rings - 1), j / radial, C[i], th)
            jj, ww = jw_fn(S[i], total, i)
            row.append(geo.vert(p, uv, jj, ww, tag))
        grid.append(row)
    s_tri = len(geo.I)
    for i in range(rings - 1):
        for j in range(radial):
            a, b = grid[i][j], grid[i + 1][j]
            c, d = grid[i + 1][j + 1], grid[i][j + 1]
            geo.tri(a, d, b)
            geo.tri(b, d, c)
    if outward_centers is None:
        CC = C.copy()
        Tn = T.copy()

        def out_fn(p, _CC=CC):
            d2 = np.sum((_CC - p) ** 2, axis=1)
            c = _CC[int(np.argmin(d2))]
            return p - c
    else:
        out_fn = outward_centers
    geo.fix_winding(s_tri, len(geo.I), out_fn)
    if cap_start:
        jj, ww = jw_fn(0.0, total, 0)
        geo.cap_fan(grid[0][:-1][::-1] if False else grid[0][:-1], C[0],
                    uv_fn(0, 0.5, C[0], 0), jj, ww, tag, -T[0])
    if cap_end:
        jj, ww = jw_fn(total, total, rings - 1)
        geo.cap_fan(grid[-1][:-1], C[-1], uv_fn(1, 0.5, C[-1], 0), jj, ww, tag, T[-1])
    return C, R, S, total


def build_loft_y(geo, stations, rings_total, radial, uv_fn, jw_fn, tag,
                 cap_bottom=True, cap_top=False):
    """stations: list of (y, rx, rz, cx, cz). Rings advance upward."""
    st = np.asarray(stations, dtype=np.float64)
    ys = np.linspace(st[0, 0], st[-1, 0], rings_total)
    rxs = np.interp(ys, st[:, 0], st[:, 1])
    rzs = np.interp(ys, st[:, 0], st[:, 2])
    cxs = np.interp(ys, st[:, 0], st[:, 3])
    czs = np.interp(ys, st[:, 0], st[:, 4])
    grid = []
    for i, y in enumerate(ys):
        row = []
        for j in range(radial + 1):
            th = 2 * math.pi * j / radial
            p = (cxs[i] + rxs[i] * math.sin(th), y, czs[i] + rzs[i] * math.cos(th))
            row.append(geo.vert(p, uv_fn(y, j / radial, th), *jw_fn(y), tag))
        grid.append(row)
    s_tri = len(geo.I)
    for i in range(rings_total - 1):
        for j in range(radial):
            a, b = grid[i][j], grid[i + 1][j]
            c, d = grid[i + 1][j + 1], grid[i][j + 1]
            geo.tri(a, d, b)
            geo.tri(b, d, c)

    def out_fn(p):
        i = int(np.argmin(np.abs(ys - p[1])))
        return np.array([p[0] - cxs[i], 0.0, p[2] - czs[i]])

    geo.fix_winding(s_tri, len(geo.I), out_fn)
    if cap_bottom:
        jj, ww = jw_fn(ys[0])
        geo.cap_fan(grid[0][:-1], (cxs[0], ys[0], czs[0]), uv_fn(ys[0], 0.5, 0),
                    jj, ww, tag, (0, -1, 0))
    if cap_top:
        jj, ww = jw_fn(ys[-1])
        geo.cap_fan(grid[-1][:-1], (cxs[-1], ys[-1], czs[-1]), uv_fn(ys[-1], 0.5, 0),
                    jj, ww, tag, (0, 1, 0))
    return ys, rxs, rzs, cxs, czs


def build_sphere(geo, center, radii, nlat, nlon, uv_fn, jw_fn, tag,
                 lat_range=(0.0, math.pi), theta_max_fn=None):
    """uv_fn(lat01, lon01, pos) ; jw_fn(pos)->(j,w)."""
    cx, cy, cz = center
    rx, ry, rz = radii
    grid = []
    for i in range(nlat + 1):
        f = i / nlat
        row = []
        for j in range(nlon + 1):
            g = j / nlon
            phi = 2 * math.pi * g
            if theta_max_fn is not None:
                th = theta_max_fn(phi) * f
            else:
                th = lat_range[0] + (lat_range[1] - lat_range[0]) * f
            p = np.array([cx + rx * math.sin(th) * math.sin(phi),
                          cy + ry * math.cos(th),
                          cz + rz * math.sin(th) * math.cos(phi)])
            row.append(geo.vert(p, uv_fn(f, g, p), *jw_fn(p), tag))
        grid.append(row)
    s_tri = len(geo.I)
    for i in range(nlat):
        for j in range(nlon):
            a, b = grid[i][j], grid[i + 1][j]
            c, d = grid[i + 1][j + 1], grid[i][j + 1]
            geo.tri(a, d, b)
            geo.tri(b, d, c)
    cc = np.array(center)

    def out_fn(p, _cc=cc):
        return p - _cc

    geo.fix_winding(s_tri, len(geo.I), out_fn)
    return grid


# --------------------------------------------------- weight field helpers ---
def torso_bone(y):
    H = [("hips", 0.99), ("spine", 1.13), ("chest", 1.22), ("upperChest", 1.335), ("neck", 9)]
    bones = [JIDX[n] for n, _ in H]
    bounds = [b for _, b in H]
    bw = 0.035
    for k in range(len(bones) - 1):
        if y < bounds[k] - bw:
            return jw4([(bones[k], 1.0)])
        if y < bounds[k] + bw:
            t = sstep(bounds[k] - bw, bounds[k] + bw, y)
            return jw4([(bones[k], 1 - t), (bones[k + 1], t)])
    return jw4([(bones[-1], 1.0)])


def head_bone(p):
    y = p[1]
    if y >= 1.40:
        return jw4([(JIDX["head"], 1.0)])
    if y >= 1.355:
        t = sstep(1.355, 1.40, y)
        return jw4([(JIDX["neck"], 1 - t), (JIDX["head"], t)])
    return jw4([(JIDX["neck"], 1.0)])


def limb_weights(s, total, breaks):
    """breaks: [(s_pos, bone_below, bone_above, half_width)...] sorted."""
    bones = [breaks[0][1]] + [b[2] for b in breaks]
    pos = [b[0] for b in breaks]
    wdt = [b[3] for b in breaks]
    for k, (sp, hw) in enumerate(zip(pos, wdt)):
        if s < sp - hw:
            return jw4([(bones[k], 1.0)])
        if s < sp + hw:
            t = sstep(sp - hw, sp + hw, s)
            return jw4([(bones[k], 1 - t), (bones[k + 1], t)])
    return jw4([(bones[-1], 1.0)])


# ------------------------------------------------------------- UV helpers ---
TORSO_Y0, TORSO_Y1 = 0.84, 1.36


def torso_uv(y, g, th):
    return (0.02 + 0.58 * g, (y - TORSO_Y0) / (TORSO_Y1 - TORSO_Y0))


FACE_U0, FACE_U1, FACE_V0, FACE_V1 = 0.66, 0.96, 0.66, 0.96
FACE_X0, FACE_X1, FACE_Y0, FACE_Y1 = -0.075, 0.075, 1.40, 1.545


def face_uv(p):
    u = FACE_U0 + (FACE_U1 - FACE_U0) * (p[0] - FACE_X0) / (FACE_X1 - FACE_X0)
    v = FACE_V0 + (FACE_V1 - FACE_V0) * (FACE_Y1 - p[1]) / (FACE_Y1 - FACE_Y0)
    return (min(FACE_U1, max(FACE_U0, u)), min(FACE_V1, max(FACE_V0, v)))


def plain_uv(u, v):
    # plain skin patch, clear of shading bands (v 0.35..0.42) and face quad
    return (0.03 + 0.07 * u, 0.35 + 0.07 * v)


# ------------------------------------------------------------------ build ---
def build_body():
    g = Geo()

    # ---- torso loft ----
    TORSO = [
        (0.840, 0.140, 0.100, 0.0, -0.006),
        (0.880, 0.156, 0.110, 0.0, -0.008),
        (0.920, 0.156, 0.110, 0.0, -0.008),
        (0.960, 0.148, 0.105, 0.0, -0.005),
        (1.000, 0.134, 0.097, 0.0, -0.002),
        (1.050, 0.122, 0.090, 0.0, 0.000),
        (1.100, 0.117, 0.088, 0.0, 0.000),
        (1.140, 0.124, 0.093, 0.0, 0.001),
        (1.180, 0.138, 0.101, 0.0, 0.003),
        (1.215, 0.150, 0.107, 0.0, 0.005),
        (1.250, 0.156, 0.104, 0.0, 0.002),
        (1.290, 0.163, 0.097, 0.0, -0.003),
        (1.320, 0.150, 0.088, 0.0, -0.005),
        (1.345, 0.105, 0.070, 0.0, -0.004),
        (1.360, 0.056, 0.056, 0.0, 0.000),
    ]
    n_torso0 = len(g.P)
    build_loft_y(g, TORSO, 43, 20, torso_uv, torso_bone, "torso",
                 cap_bottom=True, cap_top=False)
    n_torso1 = len(g.P)

    # bust + buttocks shaping (smooth gaussian displacements)
    P = np.asarray(g.P, dtype=np.float64)
    torso_slice = slice(n_torso0, n_torso1)
    Pt = P[torso_slice]
    for bcx in (-0.062, 0.062):
        c = np.array([bcx, 1.215, 0.10])
        d = (Pt - c) / np.array([0.075, 0.075, 0.060])
        d2 = np.sum(d ** 2, axis=1)
        m = np.exp(-d2) * (Pt[:, 2] > 0.02)
        direction = np.stack([(Pt[:, 0] - bcx) * 1.2,
                              (Pt[:, 1] - 1.215) * 0.4,
                              np.full_like(Pt[:, 0], 0.09)], axis=1)
        direction /= np.linalg.norm(direction, axis=1, keepdims=True) + 1e-12
        Pt = Pt + direction * (0.030 * m)[:, None]
    for bcx in (-0.068, 0.068):
        c = np.array([bcx, 0.925, -0.10])
        d = (Pt - c) / np.array([0.075, 0.070, 0.060])
        d2 = np.sum(d ** 2, axis=1)
        m = np.exp(-d2) * (Pt[:, 2] < -0.02)
        direction = np.stack([(Pt[:, 0] - bcx) * 0.8,
                              (Pt[:, 1] - 0.925) * 0.3,
                              np.full_like(Pt[:, 0], -0.09)], axis=1)
        direction /= np.linalg.norm(direction, axis=1, keepdims=True) + 1e-12
        Pt = Pt + direction * (0.024 * m)[:, None]
    # gentle crotch pinch (front, bottom)
    m = np.exp(-(((Pt[:, 0]) / 0.05) ** 2 + ((Pt[:, 1] - 0.845) / 0.035) ** 2)) * (Pt[:, 2] > 0.0)
    Pt[:, 2] -= 0.010 * m
    P[torso_slice] = Pt
    g.P = [tuple(p) for p in P.tolist()]

    # ---- neck ----
    build_tube(g, [(0, 1.340, 0), (0, 1.375, 0.001), (0, 1.410, 0.003), (0, 1.445, 0.004)],
               [0.054, 0.051, 0.050, 0.052], 8, 12,
               lambda f, h, c, th: plain_uv(h, f), lambda s, t, i: head_bone((0, 1.34 + 0.105 * (s / max(t, 1e-9)), 0)),
               "neck", cap_start=True, cap_end=False)

    # ---- head ----
    HC = np.array([0, 1.485, 0.012])
    HR = (0.105, 0.125, 0.115)
    n_h0 = len(g.P)
    build_sphere(g, HC, HR, 22, 26, lambda f, hh, p: face_uv(p),
                 lambda p: head_bone(p), "head")
    n_h1 = len(g.P)
    P = np.asarray(g.P, dtype=np.float64)
    Ph = P[n_h0:n_h1]
    # anime chin taper + slight face flatten
    below = np.clip((HC[1] - Ph[:, 1]) / HR[1], 0, 1.2)
    taper_x = 1 - 0.38 * np.power(np.clip(below, 0, 1), 1.6) * (Ph[:, 1] < HC[1])
    taper_z = 1 - 0.22 * np.power(np.clip(below, 0, 1), 1.5) * (Ph[:, 1] < HC[1])
    Ph[:, 0] = HC[0] + (Ph[:, 0] - HC[0]) * taper_x
    Ph[:, 2] = HC[2] + (Ph[:, 2] - HC[2]) * taper_z
    front = (Ph[:, 2] > HC[2]) & (np.abs(Ph[:, 0]) < 0.055) & (Ph[:, 1] > 1.44) & (Ph[:, 1] < 1.53)
    Ph[front, 2] -= 0.005 * np.exp(-((Ph[front, 0]) / 0.04) ** 2)
    P[n_h0:n_h1] = Ph
    g.P = [tuple(p) for p in P.tolist()]

    # ---- ears ----
    for sx in (1.0, -1.0):
        build_sphere(g, (sx * 0.102, 1.472, 0.005), (0.018, 0.028, 0.022), 10, 12,
                     lambda f, hh, p: plain_uv(hh, f),
                     lambda p: jw4([(JIDX["head"], 1.0)]), "ear")

    # ---- arms ----
    for side, sx in (("left", 1.0), ("right", -1.0)):
        p0 = (sx * 0.150, 1.310, 0)
        p1 = tuple(JWORLD[JIDX[f"{side}UpperArm"]])
        p2 = tuple(JWORLD[JIDX[f"{side}LowerArm"]])
        p3 = tuple(JWORLD[JIDX[f"{side}Hand"]])
        pts = [p0, p1, p2, p3]
        seg = [np.linalg.norm(np.subtract(pts[i + 1], pts[i])) for i in range(3)]
        s_elbow = seg[0] + seg[1]
        total = sum(seg)
        jsh, jua, jla, jha = (JIDX[f"{side}{b}"] for b in
                              ("Shoulder", "UpperArm", "LowerArm", "Hand"))
        breaks = [(0.055, jsh, jua, 0.045), (s_elbow, jua, jla, 0.045),
                  (total - 0.035, jla, jha, 0.035)]

        def jw_arm(s, t, i, _b=breaks):
            return limb_weights(s, t, _b)

        build_tube(g, pts, [0.055, 0.050, 0.038, 0.027], 26, 12,
                   lambda f, h, c, th: plain_uv(h, f), jw_arm, f"arm_{side}",
                   cap_start=True, cap_end=False)
        # palm
        build_tube(g, [(sx * 0.287, 0.850, 0.008), (sx * 0.288, 0.800, 0.007),
                       (sx * 0.288, 0.755, 0.006)],
                   [(0.016, 0.037), (0.015, 0.035), (0.013, 0.033)], 8, 10,
                   lambda f, h, c, th: plain_uv(h, f),
                   lambda s, t, i: jw4([(jha, 1.0)]), f"palm_{side}",
                   cap_start=False, cap_end=True)
        # fingers
        def finger_chain(names, radii):
            jp = [JIDX[n] for n in names]
            pw = [tuple(JWORLD[j]) for j in jp]
            tip_dir = np.array([-sx * 0.35, -1.0, 0.0])
            tip_dir /= np.linalg.norm(tip_dir)
            tip = tuple(np.array(pw[-1]) + tip_dir * 0.022)
            path = pw + [tip]
            rr = np.linspace(radii[0], radii[1], 4)

            def jw_f(s, t, i, _jp=jp):
                # 3 segments over path; blend 20% zones
                segs = [np.linalg.norm(np.subtract(path[k + 1], path[k])) for k in range(3)]
                b1, b2 = segs[0], segs[0] + segs[1]
                if s < b1 - 0.004:
                    return jw4([(_jp[0], 1.0)])
                if s < b1 + 0.004:
                    tt = sstep(b1 - 0.004, b1 + 0.004, s)
                    return jw4([(_jp[0], 1 - tt), (_jp[1], tt)])
                if s < b2 - 0.004:
                    return jw4([(_jp[1], 1.0)])
                if s < b2 + 0.004:
                    tt = sstep(b2 - 0.004, b2 + 0.004, s)
                    return jw4([(_jp[1], 1 - tt), (_jp[2], tt)])
                return jw4([(_jp[2], 1.0)])

            build_tube(g, path, rr, 14, 8, lambda f, h, c, th: plain_uv(h, f),
                       jw_f, f"finger_{side}", cap_start=False, cap_end=True,
                       ref=(1, 0, 0))

        finger_chain([f"{side}ThumbProximal", f"{side}ThumbIntermediate", f"{side}ThumbDistal"],
                     (0.0105, 0.0082))
        for fn, rr in (("Index", (0.0095, 0.0075)), ("Middle", (0.0098, 0.0077)),
                       ("Ring", (0.0094, 0.0074)), ("Little", (0.0084, 0.0066))):
            finger_chain([f"{side}{fn}Proximal", f"{side}{fn}Intermediate", f"{side}{fn}Distal"], rr)

    # ---- legs ----
    for side, sx in (("left", 1.0), ("right", -1.0)):
        p0 = (sx * 0.095, 0.995, 0)
        p1 = tuple(JWORLD[JIDX[f"{side}UpperLeg"]])
        p2 = tuple(JWORLD[JIDX[f"{side}LowerLeg"]])
        p3 = tuple(JWORLD[JIDX[f"{side}Foot"]])
        p4 = (sx * 0.11, 0.055, 0.012)  # stub down into the foot (no gap)
        pts = [p0, p1, p2, p3, p4]
        seg = [np.linalg.norm(np.subtract(pts[i + 1], pts[i])) for i in range(3)]
        s_knee = seg[0] + seg[1]
        total = sum(seg)
        jh, jul, jll, jft = JIDX["hips"], JIDX[f"{side}UpperLeg"], \
            JIDX[f"{side}LowerLeg"], JIDX[f"{side}Foot"]
        breaks = [(0.075, jh, jul, 0.05), (s_knee, jul, jll, 0.05),
                  (total - 0.045, jll, jft, 0.045)]

        def jw_leg(s, t, i, _b=breaks):
            return limb_weights(s, t, _b)

        n_l0 = len(g.P)
        build_tube(g, pts, [0.088, 0.084, 0.054, 0.033, 0.030], 32, 14,
                   lambda f, h, c, th: plain_uv(h, f), jw_leg, f"leg_{side}",
                   cap_start=True, cap_end=False)
        n_l1 = len(g.P)
        # thigh rounding bulge
        P = np.asarray(g.P, dtype=np.float64)
        Pl = P[n_l0:n_l1]
        axis_x = sx * (0.095 + (0.105 - 0.095) * np.clip((0.96 - Pl[:, 1]) / 0.43, 0, 1))
        rad = np.stack([Pl[:, 0] - axis_x, np.zeros_like(Pl[:, 0]), Pl[:, 2] - 0.004], axis=1)
        rl = np.linalg.norm(rad, axis=1, keepdims=True) + 1e-12
        m = np.exp(-((Pl[:, 1] - 0.78) / 0.15) ** 2) * (Pl[:, 1] > 0.55) * (Pl[:, 1] < 0.98)
        Pl = Pl + (rad / rl) * (0.009 * m)[:, None]
        P[n_l0:n_l1] = Pl
        g.P = [tuple(p) for p in P.tolist()]

        # ---- foot (loft along +Z) ----
        jto = JIDX[f"{side}Toes"]
        stations = [  # z, cy, rx, ry
            (-0.050, 0.062, 0.026, 0.042),
            (-0.030, 0.052, 0.032, 0.048),
            (0.020, 0.048, 0.035, 0.044),
            (0.070, 0.046, 0.036, 0.040),
            (0.120, 0.042, 0.035, 0.034),
            (0.160, 0.036, 0.032, 0.028),
            (0.182, 0.032, 0.028, 0.024),
            (0.196, 0.030, 0.021, 0.018),
        ]
        cx = sx * 0.11
        zs = np.linspace(stations[0][0], stations[-1][0], 13)
        cys = np.interp(zs, [s[0] for s in stations], [s[1] for s in stations])
        rxs = np.interp(zs, [s[0] for s in stations], [s[2] for s in stations])
        rys = np.interp(zs, [s[0] for s in stations], [s[3] for s in stations])
        fgrid = []
        for i, z in enumerate(zs):
            row = []
            for j in range(13):
                th = 2 * math.pi * j / 12
                p = (cx + rxs[i] * math.cos(th), cys[i] + rys[i] * math.sin(th), z)
                if z < 0.085:
                    jj, ww = jw4([(jft, 1.0)])
                elif z < 0.135:
                    tt = sstep(0.085, 0.135, z)
                    jj, ww = jw4([(jft, 1 - tt), (jto, tt)])
                else:
                    jj, ww = jw4([(jto, 1.0)])
                row.append(g.vert(p, plain_uv(j / 12, i / 12), jj, ww, f"foot_{side}"))
            fgrid.append(row)
        s_tri = len(g.I)
        for i in range(len(zs) - 1):
            for j in range(12):
                a, b = fgrid[i][j], fgrid[i + 1][j]
                c, d = fgrid[i + 1][j + 1], fgrid[i][j + 1]
                g.tri(a, b, d)
                g.tri(b, c, d)

        def out_foot(p, _cx=cx):
            return np.array([p[0] - _cx, p[1] - 0.05, 0.0])

        g.fix_winding(s_tri, len(g.I), out_foot)
        g.cap_fan(fgrid[0][:-1], (cx, cys[0], zs[0]), plain_uv(0.5, 0),
                  *jw4([(jft, 1.0)]), f"foot_{side}", (0, 0, -1))
        g.cap_fan(fgrid[-1][:-1], (cx, cys[-1], zs[-1]), plain_uv(0.5, 1),
                  *jw4([(jto, 1.0)]), f"foot_{side}", (0, 0, 1))
    return g


def build_hair():
    g = Geo()
    HC = (0, 1.492, -0.002)
    HR = (0.112, 0.132, 0.122)

    def th_max(phi):
        a = abs((phi + math.pi) % (2 * math.pi) - math.pi)
        temple_dip = 0.10 * math.exp(-((a - 0.75) / 0.28) ** 2)
        return 1.02 + 0.78 * sstep(0.3, 1.4, a) + 0.15 * sstep(1.4, 2.6, a) + temple_dip

    build_sphere(g, HC, HR, 26, 56, lambda f, hh, p: (hh, f),
                 lambda p: jw4([(JIDX["head"], 1.0)]), "hair",
                 theta_max_fn=th_max)
    return g


def build_eyes():
    g = Geo()
    for sx, u0, u1 in ((1.0, 0.52, 0.98), (-1.0, 0.02, 0.48)):
        cx, cy, cz = sx * 0.044, 1.492, 0.1185
        w, h = 0.040, 0.053
        n = 8
        grid = []
        for i in range(n):
            f = i / (n - 1)
            row = []
            for j in range(n):
                hh = j / (n - 1)
                dx = (hh - 0.5) * w
                dy = (0.5 - f) * h
                z = cz - (dx / (w / 2)) ** 2 * 0.008 - (dy / (h / 2)) ** 2 * 0.005
                row.append(g.vert((cx + dx, cy + dy, z),
                                  (u0 + (u1 - u0) * hh, 0.06 + 0.88 * f),
                                  *jw4([(JIDX["head"], 1.0)]), "eye"))
            grid.append(row)
        s_tri = len(g.I)
        for i in range(n - 1):
            for j in range(n - 1):
                a, b = grid[i][j], grid[i + 1][j]
                c, d = grid[i + 1][j + 1], grid[i][j + 1]
                g.tri(a, b, d)
                g.tri(b, c, d)
        g.fix_winding(s_tri, len(g.I), lambda p: np.array([0.0, 0.0, 1.0]))
    return g


# ------------------------------------------------------------------ morphs ---
def torso_cz(y):
    pts = [(0.84, -0.006), (0.92, -0.008), (1.0, -0.002), (1.1, 0.0),
           (1.18, 0.003), (1.215, 0.005), (1.29, -0.003), (1.36, 0.0)]
    ys = [p[0] for p in pts]
    return float(np.interp(y, ys, [p[1] for p in pts]))


def build_morphs(body):
    P = np.asarray(body.P, dtype=np.float64)
    TAG = np.array(body.TAG)
    n = len(P)
    is_torso = TAG == "torso"
    is_leg = np.array([t.startswith("leg_") for t in TAG])
    is_arm = np.array([t.startswith("arm_") for t in TAG])
    morphs = {}

    # bust: fuller bust, smooth falloff around bust centres
    d = np.zeros((n, 3))
    for bcx in (-0.062, 0.062):
        c = np.array([bcx, 1.215, 0.10])
        dd = np.sum(((P - c) / np.array([0.085, 0.085, 0.070])) ** 2, axis=1)
        m = np.exp(-dd) * is_torso * (P[:, 2] > 0.02)
        direction = np.stack([(P[:, 0] - bcx) * 1.1, (P[:, 1] - 1.215) * 0.35,
                              np.full(n, 0.10)], axis=1)
        direction /= np.linalg.norm(direction, axis=1, keepdims=True) + 1e-12
        d += direction * (0.038 * m)[:, None]
    morphs["bust"] = d

    # waist: cinch (inward radial around waist)
    d = np.zeros((n, 3))
    cz = np.array([torso_cz(y) for y in P[:, 1]])
    rad = np.stack([P[:, 0], np.zeros(n), P[:, 2] - cz], axis=1)
    rl = np.linalg.norm(rad, axis=1, keepdims=True) + 1e-12
    m = np.exp(-((P[:, 1] - 1.10) / 0.085) ** 2) * is_torso
    d = -(rad / rl) * (0.020 * m)[:, None]
    morphs["waist"] = d

    # hips: rounder hips
    d = np.zeros((n, 3))
    m = np.exp(-((P[:, 1] - 0.93) / 0.095) ** 2) * (is_torso | is_leg) * (P[:, 1] < 1.04)
    d = (rad / rl) * (0.024 * m)[:, None]
    morphs["hips"] = d

    # thighs: fuller thighs
    d = np.zeros((n, 3))
    sx = np.sign(P[:, 0])
    sx[sx == 0] = 1.0
    axis_x = sx * (0.095 + (0.105 - 0.095) * np.clip((0.96 - P[:, 1]) / 0.43, 0, 1))
    rad2 = np.stack([P[:, 0] - axis_x, np.zeros(n), P[:, 2] - 0.004], axis=1)
    rl2 = np.linalg.norm(rad2, axis=1, keepdims=True) + 1e-12
    m = np.exp(-((P[:, 1] - 0.78) / 0.16) ** 2) * is_leg * (P[:, 1] > 0.50) * (P[:, 1] < 0.98)
    d = (rad2 / rl2) * (0.022 * m)[:, None]
    morphs["thighs"] = d

    # arms: thicker arms
    d = np.zeros((n, 3))
    for sxx in (1.0, -1.0):
        sel = is_arm & (np.sign(P[:, 0]) == sxx)
        if not np.any(sel):
            continue
        A = np.array([sxx * 0.205, 1.295, 0.0])
        Bp = np.array([sxx * 0.258, 1.075, 0.005])
        Cc = np.array([sxx * 0.287, 0.845, 0.008])
        Pp = P[sel]
        for Q0, Q1 in ((A, Bp), (Bp, Cc)):
            v = Q1 - Q0
            t = np.clip(((Pp - Q0) @ v) / (v @ v), 0, 1)
            q = Q0 + t[:, None] * v
            rad3 = Pp - q
            rl3 = np.linalg.norm(rad3, axis=1, keepdims=True) + 1e-12
            idx = np.where(sel)[0]
            take = (t > 0.02) & (t < 0.98)
            d[idx[take]] += (rad3[take] / rl3[take]) * 0.013
    morphs["arms"] = d

    out = {}
    for k, v in morphs.items():
        v = np.nan_to_num(v, nan=0.0, posinf=0.0, neginf=0.0).astype(np.float32)
        out[k] = v
    return out


# ------------------------------------------------------------- GLB assembly ---
def assemble(body, hair, eyes, morphs, tex):
    NB = np.asarray(body.compute_normals(), dtype=np.float32)
    NH = np.asarray(hair.compute_normals(), dtype=np.float32)
    NE = np.asarray(eyes.compute_normals(), dtype=np.float32)
    blobs = []       # (bytes,)
    views = []       # {buffer, byteOffset, byteLength, ...}
    accs = []

    def add_view(data: bytes, target=None):
        off = sum(len(b) for b in blobs)
        pad = (-off) % 4
        if pad:
            blobs.append(b"\x00" * pad)
            off += pad
        blobs.append(bytes(data))
        v = {"buffer": 0, "byteOffset": off, "byteLength": len(data)}
        if target is not None:
            v["target"] = target
        views.append(v)
        return len(views) - 1

    def add_acc(view, comp, count, typ, mn=None, mx=None):
        a = {"bufferView": view, "componentType": comp, "count": count, "type": typ}
        if mn is not None:
            a["min"] = [float(x) for x in mn]
            a["max"] = [float(x) for x in mx]
        accs.append(a)
        return len(accs) - 1

    def arr_minmax(a):
        return a.min(axis=0), a.max(axis=0)

    parts = []
    for geo, N in ((body, NB), (hair, NH), (eyes, NE)):
        P = np.asarray(geo.P, dtype=np.float32)
        UV = np.asarray(geo.UV, dtype=np.float32)
        J = np.asarray(geo.J, dtype=np.uint16)
        W = np.asarray(geo.W, dtype=np.float32)
        II = np.asarray(geo.I, dtype=np.uint16)
        assert P.max() < 10 and np.all(np.isfinite(P))
        assert np.all(np.isfinite(N)) and np.all(np.isfinite(UV))
        assert np.all(np.isfinite(W))
        ws = W.sum(axis=1)
        assert np.allclose(ws, 1.0), f"weights must sum to 1 (got {ws.min()}..{ws.max()})"
        vP = add_view(P.tobytes(), 34962)
        vN = add_view(N.tobytes(), 34962)
        vU = add_view(UV.tobytes(), 34962)
        vJ = add_view(J.tobytes(), 34962)
        vW = add_view(W.tobytes(), 34962)
        vI = add_view(II.tobytes(), 34963)
        mn, mx = arr_minmax(P)
        aP = add_acc(vP, 5126, len(P), "VEC3", mn, mx)
        mn, mx = arr_minmax(N)
        aN = add_acc(vN, 5126, len(N), "VEC3", mn, mx)
        mn, mx = arr_minmax(UV)
        aU = add_acc(vU, 5126, len(UV), "VEC2", mn, mx)
        mn, mx = arr_minmax(J.astype(np.float32))
        aJ = add_acc(vJ, 5123, len(J), "VEC4", mn, mx)
        mn, mx = arr_minmax(W)
        aW = add_acc(vW, 5126, len(W), "VEC4", mn, mx)
        aI = add_acc(vI, 5123, len(II), "SCALAR", [int(II.min())], [int(II.max())])
        parts.append(dict(P=aP, N=aN, U=aU, J=aJ, W=aW, I=aI, n=len(P)))

    # morph targets (Body only)
    target_names = ["bust", "waist", "hips", "thighs", "arms"]
    tgt_acc = []
    for name in target_names:
        D = morphs[name]
        assert D.shape == (parts[0]["n"], 3)
        assert np.all(np.isfinite(D)), f"NaN in morph {name}"
        mx = float(np.abs(D).max())
        assert mx > 1e-4, f"morph {name} is empty!"
        v = add_view(np.ascontiguousarray(D).tobytes(), 34962)
        mn, mxv = arr_minmax(D)
        tgt_acc.append(add_acc(v, 5126, len(D), "VEC3", mn, mxv))
        print(f"  morph {name:7s} max|d|={mx * 1000:.1f}mm")

    # inverse bind matrices
    ibm = np.zeros((NJ, 4, 4), dtype=np.float32)
    for i in range(NJ):
        ibm[i] = np.eye(4, dtype=np.float32)
        ibm[i, 0:3, 3] = -JWORLD[i].astype(np.float32)  # column-vector convention: T in col 3
    ibm_t = np.ascontiguousarray(ibm.transpose(0, 2, 1))  # column-major
    vIBM = add_view(ibm_t.tobytes())
    aIBM = add_acc(vIBM, 5126, NJ, "MAT4")

    # images
    names = ["body", "hair", "eyes", "normal"]
    img_views = []
    for n in names:
        img_views.append(add_view(tex[n]))

    # ---- nodes ----
    children = {i: [] for i in range(NJ)}
    for i, p in enumerate(JPARENT):
        if p >= 0:
            children[p].append(i)
    nodes = []
    for i, (name, parent, w) in enumerate(joint_defs):
        local = np.array(w) - (np.array(joint_defs[JPARENT[i]][2]) if JPARENT[i] >= 0 else 0)
        nd = {"name": name, "translation": [float(x) for x in local]}
        if children[i]:
            nd["children"] = children[i]
        nodes.append(nd)
    mesh_node = []
    for mi, nm in enumerate(("Body", "Hair", "Eyes")):
        nodes.append({"name": nm, "mesh": mi, "skin": 0})
        mesh_node.append(len(nodes) - 1)

    meshes = [
        {"name": "Body",
         "primitives": [{
             "attributes": {"POSITION": parts[0]["P"], "NORMAL": parts[0]["N"],
                            "TEXCOORD_0": parts[0]["U"], "JOINTS_0": parts[0]["J"],
                            "WEIGHTS_0": parts[0]["W"]},
             "indices": parts[0]["I"], "material": 0,
             "targets": [{"POSITION": a} for a in tgt_acc],
             "extras": {"targetNames": target_names}}],
         "extras": {"targetNames": target_names}},
        {"name": "Hair",
         "primitives": [{
             "attributes": {"POSITION": parts[1]["P"], "NORMAL": parts[1]["N"],
                            "TEXCOORD_0": parts[1]["U"], "JOINTS_0": parts[1]["J"],
                            "WEIGHTS_0": parts[1]["W"]},
             "indices": parts[1]["I"], "material": 1}]},
        {"name": "Eyes",
         "primitives": [{
             "attributes": {"POSITION": parts[2]["P"], "NORMAL": parts[2]["N"],
                            "TEXCOORD_0": parts[2]["U"], "JOINTS_0": parts[2]["J"],
                            "WEIGHTS_0": parts[2]["W"]},
             "indices": parts[2]["I"], "material": 2}]},
    ]

    head_idx = JIDX["head"]
    vrm = {
        "exporterVersion": "AnimeDrawingStudio-gen-1.0",
        "specVersion": "0.0",
        "meta": {
            "title": "Anime Drawing Base", "version": "1.0",
            "author": "AnimeDrawingStudio", "contactInformation": "",
            "reference": "", "texture": -1,
            "allowedUserName": "Everyone", "violentUssageName": "Disallow",
            "sexualUssageName": "Disallow", "commercialUssageName": "PersonalNonProfit",
            "otherPermissionUrl": "", "licenseName": "Redistribution_Prohibited",
            "otherLicenseUrl": "",
        },
        "humanoid": {
            "humanBones": [{"bone": n, "node": JIDX[n], "useDefaultValues": True}
                           for n, _, _ in joint_defs],
            "armStretch": 0.05, "legStretch": 0.05,
            "upperArmTwist": 0.5, "lowerArmTwist": 0.5,
            "upperLegTwist": 0.5, "lowerLegTwist": 0.5,
            "feetSpacing": 0.0, "hasTranslationDoF": False,
        },
        "firstPerson": {
            "firstPersonBone": head_idx,
            "firstPersonBoneOffset": {"x": 0, "y": 0.06, "z": 0},
            "meshAnnotations": [{"mesh": i, "firstPersonFlag": "Auto"} for i in range(3)],
            "lookAtTypeName": "Bone",
            "lookAtHorizontalInner": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90.0, "yRange": 10.0},
            "lookAtHorizontalOuter": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90.0, "yRange": 10.0},
            "lookAtVerticalDown": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90.0, "yRange": 10.0},
            "lookAtVerticalUp": {"curve": [0, 0, 0, 1, 1, 1, 1, 0], "xRange": 90.0, "yRange": 10.0},
        },
        "blendShapeMaster": {"blendShapeGroups": []},
        "secondaryAnimation": {"boneGroups": [], "colliderGroups": []},
        "materialProperties": [
            {"name": "Body", "shader": "VRM_USE_GLTFSHADER", "renderQueue": -1},
            {"name": "Hair", "shader": "VRM_USE_GLTFSHADER", "renderQueue": -1},
            {"name": "Eyes", "shader": "VRM_USE_GLTFSHADER", "renderQueue": -1},
        ],
    }

    gltf = {
        "asset": {"version": "2.0", "generator": "AnimeDrawingStudio-gen-1.0"},
        "extensionsUsed": ["VRM"],
        "extensions": {"VRM": vrm},
        "scene": 0,
        "scenes": [{"nodes": [0] + mesh_node, "name": "Scene"}],
        "nodes": nodes,
        "meshes": meshes,
        "skins": [{"inverseBindMatrices": aIBM, "skeleton": 0,
                   "joints": list(range(NJ)), "name": "Armature"}],
        "materials": [
            {"name": "Body", "doubleSided": False,
             "pbrMetallicRoughness": {"baseColorTexture": {"index": 0},
                                      "metallicFactor": 0.0, "roughnessFactor": 0.85},
             "normalTexture": {"index": 3, "scale": 1.0}},
            {"name": "Hair", "doubleSided": True,
             "pbrMetallicRoughness": {"baseColorTexture": {"index": 1},
                                      "metallicFactor": 0.0, "roughnessFactor": 0.9},
             "normalTexture": {"index": 3, "scale": 1.0}},
            {"name": "Eyes", "doubleSided": False,
             "pbrMetallicRoughness": {"baseColorTexture": {"index": 2},
                                      "metallicFactor": 0.0, "roughnessFactor": 0.35},
             "normalTexture": {"index": 3, "scale": 1.0}},
        ],
        "textures": [{"source": i, "sampler": 0, "name": n} for i, n in enumerate(names)],
        "images": [{"bufferView": v, "mimeType": "image/png", "name": n}
                   for v, n in zip(img_views, names)],
        "samplers": [{"magFilter": 9729, "minFilter": 9986,
                      "wrapS": 10497, "wrapT": 10497}],
        "accessors": accs,
        "bufferViews": views,
        "buffers": [{"byteLength": sum(len(b) for b in blobs)}],
    }

    jdata = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    jpad = (-len(jdata)) % 4
    jdata += b" " * jpad
    bdata = b"".join(blobs)
    bpad = (-len(bdata)) % 4
    bdata += b"\x00" * bpad
    total = 12 + 8 + len(jdata) + 8 + len(bdata)
    glb = struct.pack("<III", 0x46546C67, 2, total)
    glb += struct.pack("<II", len(jdata), 0x4E4F534A) + jdata
    glb += struct.pack("<II", len(bdata), 0x004E4942) + bdata
    return glb


def main():
    print("painting textures...")
    tex = {"body": png_bytes(tex_body()), "hair": png_bytes(tex_hair()),
           "eyes": png_bytes(tex_eyes()), "normal": png_bytes(tex_normal_flat())}
    for k, v in tex.items():
        print(f"  {k:6s} {len(v) / 1024:.1f} KB")
    print("building body...")
    body = build_body()
    print(f"  body verts={len(body.P)} tris={len(body.I) // 3}")
    print("building hair/eyes...")
    hair = build_hair()
    eyes = build_eyes()
    print(f"  hair verts={len(hair.P)} tris={len(hair.I) // 3}")
    print(f"  eyes verts={len(eyes.P)} tris={len(eyes.I) // 3}")
    print("building morphs...")
    morphs = build_morphs(body)
    print("assembling GLB...")
    glb = assemble(body, hair, eyes, morphs, tex)
    with open(OUT_PATH, "wb") as f:
        f.write(glb)
    mb = len(glb) / (1024 * 1024)
    print(f"wrote {OUT_PATH}  {len(glb)} bytes ({mb:.2f} MB)")
    assert mb < 2.5, "OVER 2.5 MB BUDGET!"
    print("OK - under 2.5 MB")


if __name__ == "__main__":
    main()
