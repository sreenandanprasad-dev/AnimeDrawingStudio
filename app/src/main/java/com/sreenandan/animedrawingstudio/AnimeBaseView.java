package com.sreenandan.animedrawingstudio;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.graphics.PixelFormat;
import android.opengl.GLU;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Built-in, lightweight anime drawing mannequins generated from simple 3D meshes. */
public class AnimeBaseView extends GLSurfaceView {
    private final BaseRenderer renderer;
    private float lastX,lastY,lastDistance;
    private boolean pinch;
    private boolean interactionEnabled=true;

    public AnimeBaseView(Context context,int model) {
        super(context);
        setEGLContextClientVersion(1);
        setEGLConfigChooser(8,8,8,8,16,0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderMediaOverlay(true);
        setPreserveEGLContextOnPause(true);
        renderer=new BaseRenderer(model);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setInteractionEnabled(boolean enabled) {
        interactionEnabled=enabled;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(!interactionEnabled) return false;
        int n=e.getPointerCount();
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            lastX=e.getX(); lastY=e.getY(); pinch=false; return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN && n>=2) {
            lastDistance=distance(e); pinch=true; return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
            if(n>=2) {
                float d=distance(e);
                if(lastDistance>0) renderer.zoom(d/lastDistance);
                lastDistance=d; pinch=true;
            } else if(!pinch) {
                float x=e.getX(),y=e.getY();
                renderer.rotate((y-lastY)*0.55f,(x-lastX)*0.55f);
                lastX=x; lastY=y;
            }
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP) {
            pinch=false; lastDistance=0; return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP) {
            pinch=false; performClick(); return true;
        }
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}
    private float distance(MotionEvent e) {
        float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);
        return (float)Math.sqrt(dx*dx+dy*dy);
    }

