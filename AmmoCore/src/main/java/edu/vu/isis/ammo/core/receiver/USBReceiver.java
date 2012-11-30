/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
*/
package edu.vu.isis.ammo.core.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * USBReceiver is a broadcast receiver which handles system intents broadcast
 * when a device is connected via USB to some other device.
 * 
 * NOTE: It turns out that Google doesn't really want you to connect an 
 * android application to another device via USB. For this reason, all usb
 * synchronization will be handled from the external device side.
 *
 */
public class USBReceiver extends BroadcastReceiver {

	// ===========================================================
	// Constants
	// ===========================================================
private static final Logger logger = LoggerFactory.getLogger("receiver.usb");
	
	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Broadcast Receiver
	// ===========================================================

	@Override
	public void onReceive(Context context, Intent intent) {
		logger.debug("::onReceive");
	}
}