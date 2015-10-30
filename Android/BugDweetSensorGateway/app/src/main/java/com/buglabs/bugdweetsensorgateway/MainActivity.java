package com.buglabs.bugdweetsensorgateway;

import com.buglabs.bugdweetsensorgateway.R;
import com.buglabs.bugdweetsensorgateway.*;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Bundle;
import android.view.Window;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;

/**
 * Created by tjbuick on 15-07-08.
 * (c) BugLabs
 */
public class MainActivity extends Activity {

    private static int SPLASH_TIME_OUT = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);


        setContentView(R.layout.activity_main);



        ActionBar actionBar = getActionBar();
        if (actionBar==null) {
            System.out.println("action bar is null");
        } else {
            actionBar.hide();
        }

        System.out.println("MainActivity onCreate");

        LinearLayout layout = new LinearLayout(this);
        layout.setBackgroundResource(R.drawable.bg);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentView(layout);


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, DeviceScan.class));
                finish();
            }
        }, SPLASH_TIME_OUT);


    }


}
