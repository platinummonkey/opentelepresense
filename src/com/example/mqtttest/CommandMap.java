package com.example.mqtttest;

import android.util.Log;

public class CommandMap {
	
	public static final String TAG = "CommandMap";
	
	public static final String FORWARD = "f";
	public static final String REVERSE = "b";
	public static final String LEFT = "l";
	public static final String RIGHT = "r";
	public static final String DOCK = "d";
	public static final String IDLE = "i";
	public static final String WAKE = "w";
	
	public static String parseMessage(String msg) {
		try {
			switch (msg.substring(0, 1)) {
				case FORWARD:
					return "go forward";
				case REVERSE:
					return "go reverse";
				case LEFT:
					return "go left";
				case RIGHT:
					return "go right";
				case DOCK:
					return "go dock";
				case IDLE:
					return "start idling";
				case WAKE:
					return "stop idling";
				default:
					break;
			}
		} catch (StringIndexOutOfBoundsException e) {
			Log.d(TAG, "Invalid Input");
		} catch (Exception e) {
			Log.e(TAG, "Error: "+ e.getMessage());
            e.printStackTrace();
		}
		return "";
	}

}
