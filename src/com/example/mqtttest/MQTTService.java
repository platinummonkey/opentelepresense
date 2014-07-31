package com.example.mqtttest;

import com.example.mqtttest.CommandMap;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;

import android.app.PendingIntent;
//import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
//import android.net.wifi.WifiInfo;
//import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.AsyncTask;
import android.os.SystemClock;
//import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;


public class MQTTService extends Service {

	final protected static char[] hexArray = "01234567890ABCDEF".toCharArray();
	
	// Receivers
	private BroadcastReceiver mqttReceiver;
	
	// Service
	private static final String TAG = "MQTTService";
    private static boolean hasWifi = false;
    private static boolean hasMmobile = false;
    private Thread thread;
    private ConnectivityManager mConnMan;
    
    // MQTT
    private volatile IMqttAsyncClient mqttClient;
    private String deviceId;
    private String topic_sub;
    private String topic_pub;
    
    // USB
    private UsbManager mUsbManager;
    private UsbSerialDriver mUsbDriver;
    private static class DeviceEntry {
    	public UsbDevice device;
    	public UsbSerialDriver driver;
    	
    	DeviceEntry(UsbDevice device, UsbSerialDriver driver) {
    		this.device = device;
    		this.driver = driver;
    	}
    }
    private List<DeviceEntry> mUsbEntries = new ArrayList<DeviceEntry>();
    private ArrayAdapter<DeviceEntry> mAdapter;
    
