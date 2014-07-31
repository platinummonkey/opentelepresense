package com.example.mqtttest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import com.example.mqtttest.Constants;

public class MainActivity extends Activity {
	
	private static final int SETTINGS_CHANGE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		onConnectionSettingsChange();
		
		// start service
		//Intent serviceIntent = new Intent(getApplicationContext(),MQTTService.class);
	    //startService(serviceIntent);
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }
 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
	        case R.id.menu_settings:
	            Intent i = new Intent(this, ConnectionPreferences.class);
	            startActivityForResult(i, SETTINGS_CHANGE);
	            break;
        }
        return true;
    }
 
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
 
        switch (requestCode) {
	        case SETTINGS_CHANGE:
	            onConnectionSettingsChange();
	            break;
        }
    }
	
	private void onConnectionSettingsChange() {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		StringBuilder builder = new StringBuilder();
		
		String device_id = sharedPrefs.getString("connection_device_id", Constants.default_deviceID);
		
		builder.append("Broker: " + sharedPrefs.getString("connection_host", Constants.default_connection_host));
		builder.append("\nListen Topic: " + sharedPrefs.getString("connection_topic_sub", device_id + "/default/sub"));
		builder.append("\nPublish Topic: " + sharedPrefs.getString("connection_topic_pub", device_id + "/default/pub"));
		builder.append("\nDevice ID: " + sharedPrefs.getString("connection_device_id", Constants.default_deviceID));
		
		TextView settingsTextView = (TextView) findViewById(R.id.settings_text_view);
		settingsTextView.setText(builder.toString());
		
		// restart service
		restartService();
	}
	
	private void restartService() {
		// stop the service
		Intent oldServiceIntent = new Intent(this, MQTTService.class);
		stopService(oldServiceIntent);
		
		// start the service
		Intent serviceIntent = new Intent(getApplicationContext(), MQTTService.class);
	    startService(serviceIntent);
	}
}
