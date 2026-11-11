
package com.sreenandan.animedrawingstudio;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView screen = new TextView(this);
        screen.setText("STARTING APP...");
        screen.setTextSize(22);
        screen.setTextColor(Color.BLACK);
        screen.setBackgroundColor(Color.WHITE);
        screen.setGravity(17);

        setContentView(screen);

        try {
            screen.setText("APP STARTED SUCCESSFULLY");
        } catch (Throwable error) {
            screen.setText(error.toString());
        }
    }
}
