package com.sreenandan.animedrawingstudio;

import android.content.Context;
import android.opengl.GLSurfaceView;
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

    public AnimeBaseView(Context context,int model) {
        super(context);
        setEGLContextClientVersion(1);
        renderer=new BaseRenderer(model);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
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
        final ArrayList<Float> p=new ArrayList<>();
        final ArrayList<Float> c=new ArrayList<>();
        void vertex(float x,float y,float z,int color) {
            p.add(x);p.add(y);p.add(z);
            c.add(((color>>16)&255)/255f);
            c.add(((color>>8)&255)/255f);
            c.add((color&255)/255f);
            c.add(((color>>>24)&255)/255f);
        }
        void tri(float[] a,float[] b,float[] d,int color) {
            vertex(a[0],a[1],a[2],color);vertex(b[0],b[1],b[2],color);vertex(d[0],d[1],d[2],color);
        }
        void ellipsoid(float x,float y,float z,float rx,float ry,float rz,int color) {
            int rings=10,sectors=16;
            for(int r=0;r<rings;r++) {
                double a1=-Math.PI/2+Math.PI*r/rings,a2=-Math.PI/2+Math.PI*(r+1)/rings;
                for(int s=0;s<sectors;s++) {
                    double b1=2*Math.PI*s/sectors,b2=2*Math.PI*(s+1)/sectors;
                    float[] v1=point(x,y,z,rx,ry,rz,a1,b1);
                    float[] v2=point(x,y,z,rx,ry,rz,a2,b1);
                    float[] v3=point(x,y,z,rx,ry,rz,a2,b2);
                    float[] v4=point(x,y,z,rx,ry,rz,a1,b2);
                    tri(v1,v2,v3,color);tri(v1,v3,v4,color);
                }
            }
        }
        float[] point(float x,float y,float z,float rx,float ry,float rz,double a,double b) {
            return new float[]{x+(float)(rx*Math.cos(a)*Math.cos(b)),
                    y+(float)(ry*Math.sin(a)),z+(float)(rz*Math.cos(a)*Math.sin(b))};
        }
        void limb(float[] a,float[] b,float radiusA,float radiusB,int color) {
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(len<0.0001f)return;
            float ux=dx/len,uy=dy/len,uz=dz/len;
            float ax=-uy,ay=ux,az=0;
            float al=(float)Math.sqrt(ax*ax+ay*ay+az*az);
            if(al<0.001f){ax=1;ay=0;az=0;al=1;}
            ax/=al;ay/=al;az/=al;
            float bx=uy*az-uz*ay,by=uz*ax-ux*az,bz=ux*ay-uy*ax;
            int sides=12;
            for(int i=0;i<sides;i++) {
                double t1=2*Math.PI*i/sides,t2=2*Math.PI*(i+1)/sides;
                float[] p1=ring(a,ax,ay,az,bx,by,bz,radiusA,t1);
                float[] p2=ring(a,ax,ay,az,bx,by,bz,radiusA,t2);
                float[] p3=ring(b,ax,ay,az,bx,by,bz,radiusB,t2);
                float[] p4=ring(b,ax,ay,az,bx,by,bz,radiusB,t1);
                tri(p1,p2,p3,color);tri(p1,p3,p4,color);
            }
            ellipsoid(a[0],a[1],a[2],radiusA,radiusA,radiusA,color);
            ellipsoid(b[0],b[1],b[2],radiusB,radiusB,radiusB,color);
        }
        float[] ring(float[] c,float ax,float ay,float az,float bx,float by,float bz,float r,double t) {
            float co=(float)Math.cos(t)*r,si=(float)Math.sin(t)*r;
            return new float[]{c[0]+ax*co+bx*si,c[1]+ay*co+by*si,c[2]+az*co+bz*si};
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
            int skin=0xFFFFC69F,skinShade=0xFFE9A77F,hair=0xFF26364D;
            int cloth=0xFF42A9D5,clothShade=0xFF2479B2,shoe=0xFF344B68;
            m.ellipsoid(0,1.40f,0,.30f,.39f,.27f,skin);
            m.ellipsoid(0,1.66f,-.015f,.315f,.20f,.285f,hair);
            m.ellipsoid(-.15f,1.48f,.205f,.12f,.18f,.09f,hair);
            m.ellipsoid(.15f,1.48f,.205f,.12f,.18f,.09f,hair);
            m.ellipsoid(-.105f,1.42f,.246f,.035f,.055f,.018f,0xFF263C62);
            m.ellipsoid(.105f,1.42f,.246f,.035f,.055f,.018f,0xFF263C62);
            m.ellipsoid(0,1.30f,.255f,.045f,.018f,.018f,skinShade);
            m.limb(new float[]{0,1.02f,0},new float[]{0,1.13f,0},.105f,.105f,skin);
            m.ellipsoid(0,.66f,0,.32f,.43f,.205f,cloth);
            m.ellipsoid(0,.20f,0,.27f,.22f,.18f,clothShade);

            float[][] shoulder={{-.29f,.91f,0},{.29f,.91f,0}};
            float[][] elbow=new float[2][3],wrist=new float[2][3];
            for(int side=0;side<2;side++) {
                float s=side==0?-1:1;
                if(model==1) {
                    elbow[side]=new float[]{s*.58f,.91f,0};
                    wrist[side]=new float[]{s*.87f,.91f,0};
                } else if(model==2) {
                    elbow[side]=new float[]{s*.52f,.55f,.02f};
                    wrist[side]=new float[]{s*.40f,.25f,.08f};
                } else {
                    elbow[side]=new float[]{s*.52f,.62f,0};
                    wrist[side]=new float[]{s*.66f,.27f,.03f};
                }
                m.limb(shoulder[side],elbow[side],.105f,.075f,cloth);
                m.limb(elbow[side],wrist[side],.075f,.055f,skin);
                m.ellipsoid(wrist[side][0],wrist[side][1]-.045f,wrist[side][2],.065f,.085f,.055f,skin);
            }

            for(int side=0;side<2;side++) {
                float s=side==0?-1:1;
                float[] hip={s*.15f,.08f,0};
                float[] knee,ankle,foot;
                if(model==2) {
                    knee=new float[]{s*.43f,-.43f,.10f};
                    ankle=new float[]{s*.48f,-.80f,.12f};
                    foot=new float[]{s*.48f,-.86f,.20f};
                } else {
                    knee=new float[]{s*.18f,-.53f,0};
                    ankle=new float[]{s*.19f,-1.12f,.015f};
                    foot=new float[]{s*.19f,-1.20f,.10f};
                }
                m.limb(hip,knee,.14f,.105f,clothShade);
                m.limb(knee,ankle,.105f,.065f,skin);
                m.ellipsoid(foot[0],foot[1],foot[2],.105f,.065f,.19f,shoe);
            }
            return m;
        }

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            gl.glClearColor(.91f,.97f,.99f,1f);
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
