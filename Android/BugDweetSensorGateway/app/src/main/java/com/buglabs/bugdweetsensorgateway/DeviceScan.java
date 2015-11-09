package com.buglabs.bugdweetsensorgateway;

import android.app.ActionBar;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by tjbuick on 15-07-08.
 * (c) BugLabs
 */
public class DeviceScan extends ListActivity {

    private HashMap<String,Object> deviceConfig;

    private BluetoothManager BtManager;
    private BluetoothAdapter BtAdapter;

    private DeviceScanAdapter deviceScanAdapter;

    private boolean isScanning;
    private Handler scanHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // create action bar, with logo and menu
        ActionBar actionBar = getActionBar();
        if (actionBar==null) {
            System.out.println("action bar is null");
        } else {
            actionBar.setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            Drawable d=getResources().getDrawable(R.drawable.buglogo);
            LayoutInflater inflator = (LayoutInflater) this .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflator.inflate(R.layout.action_bar, null);
            getActionBar().setCustomView(v);
        }



        System.out.println("DeviceScan onCreate");

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
        String thingName = preferences.getString("thingName", "");

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

        }


        // load/create the saved dweet frequency
        int freq = preferences.getInt("dweetFreq",0);
        if (freq==0) {
            editor.putInt("dweetFreq",2);
            editor.commit();
        }


        // create a generic handler for BTLE scan
        scanHandler = new Handler();


        // check to see if this device has BTLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BTLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // get the BTLE manager and adapter
        BtManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BtAdapter = BtManager.getAdapter();

        // final check for BTLE support
        if (BtAdapter == null) {
            Toast.makeText(this, "BTLE not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        String folder_main = Environment.getExternalStorageDirectory()+"/DweetGatewayCfg";
        deviceConfig = new HashMap<>();

        // Make sure external document folder exists
        File f = new File(folder_main);
        if (!f.exists()) {
            f.mkdirs();
        }
        File f1;

        // add static BlueMS1 config
        f1 = new File(folder_main, "BlueMS1.txt");

        // TEMP
        //f1.delete();

        if (!f1.exists()) {

            System.out.println("copying BlueMS1.txt");

            AssetManager is = this.getAssets();
            InputStream fis;
            try {

                fis = is.open("BlueMS1.txt");
                FileOutputStream fos;
                fos = new FileOutputStream(new File(folder_main,"BlueMS1.txt"));
                byte[] b = new byte[8];
                int i;
                while ((i = fis.read(b)) != -1) {
                    fos.write(b, 0, i);
                }
                fos.flush();
                fos.close();
                fis.close();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }

        }

        // add static Blue6180 config
        f1 = new File(folder_main, "Blue6180.txt");

        // TEMP
        //f1.delete();

        if (!f1.exists()) {

            System.out.println("copying Blue6180.txt");

            AssetManager is = this.getAssets();
            InputStream fis;
            try {

                fis = is.open("Blue6180.txt");
                FileOutputStream fos;
                fos = new FileOutputStream(new File(folder_main,"Blue6180.txt"));
                byte[] b = new byte[8];
                int i;
                while ((i = fis.read(b)) != -1) {
                    fos.write(b, 0, i);
                }
                fos.flush();
                fos.close();
                fis.close();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }

        }


        // Open all config files in the config directory
        File dirs = new File(folder_main);
        File[] files = dirs.listFiles();
        for (File fil : files) {
            System.out.println("-: "+fil.getName());

            File yourFile = new File(folder_main, fil.getName());
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(yourFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            String jsonStr = null;
            try {
                FileChannel fc = null;
                if (stream != null) {
                    fc = stream.getChannel();
                }
                MappedByteBuffer bb = null;
                try {
                    if (fc != null) {
                        bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (bb != null) {
                    jsonStr = Charset.defaultCharset().decode(bb).toString();
                }
            }
            finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            JSONObject jsonObj=null;
            if (jsonStr!=null) {
                try {
                    jsonObj = new JSONObject(jsonStr);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (jsonObj!=null) {
                try {
                    System.out.println("--: "+jsonObj.getString("devName"));
                    deviceConfig.put(jsonObj.getString("devName"),jsonObj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(":");

        }




    }

    @Override
    protected void onResume() {
        super.onResume();

        System.out.println("DeviceScan onResume");

        // recreate the device table datasource when this activity resume/starts
        deviceScanAdapter = new DeviceScanAdapter();
        setListAdapter(deviceScanAdapter);
        deviceScanAdapter.notifyDataSetChanged();

        // and start a new BTLE scan for devices
        Thread scanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!BtAdapter.isEnabled()) {
                        BtAdapter.enable();
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    e.getLocalizedMessage();
                }
                scanForDevices();
            }
        });
        scanThread.start();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // this function is called when returning to this activity
        // check for a hardcoded request/result code to capture
        // a hardware device disconnect
        if (requestCode == 100) {
            if (resultCode==101) {
                System.out.println("DISCONNECTED HW");
                Toast.makeText(this, "Hardware Disconnected.", Toast.LENGTH_SHORT).show();
            } else {
                System.out.println("normal return");
            }
        }

    }


    public boolean onCreateOptionsMenu(Menu menu) {
        // create the menu items shown in the actionbar.
        // if a scan is in progress, show a spinner.
        // if not, allow a new scan to start.
        // the dweet config can be accessed anytime
        menu.clear();
        if (isScanning) {
            MenuItem mi = menu.add(Menu.NONE, R.id.menu_spinner, 1, "scanning...");
            mi.setActionView(R.layout.menu_spinner);
            MenuItemCompat.setShowAsAction(mi,MenuItem.SHOW_AS_ACTION_IF_ROOM|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            menu.add(Menu.NONE, R.id.dweet_cfg_menu_action, 100, "dweet config");
        } else {
            menu.add(Menu.NONE, R.id.scan_menu_action, 100, "scan devices");
            menu.add(Menu.NONE, R.id.dweet_cfg_menu_action, 100, "dweet config");
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan_menu_action:
                // 'scan devices' selected, start a new BTLE scan
                // clear the table datasource first
                deviceScanAdapter.clear();
                scanForDevices();
                return true;
            case R.id.dweet_cfg_menu_action:
                // 'dweet config' selected, show the config activity
                System.out.println("dweet cfg hit");
                final Intent intent = new Intent(this, ConfigActivity.class);
                startActivityForResult(intent, 100);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }




    private void scanForDevices() {
        // if a scan is not in progress,
        // start a BTLE scan
        // after 10s, stop the scan automatically
        if (!isScanning) {
            System.out.println("in scanForDevices ON");
            scanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isScanning = false;
                    BtAdapter.stopLeScan(scanCallback);
                    invalidateOptionsMenu();
                }
            }, 10000);
            isScanning = true;
            // start the scan and register the callback
            BtAdapter.startLeScan(scanCallback);
            invalidateOptionsMenu();
        }
    }



    // BTLE scan callback
    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    // device found, call class method to add it to the
                    // datasource
                    deviceScanAdapter.addDevice(device);
                    deviceScanAdapter.notifyDataSetChanged();

                    // some debug printout
                    System.out.println("in LeScanCallback : found device");
                    if (device != null) {
                        System.out.println(" -- " + device.getAddress());
                        System.out.println(" -- " + device.getName());
                    } else {
                        System.out.println(" -- device is null(?)");
                    }

                }
            });
        }
    };




    // custom class to handle table datasource for BTLE devices
    private class DeviceScanAdapter extends BaseAdapter {

        // data structures for BTLE devices
        private ArrayList<BluetoothDevice> knownDevices;
        private ArrayList<BluetoothDevice> unknownDevices;
        private LayoutInflater inflator;

        public DeviceScanAdapter() {
            super();
            // initialize data structures
            knownDevices = new ArrayList<BluetoothDevice>();
            unknownDevices = new ArrayList<BluetoothDevice>();
            inflator = DeviceScan.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {

            // check for duplicate devices
            if (knownDevices.contains(device) || unknownDevices.contains(device)) {
                return;
            }

            // check for devices with no name
            // check for devices with specific name
            if (device.getName()==null) {
                unknownDevices.add(device);
            } else if (deviceConfig.containsKey(device.getName())) {
                knownDevices.add(device);
            } else {
                unknownDevices.add(device);
            }


        }

        public void clear() {
            // wipe current devices
            knownDevices.clear();
            unknownDevices.clear();
        }

        @Override
        // +2 to allow for table header rows
        public int getCount() {
            return knownDevices.size() + unknownDevices.size() + 2;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public boolean isEnabled(int position) {
            // don't allow selection of the section header rows
            if (position==0 || position==knownDevices.size()+1)
                return false;

            // allow selection of known devices
            if (position<=knownDevices.size())
                return true;

            // everything else is unselectable
            return false;
        }

        public BluetoothDevice getDevice (int position) {
            // don't return a device for section header rows
            if (position==0 || position==knownDevices.size()+1)
                return null;

            // return a device for known devices
            if (position<=knownDevices.size())
                return knownDevices.get(position-1);

            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // create the hardcoded views for the BlueMS1 case
            View view;

            if (position==0) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText("known devices");
            } else if (position==knownDevices.size()+1) {
                // it's a section header
                view = inflator.inflate(R.layout.scan_separator, null);
                TextView tv = (TextView) view.findViewById(R.id.separator);
                tv.setText("unknown devices");
            } else {
                // ok, it's a real row. It needs a name and macaddr
                ViewHolder holder;
                view = inflator.inflate(R.layout.scan_item, null);
                holder = new ViewHolder();
                holder.name = (TextView) view.findViewById(R.id.name);
                holder.macaddr = (TextView) view.findViewById(R.id.macaddr);
                ImageView iv = (ImageView) view.findViewById(R.id.logo);

                // only known devices have a visible logo icon
                BluetoothDevice device;
                if (position <= knownDevices.size()) {
                    device = knownDevices.get(position-1);
                } else {
                    device = unknownDevices.get(position-2-knownDevices.size());
                    iv.setVisibility(View.GONE);
                }
                if (device.getName() == null) {
                    holder.name.setText("unknown");
                } else {
                    holder.name.setText(device.getName());
                }
                holder.macaddr.setText(device.getAddress());
            }

            return view;
        }

        private class ViewHolder {
            public TextView name, macaddr;
        }
    }



    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = deviceScanAdapter.getDevice(position);

        // do nothing if the device is null or the name is null
        // we should never really have this case because section headers
        // and unknown devices are unselectable
        if (device == null)
            return;
        if (device.getName() == null)
            return;

        JSONObject json = (JSONObject)deviceConfig.get(device.getName());

        System.out.println("tapped "+device.getName());

        // stop a scan if it's running
        if (isScanning)
            BtAdapter.stopLeScan(scanCallback);
        isScanning = false;

        // create the sensor detail activity with the tapped device
        final Intent intent = new Intent(this, SensorDetailActivity.class);
        intent.putExtra("ADDRESS", device.getAddress());
        intent.putExtra("NAME", device.getName());
        intent.putExtra("CONFIG", json.toString());
        startActivityForResult(intent, 100);

    }

}