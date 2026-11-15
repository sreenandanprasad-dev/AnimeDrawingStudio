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
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private DrawingView drawingView;
    private FrameLayout canvasStage;
    private AnimeBaseView canvasAnimeBase;
    private View canvas3DControls;
    private String canvas3DLayerName=null;
    private boolean canvas3DVisible=true;
    private int canvas3DOpacity=100;
    private boolean baseAdjustMode=false;
    private int currentColor = Color.BLACK;
    private float brushSize = 8f;
    private int brushOpacity = 100;
    private int stabilizer = 0;
    private int brushPreset = 0;
    private Button brushToolButton, eraserToolButton, fillToolButton;
    private Button lineToolButton, rectToolButton, ellipseToolButton, eyedropperToolButton, selectionToolButton, shapeToolButton;
    private TextView activeToolLabel;
    private Button symmetryButton;

    private boolean studioVisible=false;
    private int newCanvasWidth=1600;
    private int newCanvasHeight=2200;
    private int newCanvasBackground=Color.WHITE;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadBrushPreferences();
        loadCanvasPreferences();
        installCrashReporter();
        getWindow().setStatusBarColor(Color.rgb(38,112,171));
        getWindow().setNavigationBarColor(Color.rgb(207,235,246));
        showGallery();
        showPreviousCrashReport();
    }

    private void loadBrushPreferences() {
        android.content.SharedPreferences p=getSharedPreferences("brush_preferences",MODE_PRIVATE);
        brushSize=Math.max(1f,Math.min(80f,p.getFloat("size",8f)));
        brushOpacity=Math.max(1,Math.min(100,p.getInt("opacity",100)));
        stabilizer=Math.max(0,Math.min(100,p.getInt("stabilizer",0)));
        brushPreset=Math.max(0,Math.min(4,p.getInt("preset",0)));
    }

    private void saveBrushPreferences() {
        getSharedPreferences("brush_preferences",MODE_PRIVATE).edit()
                .putFloat("size",brushSize)
                .putInt("opacity",brushOpacity)
                .putInt("stabilizer",stabilizer)
                .putInt("preset",brushPreset)
                .apply();
    }

    private void loadCanvasPreferences() {
        android.content.SharedPreferences p=getSharedPreferences("canvas_preferences",MODE_PRIVATE);
        int width=p.getInt("width",1600);
        int height=p.getInt("height",2200);
        if(width>=256 && width<=2400 && height>=256 && height<=3200) {
            newCanvasWidth=width;
            newCanvasHeight=height;
        }
        newCanvasBackground=p.getInt("background",Color.WHITE);
    }

    private void saveCanvasPreferences() {
        getSharedPreferences("canvas_preferences",MODE_PRIVATE).edit()
                .putInt("width",newCanvasWidth)
                .putInt("height",newCanvasHeight)
                .putInt("background",newCanvasBackground)
                .apply();
    }

    @Override public void onBackPressed() {
        if (studioVisible) {
            showGallery();
        } else {
            super.onBackPressed();
        }
    }

    private void showGallery() {
        studioVisible=false;
        getWindow().setStatusBarColor(Color.rgb(38,112,171));
        getWindow().setNavigationBarColor(Color.rgb(207,235,246));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.setBackground(glassGradient(Color.rgb(218,244,250),Color.rgb(126,190,211),Color.WHITE,dp(0)));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),dp(8),dp(12),dp(8));
        header.setBackground(glassGradient(Color.WHITE,Color.rgb(174,220,239),Color.WHITE,dp(16)));
        TextView heading=new TextView(this);
        heading.setText("✎  Anime Drawing Studio");
        heading.setTextColor(Color.rgb(25,74,112));
        heading.setTextSize(19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(60)));

        TextView subtitle=new TextView(this);
        subtitle.setText("MY DRAWINGS");
        subtitle.setTextColor(Color.rgb(30,91,128));
        subtitle.setTextSize(13);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setPadding(dp(4),dp(14),dp(4),dp(8));
        root.addView(subtitle);

        Button newCanvas=addButton(root,"＋  New Canvas",() -> showNewCanvasSetup());
        newCanvas.setTextSize(15);
        newCanvas.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView scroll=new ScrollView(this);
        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0,dp(10),0,dp(10));
        scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        ArrayList<Uri> drawingUris=new ArrayList<>();
        ArrayList<String> drawingNames=new ArrayList<>();
        android.database.Cursor cursor=null;
        try {
            String[] projection={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME};
            String selection=MediaStore.Images.Media.DISPLAY_NAME+" LIKE ?";
            String[] selectionArgs=new String[]{"AnimeDrawing%"};
            if(Build.VERSION.SDK_INT>=29) {
                selection=MediaStore.Images.Media.RELATIVE_PATH+"=? AND "+MediaStore.Images.Media.DISPLAY_NAME+" LIKE ?";
                selectionArgs=new String[]{"Pictures/AnimeDrawingStudio/","AnimeDrawing%"};
            }
            cursor=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,selection,selectionArgs,MediaStore.Images.Media.DATE_ADDED+" DESC");
            if(cursor!=null) {
                int idColumn=cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameColumn=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                while(cursor.moveToNext()) {
                    long id=cursor.getLong(idColumn);
                    drawingUris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,String.valueOf(id)));
                    drawingNames.add(cursor.getString(nameColumn));
                }
            }
        } catch(Exception error) {
            Toast.makeText(this,"Could not load saved drawings",Toast.LENGTH_SHORT).show();
        } finally {
            if(cursor!=null) cursor.close();
        }

        if(drawingUris.isEmpty()) {
            TextView empty=new TextView(this);
            empty.setText("No drawings saved yet.\n\nCreate your first drawing with New Canvas, then tap Save.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.rgb(45,91,116));
            empty.setTextSize(15);
            empty.setPadding(dp(24),dp(50),dp(24),dp(50));
            grid.addView(empty,new LinearLayout.LayoutParams(-1,dp(210)));
        } else {
            for(int i=0;i<drawingUris.size();i+=2) {
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                for(int j=i;j<Math.min(i+2,drawingUris.size());j++) {
                    final Uri imageUri=drawingUris.get(j);
                    final String imageName=drawingNames.get(j);
                    LinearLayout tile=new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setPadding(dp(6),dp(6),dp(6),dp(6));
                    tile.setBackground(glassGradient(Color.WHITE,Color.rgb(205,237,247),Color.rgb(255,255,255),dp(12)));
                    ImageView preview=new ImageView(this);
                    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    try {
                        if(Build.VERSION.SDK_INT>=29)
                            preview.setImageBitmap(getContentResolver().loadThumbnail(imageUri,new android.util.Size(dp(150),dp(180)),null));
                        else preview.setImageURI(imageUri);
                    } catch(Exception ignored) {}
                    tile.addView(preview,new LinearLayout.LayoutParams(-1,dp(150)));
                    TextView name=new TextView(this);
                    name.setText(imageName);
                    name.setTextColor(Color.rgb(25,74,112));
                    name.setTextSize(11);
                    name.setMaxLines(2);
                    name.setPadding(dp(3),dp(5),dp(3),dp(3));
                    tile.addView(name);
                    tile.setOnClickListener(v -> {
                        ImageView large=new ImageView(this);
                        large.setImageURI(imageUri);
                        large.setAdjustViewBounds(true);
                        large.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        new AlertDialog.Builder(this).setTitle(imageName).setView(large)
                                .setPositiveButton("Close",null).show();
                    });
                    LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-2,1);
                    tileParams.setMargins(dp(4),dp(4),dp(4),dp(4));
                    row.addView(tile,tileParams);
                }
                if(row.getChildCount()==1) row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
                grid.addView(row,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        setContentView(root);
    }

    private void showNewCanvasSetup() {
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView sizeTitle=new TextView(this);
        sizeTitle.setText("Canvas size");
        sizeTitle.setTextColor(Color.rgb(25,74,112));
        sizeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(sizeTitle);
        Spinner presets=new Spinner(this);
        String[] options={"Portrait 1600 × 2200","Landscape 2200 × 1600","Square 1600 × 1600","Custom size"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presets.setAdapter(adapter);
        int savedPreset=(newCanvasWidth==1600 && newCanvasHeight==2200)?0:
                (newCanvasWidth==2200 && newCanvasHeight==1600)?1:
                (newCanvasWidth==1600 && newCanvasHeight==1600)?2:3;
        presets.setSelection(savedPreset);
        panel.addView(presets);
        LinearLayout customRow=new LinearLayout(this);
        EditText widthInput=new EditText(this);
        widthInput.setSingleLine(true);
        widthInput.setInputType(2);
        widthInput.setHint("Width px");
        widthInput.setText(String.valueOf(newCanvasWidth));
        EditText heightInput=new EditText(this);
        heightInput.setSingleLine(true);
        heightInput.setInputType(2);
        heightInput.setHint("Height px");
        heightInput.setText(String.valueOf(newCanvasHeight));
        customRow.addView(widthInput,new LinearLayout.LayoutParams(0,dp(48),1));
        customRow.addView(heightInput,new LinearLayout.LayoutParams(0,dp(48),1));
        panel.addView(customRow);
        customRow.setVisibility(View.GONE);
        presets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                customRow.setVisibility(position==3?View.VISIBLE:View.GONE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        TextView bgTitle=new TextView(this);
        bgTitle.setText("Background");
        bgTitle.setTextColor(Color.rgb(25,74,112));
        bgTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bgTitle.setPadding(0,dp(12),0,dp(2));
        panel.addView(bgTitle);
        Spinner backgrounds=new Spinner(this);
        String[] bgOptions={"White","Transparent","Custom colour"};
        ArrayAdapter<String> bgAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,bgOptions);
        bgAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        backgrounds.setAdapter(bgAdapter);
        final int[] chosenBackground={newCanvasBackground};
        int savedBackgroundChoice=newCanvasBackground==Color.WHITE?0:
                newCanvasBackground==Color.TRANSPARENT?1:2;
        backgrounds.setSelection(savedBackgroundChoice);
        backgrounds.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id) {
                if(position==2) showColorWheel(chosenBackground[0],false,color -> chosenBackground[0]=color);
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        panel.addView(backgrounds);
        new AlertDialog.Builder(this).setTitle("New Canvas").setView(panel)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Create",(dialog,which)->{
                    int width=1600,height=2200;
                    int choice=presets.getSelectedItemPosition();
                    if(choice==1){width=2200;height=1600;}
                    else if(choice==2){width=1600;height=1600;}
                    else if(choice==3){
                        try { width=Integer.parseInt(widthInput.getText().toString().trim());
                              height=Integer.parseInt(heightInput.getText().toString().trim()); }
                        catch(Exception error){Toast.makeText(this,"Enter valid canvas dimensions",Toast.LENGTH_SHORT).show();return;}
                    }
                    if(width<256||height<256||width>2400||height>3200){
                        Toast.makeText(this,"Canvas size must be 256–2400 px wide and 256–3200 px high",Toast.LENGTH_LONG).show();
                        return;
                    }
                    newCanvasWidth=width;
                    newCanvasHeight=height;
                    newCanvasBackground=backgrounds.getSelectedItemPosition()==1?Color.TRANSPARENT:(backgrounds.getSelectedItemPosition()==2?chosenBackground[0]:Color.WHITE);
                    saveCanvasPreferences();
                    showDrawingStudio();
                }).show();
    }

    private void showDrawingStudio() {
        studioVisible=true;
        getWindow().setStatusBarColor(Color.rgb(38,112,171));
        getWindow().setNavigationBarColor(Color.rgb(207,235,246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(glassGradient(Color.rgb(218,244,250),Color.rgb(126,190,211),Color.rgb(255,255,255),dp(0)));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(10,2,8,2);
        titleBar.setBackground(glassGradient(Color.rgb(250,255,255),Color.rgb(174,220,239),Color.rgb(255,255,255),dp(14)));
        TextView appTitle = new TextView(this);
        appTitle.setText("✎  Anime Drawing Studio");
        appTitle.setTextColor(Color.rgb(25,74,112));
        appTitle.setTextSize(15);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleBar.addView(appTitle,new LinearLayout.LayoutParams(-1,32));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(4,3,4,3);
        toolbar.setBackground(glassGradient(Color.rgb(255,255,255),Color.rgb(178,220,240),Color.rgb(255,255,255),dp(12)));
        addButton(toolbar,"↶",() -> drawingView.undo());
        addButton(toolbar,"↷",() -> drawingView.redo());
        addButton(toolbar,"▤",() -> showLayers());
        addButton(toolbar,"Save",() -> saveImage());
        addButton(toolbar,"Home",() -> showGallery());
        addButton(toolbar,"⋮",() -> showMoreActions());

        drawingView = new DrawingView(newCanvasWidth,newCanvasHeight,newCanvasBackground);
        root.addView(titleBar,new LinearLayout.LayoutParams(-1,34));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 42));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(5,3,5,4);
        tools.setBackground(glassGradient(Color.rgb(250,255,255),Color.rgb(167,216,233),Color.rgb(255,255,255),dp(12)));

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
                // Changing colour must never change the active drawing tool.
                currentColor = color;
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
        toolRow.setOrientation(LinearLayout.VERTICAL);
        toolRow.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toolRow.setPadding(3,4,3,4);
        toolRow.setBackground(glassGradient(Color.rgb(255,255,255),Color.rgb(178,222,239),Color.rgb(255,255,255),dp(12)));
        brushToolButton = addButton(toolRow, "✎", () -> {
            drawingView.setEyedropper(false);
            drawingView.setEraser(false);
            setActiveTool("BRUSH");
        });
        eraserToolButton = addButton(toolRow, "▰", () -> {
            drawingView.setEyedropper(false);
            drawingView.setEraser(true);
            setActiveTool("ERASER");
        });
        fillToolButton = addButton(toolRow, "▧", () -> {
            drawingView.setEyedropper(false);
            drawingView.setBucketTool(true);
            setActiveTool("FILL");
        });
        shapeToolButton = addButton(toolRow, "◇", () -> showShapeMenu(shapeToolButton));
        eyedropperToolButton = addButton(toolRow, "Pick", () -> {
            drawingView.setEyedropper(true);
            drawingView.setBucketTool(false);
            drawingView.setShapeTool(0);
            drawingView.setEraser(false);
            setActiveTool("PICKER");
            Toast.makeText(this,"Tap the canvas to pick a colour",Toast.LENGTH_SHORT).show();
        });
        selectionToolButton = addButton(toolRow, "Sel", () -> {
            drawingView.setSelectionTool(true);
            setActiveTool("SELECT");
            Toast.makeText(this,"Drag on the canvas to make a rectangular selection",Toast.LENGTH_SHORT).show();
        });
        Button[] railButtons = {brushToolButton,eraserToolButton,fillToolButton,shapeToolButton,eyedropperToolButton,selectionToolButton};
        for (Button railButton : railButtons) {
            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(-1,dp(42));
            railParams.setMargins(dp(1),dp(1),dp(1),dp(1));
            railButton.setLayoutParams(railParams);
            railButton.setMinHeight(dp(38));
            railButton.setMinimumHeight(dp(38));
        }
        activeToolLabel = new TextView(this);
        activeToolLabel.setText("BRUSH");
        activeToolLabel.setTextColor(Color.rgb(20,91,145));
        activeToolLabel.setTypeface(Typeface.DEFAULT_BOLD);
        activeToolLabel.setTextSize(10);
        activeToolLabel.setGravity(Gravity.CENTER);
        activeToolLabel.setBackground(glassGradient(Color.rgb(255,255,255),Color.rgb(189,229,244),Color.rgb(255,255,255),dp(9)));
        activeToolLabel.setTextSize(8);
        toolRow.addView(activeToolLabel,new LinearLayout.LayoutParams(-1,dp(20)));
        tools.addView(toolRow);

        LinearLayout sizeOpacityRow = new LinearLayout(this);
        sizeOpacityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Size");
        sizeLabel.setTextColor(Color.rgb(24,76,108));
        sizeLabel.setTextSize(11);
        sizeOpacityRow.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(80);
        size.setProgress((int)brushSize);
        size.setPadding(2,0,4,0);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushSize = Math.max(1,value);
                drawingView.setBrushSize(brushSize);
                if(user) saveBrushPreferences();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        sizeOpacityRow.addView(size,new LinearLayout.LayoutParams(0,30,1));
        TextView opacityLabel = new TextView(this);
        opacityLabel.setText("Opacity");
        opacityLabel.setTextColor(Color.rgb(24,76,108));
        opacityLabel.setTextSize(11);
        sizeOpacityRow.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(brushOpacity);
        opacity.setPadding(2,0,4,0);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                brushOpacity = value;
                drawingView.setBrushOpacity(brushOpacity);
                if(user) saveBrushPreferences();
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
                saveBrushPreferences();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        presetStabilizerRow.addView(presets,new LinearLayout.LayoutParams(0,32,1));
        TextView stabilizerLabel = new TextView(this);
        stabilizerLabel.setText("Stab 0");
        stabilizerLabel.setTextColor(Color.rgb(24,76,108));
        stabilizerLabel.setTextSize(11);
        presetStabilizerRow.addView(stabilizerLabel);
        SeekBar stabilizerBar = new SeekBar(this);
        stabilizerBar.setMax(100);
        stabilizerBar.setProgress(stabilizer);
        stabilizerBar.setPadding(2,0,2,0);
        stabilizerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean user) {
                stabilizer = value;
                stabilizerLabel.setText("Stab " + value);
                drawingView.setStabilizer(value);
                if(user) saveBrushPreferences();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        presetStabilizerRow.addView(stabilizerBar,new LinearLayout.LayoutParams(0,30,1));
        tools.addView(presetStabilizerRow);
        android.content.res.ColorStateList accent = android.content.res.ColorStateList.valueOf(Color.rgb(30,155,202));
        size.setProgressTintList(accent); size.setThumbTintList(accent);
        opacity.setProgressTintList(accent); opacity.setThumbTintList(accent);
        stabilizerBar.setProgressTintList(accent); stabilizerBar.setThumbTintList(accent);
        tools.removeView(toolRow);
        LinearLayout workspace = new LinearLayout(this);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        workspace.setBackground(glassGradient(Color.rgb(223,247,250),Color.rgb(177,221,231),Color.rgb(255,255,255),dp(0)));
        workspace.addView(toolRow,new LinearLayout.LayoutParams(dp(56),-1));
        canvasStage=new FrameLayout(this);
        canvasStage.addView(drawingView,new FrameLayout.LayoutParams(-1,-1));
        workspace.addView(canvasStage,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
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

    private void showShapeMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this,anchor);
        menu.getMenu().add("Line");
        menu.getMenu().add("Rectangle");
        menu.getMenu().add("Ellipse");
        menu.setOnMenuItemClickListener(item -> {
            String selected=item.getTitle().toString();
            drawingView.setEyedropper(false);
            if("Line".equals(selected)) {
                drawingView.setShapeTool(1);
                setActiveTool("LINE");
            } else if("Rectangle".equals(selected)) {
                drawingView.setShapeTool(2);
                setActiveTool("RECT");
            } else {
                drawingView.setShapeTool(3);
                setActiveTool("ELLIPSE");
            }
            return true;
        });
        menu.show();
    }

    private void showMoreActions() {
        String[] actions = {"Reset canvas view","Clear selection","Duplicate selection",
                "Flip selection horizontally","Flip selection vertically","Delete selection",
                "Select all","Toggle grid","Toggle symmetry","Change canvas background","Grid spacing",
                "Built-in Anime Base Models","Import 3D model (.obj)"};
        new AlertDialog.Builder(this)
                .setTitle("Studio tools")
                .setItems(actions,(dialog,which) -> {
                    switch(which) {
                        case 0: drawingView.resetView(); break;
                        case 1: drawingView.clearSelection(); break;
                        case 2: drawingView.duplicateSelection(); break;
                        case 3: drawingView.flipSelection(true); break;
                        case 4: drawingView.flipSelection(false); break;
                        case 5: drawingView.deleteSelection(); break;
                        case 6: drawingView.selectAll(); break;
                        case 7:
                            drawingView.setGridEnabled(!drawingView.isGridEnabled());
                            Toast.makeText(this,drawingView.isGridEnabled()?"Grid on":"Grid off",Toast.LENGTH_SHORT).show();
                            break;
                        case 8:
                            drawingView.setSymmetryEnabled(!drawingView.isSymmetryEnabled());
                            Toast.makeText(this,drawingView.isSymmetryEnabled()?"Symmetry on":"Symmetry off",Toast.LENGTH_SHORT).show();
                            break;
                        case 9:
                            showCanvasBackgroundOptions();
                            break;
                        case 10:
                            String[] gridOptions={"25 px","50 px","100 px","200 px","400 px"};
                            new AlertDialog.Builder(this).setTitle("Grid spacing (current: "+drawingView.getGridSpacing()+" px)")
                                    .setItems(gridOptions,(gridDialog,gridChoice)->{
                                        int[] spacings={25,50,100,200,400};
                                        drawingView.setGridSpacing(spacings[gridChoice]);
                                        Toast.makeText(this,"Grid spacing: "+spacings[gridChoice]+" px",Toast.LENGTH_SHORT).show();
                                    }).show();
                            break;
                        case 11:
                            showBuiltInAnimeBases();
                            break;
                        case 12:
                            open3DModel();
                            break;
                    }
                }).show();
    }

    private void showBuiltInAnimeBases() {
        String[] models={"Standing anime base","T-pose reference base","Seated anime base"};
        new AlertDialog.Builder(this).setTitle("Anime Base Model Library")
                .setItems(models,(dialog,which)->addAnimeBaseToCanvas(which,models[which])).show();
    }

    private void updateCanvas3DPlacement() {
        if(canvasStage==null || canvasAnimeBase==null || drawingView==null) return;
        int viewWidth=drawingView.getWidth(), viewHeight=drawingView.getHeight();
        if(viewWidth<=0 || viewHeight<=0) return;
        float fit=Math.min((float)viewWidth/1600f,(float)viewHeight/newCanvasHeight);
        float displayScale=fit*drawingView.zoom;
        int width=Math.max(1,Math.round(newCanvasWidth*displayScale));
        int height=Math.max(1,Math.round(newCanvasHeight*displayScale));
        float centerX=viewWidth/2f+drawingView.panX;
        float centerY=viewHeight/2f+drawingView.panY;
        int left=Math.round(centerX-width/2f);
        int top=Math.round(centerY-height/2f);
        ViewGroup.LayoutParams raw=canvasAnimeBase.getLayoutParams();
        FrameLayout.LayoutParams lp;
        if(raw instanceof FrameLayout.LayoutParams) lp=(FrameLayout.LayoutParams)raw;
        else lp=new FrameLayout.LayoutParams(width,height);
        boolean changed=lp.width!=width || lp.height!=height || lp.leftMargin!=left || lp.topMargin!=top;
        lp.width=width; lp.height=height; lp.gravity=Gravity.TOP|Gravity.LEFT;
        lp.leftMargin=left; lp.topMargin=top;
        if(changed) canvasAnimeBase.setLayoutParams(lp);
        if(canvasAnimeBase.getRotation()!=drawingView.rotation)
            canvasAnimeBase.setRotation(drawingView.rotation);
    }

    private void addAnimeBaseToCanvas(int model,String name) {
        if(canvasStage==null || drawingView==null) {
            Toast.makeText(this,"Open a canvas first",Toast.LENGTH_SHORT).show();
            return;
        }
        if(canvasAnimeBase!=null) {
            canvasAnimeBase.onPause();
            canvasStage.removeView(canvasAnimeBase);
        }
        canvasAnimeBase=new AnimeBaseView(this,model);
        canvas3DLayerName=name;
        canvas3DVisible=true;
        canvas3DOpacity=100;
        canvasAnimeBase.setInteractionEnabled(false);
        canvasAnimeBase.setModelOpacity(1f);
        canvasStage.addView(canvasAnimeBase,new FrameLayout.LayoutParams(1,1));
        updateCanvas3DPlacement();
        baseAdjustMode=false;

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(5),dp(3),dp(5),dp(3));
        controls.setBackground(glassGradient(Color.WHITE,Color.rgb(178,220,240),Color.WHITE,dp(12)));
        Button modeButton=new Button(this);
        modeButton.setText("Adjust");
        modeButton.setTextSize(10);
        modeButton.setAllCaps(false);
        controls.addView(modeButton,new LinearLayout.LayoutParams(dp(76),dp(36)));
        modeButton.setOnClickListener(v->{
            baseAdjustMode=!baseAdjustMode;
            if(canvasAnimeBase!=null) canvasAnimeBase.setInteractionEnabled(baseAdjustMode);
            modeButton.setText(baseAdjustMode?"Draw":"Adjust");
            Toast.makeText(this,baseAdjustMode?"3D reference adjustment enabled":"Drawing enabled",Toast.LENGTH_SHORT).show();
        });
        Button hideButton=new Button(this);
        hideButton.setText("Hide");
        hideButton.setTextSize(10);
        hideButton.setAllCaps(false);
        controls.addView(hideButton,new LinearLayout.LayoutParams(dp(68),dp(36)));
        hideButton.setOnClickListener(v->{
            if(canvasAnimeBase!=null) {
                boolean visible=canvasAnimeBase.getVisibility()==View.VISIBLE;
                canvasAnimeBase.setVisibility(visible?View.INVISIBLE:View.VISIBLE);
                canvas3DVisible=!visible;
                hideButton.setText(visible?"Show":"Hide");
            }
        });
        Button removeButton=new Button(this);
        removeButton.setText("×");
        removeButton.setTextSize(14);
        removeButton.setAllCaps(false);
        controls.addView(removeButton,new LinearLayout.LayoutParams(dp(44),dp(36)));
        removeButton.setOnClickListener(v->{
            if(canvasAnimeBase!=null) {
                canvasAnimeBase.onPause();
                canvasStage.removeView(canvasAnimeBase);
                canvasAnimeBase=null;
            }
            baseAdjustMode=false;
            canvas3DLayerName=null;
            canvas3DControls=null;
            canvas3DVisible=false;
            canvasStage.removeView(controls);
        });
        FrameLayout.LayoutParams controlParams=new FrameLayout.LayoutParams(-2,dp(42),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        controlParams.topMargin=dp(6);
        canvasStage.addView(controls,controlParams);
        canvas3DControls=controls;
        canvasAnimeBase.onResume();
        Toast.makeText(this,name+" added as a canvas reference. Choose Adjust to rotate it, then Draw to draw over it.",Toast.LENGTH_LONG).show();
    }

    private void showAnimeBaseViewer(int model,String name) {
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        TextView instructions=new TextView(this);
        instructions.setText("Drag with one finger to rotate • Pinch with two fingers to zoom\\nBuilt-in 3D drawing reference");
        instructions.setTextColor(Color.rgb(25,74,112));
        instructions.setPadding(dp(10),dp(8),dp(10),dp(8));
        panel.addView(instructions);
        AnimeBaseView modelView=new AnimeBaseView(this,model);
        panel.addView(modelView,new LinearLayout.LayoutParams(-1,dp(430)));
        AlertDialog viewer=new AlertDialog.Builder(this).setTitle(name)
                .setView(panel).setPositiveButton("Close",null).create();
        viewer.setOnShowListener(d->modelView.onResume());
        viewer.setOnDismissListener(d->modelView.onPause());
        viewer.show();
    }

    private static final int REQUEST_3D_MODEL = 7401;

    private void open3DModel() {
        android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                new String[]{"model/obj","text/plain","application/octet-stream"});
        startActivityForResult(intent,REQUEST_3D_MODEL);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_3D_MODEL || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri modelUri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(modelUri,
                    data.getFlags() & (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch(Exception ignored) {}
        try {
            ObjModelView modelView=new ObjModelView(this,modelUri);
            LinearLayout panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            TextView instructions=new TextView(this);
            instructions.setText("Drag to rotate  •  Pinch to zoom\\nOBJ files supported; materials and textures are not loaded yet.");
            instructions.setTextColor(Color.rgb(25,74,112));
            instructions.setPadding(dp(10),dp(8),dp(10),dp(8));
            panel.addView(instructions);
            panel.addView(modelView,new LinearLayout.LayoutParams(-1,dp(390)));
            AlertDialog viewer=new AlertDialog.Builder(this).setTitle("3D Reference")
                    .setView(panel).setPositiveButton("Close",null).create();
            viewer.setOnDismissListener(dialog -> modelView.onPause());
            viewer.show();
            viewer.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(25,100,150));
            modelView.onResume();
        } catch(Exception error) {
            new AlertDialog.Builder(this).setTitle("Could not open 3D model")
                    .setMessage(error.getMessage()==null?"This OBJ file could not be read.":error.getMessage())
                    .setPositiveButton("OK",null).show();
        }
    }

    private void showCanvasBackgroundOptions() {
        String[] options={"Choose colour","White","Transparent"};
        new AlertDialog.Builder(this)
                .setTitle("Canvas background")
                .setItems(options,(dialog,which)->{
                    if(which==0) {
                        int background=drawingView.getCanvasBackground();
                        showColorWheel(background==Color.TRANSPARENT?Color.WHITE:background,false,
                                color -> {
                                    drawingView.setCanvasBackground(color);
                                    Toast.makeText(this,"Canvas background changed",Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        drawingView.setCanvasBackground(which==1?Color.WHITE:Color.TRANSPARENT);
                        Toast.makeText(this,which==1?"White background applied":"Transparent background applied",Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void gridButtonLabelRefresh(LinearLayout toolbar, boolean enabled) {
        for (int i=0;i<toolbar.getChildCount();i++) {
            View child=toolbar.getChildAt(i);
            if (child instanceof Button && (((Button)child).getText().toString().startsWith("Grid"))) {
                ((Button)child).setText(enabled ? "Grid ON" : "Grid OFF");
                return;
            }
        }
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

    private GradientDrawable glassGradient(int topColor, int bottomColor, int borderColor, int radius) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, bottomColor});
        background.setCornerRadius(radius);
        background.setStroke(dp(1), borderColor);
        return background;
    }

    private GradientDrawable bevel(boolean pressed) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                pressed ? new int[]{Color.rgb(190,239,255),Color.rgb(83,180,222),Color.rgb(35,126,184)}
                        : new int[]{Color.WHITE,Color.rgb(226,246,252),Color.rgb(151,211,235)});
        d.setCornerRadius(dp(9));
        d.setStroke(dp(1),pressed?Color.rgb(35,132,187):Color.rgb(255,255,255));
        return d;
    }

    private void setActiveTool(String tool) {
        activeToolLabel.setText(tool);
        styleToolButton(brushToolButton,"BRUSH".equals(tool));
        styleToolButton(eraserToolButton,"ERASER".equals(tool));
        styleToolButton(fillToolButton,"FILL".equals(tool));
        styleToolButton(shapeToolButton,"LINE".equals(tool)||"RECT".equals(tool)||"ELLIPSE".equals(tool));
        styleToolButton(eyedropperToolButton,"PICKER".equals(tool));
        styleToolButton(selectionToolButton,"SELECT".equals(tool));
    }

    private void styleToolButton(Button button, boolean active) {
        if (button == null) return;
        String type = button == brushToolButton ? "brush"
                : button == eraserToolButton ? "eraser"
                : button == fillToolButton ? "fill"
                : button == shapeToolButton ? "shape"
                : button == eyedropperToolButton ? "picker" : "select";
        button.setText("");
        button.setContentDescription(type + " tool");
        AeroToolIcon icon = new AeroToolIcon(type,active);
        icon.setBounds(0,0,dp(28),dp(28));
        button.setCompoundDrawables(null,icon,null,null);
        button.setCompoundDrawablePadding(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(1,1,1,1);
        button.setElevation(dp(active?4:1));
        button.setBackgroundColor(Color.TRANSPARENT);
    }

    // Glossy, three-dimensional Frutiger Aero tool artwork.
    private class AeroToolIcon extends android.graphics.drawable.Drawable {
        private final String type;
        private final boolean active;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AeroToolIcon(String type, boolean active) { this.type=type; this.active=active; }

        private void fill(Canvas c, Paint paint, int color) {
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
            paint.setStrokeWidth(1f); c.drawPath(new Path(),paint);
        }
        private void strokePath(Canvas c, Path path, int color, float width) {
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color); c.drawPath(path,p);
        }
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width) {
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(width); p.setColor(color); c.drawLine(x1,y1,x2,y2,p);
        }
        private void rounded(Canvas c,float l,float t,float r,float b,float radius,int[] colors) {
            p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(l,t,l,b,colors,null,Shader.TileMode.CLAMP));
            c.drawRoundRect(l,t,r,b,radius,radius,p); p.setShader(null);
        }
        private void drawTool(Canvas c) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if ("brush".equals(type)) {
                Path shaft=new Path(); shaft.moveTo(10,27); shaft.lineTo(25,10);
                strokePath(c,shaft,Color.rgb(30,67,112),7);
                strokePath(c,shaft,Color.rgb(20,126,235),4);
                line(c,13,23,24,11,Color.WHITE,1.3f);
                rounded(c,7,23,15,29,3,new int[]{Color.rgb(255,220,125),Color.rgb(171,83,28)});
                Path bristles=new Path(); bristles.moveTo(7,25); bristles.quadTo(3,29,5,31);
                bristles.quadTo(11,31,14,27); strokePath(c,bristles,Color.rgb(119,62,25),2.6f);
                line(c,11,22,16,17,Color.rgb(205,220,230),2);
            } else if ("eraser".equals(type)) {
                Path e=new Path(); e.moveTo(8,22); e.lineTo(19,8); e.quadTo(21,6,24,9);
                e.lineTo(29,14); e.quadTo(31,16,29,18); e.lineTo(20,29); e.close();
                p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(8,8,24,30,
                    new int[]{Color.WHITE,Color.rgb(255,147,176),Color.rgb(210,65,117)},null,Shader.TileMode.CLAMP));
                c.drawPath(e,p); p.setShader(null); strokePath(c,e,Color.rgb(80,105,145),1.5f);
                line(c,10,23,19,29,Color.WHITE,1.4f);
            } else if ("fill".equals(type)) {
                Path bucket=new Path(); bucket.moveTo(8,14); bucket.lineTo(24,14);
                bucket.lineTo(22,26); bucket.quadTo(16,30,10,26); bucket.close();
                p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(9,14,23,28,
                    new int[]{Color.WHITE,Color.rgb(80,185,255),Color.rgb(0,92,202)},null,Shader.TileMode.CLAMP));
                c.drawPath(bucket,p); p.setShader(null); strokePath(c,bucket,Color.rgb(35,75,130),1.8f);
                Path rim=new Path(); rim.moveTo(7,14); rim.quadTo(16,9,25,14);
                strokePath(c,rim,Color.WHITE,3); strokePath(c,rim,Color.rgb(100,185,245),1.2f);
                Path drop=new Path(); drop.moveTo(25,23); drop.quadTo(21,28,25,30);
                drop.quadTo(30,28,25,23); p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(22,23,28,30,Color.WHITE,Color.rgb(0,150,255),Shader.TileMode.CLAMP));
                c.drawPath(drop,p); p.setShader(null);
            } else if ("line".equals(type)) {
                line(c,7,27,27,7,Color.rgb(25,75,135),5);
                line(c,7,26,27,6,Color.WHITE,2.5f);
                line(c,8,25,26,7,Color.rgb(45,185,255),1.2f);
            } else if ("rect".equals(type) || "ellipse".equals(type)) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.5f); p.setColor(Color.WHITE);
                if("rect".equals(type)) c.drawRoundRect(7,8,27,26,2,2,p);
                else c.drawOval(7,8,27,26,p);
                p.setStrokeWidth(1.2f); p.setColor(Color.rgb(0,105,220));
                if("rect".equals(type)) c.drawRoundRect(7,8,27,26,2,2,p);
                else c.drawOval(7,8,27,26,p);
            } else if ("shape".equals(type)) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.2f); p.setColor(Color.WHITE);
                c.drawRoundRect(7,7,27,27,3,3,p);
                p.setStrokeWidth(1.2f); p.setColor(Color.rgb(0,93,180)); c.drawRoundRect(7,7,27,27,3,3,p);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(10,9,23,25,new int[]{Color.WHITE,Color.rgb(120,225,255),Color.rgb(0,103,208)},null,Shader.TileMode.CLAMP));
                c.drawCircle(23,23,3,p); p.setShader(null);
            } else if ("picker".equals(type)) {
                line(c,9,25,24,9,Color.rgb(38,65,105),7);
                line(c,9,24,24,8,Color.rgb(195,225,245),4);
                line(c,10,23,22,10,Color.WHITE,1.2f);
                p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(6,24,12,31,
                    Color.rgb(100,240,255),Color.rgb(0,110,230),Shader.TileMode.CLAMP));
                c.drawCircle(8,27,3.2f,p); p.setShader(null);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{3,2},0));
                p.setColor(Color.WHITE); c.drawRoundRect(7,8,27,26,2,2,p); p.setPathEffect(null);
                line(c,7,8,12,8,Color.rgb(0,90,195),1);
            }
        }
        @Override public void draw(Canvas canvas) {
            Rect bounds=getBounds(); int save=canvas.save();
            canvas.translate(bounds.left,bounds.top); canvas.scale(bounds.width()/36f,bounds.height()/36f);
            // Dark underside, polished chrome rim, and translucent aqua glass face.
            p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(Color.rgb(22,83,137));
            canvas.drawRoundRect(1,2,35,35,8,8,p);
            rounded(canvas,1,1,35,34,8,new int[]{Color.WHITE,Color.rgb(132,204,237),Color.rgb(32,111,187)});
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(Color.WHITE);
            canvas.drawRoundRect(2,2,34,33,7,7,p);
            rounded(canvas,4,4,32,31,6,active
                ? new int[]{Color.rgb(170,239,255),Color.rgb(26,154,236),Color.rgb(0,76,178)}
                : new int[]{Color.rgb(210,249,255),Color.rgb(52,177,239),Color.rgb(0,111,205)});
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(Color.rgb(180,242,255));
            canvas.drawRoundRect(4,4,32,31,6,6,p);
            p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(5,5,18,18,
                new int[]{0xCCFFFFFF,0x44FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
            canvas.drawOval(5,4,25,15,p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(0x99FFFFFF);
            canvas.drawArc(5,5,31,31,205,125,false,p);
            drawTool(canvas);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { p.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 36; }
        @Override public int getIntrinsicHeight() { return 36; }
    }

    private Button addButton(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(parent.getOrientation()==LinearLayout.VERTICAL ? 21 : 12);
        b.setTextColor(Color.rgb(24,75,108));
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT);
        b.setPadding(3,0,3,0);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},bevel(true));
        states.addState(new int[]{},bevel(false));
        b.setBackground(states);
        b.setMinHeight(32);
        b.setMinimumHeight(32);
        LinearLayout.LayoutParams lp = parent.getOrientation()==LinearLayout.VERTICAL
                ? new LinearLayout.LayoutParams(-1,46)
                : new LinearLayout.LayoutParams(0,38,1);
        lp.setMargins(2,2,2,2);
        parent.addView(b,lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showColorWheel() { showColorWheel(currentColor,true,null); }

    private void showColorWheel(int initialColor,boolean applyToBrush,java.util.function.IntConsumer onSelected) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(12),dp(8),dp(12),dp(4));

        ColorWheelView wheel = new ColorWheelView();
        wheel.setColor(initialColor);
        TextView preview = new TextView(this);
        preview.setText("Selected colour");
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Color.BLACK);
        preview.setBackgroundColor(initialColor);
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
        final int[] selected = {initialColor};
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
                    // Keep the currently selected tool (especially Bucket Fill) active.
                    if(applyToBrush) currentColor = selected[0];
                    if(onSelected!=null) onSelected.accept(selected[0]);
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
            // Keep the wheel fully visible even when the current drawing colour is black.
            // Brightness is adjusted separately with the slider.
            brightness=1f;
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
                // 3D references are independent scene layers, never flattened into paint bitmaps.
                if(canvasAnimeBase!=null) {
                    LinearLayout referenceRow=new LinearLayout(MainActivity.this);
                    referenceRow.setGravity(Gravity.CENTER_VERTICAL);
                    referenceRow.setPadding(dp(8),dp(6),dp(8),dp(6));
                    referenceRow.setBackground(glassGradient(Color.rgb(238,250,255),Color.rgb(195,230,245),Color.WHITE,dp(8)));
                    TextView modelIcon=new TextView(MainActivity.this);
                    modelIcon.setText("◇");
                    modelIcon.setTextSize(25);
                    modelIcon.setTextColor(Color.rgb(35,105,155));
                    referenceRow.addView(modelIcon,new LinearLayout.LayoutParams(dp(42),dp(48)));
                    TextView modelTitle=new TextView(MainActivity.this);
                    modelTitle.setText("3D  •  "+(canvas3DLayerName==null?"Anime reference":canvas3DLayerName));
                    modelTitle.setTextSize(13);
                    modelTitle.setTextColor(Color.rgb(25,74,112));
                    modelTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    referenceRow.addView(modelTitle,new LinearLayout.LayoutParams(0,dp(48),1));
                    CheckBox modelVisible=new CheckBox(MainActivity.this);
                    modelVisible.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35,105,155)));
                    modelVisible.setChecked(canvas3DVisible);
                    modelVisible.setOnCheckedChangeListener((button,checked)->{
                        canvas3DVisible=checked;
                        if(canvasAnimeBase!=null) canvasAnimeBase.setVisibility(checked?View.VISIBLE:View.INVISIBLE);
                    });
                    referenceRow.addView(modelVisible);
                    Button adjust3D=new Button(MainActivity.this);
                    adjust3D.setText("Adjust");
                    adjust3D.setTextSize(10);
                    adjust3D.setAllCaps(false);
                    referenceRow.addView(adjust3D,new LinearLayout.LayoutParams(dp(72),dp(42)));
                    adjust3D.setOnClickListener(v->{
                        baseAdjustMode=true;
                        if(canvasAnimeBase!=null) canvasAnimeBase.setInteractionEnabled(true);
                        Toast.makeText(MainActivity.this,"3D layer selected: drag to rotate, pinch to zoom. Tap Draw when finished.",Toast.LENGTH_SHORT).show();
                    });
                    LinearLayout opacityRow=new LinearLayout(MainActivity.this);
                    opacityRow.setGravity(Gravity.CENTER_VERTICAL);
                    opacityRow.setPadding(dp(10),0,dp(10),dp(5));
                    TextView opacityTitle=new TextView(MainActivity.this);
                    opacityTitle.setText("3D opacity");
                    opacityTitle.setTextSize(12);
                    opacityTitle.setTextColor(Color.rgb(25,74,112));
                    opacityRow.addView(opacityTitle,new LinearLayout.LayoutParams(dp(82),dp(38)));
                    SeekBar opacitySlider=new SeekBar(MainActivity.this);
                    opacitySlider.setMax(100);
                    opacitySlider.setProgress(canvas3DOpacity);
                    opacityRow.addView(opacitySlider,new LinearLayout.LayoutParams(0,dp(38),1));
                    TextView opacityValue=new TextView(MainActivity.this);
                    opacityValue.setText(canvas3DOpacity+"%");
                    opacityValue.setTextSize(12);
                    opacityValue.setTextColor(Color.rgb(25,74,112));
                    opacityValue.setGravity(Gravity.CENTER);
                    opacityRow.addView(opacityValue,new LinearLayout.LayoutParams(dp(42),dp(38)));
                    opacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                        @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                            canvas3DOpacity=value;
                            opacityValue.setText(value+"%");
                            if(canvasAnimeBase!=null) canvasAnimeBase.setModelOpacity(value/100f);
                        }
                        @Override public void onStartTrackingTouch(SeekBar bar){}
                        @Override public void onStopTrackingTouch(SeekBar bar){}
                    });
                    rows.addView(referenceRow);
                    rows.addView(opacityRow);
                    View separator=new View(MainActivity.this);
                    separator.setBackgroundColor(Color.rgb(100,160,190));
                    rows.addView(separator,new LinearLayout.LayoutParams(-1,dp(2)));
                }
                ArrayList<String> names = drawingView.getLayerNames();
                for (int i=names.size()-1;i>=0;i--) {
                    final int index=i;
                    LinearLayout layerCard = new LinearLayout(MainActivity.this);
                    layerCard.setOrientation(LinearLayout.VERTICAL);
                    layerCard.setTag(i);
                    layerCard.setPadding(3,2,3,2);
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView dragHandle = new TextView(MainActivity.this);
                    dragHandle.setText("☰");
                    dragHandle.setTextSize(20);
                    dragHandle.setTextColor(Color.DKGRAY);
                    dragHandle.setGravity(Gravity.CENTER);
                    dragHandle.setPadding(10,8,10,8);
                    dragHandle.setContentDescription("Drag to reorder layer");
                    row.addView(dragHandle,new LinearLayout.LayoutParams(42,48));
                    dragHandle.setOnLongClickListener(v -> {
                        android.content.ClipData data=android.content.ClipData.newPlainText("layer",String.valueOf(index));
                        v.startDrag(data,new View.DragShadowBuilder(layerCard),layerCard,0);
                        return true;
                    });
                    ImageView thumbnail = new ImageView(MainActivity.this);
                    Bitmap layerPreview = drawingView.getLayerThumbnail(i);
                    thumbnail.setImageBitmap(layerPreview);
                    thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    thumbnail.setBackground(glassGradient(Color.WHITE,Color.rgb(225,244,250),Color.rgb(160,205,225),dp(5)));
                    LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(42),dp(52));
                    thumbParams.setMargins(dp(3),dp(2),dp(7),dp(2));
                    row.addView(thumbnail,thumbParams);

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
                    TextView alphaLabel = new TextView(MainActivity.this);
                    alphaLabel.setText("Lock α");
                    alphaLabel.setTextSize(10);
                    alphaLabel.setTextColor(Color.BLACK);
                    row.addView(alphaLabel);
                    CheckBox alphaLock = new CheckBox(MainActivity.this);
                    alphaLock.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
                    alphaLock.setChecked(drawingView.isLayerAlphaLocked(index));
                    alphaLock.setOnCheckedChangeListener((button,checked) ->
                            drawingView.setLayerAlphaLocked(index,checked));
                    row.addView(alphaLock);
                    TextView clipLabel = new TextView(MainActivity.this);
                    clipLabel.setText("Clip");
                    clipLabel.setTextSize(10);
                    clipLabel.setTextColor(Color.BLACK);
                    row.addView(clipLabel);
                    CheckBox clip = new CheckBox(MainActivity.this);
                    clip.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
                    clip.setChecked(drawingView.isLayerClipped(index));
                    clip.setEnabled(index>0);
                    clip.setOnCheckedChangeListener((button,checked) -> drawingView.setLayerClipped(index,checked));
                    row.addView(clip);
                    layerCard.addView(row);
                    layerCard.setOnDragListener((target,event) -> {
                        switch(event.getAction()) {
                            case android.view.DragEvent.ACTION_DRAG_STARTED:
                                return event.getClipDescription()!=null;
                            case android.view.DragEvent.ACTION_DRAG_LOCATION: {
                                Object state=event.getLocalState();
                                if(!(state instanceof View)) return true;
                                View moving=(View)state;
                                int fromPosition=rows.indexOfChild(moving);
                                int targetPosition=rows.indexOfChild(layerCard);
                                if(fromPosition<0 || targetPosition<0 || moving==layerCard) return true;

                                // Move only the visible card while the finger is held.
                                // Keep its original model-index tag unchanged. The actual
                                // canvas stack is reordered once, on ACTION_DROP, using the
                                // original index and the final visual position.
                                int insertPosition=targetPosition;
                                if(event.getY()>layerCard.getHeight()/2f) insertPosition++;
                                if(insertPosition>fromPosition) insertPosition--;
                                insertPosition=Math.max(0,Math.min(rows.getChildCount()-1,insertPosition));
                                if(insertPosition!=fromPosition) {
                                    android.transition.TransitionManager.beginDelayedTransition(rows);
                                    rows.removeView(moving);
                                    rows.addView(moving,insertPosition);
                                }
                                return true;
                            }
                            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                                return true;
                            case android.view.DragEvent.ACTION_DRAG_EXITED:
                                return true;
                            case android.view.DragEvent.ACTION_DROP: {
                                Object state=event.getLocalState();
                                if(state instanceof View) {
                                    View moving=(View)state;
                                    Object fromTag=moving.getTag();
                                    int finalVisualPosition=rows.indexOfChild(moving);
                                    int count=names.size();
                                    if(fromTag instanceof Integer && finalVisualPosition>=0) {
                                        int fromModel=(Integer)fromTag;
                                        int toModel=count-1-finalVisualPosition;
                                        if(drawingView.moveLayer(fromModel,toModel)) refresh[0].run();
                                    }
                                }
                                return true;
                            }
                            case android.view.DragEvent.ACTION_DRAG_ENDED:
                                return true;
                        }
                        return true;
                    });
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
                    layerCard.addView(opacityRow);
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
                    layerCard.addView(blendRow);
                    View line = new View(MainActivity.this);
                    line.setBackgroundColor(Color.DKGRAY);
                    layerCard.addView(line,new LinearLayout.LayoutParams(-1,1));
                    rows.addView(layerCard);
                }
            }
        };
        refresh[0].run();

        LinearLayout actions = new LinearLayout(this);
        addButton(actions,"+ Add",() -> {
            if (drawingView.addLayer()) refresh[0].run();
            else Toast.makeText(this,"Layer limit reached (6)",Toast.LENGTH_SHORT).show();
        });
        addButton(actions,"Duplicate",() -> {
            if (drawingView.duplicateActiveLayer()) refresh[0].run();
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


    // Save the next fatal error so we can diagnose crashes on the user's phone.
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                String report = "Anime Drawing Studio crash report\\n"
                        + "Android: " + Build.VERSION.RELEASE + "\\n"
                        + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\\n"
                        + "Thread: " + thread.getName() + "\\n\\n" + trace;
                File file = new File(getFilesDir(), "last_crash.txt");
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(report.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    output.getFD().sync();
                }
            } catch (Throwable ignored) {
                // Never prevent Android's normal crash handling.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private void showPreviousCrashReport() {
        File file = new File(getFilesDir(), "last_crash.txt");
        if (!file.isFile()) return;
        try {
            byte[] bytes = new byte[(int)Math.min(file.length(), 60000)];
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                int count = input.read(bytes);
                String report = new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8);
                new AlertDialog.Builder(this)
                        .setTitle("A previous crash was recorded")
                        .setMessage("Please copy this report and send it to me. It contains technical error details, not your drawings.\\n\\n" + report)
                        .setPositiveButton("Copy report", (dialog, which) -> {
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crash report", report));
                            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "A crash was recorded but its report could not be opened.", Toast.LENGTH_LONG).show();
        }
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
        private final int canvasWidth;
        private final int canvasHeight;
        private int canvasBackground;
        private class Layer {
            Bitmap bitmap;
            String name;
            boolean visible=true;
            int opacity=100;
            int blendMode=0;
            boolean alphaLocked=false;
            boolean clipToBelow=false;
            Layer(String label) {
                name=label;
                bitmap=Bitmap.createBitmap(canvasWidth,canvasHeight,Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.TRANSPARENT);
            }
            Layer(String label,Bitmap source) {
                name=label;
                if(source==null || source.isRecycled())
                    throw new IllegalStateException("Source layer bitmap is unavailable");
                bitmap=source.copy(Bitmap.Config.ARGB_8888,true);
                // Bitmap.copy() can return null on some devices instead of throwing.
                // Never add a layer without a valid pixel buffer.
                if(bitmap==null)
                    throw new OutOfMemoryError("Layer bitmap copy failed");
            }
        }
        private class Snapshot {
            int layerIndex;
            Bitmap bitmap;
            boolean regionMove=false;
            int sourceX,sourceY,destX,destY;
            Bitmap sourceBitmap,destinationBitmap;
            Snapshot(int index,Bitmap image) { layerIndex=index; bitmap=image; }
            Snapshot(int index,int sx,int sy,int dx,int dy,Bitmap source,Bitmap destination) {
                layerIndex=index; regionMove=true;
                sourceX=sx; sourceY=sy; destX=dx; destY=dy;
                sourceBitmap=source; destinationBitmap=destination;
            }
            void recycle() {
                if(bitmap!=null&&!bitmap.isRecycled()) bitmap.recycle();
                if(sourceBitmap!=null&&!sourceBitmap.isRecycled()) sourceBitmap.recycle();
                if(destinationBitmap!=null&&!destinationBitmap.isRecycled()) destinationBitmap.recycle();
                bitmap=null; sourceBitmap=null; destinationBitmap=null;
            }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint liveEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        private boolean eyedropperTool=false;
        private boolean pickerPreviewVisible=false;
        private float pickerPreviewX=0f,pickerPreviewY=0f;
        private int pickerPreviewColor=Color.WHITE;
        private boolean selectionTool=false;
        private boolean selectionInProgress=false;
        private boolean hasSelection=false;
        private boolean movingSelection=false;
        private Bitmap movingSelectionBitmap=null;
        private float selectionStartX,selectionStartY,selectionEndX,selectionEndY;
        private float selectionMoveStartX,selectionMoveStartY,selectionMoveDX,selectionMoveDY;
        private float selectionMoveBaseDX,selectionMoveBaseDY;
        private int selectionBitmapLeft,selectionBitmapTop;
        private boolean symmetryEnabled=false;
        private boolean gridEnabled=false;
        private int gridSpacing=100;
        private int shapeTool=0;
        private boolean shapeInProgress=false;
        private float shapeStartX,shapeStartY,shapeEndX,shapeEndY;
        private float zoom=1f, panX=0f, panY=0f, rotation=0f;
        private float lastMidX, lastMidY, lastAngle;
        private boolean multiTouch=false, drawingGesture=false;
        private boolean pendingBucketFill=false;
        private int pendingFillX=0, pendingFillY=0;
        private final ScaleGestureDetector scaleDetector;

        DrawingView(int width,int height,int background) {
            super(MainActivity.this);
            canvasWidth=width; canvasHeight=height; canvasBackground=background;
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

        void setEyedropper(boolean enabled) {
            eyedropperTool=enabled;
            if(enabled) selectionTool=false;
        }
        void setSelectionTool(boolean enabled) {
            selectionTool=enabled;
            eyedropperTool=false;
            eraser=false;
            bucketTool=false;
            shapeTool=0;
            shapeInProgress=false;
            strokeInProgress=false;
            if(!enabled) finishSelectionMove();
            invalidate();
        }
        void selectAll() {
            if(movingSelection) finishSelectionMove();
            selectionInProgress=false;
            selectionStartX=0f;
            selectionStartY=0f;
            selectionEndX=canvasWidth;
            selectionEndY=canvasHeight;
            hasSelection=true;
            selectionTool=true;
            invalidate();
            Toast.makeText(MainActivity.this,"Entire canvas selected",Toast.LENGTH_SHORT).show();
        }
        void clearSelection() {
            if(movingSelection) finishSelectionMove();
            selectionInProgress=false;
            hasSelection=false;
            invalidate();
        }
        void duplicateSelection() {
            if(movingSelection) {
                Toast.makeText(MainActivity.this,"Finish the current move before duplicating",Toast.LENGTH_SHORT).show();
                return;
            }
            if(!hasSelection) {
                Toast.makeText(MainActivity.this,"Make a selection first",Toast.LENGTH_SHORT).show();
                return;
            }
            int left=Math.max(0,(int)Math.floor(Math.min(selectionStartX,selectionEndX)));
            int top=Math.max(0,(int)Math.floor(Math.min(selectionStartY,selectionEndY)));
            int right=Math.min(canvasWidth,(int)Math.ceil(Math.max(selectionStartX,selectionEndX)));
            int bottom=Math.min(canvasHeight,(int)Math.ceil(Math.max(selectionStartY,selectionEndY)));
            if(right<=left || bottom<=top) return;

            Bitmap copy=null;
            try {
                copy=Bitmap.createBitmap(layers.get(activeLayer).bitmap,left,top,
                        right-left,bottom-top);
                saveUndo();
                int offsetX=(right+24<=canvasWidth) ? 24 : (left>=24 ? -24 : 0);
                int offsetY=(bottom+24<=canvasHeight) ? 24 : (top>=24 ? -24 : 0);
                Canvas target=new Canvas(layers.get(activeLayer).bitmap);
                target.drawBitmap(copy,left+offsetX,top+offsetY,null);
                selectionStartX=left+offsetX;
                selectionEndX=right+offsetX;
                selectionStartY=top+offsetY;
                selectionEndY=bottom+offsetY;
                selectionTool=true;
                hasSelection=true;
                invalidate();
                Toast.makeText(MainActivity.this,"Selection duplicated. Drag the copy to move it.",
                        Toast.LENGTH_SHORT).show();
            } catch (OutOfMemoryError memoryError) {
                System.gc();
                Toast.makeText(MainActivity.this,
                        "Not enough memory to duplicate this selection. Try a smaller area.",
                        Toast.LENGTH_LONG).show();
            } finally {
                if(copy!=null && !copy.isRecycled()) copy.recycle();
            }
        }
        void deleteSelection() {
            if(movingSelection) {
                Toast.makeText(MainActivity.this,"Finish the current move before deleting",Toast.LENGTH_SHORT).show();
                return;
            }
            if(!hasSelection) {
                Toast.makeText(MainActivity.this,"Make a selection first",Toast.LENGTH_SHORT).show();
                return;
            }
            if(layers.get(activeLayer).alphaLocked) {
                Toast.makeText(MainActivity.this,"Turn off Alpha Lock before deleting",Toast.LENGTH_SHORT).show();
                return;
            }
            int left=Math.max(0,(int)Math.floor(Math.min(selectionStartX,selectionEndX)));
            int top=Math.max(0,(int)Math.floor(Math.min(selectionStartY,selectionEndY)));
            int right=Math.min(canvasWidth,(int)Math.ceil(Math.max(selectionStartX,selectionEndX)));
            int bottom=Math.min(canvasHeight,(int)Math.ceil(Math.max(selectionStartY,selectionEndY)));
            if(right<=left || bottom<=top) return;
            saveUndo();
            Paint clearPaint=new Paint();
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            new Canvas(layers.get(activeLayer).bitmap).drawRect(left,top,right,bottom,clearPaint);
            clearPaint.setXfermode(null);
            invalidate();
            Toast.makeText(MainActivity.this,"Selected area cleared — Undo to restore",Toast.LENGTH_SHORT).show();
        }

        void flipSelection(boolean horizontal) {
            if(movingSelection) {
                Toast.makeText(MainActivity.this,"Finish the current move before flipping",Toast.LENGTH_SHORT).show();
                return;
            }
            if(!hasSelection) {
                Toast.makeText(MainActivity.this,"Make a selection first",Toast.LENGTH_SHORT).show();
                return;
            }
            if(layers.get(activeLayer).alphaLocked) {
                Toast.makeText(MainActivity.this,"Turn off Alpha Lock before flipping",Toast.LENGTH_SHORT).show();
                return;
            }
            int left=Math.max(0,(int)Math.floor(Math.min(selectionStartX,selectionEndX)));
            int top=Math.max(0,(int)Math.floor(Math.min(selectionStartY,selectionEndY)));
            int right=Math.min(canvasWidth,(int)Math.ceil(Math.max(selectionStartX,selectionEndX)));
            int bottom=Math.min(canvasHeight,(int)Math.ceil(Math.max(selectionStartY,selectionEndY)));
            int width=right-left, height=bottom-top;
            if(width<=0 || height<=0) return;
            saveUndo();
            Bitmap source=Bitmap.createBitmap(layers.get(activeLayer).bitmap,left,top,width,height);
            Matrix transform=new Matrix();
            if(horizontal) {
                transform.setScale(-1f,1f);
                transform.postTranslate(width,0f);
            } else {
                transform.setScale(1f,-1f);
                transform.postTranslate(0f,height);
            }
            Bitmap flipped=Bitmap.createBitmap(source,0,0,width,height,transform,false);
            Canvas target=new Canvas(layers.get(activeLayer).bitmap);
            Paint clear=new Paint();
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            target.drawRect(left,top,right,bottom,clear);
            clear.setXfermode(null);
            target.drawBitmap(flipped,left,top,null);
            source.recycle();
            flipped.recycle();
            invalidate();
            Toast.makeText(MainActivity.this,horizontal ? "Flipped horizontally" : "Flipped vertically",
                    Toast.LENGTH_SHORT).show();
        }
        private boolean pointInsideSelection(float px,float py) {
            float offsetX=movingSelection ? selectionMoveDX : 0f;
            float offsetY=movingSelection ? selectionMoveDY : 0f;
            return hasSelection && px>=Math.min(selectionStartX,selectionEndX)+offsetX
                    && px<=Math.max(selectionStartX,selectionEndX)+offsetX
                    && py>=Math.min(selectionStartY,selectionEndY)+offsetY
                    && py<=Math.max(selectionStartY,selectionEndY)+offsetY;
        }
        private void finishSelectionMove() {
            if(!movingSelection || movingSelectionBitmap==null) return;
            int width=movingSelectionBitmap.getWidth();
            int height=movingSelectionBitmap.getHeight();
            int destX=Math.max(0,Math.min(canvasWidth-width,Math.round(selectionBitmapLeft+selectionMoveDX)));
            int destY=Math.max(0,Math.min(canvasHeight-height,Math.round(selectionBitmapTop+selectionMoveDY)));
            Bitmap destinationBefore=null;
            try {
                destinationBefore=Bitmap.createBitmap(layers.get(activeLayer).bitmap,destX,destY,width,height);
                trimUndoHistory(undo);
                clearRedoHistory();
                Snapshot moveUndo=new Snapshot(activeLayer,selectionBitmapLeft,selectionBitmapTop,
                        destX,destY,movingSelectionBitmap,destinationBefore);
                undo.add(moveUndo);
                movingSelectionBitmap=null; // The undo snapshot now owns the original pixels.
                Canvas layerCanvas=new Canvas(layers.get(activeLayer).bitmap);
                layerCanvas.drawBitmap(moveUndo.sourceBitmap,destX,destY,null);
            } catch(OutOfMemoryError memoryError) {
                if(destinationBefore!=null&&!destinationBefore.isRecycled()) destinationBefore.recycle();
                // Keep the original artwork intact if a safe undo record cannot be allocated.
                Canvas restoreCanvas=new Canvas(layers.get(activeLayer).bitmap);
                restoreCanvas.drawBitmap(movingSelectionBitmap,selectionBitmapLeft,selectionBitmapTop,null);
                movingSelectionBitmap.recycle();
                movingSelectionBitmap=null;
                Toast.makeText(MainActivity.this,"Not enough memory to move this selection. Try a smaller area.",Toast.LENGTH_LONG).show();
                selectionMoveDX=selectionMoveDY=0f;
                selectionMoveBaseDX=selectionMoveBaseDY=0f;
                movingSelection=false;
                invalidate();
                return;
            }
            float appliedDX=destX-selectionBitmapLeft;
            float appliedDY=destY-selectionBitmapTop;
            selectionStartX+=appliedDX;
            selectionEndX+=appliedDX;
            selectionStartY+=appliedDY;
            selectionEndY+=appliedDY;
            selectionBitmapLeft=destX;
            selectionBitmapTop=destY;
            selectionMoveDX=selectionMoveDY=0f;
            selectionMoveBaseDX=selectionMoveBaseDY=0f;
            movingSelection=false;
            invalidate();
        }
        
        private int sampleCompositePixel(int x,int y) {
            Bitmap sample=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
            Canvas sampleCanvas=new Canvas(sample);
            sampleCanvas.drawColor(Color.WHITE);
            Rect sourceRect=new Rect(x,y,x+1,y+1);
            Rect destRect=new Rect(0,0,1,1);
            for (int i=0;i<layers.size();i++) {
                Layer layer=layers.get(i);
                boolean clippedOut=layer.clipToBelow && (i==0 || !layers.get(i-1).visible
                        || layers.get(i-1).opacity<=0 || Color.alpha(layers.get(i-1).bitmap.getPixel(x,y))==0);
                if (layer.visible && layer.opacity>0 && !clippedOut) {
                    configureLayerPaint(layer);
                    sampleCanvas.drawBitmap(layer.bitmap,sourceRect,destRect,layerPaint);
                }
            }
            resetLayerPaint();
            int result=sample.getPixel(0,0);
            sample.recycle();
            return result;
        }

        void resetView() {
            zoom=1f; panX=0f; panY=0f; rotation=0f;
            invalidate();
        }

        boolean isGridEnabled() { return gridEnabled; }
        void setGridEnabled(boolean enabled) { gridEnabled=enabled; invalidate(); }
        int getGridSpacing() { return gridSpacing; }
        void setGridSpacing(int spacing) {
            gridSpacing=Math.max(25,Math.min(400,spacing));
            gridEnabled=true;
            invalidate();
        }

        ArrayList<String> getLayerNames() {
            ArrayList<String> result=new ArrayList<>();
            for (Layer layer:layers) result.add(layer.name);
            return result;
        }
        Bitmap getLayerThumbnail(int index) {
            if(index<0 || index>=layers.size()) return null;
            Bitmap source=layers.get(index).bitmap;
            int width=72, height=99;
            Bitmap preview=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(preview);
            Paint checker=new Paint(Paint.ANTI_ALIAS_FLAG);
            checker.setColor(Color.rgb(238,246,250));
            canvas.drawColor(Color.WHITE);
            checker.setColor(Color.rgb(225,237,243));
            for(int y=0;y<height;y+=12) for(int x=0;x<width;x+=12)
                if(((x/12)+(y/12))%2==0) canvas.drawRect(x,y,x+12,y+12,checker);
            Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            Rect src=new Rect(0,0,source.getWidth(),source.getHeight());
            Rect dst=new Rect(0,0,width,height);
            canvas.drawBitmap(source,src,dst,imagePaint);
            return preview;
        }
        int getActiveLayer() { return activeLayer; }
        String getActiveLayerName() { return layers.get(activeLayer).name; }
        boolean isLayerVisible(int index) { return layers.get(index).visible; }
        int getLayerOpacity(int index) { return layers.get(index).opacity; }
        int getLayerBlendMode(int index) { return layers.get(index).blendMode; }
        boolean isLayerAlphaLocked(int index) { return layers.get(index).alphaLocked; }
        boolean isLayerClipped(int index) { return layers.get(index).clipToBelow; }
        void setLayerClipped(int index,boolean clipped) {
            if (index==0) clipped=false;
            layers.get(index).clipToBelow=clipped;
            invalidate();
        }
        void setLayerAlphaLocked(int index,boolean locked) {
            layers.get(index).alphaLocked=locked;
            invalidate();
        }
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

        // Clip the layer to the alpha of the layer immediately beneath it.
        private void drawLayerStack(Canvas target) {
            RectF bounds=new RectF(0,0,canvasWidth,canvasHeight);
            Paint clipMask=new Paint(Paint.ANTI_ALIAS_FLAG);
            clipMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            for(int i=0;i<layers.size();i++) {
                Layer layer=layers.get(i);
                if(!layer.visible || layer.opacity<=0) continue;
                if(layer.clipToBelow && i>0) {
                    Layer base=layers.get(i-1);
                    if(!base.visible || base.opacity<=0) continue;
                    configureLayerPaint(layer);
                    int save=target.saveLayer(bounds,layerPaint);
                    target.drawBitmap(layer.bitmap,0,0,null);
                    target.drawBitmap(base.bitmap,0,0,clipMask);
                    target.restoreToCount(save);
                    resetLayerPaint();
                } else {
                    configureLayerPaint(layer);
                    target.drawBitmap(layer.bitmap,0,0,layerPaint);
                    resetLayerPaint();
                }
            }
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
        boolean moveLayer(int from,int to) {
            if(from<0 || from>=layers.size() || to<0 || to>=layers.size() || from==to) return false;
            Layer selected=layers.get(activeLayer);
            Layer moving=layers.remove(from);
            layers.add(to,moving);
            activeLayer=layers.indexOf(selected);
            // Undo records refer to layer indexes; discard them after changing stack order.
            clearHistory();
            invalidate();
            return true;
        }
        boolean addLayer() {
            if (layers.size()>=6) return false;
            layers.add(new Layer("Layer "+(layers.size()+1)));
            activeLayer=layers.size()-1;
            invalidate();
            return true;
        }
        boolean duplicateActiveLayer() {
            if (layers.size()>=6) return false;
            Layer source=layers.get(activeLayer);
            Layer copy;
            try {
                copy=new Layer(source.name + " copy",source.bitmap);
            } catch (OutOfMemoryError memoryError) {
                System.gc();
                Toast.makeText(MainActivity.this,"Could not copy this layer. Please try again.",Toast.LENGTH_LONG).show();
                return false;
            } catch (RuntimeException copyError) {
                Toast.makeText(MainActivity.this,"This layer could not be copied safely.",Toast.LENGTH_LONG).show();
                return false;
            }
            copy.visible=source.visible;
            copy.opacity=source.opacity;
            copy.blendMode=source.blendMode;
            copy.alphaLocked=source.alphaLocked;
            copy.clipToBelow=source.clipToBelow;
            layers.add(activeLayer+1,copy);
            activeLayer=activeLayer+1;
            clearHistory();
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
        void setEraser(boolean value) { eraser=value; bucketTool=false; shapeTool=0; selectionTool=false; }
        void setBucketTool(boolean value) {
            bucketTool=value;
            eraser=false;
            shapeTool=0;
            selectionTool=false;
            if(value) {
                if(movingSelection) finishSelectionMove();
                selectionInProgress=false;
                strokeInProgress=false;
                shapeInProgress=false;
                pendingBucketFill=false;
            }
            invalidate();
        }
        void setShapeTool(int value) { shapeTool=Math.max(0,Math.min(3,value)); eraser=false; bucketTool=false; selectionTool=false; invalidate(); }
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

        // Render the in-progress stroke into an isolated layer and apply the same
        // alpha masks used by Alpha Lock and Clip to Below before showing it.
        private void drawMaskedPreview(Canvas target,int layerIndex,Runnable drawContent) {
            Layer active=layers.get(layerIndex);
            boolean useAlphaLock=active.alphaLocked;
            boolean useClip=active.clipToBelow && layerIndex>0;
            if(!useAlphaLock && !useClip) {
                drawContent.run();
                return;
            }
            int save=target.saveLayer(0,0,canvasWidth,canvasHeight,null);
            drawContent.run();
            Paint mask=new Paint(Paint.ANTI_ALIAS_FLAG);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            if(useAlphaLock) target.drawBitmap(active.bitmap,0,0,mask);
            if(useClip) target.drawBitmap(layers.get(layerIndex-1).bitmap,0,0,mask);
            mask.setXfermode(null);
            target.restoreToCount(save);
        }

        void clearHistory() {
            for (Snapshot item:undo) item.recycle();
            for (Snapshot item:redo) item.recycle();
            undo.clear(); redo.clear();
        }
        private void trimUndoHistory(ArrayList<Snapshot> history) {
            while(history.size()>=2) history.remove(0).recycle();
        }
        private void clearRedoHistory() {
            for(Snapshot item:redo) item.recycle();
            redo.clear();
        }
        void saveUndo() {
            trimUndoHistory(undo);
            Bitmap snapshot=layers.get(activeLayer).bitmap.copy(Bitmap.Config.ARGB_8888,false);
            undo.add(new Snapshot(activeLayer,snapshot));
            clearRedoHistory();
        }
        private Snapshot captureRegionState(int layerIndex,int sx,int sy,int dx,int dy,int width,int height) {
            Bitmap source=Bitmap.createBitmap(layers.get(layerIndex).bitmap,sx,sy,width,height);
            Bitmap destination=null;
            try {
                destination=Bitmap.createBitmap(layers.get(layerIndex).bitmap,dx,dy,width,height);
                return new Snapshot(layerIndex,sx,sy,dx,dy,source,destination);
            } catch(OutOfMemoryError error) {
                if(source!=null&&!source.isRecycled()) source.recycle();
                if(destination!=null&&!destination.isRecycled()) destination.recycle();
                throw error;
            }
        }
        private void restoreRegion(Snapshot item,boolean redoDirection) {
            Canvas canvas=new Canvas(layers.get(item.layerIndex).bitmap);
            if(redoDirection) {
                canvas.drawBitmap(item.sourceBitmap,item.sourceX,item.sourceY,null);
                canvas.drawBitmap(item.destinationBitmap,item.destX,item.destY,null);
            } else {
                canvas.drawBitmap(item.destinationBitmap,item.destX,item.destY,null);
                canvas.drawBitmap(item.sourceBitmap,item.sourceX,item.sourceY,null);
            }
            item.recycle();
            invalidate();
        }
        void restore(Snapshot snapshot) {
            if(snapshot.regionMove) {
                restoreRegion(snapshot,false);
                return;
            }
            Layer layer=layers.get(snapshot.layerIndex);
            Canvas c=new Canvas(layer.bitmap);
            c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            c.drawBitmap(snapshot.bitmap,0,0,null);
            snapshot.recycle();
            invalidate();
        }
        void undo() {
            if (undo.isEmpty()) return;
            Snapshot previous=undo.remove(undo.size()-1);
            try {
                Snapshot current;
                if(previous.regionMove) {
                    current=captureRegionState(previous.layerIndex,previous.sourceX,previous.sourceY,
                            previous.destX,previous.destY,
                            previous.sourceBitmap.getWidth(),previous.sourceBitmap.getHeight());
                    redo.add(current);
                    activeLayer=previous.layerIndex;
                    restoreRegion(previous,false);
                } else {
                    current=new Snapshot(previous.layerIndex,
                            layers.get(previous.layerIndex).bitmap.copy(Bitmap.Config.ARGB_8888,false));
                    redo.add(current);
                    activeLayer=previous.layerIndex;
                    restore(previous);
                }
            } catch(OutOfMemoryError error) {
                undo.add(previous);
                Toast.makeText(MainActivity.this,"Not enough memory to undo this change",Toast.LENGTH_SHORT).show();
            }
        }
        void redo() {
            if (redo.isEmpty()) return;
            Snapshot next=redo.remove(redo.size()-1);
            try {
                trimUndoHistory(undo);
                Snapshot current;
                if(next.regionMove) {
                    current=captureRegionState(next.layerIndex,next.sourceX,next.sourceY,next.destX,next.destY,
                            next.sourceBitmap.getWidth(),next.sourceBitmap.getHeight());
                    undo.add(current);
                    activeLayer=next.layerIndex;
                    restoreRegion(next,true);
                } else {
                    current=new Snapshot(next.layerIndex,
                            layers.get(next.layerIndex).bitmap.copy(Bitmap.Config.ARGB_8888,false));
                    undo.add(current);
                    activeLayer=next.layerIndex;
                    restore(next);
                }
            } catch(OutOfMemoryError error) {
                redo.add(next);
                Toast.makeText(MainActivity.this,"Not enough memory to redo this change",Toast.LENGTH_SHORT).show();
            }
        }
        // PaintMan-style contiguous fill. The visible composite is sampled so
        // line art on a separate layer can define the regions to be coloured.
        void fillAt(int startX,int startY) {
            if (!layers.get(activeLayer).visible) {
                Toast.makeText(MainActivity.this,"Selected layer is hidden",Toast.LENGTH_SHORT).show();
                return;
            }
            final int width=canvasWidth, height=canvasHeight, total=width*height;
            startX=Math.max(0,Math.min(width-1,startX));
            startY=Math.max(0,Math.min(height-1,startY));
            Bitmap composite=exportBitmap();
            int[] source=new int[total];
            int[] target=new int[total];
            composite.getPixels(source,0,width,0,0,width,height);
            composite.recycle();
            Layer destination=layers.get(activeLayer);
            destination.bitmap.getPixels(target,0,width,0,0,width,height);
            final int start=startY*width+startX;
            final int sampled=source[start];
            final int replacement=currentColor;
            if (colorDistance(sampled,replacement)<8) return;

            // Visited is separate from the pixel data so no colour can be mistaken
            // for a marker. Enqueue one seed per contiguous run to avoid queue blow-up.
            final boolean[] visited=new boolean[total];
            int[] queue=new int[4096];
            int head=0,tail=0,capacity=queue.length;
            queue[tail++]=start;
            // Use a stricter colour match when zoomed so anti-aliased outline edges
            // are less likely to be crossed by the bucket's contiguous-region search.
            final int tolerance=zoom>=2f ? 22 : 32;
            boolean changed=false;
            while(head<tail) {
                int seed=queue[head++];
                if(visited[seed] || colorDistance(source[seed],sampled)>tolerance) continue;
                int sy=seed/width, sx=seed%width;
                int left=sx, right=sx;
                while(left>0) {
                    int index=sy*width+left-1;
                    if(visited[index] || colorDistance(source[index],sampled)>tolerance) break;
                    left--;
                }
                while(right<width-1) {
                    int index=sy*width+right+1;
                    if(visited[index] || colorDistance(source[index],sampled)>tolerance) break;
                    right++;
                }
                for(int px=left;px<=right;px++) {
                    int index=sy*width+px;
                    visited[index]=true;
                    if(!destination.alphaLocked || Color.alpha(target[index])>0) {
                        target[index]=replacement;
                        changed=true;
                    }
                }
                for(int ny : new int[]{sy-1,sy+1}) {
                    if(ny<0 || ny>=height) continue;
                    int px=left;
                    while(px<=right) {
                        int index=ny*width+px;
                        if(!visited[index] && colorDistance(source[index],sampled)<=tolerance) {
                            if(tail==capacity) {
                                capacity=Math.min(total,capacity*2);
                                int[] grown=new int[capacity];
                                System.arraycopy(queue,0,grown,0,tail);
                                queue=grown;
                            }
                            queue[tail++]=index;
                            px++;
                            while(px<=right) {
                                int next=ny*width+px;
                                if(visited[next] || colorDistance(source[next],sampled)>tolerance) break;
                                px++;
                            }
                        } else px++;
                    }
                }
            }
            if(changed) {
                saveUndo();
                destination.bitmap.setPixels(target,0,width,0,0,width,height);
                invalidate();
            } else {
                Toast.makeText(MainActivity.this,"Nothing to fill on this layer",Toast.LENGTH_SHORT).show();
            }
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
        int getCanvasBackground() { return canvasBackground; }

        void setCanvasBackground(int color) {
            canvasBackground=color;
            invalidate();
        }

        Bitmap exportBitmap() {
            Bitmap merged=Bitmap.createBitmap(canvasWidth,canvasHeight,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(merged);
            if(canvasBackground!=Color.TRANSPARENT) c.drawColor(canvasBackground);
            drawLayerStack(c);
            return merged;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateCanvas3DPlacement();
            canvas.drawColor(Color.rgb(65,65,70));
            float scale=Math.min((float)getWidth()/1600f,(float)getHeight()/canvasHeight);
            canvas.save();
            canvas.translate(getWidth()/2f+panX,getHeight()/2f+panY);
            canvas.rotate(rotation);
            canvas.scale(scale*zoom,scale*zoom);
            canvas.translate(-(canvasWidth/2f),-(canvasHeight/2f));
            Paint background=new Paint();
            background.setColor(canvasBackground);
            if(canvasBackground!=Color.TRANSPARENT) {
                canvas.drawRect(0,0,canvasWidth,canvasHeight,background);
            } else {
                // Editor-only transparency checkerboard; it is not part of the drawing or PNG.
                Paint checker=new Paint();
                int tile=48;
                checker.setColor(Color.rgb(225,232,237));
                canvas.drawRect(0,0,canvasWidth,canvasHeight,checker);
                checker.setColor(Color.rgb(250,252,253));
                for(int cy=0;cy<canvasHeight;cy+=tile) {
                    for(int cx=0;cx<canvasWidth;cx+=tile) {
                        if(((cx/tile)+(cy/tile))%2==0)
                            canvas.drawRect(cx,cy,Math.min(cx+tile,canvasWidth),
                                    Math.min(cy+tile,canvasHeight),checker);
                    }
                }
            }
            drawLayerStack(canvas);
            // Editor-only canvas boundary: visible on transparent canvases,
            // but never included in the merged/exported PNG.
            if(canvasBackground==Color.TRANSPARENT) {
                Paint boundary=new Paint(Paint.ANTI_ALIAS_FLAG);
                boundary.setStyle(Paint.Style.STROKE);
                boundary.setColor(Color.WHITE);
                boundary.setAlpha(230);
                boundary.setStrokeWidth(5f);
                canvas.drawRect(1.5f,1.5f,canvasWidth-1.5f,canvasHeight-1.5f,boundary);
                boundary.setColor(Color.BLACK);
                boundary.setAlpha(190);
                boundary.setStrokeWidth(1.5f);
                canvas.drawRect(1.5f,1.5f,canvasWidth-1.5f,canvasHeight-1.5f,boundary);
            }
            if(movingSelection && movingSelectionBitmap!=null) {
                canvas.drawBitmap(movingSelectionBitmap,
                        selectionBitmapLeft+selectionMoveDX,selectionBitmapTop+selectionMoveDY,null);
            }
            if (strokeInProgress && !strokeEraser) {
                configureStrokePaint(previewPaint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                drawMaskedPreview(canvas,activeLayer,() ->
                        drawSymmetricPath(canvas,currentPath,previewPaint));
                previewPaint.setShader(null);
                previewPaint.setColorFilter(null);
                previewPaint.setMaskFilter(null);
            }
            if (shapeInProgress) {
                configureStrokePaint(previewPaint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                drawMaskedPreview(canvas,activeLayer,() ->
                        drawSymmetricShape(canvas,previewPaint,shapeTool,shapeStartX,shapeStartY,shapeEndX,shapeEndY));
                previewPaint.setShader(null);
                previewPaint.setColorFilter(null);
                previewPaint.setMaskFilter(null);
            }
            if (hasSelection || selectionInProgress) {
                Paint marquee = new Paint(Paint.ANTI_ALIAS_FLAG);
                marquee.setStyle(Paint.Style.STROKE);
                marquee.setStrokeWidth(3f/(scale*zoom));
                marquee.setColor(Color.WHITE);
                marquee.setPathEffect(new DashPathEffect(new float[]{10f/(scale*zoom),6f/(scale*zoom)},0));
                float moveX=movingSelection ? selectionMoveDX : 0f;
                float moveY=movingSelection ? selectionMoveDY : 0f;
                float left=Math.min(selectionStartX,selectionEndX)+moveX;
                float top=Math.min(selectionStartY,selectionEndY)+moveY;
                float right=Math.max(selectionStartX,selectionEndX)+moveX;
                float bottom=Math.max(selectionStartY,selectionEndY)+moveY;
                canvas.drawRect(left,top,right,bottom,marquee);
                marquee.setColor(Color.BLACK);
                marquee.setPathEffect(new DashPathEffect(new float[]{10f/(scale*zoom),6f/(scale*zoom)},10f/(scale*zoom)));
                canvas.drawRect(left,top,right,bottom,marquee);
            }
            if (gridEnabled) {
                Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                gridPaint.setColor(Color.argb(105,35,92,135));
                gridPaint.setStrokeWidth(1.8f/(scale*zoom));
                for (int gx=gridSpacing;gx<canvasWidth;gx+=gridSpacing) canvas.drawLine(gx,0,gx,canvasHeight,gridPaint);
                for (int gy=gridSpacing;gy<canvasHeight;gy+=gridSpacing) canvas.drawLine(0,gy,canvasWidth,gy,gridPaint);
            }
            if (symmetryEnabled) {
                Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
                axis.setColor(Color.rgb(40,120,220));
                axis.setStrokeWidth(2f / (scale*zoom));
                axis.setPathEffect(new DashPathEffect(new float[]{12f/(scale*zoom),8f/(scale*zoom)},0));
                canvas.drawLine((canvasWidth/2f),0f,(canvasWidth/2f),canvasHeight,axis);
            }
            canvas.restore();
            if(eyedropperTool && pickerPreviewVisible) {
                float lensX=pickerPreviewX;
                float lensY=Math.max(dp(34),pickerPreviewY-dp(62));
                Paint lens=new Paint(Paint.ANTI_ALIAS_FLAG);
                lens.setColor(Color.rgb(12,67,112)); canvas.drawCircle(lensX,lensY,dp(25),lens);
                lens.setColor(Color.WHITE); canvas.drawCircle(lensX,lensY,dp(22),lens);
                lens.setColor(pickerPreviewColor); canvas.drawCircle(lensX,lensY,dp(18),lens);
                lens.setStyle(Paint.Style.STROKE); lens.setStrokeWidth(dp(1.5f)); lens.setColor(Color.WHITE);
                canvas.drawCircle(lensX,lensY,dp(18),lens);
                lens.setStyle(Paint.Style.FILL); lens.setTextSize(dp(8)); lens.setTypeface(Typeface.DEFAULT_BOLD);
                lens.setTextAlign(Paint.Align.CENTER); lens.setColor(Color.WHITE);
                canvas.drawText("PICK",lensX,lensY+dp(3),lens);
            }
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
            float base=Math.min((float)getWidth()/1600f,(float)getHeight()/canvasHeight);
            float scale=base*zoom;
            float dx=event.getX()-getWidth()/2f-panX;
            float dy=event.getY()-getHeight()/2f-panY;
            double radians=Math.toRadians(-rotation);
            float x=(float)(Math.cos(radians)*dx-Math.sin(radians)*dy)/scale+(canvasWidth/2f);
            float y=(float)(Math.sin(radians)*dx+Math.cos(radians)*dy)/scale+(canvasHeight/2f);

            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    multiTouch=false;
                    if (selectionTool) {
                        if(pointInsideSelection(x,y)) {
                            if(movingSelection && movingSelectionBitmap!=null) {
                                selectionMoveStartX=x;
                                selectionMoveStartY=y;
                                selectionMoveBaseDX=selectionMoveDX;
                                selectionMoveBaseDY=selectionMoveDY;
                                drawingGesture=true;
                                return true;
                            }
                            if(layers.get(activeLayer).alphaLocked) {
                                Toast.makeText(MainActivity.this,"Turn off Alpha Lock to move this selection",Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            int left=Math.max(0,(int)Math.floor(Math.min(selectionStartX,selectionEndX)));
                            int top=Math.max(0,(int)Math.floor(Math.min(selectionStartY,selectionEndY)));
                            int right=Math.min(canvasWidth,(int)Math.ceil(Math.max(selectionStartX,selectionEndX)));
                            int bottom=Math.min(canvasHeight,(int)Math.ceil(Math.max(selectionStartY,selectionEndY)));
                            if(right>left && bottom>top) {
                                try {
                                    movingSelectionBitmap=Bitmap.createBitmap(layers.get(activeLayer).bitmap,left,top,right-left,bottom-top);
                                } catch (OutOfMemoryError memoryError) {
                                    if(movingSelectionBitmap!=null && !movingSelectionBitmap.isRecycled()) movingSelectionBitmap.recycle();
                                    movingSelectionBitmap=null;
                                    System.gc();
                                    Toast.makeText(MainActivity.this,"Not enough memory to move this selection. Try a smaller area.",Toast.LENGTH_LONG).show();
                                    return true;
                                }
                                selectionBitmapLeft=left;
                                selectionBitmapTop=top;
                                Canvas clearCanvas=new Canvas(layers.get(activeLayer).bitmap);
                                Paint clearPaint=new Paint();
                                clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                                clearCanvas.drawRect(left,top,right,bottom,clearPaint);
                                clearPaint.setXfermode(null);
                                movingSelection=true;
                                selectionMoveStartX=x;
                                selectionMoveStartY=y;
                                selectionMoveDX=selectionMoveDY=0f;
                                selectionMoveBaseDX=selectionMoveBaseDY=0f;
                                drawingGesture=true;
                                invalidate();
                                return true;
                            }
                        }
                        selectionStartX=Math.max(0f,Math.min(1600f,x));
                        selectionStartY=Math.max(0f,Math.min(canvasHeight,y));
                        selectionEndX=selectionStartX;
                        selectionEndY=selectionStartY;
                        selectionInProgress=true;
                        hasSelection=false;
                        drawingGesture=true;
                        invalidate();
                        return true;
                    }
                    if (eyedropperTool) {
                        if (x>=0 && y>=0 && x<canvasWidth && y<canvasHeight) {
                            pickerPreviewX=event.getX();
                            pickerPreviewY=event.getY();
                            pickerPreviewColor=sampleCompositePixel(Math.max(0,Math.min(1599,(int)x)),
                                    Math.max(0,Math.min(2199,(int)y)));
                            pickerPreviewVisible=true;
                            invalidate();
                        }
                        return true;
                    }
                    if (bucketTool) {
                        pendingBucketFill=(x>=0 && y>=0 && x<canvasWidth && y<canvasHeight);
                        pendingFillX=Math.max(0,Math.min(1599,Math.round(x)));
                        pendingFillY=Math.max(0,Math.min(2199,Math.round(y)));
                        return true;
                    }
                    drawingGesture=(x>=0 && y>=0 && x<=canvasWidth && y<=canvasHeight);
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
                    if(strokeEraser && !layers.get(activeLayer).alphaLocked) {
                        liveEraserPaint.setStyle(Paint.Style.STROKE);
                        liveEraserPaint.setStrokeCap(Paint.Cap.ROUND);
                        liveEraserPaint.setStrokeJoin(Paint.Join.ROUND);
                        liveEraserPaint.setStrokeWidth(strokeWidth);
                        liveEraserPaint.setColor(Color.TRANSPARENT);
                        liveEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        Canvas layerCanvas=new Canvas(layers.get(activeLayer).bitmap);
                        layerCanvas.drawCircle(x,y,strokeWidth/2f,liveEraserPaint);
                        if(symmetryEnabled) layerCanvas.drawCircle(1600f-x,y,strokeWidth/2f,liveEraserPaint);
                        liveEraserPaint.setXfermode(null);
                    }
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
                    // Multi-touch is reserved for canvas navigation, not selection.
                    selectionInProgress=false;
                    if(movingSelection) finishSelectionMove();
                    drawingGesture=false;
                    // A second finger means this is a zoom/rotate gesture, not a fill tap.
                    pendingBucketFill=false;
                    if (event.getPointerCount()>=2) {
                        lastMidX=(event.getX(0)+event.getX(1))/2f;
                        lastMidY=(event.getY(0)+event.getY(1))/2f;
                        lastAngle=(float)Math.toDegrees(Math.atan2(
                                event.getY(1)-event.getY(0),
                                event.getX(1)-event.getX(0)));
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if(eyedropperTool && event.getPointerCount()==1) {
                        pickerPreviewX=event.getX();
                        pickerPreviewY=event.getY();
                        if(x>=0 && y>=0 && x<canvasWidth && y<canvasHeight) {
                            pickerPreviewColor=sampleCompositePixel(Math.max(0,Math.min(1599,(int)x)),
                                    Math.max(0,Math.min(2199,(int)y)));
                            pickerPreviewVisible=true;
                        } else pickerPreviewVisible=false;
                        invalidate();
                        return true;
                    }
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
                    if(movingSelection) {
                        selectionMoveDX=selectionMoveBaseDX+x-selectionMoveStartX;
                        selectionMoveDY=selectionMoveBaseDY+y-selectionMoveStartY;
                        invalidate();
                        return true;
                    }
                    if (selectionInProgress) {
                        selectionEndX=Math.max(0f,Math.min(1600f,x));
                        selectionEndY=Math.max(0f,Math.min(canvasHeight,y));
                        invalidate();
                        return true;
                    }
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

                    float segmentStartX=lastX;
                    float segmentStartY=lastY;
                    if (activeStabilizer == 0) {
                        // Keep zero-stabilisation responsive, but smooth the small
                        // straight segments between finger samples into gentle curves.
                        float midX=(lastX+x)*0.5f;
                        float midY=(lastY+y)*0.5f;
                        currentPath.quadTo(lastX,lastY,midX,midY);
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
                    if(strokeEraser && !layers.get(activeLayer).alphaLocked) {
                        liveEraserPaint.setStyle(Paint.Style.STROKE);
                        liveEraserPaint.setStrokeCap(Paint.Cap.ROUND);
                        liveEraserPaint.setStrokeJoin(Paint.Join.ROUND);
                        liveEraserPaint.setStrokeWidth(strokeWidth);
                        liveEraserPaint.setColor(Color.TRANSPARENT);
                        liveEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        Path eraseSegment=new Path();
                        eraseSegment.moveTo(segmentStartX,segmentStartY);
                        eraseSegment.lineTo(lastX,lastY);
                        Canvas layerCanvas=new Canvas(layers.get(activeLayer).bitmap);
                        drawSymmetricPath(layerCanvas,eraseSegment,liveEraserPaint);
                        liveEraserPaint.setXfermode(null);
                    }
                    invalidate();
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    multiTouch=true;
                    return true;

                case MotionEvent.ACTION_UP:
                    if(eyedropperTool) {
                        if(pickerPreviewVisible) {
                            currentColor=pickerPreviewColor;
                            Toast.makeText(MainActivity.this,"Colour picked",Toast.LENGTH_SHORT).show();
                        }
                        eyedropperTool=false;
                        pickerPreviewVisible=false;
                        setActiveTool("BRUSH");
                        invalidate();
                        return true;
                    }
                    // A pending bucket tap must be handled before floating-selection
                    // completion; otherwise stale selection state can swallow the tap.
                    if (pendingBucketFill) {
                        if (!multiTouch && x>=0 && y>=0 && x<canvasWidth && y<canvasHeight) {
                            fillAt(Math.max(0,Math.min(1599,Math.round(x))),
                                    Math.max(0,Math.min(2199,Math.round(y))));
                        }
                        pendingBucketFill=false;
                        multiTouch=false;
                        drawingGesture=false;
                        return true;
                    }
                    if(movingSelection) {
                        selectionMoveDX=selectionMoveBaseDX+x-selectionMoveStartX;
                        selectionMoveDY=selectionMoveBaseDY+y-selectionMoveStartY;
                        drawingGesture=false;
                        invalidate();
                        return true;
                    }
                    if (selectionInProgress) {
                        selectionEndX=Math.max(0f,Math.min(1600f,x));
                        selectionEndY=Math.max(0f,Math.min(canvasHeight,y));
                        selectionInProgress=false;
                        drawingGesture=false;
                        hasSelection=Math.abs(selectionEndX-selectionStartX)>=2f
                                && Math.abs(selectionEndY-selectionStartY)>=2f;
                        invalidate();
                        return true;
                    }
                    if (multiTouch || !drawingGesture) { multiTouch=false; drawingGesture=false; strokeInProgress=false; shapeInProgress=false; currentPath.reset(); invalidate(); return true; }
                    drawingGesture=false;
                    if (shapeInProgress) {
                        shapeEndX=x;
                        shapeEndY=y;
                        configureStrokePaint(paint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                        if (layers.get(activeLayer).alphaLocked) {
                            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                        }
                        drawSymmetricShape(new Canvas(layers.get(activeLayer).bitmap),paint,shapeTool,
                                shapeStartX,shapeStartY,shapeEndX,shapeEndY);
                        paint.setXfermode(null);
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
                        if(strokeEraser && layers.get(activeLayer).alphaLocked) {
                            Toast.makeText(MainActivity.this,"Turn off Alpha Lock before erasing",Toast.LENGTH_SHORT).show();
                            paint.setXfermode(null);
                        } else if(strokeEraser) {
                            paint.setColor(Color.TRANSPARENT);
                            paint.setAlpha(255);
                            paint.setShader(null);
                            paint.setColorFilter(null);
                            paint.setMaskFilter(null);
                            paint.setStrokeWidth(strokeWidth);
                        } else {
                            configureStrokePaint(paint,strokePreset,strokeColor,brushOpacity,strokeWidth);
                        }
                        if (layers.get(activeLayer).alphaLocked && !strokeEraser) {
                            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                        } else {
                            paint.setXfermode(strokeEraser ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
                        }
                        if (!(strokeEraser && layers.get(activeLayer).alphaLocked) && !strokeEraser) {
                            drawSymmetricPath(new Canvas(layers.get(activeLayer).bitmap),currentPath,paint);
                        }
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
                    pendingBucketFill=false;
                    drawingGesture=false;
                    strokeInProgress=false;
                    shapeInProgress=false;
                    selectionInProgress=false;
                    if(movingSelection) finishSelectionMove();
                    currentPath.reset();
                    paint.setXfermode(null);
                    invalidate();
                    return true;
            }
            return true;
        }
    }
}
