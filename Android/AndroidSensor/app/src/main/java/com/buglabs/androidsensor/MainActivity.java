package com.buglabs.androidsensor;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.buglabs.dweetlib.DweetLib;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;


public class MainActivity extends Activity implements SensorEventListener,LocationListener {

    private Handler dweetHandler;

    private static SensorManager sensorService;
    private Sensor sensor;

    private static LocationManager locationManager;


    private SensorDataAdapter adapter;

    String thingName;
    int dweetFreq;


    private float ax,ay,az;
    private float rx,ry,rz;
    private float gx,gy,gz;
    private float light;
    private double longitude, latitude, altitude, heading, speed;

    String devmodel,devname,osver,headphonesConn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        devmodel = Build.MODEL;
        devname = Build.PRODUCT;
        osver = Build.VERSION.RELEASE;

        System.out.println("MODEL: "+android.os.Build.MODEL
                +"\nDEVICE: "+android.os.Build.DEVICE
                +"\nBRAND: "+android.os.Build.BRAND
                +"\nDISPLAY: "+android.os.Build.DISPLAY
                +"\nBOARD: "+android.os.Build.BOARD
                +"\nHOST: "+android.os.Build.HOST
                +"\nMANUFACTURER: "+android.os.Build.MANUFACTURER
                +"\nPRODUCT: "+android.os.Build.PRODUCT);

