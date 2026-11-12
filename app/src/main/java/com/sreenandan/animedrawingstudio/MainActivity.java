package com.sreenandan.animedrawingstudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.ContentValues;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private DrawingView drawingView;
    private int currentColor = Color.BLACK;
    private float brushSize = 8f;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(28,28,32));
        getWindow().setNavigationBarColor(Color.rgb(20,20,24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(24,24,28));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(8,8,8,8);
        addButton(toolbar, "Undo", () -> drawingView.undo());
        addButton(toolbar, "Redo", () -> drawingView.redo());
        addButton(toolbar, "Clear", () -> drawingView.clearCanvas());
        addButton(toolbar, "Save", () -> saveImage());

        drawingView = new DrawingView();
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 56));
        root.addView(drawingView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(12,8,12,10);

        LinearLayout colors = new LinearLayout(this);
        int[] palette = {Color.BLACK, Color.WHITE, Color.RED, Color.rgb(255,130,0),
                Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
                Color.MAGENTA, Color.rgb(130,70,210), Color.rgb(120,75,45)};
        for (int color : palette) {
            View swatch = new View(this);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,34,1);
            p.setMargins(2,2,2,2);
            colors.addView(swatch,p);
            swatch.setOnClickListener(v -> {
                currentColor = color;
                drawingView.setEraser(false);
            });
        }
        tools.addView(colors);

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);
        addButton(options, "Brush", () -> drawingView.setEraser(false));
        addButton(options, "Eraser", () -> drawingView.setEraser(true));
        SeekBar size = new SeekBar(this);
        size.setMax(80);
        size.setProgress(6);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushSize = Math.max(1,value);
                drawingView.setBrushSize(brushSize);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        options.addView(size,new LinearLayout.LayoutParams(0,48,1));
        tools.addView(options);
        root.addView(tools,new LinearLayout.LayoutParams(-1,104));
        setContentView(root);
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(4,0,4,0);
        parent.addView(b,new LinearLayout.LayoutParams(0,48,1));
        b.setOnClickListener(v -> action.run());
    }

    private void saveImage() {
        Bitmap bitmap = drawingView.exportBitmap();
        String name = "AnimeDrawing_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        if (Build.VERSION.SDK_INT >= 29)
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AnimeDrawingStudio");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if (uri == null) {
            Toast.makeText(this,"Could not save image",Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            Toast.makeText(this,"Saved to Pictures/AnimeDrawingStudio",Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,"Save failed: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private class DrawingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap bitmap;
        private final Canvas bitmapCanvas;
        private final ArrayList<Bitmap> undo = new ArrayList<>();
        private final ArrayList<Bitmap> redo = new ArrayList<>();
        private float lastX,lastY;
        private boolean eraser = false;

        DrawingView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            bitmap = Bitmap.createBitmap(1600,2200,Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            bitmapCanvas = new Canvas(bitmap);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(brushSize);
        }

        void setEraser(boolean value) { eraser=value; }
        void setBrushSize(float value) { brushSize=value; paint.setStrokeWidth(value); }

        void saveUndo() {
            if (undo.size() >= 20) undo.remove(0);
            undo.add(bitmap.copy(Bitmap.Config.ARGB_8888,false));
            for (Bitmap b: redo) b.recycle();
            redo.clear();
        }

        void undo() {
            if (undo.isEmpty()) return;
            redo.add(bitmap.copy(Bitmap.Config.ARGB_8888,false));
            Bitmap previous=undo.remove(undo.size()-1);
            new Canvas(bitmap).drawBitmap(previous,0,0,null);
            previous.recycle();
            invalidate();
        }

        void redo() {
            if (redo.isEmpty()) return;
            undo.add(bitmap.copy(Bitmap.Config.ARGB_8888,false));
            Bitmap next=redo.remove(redo.size()-1);
            new Canvas(bitmap).drawBitmap(next,0,0,null);
            next.recycle();
            invalidate();
        }

        void clearCanvas() {
            saveUndo();
            bitmap.eraseColor(Color.WHITE);
            invalidate();
        }

        Bitmap exportBitmap() { return bitmap.copy(Bitmap.Config.ARGB_8888,false); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(65,65,70));
            float scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());
            float left=(getWidth()-bitmap.getWidth()*scale)/2f;
            float top=(getHeight()-bitmap.getHeight()*scale)/2f;
            canvas.save();
            canvas.translate(left,top);
            canvas.scale(scale,scale);
            canvas.drawBitmap(bitmap,0,0,null);
            canvas.restore();
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            float scale=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());
            float left=(getWidth()-bitmap.getWidth()*scale)/2f;
            float top=(getHeight()-bitmap.getHeight()*scale)/2f;
            float x=(event.getX()-left)/scale;
            float y=(event.getY()-top)/scale;
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (x<0 || y<0 || x>bitmap.getWidth() || y>bitmap.getHeight()) return false;
                    saveUndo();
                    lastX=x; lastY=y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    paint.setColor(eraser?Color.WHITE:currentColor);
                    paint.setStrokeWidth(eraser?brushSize*3:brushSize);
                    bitmapCanvas.drawLine(lastX,lastY,x,y,paint);
                    lastX=x; lastY=y;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    paint.setColor(eraser?Color.WHITE:currentColor);
                    paint.setStrokeWidth(eraser?brushSize*3:brushSize);
                    bitmapCanvas.drawPoint(x,y,paint);
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
