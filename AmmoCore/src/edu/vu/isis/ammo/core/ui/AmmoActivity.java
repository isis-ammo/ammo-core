//There are things in this file that are prepared for the Android 3.0 port
//They are tagged by ANDROID3.0
package edu.vu.isis.ammo.core.ui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.ToggleButton;
import android.widget.TextView;
import edu.vu.isis.ammo.api.AmmoIntents;
import edu.vu.isis.ammo.core.OnStatusChangeListenerByName;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.ui.DistributorTabActivity;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.Netlink;
import edu.vu.isis.ammo.core.model.PhoneNetlink;
import edu.vu.isis.ammo.core.model.WifiNetlink;
import edu.vu.isis.ammo.core.model.WiredNetlink;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.network.NetworkService;
import edu.vu.isis.ammo.core.receiver.StartUpReceiver;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;

/**
 * The principle activity for the ammo core application.
 * Provides a means for...
 * ...changing the user preferences.
 * ...checking delivery status of various messages.
 * ...registering/unregistering content interest requests.
 *
 * @author phreed
 *
 */
public class AmmoActivity extends TabActivityEx implements OnStatusChangeListenerByName
{
    public static final Logger logger = LoggerFactory.getLogger(AmmoActivity.class);
    public static final Logger log_status = LoggerFactory.getLogger("scenario.network.status");

    private static final int VIEW_TABLES_MENU = Menu.NONE + 0;
    private static final int LOGGING_MENU = Menu.NONE + 1;
    private static final int DEBUG_MENU = Menu.NONE + 2;
    private static final int ABOUT_MENU = Menu.NONE + 3;

    // ===========================================================
    // Fields
    // ===========================================================

    private List<Gateway> gatewayModel = null;
    private GatewayAdapter gatewayAdapter = null;

    private List<Netlink> netlinkModel = new ArrayList<Netlink>();
    private NetlinkAdapter netlinkAdapter = null;

    public boolean netlinkAdvancedView = false;

    private Menu activity_menu;
    // ===========================================================
    // Views
    // ===========================================================

    private ListView gatewayList = null;
    private ListView netlinkList = null;

