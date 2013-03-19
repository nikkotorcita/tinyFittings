package com.mantis.tinyfittings.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BTConnectionManager {
	
	private static final String TAG = "BTConnectionManager";
	
	private static final String SDP_NAME = "BTConnectionManager";
	private static final UUID MY_UUID = 
			UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;
	
	public static final int STATE_NONE = 0;
	public static final int STATE_LISTEN = 1;
	public static final int STATE_CONNECTING = 2;
	public static final int STATE_CONNECTED = 3;
	
	public BTConnectionManager(Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}
	
	private synchronized void setState(int state) {
		Log.v(TAG, "setState() " + mState + " -> " + state);
		mState = state;
		
		mHandler.obtainMessage(BTHandler.MESSAGE_STATE_CHANGE, 
				state, -1).sendToTarget();
	}
	
	public synchronized int getState() {
		return mState;
	}
	
	public synchronized void start() {
		Log.v(TAG, "start");
		
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		setState(STATE_LISTEN);
		
		if(mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
	}
	
	public synchronized void connect(BluetoothDevice device) {
		Log.v(TAG, "connect to: " + device);
		
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
	
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice
			device) {
		Log.v(TAG, "connected");
		
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		if(mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();
		
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		setState(STATE_CONNECTED);
	}
	
	public synchronized void stop() {
		Log.v(TAG, "stop");
		
		if(mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		
		if(mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		if(mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		
		setState(STATE_NONE);
	}
	
	public void write(byte[] out) {
		ConnectedThread r;
		synchronized(this) {
			if(mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		r.write(out);
	}
	
	private void connectionFailed() {
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		BTConnectionManager.this.start();
	}
	
	private void connectionLost() {
		Message msg = mHandler.obtainMessage(BTHandler.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BTHandler.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
		
		BTConnectionManager.this.start();
	}
	
	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;
		
		public AcceptThread() {
			BluetoothServerSocket tmp = null;
			
			try {
				tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(SDP_NAME, MY_UUID);
			}
			catch(IOException e) {
				Log.e(TAG, "listen() failed", e);
			}
			mmServerSocket = tmp;
		}
		
		public void run() {
			Log.v(TAG, "Begin mAcceptThread" + this);
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
					synchronized(BTConnectionManager.this) {
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
			Log.v(TAG, "End mAcceptThread");
		}
		
		public void cancel() {
			Log.v(TAG, "cancel " + this);
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
			Log.v(TAG, "Begin mConnectThread");
			setName("ConnectThread");
			
			mAdapter.cancelDiscovery();
			
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
			
			synchronized(BTConnectionManager.this) {
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
		
		public ConnectedThread(BluetoothSocket socket) {
			Log.v(TAG, "create ConnectedThread");
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
			Log.v(TAG, "Begin mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;
			
			while(true) {
				try{
					bytes = mmInStream.read(buffer);
					
					mHandler.obtainMessage(BTHandler.MESSAGE_READ, bytes, -1, buffer)
						.sendToTarget();
				}
				catch(IOException e) {
					Log.v(TAG, "disconneceted", e);
					connectionLost();
					BTConnectionManager.this.start();
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
		
		public void cancel() {
			try{
				mmSocket.close();
			}
			catch(IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}