package edu.vu.isis.ammo.core.ethertracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.IBinder;
import android.preference.PreferenceManager;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.ApplicationEx;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.ServiceEx;

public class EthTrackSvc extends ServiceEx {

	private static final Logger logger = LoggerFactory.getLogger(EthTrackSvc.class);
    private ApplicationEx application;
    
	@Override
	public void onCreate() {
		this.application = (ApplicationEx)this.getApplication();
	}

	@Override
	public void onDestroy() {
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		logger.debug("::onStartCommand with intent {}", intent.getAction());
		handleCommand();
		
		this.application.setWiredState(WIRED_NETLINK_DOWN);
		
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/*
	 * @function handleCommand It initiates the RTM message handling by calling
	 * the native method initEthernetNative.
	 * 
	 * It then starts the thread which waits on any RTM messages signifying
	 * state changes of the interface
	 */
	public void handleCommand() {
		
		int ret = this.initEthernetNative();
		
		if (ret == -1)
		{
			logger.info("Error in InitEthernet: create or socket bind error, Exiting ...");
			return;
		}

		EtherStatReceiver stat = new EtherStatReceiver("ethersvc", this);
		stat.start();
	}

	private static final int HELLO_ID = 1;

	public static final int[] WIRED_NETLINK_UP = new int[] {1};
	public static final int[] WIRED_NETLINK_DOWN = new int[] {2};
	
	/*
	 * @function Notify Send a notification to android once interface goes up or
	 * down
	 */
	public int Notify(String msg) {
		this.updateSharedPreferencesForInterfaceStatus(msg);
		
		// Start specific application respond on selection
		
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = "Ethernet Status";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		Context context = getApplicationContext();

		CharSequence contentTitle = "Network Interface";
		CharSequence contentText = msg;

		Intent notificationIntent = new Intent(this, EthTrackSvc.class);

		PendingIntent contentIntent = PendingIntent
			.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		mNotificationManager.notify(HELLO_ID, notification);
		
		// Let applications respond immediately by receiving a broadcast intent.
		
		Intent broadcastIntent = new Intent(AmmoIntents.AMMO_ACTION_ETHER_LINK_CHANGE);
		if (msg.indexOf("Up") > 0) {
			broadcastIntent.putExtra("state",AmmoIntents.LINK_UP);
			this.application.setWiredState(WIRED_NETLINK_UP);
		} else if (msg.indexOf("Down") > 0) {
			broadcastIntent.putExtra("state", AmmoIntents.LINK_DOWN);
			this.application.setWiredState(WIRED_NETLINK_DOWN);
		}
		this.sendBroadcast(broadcastIntent);
		

		return 0;
	}
	
	/**
	 * Writes a flag to the system preferences based on status of interface.
	 * @param status - Status message relating to interface. Either "Up" or "Down"
	 */
	public void updateSharedPreferencesForInterfaceStatus(String msg) {
		boolean status = (msg.equals("Up")) ? true : false;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = prefs.edit();
		 
		editor.putBoolean(INetPrefKeys.PHYSICAL_LINK_PREF_IS_ACTIVE, status);
		editor.commit();
	}

	/*
	 * A native method that is implemented by the 'android_net_ethernet.cpp'
	 * native library, which is packaged with this application.
	 * 
	 * This function waits for any event from the underlying interface
	 */
	public native String waitForEvent();

	/*
	 * This is a native function implemented in android_net_ethernet function.
	 * It initiates the ethernet interface
	 */
	public native int initEthernetNative();

	/*
	 * This function is not used now
	 */
	public native String getInterfaceName(int index);

	/*
	 * This function is not used now
	 */
	public native int getInterfaceCnt();

	/*
	 * @class EtherStatReceiver
	 * 
	 * This extends the Thread class and makes a call on the Native waitForEvent
	 * function.
	 * 
	 * The waitForEvent returns when there is a status change in the underlying
	 * interface.
	 */
	private class EtherStatReceiver extends Thread {

		EthTrackSvc parent;

		/*
		 * The Thread constructor
		 */

		public EtherStatReceiver(String str, EthTrackSvc _parent) {
			super(str);
			parent = _parent;
		}

		/*
		 * The main thread function.
		 * 
		 * It makes a call on the waitForEvent and makes call on the notify
		 * function to send a notification once the interface is either up or
		 * down.
		 */
		public void run() {
			while (true) {

				String res = waitForEvent();
				
				if (res.indexOf("Error") > 0)
				{
					logger.info("Error in waitForEvent: Exiting Thread");
					return;
				}

				// send the notification if the interface is
				// up or down
				if ((res.indexOf("Up") > 0) || (res.indexOf("Down") > 0)) {
					parent.Notify(res);

					// Write a true/false value to the shared preferences
					// indicating status
					// of connection.

				}

				try {
					sleep((int) (Math.random() * 1000));
				} catch (InterruptedException e) {
				}
			}
			// logger.info("Thread Done");
		}
	}

	static {
        System.loadLibrary("ethrmon");
    }
}
