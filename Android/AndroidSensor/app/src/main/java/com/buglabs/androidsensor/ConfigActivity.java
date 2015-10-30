package com.buglabs.androidsensor;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.buglabs.dweetlib.DweetLib;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;


/**
 * Created by tjbuick on 15-07-24.
 * (c) BugLabs
 */
public class ConfigActivity extends Activity implements RadioGroup.OnCheckedChangeListener {



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        System.out.println("config oncreate");

        super.onCreate(savedInstanceState);

        // create action bar, with logo
        ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            System.out.println("action bar is null");
        } else {
            actionBar.setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            Drawable d = getResources().getDrawable(R.drawable.buglogo);
            LayoutInflater inflator = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflator.inflate(R.layout.action_bar, null);
            ActionBar.LayoutParams layout = new ActionBar.LayoutParams(ActionBar.LayoutParams.FILL_PARENT, ActionBar.LayoutParams.FILL_PARENT);
            getActionBar().setCustomView(v, layout);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.config_main);



        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);

        // load the saved thing name
        String thingName = preferences.getString("thingName", "");
        String autoThingName = preferences.getString("autoThingName", "");


        // change textview string if custom thing name has been selected
        TextView label = (TextView)findViewById(R.id.autoLabel);
        if (!thingName.equals(autoThingName)) {
            label.setText("current thing name");
        }

        // load/configure edittext
        final EditText tv = (EditText)findViewById(R.id.editText);
        tv.setText(thingName);
        tv.setSelection(thingName.length());
        tv.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tv.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                TextView label = (TextView)findViewById(R.id.changeText);
                label.setText("thing name updated");
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // don't allow spaces
                String result = s.toString().replaceAll(" ", "");
                if (!s.toString().equals(result)) {
                    tv.setText(result);
                    tv.setSelection(result.length());
                }
                System.out.println("text changed : "+s);
                // save the new thing name
                SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                String autoThingName = preferences.getString("autoThingName", "");
                editor.putString("thingName", s.toString());
                editor.commit();
                // adjust the textview if the thing name is custom or not
                TextView label = (TextView)findViewById(R.id.autoLabel);
                if (autoThingName.equals(s.toString())) {
                    label.setText("auto-assigned thing name");
                } else {
                    label.setText("current thing name");
                }

            }

        });


        // load saved dweet frequency
        int freq = preferences.getInt("dweetFreq",0);

        // setup the radiobuttons based on dweet frequency
        if (freq==1) {
            RadioButton b = (RadioButton)findViewById(R.id.halfsec);
            b.setChecked(true);
        } else if (freq==2) {
            RadioButton b = (RadioButton)findViewById(R.id.onesec);
            b.setChecked(true);
        } else if (freq==3) {
            RadioButton b = (RadioButton)findViewById(R.id.twosec);
            b.setChecked(true);
        } else if (freq==4) {
            RadioButton b = (RadioButton) findViewById(R.id.fivesec);
            b.setChecked(true);
        }

        final RadioGroup radios = (RadioGroup) findViewById(R.id.radiogroup);
        radios.setOnCheckedChangeListener(this);


    }




    @Override
    protected void onResume() {
        super.onResume();

        System.out.println("Config onResume");

    }


        @Override
    protected void onDestroy() {
        super.onDestroy();

        }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // allow the back button to close this activity
            case android.R.id.home:
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }



    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        // find the radiobutton by returned id
        RadioButton rb = (RadioButton) findViewById(checkedId);

        System.out.println("selected"+rb.getTag());

        // update the saved dweet frequency
        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("dweetFreq",Integer.valueOf((String)rb.getTag()));
        editor.commit();


    }




}
