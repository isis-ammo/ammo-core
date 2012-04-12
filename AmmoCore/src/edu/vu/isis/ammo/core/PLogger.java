package edu.vu.isis.ammo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * These are the loggers for Threads/Processes/Queues/Intents
 * 
 * The view for these loggers is that of functional decomposition.
 * These loggers should be named according to the pattern...
 * <component>.proc.<thread/process>.<class>.<property>
 * <component>.ipc.<queue/intent/shared-pref>.<class>.<property>
 * 
 * All of these loggers belong to the "omma" component.
 * These loggers are used for monitoring communication between components.
 * 
 */
public interface PLogger {
	// omma threads/processes
	public static final Logger TOP = LoggerFactory.getLogger( "proc.top" );
	public static final Logger DIST = LoggerFactory.getLogger( "proc.dist" );
	public static final Logger POLICY = LoggerFactory.getLogger( "proc.policy" );
	public static final Logger CHANNEL = LoggerFactory.getLogger( "proc.serve.channel" );
	
	// omma queues
	public static final Logger IPC_CONN = LoggerFactory.getLogger( "ipc.channel.conn" );
	public static final Logger IPC_SEND = LoggerFactory.getLogger( "ipc.channel.send" );
	public static final Logger IPC_RECV = LoggerFactory.getLogger( "ipc.channel.recv" );
	
	public static final Logger IPC_LOCAL = LoggerFactory.getLogger( "ipc.local" );
	
	public static final Logger IPC_PANTHR = LoggerFactory.getLogger( "ipc.panthr" );
	public static final Logger IPC_PANTHR_GW = LoggerFactory.getLogger( "ipc.panthr.gateway" );
	public static final Logger IPC_PANTHR_MC = LoggerFactory.getLogger( "ipc.panthr.multicast" );
	public static final Logger IPC_PANTHR_RMC = LoggerFactory.getLogger( "ipc.panthr.reliable" );
	public static final Logger IPC_PANTHR_SERIAL = LoggerFactory.getLogger( "ipc.panthr.serial" );
	public static final Logger IPC_PANTHR_JOURNAL = LoggerFactory.getLogger( "ipc.panthr.journal" );
	
	// omma data store
	public static final Logger STORE = LoggerFactory.getLogger( "store" );
	public static final Logger STORE_DDL = LoggerFactory.getLogger( "store.ddl" );
	public static final Logger STORE_DML = LoggerFactory.getLogger( "store.dml" );
	public static final Logger STORE_DQL = LoggerFactory.getLogger( "store.dql" );
	public static final Logger STORE_POSTAL = LoggerFactory.getLogger( "store.postal" );
	
	// omma intents
	public static final Logger IPC_INTENT = LoggerFactory.getLogger( "ipc.intent.service" );
	public static final Logger IPC_BCAST = LoggerFactory.getLogger( "ipc.broadcast" );
	public static final Logger IPC_ACTIVITY = LoggerFactory.getLogger( "ipc.activity" );
}
