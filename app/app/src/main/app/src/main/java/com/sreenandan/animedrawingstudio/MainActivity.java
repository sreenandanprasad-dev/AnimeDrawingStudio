
package com.sreenandan.animedrawingstudio;

import android.app.Activity;
import android.os.Bundle;
import android.content.ContentValues;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    DrawingView drawingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(28, 28, 35));

        TextView title = new TextView(this);
        title.setText("ANIME DRAWING STUDIO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(16, 18, 16, 18);
        root.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        addTool(tools, "Black", () -> drawingView.setColor(Color.BLACK));
        addTool(tools, "Red", () -> drawingView.setColor(Color.RED));
        addTool(tools, "Blue", () -> drawingView.setColor(Color.BLUE));
        addTool(tools, "Green", () -> drawingView.setColor(Color.GREEN));
        addTool(tools, "Eraser", () -> drawingView.setColor(Color.WHITE));
        addTool(tools, "Undo", () -> drawingView.undo());
        addTool(tools, "Clear", () -> drawingView.clear());

        scroll.addView(tools);
        root.addView(scroll);

        drawingView = new DrawingView(this);
        root.addView(drawingView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        SeekBar size = new SeekBar(this);
        size.setMax(60);
        size.setProgress(8);
        size.setPadding(16, 4, 16, 4);
        size.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    drawingView.setBrushSize(Math.max(2, p + 2));
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
        root.addView(size);

        Button save = new Button(this);
        save.setText("SAVE DRAWING AS PNG");
        save.setOnClickListener(v -> saveDrawing());
        root.addView(save);

        setContentView(root);
    }

    void addTool(LinearLayout layout, String name, Runnable action) {
        Button button = new Button(this);
        button.setText(name);
        button.setTextSize(12);
        button.setOnClickListener(v -> action.run());
        layout.addView(button);
    }

    void saveDrawing() {
        Bitmap bitmap = drawingView.getBitmap();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
            "AnimeDrawing_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/AnimeDrawingStudio");
        }

        try {
            Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) throw new Exception("Could not create image");

            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("Could not open image");

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();

            Toast.makeText(this, "Drawing saved!",
                Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    class DrawingView extends View {

        ArrayList<Path> paths = new ArrayList<>();
        ArrayList<Paint> paints = new ArrayList<>();

        Path currentPath;
        Paint currentPaint;
        int color = Color.BLACK;
        float brushSize = 10;

        public DrawingView(Activity context) {
            super(context);
            setBackgroundColor(Color.WHITE);
        }

        void setColor(int newColor) {
            color = newColor;
        }

        void setBrushSize(float size) {
            brushSize = size;
        }

        void undo() {
            if (!paths.isEmpty()) {
                paths.remove(paths.size() - 1);
                paints.remove(paints.size() - 1);
                invalidate();
            }
        }

        void clear() {
            paths.clear();
            paints.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawColor(Color.WHITE);

            for (int i = 0; i < paths.size(); i++) {
                canvas.drawPath(paths.get(i), paints.get(i));
            }

            if (currentPath != null) {
                canvas.drawPath(currentPath, currentPaint);
            }
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentPath = new Path();
                    currentPath.moveTo(x, y);

                    currentPaint = new Paint();
                    currentPaint.setColor(color);
                    currentPaint.setStrokeWidth(brushSize);
                    currentPaint.setStyle(Paint.Style.STROKE);
                    currentPaint.setStrokeCap(Paint.Cap.ROUND);
                    currentPaint.setStrokeJoin(Paint.Join.ROUND);
                    currentPaint.setAntiAlias(true);

                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (currentPath != null) {
                        currentPath.lineTo(x, y);
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (currentPath != null) {
                        paths.add(currentPath);
                        paints.add(currentPaint);
                        currentPath = null;
                        currentPaint = null;
                        invalidate();
                    }
                    return true;
            }

            return true;
        }

        Bitmap getBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(
                getWidth(), getHeight(), Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            for (int i = 0; i < paths.size(); i++) {
                canvas.drawPath(paths.get(i), paints.get(i));
            }

            return bitmap;
        }
    }
}
