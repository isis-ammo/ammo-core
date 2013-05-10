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
package edu.vu.isis.ammo.core.provider;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.ui.ProviderAdapter;

public class ChannelProvider extends ContentProvider {
    List<ModelChannel> _gatewayList = null;//new ArrayList<ModelChannel>(gChannels.values());
    
    public static final Logger logger = LoggerFactory.getLogger("ui");
    
    public final Handler myHandler = new Handler();
    
    private ProviderAdapter adapter;
    
    private boolean queryStarted = false;
    
    private boolean isConnected = false;
    private static final Intent AMMO_SERVICE;
    static {
        AMMO_SERVICE = new Intent();
        final ComponentName serviceComponent = 
                new ComponentName(AmmoService.class.getPackage().getName(), 
                        AmmoService.class.getCanonicalName());
        AMMO_SERVICE.setComponent(serviceComponent);
    }
    
    public static final Uri CONTENT_URI = Uri.parse("content://edu.vu.isis.ammo.core.provider.channel/Channel");
    
    private INetworkService networkServiceBinder;
    
    Messenger mService = null;
    
    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        final private ChannelProvider parent = ChannelProvider.this; 

        public void onServiceConnected(ComponentName name, IBinder service) {
            logger.trace("::onServiceConnected - Network Service");
            final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
            parent.networkServiceBinder = binder.getService();
            getGatewayList();
            
            isConnected = true;
            
            adapter = new ProviderAdapter(getContext(), _gatewayList);
            
            mService = new Messenger(binder.getMessenger());
            
            try {
                Message msg = Message.obtain(null,
                        1);
                msg.replyTo = mMessenger;
                mService.send(msg);
                } 
            catch (RemoteException e) {
            }
            
            Toast.makeText(parent.getContext().getApplicationContext(), "Service should be retrieved", Toast.LENGTH_SHORT).show();

            // Netlink Adapter is disabled for now (doesn't work)
            //initializeNetlinkAdapter();
        }

