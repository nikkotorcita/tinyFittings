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

import android.util.Log;

import com.mantis.tinyfittings.serial.Serial;

/**
 * Together with the Firmata 1 firmware (an Arduino sketch uploaded to the
 * Arduino board), this class allows you to control the Arduino board from
 * Processing: reading from and writing to the digital pins and reading the
 * analog inputs.
 */
public class Arduino_v1 extends Arduino {

	public static final String TAG = "Arduino_v1";


	private final int DIGITAL_MESSAGE        = 0x90; // send data for a digital pin
	private final int ANALOG_MESSAGE         = 0xE0; // send data for an analog pin (or PWM)
	private final int REPORT_ANALOG_PIN      = 0xC0; // enable analog input by pin #
	private final int SET_DIGITAL_PIN_MODE   = 0xF4; // set a digital pin to INPUT or OUTPUT 
	private final int REPORT_VERSION         = 0xF9; // report firmware version

	int inputData;

	int waitForData = 0;
	int executeMultiByteCommand = 0;
	int multiByteChannel = 0;
	int[] storedInputData = new int[2];

	int[] digitalOutputData = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	int[] digitalInputData  = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	int[] analogInputData = { 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	public Arduino_v1(Serial serial) {
		this.serial = serial;
		serial.registerArduino(this);
		reportState();
	}

	@Override
	public void reportState(){
		for (int i = 0; i < 6; i++) {
			serial.write(REPORT_ANALOG_PIN | i);
			serial.write(1);
		}
//		new Thread() {
//			public void run(){
//				try {
//					Thread.sleep(2000);
//				} catch (InterruptedException e) {}
//
//				//Log.d(TAG, "send report analog");
//				for (int i = 0; i < 6; i++) {
//					serial.write(REPORT_ANALOG_PIN | i);
//					serial.write(1);
//				}
//			}
//		}.start();
	}

	/**
	 * Returns the last known value read from the digital pin: HIGH or LOW.
	 *
	 * @param pin the digital pin whose value should be returned (from 2 to 13,
	 * since pins 0 and 1 are used for serial communication)
	 */
	@Override
	public int digitalRead(int pin) {
		return digitalInputData[pin];
	}

	/**
	 * Returns the last known value read from the analog pin: 0 (0 volts) to
	 * 1023 (5 volts).
	 *
	 * @param pin the analog pin whose value should be returned (from 0 to 5)
	 */
	@Override
	public int analogRead(int pin) {
		return analogInputData[pin];
	}

	/**
	 * Set a digital pin to input or output mode.
	 *
	 * @param pin the pin whose mode to set (from 2 to 13)
	 * @param mode either Arduino.INPUT or Arduino.OUTPUT
	 */
	@Override
	public void pinMode(int pin, int mode) {
		serial.write(SET_DIGITAL_PIN_MODE);
		serial.write(pin);
		serial.write(mode);
	}

	/**
	 * Write to a digital pin (the pin must have been put into output mode with
	 * pinMode()).
	 *
	 * @param pin the pin to write to (from 2 to 13)
	 * @param value the value to write: Arduino.LOW (0 volts) or Arduino.HIGH
	 * (5 volts)
	 */
	@Override
	public void digitalWrite(int pin, int value) {
		int transmitByte;
		digitalOutputData[pin] = value;
		serial.write(DIGITAL_MESSAGE);

		transmitByte = 0;
		for (int i = 0; i <= 6; i++)
			if (digitalOutputData[i] != 0)
				transmitByte |= 1 << i;
		serial.write(transmitByte);

		transmitByte = 0;
		for (int i = 7; i <= 13; i++)
			if (digitalOutputData[i] != 0)
				transmitByte |= (1 << (i - 7));
		serial.write(transmitByte);
	}

	/**
	 * Write an analog value (PWM-wave) to a digital pin.
	 *
	 * @param pin the pin to write to (must be 9, 10, or 11, as those are they
	 * only ones which support hardware pwm)
	 * @param the value: 0 being the lowest (always off), and 255 the highest
	 * (always on)
	 */
	@Override
	public void analogWrite(int pin, int value) {
		serial.write(ANALOG_MESSAGE | (pin & 0x0F));
		serial.write(value & 0x7F);
		serial.write(value >> 7);
	}

	private void setDigitalInputs(int inputData0, int inputData1) {
		for (int i = 0; i < 7; i++) {
			//System.out.println("digital pin " + i +       " is " + ((inputData0 >> i) & 1));
			//System.out.println("digital pin " + (i + 7) + " is " + ((inputData1 >> i) & 1));
			digitalInputData[i]   = (inputData0 >> i) & 1;
			digitalInputData[i+7] = (inputData1 >> i) & 1;
		}
	}

	private void setAnalogInput(int pin, int inputData0, int inputData1) {
		//System.out.println("analog pin " + pin + " is " + (inputData1 * 128 + inputData0));
		analogInputData[pin] = (inputData1 * 128 + inputData0);
	}

	private void setVersion(int inputData0, int inputData1) {
		//System.out.println("version is " + inputData1 + "." + inputData0);
		majorVersion = inputData1;
		minorVersion = inputData0;
	}


	protected void processInput() {
		inputData = serial.read();
		if (waitForData > 0 && inputData < 128) {
			waitForData--;
			storedInputData[waitForData] = inputData;

			if((executeMultiByteCommand!=0) && (waitForData==0)) {
				//we got everything
				switch(executeMultiByteCommand) {
				case DIGITAL_MESSAGE:
					setDigitalInputs(storedInputData[1], storedInputData[0]);
					break;
				case ANALOG_MESSAGE:
					setAnalogInput(multiByteChannel, storedInputData[1], storedInputData[0]);
					break;
				case REPORT_VERSION:
					setVersion(storedInputData[1], storedInputData[0]);
					break;
				}
			}
		} else {
			int command;
			if(inputData < 0xF0) {
				command = inputData & 0xF0;
				multiByteChannel = inputData & 0x0F;
			} else {
				command = inputData;
				// commands in the 0xF* range don't use channel data
			}
			switch (command) {
			case DIGITAL_MESSAGE:
			case ANALOG_MESSAGE:
			case REPORT_VERSION:
				waitForData = 2;
				executeMultiByteCommand = command;
				break;      
			}
		}
	}
}
