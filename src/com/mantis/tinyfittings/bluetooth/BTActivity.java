/*
  Copyright (c) 2009 Bonifaz Kaufmann. 
  
  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this library; if not, write to the Free Software
  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/
package com.mantis.tinyfittings.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.mantis.tinyfittings.R;
import com.mantis.tinyfittings.arduino.Arduino;
import com.mantis.tinyfittings.arduino.Arduino_v1;
import com.mantis.tinyfittings.serial.StandAloneSerial;

public abstract class BTActivity extends Activity {
	
	private static final String TAG = "BTActivity";
	
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 3;
	
	static BTHandler btHandler;
	protected Arduino arduino;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			btHandler = new BTHandler();		
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "ON START");
		
		if(!btHandler.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		btHandler.start();
	}
	
	@Override
	public void onStop() {
		Log.d(TAG, "ON STOP");
		super.onStop();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "ON PAUSE");
	}
	
	@Override
	public synchronized void onResume() {
		super.onResume();
		Log.d(TAG, "ON RESUME");
		if(btHandler.getState() == BTHandler.STATE_NONE) {
			btHandler.start();
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if(btHandler != null)
			btHandler.stop();
		Log.d(TAG, "ON DESTROY");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch(item.getItemId()) {
		case R.id.insecure_connect_scan:
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		}
		return false;
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult " + resultCode);
		switch(requestCode) {
		case REQUEST_CONNECT_DEVICE:
			if(resultCode == Activity.RESULT_OK) {
				btHandler.connectDevice(data);
				StandAloneSerial mStandAloneSerial = new StandAloneSerial(btHandler);
				arduino = new Arduino_v1(mStandAloneSerial);
			}
		case REQUEST_ENABLE_BT:
			if(resultCode == Activity.RESULT_OK) {
				Log.d(TAG, "Bluetooth enabled");
			}
			else {
				Log.d(TAG, "Bluetooth not enabled");
				finish();
			}
		}
	}
}