    private static final String ACTION_USB_PERMISSION = "com.example.mqtttest.USB_PERMISSION";
	
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        if (ACTION_USB_PERMISSION.equals(action)) {
	            synchronized (this) {
	                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

	                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
	                    if(device != null){
	                    	Log.d(TAG, "Have permission for device:" + device);
	                      //call method to set up device communication
	                   }
	                } 
	                else {
	                    Log.d(TAG, "permission denied for device " + device);
	                }
	            }
	        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
	        	synchronized (this) {
	                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if(device != null){
                    	Log.d(TAG, "Have permission for device:" + device);
                      //call method to clean up device communication
                   }
	            }
	        }
	    }
	};
	
	public void connectToDevice(UsbDevice device) {
		Log.d(TAG, "Trying device: " + device);
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
    	mUsbManager.requestPermission(device, mPermissionIntent);
    	Log.d(TAG, "Have device permissions: " + device);
    	mUsbManager.openDevice(device);
    	
    	
    	mUsbDriver = UsbSerialProber.acquire(mUsbManager, device);
    	if (mUsbDriver != null) {
    		Log.d(TAG, "Found driver, trying to open: " + mUsbDriver);
    		
    		try {
    			mUsbDriver.open();
    			mUsbDriver.setParameters(115200, 8, 1, UsbSerialDriver.PARITY_NONE);
    			//mUsbDriver.setDTR(true);
        		mUsbDriver.purgeHwBuffers(true, true);
        		
    			String testString = "hello world.";
    			byte bufferOutput[] = testString.getBytes(Charset.forName("UTF-8"));
    			//byte bufferOutput[] = testString.getBytes();
        		byte buffer[] = new byte[bufferOutput.length];
        		
        		// write
        		mUsbDriver.write(bufferOutput, 2000);
        		Log.d(TAG, "Wrote: " + testString + " bytes: " + bufferOutput + "[" + bytesToHex(bufferOutput) + "] | length: " + bufferOutput.length);
        		
        		//read
        		int numBytesRead = mUsbDriver.read(buffer, 2000);
        		//Delay(1000);
        		String response = new String(buffer);
        		numBytesRead = Math.max(numBytesRead, buffer.length);
        		boolean isSame = (buffer == bufferOutput);
        		Log.d(TAG, "Read " + numBytesRead + " bytes." + buffer + "[" + bytesToHex(buffer) + "] (Same: " + isSame + ") | String: " + response);
        		
        	} catch (IOException e) {
        		// TODO: deal with error
        		Log.e(TAG, e.getMessage());
        		e.printStackTrace();
        	} finally {
        		try {
        			Log.d(TAG, "Closing up the serial connection...");
        			mUsbDriver.close();
        		} catch (IOException e) {
        			// TODO: deal with this error too
        		}
        	}
    	} else {
    		Log.d(TAG, "No Driver found for: " + device);
    		//return null;
    	}
	}
	
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j*2] = hexArray[v >>> 4];
			hexChars[j*2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
 
    class MQTTBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            IMqttToken token;
            boolean hasConnectivity = false;
            boolean hasChanged = false;
            NetworkInfo infos[] = mConnMan.getAllNetworkInfo();
 
            for (int i = 0; i < infos.length; i++){
                if (infos[i].getTypeName().equalsIgnoreCase("MOBILE")){
                    if((infos[i].isConnected() != hasMmobile)){
                        hasChanged = true;
                        hasMmobile = infos[i].isConnected();
                    }
                    Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());
                } else if ( infos[i].getTypeName().equalsIgnoreCase("WIFI") ){ 
                    if((infos[i].isConnected() != hasWifi)){
                        hasChanged = true;
                        hasWifi = infos[i].isConnected();
                    }
                    Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());
                }
            }
 
            hasConnectivity = hasMmobile || hasWifi;
            Log.v(TAG, "hasConn: " + hasConnectivity + " hasChange: " + hasChanged + " - "+(mqttClient == null || !mqttClient.isConnected()));
            if (hasConnectivity && hasChanged && (mqttClient == null || !mqttClient.isConnected())) {
                doConnect();
            } else if (!hasConnectivity && mqttClient != null && mqttClient.isConnected()) {
                Log.d(TAG, "doDisconnect()");
                try {
                    token = mqttClient.disconnect();
                    token.waitForCompletion(1000);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    };
 
    public class MQTTBinder extends Binder {
        public MQTTService getService(){
        	return MQTTService.this;
        }
    }
    
    @Override
    public void onDestroy() {
    	unregisterReceiver(mqttReceiver);
    	unregisterReceiver(mUsbReceiver);
    	super.onDestroy();
    }
 
	@Override
    public void onCreate() {
    	// MQTT
        IntentFilter intentf = new IntentFilter();
        //setClientID();
        intentf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mqttReceiver = new MQTTBroadcastReceiver();
        registerReceiver(mqttReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        
        
        mConnMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
        	UsbDevice device = deviceIterator.next();
        	Log.d(TAG, "Trying device: " + device);
        	/* mUsbManager.requestPermission(device, mPermissionIntent);
        	Log.d(TAG, "Have device permissions: " + device);
        	mUsbManager.openDevice(device);
        	
        	mUsbDriver = UsbSerialProber.acquire(mUsbManager, device);
        	if (mUsbDriver != null) {
        		Log.d(TAG, "Found driver, trying to open: " + mUsbDriver);
        		
        		try {
        			mUsbDriver.open();
	    			mUsbDriver.setParameters(115200, 8, 1, UsbSerialDriver.PARITY_NONE);
	    			//mUsbDriver.setDTR(true);
	        		mUsbDriver.purgeHwBuffers(true, true);
	        		
	    			String testString = "hello world.";
	    			byte bufferOutput[] = testString.getBytes(Charset.forName("UTF-8"));
	    			//byte bufferOutput[] = testString.getBytes();
	        		byte buffer[] = new byte[bufferOutput.length];
	        		
	        		// write
	        		mUsbDriver.write(bufferOutput, 2000);
	        		Log.d(TAG, "Wrote: " + testString + " bytes: " + bufferOutput + "[" + bytesToHex(bufferOutput) + "] | length: " + bufferOutput.length);
	        		
	        		//read
	        		int numBytesRead = mUsbDriver.read(buffer, 2000);
	        		//Delay(1000);
	        		String response = new String(buffer);
	        		numBytesRead = Math.max(numBytesRead, buffer.length);
	        		boolean isSame = (buffer == bufferOutput);
	        		Log.d(TAG, "Read " + numBytesRead + " bytes." + buffer + "[" + bytesToHex(buffer) + "] (Same: " + isSame + ") | String: " + response);
	        		
	        	} catch (IOException e) {
	        		// TODO: deal with error
	        		Log.e(TAG, e.getMessage());
	        		e.printStackTrace();
	        	} finally {
	        		try {
	        			Log.d(TAG, "Closing up the serial connection...");
	        			mUsbDriver.close();
	        		} catch (IOException e) {
	        			// TODO: deal with this error too
	        		}
	        	}
        	} else {
        		Log.d(TAG, "No Driver found for: " + device);
        	} */
        }  // end while
        
        /*mUsbDriver = (UsbSerialDriver) UsbSerialProber.findFirstDevice(mUsbManager);
        if (mUsbDriver != null) {
        	try {
        		//mUsbManager.requestPermission((UsbDevice) mUsbDriver, mPermissionIntent);
        		mUsbDriver.open();
        		mUsbDriver.setParameters(115200, 8, 1, UsbSerialDriver.PARITY_NONE);
        		
        		byte buffer[] = new byte[16];
        		int numBytesRead = mUsbDriver.read(buffer, 1000);
        		Log.d(TAG, "Read " + numBytesRead + " bytes.");
        	} catch (IOException e) {
        		// TODO: deal with error
        	} finally {
        		try {
        			mUsbDriver.close();
        		} catch (IOException e) {
        			// TODO: deal with this error too
        		}
        	}
        }*/
        // TODO: https://github.com/mik3y/usb-serial-for-android/blob/master/UsbSerialExamples/src/com/hoho/android/usbserial/examples/DeviceListActivity.java
    }
 
    private void refreshDeviceList() {
    	Log.d(TAG, "starting refresh...");
    	new AsyncTask<Void, Void, List<DeviceEntry>>() {
    		@Override
    		protected List<DeviceEntry> doInBackground(Void... params) {
    			Log.d(TAG, "refreshing device list....");
    			SystemClock.sleep(1000);
    			final List<DeviceEntry> result = new ArrayList<DeviceEntry>();
    			for (final UsbDevice device : mUsbManager.getDeviceList().values()) {
    				final List<UsbSerialDriver> drivers = UsbSerialProber.probeSingleDevice(mUsbManager, device);
    				Log.d(TAG, "Found usb device: " + device);
    				if (drivers.isEmpty()) {
    					Log.d(TAG, "  - No UsbSerialDriver available.");
    					result.add(new DeviceEntry(device, null));
    				} else {
    					for (UsbSerialDriver driver : drivers) {
    						Log.d(TAG, "  + " + driver);
    						result.add(new DeviceEntry(device, driver));
    					}
    				}
    			}
    			return result;
    		}
    		
    		@Override
    		protected void onPostExecute(List<DeviceEntry> result) {
    			Log.d(TAG, "Done refreshing, " + result.size() + " entries found...");
    		}
    	}.execute((Void) null);
    }
    
    /*private void showConsoleActivity(UsbSerialDriver driver) {
    	SerialConsoleActivity.show(this, driver);
    }*/
    
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged()");
        android.os.Debug.waitForDebugger();
        super.onConfigurationChanged(newConfig);
 
    }
 
    /*private void setClientID(){
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wInfo = wifiManager.getConnectionInfo();
        deviceId = wInfo.getMacAddress();
        if(deviceId == null){
            deviceId = MqttAsyncClient.generateClientId();
        }
    }*/
 
    private void doConnect(){
    	SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    	String connection_host = sharedPrefs.getString("connection_host", Constants.default_connection_host);
    	deviceId = sharedPrefs.getString("connection_device_id", Constants.default_deviceID);
    	topic_sub = sharedPrefs.getString("connection_topic_sub", deviceId + "/default/sub");
    	topic_pub = sharedPrefs.getString("connection_topic_pub", deviceId + "/default/pub");
    	
        Log.d(TAG, "doConnect()");
        IMqttToken token;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        try {
            mqttClient = new MqttAsyncClient(connection_host, deviceId, new MemoryPersistence());
            token = mqttClient.connect();
            token.waitForCompletion(3500);
            mqttClient.setCallback(new MqttEventCallback());
            token = mqttClient.subscribe(topic_sub, 0);
            token.waitForCompletion(5000);
        } catch (MqttSecurityException e) {
            e.printStackTrace();
        } catch (MqttException e) {
            switch (e.getReasonCode()) {
            case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
            case MqttException.REASON_CODE_CLIENT_TIMEOUT:
            case MqttException.REASON_CODE_CONNECTION_LOST:
            case MqttException.REASON_CODE_SERVER_CONNECT_ERROR:
                Log.v(TAG, "c" +e.getMessage());
                e.printStackTrace();
                break;
            case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
                Intent i = new Intent("RAISEALARM");
                i.putExtra("ALARM", e);
                Log.e(TAG, "b"+ e.getMessage());
                e.printStackTrace();
                break;
            default:
                Log.e(TAG, "a" + e.getMessage());
                e.printStackTrace();
            }
        }
    }
 
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        return START_STICKY;
    }
 
    private class MqttEventCallback implements MqttCallback {
 
        @Override
        public void connectionLost(Throwable arg0) {
        	Log.i(TAG, "Lost Connection");
        	// attempt to reconnect
        	doConnect(); 
        }
 
        @Override
        public void deliveryComplete(IMqttDeliveryToken arg0) {
    		Log.d(TAG, "Delivery Complete");
        }
 
        //@SuppressLint("NewApi")
        @Override
        public void messageArrived(String topic, final MqttMessage msg) throws Exception {
        	String payload = new String(msg.getPayload());
        	Log.i(TAG, "Message arrived from topic: " + topic + " -> " + payload);
        	Log.i(TAG, "Message received from command map: " + CommandMap.parseMessage(payload));
        	MqttMessage outmsg = new MqttMessage(("test out:" + payload).getBytes());
        	mqttClient.publish(topic_pub, outmsg);
        	Handler h = new Handler(getMainLooper());
        	h.post(new Runnable() {
        		@Override
        		public void run() {
        		    //Intent launchA = new Intent(MQTTService.this, MainActivity.class);
        		    //launchA.putExtra("message", msg.getPayload());
        		    //TODO write something that has some sense
        		    //if(Build.VERSION.SDK_INT >= 11){
        		    //    launchA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        		    //} /*else {
        		    //    launchA.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        		    //}*/
        		    //startActivity(launchA);
        			Toast.makeText(getApplicationContext(), "MQTT Message:\n" + new String(msg.getPayload()), Toast.LENGTH_SHORT).show();
        		}
        	});
        }
    }
 
    public String getThread(){
        return Long.valueOf(thread.getId()).toString();
    }
 
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind called");
        return null;
    }	
}
