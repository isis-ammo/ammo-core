package edu.vu.isis.ammo.core.model;

import android.content.SharedPreferences;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.ui.TabActivityEx;


public class WiredNetlink extends Netlink {

	private WiredNetlink(TabActivityEx context) {
		super(context, "Wired Netlink", "wired");
	}
	
	public static Netlink getInstance(TabActivityEx context) {
		// initialize the gateway from the shared preferences
		return new WiredNetlink(context);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(INetPrefKeys.WIRED_PREF_SHOULD_USE)) {
		      //shouldUse(prefs);
		}
	}
	
	public void initialize() {
		this.statusListener.onStatusChange(this.statusView, this.application.getWiredNetlinkState() );
	}

}
