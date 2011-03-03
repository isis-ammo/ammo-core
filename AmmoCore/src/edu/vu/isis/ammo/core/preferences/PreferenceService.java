package edu.vu.isis.ammo.core.preferences;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.IPrefKeys;

public class PreferenceService extends Service {
	 @Override
	    public void onCreate() {
	        super.onCreate();
	 }
	 
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	private final IPreferenceService.Stub mBinder = new IPreferenceService.Stub() {
		
		@Override
		public String getOperatorId() throws RemoteException {
			Log.d("PreferenceService", "::getOperatorId()");
			return PreferenceManager.getDefaultSharedPreferences(PreferenceService.this).getString(IPrefKeys.PREF_OPERATOR_ID, "foo");
		}
		
		@Override
		public String getDeviceId() throws RemoteException {
			return PreferenceManager.getDefaultSharedPreferences(PreferenceService.this).getString(INetPrefKeys.PREF_DEVICE_ID, "");
		}
	};

}