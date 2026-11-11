package com.sreenandan.animedrawingstudio;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setText("ANIME DRAWING STUDIO\n\nApp is working!");
        text.setTextSize(24);
        text.setTextColor(Color.BLACK);
        text.setBackgroundColor(Color.WHITE);
        text.setGravity(17);

        setContentView(text);
    }
}
