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
 * Mesh and pose stay live; every frame is projected into the canvas stack.
 */
public final class AnimeBaseScene {
    private final Mesh mesh;
    private final float[] positions, colors;
    private float rx=-8f, ry=18f, zoom=1f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap cachedBitmap;
    private int cachedWidth=0,cachedHeight=0;
    private boolean dirty=true;

    public AnimeBaseScene(int model) {
        mesh=build(model);
        positions=mesh.positions();
        colors=mesh.colors();
        paint.setStyle(Paint.Style.FILL);
    }

    public synchronized void rotate(float dx,float dy) {
        rx=Math.max(-85f,Math.min(85f,rx+dx));
        ry=(ry+dy)%360f;
        dirty=true;
    }
    public synchronized void zoom(float factor) {
        if(factor>0f&&!Float.isNaN(factor)) { zoom=Math.max(.45f,Math.min(4f,zoom*factor)); dirty=true; }
    }

    private static final class Face {
        final int index;
        final float depth;
        Face(int index,float depth){this.index=index;this.depth=depth;}
    }

    /** Cached mesh render: geometry is rebuilt only when the pose changes. */
    public synchronized void draw(Canvas canvas, RectF bounds, int opacity) {
        int width=Math.max(1,Math.round(bounds.width()));
        int height=Math.max(1,Math.round(bounds.height()));
        if(cachedBitmap==null || cachedBitmap.isRecycled() || cachedWidth!=width || cachedHeight!=height || dirty) {
            if(cachedBitmap!=null&&!cachedBitmap.isRecycled()) cachedBitmap.recycle();
            cachedBitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            cachedWidth=width; cachedHeight=height;
            render(new Canvas(cachedBitmap),new RectF(0,0,width,height));
            dirty=false;
        }
        bitmapPaint.setAlpha(Math.max(0,Math.min(100,opacity))*255/100);
        canvas.drawBitmap(cachedBitmap,null,bounds,bitmapPaint);
        bitmapPaint.setAlpha(255);
    }

