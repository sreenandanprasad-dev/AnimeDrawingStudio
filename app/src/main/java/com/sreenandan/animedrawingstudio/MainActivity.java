package com.sreenandan.animedrawingstudio;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.ContentValues;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
    private int brushOpacity = 100;
    private int stabilizer = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(0,0,128));
        getWindow().setNavigationBarColor(Color.rgb(192,192,192));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(192,192,192));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(10,4,8,4);
        titleBar.setBackgroundColor(Color.rgb(0,0,128));
        TextView appTitle = new TextView(this);
        appTitle.setText("✎  Anime Drawing Studio");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(17);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(appTitle,new LinearLayout.LayoutParams(-1,38));

        LinearLayout menuBar = new LinearLayout(this);
        menuBar.setGravity(Gravity.CENTER_VERTICAL);
        menuBar.setPadding(6,0,6,0);
        menuBar.setBackgroundColor(Color.rgb(212,208,200));
        for (String menu : new String[]{"File","Edit","View","Layer","Help"}) {
            TextView item = new TextView(this);
            item.setText("  "+menu+"  ");
            item.setTextColor(Color.BLACK);
            item.setTextSize(14);
            menuBar.addView(item,new LinearLayout.LayoutParams(-2,34));
        }

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(4,4,4,4);
        toolbar.setBackgroundColor(Color.rgb(212,208,200));
        addButton(toolbar, "↶", () -> drawingView.undo());
        addButton(toolbar, "↷", () -> drawingView.redo());
        addButton(toolbar, "▤", () -> showLayers());
        addButton(toolbar, "💾", () -> saveImage());
        addButton(toolbar, "⌕", () -> drawingView.resetView());

        drawingView = new DrawingView();
        root.addView(titleBar,new LinearLayout.LayoutParams(-1,42));
        root.addView(menuBar,new LinearLayout.LayoutParams(-1,34));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 54));
        root.addView(drawingView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(8,6,8,8);
        tools.setBackgroundColor(Color.rgb(212,208,200));

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
        addButton(options, "✎ Brush", () -> drawingView.setEraser(false));
        addButton(options, "▰ Eraser", () -> drawingView.setEraser(true));
        addButton(options, "▧ Fill", () -> drawingView.setBucketTool(true));
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
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(Color.BLACK);
        options.addView(sizeLabel);
        options.addView(size,new LinearLayout.LayoutParams(0,48,1));
        tools.addView(options);

        LinearLayout opacityRow = new LinearLayout(this);
        opacityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView opacityLabel = new TextView(this);
        opacityLabel.setText("Opacity");
        opacityLabel.setTextColor(Color.BLACK);
        opacityRow.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(100);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushOpacity = Math.max(1,value);
                drawingView.setBrushOpacity(brushOpacity);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        opacityRow.addView(opacity,new LinearLayout.LayoutParams(0,44,1));
        tools.addView(opacityRow);
        LinearLayout stabilizerRow = new LinearLayout(this);
        stabilizerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView stabilizerLabel = new TextView(this);
        stabilizerLabel.setText("Stabilizer 0");
        stabilizerLabel.setTextColor(Color.BLACK);
        stabilizerRow.addView(stabilizerLabel);
        SeekBar stabilizerBar = new SeekBar(this);
        stabilizerBar.setMax(100);
        stabilizerBar.setProgress(0);
        stabilizerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                stabilizer = value;
                stabilizerLabel.setText("Stabilizer " + value);
                drawingView.setStabilizer(value);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        stabilizerRow.addView(stabilizerBar,new LinearLayout.LayoutParams(0,44,1));
        tools.addView(stabilizerRow);
        root.addView(tools,new LinearLayout.LayoutParams(-1,192));
        setContentView(root);
    }

    private GradientDrawable bevel(boolean pressed) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(212,208,200));
        d.setCornerRadius(2);
        d.setStroke(pressed ? 2 : 1, Color.rgb(90,90,90));
        return d;
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.BLACK);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT);
        b.setPadding(3,0,3,0);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},bevel(true));
        states.addState(new int[]{},bevel(false));
        b.setBackground(states);
        b.setMinHeight(42);
        b.setMinimumHeight(42);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,46,1);
        lp.setMargins(2,1,2,1);
        parent.addView(b,lp);
        b.setOnClickListener(v -> action.run());
    }

    private void showLayers() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18,8,18,8);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Layers")
                .setView(panel)
                .setNegativeButton("Close",null)
                .create();

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            @Override public void run() {
                rows.removeAllViews();
                ArrayList<String> names = drawingView.getLayerNames();
                for (int i=names.size()-1;i>=0;i--) {
                    final int index=i;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView select = new TextView(MainActivity.this);
                    select.setText((drawingView.getActiveLayer()==i ? "● " : "○ ") + names.get(i));
                    select.setTextSize(16);
                    select.setPadding(8,12,8,12);
                    select.setTextColor(Color.BLACK);
                    row.addView(select,new LinearLayout.LayoutParams(0,48,1));
                    select.setOnClickListener(v -> {
                        drawingView.setActiveLayer(index);
                        refresh[0].run();
                    });
                    CheckBox visible = new CheckBox(MainActivity.this);
                    visible.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
                    visible.setChecked(drawingView.isLayerVisible(index));
                    visible.setOnCheckedChangeListener((button,checked) -> drawingView.setLayerVisible(index,checked));
                    row.addView(visible);
                    rows.addView(row);
                    View line = new View(MainActivity.this);
                    line.setBackgroundColor(Color.DKGRAY);
                    rows.addView(line,new LinearLayout.LayoutParams(-1,1));
                }
            }
        };
        refresh[0].run();

        LinearLayout actions = new LinearLayout(this);
        addButton(actions,"+ Add",() -> {
            if (drawingView.addLayer()) refresh[0].run();
            else Toast.makeText(this,"Layer limit reached (6)",Toast.LENGTH_SHORT).show();
        });
        addButton(actions,"Rename",() -> renameLayer(refresh[0]));
        addButton(actions,"Delete",() -> {
            if (!drawingView.deleteActiveLayer())
                Toast.makeText(this,"Keep at least one layer",Toast.LENGTH_SHORT).show();
            refresh[0].run();
        });
        panel.addView(actions,new LinearLayout.LayoutParams(-1,52));
        dialog.setOnShowListener(v -> dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels*0.92),
                (int)(getResources().getDisplayMetrics().heightPixels*0.72)));
        dialog.show();
        dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels*0.92),
                (int)(getResources().getDisplayMetrics().heightPixels*0.72));
    }

    private void renameLayer(Runnable refresh) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(drawingView.getActiveLayerName());
        new AlertDialog.Builder(this).setTitle("Rename layer").setView(input)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w) -> {
                    String name=input.getText().toString().trim();
                    if (!name.isEmpty()) drawingView.renameActiveLayer(name);
                    refresh.run();
                }).show();
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
            bitmap.recycle();
            Toast.makeText(this,"Merged image saved to Pictures/AnimeDrawingStudio",Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,"Save failed: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private class DrawingView extends View {
        private class Layer {
            Bitmap bitmap;
            String name;
            boolean visible=true;
            Layer(String label) {
                name=label;
                bitmap=Bitmap.createBitmap(1600,2200,Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.TRANSPARENT);
            }
        }
        private class Snapshot {
            int layerIndex;
            Bitmap bitmap;
            Snapshot(int index,Bitmap image) { layerIndex=index; bitmap=image; }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path currentPath = new Path();
        private boolean strokeInProgress=false;
        private int strokeColor=Color.BLACK;
        private int strokeAlpha=255;
        private float strokeWidth=8f;
        private int activeStabilizer=0;
        private boolean strokeEraser=false;
        private final ArrayList<Layer> layers = new ArrayList<>();
        private final ArrayList<Snapshot> undo = new ArrayList<>();
        private final ArrayList<Snapshot> redo = new ArrayList<>();
        private int activeLayer=0;
        private float lastX,lastY;
        // Raw finger movement is tracked separately from the smoothed brush position.
        private float lastTouchX,lastTouchY,previousTouchDX,previousTouchDY;
        private long lastTouchTime;
        private float reversalScore=0f;
        private boolean eraser=false;
        private boolean bucketTool=false;
        private float zoom=1f, panX=0f, panY=0f, rotation=0f;
        private float lastMidX, lastMidY, lastAngle;
        private boolean multiTouch=false, drawingGesture=false;
        private final ScaleGestureDetector scaleDetector;

        DrawingView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            layers.add(new Layer("Layer 1"));
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(brushSize);
            scaleDetector = new ScaleGestureDetector(MainActivity.this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float oldZoom=zoom;
                    zoom=Math.max(1f,Math.min(20f,zoom*detector.getScaleFactor()));
                    float factor=zoom/oldZoom;
                    float vx=detector.getFocusX()-(getWidth()/2f+panX);
                    float vy=detector.getFocusY()-(getHeight()/2f+panY);
                    panX=detector.getFocusX()-getWidth()/2f-vx*factor;
                    panY=detector.getFocusY()-getHeight()/2f-vy*factor;
                    invalidate();
                    return true;
                }
            });
        }

        void resetView() {
            zoom=1f; panX=0f; panY=0f; rotation=0f;
            invalidate();
        }

        ArrayList<String> getLayerNames() {
            ArrayList<String> result=new ArrayList<>();
            for (Layer layer:layers) result.add(layer.name);
            return result;
        }
        int getActiveLayer() { return activeLayer; }
        String getActiveLayerName() { return layers.get(activeLayer).name; }
        boolean isLayerVisible(int index) { return layers.get(index).visible; }
        void setLayerVisible(int index,boolean visible) {
            layers.get(index).visible=visible;
            invalidate();
        }
        void setActiveLayer(int index) {
            if (index>=0 && index<layers.size()) activeLayer=index;
            invalidate();
        }
        boolean addLayer() {
            if (layers.size()>=6) return false;
            layers.add(new Layer("Layer "+(layers.size()+1)));
            activeLayer=layers.size()-1;
            invalidate();
            return true;
        }
        boolean deleteActiveLayer() {
            if (layers.size()<=1) return false;
            Layer removed=layers.remove(activeLayer);
            removed.bitmap.recycle();
            activeLayer=Math.min(activeLayer,layers.size()-1);
            clearHistory();
            invalidate();
            return true;
        }
        void renameActiveLayer(String name) {
            layers.get(activeLayer).name=name;
        }
        void setEraser(boolean value) { eraser=value; bucketTool=false; }
        void setBucketTool(boolean value) { bucketTool=value; eraser=false; invalidate(); }
        void setBrushSize(float value) { brushSize=value; paint.setStrokeWidth(value); }
        void setBrushOpacity(int value) { brushOpacity=Math.max(1,Math.min(100,value)); }
        void setStabilizer(int value) { activeStabilizer=Math.max(0,Math.min(100,value)); }

        void clearHistory() {
            for (Snapshot s:undo) if (!s.bitmap.isRecycled()) s.bitmap.recycle();
            for (Snapshot s:redo) if (!s.bitmap.isRecycled()) s.bitmap.recycle();
            undo.clear(); redo.clear();
        }
        void saveUndo() {
            if (undo.size()>=8) {
                Snapshot old=undo.remove(0);
                if (!old.bitmap.isRecycled()) old.bitmap.recycle();
            }
            undo.add(new Snapshot(activeLayer,layers.get(activeLayer).bitmap.copy(Bitmap.Config.ARGB_8888,false)));
            for (Snapshot s:redo) if (!s.bitmap.isRecycled()) s.bitmap.recycle();
            redo.clear();
        }
        void restore(Snapshot snapshot) {
            Layer layer=layers.get(snapshot.layerIndex);
            Canvas c=new Canvas(layer.bitmap);
            c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            c.drawBitmap(snapshot.bitmap,0,0,null);
            snapshot.bitmap.recycle();
            invalidate();
        }
        void undo() {
            if (undo.isEmpty()) return;
            Snapshot current=new Snapshot(undo.get(undo.size()-1).layerIndex,
                    layers.get(undo.get(undo.size()-1).layerIndex).bitmap.copy(Bitmap.Config.ARGB_8888,false));
            redo.add(current);
            Snapshot previous=undo.remove(undo.size()-1);
            activeLayer=previous.layerIndex;
            restore(previous);
        }
        void redo() {
            if (redo.isEmpty()) return;
            Snapshot next=redo.remove(redo.size()-1);
            if (undo.size()>=8) {
                Snapshot old=undo.remove(0);
                old.bitmap.recycle();
            }
            undo.add(new Snapshot(next.layerIndex,layers.get(next.layerIndex).bitmap.copy(Bitmap.Config.ARGB_8888,false)));
            activeLayer=next.layerIndex;
            restore(next);
        }
        // PaintMan-style contiguous fill. The visible composite is sampled so
        // line art on a separate layer can define the regions to be coloured.
        void fillAt(int startX,int startY) {
            if (!layers.get(activeLayer).visible) {
                Toast.makeText(MainActivity.this,"Selected layer is hidden",Toast.LENGTH_SHORT).show();
                return;
            }
            final int width=1600, height=2200, total=width*height;
            Bitmap composite=exportBitmap();
            int[] source=new int[total];
            int[] target=new int[total];
            composite.getPixels(source,0,width,0,0,width,height);
            composite.recycle();
            Layer destination=layers.get(activeLayer);
            destination.bitmap.getPixels(target,0,width,0,0,width,height);
            int start=startY*width+startX;
            int sampled=source[start];
            int replacement=currentColor;
            if (sampled==replacement || colorDistance(sampled,replacement)<8) return;
            saveUndo();
            // Mark visited source pixels with a sentinel while using a scanline queue.
            int capacity=4096, head=0, tail=0;
            int[] queue=new int[capacity];
            queue[tail++]=start;
            final int tolerance=42;
            while(head<tail) {
                int seed=queue[head++];
                int sy=seed/width, sx=seed%width;
                if (source[seed]==Integer.MIN_VALUE || colorDistance(source[seed],sampled)>tolerance) continue;
                int left=sx, right=sx;
                while(left>0 && source[sy*width+left-1]!=Integer.MIN_VALUE
                        && colorDistance(source[sy*width+left-1],sampled)<=tolerance) left--;
                while(right<width-1 && source[sy*width+right+1]!=Integer.MIN_VALUE
                        && colorDistance(source[sy*width+right+1],sampled)<=tolerance) right++;
                for(int px=left;px<=right;px++) {
                    int index=sy*width+px;
                    source[index]=Integer.MIN_VALUE;
                    target[index]=replacement;
                }
                for(int ny : new int[]{sy-1,sy+1}) {
                    if(ny<0 || ny>=height) continue;
                    int px=left;
                    while(px<=right) {
                        int index=ny*width+px;
                        if(source[index]!=Integer.MIN_VALUE && colorDistance(source[index],sampled)<=tolerance) {
                            if(tail==capacity) {
                                capacity*=2;
                                int[] grown=new int[capacity];
                                System.arraycopy(queue,0,grown,0,tail);
                                queue=grown;
                            }
                            queue[tail++]=index;
                            px++;
                            while(px<=right && source[ny*width+px]!=Integer.MIN_VALUE
                                    && colorDistance(source[ny*width+px],sampled)<=tolerance) px++;
                        } else px++;
                    }
                }
            }
            destination.bitmap.setPixels(target,0,width,0,0,width,height);
            invalidate();
        }
        private int colorDistance(int a,int b) {
            int dr=Math.abs(Color.red(a)-Color.red(b));
            int dg=Math.abs(Color.green(a)-Color.green(b));
            int db=Math.abs(Color.blue(a)-Color.blue(b));
            return Math.max(dr,Math.max(dg,db));
        }

        void clearCanvas() {
            saveUndo();
            Canvas c=new Canvas(layers.get(activeLayer).bitmap);
            c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            invalidate();
        }
        Bitmap exportBitmap() {
            Bitmap merged=Bitmap.createBitmap(1600,2200,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(merged);
            c.drawColor(Color.WHITE);
            for (Layer layer:layers) if (layer.visible) c.drawBitmap(layer.bitmap,0,0,null);
            return merged;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(65,65,70));
            float scale=Math.min((float)getWidth()/1600f,(float)getHeight()/2200f);
            canvas.save();
            canvas.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            canvas.rotate(rotation);
            canvas.scale(scale*zoom,scale*zoom);
            canvas.translate(-800f,-1100f);
            Paint background=new Paint();
            background.setColor(Color.WHITE);
            canvas.drawRect(0,0,1600,2200,background);
            for (Layer layer:layers) if (layer.visible) canvas.drawBitmap(layer.bitmap,0,0,null);
            if (strokeInProgress && !strokeEraser) {
                previewPaint.setColor(strokeColor);
                previewPaint.setAlpha(strokeAlpha);
                previewPaint.setStyle(Paint.Style.STROKE);
                previewPaint.setStrokeCap(Paint.Cap.ROUND);
                previewPaint.setStrokeJoin(Paint.Join.ROUND);
                previewPaint.setStrokeWidth(strokeWidth);
                canvas.drawPath(currentPath,previewPaint);
            }
            canvas.restore();
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            float base=Math.min((float)getWidth()/1600f,(float)getHeight()/2200f);
            float scale=base*zoom;
            float dx=event.getX()-getWidth()/2f-panX;
            float dy=event.getY()-getHeight()/2f-panY;
            double radians=Math.toRadians(-rotation);
            float x=(float)(Math.cos(radians)*dx-Math.sin(radians)*dy)/scale+800f;
            float y=(float)(Math.sin(radians)*dx+Math.cos(radians)*dy)/scale+1100f;

            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    multiTouch=false;
                    if (bucketTool) {
                        if (x>=0 && y>=0 && x<1600 && y<2200) fillAt(Math.round(x),Math.round(y));
                        return true;
                    }
                    drawingGesture=(x>=0 && y>=0 && x<=1600 && y<=2200);
                    if (!drawingGesture) return true;
                    if (!layers.get(activeLayer).visible) {
                        Toast.makeText(MainActivity.this,"Selected layer is hidden",Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    saveUndo();
                    lastX=x; lastY=y;
                    lastTouchX=event.getX(); lastTouchY=event.getY();
                    previousTouchDX=0f; previousTouchDY=0f;
                    lastTouchTime=event.getEventTime();
                    reversalScore=0f;
                    currentPath.reset();
                    currentPath.moveTo(x,y);
                    strokeColor=currentColor;
                    strokeAlpha=(int)(255f*brushOpacity/100f);
                    strokeWidth=brushSize;
                    strokeEraser=eraser;
                    strokeInProgress=true;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    multiTouch=true;
                    if (event.getPointerCount()>=2) {
                        lastMidX=(event.getX(0)+event.getX(1))/2f;
                        lastMidY=(event.getY(0)+event.getY(1))/2f;
                        lastAngle=(float)Math.toDegrees(Math.atan2(
                                event.getY(1)-event.getY(0),
                                event.getX(1)-event.getX(0)));
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount()>=2) {
                        float midX=(event.getX(0)+event.getX(1))/2f;
                        float midY=(event.getY(0)+event.getY(1))/2f;
                        float angle=(float)Math.toDegrees(Math.atan2(
                                event.getY(1)-event.getY(0),
                                event.getX(1)-event.getX(0)));
                        float delta=angle-lastAngle;
                        while (delta>180f) delta-=360f;
                        while (delta<-180f) delta+=360f;

                        float vx=lastMidX-(getWidth()/2f+panX);
                        float vy=lastMidY-(getHeight()/2f+panY);
                        double turn=Math.toRadians(delta);
                        float rotatedX=(float)(Math.cos(turn)*vx-Math.sin(turn)*vy);
                        float rotatedY=(float)(Math.sin(turn)*vx+Math.cos(turn)*vy);
                        rotation+=delta;
                        panX=midX-getWidth()/2f-rotatedX;
                        panY=midY-getHeight()/2f-rotatedY;

                        lastMidX=midX;
                        lastMidY=midY;
                        lastAngle=angle;
                        invalidate();
                        return true;
                    }
                    if (multiTouch || !drawingGesture || !strokeInProgress) return true;
                    float touchDX=event.getX()-lastTouchX;
                    float touchDY=event.getY()-lastTouchY;
                    long eventTime=event.getEventTime();
                    long elapsed=Math.max(1L,eventTime-lastTouchTime);
                    float speed=(float)Math.hypot(touchDX,touchDY)/elapsed;
                    float previousLength=(float)Math.hypot(previousTouchDX,previousTouchDY);
                    float currentLength=(float)Math.hypot(touchDX,touchDY);
                    if (speed>0.16f && previousLength>1.2f && currentLength>1.2f) {
                        float directionDot=(touchDX*previousTouchDX+touchDY*previousTouchDY)
                                /(currentLength*previousLength);
                        if (directionDot < -0.20f) {
                            // Repeated quick reversals are characteristic of colouring strokes.
                            reversalScore=Math.min(3f,reversalScore+1.15f);
                        } else {
                            reversalScore=Math.max(0f,reversalScore-0.035f);
                        }
                    } else {
                        reversalScore=Math.max(0f,reversalScore-0.08f);
                    }
                    if (currentLength>1f) {
                        previousTouchDX=touchDX;
                        previousTouchDY=touchDY;
                    }
                    lastTouchX=event.getX();
                    lastTouchY=event.getY();
                    lastTouchTime=eventTime;

                    if (activeStabilizer == 0) {
                        currentPath.lineTo(x,y);
                        lastX=x;
                        lastY=y;
                    } else {
                        // The normal stabilizer smooths deliberate strokes. During quick,
                        // repeated back-and-forth movements it automatically loosens so
                        // colouring stays responsive without turning the stabilizer off.
                        float strength = activeStabilizer / 100f;
                        // A gentler curve keeps high settings steady without making
                        // the brush lag far behind the finger.
                        float response = (float)Math.pow(1f - strength, 1.6f);
                        response = Math.max(0.045f, response);
                        // Quick repeated reversals loosen the brush for colouring.
                        float colouringAssist=Math.min(0.94f,reversalScore*0.38f);
                        response += (1f-response)*colouringAssist;
                        float filteredX = lastX + (x-lastX) * response;
                        float filteredY = lastY + (y-lastY) * response;
                        float midX = (lastX + filteredX) * 0.5f;
                        float midY = (lastY + filteredY) * 0.5f;
                        currentPath.quadTo(lastX,lastY,midX,midY);
                        lastX=filteredX;
                        lastY=filteredY;
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    multiTouch=true;
                    return true;

                case MotionEvent.ACTION_UP:
                    if (multiTouch || !drawingGesture) { multiTouch=false; drawingGesture=false; strokeInProgress=false; currentPath.reset(); invalidate(); return true; }
                    drawingGesture=false;
                    if (strokeInProgress) {
                        // Finish at the filtered brush position when stabilization is active.
                        // Connecting to the raw finger point here can create a sudden final sweep.
                        if (activeStabilizer == 0) currentPath.lineTo(x,y);
                        else currentPath.lineTo(lastX,lastY);
                        paint.setColor(strokeColor);
                        paint.setAlpha(strokeAlpha);
                        paint.setStrokeWidth(strokeWidth);
                        paint.setXfermode(strokeEraser ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
                        new Canvas(layers.get(activeLayer).bitmap).drawPath(currentPath,paint);
                        paint.setXfermode(null);
                    }
                    strokeInProgress=false;
                    currentPath.reset();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    multiTouch=false;
                    drawingGesture=false;
                    strokeInProgress=false;
                    currentPath.reset();
                    paint.setXfermode(null);
                    return true;
            }
            return true;
        }
    }
}
