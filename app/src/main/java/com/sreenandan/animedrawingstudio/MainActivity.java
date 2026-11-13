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
    private int brushPreset = 0;
    private Button brushToolButton, eraserToolButton, fillToolButton;
    private Button lineToolButton, rectToolButton, ellipseToolButton;
    private TextView activeToolLabel;
    private Button symmetryButton;

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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(3,2,3,2);
        toolbar.setBackgroundColor(Color.rgb(212,208,200));
        addButton(toolbar,"↶",() -> drawingView.undo());
        addButton(toolbar,"↷",() -> drawingView.redo());
        addButton(toolbar,"▤",() -> showLayers());
        addButton(toolbar,"Save",() -> saveImage());
        addButton(toolbar,"⌕",() -> drawingView.resetView());
        symmetryButton = addButton(toolbar,"Sym OFF",() -> {
            boolean enabled = !drawingView.isSymmetryEnabled();
            drawingView.setSymmetryEnabled(enabled);
            symmetryButton.setText(enabled ? "Sym ON" : "Sym OFF");
        });

        drawingView = new DrawingView();
        root.addView(titleBar,new LinearLayout.LayoutParams(-1,34));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 42));
        root.addView(drawingView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(5,3,5,4);
        tools.setBackgroundColor(Color.rgb(212,208,200));

        LinearLayout colors = new LinearLayout(this);
        int[] palette = {Color.BLACK, Color.WHITE, Color.RED, Color.rgb(255,130,0),
                Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE,
                Color.MAGENTA, Color.rgb(130,70,210), Color.rgb(120,75,45)};
        for (int color : palette) {
            View swatch = new View(this);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,22,1);
            p.setMargins(2,1,2,1);
            colors.addView(swatch,p);
            swatch.setOnClickListener(v -> {
                currentColor = color;
                drawingView.setEraser(false);
            });
        }
        Button wheelButton = new Button(this);
        wheelButton.setText("Wheel");
        wheelButton.setTextSize(10);
        wheelButton.setAllCaps(false);
        wheelButton.setPadding(1,0,1,0);
        wheelButton.setOnClickListener(v -> showColorWheel());
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(0,30,1);
        wheelParams.setMargins(2,1,2,1);
        colors.addView(wheelButton,wheelParams);
        tools.addView(colors);

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        brushToolButton = addButton(toolRow, "✎", () -> {
            drawingView.setEraser(false);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(0);
            setActiveTool("BRUSH");
        });
        eraserToolButton = addButton(toolRow, "▰", () -> {
            drawingView.setEraser(true);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(0);
            setActiveTool("ERASER");
        });
        fillToolButton = addButton(toolRow, "▧", () -> {
            drawingView.setBucketTool(true);
            drawingView.setShapeTool(0);
            setActiveTool("FILL");
        });
        lineToolButton = addButton(toolRow, "╱", () -> {
            drawingView.setEraser(false);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(1);
            setActiveTool("LINE");
        });
        rectToolButton = addButton(toolRow, "□", () -> {
            drawingView.setEraser(false);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(2);
            setActiveTool("RECT");
        });
        ellipseToolButton = addButton(toolRow, "○", () -> {
            drawingView.setEraser(false);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(3);
            setActiveTool("ELLIPSE");
        });
        activeToolLabel = new TextView(this);
        activeToolLabel.setText("BRUSH");
        activeToolLabel.setTextColor(Color.WHITE);
        activeToolLabel.setTypeface(Typeface.DEFAULT_BOLD);
        activeToolLabel.setTextSize(10);
        activeToolLabel.setGravity(Gravity.CENTER);
        activeToolLabel.setBackgroundColor(Color.rgb(0,0,128));
        toolRow.addView(activeToolLabel,new LinearLayout.LayoutParams(0,34,1));
        tools.addView(toolRow);

        LinearLayout sizeOpacityRow = new LinearLayout(this);
        sizeOpacityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(Color.BLACK);
        sizeLabel.setTextSize(11);
        sizeOpacityRow.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(80);
        size.setProgress(6);
        size.setPadding(2,0,4,0);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushSize = Math.max(1,value);
                drawingView.setBrushSize(brushSize);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        sizeOpacityRow.addView(size,new LinearLayout.LayoutParams(0,30,1));
        TextView opacityLabel = new TextView(this);
        opacityLabel.setText("Opacity");
        opacityLabel.setTextColor(Color.BLACK);
        opacityLabel.setTextSize(11);
        sizeOpacityRow.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(100);
        opacity.setPadding(2,0,4,0);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushOpacity = value;
                drawingView.setBrushOpacity(brushOpacity);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        sizeOpacityRow.addView(opacity,new LinearLayout.LayoutParams(0,30,1));
        tools.addView(sizeOpacityRow);

        LinearLayout presetStabilizerRow = new LinearLayout(this);
        presetStabilizerRow.setGravity(Gravity.CENTER_VERTICAL);
        Spinner presets = new Spinner(this);
        String[] presetNames = {"G-Pen", "Mapping Pen", "Pencil", "Marker", "Airbrush"};
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presetNames);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presets.setAdapter(presetAdapter);
        presets.setSelection(brushPreset);
        presets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                brushPreset = position;
                drawingView.setBrushPreset(position);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        presetStabilizerRow.addView(presets,new LinearLayout.LayoutParams(0,32,1));
        TextView stabilizerLabel = new TextView(this);
        stabilizerLabel.setText("Stab 0");
        stabilizerLabel.setTextColor(Color.BLACK);
        stabilizerLabel.setTextSize(11);
        presetStabilizerRow.addView(stabilizerLabel);
        SeekBar stabilizerBar = new SeekBar(this);
        stabilizerBar.setMax(100);
        stabilizerBar.setProgress(0);
        stabilizerBar.setPadding(2,0,2,0);
        stabilizerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                stabilizer = value;
                stabilizerLabel.setText("Stab " + value);
                drawingView.setStabilizer(value);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        presetStabilizerRow.addView(stabilizerBar,new LinearLayout.LayoutParams(0,30,1));
        tools.addView(presetStabilizerRow);
        root.addView(tools,new LinearLayout.LayoutParams(-1,132));

        android.content.SharedPreferences preferences = getSharedPreferences("drawing_ui",MODE_PRIVATE);
        final boolean[] rightHanded = {preferences.getBoolean("right_handed",true)};
        tools.setLayoutDirection(rightHanded[0] ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        toolbar.setLayoutDirection(rightHanded[0] ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        Button handButton = addButton(toolbar,rightHanded[0] ? "Right" : "Left",() -> {
            rightHanded[0] = !rightHanded[0];
            preferences.edit().putBoolean("right_handed",rightHanded[0]).apply();
            tools.setLayoutDirection(rightHanded[0] ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
            toolbar.setLayoutDirection(rightHanded[0] ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
            handButtonLabelRefresh(toolbar,rightHanded[0]);
        });
        handButton.setTextSize(10);
        setActiveTool("BRUSH");
        setContentView(root);
    }

    private void handButtonLabelRefresh(LinearLayout toolbar, boolean rightHanded) {
        for (int i=0;i<toolbar.getChildCount();i++) {
            View child=toolbar.getChildAt(i);
            if (child instanceof Button && ("Right".equals(((Button)child).getText().toString())
                    || "Left".equals(((Button)child).getText().toString()))) {
                ((Button)child).setText(rightHanded ? "Right" : "Left");
                return;
            }
        }
    }

    private GradientDrawable bevel(boolean pressed) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(212,208,200));
        d.setCornerRadius(2);
        d.setStroke(pressed ? 2 : 1, Color.rgb(90,90,90));
        return d;
    }

    private void setActiveTool(String tool) {
        activeToolLabel.setText(tool);
        styleToolButton(brushToolButton,"BRUSH".equals(tool));
        styleToolButton(eraserToolButton,"ERASER".equals(tool));
        styleToolButton(fillToolButton,"FILL".equals(tool));
        styleToolButton(lineToolButton,"LINE".equals(tool));
        styleToolButton(rectToolButton,"RECT".equals(tool));
        styleToolButton(ellipseToolButton,"ELLIPSE".equals(tool));
    }

    private void styleToolButton(Button button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? Color.WHITE : Color.BLACK);
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? Color.rgb(0,0,128) : Color.rgb(212,208,200));
        background.setCornerRadius(2);
        background.setStroke(active ? 3 : 1,
                active ? Color.rgb(255,255,120) : Color.rgb(90,90,90));
        button.setBackground(background);
    }

    private Button addButton(LinearLayout parent, String label, Runnable action) {
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
        b.setMinHeight(32);
        b.setMinimumHeight(32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,38,1);
        lp.setMargins(2,1,2,1);
        parent.addView(b,lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showColorWheel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(12),dp(8),dp(12),dp(4));

        ColorWheelView wheel = new ColorWheelView();
        wheel.setColor(currentColor);
        TextView preview = new TextView(this);
        preview.setText("Selected colour");
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Color.BLACK);
        preview.setBackgroundColor(currentColor);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1,dp(34));
        previewParams.bottomMargin = dp(6);
        panel.addView(preview,previewParams);

        panel.addView(wheel,new LinearLayout.LayoutParams(dp(244),dp(244)));
        TextView valueLabel = new TextView(this);
        valueLabel.setText("Brightness");
        valueLabel.setTextColor(Color.BLACK);
        panel.addView(valueLabel);
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(100);
        panel.addView(brightness,new LinearLayout.LayoutParams(-1,dp(36)));
        final int[] selected = {currentColor};
        wheel.setOnColorChanged(color -> {
            selected[0] = color;
            preview.setBackgroundColor(color);
        });
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser) {
                wheel.setBrightness(Math.max(1,value)/100f);
                selected[0] = wheel.getSelectedColor();
                preview.setBackgroundColor(selected[0]);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        new AlertDialog.Builder(this)
                .setTitle("Colour Wheel")
                .setView(panel)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Use colour",(dialog,which) -> {
                    currentColor = selected[0];
                    drawingView.setEraser(false);
                })
                .show();
    }

    private class ColorWheelView extends View {
        private Bitmap wheelBitmap;
        private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue;
        private float saturation = 1f;
        private float brightness = 1f;
        private float centerX,centerY,radius;
        private java.util.function.IntConsumer colorChanged;

        ColorWheelView() {
            super(MainActivity.this);
            markerPaint.setStyle(Paint.Style.STROKE);
            markerPaint.setStrokeWidth(dp(2));
            markerPaint.setColor(Color.WHITE);
        }

        void setColor(int color) {
            float[] hsv = new float[3];
            Color.colorToHSV(color,hsv);
            hue=hsv[0];
            saturation=hsv[1];
            brightness=Math.max(0.01f,hsv[2]);
            rebuildWheel();
            invalidate();
        }

        void setOnColorChanged(java.util.function.IntConsumer listener) {
            colorChanged=listener;
        }

        void setBrightness(float value) {
            brightness=Math.max(0.01f,Math.min(1f,value));
            rebuildWheel();
            invalidate();
            if(colorChanged!=null) colorChanged.accept(getSelectedColor());
        }

        int getSelectedColor() {
            return Color.HSVToColor(new float[]{hue,saturation,brightness});
        }

        private void rebuildWheel() {
            final int size=256;
            if(wheelBitmap!=null && !wheelBitmap.isRecycled()) wheelBitmap.recycle();
            wheelBitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            int[] pixels=new int[size*size];
            float c=(size-1)/2f;
            float r=c;
            for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
                float dx=x-c,dy=y-c;
                float distance=(float)Math.sqrt(dx*dx+dy*dy);
                int index=y*size+x;
                if(distance<=r) {
                    float angle=(float)Math.toDegrees(Math.atan2(dy,dx));
                    float h=(angle+360f)%360f;
                    float sat=Math.min(1f,distance/r);
                    pixels[index]=Color.HSVToColor(new float[]{h,sat,brightness});
                } else pixels[index]=Color.TRANSPARENT;
            }
            wheelBitmap.setPixels(pixels,0,size,0,0,size,size);
        }

        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            centerX=w/2f;
            centerY=h/2f;
            radius=Math.min(w,h)/2f-dp(3);
            rebuildWheel();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(wheelBitmap==null) rebuildWheel();
            RectF bounds=new RectF(centerX-radius,centerY-radius,centerX+radius,centerY+radius);
            canvas.drawBitmap(wheelBitmap,null,bounds,wheelPaint);
            float markerX=centerX+(float)Math.cos(Math.toRadians(hue))*saturation*radius;
            float markerY=centerY+(float)Math.sin(Math.toRadians(hue))*saturation*radius;
            markerPaint.setColor(Color.BLACK);
            canvas.drawCircle(markerX,markerY,dp(7),markerPaint);
            markerPaint.setColor(Color.WHITE);
            canvas.drawCircle(markerX,markerY,dp(5),markerPaint);
            Paint centerPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setColor(getSelectedColor());
            centerPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX,centerY,dp(5),centerPaint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN
                    || event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                float dx=event.getX()-centerX;
                float dy=event.getY()-centerY;
                float distance=(float)Math.hypot(dx,dy);
                if(distance>radius) {
                    dx=dx*radius/distance;
                    dy=dy*radius/distance;
                    distance=radius;
                }
                hue=(float)(Math.toDegrees(Math.atan2(dy,dx))+360.0)%360f;
                saturation=Math.min(1f,distance/radius);
                invalidate();
                if(colorChanged!=null) colorChanged.accept(getSelectedColor());
                return true;
            }
            return event.getActionMasked()==MotionEvent.ACTION_UP;
        }
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
                    LinearLayout opacityRow = new LinearLayout(MainActivity.this);
                    opacityRow.setGravity(Gravity.CENTER_VERTICAL);
                    TextView opacityText = new TextView(MainActivity.this);
                    opacityText.setText("Opacity " + drawingView.getLayerOpacity(index) + "%");
                    opacityText.setTextColor(Color.BLACK);
                    opacityText.setTextSize(11);
                    opacityRow.addView(opacityText,new LinearLayout.LayoutParams(82,32));
                    SeekBar layerOpacity = new SeekBar(MainActivity.this);
                    layerOpacity.setMax(100);
                    layerOpacity.setProgress(drawingView.getLayerOpacity(index));
                    layerOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        public void onProgressChanged(SeekBar bar,int value,boolean fromUser) {
                            drawingView.setLayerOpacity(index,value);
                            opacityText.setText("Opacity " + value + "%");
                        }
                        public void onStartTrackingTouch(SeekBar bar) {}
                        public void onStopTrackingTouch(SeekBar bar) {}
                    });
                    opacityRow.addView(layerOpacity,new LinearLayout.LayoutParams(0,32,1));
                    rows.addView(opacityRow);
                    LinearLayout blendRow = new LinearLayout(MainActivity.this);
                    blendRow.setGravity(Gravity.CENTER_VERTICAL);
                    TextView blendLabel = new TextView(MainActivity.this);
                    blendLabel.setText("Blend");
                    blendLabel.setTextColor(Color.BLACK);
                    blendLabel.setTextSize(11);
                    blendRow.addView(blendLabel,new LinearLayout.LayoutParams(82,36));
                    Spinner blendSpinner = new Spinner(MainActivity.this);
                    String[] blendNames = {"Normal","Multiply","Screen","Add","Darken","Lighten"};
                    ArrayAdapter<String> blendAdapter = new ArrayAdapter<>(MainActivity.this,
                            android.R.layout.simple_spinner_item,blendNames);
                    blendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    blendSpinner.setAdapter(blendAdapter);
                    blendSpinner.setSelection(drawingView.getLayerBlendMode(index));
                    blendSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                            drawingView.setLayerBlendMode(index,position);
                        }
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });
                    blendRow.addView(blendSpinner,new LinearLayout.LayoutParams(0,40,1));
                    rows.addView(blendRow);
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
            int opacity=100;
            int blendMode=0;
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
        private final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap pencilTexture;
        private final Path currentPath = new Path();
        private boolean strokeInProgress=false;
        private int strokeColor=Color.BLACK;
        private int strokeAlpha=255;
        private float strokeWidth=8f;
        private int strokePreset=0;
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
        private boolean symmetryEnabled=false;
        private int shapeTool=0;
        private boolean shapeInProgress=false;
        private float shapeStartX,shapeStartY,shapeEndX,shapeEndY;
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
            pencilTexture = createPencilTexture();
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

        private Bitmap createPencilTexture() {
            Bitmap texture=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            java.util.Random random=new java.util.Random(7319L);
            int[] pixels=new int[48*48];
            for(int i=0;i<pixels.length;i++) {
                int alpha=random.nextInt(256);
                pixels[i]=Color.argb(alpha,255,255,255);
            }
            texture.setPixels(pixels,0,48,0,0,48,48);
            return texture;
        }

        private int presetOpacity(int preset, int userOpacity) {
            int base=preset==2 ? 225 : (preset==3 ? 112 : (preset==4 ? 82 : 255));
            return Math.max(0,Math.min(255,base*userOpacity/100));
        }

        private void configureStrokePaint(Paint target,int preset,int color,int userOpacity,float width) {
            target.setStyle(Paint.Style.STROKE);
            target.setStrokeCap(Paint.Cap.ROUND);
            target.setStrokeJoin(Paint.Join.ROUND);
            target.setStrokeWidth(width);
            target.setColor(color);
            target.setAlpha(presetOpacity(preset,userOpacity));
            target.setShader(null);
            target.setColorFilter(null);
            target.setMaskFilter(null);
            if(preset==2) {
                target.setShader(new BitmapShader(pencilTexture,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT));
                target.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));
            } else if(preset==4) {
                target.setMaskFilter(new BlurMaskFilter(Math.max(2f,width*0.9f),BlurMaskFilter.Blur.NORMAL));
            }
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
        int getLayerOpacity(int index) { return layers.get(index).opacity; }
        int getLayerBlendMode(int index) { return layers.get(index).blendMode; }
        void setLayerBlendMode(int index,int mode) {
            layers.get(index).blendMode=Math.max(0,Math.min(5,mode));
            invalidate();
        }
        private Xfermode createLayerXfermode(int mode) {
            switch(mode) {
                case 1: return new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY);
                case 2: return new PorterDuffXfermode(PorterDuff.Mode.SCREEN);
                case 3: return new PorterDuffXfermode(PorterDuff.Mode.ADD);
                case 4: return new PorterDuffXfermode(PorterDuff.Mode.DARKEN);
                case 5: return new PorterDuffXfermode(PorterDuff.Mode.LIGHTEN);
                default: return null;
            }
        }

        private void configureLayerPaint(Layer layer) {
            layerPaint.setAlpha(layer.opacity * 255 / 100);
            if (Build.VERSION.SDK_INT >= 29) {
                layerPaint.setXfermode(null);
                switch (layer.blendMode) {
                    case 1: layerPaint.setBlendMode(BlendMode.MULTIPLY); break;
                    case 2: layerPaint.setBlendMode(BlendMode.SCREEN); break;
                    case 3: layerPaint.setBlendMode(BlendMode.PLUS); break;
                    case 4: layerPaint.setBlendMode(BlendMode.DARKEN); break;
                    case 5: layerPaint.setBlendMode(BlendMode.LIGHTEN); break;
                    default: layerPaint.setBlendMode(null); break;
                }
            } else {
                layerPaint.setXfermode(createLayerXfermode(layer.blendMode));
            }
        }

        private void resetLayerPaint() {
            layerPaint.setAlpha(255);
            layerPaint.setXfermode(null);
            if (Build.VERSION.SDK_INT >= 29) layerPaint.setBlendMode(null);
        }
        void setLayerOpacity(int index,int opacity) {
            layers.get(index).opacity=Math.max(0,Math.min(100,opacity));
            invalidate();
        }
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
        void setEraser(boolean value) { eraser=value; bucketTool=false; shapeTool=0; }
        void setBucketTool(boolean value) { bucketTool=value; eraser=false; shapeTool=0; invalidate(); }
        void setShapeTool(int value) { shapeTool=Math.max(0,Math.min(3,value)); eraser=false; bucketTool=false; invalidate(); }
        void setBrushSize(float value) { brushSize=value; paint.setStrokeWidth(value); }
        void setBrushPreset(int value) { brushPreset=Math.max(0,Math.min(4,value)); }
        void setBrushOpacity(int value) { brushOpacity=Math.max(1,Math.min(100,value)); }
        void setStabilizer(int value) { activeStabilizer=Math.max(0,Math.min(100,value)); }
        boolean isSymmetryEnabled() { return symmetryEnabled; }
        void setSymmetryEnabled(boolean enabled) { symmetryEnabled=enabled; invalidate(); }

        private void drawSymmetricPath(Canvas target,Path path,Paint strokePaint) {
            target.drawPath(path,strokePaint);
            if (symmetryEnabled) {
                target.save();
                target.translate(1600f,0f);
                target.scale(-1f,1f);
                target.drawPath(path,strokePaint);
                target.restore();
            }
        }

        private void drawSymmetricShape(Canvas target,Paint shapePaint,int tool,
                                        float x1,float y1,float x2,float y2) {
            drawShape(target,shapePaint,tool,x1,y1,x2,y2);
            if (symmetryEnabled) {
                target.save();
                target.translate(1600f,0f);
                target.scale(-1f,1f);
                drawShape(target,shapePaint,tool,x1,y1,x2,y2);
                target.restore();
            }
        }

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
            for (Layer layer:layers) if (layer.visible && layer.opacity>0) {
                configureLayerPaint(layer);
                c.drawBitmap(layer.bitmap,0,0,layerPaint);
            }
            resetLayerPaint();
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
            for (Layer layer:layers) if (layer.visible && layer.opacity>0) {
                configureLayerPaint(layer);
                canvas.drawBitmap(layer.bitmap,0,0,layerPaint);
            }
            resetLayerPaint();
            if (strokeInProgress && !strokeEraser) {
                configureStrokePaint(previewPaint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                drawSymmetricPath(canvas,currentPath,previewPaint);
                previewPaint.setShader(null);
                previewPaint.setColorFilter(null);
                previewPaint.setMaskFilter(null);
            }
            if (shapeInProgress) {
                configureStrokePaint(previewPaint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                drawSymmetricShape(canvas,previewPaint,shapeTool,shapeStartX,shapeStartY,shapeEndX,shapeEndY);
                previewPaint.setShader(null);
                previewPaint.setColorFilter(null);
                previewPaint.setMaskFilter(null);
            }
            if (symmetryEnabled) {
                Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
                axis.setColor(Color.rgb(40,120,220));
                axis.setStrokeWidth(2f / (scale*zoom));
                axis.setPathEffect(new DashPathEffect(new float[]{12f/(scale*zoom),8f/(scale*zoom)},0));
                canvas.drawLine(800f,0f,800f,2200f,axis);
            }
            canvas.restore();
        }

        private void drawShape(Canvas target,Paint shapePaint,int tool,float x1,float y1,float x2,float y2) {
            if (tool==1) target.drawLine(x1,y1,x2,y2,shapePaint);
            else {
                float left=Math.min(x1,x2), top=Math.min(y1,y2);
                float right=Math.max(x1,x2), bottom=Math.max(y1,y2);
                if (tool==2) target.drawRect(left,top,right,bottom,shapePaint);
                else if (tool==3) target.drawOval(left,top,right,bottom,shapePaint);
            }
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
                    strokePreset=brushPreset;
                    if (strokePreset==1) strokeWidth*=0.72f;
                    if (strokePreset==2) strokeWidth*=0.82f;
                    if (strokePreset==3) strokeWidth*=2.2f;
                    if (strokePreset==4) strokeWidth*=2.8f;
                    strokeEraser=eraser;
                    if (shapeTool>0) {
                        shapeStartX=shapeEndX=x;
                        shapeStartY=shapeEndY=y;
                        shapeInProgress=true;
                        strokeInProgress=false;
                    } else {
                        shapeInProgress=false;
                        strokeInProgress=true;
                    }
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
                    if (multiTouch || !drawingGesture) return true;
                    if (shapeInProgress) {
                        shapeEndX=x;
                        shapeEndY=y;
                        invalidate();
                        return true;
                    }
                    if (!strokeInProgress) return true;
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
                    if (multiTouch || !drawingGesture) { multiTouch=false; drawingGesture=false; strokeInProgress=false; shapeInProgress=false; currentPath.reset(); invalidate(); return true; }
                    drawingGesture=false;
                    if (shapeInProgress) {
                        shapeEndX=x;
                        shapeEndY=y;
                        configureStrokePaint(paint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                        drawSymmetricShape(new Canvas(layers.get(activeLayer).bitmap),paint,shapeTool,
                                shapeStartX,shapeStartY,shapeEndX,shapeEndY);
                        paint.setShader(null);
                        paint.setColorFilter(null);
                        paint.setMaskFilter(null);
                        shapeInProgress=false;
                    }
                    if (strokeInProgress) {
                        // Finish at the filtered brush position when stabilization is active.
                        // Connecting to the raw finger point here can create a sudden final sweep.
                        if (activeStabilizer == 0) currentPath.lineTo(x,y);
                        else currentPath.lineTo(lastX,lastY);
                        if(strokeEraser) {
                            paint.setColor(Color.TRANSPARENT);
                            paint.setAlpha(255);
                            paint.setShader(null);
                            paint.setColorFilter(null);
                            paint.setMaskFilter(null);
                            paint.setStrokeWidth(strokeWidth);
                        } else {
                            configureStrokePaint(paint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                        }
                        paint.setXfermode(strokeEraser ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
                        drawSymmetricPath(new Canvas(layers.get(activeLayer).bitmap),currentPath,paint);
                        paint.setXfermode(null);
                        paint.setShader(null);
                        paint.setColorFilter(null);
                        paint.setMaskFilter(null);
                    }
                    strokeInProgress=false;
                    currentPath.reset();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    multiTouch=false;
                    drawingGesture=false;
                    strokeInProgress=false;
                    shapeInProgress=false;
                    currentPath.reset();
                    paint.setXfermode(null);
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
