package com.mantis.tinyfittings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mantis.tinyfittings.bluetooth.BTActivity;
import com.mantis.tinyfittings.arduino.Arduino;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.view.View.OnClickListener;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class TinyFittingsOnFirmata extends BTActivity implements OnClickListener{

	private static final String TAG = "TinyFittingsOnFirmata";
	private static final String fittingId = "1";
	private static final String TINYFITTINGS_URL = "http://artiswrong.com/tinyFittings/live.html";
	
	private HttpClient httpclient = new DefaultHttpClient();
	private HttpPost httppost = new HttpPost("http://www.artiswrong.com/tinyFittings/index.php");
	private HttpResponse httpresponse;
	
	private List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
	
	private boolean shouldContinue = true;
	
	protected static final int NUM_OF_ANALOG_CHANNELS = 6;
	Button viewLiveData, initArduino;
	TextView analogReading0, analogReading1, analogReading2, analogReading3, analogReading4, analogReading5;
	private boolean ledPin = false;
	private String[] anValues = {"","","","","",""};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tiny_fittings_on_firmata);
		
		analogReading0 = (TextView) findViewById(R.id.analog_reading0);
		analogReading1 = (TextView) findViewById(R.id.analog_reading1);
		analogReading2 = (TextView) findViewById(R.id.analog_reading2);
		analogReading3 = (TextView) findViewById(R.id.analog_reading3);
		analogReading4 = (TextView) findViewById(R.id.analog_reading4);
		analogReading5 = (TextView) findViewById(R.id.analog_reading5);
		
		initArduino = (Button) findViewById(R.id.init_arduino);
		viewLiveData = (Button) findViewById(R.id.view_livedata);
		
		initArduino.setOnClickListener(this);
		viewLiveData.setOnClickListener(this);
	}
	
	@Override
	public
	void onStop() {
		super.onStop();
		shouldContinue = false;
		try {
			readAnalogValuesThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//readAnalogValuesThread.stop();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause() called");
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume() called");
	}
	
	private void setupArduino() {
		Log.d(TAG, "Setting up Arduino..");
		
		arduino.pinMode(13, Arduino.OUTPUT);
		arduino.reportState();
		readAnalogValuesThread.start();
	}
	
	void postAnalogValues(String[] data) throws ClientProtocolException {
		try{
			String jsonString = "{\"fittingId\": " + fittingId + ",";
			for(int i = 0; i < 6; i++) {
				jsonString += "\"channel-" + i + "\":" + data[i];
				if(i < 5)
					jsonString += ",";
			}
			jsonString += "}";
			
			nameValuePairs.add(new BasicNameValuePair("sample", jsonString));
	
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			httpresponse = httpclient.execute(httppost);
			Log.d(TAG, "response = " + httpresponse.getStatusLine().toString());
			
			httpresponse.getEntity().consumeContent();
			nameValuePairs.clear();
		}
		catch(IOException e) {
			Log.d(TAG, "some unexplained shit happened...");
		}
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.init_arduino:
			Log.d(TAG, "pressing init_arduino button");
			setupArduino();
			break;
		case R.id.view_livedata:
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(TINYFITTINGS_URL));
			startActivity(i);
		}
	}
	
	private void toggleLed() {
		if(ledPin)
			arduino.digitalWrite(13, Arduino.LOW);
		else
			arduino.digitalWrite(13, Arduino.HIGH);
		
		ledPin = !ledPin;
	}
	
	private void setValuesOnUI(String[] data) {
		analogReading0.setText(data[0]);
		analogReading1.setText(data[1]);
		analogReading2.setText(data[2]);
		analogReading3.setText(data[3]);
		analogReading4.setText(data[4]);
		analogReading5.setText(data[5]);
	}
	
	Thread readAnalogValuesThread = new Thread() {
		@Override public void run() {
			while(shouldContinue) {
				for(int i = 0; i < NUM_OF_ANALOG_CHANNELS; i++) {
					//Log.d(TAG, "analog channel " + i + " = " + Integer.toString(arduino.analogRead(i)));
					anValues[i] = Integer.toString(arduino.analogRead(i));
				}
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						setValuesOnUI(anValues);
					}
				});
	
				try {
					postAnalogValues(anValues);
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	};
}