    private static class Mesh {
        final ArrayList<Float> p=new ArrayList<>(), c=new ArrayList<>();
        void vertex(float x,float y,float z,int color){
            p.add(x);p.add(y);p.add(z);
            c.add(((color>>16)&255)/255f);c.add(((color>>8)&255)/255f);
            c.add((color&255)/255f);c.add(((color>>>24)&255)/255f);
        }
        void tri(float[] a,float[] b,float[] d,int color){
            vertex(a[0],a[1],a[2],color);vertex(b[0],b[1],b[2],color);vertex(d[0],d[1],d[2],color);
        }
        // A continuous surface made from shaped cross-sections, not stacked primitives.
        void loft(float[][] rings,int sides,int color){
            for(int r=0;r<rings.length-1;r++) for(int s=0;s<sides;s++){
                int n=sides, s2=(s+1)%n;
                float[] p1=ringPoint(rings,r,s,n),p2=ringPoint(rings,r,s2,n);
                float[] p3=ringPoint(rings,r+1,s2,n),p4=ringPoint(rings,r+1,s,n);
                tri(p1,p2,p3,color);tri(p1,p3,p4,color);
            }
        }
        float[] ringPoint(float[][] rings,int r,int s,int n){
            float[] q=rings[r], out=new float[3];
            double a=2*Math.PI*s/n;
            out[0]=q[0]+(float)Math.cos(a)*q[3];
            out[1]=q[1];
            out[2]=q[2]+(float)Math.sin(a)*q[5];
            return out;
        }
        void tube(float[] a,float[] b,float ra,float rb,int color){
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz); if(len<.0001f)return;
            float ux=dx/len,uy=dy/len,uz=dz/len;
            float ax=-uy,ay=ux,az=0,al=(float)Math.sqrt(ax*ax+ay*ay);
            if(al<.001f){ax=1;ay=0;al=1;} ax/=al;ay/=al;
            float bx=uy*az-uz*ay,by=uz*ax-ux*az,bz=ux*ay-uy*ax;
            int steps=8,sides=12;
            for(int i=0;i<steps;i++) for(int s=0;s<sides;s++){
                float t1=(float)i/steps,t2=(float)(i+1)/steps;
                float r1=ra+(rb-ra)*t1,r2=ra+(rb-ra)*t2;
                float[] p1=around(a,b,ax,ay,az,bx,by,bz,t1,r1,s,sides);
                float[] p2=around(a,b,ax,ay,az,bx,by,bz,t1,r1,(s+1)%sides,sides);
                float[] p3=around(a,b,ax,ay,az,bx,by,bz,t2,r2,(s+1)%sides,sides);
                float[] p4=around(a,b,ax,ay,az,bx,by,bz,t2,r2,s,sides);
                tri(p1,p2,p3,color);tri(p1,p3,p4,color);
            }
        }
        float[] around(float[] a,float[] b,float ax,float ay,float az,float bx,float by,float bz,float t,float r,int s,int n){
            double ang=2*Math.PI*s/n;float co=(float)Math.cos(ang)*r,si=(float)Math.sin(ang)*r;
            return new float[]{a[0]+(b[0]-a[0])*t+ax*co+bx*si,
                a[1]+(b[1]-a[1])*t+ay*co+by*si,a[2]+(b[2]-a[2])*t+az*co+bz*si};
        }
        void almond(float x,float y,float z,float w,float h,int color){
            int steps=16; float[] center={x,y,z};
            for(int i=0;i<steps;i++){
                double a=2*Math.PI*i/steps,b=2*Math.PI*(i+1)/steps;
                tri(center,new float[]{x+w*(float)Math.cos(a),y+h*(float)Math.sin(a),z},
                    new float[]{x+w*(float)Math.cos(b),y+h*(float)Math.sin(b),z},color);
            }
        }
        float[] positions(){float[] a=new float[p.size()];for(int i=0;i<a.length;i++)a[i]=p.get(i);return a;}
        float[] colors(){float[] a=new float[c.size()];for(int i=0;i<a.length;i++)a[i]=c.get(i);return a;}
    }

    private static class BaseRenderer implements Renderer {
        private final FloatBuffer vertices,colors;
        private final int count;
        private float rx=-8,ry=18,zoom=1f;
        BaseRenderer(int model) {
            Mesh m=build(model);
            float[] p=m.positions(),c=m.colors();count=p.length/3;
            ByteBuffer pb=ByteBuffer.allocateDirect(p.length*4).order(ByteOrder.nativeOrder());
            vertices=pb.asFloatBuffer();vertices.put(p).position(0);
            ByteBuffer cb=ByteBuffer.allocateDirect(c.length*4).order(ByteOrder.nativeOrder());
            colors=cb.asFloatBuffer();colors.put(c).position(0);
        }
        synchronized void rotate(float x,float y){rx=Math.max(-85,Math.min(85,rx+x));ry=(ry+y)%360;}
        synchronized void zoom(float f){if(f>0&&!Float.isNaN(f))zoom=Math.max(.45f,Math.min(4f,zoom*f));}

        private static Mesh build(int model) {
            Mesh m=new Mesh();
            int skin=0xFFFFC69F, shade=0xFFE9A77F, hair=0xFF26364D, hair2=0xFF405A7A;
            int top=0xFF42A9D5, topShade=0xFF2479B2, bottom=0xFF354C75, shoe=0xFF26364D;
            int white=0xFFFFF8F1, iris=0xFF55A8D8, pupil=0xFF172D4C;
            // Head silhouette: broad cranium tapering through cheeks into a manga chin.
            float[][] head={
                {0,1.18f,0,.115f,.11f,.13f},{0,1.25f,0,.205f,.16f,.19f},
                {0,1.36f,0,.255f,.22f,.225f},{0,1.49f,0,.275f,.25f,.235f},
                {0,1.62f,-.005f,.27f,.24f,.23f},{0,1.72f,-.015f,.20f,.18f,.18f},
                {0,1.77f,-.015f,.08f,.08f,.08f}};
            m.loft(head,24,skin);
            // Neck and unified torso silhouette with shoulders, ribcage, waist and pelvis.
            m.tube(new float[]{0,1.08f,0},new float[]{0,1.24f,0},.075f,.085f,skin);
            float[][] torso={
                {0,.34f,0,.20f,.15f,.16f},{0,.43f,0,.29f,.19f,.19f},
                {0,.58f,0,.255f,.18f,.18f},{0,.72f,0,.205f,.17f,.16f},
                {0,.88f,0,.255f,.19f,.17f},{0,1.00f,0,.31f,.18f,.16f},
                {0,1.08f,0,.22f,.14f,.13f}};
            m.loft(torso,24,top);
            // Pelvis and clothing form one broad manga mannequin base.
            float[][] pelvis={{0,.18f,0,.19f,.14f,.15f},{0,.25f,0,.28f,.17f,.18f},{0,.40f,0,.29f,.18f,.18f}};
            m.loft(pelvis,24,bottom);
            // Face details are shallow graphic forms on the front plane.
            for(int side=0;side<2;side++){
                float x=side==0?-.105f:.105f;
                m.almond(x,1.405f,.224f,.078f,.045f,white);
                m.almond(x,1.405f,.229f,.039f,.041f,iris);
                m.almond(x,1.402f,.233f,.020f,.032f,pupil);
                m.almond(x-.012f,1.422f,.237f,.010f,.012f,white);
                m.tube(new float[]{x-.075f,1.47f,.226f},new float[]{x+.065f,1.47f,.226f},.012f,.009f,hair);
                m.tube(new float[]{side==0?-.27f:.27f,1.37f,0},new float[]{side==0?-.25f:.25f,1.48f,0},.035f,.035f,shade);
            }
            m.tube(new float[]{0,1.36f,.235f},new float[]{0,1.33f,.247f},.012f,.006f,shade);
            m.tube(new float[]{-.025f,1.285f,.221f},new float[]{.025f,1.285f,.221f},.006f,.006f,0xFF9D5360);
            // Hair cap and tapered locks; deliberately graphic, with pointed fringe.
            float[][] cap={{0,1.51f,-.01f,.26f,.22f,.23f},{0,1.62f,-.01f,.28f,.20f,.24f},{0,1.72f,-.01f,.22f,.16f,.20f},{0,1.78f,-.01f,.06f,.06f,.07f}};
            m.loft(cap,24,hair);
            m.tube(new float[]{-.20f,1.62f,.16f},new float[]{-.10f,1.40f,.22f},.07f,.008f,hair2);
            m.tube(new float[]{-.06f,1.68f,.20f},new float[]{.015f,1.40f,.24f},.065f,.008f,hair);
            m.tube(new float[]{.12f,1.65f,.17f},new float[]{.10f,1.42f,.23f},.065f,.008f,hair2);
            m.tube(new float[]{-.25f,1.55f,-.02f},new float[]{-.24f,1.22f,-.015f},.065f,.012f,hair);
            m.tube(new float[]{.25f,1.55f,-.02f},new float[]{.24f,1.22f,-.015f},.065f,.012f,hair);
            // Collar and simple garment seams.
            m.tube(new float[]{-.17f,1.00f,.12f},new float[]{0,.88f,.19f},.025f,.008f,white);
            m.tube(new float[]{.17f,1.00f,.12f},new float[]{0,.88f,.19f},.025f,.008f,white);
            m.almond(0,.88f,.205f,.035f,.05f,0xFFE45C75);
            // Arms are tapered, curved surface tubes with softly changing widths.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                float[] shoulder={s*.27f,.96f,0};
                float[] elbow=model==1?new float[]{s*.58f,.94f,0}:model==2?new float[]{s*.49f,.62f,.02f}:new float[]{s*.50f,.66f,0};
                float[] wrist=model==1?new float[]{s*.87f,.94f,0}:model==2?new float[]{s*.40f,.34f,.08f}:new float[]{s*.66f,.31f,.03f};
                m.tube(shoulder,elbow,.105f,.068f,top);
                m.tube(elbow,wrist,.068f,.043f,skin);
                float hx=wrist[0],hy=wrist[1]-.035f,hz=wrist[2]+.015f;
                // Palm plus individually tapered fingers and an angled thumb.
                m.tube(new float[]{hx,hy+.035f,hz},new float[]{hx,hy-.045f,hz+.01f},.052f,.043f,skin);
                for(int f=0;f<4;f++){
                    float fx=hx+s*((f-1.5f)*.024f);
                    m.tube(new float[]{fx,hy-.025f,hz+.012f},new float[]{fx+s*.004f,hy-.105f-(f==1||f==2?.012f:0),hz+.015f},.014f,.006f,skin);
                }
                m.tube(new float[]{hx-s*.04f,hy+.005f,hz},new float[]{hx-s*.085f,hy-.045f,hz+.02f},.018f,.007f,skin);
            }
            // Legs: continuous tapered thigh-to-calf contours, ankles and wedge-shaped feet.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                float[] hip={s*.15f,.27f,0};
                float[] knee=model==2?new float[]{s*.43f,-.15f,.10f}:new float[]{s*.18f,-.28f,0};
                float[] ankle=model==2?new float[]{s*.48f,-.48f,.12f}:new float[]{s*.19f,-.91f,.015f};
                float[] toe=model==2?new float[]{s*.48f,-.54f,.20f}:new float[]{s*.19f,-.99f,.10f};
                m.tube(hip,knee,.125f,.085f,skin);
                m.tube(knee,ankle,.085f,.052f,skin);
                m.tube(ankle,toe,.055f,.035f,shoe);
            }
            return m;
        }

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            gl.glClearColor(0f,0f,0f,0f);
            gl.glEnable(GL10.GL_DEPTH_TEST);
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
            gl.glViewport(0,0,w,h);
            gl.glMatrixMode(GL10.GL_PROJECTION);gl.glLoadIdentity();
            GLU.gluPerspective(gl,38f,(float)w/Math.max(1,h),.1f,100f);
            gl.glMatrixMode(GL10.GL_MODELVIEW);
        }
        @Override public synchronized void onDrawFrame(GL10 gl) {
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT|GL10.GL_DEPTH_BUFFER_BIT);
            gl.glLoadIdentity();GLU.gluLookAt(gl,0,0,4.6f,0,.2f,0,0,1,0);
            gl.glRotatef(rx,1,0,0);gl.glRotatef(ry,0,1,0);gl.glScalef(zoom,zoom,zoom);
            gl.glVertexPointer(3,GL10.GL_FLOAT,0,vertices);
            gl.glColorPointer(4,GL10.GL_FLOAT,0,colors);
            gl.glDrawArrays(GL10.GL_TRIANGLES,0,count);
        }
    }
}
