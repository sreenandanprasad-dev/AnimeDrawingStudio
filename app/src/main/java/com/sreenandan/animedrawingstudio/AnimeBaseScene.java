package com.sreenandan.animedrawingstudio;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * Editable vector scene for an anime drawing reference.
 * The mannequin is a forward-kinematics rig: every vertex is bound to one of
 * 17 joints and the pose dialog drives each joint in 3 axes, so the figure can
 * be posed in full 3D and traced from any view angle.
 */
public final class AnimeBaseScene {
    // ---- joint rig -------------------------------------------------------
    public static final int J_PELVIS = 0;
    public static final int J_SPINE = 1;
    public static final int J_CHEST = 2;
    public static final int J_NECK = 3;
    public static final int J_HEAD = 4;
    public static final int J_SHOULDER_L = 5;
    public static final int J_ELBOW_L = 6;
    public static final int J_WRIST_L = 7;
    public static final int J_SHOULDER_R = 8;
    public static final int J_ELBOW_R = 9;
    public static final int J_WRIST_R = 10;
    public static final int J_HIP_L = 11;
    public static final int J_KNEE_L = 12;
    public static final int J_ANKLE_L = 13;
    public static final int J_HIP_R = 14;
    public static final int J_KNEE_R = 15;
    public static final int J_ANKLE_R = 16;
    public static final int JOINT_COUNT = 17;

    private static final String[] JOINT_NAMES = {
            "Pelvis (root)", "Spine", "Chest", "Neck", "Head",
            "L Shoulder", "L Elbow", "L Wrist", "R Shoulder", "R Elbow", "R Wrist",
            "L Hip", "L Knee", "L Ankle", "R Hip", "R Knee", "R Ankle"};
    private static final int[] JOINT_PARENTS = {
            -1, 0, 1, 2, 3, 2, 5, 6, 2, 8, 9, 0, 11, 12, 0, 14, 15};
    // {minX,maxX, minY,maxY, minZ,maxZ} per joint, in degrees.
    private static final int[][] JOINT_LIMITS = {
            {0, 0, 0, 0, 0, 0},
            {-40, 40, -30, 30, -30, 30},
            {-35, 35, -35, 35, -30, 30},
            {-35, 35, -60, 60, -35, 35},
            {-40, 40, -70, 70, -35, 35},
            {-180, 180, -90, 90, -170, 170},
            {-150, 0, -90, 90, -30, 30},
            {-60, 60, -60, 60, -60, 60},
            {-180, 180, -90, 90, -170, 170},
            {-150, 0, -90, 90, -30, 30},
            {-60, 60, -60, 60, -60, 60},
            {-120, 120, -70, 70, -90, 90},
            {0, 150, -15, 15, -15, 15},
            {-45, 45, -30, 30, -30, 30},
            {-120, 120, -70, 70, -90, 90},
            {0, 150, -15, 15, -15, 15},
            {-45, 45, -30, 30, -30, 30}};

    public static String jointName(int joint) {
        return (joint >= 0 && joint < JOINT_COUNT) ? JOINT_NAMES[joint] : "?";
    }

    public static int jointMin(int joint, int axis) {
        if (joint < 0 || joint >= JOINT_COUNT || axis < 0 || axis > 2) return 0;
        return JOINT_LIMITS[joint][axis * 2];
    }

    public static int jointMax(int joint, int axis) {
        if (joint < 0 || joint >= JOINT_COUNT || axis < 0 || axis > 2) return 0;
        return JOINT_LIMITS[joint][axis * 2 + 1];
    }

    public static final String[] PRESET_NAMES = {"Neutral", "T-Pose", "Walking", "Sitting", "Wave"};

    private final float[] positions, colors;
    private final int[] bones;
    // pivot per joint (elbow/wrist/knee/ankle pivots follow the chosen base model)
    private final float[][] pivots = new float[JOINT_COUNT][3];
    // live euler pose per joint, degrees
    private final float[][] pose = new float[JOINT_COUNT][3];
    // world matrices per joint, row-major 3x4
    private final float[][] jointWorld = new float[JOINT_COUNT][12];
    private float rx = -8f, ry = 18f, zoom = 1f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap cachedBitmap;
    private int cachedWidth = 0, cachedHeight = 0;
    private boolean dirty = true;

