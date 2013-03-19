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
  
  Code depends on David A. Mellis's implementation for Processing
*/
package com.mantis.tinyfittings.arduino;

import com.mantis.tinyfittings.serial.OnSerialEventListener;
import com.mantis.tinyfittings.serial.Serial;

public abstract class Arduino implements OnSerialEventListener{

	public static final String TAG = "Arduino";
	/**
	 * Constant to set a pin to input mode (in a call to pinMode()).
	 */
	public static final int INPUT = 0;
	/**
	 * Constant to set a pin to output mode (in a call to pinMode()).
	 */
	public static final int OUTPUT = 1;
	/**
	 * Constant to set a pin to analog mode (in a call to pinMode()).
	 */
	public static final int ANALOG = 2;
	/**
	 * Constant to set a pin to PWM mode (in a call to pinMode()).
	 */
	public static final int PWM = 3;
	/**
	 * Constant to set a pin to servo mode (in a call to pinMode()).
	 */
	public static final int SERVO = 4;
	/**
	 * Constant to set a pin to shiftIn/shiftOut mode (in a call to pinMode()).
	 */
	public static final int SHIFT = 5;
	/**
	 * Constant to set a pin to I2C mode (in a call to pinMode()).
	 */
	public static final int I2C = 6;


	/**
	 * Constant to write a low value (0 volts) to a pin (in a call to
	 * digitalWrite()).
	 */
	public static final int LOW = 0;

	/**
	 * Constant to write a high value (+5 volts) to a pin (in a call to
	 * digitalWrite()).
	 */
	public static final int HIGH = 1;

	protected Serial serial;
	
	protected int majorVersion = 0;
	protected int minorVersion = 0;
	

	@Override
	public void serialEvent() {
		//Log.d(TAG, "Serial data received");
		// Notify the Arduino class that there's serial data for it to process.
		while (serial.available() > 0)
			processInput();
	}

	public void dispose() {
		this.serial.dispose();
	}

	public abstract void reportState();
	public abstract int digitalRead(int pin);
	public abstract int analogRead(int pin);
	public abstract void pinMode(int pin, int mode);
	public abstract void digitalWrite(int pin, int value);
	public abstract void analogWrite(int pin, int value);
	
	protected abstract void processInput();
}
