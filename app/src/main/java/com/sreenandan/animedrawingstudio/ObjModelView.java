package com.sreenandan.animedrawingstudio;

import android.content.Context;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Lightweight OBJ triangle viewer using the Android OpenGL ES 1.0 pipeline. */
public class ObjModelView extends GLSurfaceView {
    private final ModelRenderer modelRenderer;
    private float previousX, previousY, previousDistance;
    private boolean pinching;

    public ObjModelView(Context context, Uri uri) throws Exception {
        super(context);
        setEGLContextClientVersion(1);
        modelRenderer = new ModelRenderer(readObj(context, uri));
        setRenderer(modelRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int count = event.getPointerCount();
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && count >= 2) {
            previousDistance = distance(event);
            pinching = true;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (count >= 2) {
                float d = distance(event);
                if (previousDistance > 0) modelRenderer.zoom(d / previousDistance);
                previousDistance = d;
                pinching = true;
            } else if (!pinching) {
                float x = event.getX(), y = event.getY();
                modelRenderer.rotate((y - previousY) * 0.55f, (x - previousX) * 0.55f);
                previousX = x;
                previousY = y;
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            previousX = event.getX();
            previousY = event.getY();
            pinching = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            pinching = false;
            previousDistance = 0;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            pinching = false;
            performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private float distance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float[] readObj(Context context, Uri uri) throws Exception {
        ArrayList<float[]> positions = new ArrayList<>();
        ArrayList<Float> triangles = new ArrayList<>();
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IllegalArgumentException("The selected file could not be opened.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) positions.add(new float[]{
                            Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                } else if (line.startsWith("f ")) {
                    String[] face = line.substring(2).trim().split("\\s+");
                    if (face.length < 3) continue;
                    int first = index(face[0], positions.size());
                    for (int i = 1; i < face.length - 1; i++) {
                        addVertex(triangles, positions, first);
                        addVertex(triangles, positions, index(face[i], positions.size()));
                        addVertex(triangles, positions, index(face[i + 1], positions.size()));
                    }
                }
            }
        }
        if (triangles.isEmpty()) throw new IllegalArgumentException("No usable OBJ faces were found.");
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < triangles.size(); i += 3) {
            float x = triangles.get(i), y = triangles.get(i + 1), z = triangles.get(i + 2);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float extent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (extent < 0.00001f) extent = 1f;
        float scale = 2f / extent;
        float[] result = new float[triangles.size()];
        for (int i = 0; i < result.length; i += 3) {
            result[i] = (triangles.get(i) - cx) * scale;
            result[i + 1] = (triangles.get(i + 1) - cy) * scale;
            result[i + 2] = (triangles.get(i + 2) - cz) * scale;
        }
        return result;
    }

    private static int index(String token, int size) {
        int raw = Integer.parseInt(token.split("/")[0]);
        int result = raw > 0 ? raw - 1 : size + raw;
        if (result < 0 || result >= size)
            throw new IllegalArgumentException("The OBJ file contains an invalid face index.");
        return result;
    }

    private static void addVertex(ArrayList<Float> out, ArrayList<float[]> positions, int index) {
        float[] v = positions.get(index);
        out.add(v[0]); out.add(v[1]); out.add(v[2]);
    }

    private static class ModelRenderer implements Renderer {
        private final FloatBuffer vertexBuffer;
        private final int vertexCount;
        private float angleX = -12f, angleY = 25f, zoom = 1f;

        ModelRenderer(float[] vertices) {
            vertexCount = vertices.length / 3;
            ByteBuffer bytes = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder());
            vertexBuffer = bytes.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);
        }

        synchronized void rotate(float x, float y) {
            angleX = Math.max(-89f, Math.min(89f, angleX + x));
            angleY = (angleY + y) % 360f;
        }

        synchronized void zoom(float factor) {
            if (factor > 0 && !Float.isNaN(factor))
                zoom = Math.max(0.35f, Math.min(5f, zoom * factor));
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            gl.glClearColor(0.91f, 0.97f, 0.99f, 1f);
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            gl.glViewport(0, 0, width, height);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            GLU.gluPerspective(gl, 42f, (float) width / Math.max(1, height), 0.1f, 100f);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
        }

        @Override public synchronized void onDrawFrame(GL10 gl) {
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            gl.glLoadIdentity();
            GLU.gluLookAt(gl, 0f, 0f, 4.2f, 0f, 0f, 0f, 0f, 1f, 0f);
            gl.glRotatef(angleX, 1f, 0f, 0f);
            gl.glRotatef(angleY, 0f, 1f, 0f);
            gl.glScalef(zoom, zoom, zoom);
            gl.glColor4f(0.18f, 0.52f, 0.82f, 1f);
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLES, 0, vertexCount);
        }
    }
}