    public AnimeBaseScene(int model) {
        for (int j = 0; j < JOINT_COUNT; j++) jointWorld[j] = identity34();
        Mesh mesh = build(model);
        positions = mesh.positions();
        colors = mesh.colors();
        bones = mesh.bones();
        paint.setStyle(Paint.Style.FILL);
    }

    private static float[] identity34() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
    }

    public synchronized void rotate(float dx, float dy) {
        rx = Math.max(-85f, Math.min(85f, rx + dx));
        ry = (ry + dy) % 360f;
        dirty = true;
    }

    /** Legacy 4-slider pose, mapped onto the joint rig. */
    public synchronized void setPose(float arm, float elbow, float leg, float knee) {
        arm = Math.max(-70f, Math.min(70f, arm));
        elbow = Math.max(-100f, Math.min(100f, elbow));
        leg = Math.max(-45f, Math.min(45f, leg));
        knee = Math.max(-90f, Math.min(90f, knee));
        setJointPose(J_SHOULDER_L, 0, 0, -arm);
        setJointPose(J_SHOULDER_R, 0, 0, arm);
        setJointPose(J_ELBOW_L, 0, 0, -elbow);
        setJointPose(J_ELBOW_R, 0, 0, elbow);
        setJointPose(J_HIP_L, 0, 0, -leg);
        setJointPose(J_HIP_R, 0, 0, leg);
        setJointPose(J_KNEE_L, 0, 0, -knee);
        setJointPose(J_KNEE_R, 0, 0, knee);
    }

    /** Full 3D joint pose: euler degrees, applied Z then Y then X. */
    public synchronized void setJointPose(int joint, float x, float y, float z) {
        if (joint < 0 || joint >= JOINT_COUNT) return;
        pose[joint][0] = Math.max(jointMin(joint, 0), Math.min(jointMax(joint, 0), x));
        pose[joint][1] = Math.max(jointMin(joint, 1), Math.min(jointMax(joint, 1), y));
        pose[joint][2] = Math.max(jointMin(joint, 2), Math.min(jointMax(joint, 2), z));
        updateJointMatrices();
        dirty = true;
    }

    public synchronized float[] getJointPose(int joint) {
        if (joint < 0 || joint >= JOINT_COUNT) return new float[3];
        return new float[]{pose[joint][0], pose[joint][1], pose[joint][2]};
    }

    public synchronized void resetPose() {
        for (int j = 0; j < JOINT_COUNT; j++) {
            pose[j][0] = 0;
            pose[j][1] = 0;
            pose[j][2] = 0;
        }
        updateJointMatrices();
        dirty = true;
    }

    /** Mirror the pose across the body's left/right plane. */
    public synchronized void mirrorPose() {
        int[][] pairs = {{J_SHOULDER_L, J_SHOULDER_R}, {J_ELBOW_L, J_ELBOW_R},
                {J_WRIST_L, J_WRIST_R}, {J_HIP_L, J_HIP_R}, {J_KNEE_L, J_KNEE_R},
                {J_ANKLE_L, J_ANKLE_R}};
        for (int[] pr : pairs) {
            float[] a = {pose[pr[0]][0], pose[pr[0]][1], pose[pr[0]][2]};
            pose[pr[0]][0] = pose[pr[1]][0];
            pose[pr[0]][1] = -pose[pr[1]][1];
            pose[pr[0]][2] = -pose[pr[1]][2];
            pose[pr[1]][0] = a[0];
            pose[pr[1]][1] = -a[1];
            pose[pr[1]][2] = -a[2];
        }
        for (int j : new int[]{J_SPINE, J_CHEST, J_NECK, J_HEAD}) {
            pose[j][1] = -pose[j][1];
            pose[j][2] = -pose[j][2];
        }
        updateJointMatrices();
        dirty = true;
    }

    public synchronized void applyPreset(int preset) {
        resetPose();
        switch (preset) {
            case 1: // T-pose
                setJointPose(J_SHOULDER_L, 0, 0, -80);
                setJointPose(J_SHOULDER_R, 0, 0, 80);
                break;
            case 2: // walking
                setJointPose(J_HIP_R, -25, 0, 0);
                setJointPose(J_HIP_L, 22, 0, 0);
                setJointPose(J_KNEE_R, 20, 0, 0);
                setJointPose(J_KNEE_L, 6, 0, 0);
                setJointPose(J_ANKLE_R, 8, 0, 0);
                setJointPose(J_ANKLE_L, -6, 0, 0);
                setJointPose(J_SHOULDER_L, -18, 0, -6);
                setJointPose(J_SHOULDER_R, 15, 0, 6);
                setJointPose(J_ELBOW_L, -12, 0, 0);
                setJointPose(J_ELBOW_R, -8, 0, 0);
                setJointPose(J_CHEST, 0, -6, 0);
                break;
            case 3: // sitting
                setJointPose(J_HIP_L, -80, 0, -6);
                setJointPose(J_HIP_R, -80, 0, 6);
                setJointPose(J_KNEE_L, 85, 0, 0);
                setJointPose(J_KNEE_R, 85, 0, 0);
                setJointPose(J_ANKLE_L, 10, 0, 0);
                setJointPose(J_ANKLE_R, 10, 0, 0);
                setJointPose(J_SHOULDER_L, -10, 0, -8);
                setJointPose(J_SHOULDER_R, -10, 0, 8);
                setJointPose(J_ELBOW_L, -15, 0, 0);
                setJointPose(J_ELBOW_R, -15, 0, 0);
                break;
            case 4: // wave
                setJointPose(J_SHOULDER_R, 0, 0, 150);
                setJointPose(J_ELBOW_R, 0, 0, 25);
                setJointPose(J_WRIST_R, 0, 0, 15);
                setJointPose(J_HEAD, 0, -15, -8);
                setJointPose(J_CHEST, 0, 0, -5);
                break;
            default: // neutral already reset
                break;
        }
        dirty = true;
    }

    private void updateJointMatrices() {
        float[] local = new float[12];
        for (int j = 0; j < JOINT_COUNT; j++) {
            eulerToMat34(pose[j][0], pose[j][1], pose[j][2], pivots[j], local);
            int parent = JOINT_PARENTS[j];
            if (parent < 0) {
                System.arraycopy(local, 0, jointWorld[j], 0, 12);
            } else {
                mul34(jointWorld[parent], local, jointWorld[j]);
            }
        }
    }

    /** R = Rx * Ry * Rz with pivot: M = T(p) * R * T(-p), row-major 3x4. */
    private static void eulerToMat34(float dx, float dy, float dz, float[] p, float[] out) {
        double ax = Math.toRadians(dx), ay = Math.toRadians(dy), az = Math.toRadians(dz);
        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
        float cy = (float) Math.cos(ay), sy = (float) Math.sin(ay);
        float cz = (float) Math.cos(az), sz = (float) Math.sin(az);
        float m00 = cy * cz, m01 = -cy * sz, m02 = sy;
        float m10 = cx * sz + sx * sy * cz, m11 = cx * cz - sx * sy * sz, m12 = -sx * cy;
        float m20 = sx * sz - cx * sy * cz, m21 = sx * cz + cx * sy * sz, m22 = cx * cy;
        out[0] = m00;
        out[1] = m01;
        out[2] = m02;
        out[3] = p[0] - (m00 * p[0] + m01 * p[1] + m02 * p[2]);
        out[4] = m10;
        out[5] = m11;
        out[6] = m12;
        out[7] = p[1] - (m10 * p[0] + m11 * p[1] + m12 * p[2]);
        out[8] = m20;
        out[9] = m21;
        out[10] = m22;
        out[11] = p[2] - (m20 * p[0] + m21 * p[1] + m22 * p[2]);
    }

    private static void mul34(float[] a, float[] b, float[] out) {
        float[] t = new float[12];
        for (int r = 0; r < 3; r++) {
            t[r * 4] = a[r * 4] * b[0] + a[r * 4 + 1] * b[4] + a[r * 4 + 2] * b[8];
            t[r * 4 + 1] = a[r * 4] * b[1] + a[r * 4 + 1] * b[5] + a[r * 4 + 2] * b[9];
            t[r * 4 + 2] = a[r * 4] * b[2] + a[r * 4 + 1] * b[6] + a[r * 4 + 2] * b[10];
            t[r * 4 + 3] = a[r * 4] * b[3] + a[r * 4 + 1] * b[7] + a[r * 4 + 2] * b[11] + a[r * 4 + 3];
        }
        System.arraycopy(t, 0, out, 0, 12);
    }

    public synchronized void zoom(float factor) {
        if (factor > 0f && !Float.isNaN(factor)) {
            zoom = Math.max(.45f, Math.min(4f, zoom * factor));
            dirty = true;
        }
    }

    private static final class Face {
        final int index;
        final float depth;

        Face(int index, float depth) {
            this.index = index;
            this.depth = depth;
        }
    }

    /** Cached mesh render: geometry is rebuilt only when the pose changes. */
    public synchronized void draw(Canvas canvas, RectF bounds, int opacity) {
        int width = Math.max(1, Math.round(bounds.width()));
        int height = Math.max(1, Math.round(bounds.height()));
        if (cachedBitmap == null || cachedBitmap.isRecycled() || cachedWidth != width || cachedHeight != height || dirty) {
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) cachedBitmap.recycle();
            cachedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            cachedWidth = width;
            cachedHeight = height;
            render(new Canvas(cachedBitmap), new RectF(0, 0, width, height));
            dirty = false;
        }
        bitmapPaint.setAlpha(Math.max(0, Math.min(100, opacity)) * 255 / 100);
        canvas.drawBitmap(cachedBitmap, null, bounds, bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    private void render(Canvas canvas, RectF bounds) {
        int faceCount = positions.length / 9;
        float[][] projected = new float[positions.length / 3][3];
        double ax = Math.toRadians(rx), ay = Math.toRadians(ry);
        float ca = (float) Math.cos(ax), sa = (float) Math.sin(ax);
        float cb = (float) Math.cos(ay), sb = (float) Math.sin(ay);
        float fit = Math.min(bounds.width() / 2.65f, bounds.height() / 3.15f) * zoom;
        float cx = bounds.centerX(), cy = bounds.centerY();
        for (int v = 0; v < projected.length; v++) {
            float x = positions[v * 3], y = positions[v * 3 + 1], z = positions[v * 3 + 2];
            int jb = (v < bones.length) ? bones[v] : 0;
            if (jb < 0 || jb >= JOINT_COUNT) jb = 0;
            float[] m = jointWorld[jb];
            float sx = m[0] * x + m[1] * y + m[2] * z + m[3];
            float sy = m[4] * x + m[5] * y + m[6] * z + m[7];
            float sz = m[8] * x + m[9] * y + m[10] * z + m[11];
            sy -= .38f;
            float y1 = sy * ca - sz * sa, z1 = sy * sa + sz * ca;
            float x2 = sx * cb + z1 * sb, z2 = -sx * sb + z1 * cb;
            float camera = 4.6f - z2;
            float perspective = 4.6f / Math.max(1.2f, camera);
            projected[v][0] = cx + x2 * fit * perspective;
            projected[v][1] = cy - y1 * fit * perspective;
            projected[v][2] = z2;
        }
        Face[] faces = new Face[faceCount];
        for (int i = 0; i < faceCount; i++) {
            int v = i * 3;
            faces[i] = new Face(i, (projected[v][2] + projected[v + 1][2] + projected[v + 2][2]) / 3f);
        }
        Arrays.sort(faces, (a, b) -> Float.compare(a.depth, b.depth));
        int save = canvas.save();
        canvas.clipRect(bounds);
        Path path = new Path();
        for (Face face : faces) {
            int cbase = face.index * 12;
            path.reset();
            path.moveTo(projected[face.index * 3][0], projected[face.index * 3][1]);
            path.lineTo(projected[face.index * 3 + 1][0], projected[face.index * 3 + 1][1]);
            path.lineTo(projected[face.index * 3 + 2][0], projected[face.index * 3 + 2][1]);
            path.close();
            float[] va = projected[face.index * 3], vb = projected[face.index * 3 + 1], vc = projected[face.index * 3 + 2];
            float ux = vb[0] - va[0], uy = vb[1] - va[1], uz = vb[2] - va[2];
            float vx = vc[0] - va[0], vy = vc[1] - va[1], vz = vc[2] - va[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float light = .82f;
            if (nl > .0001f) {
                float diffuse = Math.abs((nx * .28f + ny * -.38f + nz * .88f) / nl);
                light = .68f + .32f * diffuse;
            }
            int r = Math.min(255, Math.round(((colors[cbase] + colors[cbase + 4] + colors[cbase + 8]) / 3f) * 255f * light));
            int g = Math.min(255, Math.round(((colors[cbase + 1] + colors[cbase + 5] + colors[cbase + 9]) / 3f) * 255f * light));
            int b = Math.min(255, Math.round(((colors[cbase + 2] + colors[cbase + 6] + colors[cbase + 10]) / 3f) * 255f * light));
            int a = (int) (((colors[cbase + 3] + colors[cbase + 7] + colors[cbase + 11]) / 3f) * 255f);
            paint.setColor(android.graphics.Color.argb(a, r, g, b));
            canvas.drawPath(path, paint);
        }
        canvas.restoreToCount(save);
    }

    private static class Mesh {
        final ArrayList<Float> p = new ArrayList<>(), c = new ArrayList<>();
        final ArrayList<Integer> bn = new ArrayList<>();
        int bone = 0;

        void vertex(float x, float y, float z, int color) {
            p.add(x);
            p.add(y);
            p.add(z);
            c.add(((color >> 16) & 255) / 255f);
            c.add(((color >> 8) & 255) / 255f);
            c.add((color & 255) / 255f);
            c.add(((color >>> 24) & 255) / 255f);
            bn.add(bone);
        }

        void tri(float[] a, float[] b, float[] d, int color) {
            vertex(a[0], a[1], a[2], color);
            vertex(b[0], b[1], b[2], color);
            vertex(d[0], d[1], d[2], color);
        }

        // A continuous surface made from shaped cross-sections, not stacked primitives.
        void loft(float[][] rings, int sides, int color) {
            for (int r = 0; r < rings.length - 1; r++) for (int s = 0; s < sides; s++) {
                int n = sides, s2 = (s + 1) % n;
                float[] p1 = ringPoint(rings, r, s, n), p2 = ringPoint(rings, r, s2, n);
                float[] p3 = ringPoint(rings, r + 1, s2, n), p4 = ringPoint(rings, r + 1, s, n);
                tri(p1, p2, p3, color);
                tri(p1, p3, p4, color);
            }
        }

        float[] ringPoint(float[][] rings, int r, int s, int n) {
            float[] q = rings[r], out = new float[3];
            double a = 2 * Math.PI * s / n;
            out[0] = q[0] + (float) Math.cos(a) * q[3];
            out[1] = q[1];
            out[2] = q[2] + (float) Math.sin(a) * q[5];
            return out;
        }

        // Swept tube with continuous rings through the elbow/knee instead of
        // two disconnected cylinders. Each ring follows the local limb direction.
        // segBones tags each span between control points with its own joint so
        // the limb bends at the elbow/knee instead of shearing.
        void tubePath(float[][] points, float[] radii, int sides, int color, int[] segBones) {
            if (points.length < 2 || radii.length != points.length) return;
            float[][][] rings = new float[points.length][sides][3];
            for (int i = 0; i < points.length; i++) {
                float[] before = points[Math.max(0, i - 1)];
                float[] after = points[Math.min(points.length - 1, i + 1)];
                float tx = after[0] - before[0], ty = after[1] - before[1], tz = after[2] - before[2];
                float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len < .0001f) continue;
                tx /= len;
                ty /= len;
                tz /= len;
                float rx = 0, ry = 0, rz = 1;
                if (Math.abs(tz) > .88f) {
                    rx = 0;
                    ry = 1;
                    rz = 0;
                }
                float nx = ty * rz - tz * ry, ny = tz * rx - tx * rz, nz = tx * ry - ty * rx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < .0001f) continue;
                nx /= nl;
                ny /= nl;
                nz /= nl;
                float bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
                for (int s = 0; s < sides; s++) {
                    double a = 2 * Math.PI * s / sides;
                    float co = (float) Math.cos(a) * radii[i], si = (float) Math.sin(a) * radii[i];
                    rings[i][s][0] = points[i][0] + nx * co + bx * si;
                    rings[i][s][1] = points[i][1] + ny * co + by * si;
                    rings[i][s][2] = points[i][2] + nz * co + bz * si;
                }
            }
            int saved = bone;
            for (int i = 0; i < points.length - 1; i++) {
                if (segBones != null && i < segBones.length) bone = segBones[i];
                for (int s = 0; s < sides; s++) {
                    int n = (s + 1) % sides;
                    float[] a = rings[i][s], b = rings[i][n], d = rings[i + 1][n], e = rings[i + 1][s];
                    tri(a, b, d, color);
                    tri(a, d, e, color);
                }
            }
            bone = saved;
        }

        void tube(float[] a, float[] b, float ra, float rb, int color) {
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < .0001f) return;
            float ux = dx / len, uy = dy / len, uz = dz / len;
            float ax = -uy, ay = ux, az = 0, al = (float) Math.sqrt(ax * ax + ay * ay);
            if (al < .001f) {
                ax = 1;
                ay = 0;
                al = 1;
            }
            ax /= al;
            ay /= al;
            float bx = uy * az - uz * ay, by = uz * ax - ux * az, bz = ux * ay - uy * ax;
            int steps = 8, sides = 12;
            for (int i = 0; i < steps; i++) for (int s = 0; s < sides; s++) {
                float t1 = (float) i / steps, t2 = (float) (i + 1) / steps;
                float r1 = ra + (rb - ra) * t1, r2 = ra + (rb - ra) * t2;
                float[] p1 = around(a, b, ax, ay, az, bx, by, bz, t1, r1, s, sides);
                float[] p2 = around(a, b, ax, ay, az, bx, by, bz, t1, r1, (s + 1) % sides, sides);
                float[] p3 = around(a, b, ax, ay, az, bx, by, bz, t2, r2, (s + 1) % sides, sides);
                float[] p4 = around(a, b, ax, ay, az, bx, by, bz, t2, r2, s, sides);
                tri(p1, p2, p3, color);
                tri(p1, p3, p4, color);
            }
        }

        float[] around(float[] a, float[] b, float ax, float ay, float az, float bx, float by, float bz, float t, float r, int s, int n) {
            double ang = 2 * Math.PI * s / n;
            float co = (float) Math.cos(ang) * r, si = (float) Math.sin(ang) * r;
            return new float[]{a[0] + (b[0] - a[0]) * t + ax * co + bx * si,
                    a[1] + (b[1] - a[1]) * t + ay * co + by * si, a[2] + (b[2] - a[2]) * t + az * co + bz * si};
        }

        void almond(float x, float y, float z, float w, float h, int color) {
            int steps = 16;
            float[] center = {x, y, z};
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps, b = 2 * Math.PI * (i + 1) / steps;
                tri(center, new float[]{x + w * (float) Math.cos(a), y + h * (float) Math.sin(a), z},
                        new float[]{x + w * (float) Math.cos(b), y + h * (float) Math.sin(b), z}, color);
            }
        }

        float[] positions() {
            float[] a = new float[p.size()];
            for (int i = 0; i < a.length; i++) a[i] = p.get(i);
            return a;
        }

        float[] colors() {
            float[] a = new float[c.size()];
            for (int i = 0; i < a.length; i++) a[i] = c.get(i);
            return a;
        }

        int[] bones() {
            int[] a = new int[bn.size()];
            for (int i = 0; i < a.length; i++) a[i] = bn.get(i);
            return a;
        }
    }

    private Mesh build(int model) {
        Mesh m = new Mesh();
        int skin = 0xFFFFEBDD, skinShade = 0xFFE8D3C3, joint = 0xFFC96F72;

        pivots[J_PELVIS] = new float[]{0, .30f, 0};
        pivots[J_SPINE] = new float[]{0, .55f, 0};
        pivots[J_CHEST] = new float[]{0, .84f, 0};
        pivots[J_NECK] = new float[]{0, 1.06f, 0};
        pivots[J_HEAD] = new float[]{0, 1.22f, 0};

        // Smooth bald anime mannequin head: rounded cranium, tapered jaw and chin.
        float[][] head = {
                {0, 1.17f, 0, .105f, .10f, .105f},
                {0, 1.22f, 0, .17f, .14f, .15f},
                {0, 1.31f, 0, .225f, .19f, .19f},
                {0, 1.42f, 0, .255f, .22f, .215f},
                {0, 1.55f, -.005f, .26f, .23f, .22f},
                {0, 1.67f, -.012f, .225f, .20f, .195f},
                {0, 1.74f, -.012f, .14f, .13f, .13f},
                {0, 1.77f, -.012f, .035f, .04f, .04f}
        };
        m.bone = J_HEAD;
        m.loft(head, 32, skin);

        // Small ears, kept simple like the supplied construction mannequin.
        m.bone = J_HEAD;
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? -1 : 1;
            m.tube(new float[]{s * .245f, 1.39f, 0}, new float[]{s * .275f, 1.43f, 0}, .035f, .026f, skin);
        }

        // Neck blends into the shoulder line.
        m.bone = J_NECK;
        m.tube(new float[]{0, 1.04f, 0}, new float[]{0, 1.25f, 0}, .078f, .082f, skin);

        // Bare, neutral anatomy mannequin torso in three rigged sections sharing
        // boundary rings so the spine and chest bend without cracking.
        float[][] pelvis = {
                {0, .20f, 0, .245f, .18f, .17f},
                {0, .28f, 0, .285f, .19f, .18f},
                {0, .39f, 0, .275f, .185f, .18f},
                {0, .49f, 0, .225f, .16f, .16f}
        };
        float[][] spine = {
                {0, .49f, 0, .225f, .16f, .16f},
                {0, .61f, 0, .185f, .145f, .145f},
                {0, .72f, 0, .205f, .16f, .15f}
        };
        float[][] chest = {
                {0, .72f, 0, .205f, .16f, .15f},
                {0, .84f, 0, .245f, .18f, .16f},
                {0, .96f, 0, .305f, .18f, .16f},
                {0, 1.035f, 0, .245f, .15f, .14f},
                {0, 1.08f, 0, .15f, .12f, .12f}
        };
        m.bone = J_PELVIS;
        m.loft(pelvis, 32, skin);
        m.bone = J_SPINE;
        m.loft(spine, 32, skin);
        m.bone = J_CHEST;
        m.loft(chest, 32, skin);

        // Subtle clavicle and shoulder contours, not clothing seams.
        m.bone = J_CHEST;
        m.tube(new float[]{-.235f, .99f, .105f}, new float[]{0, .94f, .155f}, .009f, .006f, skinShade);
        m.tube(new float[]{.235f, .99f, .105f}, new float[]{0, .94f, .155f}, .009f, .006f, skinShade);

        // Arms follow a natural slight inward curve; each is one continuous surface.
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? -1 : 1;
            int jShoulder = side == 0 ? J_SHOULDER_L : J_SHOULDER_R;
            int jElbow = side == 0 ? J_ELBOW_L : J_ELBOW_R;
            int jWrist = side == 0 ? J_WRIST_L : J_WRIST_R;
            float[] shoulder = {s * .285f, .94f, 0};
            float[] upperMid = {s * .39f, .79f, .005f};
            float[] elbow = model == 1 ? new float[]{s * .57f, .91f, 0} : model == 2 ? new float[]{s * .49f, .62f, .02f} : new float[]{s * .48f, .62f, 0};
            float[] wrist = model == 1 ? new float[]{s * .82f, .88f, 0} : model == 2 ? new float[]{s * .40f, .34f, .08f} : new float[]{s * .65f, .30f, .025f};
            float[] foreMid = {elbow[0] + (wrist[0] - elbow[0]) * .45f,
                    elbow[1] + (wrist[1] - elbow[1]) * .45f,
                    elbow[2] + (wrist[2] - elbow[2]) * .45f};
            pivots[jShoulder] = shoulder.clone();
            pivots[jElbow] = elbow.clone();
            pivots[jWrist] = wrist.clone();
            m.tubePath(new float[][]{shoulder, upperMid, elbow, foreMid, wrist},
                    new float[]{.105f, .091f, .067f, .055f, .038f}, 20, skin,
                    new int[]{jShoulder, jShoulder, jElbow, jElbow});

            // Palm and individually tapered fingers, simplified for posing reference.
            m.bone = jWrist;
            float hx = wrist[0], hy = wrist[1] - .035f, hz = wrist[2] + .012f;
            m.tube(new float[]{hx, hy + .035f, hz}, new float[]{hx, hy - .045f, hz + .012f}, .048f, .038f, skin);
            for (int finger = 0; finger < 4; finger++) {
                float fx = hx + s * ((finger - 1.5f) * .022f);
                float length = (finger == 1 || finger == 2) ? .105f : .088f;
                m.tube(new float[]{fx, hy - .025f, hz + .014f},
                        new float[]{fx + s * .003f, hy - length, hz + .016f}, .012f, .005f, skin);
            }
            m.tube(new float[]{hx - s * .035f, hy + .005f, hz},
                    new float[]{hx - s * .075f, hy - .045f, hz + .02f}, .016f, .006f, skin);
        }

        // Legs use a continuous tapered silhouette with shaped thighs and calves.
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? -1 : 1;
            int jHip = side == 0 ? J_HIP_L : J_HIP_R;
            int jKnee = side == 0 ? J_KNEE_L : J_KNEE_R;
            int jAnkle = side == 0 ? J_ANKLE_L : J_ANKLE_R;
            float[] hip = {s * .155f, .25f, 0};
            float[] thighMid = {s * .18f, -.015f, .005f};
            float[] knee = model == 2 ? new float[]{s * .40f, -.15f, .10f} : new float[]{s * .18f, -.30f, 0};
            float[] calfMid = model == 2 ? new float[]{s * .45f, -.32f, .11f} : new float[]{s * .19f, -.59f, .015f};
            float[] ankle = model == 2 ? new float[]{s * .48f, -.48f, .12f} : new float[]{s * .19f, -.91f, .015f};
            pivots[jHip] = hip.clone();
            pivots[jKnee] = knee.clone();
            pivots[jAnkle] = ankle.clone();
            m.tubePath(new float[][]{hip, thighMid, knee, calfMid, ankle},
                    new float[]{.145f, .132f, .085f, .073f, .047f}, 20, skin,
                    new int[]{jHip, jHip, jKnee, jKnee});

            // Simple foot volume and arch, with no shoe overlay.
            m.bone = jAnkle;
            float[] heel = {ankle[0], ankle[1] - .035f, ankle[2] + .015f};
            float[] toe = model == 2 ? new float[]{s * .48f, -.54f, .22f} : new float[]{s * .19f, -.985f, .13f};
            m.tube(heel, toe, .052f, .035f, skin);
        }

        // Small, unobtrusive joint markers indicate the pose pivot locations.
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? -1 : 1;
            int jShoulder = side == 0 ? J_SHOULDER_L : J_SHOULDER_R;
            int jElbow = side == 0 ? J_ELBOW_L : J_ELBOW_R;
            int jWrist = side == 0 ? J_WRIST_L : J_WRIST_R;
            int jHip = side == 0 ? J_HIP_L : J_HIP_R;
            int jKnee = side == 0 ? J_KNEE_L : J_KNEE_R;
            int jAnkle = side == 0 ? J_ANKLE_L : J_ANKLE_R;
            m.bone = jShoulder;
            m.almond(s * .285f, .94f, .105f, .018f, .018f, joint);
            m.bone = jElbow;
            m.almond(pivots[jElbow][0], pivots[jElbow][1], pivots[jElbow][2] + .065f, .014f, .014f, joint);
            m.bone = jWrist;
            m.almond(pivots[jWrist][0], pivots[jWrist][1], pivots[jWrist][2] + .045f, .012f, .012f, joint);
            m.bone = jHip;
            m.almond(s * .155f, .25f, .15f, .016f, .016f, joint);
            m.bone = jKnee;
            m.almond(pivots[jKnee][0], pivots[jKnee][1], pivots[jKnee][2] + .08f, .013f, .013f, joint);
            m.bone = jAnkle;
            m.almond(pivots[jAnkle][0], pivots[jAnkle][1], pivots[jAnkle][2] + .05f, .011f, .011f, joint);
        }
        updateJointMatrices();
        return m;
    }
}
