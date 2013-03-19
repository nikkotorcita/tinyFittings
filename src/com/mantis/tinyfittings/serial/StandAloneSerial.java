package com.mantis.tinyfittings.serial;

import android.util.Log;

import com.mantis.tinyfittings.bluetooth.BTHandler;

public class StandAloneSerial extends Serial {
	
	BTHandler btHandler;
	
	public StandAloneSerial(BTHandler btHandler){
		super();
		this.btHandler = btHandler;
		btHandler.addOnReceivedDataListener(this);
	}

	@Override
	public void dispose() {
		// handled by activity life cycle
	}

	@Override
	public void write(int what) {
		btHandler.sendData(what & 0xff);  // for good measure do the &
	}

	@Override
	public void write(byte[] bytes) {
		btHandler.sendData(bytes);
	}

	@Override
	public void write(String what) {
		btHandler.sendData(what.getBytes());
	}	

}