        public void onServiceDisconnected(ComponentName name) {
            logger.trace("::onServiceDisconnected - Network Service");
            parent.networkServiceBinder = null;
            isConnected = false;
        }
    };
    
    @Override
    public boolean onCreate() {
        boolean status = this.getContext().bindService(AMMO_SERVICE, networkServiceConnection, Context.BIND_AUTO_CREATE);
        logger.trace("ChannelProvider onCreate - Attempting to bind to service. Status = {}", status);
        
        updateLoop();
        
        return status;
        
    }

    private void getGatewayList(){
        _gatewayList = networkServiceBinder.getGatewayList();
    }
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        boolean sendColors = false;
        if (isConnected){
        	
        	if (queryStarted == false){
        		queryLoop();
        		queryStarted = true;
        	}
        	
            if (selection != null){ // Check whether we need to send the color information this pass
                sendColors = true;  // 
            }
            //fakeCount++;
            String[] cols;
            if (!sendColors){
                cols = new String[]{"_id", "Name","FormalIP","StatusOne","StatusTwo","Send","Receive"};
            } else {
                cols = new String[]{"_id", "Name","FormalIP","StatusOne","StatusTwo","Send","Receive","ColorOne","ColorTwo"};// add color functionality
            }
            
            MatrixCursor mc = new MatrixCursor(cols);

            getGatewayList();
            
            int count = adapter.getCount();
            
            RelativeLayout[] itemArray = new RelativeLayout[count];
            
            String[] operatorRow = new String[cols.length];
            
            operatorRow[0] = Integer.toString(0);
            operatorRow[1] = adapter.getOperatorId();
            logger.error("["+0+"]["+0+"]= {" + operatorRow[0]+"}"); //TODO change to trace
            logger.error("["+0+"]["+1+"]= {" + operatorRow[1]+"}"); //TODO change to trace
            
            mc.addRow(operatorRow);
            
            for (int i = 0; i < count; i++){
                itemArray[i] = (RelativeLayout) adapter.getView(i, null, null);
            }
            String[][] rowChildren;

            rowChildren = new String[count][cols.length]; // the number of columns in the MC must match 
                                                          // the number of items in the String Array we pass in.
                                                          // it will be either 7 or 9
            logger.error("Child Count for 0 is : " + itemArray[0].getChildCount()); //TODO change to trace
            
            for (int i = 0; i < count; i++){
                for (int j = 0; j < rowChildren[0].length; j++){
                    if (j == 0){
                        rowChildren[i][j] = Integer.toString(i+1); //the string form of the value "i+1"; we only send 0 as id if we are sending operator
                    } else if (j < 4){
                        rowChildren[i][j] = ((TextView) (itemArray[i]).getChildAt(j)).getText().toString();
                    } else if (j == 4) {  
                    	if (adapter.getTextTwoVisibility(i)){
                    		rowChildren[i][j] = ((TextView) (itemArray[i]).getChildAt(j)).getText().toString();
                    	} else {
                    		rowChildren[i][j] = "";
                    	}
                    } else if (j == 5){
                        LinearLayout tempLL = (LinearLayout) (itemArray[i]).getChildAt(j);
                        rowChildren[i][j] = ((TextView) tempLL.getChildAt(0)).getText().toString(); //first child of LL, send stats
                        rowChildren[i][j+1] = ((TextView) tempLL.getChildAt(1)).getText().toString(); // second child of LL, receive stats
                    } else if (j == 7){//j can only be 7 or 8 if we added extra rows to rowChildren when we created it
                    	rowChildren[i][j] = ((Integer)adapter.getColor(i, 0)).toString();
                    } else if (j == 8){
                    	rowChildren[i][j] = ((Integer)adapter.getColor(i, 1)).toString();
                    }
                    logger.error("["+i+"]["+j+"]= {" + rowChildren[i][j]+"}"); //TODO change to trace
                }
                mc.addRow(rowChildren[i]);
            }
            
            
            /*Iterator<ModelChannel> _it = _gatewayList.iterator();
            
            int id = 0;
            while (_it.hasNext()) {
                ModelChannel cm = _it.next();
                View view = new View(getContext());
                
                LayoutInflater inflater = (LayoutInflater)getContext().getSystemService
                          (Context.LAYOUT_INFLATER_SERVICE);
                row = (RelativeLayout) cm.getView(view, inflater);
                
                String str1 = (String) ((TextView)row.getChildAt(row.getChildCount()-4)).getText();
                String str2 = (String) ((TextView)row.getChildAt(row.getChildCount()-3)).getText();
                String str3 = (String) ((TextView)row.getChildAt(row.getChildCount()-2)).getText();
                String str4 = (String) ((TextView)row.getChildAt(row.getChildCount()-1)).getText();
                String str5 = (String) ((TextView)row.getChildAt(row.getChildCount()-5)).getText();
                
                //logger.error("Rows = [" + row.getChildCount() + "]; Data = [" + str1 +"] ["+ str2 +"] ["+ str3 +"] ["+ str4+"] [" + str5 + "]");
                
                String[] temp = {""+id, str1, str2, "Stuff: "+str3+fakeCount, str4+fakeCount};
                mc.addRow(temp);
                id++;
            }*/
            mc.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
            
            return mc;
        }
        logger.error("Channel provider is not connected to service yet");
        return null;
    }

    @Override
    public String getType(Uri uri) {
        if (isConnected){
            return "ChannelModel";
        }
        logger.error("Channel provider is not connected to service yet");
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }
    
    
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    private ChannelProvider _parent = this;
    
    private int msgCount = 0;
    
    /**
     * Handler of incoming messages from service. This allows the service (or anyone, really) to initiate requeries from the separate process UI. 
     */
    class IncomingHandler extends Handler {
        ChannelProvider parent = _parent;
        Handler handler = myHandler;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    //getGatewayList();
                    msgCount++;
                    //Toast.makeText(getContext(), "Inside provider handler method: " + msgCount, Toast.LENGTH_SHORT).show();
                    getContext().getContentResolver().notifyChange(ChannelProvider.CONTENT_URI,null);
                    
                    if (msgCount%5 == 0){
                        Toast.makeText(getContext(), "Notification sent from provider " + msgCount, Toast.LENGTH_SHORT).show();
                    }
                    
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    
    
    //TODO remove this (see below)
    /**
     * This is for TESTING PURPOSES ONLY. It unbinds and re-binds every 15 seconds. 
     * This does not need to be in the final version (if the app works without it).
     */
    private void updateLoop(){
    	
        this.getContext().unbindService(networkServiceConnection);
        
        boolean status = this.getContext().bindService(AMMO_SERVICE, networkServiceConnection, Context.BIND_AUTO_CREATE);
        logger.trace("ChannelProvider updateLoop - Attempting to bind to service. Status = {}", status);
        /*if (currentToast != null){
            currentToast.setText("update");
        }*/
        (myHandler).postDelayed(new Runnable(){

            @Override
            public void run() {
                updateLoop();
            }
            
        }, 15000);
    }
    
    
    /**
     * queryLoop is the loop function that runs to make the whole data transfer process work. 
     * The channelProvider notifies the UI (through the cursor) that the data has changed. 
     * The UI re-queries and gets a new version of data. 
     * There is also a way to initiate this from AmmoService using message passing if desired. 
     */
    private void queryLoop(){
    	getContext().getContentResolver().notifyChange(ChannelProvider.CONTENT_URI,null);
    	
    	myHandler.postDelayed(new Runnable(){

            @Override
			public void run() {
				queryLoop();
			}
    		
    	}, 1000);
    }
    
}
