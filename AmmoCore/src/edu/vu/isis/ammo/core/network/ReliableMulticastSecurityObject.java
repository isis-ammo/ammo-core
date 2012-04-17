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
// ReliableMulticastSecurityObject.java

package edu.vu.isis.ammo.core.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.pb.AmmoMessages;


public class ReliableMulticastSecurityObject implements ISecurityObject,
                                                        INetworkService.OnSendMessageHandler
{
    private static final Logger logger = LoggerFactory.getLogger("net.rmcast.security");

    ReliableMulticastSecurityObject( ReliableMulticastChannel iChannel )
    {
        logger.trace( "Constructor of ReliableMulticastSecurityObject." );
        mChannel = iChannel;
    }


    public void authorize( AmmoMessages.MessageWrapper.Builder mwb  )
    {
        logger.trace( "ReliableMulticastSecurityObject::authorize()." );

        // This code is a hack to have authentication work before Nilabja's new
        // code is ready.
        //AmmoGatewayMessage agm = AmmoGatewayMessage.getInstance( mwb, this );

        //mChannel.putFromSecurityObject( agm );
        //mChannel.finishedPuttingFromSecurityObject();
    }


    public boolean deliverMessage( AmmoGatewayMessage agm )
    {
        logger.trace( "Delivering message to ReliableMulticastSecurityObject." );

        // For now we haven't implemented any security.  Just
        // authorize if we receive a packet back from the server.
        //mChannel.authorizationSucceeded( agm );

        // If authorization fails, call mChannel.authorizationFailed();

        return true;
    }


    public boolean ack( String channel, DisposalState status )
    {
        return true;
    }

    @SuppressWarnings("unused")
	private ReliableMulticastChannel mChannel;
}
