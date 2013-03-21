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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;

import com.mantis.tinyfittings.bluetooth.OnReceivedDataListener;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BTHandler {
	
	private static final String TAG = "tinyFittings BTHandler";
	
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
	
	private static final String SDP_NAME = "BTHandler";
	private static final UUID MY_UUID = 
			UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	private ArrayList<OnReceivedDataListener> onReceivedDataListeners;
	
	public static final int STATE_NONE = 0;
	public static final int STATE_LISTEN = 1;
	public static final int STATE_CONNECTING = 2;
	public static final int STATE_CONNECTED = 3;
	
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	
	private String mConnectedDeviceName = null;
	
	protected BluetoothAdapter mBluetoothAdapter = null;
	
	public BTHandler() {
		Log.d(TAG, "BTHandler created");
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null) {
			Log.d(TAG, "No bluetooth support.");
			return;
		}
		onReceivedDataListeners = new ArrayList<OnReceivedDataListener>();
	}
	
	public boolean isEnabled() {
		return mBluetoothAdapter.isEnabled() ? true : false;
	}
	
	public void setupConnection() {
	}
	
	public synchronized int getState() {
		return mState;
	}
	
	public synchronized void start() {
		Log.d(TAG, "start");
		
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		setState(STATE_LISTEN);
		
//		if(mAcceptThread == null) {
//			mAcceptThread = new AcceptThread();
//			mAcceptThread.start();
//		}
	}
	
	public synchronized void stop() {
		Log.d(TAG, "stop");
		
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
//		if(mAcceptThread != null) {
//			mAcceptThread.cancel();
//			mAcceptThread = null;
//		}
		
		setState(STATE_NONE);
	}
	
	public void sendData(byte[] out) {
		ConnectedThread r;
		synchronized(this) {
			if(mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		r.write(out);
	}
	
	public void sendData(int out) {
		ConnectedThread r;
		synchronized(this) {
			if(mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		r.write(out);
	}
	
	public void connectDevice(Intent data) {
		String address = data.getExtras()
				.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		connect(device);
	}
	
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_STATE_CHANGE:
				Log.d(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch(msg.arg1) {
				case STATE_CONNECTED:
					Log.d(TAG, "state : STATE_CONNECTED");
					break;
				case STATE_CONNECTING:
					Log.d(TAG, "state: STATE_CONNECTING");
					break;
				case STATE_LISTEN:
				case STATE_NONE:
					Log.d(TAG, "state: STATE_NONE");
					break;
				}
				break;
			case MESSAGE_WRITE:
			case MESSAGE_READ:
			case MESSAGE_DEVICE_NAME:
				break;
			case MESSAGE_TOAST:
			}
		}
	};
	
	private synchronized void setState(int state) {
		Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;
		
		mHandler.obtainMessage(BTHandler.MESSAGE_STATE_CHANGE, 
				state, -1).sendToTarget();
	}
	
	private synchronized void connect(BluetoothDevice device) {
		Log.d(TAG, "#############connect to: " + device);
		
		if(mState == STATE_CONNECTING) {
			if(mConnectedThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);
	}
	
	private synchronized void connected(BluetoothSocket socket, BluetoothDevice
			device) {
		Log.d(TAG, "repeat.......");
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
//		if(mAcceptThread != null) {
//			mAcceptThread.cancel();
//			mAcceptThread = null;
//		}
		
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();
		
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.DEVICE_NAME, "ngongo");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	
		
		setState(STATE_CONNECTED);
	}
	
	private void connectionFailed() {
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		BTHandler.this.start();
	}
	
	private void connectionLost() {
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		BTHandler.this.start();
	}
	
	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;
		
		public AcceptThread() {
			BluetoothServerSocket tmp = null;
			
			try {
				tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SDP_NAME, MY_UUID);
			}
			catch(IOException e) {
				Log.e(TAG, "listen() failed", e);
			}
			mmServerSocket = tmp;
		}
		
		public void run() {
			Log.d(TAG, "Begin mAcceptThread" + this);
			setName("AcceptThread");
			
			BluetoothSocket socket = null;
			
			while(mState != STATE_CONNECTED) {
				try{
					socket = mmServerSocket.accept();
				}
				catch(IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}
				
				if(socket != null) {
					synchronized(BTHandler.this) {
						switch(mState) {
						case STATE_LISTEN:
						case STATE_CONNECTING:
							connected(socket, socket.getRemoteDevice());
							break;
						case STATE_NONE:
						case STATE_CONNECTED:
							try{
								socket.close();
							}
							catch(IOException e) {
								Log.e(TAG, "could not close unwanted socket", e);
							}
							break;
						}
					}
				}
			}
			Log.d(TAG, "End mAcceptThread");
		}
		
		public void cancel() {
			Log.d(TAG, "cancel " + this);
			try{
				mmServerSocket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "close() of server failed");
			}
		}
	}
	
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		
		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tmp = null;
			
			try{
				tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
			}
			catch(IOException e) {
				Log.e(TAG, "create() failed", e);
			}
			mmSocket = tmp;
		}
		
		public void run() {
			Log.d(TAG, "Begin mConnectThread");
			setName("ConnectThread");
			
			mBluetoothAdapter.cancelDiscovery();
			
			try{
				mmSocket.connect();
			}
			catch(IOException e) {
				try{
					mmSocket.close();
				}
				catch(IOException e2) {
					Log.e(TAG, "unable to close() socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}
			
			synchronized(BTHandler.this) {
				mConnectThread = null;
			}
			
			connected(mmSocket, mmDevice);
		}
		
		public void cancel() {
			try{
				mmSocket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "close() of connect socket failed",e);
			}
		}
	}
	
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;
		ByteBuffer bBuffer = null;
		
		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
		
			try{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}
			catch(IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}
		
			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}
	
		public void run() {
			Log.d(TAG, "Begin mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;
			
			while(true) {
				try{
					while((bytes = mmInStream.read(buffer)) != -1) {
						bBuffer = ByteBuffer.allocate(bytes);
						bBuffer.put(buffer, 0, bytes);
						
						notifyListeners(bBuffer.array());
					}
					
					mHandler.obtainMessage(BTHandler.MESSAGE_READ, bytes, -1, buffer)
						.sendToTarget();
				}
				catch(IOException e) {
					Log.d(TAG, "disconnected", e);
					connectionLost();
					BTHandler.this.start();
					break;
				}
			}
		}
		
		public void write(byte[] buffer) {
			try{
				mmOutStream.write(buffer);
				mHandler.obtainMessage(BTHandler.MESSAGE_WRITE, -1, -1, buffer)
					.sendToTarget();
			}
			catch(IOException e) {
				Log.e(TAG,  "Exception during write", e);
			}
		}
		
		public void write(int oneByte) {
			try{
				Log.d(TAG, "sending data " + Integer.toString(oneByte));
				mmOutStream.write(oneByte);
				mHandler.obtainMessage(BTHandler.MESSAGE_WRITE, -1, -1, oneByte)
					.sendToTarget();
			}
			catch(IOException e) {
				Log.e(TAG,  "Exception during write", e);
			}
		}
		
		public void cancel() {
			try{
				mmSocket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
		
		private synchronized void notifyListeners(byte[] data) {
			for(OnReceivedDataListener l : onReceivedDataListeners) {
				l.receivedData(data.clone());
			}
		}
	}
	
	public void addOnReceivedDataListener(OnReceivedDataListener listener) {
		onReceivedDataListeners.add(listener);
	}
	
	public boolean removeOnReceivedDataListener(OnReceivedDataListener listener) {
		return onReceivedDataListeners.remove(listener);
	}
}
