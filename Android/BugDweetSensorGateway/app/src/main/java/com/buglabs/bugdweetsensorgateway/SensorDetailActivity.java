package com.buglabs.bugdweetsensorgateway;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import com.buglabs.dweetlib.DweetLib;

import expr.*;

/**
 * Created by tjbuick on 15-07-24.
 * (c) BugLabs
 */
public class SensorDetailActivity extends Activity {


    private static final String UPDATE_NOTIFICATION = "00002902-0000-1000-8000-00805f9b34fb";


    public static String devName;
    public static String devAddress;
    private BluetoothGatt btGatt;

    private Queue<BluetoothGattDescriptor> descriptorQueue = new LinkedList<BluetoothGattDescriptor>();


    private SensorDataAdapter adapter;

    String thingName;
    int dweetFreq;

    private Handler dweetHandler;
    private Handler tableHandler;

    JSONObject deviceConfigJson;
    private HashMap<Object,Object> characteristicData;
    private HashMap<String,String> dweetData;
    private HashMap<Object,Object> characteristicToServiceMap;

    private HashMap<String,Object> tableData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


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
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.sensor_detail);


        // extract device name and address from the intent 'extra area'
        final Intent intent = getIntent();
        devName = intent.getStringExtra("NAME");
        devAddress = intent.getStringExtra("ADDRESS");
        // get device config data
        try {
            deviceConfigJson = new JSONObject(intent.getStringExtra("CONFIG"));
        } catch (JSONException e) {
            e.printStackTrace();
            this.finish();
        }

        // build config data maps
        characteristicData = new HashMap<>();
        characteristicToServiceMap = new HashMap<>();
        dweetData = new HashMap<>();
        try {
            JSONObject services = deviceConfigJson.getJSONObject("services");
            Iterator<String> serv_uuids = services.keys();
            while(serv_uuids.hasNext()) {
                JSONObject serv = services.getJSONObject(serv_uuids.next());
                System.out.println("found service : " + serv.getString("name"));
                if (serv.getBoolean("active")) {
                    JSONObject characteristics = serv.getJSONObject("characteristics");
                    Iterator<String> char_uuids = characteristics.keys();
                    while (char_uuids.hasNext()) {
                        String char_uuid = char_uuids.next();
                        JSONObject cha = characteristics.getJSONObject(char_uuid);
                        System.out.println("-found characteristic : " + cha.getString("name") + " = " + char_uuid);
                        characteristicData.put(char_uuid, cha);
                        characteristicToServiceMap.put(char_uuid, serv.getString("name"));
                    }
                } else {
                    System.out.println("service INACTIVE");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }



        // create sensor table
        tableData = new HashMap<>();
        adapter = new SensorDataAdapter();
        ListView lv = (ListView)findViewById(R.id.detailtable);
        lv.setAdapter(adapter);
        adapter.notifyDataSetChanged();



        System.out.println("connecting to " + devName + " (" + devAddress + ")");

        // get all BTLE services
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        BluetoothDevice btDevice = btAdapter.getRemoteDevice(devAddress);

        // connect to GATT
        btGatt = btDevice.connectGatt(null, false, btleGattCallback);

        // create write queue for BTLE notifications
        descriptorQueue = new LinkedList<BluetoothGattDescriptor>();

        // load saved thing name and dweet frequency
        SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
        thingName = preferences.getString("thingName", "");
        int df = preferences.getInt("dweetFreq",2);
        if (df==1) dweetFreq=500;
        else if (df==2) dweetFreq=1000;
        else if (df==3) dweetFreq=2000;
        else if (df==4) dweetFreq=5000;
        else dweetFreq=1000; // should never get here

        // create handler for sending dweet
        dweetHandler = new Handler();
        dweetHandler.postDelayed(runnable, dweetFreq);

        // create a handler for updating the table
        tableHandler = new Handler();
        tableHandler.postDelayed(tableUpdate, 500);

        // set UI components
        TextView dName = (TextView)findViewById(R.id.devname);
        dName.setText(devName);
        TextView tName = (TextView)findViewById(R.id.thingname);
        tName.setText(thingName);

        setResult(101);

    }


    @Override
    protected void onDestroy() {

        // disconnect GATT and stop dweet timer
        descriptorQueue.clear();
        btGatt.close();
        btGatt.disconnect();
        super.onDestroy();
        dweetHandler.removeCallbacks(runnable);
        tableHandler.removeCallbacks(tableUpdate);

    }


    private Runnable runnable = new Runnable() {

        DweetLib.DweetCallback cb = new DweetLib.DweetCallback() {
            @Override
            public void callback(ArrayList<Object> ar) {
                Integer result = (Integer)ar.get(0);
                System.out.println("in ConfigActivity -> inline called back with " + result);
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

        @Override
        public void run() {
            System.out.println("in timer handler");

            // check to see if any characteristics are connected
            if (dweetData.size()>0) {
                System.out.println("-will send dweet");
                JSONObject jsonObj = null;
                try {
                    // add all connected sensor data to JSON object
                    jsonObj = new JSONObject();
                    for (String name : dweetData.keySet()) {
                        jsonObj.put(name,dweetData.get(name));
                    }
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }

                if (jsonObj != null) {
                    // send the dweet
                    String str = DweetLib.getInstance(getBaseContext()).sendDweet(jsonObj,thingName,"",this,cb,true);
                }


            }

            // restart the dweet timer
            dweetHandler.postDelayed(this, dweetFreq);
        }
    };


    private Runnable tableUpdate = new Runnable() {
        @Override
        public void run() {
            adapter.notifyDataSetChanged();
            tableHandler.postDelayed(tableUpdate, 500);
        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        // only menu item is option to share dweet link
        menu.add(Menu.NONE, R.id.detail_share_menu_action, 100, "share status");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // allow the back button to close this activity
                this.finish();
                return true;
            case R.id.detail_share_menu_action:
                System.out.println("share cfg hit");
                // all of the below to allow a link to be shared
                // via email, twitter and mms
                Resources resources = getResources();

                Intent testIntent = new Intent(Intent.ACTION_VIEW);
                Uri data = Uri.parse("mailto:?subject=" + "Follow my sensor data" + "&body=https://dweet.io/follow/" + thingName);
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
                        intent.putExtra(Intent.EXTRA_TEXT, "Follow my sensor data : https://dweet.io/follow/"+thingName);
                        intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                    }
                    if (packageName.contains("twitter")) {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName(packageName, ri.activityInfo.name));
                        intent.setAction(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, "Follow my sensor data : https://dweet.io/follow/"+thingName);
                        intentList.add(new LabeledIntent(intent, packageName, ri.loadLabel(pm), ri.icon));
                    }
                }

                LabeledIntent[] extraIntents = intentList.toArray( new LabeledIntent[ intentList.size() ]);

                openInChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

                startActivity(openInChooser);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            //System.out.println("in gattCallback:char change");

            // one of the characteristics changed, walk through and find which one.
            // once found, extract the data specific to the characteristic.

            String uuid = representativeString(characteristic.getUuid());
            if (characteristicData.containsKey(uuid)) {
                JSONObject cha = (JSONObject)characteristicData.get(uuid);
                try {
                   // System.out.println(cha.getString("name")+" updated");

                    JSONObject treat = cha.getJSONObject("treatment");


                    int width = (int)treat.getInt("width");
                    double scale = treat.getDouble("scaling");
                    Boolean sign = treat.getBoolean("signed");
                    int count = treat.getInt("count");
                    String pp1 = treat.getString("postprocess1");
                    String pp2 = treat.getString("postprocess2");
                    String dn = treat.getString("dweetname");
                    String dv = treat.getString("dweetval");

                    System.out.println(cha.getString("name")+" updated ("+width+","+count+","+scale+")");


                    double value[] = new double[3];
                    byte d[] = characteristic.getValue();

                    int data[] = new int[6];
                    data[0]=data[1]=data[2]=data[3]=data[4]=data[5]=0;

                    if (count==1) {
                        data[0] = d[0] & 0xff;
                        data[1] = (d[1] & 0xff) << 8;
                        if (width == 24) {
                            data[2] = (d[2] & 0xff) << 16;
                        }
                    }
                    if (count==3 && width==16) {
                        data[0] = d[0] & 0xff;
                        data[1] = (d[1] & 0xff) << 8;
                        data[2] = d[2] & 0xff;
                        data[3] = (d[3] & 0xff) << 8;
                        data[4] = d[4] & 0xff;
                        data[5] = (d[5] & 0xff) << 8;
                    }

                    value[0]=value[1]=value[2]=0;

                    if (count==1) {
                        if (width==16) {
                            value[0] = (data[1]+data[0]);
                        } else if (width==24) {
                            value[0] = (data[2]+data[1]+data[0]);
                        }
                        if (sign && width==16) {
                            if (value[0]>=32768) value[0]-=65536;
                        }
                        value[0] = value[0]/scale;
                    } else if (count==3) {
                        if (width==16) {
                            value[0] = (data[1] + data[0]);
                            value[1] = (data[3] + data[2]);
                            value[2] = (data[5] + data[4]);
                        }
                        if (sign && width==16) {
                            if (value[0]>=32768) value[0]-=65536;
                            if (value[1]>=32768) value[1]-=65536;
                            if (value[2]>=32768) value[2]-=65536;
                        }
                        value[0] = value[0]/scale;
                        value[1] = value[1]/scale;
                        value[2] = value[2]/scale;
                    }

                    if (count==1) {
                        System.out.println("new value:"+value[0]);
                    } else if (count==3) {
                        System.out.println("new value:"+value[0]+","+value[1]+","+value[2]);
                    }

                    Expr expr;
                    double p1,p2;
                    p1=p2=0;

                    try {

                        if (pp1.length() > 0) {
                            pp1 = pp1.replace("%v0%", Double.toString(value[0]));
                            pp1 = pp1.replace("%v1%", Double.toString(value[1]));
                            pp1 = pp1.replace("%v2%", Double.toString(value[2]));

                            System.out.println("pp1:"+pp1);

                            expr = Parser.parse(pp1);
                            p1 = expr.value();

                            if (pp2.length() > 0) {
                                pp2 = pp2.replace("%v0%", Double.toString(value[0]));
                                pp2 = pp2.replace("%v1%", Double.toString(value[1]));
                                pp2 = pp2.replace("%v2%", Double.toString(value[2]));
                                pp2 = pp2.replace("%p1%", Double.toString(p1));

                                System.out.println("pp2:"+pp2);

                                expr = Parser.parse(pp2);
                                p2 = expr.value();
                            }

                        }
                    } catch (SyntaxException e) {
                        System.err.println(e.explain());
                    }


                    dv = dv.replace("%v0%",String.format("%.2f",value[0]));
                    dv = dv.replace("%v1%",String.format("%.2f",value[1]));
                    dv = dv.replace("%v2%",String.format("%.2f",value[2]));
                    dv = dv.replace("%p1%",String.format("%.2f",p1));
                    dv = dv.replace("%p2%",String.format("%.2f",p2));



                    dweetData.put(dn,dv);

                    String groupname = (String)characteristicToServiceMap.get(uuid);
                    HashMap<String,String> groupitems=null;
                    if (tableData.containsKey(groupname)) {
                        groupitems = (HashMap<String,String>)tableData.get(groupname);
                    } else {
                        groupitems = new HashMap<>();
                    }
                    groupitems.put(dn,dv);
                    tableData.put(groupname,groupitems);


                } catch (JSONException e) {
                    System.out.println("json exception");
                    e.printStackTrace();
                } catch (Exception e) {
                    System.out.println("gen exception");
                    e.printStackTrace();
                }
            }


        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            System.out.println("in gattCallback:state change");

            // BTLE stat changed.
            // once connected, do a GATT services discovery.
            // if disconnected, close the activity and report back the 'disconnect' code
            if (gatt==btGatt) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    System.out.println(" -- " + devAddress + " CONNECTED");
                    gatt.discoverServices();
                } else {
                    System.out.println(" -- " + devAddress + " NOT CONNECTED");
                    setResult(101);
                    finish();
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            System.out.println("in gattCallback:onDescriptorWrite");

            // GATT descriptor write needs to be in a queue, because queueing is
            // not handled automatically.
            descriptorQueue.remove();
            if (descriptorQueue.size() > 0) {
                // keep writing descriptors as long as we still
                // have them in the queue
                gatt.writeDescriptor(descriptorQueue.element());
            }

        }


        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            System.out.println("in gattCallback:service discovered");

            // GATT services discovered.
            // walk though services->characteristics and
            // look for specific uuids.
            // once found, set each one to notify and
            // add the descriptor write to the write queue
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services) {
                System.out.println("s--" + service.getUuid());
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    UUID u = characteristic.getUuid();
                    System.out.println("c--" + representativeString(u));
                    if (characteristicData.containsKey(representativeString(u))) {
                        JSONObject cha = (JSONObject) characteristicData.get(representativeString(u));
                        try {
                            System.out.println(cha.getString("name"));
                            if (cha.getBoolean("active")) {
                                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(UPDATE_NOTIFICATION));
                                System.out.println("----found descriptor " + descriptor.getUuid().toString());
                                gatt.setCharacteristicNotification(characteristic, true);
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                descriptorQueue.add(descriptor);
                                if (descriptorQueue.size() == 1) {
                                    gatt.writeDescriptor(descriptor);
                                }
                            } else {
                                System.out.println("INACTIVE");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }


    };


    private String representativeString (UUID u) {

        String outputString = "";
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());

        byte[] b = bb.array();
        int bytesToConvert = b.length;
        for (int currentByteIndex = bytesToConvert-1; currentByteIndex >= 0; currentByteIndex--)
        {
            switch (currentByteIndex)
            {
                case 12:
                case 10:
                case 8:
                case 6:outputString=String.format("%s%02X-",outputString,b[currentByteIndex]);break;
                default:outputString=String.format("%s%02X",outputString,b[currentByteIndex]);
            }
        }

        return outputString;

    }

    // sensor data table

    private class SensorDataAdapter extends BaseAdapter {
        private LayoutInflater inflator;

        public SensorDataAdapter() {
            super();
            inflator = SensorDetailActivity.this.getLayoutInflater();
        }

        @Override
        // hardcoded for sensors on BlueMS1
        public int getCount() {
            return dweetData.size()+tableData.size();
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

            View view;
            ArrayList<String> spots = new ArrayList<>();
            ArrayList<Boolean> headers = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            int i=0;
            SortedSet<String> keys = new TreeSet<String>(tableData.keySet());
            for (String group : keys) {
                spots.add(group);
                headers.add(true);
                values.add("");
                HashMap<String,String> items = (HashMap<String,String>)tableData.get(group);
                SortedSet<String> ikeys = new TreeSet<String>(items.keySet());
                for (String item : ikeys) {
                    spots.add(item);
                    headers.add(false);
                    values.add((String)items.get(item));
                }
            }

            if (headers.get(position)) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText(spots.get(position));
                tv.setTextColor(Color.DKGRAY);
                view.setBackgroundColor(Color.LTGRAY);
            } else {
                // build the table row based
                view = inflator.inflate(R.layout.sensor_data_item, null);
                TextView sn = (TextView) view.findViewById(R.id.sensorName);
                TextView sv = (TextView) view.findViewById(R.id.sensorValue);
                sn.setText(spots.get(position));
                sv.setText(values.get(position));
            }


            return view;
        }
    }

}