        PackageManager PM= this.getPackageManager();
        boolean gyro = PM.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);
        System.out.println("gyro allowed:"+gyro);

        // create action bar, with logo and menu
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
        }



        setContentView(R.layout.activity_main);




        // setup the shared preferences for storing thing name and dweet frequency
        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        ////////////////////////////////
        // remove this if you don't want the
        // thing name to randomize every time
        //editor.putString("thingName", "");
        //editor.commit();
        /////////////////////////////


        // get the saved thing name
        thingName = preferences.getString("thingName", "");

        // is there a saved thing name?
        if (thingName.equals("")) {

            // no, create a new random thing name
            String newThingName = "";

            AssetManager am = getAssets();
            InputStream is = null;
            ArrayList<String> stringArray = new ArrayList<String>();
            BufferedReader br = null;
            InputStreamReader inputStreamReader = null;
            try {
                is = am.open("nouns.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (is!=null) {
                String line = null;
                try {
                    inputStreamReader = new InputStreamReader(is);
                    br = new BufferedReader(inputStreamReader);
                    while ((line = br.readLine()) != null) {
                        stringArray.add(line);
                    }
                    Random r = new Random();
                    int rand1 = r.nextInt(stringArray.size());
                    newThingName = newThingName.concat(stringArray.get(rand1));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            newThingName = newThingName.concat("-");
            stringArray.clear();
            try {
                is = am.open("adjectives.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (is!=null) {
                String line = null;
                try {
                    inputStreamReader = new InputStreamReader(is);
                    br = new BufferedReader(inputStreamReader);
                    while ((line = br.readLine()) != null) {
                        stringArray.add(line);
                    }
                    Random r = new Random();
                    int rand1 = r.nextInt(stringArray.size());
                    newThingName = newThingName.concat(stringArray.get(rand1));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // save the new randomly generated thing name
            System.out.println(newThingName);
            editor.putString("thingName", newThingName);
            editor.putString("autoThingName", newThingName);
            editor.commit();

            thingName = newThingName;

        }

        // load/create the saved dweet frequency
        int freq = preferences.getInt("dweetFreq",0);
        if (freq==0) {
            editor.putInt("dweetFreq",2);
            editor.commit();
        }




        sensorService = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorService.registerListener(this,
                sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorService.registerListener(this,
                sensorService.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorService.registerListener(this,
                sensorService.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorService.registerListener(this,
                sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorService.registerListener(this,
                sensorService.getDefaultSensor(Sensor.TYPE_LIGHT),
                SensorManager.SENSOR_DELAY_NORMAL);

        List<Sensor> listSensor = sensorService.getSensorList(Sensor.TYPE_ALL);
        for(int i=0; i<listSensor.size(); i++)
        {
            System.out.println("Sensor : " + listSensor.get(i).getName());
        }


        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 3000, 10, this);




        adapter = new SensorDataAdapter();
        ListView lv = (ListView)findViewById(R.id.detailtable);
        lv.setAdapter(adapter);
        adapter.notifyDataSetChanged();



    }




    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);

        int freq = preferences.getInt("dweetFreq", 0);
        if (freq==1) dweetFreq=500;
        else if (freq==2) dweetFreq=1000;
        else if (freq==3) dweetFreq=2000;
        else if (freq==4) dweetFreq=5000;
        else dweetFreq=1000; // should never get here

        thingName = preferences.getString("thingName", "");

        if (dweetHandler!=null) {
            dweetHandler.removeCallbacks(runnable);
        }
        dweetHandler = new Handler();
        dweetHandler.postDelayed(runnable, dweetFreq);


        TextView tName = (TextView)findViewById(R.id.thingname);
        tName.setText(thingName);


        System.out.println("Config onResume");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        dweetHandler.removeCallbacks(runnable);
        sensorService.unregisterListener(this);
        locationManager.removeUpdates(this);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menu.clear();
        menu.add(Menu.NONE, R.id.dweet_cfg_menu_action, 100, "dweet config");
        menu.add(Menu.NONE, R.id.share_menu_action, 100, "share status");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.dweet_cfg_menu_action:
                // 'dweet config' selected, show the config activity
                System.out.println("dweet cfg hit");
                final Intent intent2 = new Intent(this, ConfigActivity.class);
                startActivityForResult(intent2, 100);
                return true;
            case R.id.share_menu_action:
                System.out.println("share cfg hit");
                // all of the below to allow a link to be shared
                // via email, twitter and mms
                Resources resources = getResources();

                Intent testIntent = new Intent(Intent.ACTION_VIEW);
                Uri data = Uri.parse("mailto:?subject=" + "Follow my iPhone sensor data" + "&body=https://dweet.io/follow/" + thingName);
                testIntent.setData(data);

                Intent openInChooser = Intent.createChooser(testIntent, "Select a sharing option");

                PackageManager pm = getPackageManager();
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                List<ResolveInfo> resInfo = pm.queryIntentActivities(sendIntent, 0);
                List<LabeledIntent> intentList = new ArrayList<LabeledIntent>();

                for (int i = 0; i < resInfo.size(); i++) {
                    // Extract the label, append it, and repackage it in a LabeledIntent
                    ResolveInfo ri = resInfo.get(i);
                    String packageName = ri.activityInfo.packageName;
                    if (packageName.contains("mms")) {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
                        intent.setAction(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, "Follow my iPhone sensor data : https://dweet.io/follow/" + thingName);
                        intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                    }
                    if (packageName.contains("twitter")) {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
                        intent.setAction(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, "Follow my iPhone sensor data : https://dweet.io/follow/" + thingName);
                        intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                    }
                }

                LabeledIntent[] extraIntents = intentList.toArray(new LabeledIntent[intentList.size()]);

                openInChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

                startActivity(openInChooser);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


        // dweet timer

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {

            System.out.println("in timer handler");

            DweetLib.DweetCallback cb = new DweetLib.DweetCallback() {
                @Override
                public void callback(ArrayList<Object> ar) {
                    Integer result = (Integer)ar.get(0);
                    System.out.println("in ConfigActivity -> inline called back with "+result);
                    if (result>=0) {
                        // all was good, update the table
                        // set the green status if it's not
                        // the first time through (starts as grey)
                        View dot = (View) findViewById(R.id.statusdot);
                        dot.setBackgroundColor(Color.GREEN);
                    } else {
                        // dweet problem, set status to red
                        View dot = (View) findViewById(R.id.statusdot);
                        dot.setBackgroundColor(Color.RED);
                    }
                    // pulse the status dot
                    AlphaAnimation animation1 = new AlphaAnimation(1.0f, 0.3f);
                    animation1.setDuration(400);
                    animation1.setStartOffset(0);
                    View dot = (View) findViewById(R.id.statusdot);
                    dot.startAnimation(animation1);
                }
            };

            JSONObject jsonObj = null;
            try {
                // add all connected sensor data to JSON object
                jsonObj = new JSONObject();
                jsonObj.put("acc_x", String.format("%.2f",ax));
                jsonObj.put("acc_y", String.format("%.2f",ay));
                jsonObj.put("acc_z", String.format("%.2f",az));
                jsonObj.put("dev_model", devmodel);
                jsonObj.put("dev_product", devname);
                jsonObj.put("os_version", osver);
                jsonObj.put("headphones_connected", headphonesConn);
                jsonObj.put("light_sensor", String.format("%.2f",light));
                jsonObj.put("latitude", latitude);
                jsonObj.put("longitude", longitude);
                jsonObj.put("altitude", String.format("%.2f",altitude));
                jsonObj.put("heading", String.format("%.2f",heading));
                jsonObj.put("speed", String.format("%.2f",speed));



                String str = DweetLib.getInstance(getBaseContext()).sendDweet(jsonObj,thingName,"",this,cb,true);
            } catch (JSONException e) {
                System.out.println("cfg dweet error");
            }

            // restart the dweet timer
            dweetHandler.postDelayed(this, dweetFreq);
        }

    };





    // sensor updates

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        System.out.println("accuracy changed");
    }
    public void onSensorChanged(SensorEvent event) {
        //System.out.println("Sensor update:"+event.sensor.getName());
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float[] values = event.values;
            ax = values[0];
            ay = values[1];
            az = values[2];

            //  System.out.println("new acc: "+ax+" "+ay+" "+az);
        }
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            float[] values = event.values;
            rx = values[0];
            ry = values[1];
            rz = values[2];

           // System.out.println("new rot: "+rx+" "+ry+" "+rz);
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

            float[] values = event.values;
            gx = values[0];
            gy = values[1];
            gz = values[2];

          //  System.out.println("new gyro: "+gx+" "+gy+" "+gz);
        }
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
         //   System.out.println("new light: " + event.values[0]);
            light = event.values[0];
        }

        AudioManager am1 = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if (am1.isWiredHeadsetOn()) {
            headphonesConn="true";
        } else {
            headphonesConn="false";
        }

        adapter.notifyDataSetChanged();

    }





    // GPS updates

    @Override
    public void onLocationChanged(Location location) {
        longitude = location.getLongitude();
        latitude = location.getLatitude();
        altitude = location.getAltitude();
        heading = location.getBearing();
        speed = location.getSpeed();


        System.out.println("long/lat "+longitude+"/"+latitude);

        adapter.notifyDataSetChanged();

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }








    // sensor data table

    private class SensorDataAdapter extends BaseAdapter {
        private LayoutInflater inflator;

        public SensorDataAdapter() {
            super();
            inflator = MainActivity.this.getLayoutInflater();
        }

        @Override
        public int getCount() {
            return 16;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View view=null;

            if (position==0) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText("DEVICE");
                tv.setTextColor(Color.DKGRAY);
                view.setBackgroundColor(Color.LTGRAY);
            } else if (position==1) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Device Model");
                sv.setText(devmodel);
            } else if (position==2) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Device Product Name");
                sv.setText(devname);
                sv.setTextSize(10);
            }  else if (position==3) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("OS Version");
                sv.setText(osver);
            }  else if (position==4) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Light Sensor");
                sv.setText(String.format("%.2f lux", light));
            }  else if (position==5) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Headphones Attached");
                sv.setText(headphonesConn);
            } else if (position==6) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText("ORIENTATION");
                tv.setTextColor(Color.DKGRAY);
                view.setBackgroundColor(Color.LTGRAY);
            } else if (position==7) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Accelerometer : X");
                sv.setText(String.format("%.2f G",ax));
            } else if (position==8) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Accelerometer : Y");
                sv.setText(String.format("%.2f G",ay));
            } else if (position==9) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Accelerometer : Z");
                sv.setText(String.format("%.2f G", az));
            } else if (position==10) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText("GPS");
                tv.setTextColor(Color.DKGRAY);
                view.setBackgroundColor(Color.LTGRAY);
            } else if (position==11) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Latitude");
                sv.setText(String.format("%.2f°",latitude));
            } else if (position==12) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Longitude");
                sv.setText(String.format("%.2f°",longitude));
            } else if (position==13) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Altitude");
                sv.setText(String.format("%.2f m",altitude));
            } else if (position==14) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Heading");
                sv.setText(String.format("%.2f°",heading));
            } else if (position==15) {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText("Speed");
                sv.setText(String.format("%.2f m/s",speed));
            }



            return view;
        }
    }




}