    private void render(Canvas canvas, RectF bounds) {
        int faceCount=positions.length/9;
        float[][] projected=new float[positions.length/3][3];
        double ax=Math.toRadians(rx), ay=Math.toRadians(ry);
        float ca=(float)Math.cos(ax),sa=(float)Math.sin(ax);
        float cb=(float)Math.cos(ay),sb=(float)Math.sin(ay);
        float fit=Math.min(bounds.width()/2.65f,bounds.height()/3.15f)*zoom;
        float cx=bounds.centerX(), cy=bounds.centerY();
        for(int v=0;v<projected.length;v++){
            float x=positions[v*3], y=positions[v*3+1]-.38f, z=positions[v*3+2];
            float y1=y*ca-z*sa, z1=y*sa+z*ca;
            float x2=x*cb+z1*sb, z2=-x*sb+z1*cb;
            float camera=4.6f-z2;
            float perspective=4.6f/Math.max(1.2f,camera);
            projected[v][0]=cx+x2*fit*perspective;
            projected[v][1]=cy-y1*fit*perspective;
            projected[v][2]=z2;
        }
        Face[] faces=new Face[faceCount];
        for(int i=0;i<faceCount;i++){
            int v=i*3;
            faces[i]=new Face(i,(projected[v][2]+projected[v+1][2]+projected[v+2][2])/3f);
        }
        Arrays.sort(faces,(a,b)->Float.compare(a.depth,b.depth));
        int save=canvas.save();
        canvas.clipRect(bounds);
        Path path=new Path();
        for(Face face:faces){
            int cbase=face.index*12;
            path.reset();
            path.moveTo(projected[face.index*3][0],projected[face.index*3][1]);
            path.lineTo(projected[face.index*3+1][0],projected[face.index*3+1][1]);
            path.lineTo(projected[face.index*3+2][0],projected[face.index*3+2][1]);
            path.close();
            int r=(int)(((colors[cbase]+colors[cbase+4]+colors[cbase+8])/3f)*255f);
            int g=(int)(((colors[cbase+1]+colors[cbase+5]+colors[cbase+9])/3f)*255f);
            int b=(int)(((colors[cbase+2]+colors[cbase+6]+colors[cbase+10])/3f)*255f);
            int a=(int)(((colors[cbase+3]+colors[cbase+7]+colors[cbase+11])/3f)*255f);
            paint.setColor(android.graphics.Color.argb(a,r,g,b));
            canvas.drawPath(path,paint);
        }
        canvas.restoreToCount(save);
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
        // Swept tube with continuous rings through the elbow/knee instead of
        // two disconnected cylinders. Each ring follows the local limb direction.
        void tubePath(float[][] points,float[] radii,int sides,int color) {
            if(points.length<2 || radii.length!=points.length) return;
            float[][][] rings=new float[points.length][sides][3];
            for(int i=0;i<points.length;i++) {
                float[] before=points[Math.max(0,i-1)];
                float[] after=points[Math.min(points.length-1,i+1)];
                float tx=after[0]-before[0],ty=after[1]-before[1],tz=after[2]-before[2];
                float len=(float)Math.sqrt(tx*tx+ty*ty+tz*tz);
                if(len<.0001f) continue;
                tx/=len; ty/=len; tz/=len;
                float rx=0,ry=0,rz=1;
                if(Math.abs(tz)>.88f){rx=0;ry=1;rz=0;}
                float nx=ty*rz-tz*ry,ny=tz*rx-tx*rz,nz=tx*ry-ty*rx;
                float nl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(nl<.0001f) continue;
                nx/=nl;ny/=nl;nz/=nl;
                float bx=ty*nz-tz*ny,by=tz*nx-tx*nz,bz=tx*ny-ty*nx;
                for(int s=0;s<sides;s++) {
                    double a=2*Math.PI*s/sides;
                    float co=(float)Math.cos(a)*radii[i],si=(float)Math.sin(a)*radii[i];
                    rings[i][s][0]=points[i][0]+nx*co+bx*si;
                    rings[i][s][1]=points[i][1]+ny*co+by*si;
                    rings[i][s][2]=points[i][2]+nz*co+bz*si;
                }
            }
            for(int i=0;i<points.length-1;i++) for(int s=0;s<sides;s++) {
                int n=(s+1)%sides;
                float[] a=rings[i][s],b=rings[i][n],d=rings[i+1][n],e=rings[i+1][s];
                tri(a,b,d,color); tri(a,d,e,color);
            }
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


        private static Mesh build(int model) {
            Mesh m=new Mesh();
            int skin=0xFFFFEBDD, skinShade=0xFFE8D3C3, joint=0xFFC96F72;
            int white=0xFFFFF8F1;

            // Smooth bald anime mannequin head: rounded cranium, tapered jaw and chin.
            float[][] head={
                {0,1.17f,0,.105f,.10f,.105f},
                {0,1.22f,0,.17f,.14f,.15f},
                {0,1.31f,0,.225f,.19f,.19f},
                {0,1.42f,0,.255f,.22f,.215f},
                {0,1.55f,-.005f,.26f,.23f,.22f},
                {0,1.67f,-.012f,.225f,.20f,.195f},
                {0,1.74f,-.012f,.14f,.13f,.13f},
                {0,1.77f,-.012f,.035f,.04f,.04f}
            };
            m.loft(head,32,skin);

            // Small ears, kept simple like the supplied construction mannequin.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                m.tube(new float[]{s*.245f,1.39f,0},new float[]{s*.275f,1.43f,0},.035f,.026f,skin);
            }

            // Neck blends into the shoulder line.
            m.tube(new float[]{0,1.04f,0},new float[]{0,1.25f,0},.078f,.082f,skin);

            // Bare, neutral anatomy mannequin torso: broad shoulder/rib cage,
            // gently narrowed waist and a smooth pelvis silhouette.
            float[][] torso={
                {0,.20f,0,.245f,.18f,.17f},
                {0,.28f,0,.285f,.19f,.18f},
                {0,.39f,0,.275f,.185f,.18f},
                {0,.49f,0,.225f,.16f,.16f},
                {0,.61f,0,.185f,.145f,.145f},
                {0,.72f,0,.205f,.16f,.15f},
                {0,.84f,0,.245f,.18f,.16f},
                {0,.96f,0,.305f,.18f,.16f},
                {0,1.035f,0,.245f,.15f,.14f},
                {0,1.08f,0,.15f,.12f,.12f}
            };
            m.loft(torso,32,skin);

            // Subtle clavicle and shoulder contours, not clothing seams.
            m.tube(new float[]{-.235f,.99f,.105f},new float[]{0,.94f,.155f},.009f,.006f,skinShade);
            m.tube(new float[]{.235f,.99f,.105f},new float[]{0,.94f,.155f},.009f,.006f,skinShade);

            // Arms follow a natural slight inward curve; each is one continuous surface.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                float[] shoulder={s*.285f,.94f,0};
                float[] upperMid={s*.39f,.79f,.005f};
                float[] elbow=model==1?new float[]{s*.57f,.91f,0}:model==2?new float[]{s*.49f,.62f,.02f}:new float[]{s*.48f,.62f,0};
                float[] foreMid={model==1?s*.69f:.0f,model==1?.91f:.45f,.015f};
                float[] wrist=model==1?new float[]{s*.82f,.88f,0}:model==2?new float[]{s*.40f,.34f,.08f}:new float[]{s*.65f,.30f,.025f};
                m.tubePath(new float[][]{shoulder,upperMid,elbow,foreMid,wrist},
                    new float[]{.105f,.091f,.067f,.055f,.038f},20,skin);

                // Palm and individually tapered fingers, simplified for posing reference.
                float hx=wrist[0],hy=wrist[1]-.035f,hz=wrist[2]+.012f;
                m.tube(new float[]{hx,hy+.035f,hz},new float[]{hx,hy-.045f,hz+.012f},.048f,.038f,skin);
                for(int finger=0;finger<4;finger++){
                    float fx=hx+s*((finger-1.5f)*.022f);
                    float length=(finger==1||finger==2)?.105f:.088f;
                    m.tube(new float[]{fx,hy-.025f,hz+.014f},
                        new float[]{fx+s*.003f,hy-length,hz+.016f},.012f,.005f,skin);
                }
                m.tube(new float[]{hx-s*.035f,hy+.005f,hz},
                    new float[]{hx-s*.075f,hy-.045f,hz+.02f},.016f,.006f,skin);
            }

            // Legs use a continuous tapered silhouette with shaped thighs and calves.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                float[] hip={s*.155f,.25f,0};
                float[] thighMid={s*.18f,-.015f,.005f};
                float[] knee=model==2?new float[]{s*.40f,-.15f,.10f}:new float[]{s*.18f,-.30f,0};
                float[] calfMid=model==2?new float[]{s*.45f,-.32f,.11f}:new float[]{s*.19f,-.59f,.015f};
                float[] ankle=model==2?new float[]{s*.48f,-.48f,.12f}:new float[]{s*.19f,-.91f,.015f};
                m.tubePath(new float[][]{hip,thighMid,knee,calfMid,ankle},
                    new float[]{.145f,.132f,.085f,.073f,.047f},20,skin);

                // Simple foot volume and arch, with no shoe overlay.
                float[] heel={ankle[0],ankle[1]-.035f,ankle[2]+.015f};
                float[] toe=model==2?new float[]{s*.48f,-.54f,.22f}:new float[]{s*.19f,-.985f,.13f};
                m.tube(heel,toe,.052f,.035f,skin);
            }

            // Small, unobtrusive joint markers indicate the pose pivot locations.
            for(int side=0;side<2;side++){
                float s=side==0?-1:1;
                float[] shoulder={s*.285f,.94f,.105f};
                float[] elbow=model==1?new float[]{s*.57f,.91f,.065f}:model==2?new float[]{s*.49f,.62f,.085f}:new float[]{s*.48f,.62f,.065f};
                float[] wrist=model==1?new float[]{s*.82f,.88f,.045f}:model==2?new float[]{s*.40f,.34f,.12f}:new float[]{s*.65f,.30f,.065f};
                m.almond(shoulder[0],shoulder[1],shoulder[2],.018f,.018f,joint);
                m.almond(elbow[0],elbow[1],elbow[2],.014f,.014f,joint);
                m.almond(wrist[0],wrist[1],wrist[2],.012f,.012f,joint);
            }
            return m;
        }

}
