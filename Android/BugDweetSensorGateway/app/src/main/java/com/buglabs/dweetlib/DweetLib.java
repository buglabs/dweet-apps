
package com.buglabs.dweetlib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.view.ViewParent;

import com.buglabs.bugdweetsensorgateway.MainActivity;


//
//  DweetLib Android
//
//  Pre-Release version
//
//  Created by Tim Buick on 2015-06-10.
//  Copyright (c) 2015 Bug Labs. All rights reserved.
//




public class DweetLib {

	// return codes
	public static Integer DWEET_STILL_PENDING=1;
	public static Integer DWEET_SUCCESS=0;
	public static Integer NO_NETWORK=-1;
	public static Integer COULD_NOT_CONNECT_TO_DWEETIO=-2;
	public static Integer DWEET_DID_NOT_RETURN_VALID_JSON=-3;
	public static Integer DWEET_JSON_FORMAT_UNEXPECTED=-4;
	public static Integer DWEET_RESPONSE_IS_FAILED=-5;
	public static Integer COULD_NOT_CONNECT_TO_LOCKED_THING=-6;
	public static Integer COULD_NOT_GENERATE_JSON_FROM_DATA=-7;
	public static Integer CONNECTION_ERROR=-8;

	private static DweetLib instance;

	HashMap<Object,Object> thingProcess;
	HashMap<Object,Object> thingProcessUrl;
	HashMap<Object,Object> thingProcessConnection;
	HashMap<Object,Object> thingProcessCallback;
	HashMap<Object,Object> thingProcessCaller;

	private static Context currentCtx;

	static {
        instance = new DweetLib();
    }

    private DweetLib() {
		thingProcess = new HashMap<>();
		thingProcessUrl = new HashMap<>();
		thingProcessConnection = new HashMap<>();
		thingProcessCallback = new HashMap<>();
		thingProcessCaller = new HashMap<>();


    }

    public static DweetLib getInstance(Context ctx) {
		currentCtx = ctx;
        return DweetLib.instance;
    }

	public interface DweetCallback {
		void callback(ArrayList<Object> ar);
	}
    
    public String sendDweet (JSONObject data,String thing,String key,Object caller,DweetCallback cb,boolean overwrite) {

		final String JSONString = data.toString();

		final String urlstr = "http://dweet.io/dweet/for/" + thing;
		DweetTask dt = (DweetTask) thingProcess.get(urlstr);

		int err=0;
		if (currentCtx==null) err=1;
		else if (!isNetworkingAvailable(currentCtx)) err=1;  // TODO CRASH WHEN NO NETWORK!
		if (err==1) {
			System.out.println("no network error");
			if (caller!=null) {
				ArrayList ar = new ArrayList<>();
				ar.add(NO_NETWORK);
				cb.callback(ar);
			}
			DweetTask x = (DweetTask)thingProcessUrl.get(urlstr);
			thingProcessUrl.remove(urlstr);
			thingProcess.remove(x);
			thingProcessConnection.remove(x);
			thingProcessCaller.remove(x);
			thingProcessCallback.remove(x);
			return "";
		}

		if (dt != null) {
			System.out.println("still working");
			if (overwrite) {
				System.out.println("overwriting data");
				String u = (String) thingProcessUrl.get(dt);
				thingProcess.remove(u);
				HttpURLConnection c = (HttpURLConnection) thingProcessConnection.get(dt);
				thingProcessConnection.remove(dt);
				thingProcessCallback.remove(dt);
				thingProcessCaller.remove(dt);
				c.disconnect();
				c = null;
				dt.cancel(true);
				thingProcessUrl.remove(dt);
				dt = null;
			}
		}
		if (dt==null) {
    		System.out.println("starting new dt");

			try {
				URL url = new URL(urlstr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setReadTimeout(5000);  // ms
				conn.setConnectTimeout(5000);  // ms
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setRequestMethod("POST");
				conn.setDoInput(true);

				DweetTask x = (DweetTask) new DweetTask().execute(conn,JSONString);
				thingProcess.put(urlstr,x);
				thingProcessUrl.put(x, urlstr);
				thingProcessConnection.put(x, conn);
				if (caller!=null) thingProcessCaller.put(x,caller);
				if (cb!=null) thingProcessCallback.put(x,cb);

				System.out.println("conn:"+conn.hashCode()+", task:"+x.hashCode());

			} catch (Exception e) {
				System.out.println("connection error");
				if (caller!=null) {
					ArrayList ar = new ArrayList<>();
					ar.add(CONNECTION_ERROR);
					cb.callback(ar);
				}
				DweetTask x = (DweetTask)thingProcessUrl.get(urlstr);
				thingProcessUrl.remove(urlstr);
				thingProcess.remove(x);
				thingProcessConnection.remove(x);
				thingProcessCaller.remove(x);
				thingProcessCallback.remove(x);
			}


    	}


		return "";

    }



	private class DweetTask extends AsyncTask<Object,String,Integer> {


		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			System.out.println(this.hashCode() + " onPostExecute:" + result);
			HttpURLConnection c = (HttpURLConnection) thingProcessConnection.get(this);
			System.out.println("post conn:" + c.hashCode());
			String urlstr = (String) thingProcessUrl.get(this);

			thingProcess.remove(urlstr);
			thingProcessUrl.remove(this);
			thingProcessConnection.remove(this);

			if (thingProcessCaller.get(this)!=null) {
				DweetCallback dc = (DweetCallback)thingProcessCallback.get(this);
				ArrayList ar = new ArrayList<>();
				ar.add(result);
				dc.callback(ar);
			}

			thingProcessCallback.remove(this);
			thingProcessCaller.remove(this);

		}

		@Override
		protected Integer doInBackground(Object...params) {
			System.out.println(this.hashCode() + " doInBackground");
			InputStream is = null;
			String rsp = null;

			HttpURLConnection conn = (HttpURLConnection) params[0];
			String JSONString = (String) params[1];
			try {
				OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
				wr.write(JSONString);
				wr.flush();
				conn.connect();
				int response = conn.getResponseCode();
				System.out.println(this.hashCode()+" The response is: " + response);
				is = conn.getInputStream();
				String contentAsString = convertStreamToString(is);
				// TODO parse json here
				if (contentAsString.contentEquals("err")) {
					return DWEET_DID_NOT_RETURN_VALID_JSON;
				}
				System.out.println(this.hashCode() + contentAsString);
			} catch (IOException e) {
				System.out.println(this.hashCode()+" IO Exception");
				return COULD_NOT_CONNECT_TO_DWEETIO;
			}

			conn.disconnect();
			return DWEET_SUCCESS;

		}



		private String convertStreamToString(InputStream is) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();

			String line = null;
			try {
				while ((line = reader.readLine()) != null) {
					sb.append(line).append('\n');
				}
			} catch (IOException e) {
				e.printStackTrace();
				return "err";
			} finally {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
					return "err";
				}
			}
			return sb.toString();
		}


	}



	private static boolean isNetworkingAvailable(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info =  cm.getActiveNetworkInfo();
		return (info.isAvailable() && info.isConnected());
	}





}








