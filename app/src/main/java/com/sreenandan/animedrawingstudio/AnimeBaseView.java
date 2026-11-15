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
            int skin=0xFFFFC69F,skinShade=0xFFE9A77F;
            int hair=0xFF26364D,hairLight=0xFF405A7A;
            int cloth=0xFF42A9D5,clothShade=0xFF2479B2;
            int skirt=0xFF354C75,shoe=0xFF26364D;
            int white=0xFFFFF8F1,iris=0xFF55A8D8,pupil=0xFF172D4C;

            // Head and jaw: a deliberately stylized anime silhouette.
            m.ellipsoid(0,1.405f,0,.285f,.365f,.255f,skin);
            m.ellipsoid(0,1.675f,-.025f,.302f,.185f,.275f,hair);
            // Side/back hair locks frame the face.
            m.ellipsoid(-.235f,1.445f,-.015f,.085f,.285f,.105f,hair);
            m.ellipsoid(.235f,1.445f,-.015f,.085f,.285f,.105f,hair);
            m.ellipsoid(-.255f,1.265f,-.005f,.055f,.17f,.07f,hairLight);
            m.ellipsoid(.255f,1.265f,-.005f,.055f,.17f,.07f,hairLight);
            // Layered fringe, with pointed locks over the forehead.
            m.ellipsoid(-.13f,1.57f,.205f,.13f,.13f,.095f,hair);
            m.ellipsoid(0,1.59f,.235f,.13f,.15f,.085f,hairLight);
            m.ellipsoid(.13f,1.56f,.205f,.13f,.13f,.095f,hair);
            m.limb(new float[]{-.20f,1.58f,.235f},new float[]{-.10f,1.43f,.275f},.055f,.012f,hair);
            m.limb(new float[]{-.04f,1.62f,.285f},new float[]{.015f,1.435f,.285f},.060f,.012f,hairLight);
            m.limb(new float[]{.14f,1.58f,.235f},new float[]{.105f,1.445f,.275f},.055f,.012f,hair);
            // Anime eyes: whites, large irises, pupils and small highlights.
            for(int side=0;side<2;side++) {
                float x=side==0?-.105f:.105f;
                m.ellipsoid(x,1.435f,.235f,.073f,.095f,.030f,white);
                m.ellipsoid(x,1.432f,.260f,.043f,.068f,.020f,iris);
                m.ellipsoid(x,1.425f,.276f,.022f,.045f,.012f,pupil);
                m.ellipsoid(x-.012f,1.458f,.288f,.012f,.018f,.008f,white);
                m.limb(new float[]{x-.068f,1.505f,.255f},new float[]{x+.055f,1.505f,.255f},.012f,.012f,hair);
                m.ellipsoid(side==0?-.285f:.285f,1.405f,0,.035f,.060f,.045f,skinShade);
            }
            // Nose and a subtle mouth.
            m.ellipsoid(0,1.365f,.267f,.018f,.025f,.012f,skinShade);
            m.limb(new float[]{-.027f,1.315f,.258f},new float[]{.027f,1.315f,.258f},.006f,.006f,0xFF9D5360);
            // Neck, sailor-style collar, fitted top and skirt.
            m.limb(new float[]{0,1.04f,0},new float[]{0,1.16f,0},.09f,.09f,skin);
            m.ellipsoid(0,.91f,0,.265f,.24f,.19f,cloth);
            m.ellipsoid(0,.765f,.005f,.285f,.23f,.20f,clothShade);
            m.limb(new float[]{-.17f,1.02f,.12f},new float[]{0,.88f,.205f},.035f,.012f,white);
            m.limb(new float[]{.17f,1.02f,.12f},new float[]{0,.88f,.205f},.035f,.012f,white);
            m.ellipsoid(0,.865f,.215f,.045f,.065f,.025f,0xFFE45C75);
            m.ellipsoid(0,.49f,0,.30f,.22f,.205f,skirt);
            m.limb(new float[]{-.22f,.39f,.12f},new float[]{.22f,.39f,.12f},.018f,.018f,0xFF8CA9C9);

            float[][] shoulder={{-.27f,.94f,0},{.27f,.94f,0}};
            float[][] elbow=new float[2][3],wrist=new float[2][3];
            for(int side=0;side<2;side++) {
                float s=side==0?-1:1;
                if(model==1) {
                    elbow[side]=new float[]{s*.58f,.94f,0};
                    wrist[side]=new float[]{s*.87f,.94f,0};
                } else if(model==2) {
                    elbow[side]=new float[]{s*.52f,.62f,.02f};
                    wrist[side]=new float[]{s*.40f,.34f,.08f};
                } else {
                    elbow[side]=new float[]{s*.52f,.66f,0};
                    wrist[side]=new float[]{s*.66f,.31f,.03f};
                }
                m.limb(shoulder[side],elbow[side],.105f,.075f,cloth);
                m.limb(elbow[side],wrist[side],.075f,.052f,skin);
                m.ellipsoid(wrist[side][0],wrist[side][1]-.035f,wrist[side][2],.065f,.075f,.052f,skin);
                // Simple separated fingers make the hands readable at drawing scale.
                for(int finger=0;finger<4;finger++) {
                    float fx=wrist[side][0]+s*((finger-1.5f)*.025f);
                    m.limb(new float[]{fx,wrist[side][1]-.075f,wrist[side][2]+.018f},
                            new float[]{fx+s*.006f,wrist[side][1]-.125f,wrist[side][2]+.02f},.012f,.008f,skin);
                }
            }

            for(int side=0;side<2;side++) {
                float s=side==0?-1:1;
                float[] hip={s*.15f,.34f,0};
                float[] knee,ankle,foot;
                if(model==2) {
                    knee=new float[]{s*.43f,-.15f,.10f};
                    ankle=new float[]{s*.48f,-.48f,.12f};
                    foot=new float[]{s*.48f,-.54f,.20f};
                } else {
                    knee=new float[]{s*.18f,-.28f,0};
                    ankle=new float[]{s*.19f,-.91f,.015f};
                    foot=new float[]{s*.19f,-.99f,.10f};
                }
                m.limb(hip,knee,.13f,.10f,skinShade);
                m.limb(knee,ankle,.095f,.062f,skin);
                m.ellipsoid(foot[0],foot[1],foot[2],.105f,.065f,.19f,shoe);
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