    private INetworkService networkServiceBinder;

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.info("::onServiceConnected - Network Service");
            networkServiceBinder = ((NetworkService.MyBinder) service).getService();
            initializeGatewayAdapter();
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.info("::onServiceDisconnected - Network Service");
            networkServiceBinder = null;
            // FIXME: what to do here if the NS goes away?
            // Change the model for the adapter to an empty list.
            // This situation should probably never happen, but we should
            // handle it properly anyway.
        }
    };

    private void initializeGatewayAdapter()
    {
        gatewayModel = networkServiceBinder.getGatewayList();

        // set gateway view references
        gatewayList = (ListView)findViewById(R.id.gateway_list);
        gatewayAdapter = new GatewayAdapter(this, gatewayModel);
        gatewayList.setAdapter(gatewayAdapter);

        //reset all rows
        for (int ix=0; ix < gatewayList.getChildCount(); ix++)
        {
            View row = gatewayList.getChildAt(ix);
            row.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * @Cateogry Lifecycle
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("::onCreate");
        this.setContentView(R.layout.ammo_activity);

        // Get a reference to the NetworkService.
        Intent networkServiceIntent = new Intent(this, NetworkService.class);
        boolean result = bindService( networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE );
        if ( !result )
            logger.error( "AmmoActivity failed to bind to the NetworkService!" );

        //netlinkModel = networkServiceBinder.getNetlinkList();

        // set netlink view references
        this.netlinkList = (ListView)this.findViewById(R.id.netlink_list);
        this.netlinkAdapter = new NetlinkAdapter(this, this.netlinkModel);
        netlinkList.setAdapter(netlinkAdapter);

        this.setNetlink(WifiNetlink.getInstance(this));
        this.setNetlink(WiredNetlink.getInstance(this));
        this.setNetlink(PhoneNetlink.getInstance(this));
        // this.setNetlink(JournalNetlink.getInstance(this));

        Intent intent = new Intent();

        // let others know we are running
        intent.setAction(StartUpReceiver.RESET);
        this.sendBroadcast(intent);


        // setup tabs
        Resources res = getResources(); // Resource object to get Drawables
        TabHost tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Reusable TabSpec for each tab

        spec = tabHost.newTabSpec("gateway");
        spec.setIndicator("Gateway", res.getDrawable(R.drawable.gateway_tab));
        spec.setContent(R.id.gateway_layout);
        getTabHost().addTab(spec);

        spec = tabHost.newTabSpec("netlink");
        spec.setIndicator("Link Status", res.getDrawable(R.drawable.netlink_32));
        spec.setContent(R.id.netlink_layout);
        getTabHost().addTab(spec);

        intent = new Intent().setClass(this, CorePreferenceActivity.class);
        spec = tabHost.newTabSpec("settings");
        spec.setIndicator("Preferences", res.getDrawable(R.drawable.cog_32));
        spec.setContent(intent);
        tabHost.addTab(spec);

        tabHost.setCurrentTab(0);
    }

    public void setNetlink(Netlink nl) {
        netlinkAdapter.add(nl);
    }


    @Override
    public void onStart() {
        super.onStart();
        logger.trace("::onStart");

        //reset all rows
        if ( gatewayList != null )
        {
            for (int ix=0; ix < gatewayList.getChildCount(); ix++)
            {
                View row = gatewayList.getChildAt(ix);
                row.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        mReceiver = new GatewayStatusReceiver();

        final IntentFilter statusFilter = new IntentFilter();
        statusFilter.addAction( AmmoIntents.AMMO_ACTION_GATEWAY_STATUS_CHANGE );
        registerReceiver( mReceiver, statusFilter );
    }

    private GatewayStatusReceiver mReceiver = null;

    @Override
    public void onStop() {
        super.onStop();
        for (Netlink nl : this.netlinkModel) {
            nl.teardown();
        }

        try {
            unregisterReceiver( mReceiver );
        } catch(IllegalArgumentException ex) {
            logger.trace("tearing down the gateway status object");
        }
    }

    private class GatewayStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //logger.info( "GatewayStatusReceiver::onReceive" );
            if ( gatewayAdapter != null )
                gatewayAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        logger.trace("::onCreateOptionsMenu");
        menu.add(Menu.NONE, VIEW_TABLES_MENU, Menu.NONE, getResources().getString(R.string.view_tables_label));
        menu.add(Menu.NONE, LOGGING_MENU, Menu.NONE, getResources().getString(R.string.logging_label));
        menu.add(Menu.NONE, DEBUG_MENU, Menu.NONE, getResources().getString((!this.netlinkAdvancedView)?(R.string.debug_label):(R.string.user_label)));
        menu.add(Menu.NONE, ABOUT_MENU, Menu.NONE, getResources().getString(R.string.about_label));

        //ANDROID3.0
        //Store the reference to the menu so we can use it in the toggle
        //function
        //this.activity_menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        logger.trace("::onPrepareOptionsMenu");

        menu.findItem(DEBUG_MENU).setTitle((!this.netlinkAdvancedView)?(R.string.debug_label):(R.string.user_label));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        logger.trace("::onOptionsItemSelected");

        Intent intent = new Intent();
        switch (item.getItemId()) {
        case DEBUG_MENU:
            toggleMode();
            return true;
        case VIEW_TABLES_MENU:
            intent.setClass(this, DistributorTabActivity.class);
            this.startActivity(intent);
            return true;
        case LOGGING_MENU:
            intent.setClass(this, LoggingPreferences.class);
            this.startActivity(intent);
            return true;
        case ABOUT_MENU:
            intent.setClass(this, AboutActivity.class);
            this.startActivity(intent);
                return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.trace("::onDestroy");
    }


    // ===========================================================
    // UI Management
    // ===========================================================

    public void onSettingsButtonClick(View view) {
        logger.trace("::onClick");

        Intent settingIntent = new Intent();
        settingIntent.setClass(this, CorePreferenceActivity.class);
        this.startActivity(settingIntent);
    }

    public void onGatewayElectionToggle(View view) {
        int position = this.gatewayList.getPositionForView(view);
        Gateway gw = (Gateway) this.gatewayAdapter.getItem(position);

        // get the button's row
        RelativeLayout row = (RelativeLayout)view.getParent();
        ToggleButton button = (ToggleButton)view;

        if (button.isChecked()) {
            gw.enable();
        }
        else {
            TextView t = (TextView)row.findViewById(R.id.gateway_status_text_one);
            t.setText("Disabling...");
            gw.disable();
        }

        row.refreshDrawableState();
    }

    // ===========================================================
    // Inner Classes
    // ===========================================================

    @Override
    public boolean onNetlinkStatusChange(String type, int[] status) {
        Netlink item = this.netlinkAdapter.getItemByType(type);
        item.onStatusChange(status);
        this.netlinkAdapter.notifyDataSetChanged();
        return true;
    }


    /*
     * Used to toggle the netlink view between simple and advanced.
     */
    public void toggleMode()
    {
        this.netlinkAdvancedView = !this.netlinkAdvancedView;
        this.netlinkAdapter.notifyDataSetChanged();
        this.gatewayAdapter.notifyDataSetChanged();

        //ANDROID3.0
        //There will be bugs. Ideally, when this toggles, we need to
        //refresh the menu. This line will invalidate it so that
        //onPrepareOptionsMenu(...) will be called when the user
        //opens it again.
        //this.activity_menu.invalidateOptionsMenu();

    }

}
