/**
 * 
 */
package edu.vu.isis.ammo.core.network;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Looper;

/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 * 
 * @author phreed
 *
 */
public class TcpChannel {
	private static final Logger logger = LoggerFactory.getLogger(TcpChannel.class);

	private static final int BURP_TIME = 5 * 1000; // 5 seconds expressed in milliseconds
	private boolean isEnabled = false;

	private Socket socket = null;
	private ConnectorThread connectorThread;
	private ReceiverThread receiverThread;
	private SenderThread senderThread;

	private int connectTimeout = 5 * 1000;     // this should come from network preferences
	private int socketTimeout = 5 * 1000; // milliseconds.

	private String gatewayHost = null;
	private int gatewayPort = -1;

	private ByteOrder endian = ByteOrder.LITTLE_ENDIAN;
	private final Object syncObj;

	private long flatLineTime;
	private NetworkService driver;

	private TcpChannel(NetworkService driver) {
		super();
		logger.trace("Thread <{}>TcpChannel::<constructor>", Thread.currentThread().getId());
		this.syncObj = this;
		
		this.driver = driver;
		this.connectorThread = new ConnectorThread(this, driver);
		this.senderThread = new SenderThread(this, driver);
		this.receiverThread = new ReceiverThread(this, driver);
		
		this.flatLineTime = 20 * 60 * 1000; // 20 minutes in milliseconds
	}

	public static TcpChannel getInstance(NetworkService driver) {
		logger.trace("Thread <{}>::getInstance", Thread.currentThread().getId());
		TcpChannel instance = new TcpChannel(driver);
		return instance;
	}

	public boolean isConnected() { return this.connectorThread.isConnected(); }
	/** 
	 * Was the status changed as a result of enabling the connection.
	 * @return
	 */
	public boolean isEnabled() { return this.isEnabled; }
	public boolean enable() {
		logger.trace("Thread <{}>::enable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (this.isEnabled == true) 
				return false;
			this.isEnabled = true;

			if (! this.connectorThread.isAlive()) this.connectorThread.start();
			if (! this.senderThread.isAlive()) this.senderThread.start();
			if (! this.receiverThread.isAlive()) this.receiverThread.start();
		}
		return true;
	}
	public boolean disable() {
		logger.trace("Thread <{}>::disable", Thread.currentThread().getId());
		synchronized (this.syncObj) {
			if (this.isEnabled == false) 
				return false;
			this.isEnabled = false;

//			this.connectorThread.stop();
//			this.senderThread.stop();
//			this.receiverThread.stop();
		}
		return true;
	}

	public boolean setConnectTimeout(int value) {
		logger.trace("Thread <{}>::setConnectTimeout {}", Thread.currentThread().getId(), value);
		this.connectTimeout = value;
		return true;
	}
	public boolean setSocketTimeout(int value) {
		logger.trace("Thread <{}>::setSocketTimeout {}", Thread.currentThread().getId(), value);
		this.socketTimeout = value;
		this.reset();
		return true;
	}
	
	public void setFlatLineTime(long flatLineTime) {
		this.flatLineTime = flatLineTime;
	}

	public boolean setHost(String host) {
		logger.trace("Thread <{}>::setHost {}", Thread.currentThread().getId(), host);
		if ( gatewayHost != null && gatewayHost.equals(host) ) return false;
		this.gatewayHost = host;
		this.reset();
		return true;
	}
	public boolean setPort(int port) {
		logger.trace("Thread <{}>::setPort {}", Thread.currentThread().getId(), port);
		if (gatewayPort == port) return false;
		this.gatewayPort = port;
		this.reset();
		return true;
	}

	public String toString() {
		return "socket: host["+this.gatewayHost+"] port["+this.gatewayPort+"]";
	}

	/**
	 * forces a reconnection.
	 */
	public void reset() { 
		logger.trace("Thread <{}>::reset", Thread.currentThread().getId());
		logger.trace("connector: {} sender: {} receiver: {}",
				new String[] {
				this.connectorThread.showState(), 
				this.senderThread.showState(), 
				this.receiverThread.showState()});
		
		synchronized (this.syncObj) {
			if (! this.connectorThread.isAlive()) {
				this.connectorThread = new ConnectorThread(this, this.driver);
				this.connectorThread.start();
			}
			if (! this.senderThread.isAlive()) {
				this.senderThread = new SenderThread(this, this.driver);
				this.senderThread.start();
			}
			if (! this.receiverThread.isAlive()) {
				this.receiverThread = new ReceiverThread(this, this.driver);
				this.receiverThread.start();
			}

			this.connectorThread.reset();
		}
	}

	/**
	 * manages the connection.
	 * enable or disable expresses the operator intent.
	 * There is no reason to run the thread unless the channel is enabled.
	 * 
	 * Any of the properties of the channel 
	 * @author phreed
	 *
	 */
	private static class ConnectorThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(ConnectorThread.class);

		private static final String DEFAULT_HOST = "10.0.2.2";
		private static final int DEFAULT_PORT = 32896;
		private static final int GATEWAY_RETRY_TIME = 20 * 1000; // 20 seconds

		private TcpChannel parent;
		private INetworkService.OnConnectHandler handler;
		private final State state;

		private long heartstamp;
		public long getHeartStamp() {
			synchronized (this.state) {
				return this.heartstamp;
			}
		}
		public void resetHeartStamp() {
			synchronized (this.state) {
				this.heartstamp = System.currentTimeMillis();
			}
		}
		
		private ConnectorThread(TcpChannel parent, INetworkService.OnConnectHandler handler) {
			logger.trace("Thread <{}>ConnectorThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.state = new State();
			
			this.handler = handler;
			this.resetHeartStamp();
		}
		
		private class State {
			private int value;
			private int actual;

			static private final int CONNECTED     = 0; // the socket is good an active
			static private final int CONNECTING    = 1; // trying to connect
			static private final int DISCONNECTED  = 2; // the socket is disconnected
			static private final int STALE         = 4; // indicating there is a message
			static private final int LINK_WAIT     = 5; // indicating the underlying link is down 
			static private final int EXCEPTION     = 6; // something really bad happened
			
			private long attempt; // used to uniquely name the connection
			
			public State() { 
				this.value = STALE; 
				this.attempt = Long.MIN_VALUE;
			}
			public synchronized void set(int state) {
				logger.trace("Thread <{}>State::set {}", Thread.currentThread().getId(), this.toString());
				if (state == STALE) {
					logger.error("set stale only from the special setStale method");
					return;
				}
				this.value = state; 
				this.notifyAll(); 
			}
			public synchronized int get() { return this.value; }

			public synchronized boolean isConnected() { 
				return this.value == CONNECTED; 
			}

			/**
			 * Previously this method would only set the state to stale
			 * if the current state were CONNECTED.  It may be important
			 * to return to STALE from other states as well.
			 * For example during a failed link attempt.
			 * Therefore if the attempt value matches then reset to STALE
			 * This also causes a reset to reliably perform a notify.
			 * 
			 * @param attempt value (an increasing integer)
			 * @return
			 */
			public synchronized boolean failure(long attempt) {
				if (attempt != this.attempt) return true;
				attempt++;
				this.value = STALE;
				this.notifyAll(); 
				return true;
			}
			
			public String showState () {
				if (this.value == this.actual)
					return this.showStateAux(this.value);
				else
					return this.showStateAux(this.actual) + "->" + this.showStateAux(this.actual);
			}
			public String showStateAux (int state)
			{
				switch (state)
				{
				case CONNECTED:     return "CONNECTED";
				case CONNECTING:    return "CONNECTING";
				case DISCONNECTED:  return "DISCONNECTED";
				case STALE:         return "STALE";
				case LINK_WAIT:     return "LINK_WAIT";
				case EXCEPTION:     return "EXCEPTION";
				default:
					return "Undefined State";									
				}
			}
		}
		
		public boolean isConnected() { 
			return this.state.isConnected(); 
		}
		public long getAttempt() { 
			return this.state.attempt; 
		}
		public String showState() { return this.state.showState( ); }
		
		/**
		 * reset forces the channel closed if open.
		 */
		public void reset() { 
			this.state.failure(this.state.attempt);
		}
		
		public void failure(long attempt) { 
			this.state.failure(attempt); 
		}	

		/**
		 * A value machine based.
		 * Most of the time this machine will be in a CONNECTED value.
		 * In that CONNECTED value the machine wait for the connection value to 
		 * change or for an interrupt indicating that the thread is being shut down.
		 *  
		 *  The value machine takes care of the following constraints:
		 * We don't need to reconnect unless.
		 * 1) the connection has been lost 
		 * 2) the connection has been marked stale
		 * 3) the connection is enabled.
		 * 4) an explicit reconnection was requested
		 * 
		 * @return
		 */
		@Override
		public void run() { 
			try {
				logger.trace("Thread <{}>ConnectorThread::run", Thread.currentThread().getId());
				MAINTAIN_CONNECTION: while (true) {
					logger.debug("state: {}",this.showState());

					switch (this.state.get()) {
					case State.STALE: 
						disconnect();
						this.state.set(State.LINK_WAIT);
						break;

					case State.LINK_WAIT:
						if (isLinkUp()) {
							this.state.set(State.DISCONNECTED);
						} 
						// on else wait for link to come up TODO triggered through broadcast receiver
						break;

					case State.DISCONNECTED:
						if ( !this.connect() ) {
							this.state.set(State.CONNECTING);
						} else {
							this.state.set(State.CONNECTED);
						}
						break;

					case State.CONNECTING: // keep trying
						if ( this.connect() ) {
							this.state.set(State.CONNECTED);
						} else {
							try {
								Thread.sleep(GATEWAY_RETRY_TIME);
							} catch (InterruptedException ex) {
								logger.info("sleep interrupted - intentional disable, exiting thread ...");
								this.reset();
								break MAINTAIN_CONNECTION;
							}
						}
						break;

					case State.CONNECTED:
						handler.auth();
					default: {
						try {
							synchronized (this.state) {
								while (this.isConnected()) // this is IMPORTANT don't remove it.
									this.state.wait(BURP_TIME);   // wait for somebody to change the connection status
							}
						} catch (InterruptedException ex) {
							logger.info("connection intentionally disabled {}", this.state );
							this.state.set(State.STALE);
							break MAINTAIN_CONNECTION;
						}
					}
					}
				}

			} catch (Exception ex) {
				this.state.set(State.EXCEPTION); 
			}
			try {
				this.parent.socket.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		private boolean disconnect() {
			logger.trace("Thread <{}>ConnectorThread::disconnect", Thread.currentThread().getId());
			try {
				if (this.parent.socket == null) return true;
				this.parent.socket.close();
			} catch (IOException e) {
				return false;
			}
			return true;
		}

		private boolean isLinkUp() { return true; }

		/**
		 * connects to the gateway
		 * @return
		 */
		private boolean connect() {
			logger.trace("Thread <{}>ConnectorThread::connect", Thread.currentThread().getId());

			String host = (parent.gatewayHost != null) ? parent.gatewayHost : DEFAULT_HOST;
			int port =  (parent.gatewayPort > 10) ? parent.gatewayPort : DEFAULT_PORT;
			InetAddress ipaddr = null;
			try {
				ipaddr = InetAddress.getByName(host);
			} catch (UnknownHostException e) {
				logger.info("could not resolve host name");
				return false;
			}
			parent.socket = new Socket();
			InetSocketAddress sockAddr = new InetSocketAddress(ipaddr, port);
			try {
				parent.socket.connect(sockAddr, parent.connectTimeout);
			} catch (IOException ex) {
				logger.warn("connection to {}:{} failed : " + ex.getLocalizedMessage(), ipaddr, port);
				parent.socket = null;
				return false;
			}
			if (parent.socket == null) return false;
			try {
				parent.socket.setSoTimeout(parent.socketTimeout);
			} catch (SocketException ex) {
				return false;
			}
			logger.info("connection to {}:{} established ", ipaddr, port);
			return true;
		}
		
	}    

	/** 
	 * do your best to send the message.
	 * 
	 * @param size
	 * @param checksum
	 * @param message
	 * @return
	 */
	public boolean sendRequest(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler) 
	{
		synchronized (this.syncObj) {
			this.senderThread.putMsg(new GwMessage(size, checksum, payload, handler) );
			return true;
		}
	}

	public class GwMessage {
		public final int size;
		public final CRC32 checksum;
		public final byte[] payload;
		public final INetworkService.OnSendMessageHandler handler;
		public GwMessage(int size, CRC32 checksum, byte[] payload, INetworkService.OnSendMessageHandler handler) {
			this.size = size; 
			this.checksum = checksum; 
			this.payload = payload; 
			this.handler = handler;
		}
	}

	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public static class SenderThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(SenderThread.class);
		
		static private final int WAIT_CONNECT  = 1; // waiting for connection
		static private final int SENDING       = 2; // indicating the next thing is the size
		static private final int TAKING        = 3; // indicating the next thing is the size
		static private final int INTERRUPTED   = 4; // the run was canceled via an interrupt
		static private final int EXCEPTION     = 5; // the run failed by some unhandled exception
		
		public String showState () {
			if (this.state == this.actual)
				return this.showState(this.state);
			else
				return this.showState(this.actual) + "->" + this.showState(this.actual);
		}
		private String showState (int state)
		{
			switch (state)
			{
			case WAIT_CONNECT:  return "WAIT_CONNECT";
			case SENDING:       return "SENDING";
			case TAKING:        return "TAKING";
			case INTERRUPTED:   return "INTERRUPTED";
			case EXCEPTION:     return "EXCEPTION";
			default:
				return "Undefined State";									
			}
		}
		
		volatile private int state;
		volatile private int actual;

		private final TcpChannel parent;
		private ConnectorThread connector;
		@SuppressWarnings("unused")
		private final INetworkService.OnSendMessageHandler handler;

		private final BlockingQueue<GwMessage> queue;

		private SenderThread(TcpChannel parent, INetworkService.OnSendMessageHandler handler) {
			logger.trace("Thread <{}>SenderThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;
			this.connector = parent.connectorThread;
			this.queue = new LinkedBlockingQueue<GwMessage>(20);
		}
		
		public void updateConnector(ConnectorThread connector) {
			this.connector = connector;
		}

		public void putMsg(GwMessage msg) {
			try {
				this.queue.put(msg);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}

		private void failOutStream(OutputStream os, long attempt) { 
			if (os == null) return;
			try {
				os.close();
			} catch (IOException ex) {
				logger.warn("close failed {}", ex.getLocalizedMessage());
			}
			this.connector.failure(attempt);
		}
		/**
		 * Initiate a connection to the server and then wait for a response.
		 * All responses are of the form:
		 * size     : int32
		 * checksum : int32
		 * bytes[]  : <size>
		 * This is done via a simple value machine.
		 * If the checksum doesn't match the connection is dropped and restarted.
		 * 
		 * Once the message has been read it is passed off to...
		 */
		@Override
		public void run() { 
			logger.trace("Thread <{}>SenderThread::run", Thread.currentThread().getId());

			this.state = TAKING;

			DataOutputStream dos = null;
			try {            
				// one integer for size & four bytes for checksum
				ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE/Byte.SIZE + 4);
				buf.order(parent.endian);
				GwMessage msg = null;
				long attempt = Long.MAX_VALUE;

				while (true) {
					logger.debug("state: {}",this.showState());
					this.actual = this.state;
					switch (state) {
					case WAIT_CONNECT:
						synchronized (this.connector.state) {
							while (! this.connector.isConnected()) {
								try {
									logger.trace("Thread <{}>SenderThread::value.wait",
											Thread.currentThread().getId());

									this.connector.state.wait(BURP_TIME);
								} catch (InterruptedException ex) {
									logger.warn("thread interupted {}",ex.getLocalizedMessage());
									return ; // looks like the thread is being shut down.
								}
							}
							attempt = this.connector.getAttempt();
						}
						try {
							// if connected then proceed
							// keep the working socket so that if something goes wrong
							// the socket can be checked to see if it has changed
							// in the interim.			
							OutputStream os = this.parent.socket.getOutputStream();
							dos = new DataOutputStream(os);
						} catch (IOException ex) {
							logger.warn("io exception acquiring socket for writing messages {}", ex.getLocalizedMessage());
							if (msg.handler != null) msg.handler.ack(false);
							this.failOutStream(dos, attempt);
							break;
						} 
						state = SENDING;
						break;

					case TAKING:
						msg = queue.take(); // THE MAIN BLOCKING CALL
						state = WAIT_CONNECT;
						break;
						
					case SENDING:
						buf.rewind();
						buf.putInt(msg.size);
						long cvalue = msg.checksum.getValue();
						byte[] checksum = new byte[] {
								(byte)cvalue,
								(byte)(cvalue >>> 8),
								(byte)(cvalue >>> 16),
								(byte)(cvalue >>> 24)
						};
						logger.debug("checksum [{}]", checksum);

						buf.put(checksum, 0, 4);
						try {
							dos.write(buf.array());
							dos.write(msg.payload);
							dos.flush();
						} catch (SocketException ex) {
							logger.warn("exception writing to a socket {}", ex.getLocalizedMessage());
							if (msg.handler != null) msg.handler.ack(false);
							this.failOutStream(dos, attempt);
							this.state = WAIT_CONNECT;
							break;

						} catch (IOException ex) {
							logger.warn("io exception writing messages");
							if (msg.handler != null) msg.handler.ack(false);
							this.failOutStream(dos, attempt);
							this.state = WAIT_CONNECT;
							break;
						} 

						// legitimately sent to gateway.
						if (msg.handler != null) msg.handler.ack(true);
						this.connector.resetHeartStamp();
						
						state = TAKING;
						break;
					}
				}
			} catch (InterruptedException ex) {
				logger.warn("interupted writing messages");
				this.state = INTERRUPTED;
			} catch (Exception ex) {
				logger.warn("interupted writing messages");
				this.state = EXCEPTION;
			}
			logger.warn("sender thread exiting ...");
		}
	}
	/**
	 * A thread for receiving incoming messages on the socket.
	 * The main method is run().
	 *
	 */
	public static class ReceiverThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(ReceiverThread.class);

		final private INetworkService.OnReceiveMessageHandler handler;

		private TcpChannel parent = null;
		private ConnectorThread connector = null;
		
		// private TcpChannel.ConnectorThread;
		volatile private int state;
		volatile private int actual;

		static private final int SHUTDOWN        = 0; // the run is being stopped
		static private final int START           = 1; // indicating the next thing is the size
		static private final int RESTART         = 2; // indicating the next thing is the size
		static private final int WAIT_CONNECT    = 3; // waiting for connection
		static private final int WAIT_RECONNECT  = 4; // waiting for connection
		static private final int STARTED         = 5; // indicating there is a message
		static private final int SIZED           = 6; // indicating the next thing is a checksum
		static private final int CHECKED         = 7; // indicating the bytes are being read
		static private final int DELIVER         = 8; // indicating the message has been read
		static private final int EXCEPTION       = 9; // the run failed by some unhandled exception
		
		public String showState () {
			if (this.state == this.actual)
				return this.showState(this.state);
			else
				return this.showState(this.actual) + "->" + this.showState(this.actual);
		}
		private String showState (int state)
		{
			switch (state)
			{
			case SHUTDOWN:       return "SHUTDOWN";
			case START:          return "START";
			case RESTART:        return "RESTART";
			case WAIT_CONNECT:   return "WAIT_CONNECT";
			case WAIT_RECONNECT: return "WAIT_RECONNECT";
			case STARTED:        return "STARTED";
			case SIZED:          return "SIZED";
			case CHECKED:        return "CHECKED";
			case DELIVER:        return "DELIVER";
			case EXCEPTION:      return "EXCEPTION";
			default:
				return "Undefined State "+String.valueOf(state);									
			}
		}

		private ReceiverThread(TcpChannel parent, INetworkService.OnReceiveMessageHandler handler ) {
			logger.trace("Thread <{}>ReceiverThread::<constructor>", Thread.currentThread().getId());
			this.parent = parent;
			this.handler = handler;
			this.connector = parent.connectorThread;
		}
		
		public void updateConnector(ConnectorThread connector) {
			this.connector = connector;
		}

		@Override
		public void start() {
			super.start();
			logger.trace("Thread <{}>::start", Thread.currentThread().getId());
		}

		private void failInStream(InputStream is, long attempt) { 
			if (is == null) return;
			try {
				is.close();
			} catch (IOException e) {
				logger.warn("close failed {}", e.getLocalizedMessage());
			}
			this.connector.failure(attempt);
		}
		/**
		 * Initiate a connection to the server and then wait for a response.
		 * All responses are of the form:
		 * size     : int32
		 * checksum : int32
		 * bytes[]  : <size>
		 * This is done via a simple value machine.
		 * If the checksum doesn't match the connection is dropped and restarted.
		 * 
		 * Once the message has been read it is passed off to...
		 */
		@Override
		public void run() { 
			logger.trace("Thread <{}>ReceiverThread::run", Thread.currentThread().getId());
			//Looper.prepare();

			try {
				state = WAIT_CONNECT;

				int bytesToRead = 0; // indicates how many bytes should be read
				int bytesRead = 0;   // indicates how many bytes have been read
				long checksum = 0;

				byte[] message = null;
				byte[] byteToReadBuffer = new byte[Integer.SIZE/Byte.SIZE];
				byte[] checksumBuffer = new byte[Long.SIZE/Byte.SIZE];
				BufferedInputStream bis = null;
				long attempt = Long.MAX_VALUE;

				while (true) {
					switch (state) {
					case WAIT_RECONNECT: break;
					case RESTART: break;
					default:
						logger.debug("state: {}",this.showState());
					}

					this.actual = WAIT_CONNECT;
					
					switch (state) {
					case WAIT_RECONNECT: 
					case WAIT_CONNECT:  // look for the size
						
						synchronized (this.connector.state) {
							while (! this.connector.isConnected() ) {
								try {
									logger.trace("Thread <{}>ReceiverThread::value.wait", 
											Thread.currentThread().getId());

									this.connector.state.wait(BURP_TIME);
								} catch (InterruptedException ex) {
									logger.warn("thread interupted {}",ex.getLocalizedMessage());
									shutdown(bis); // looks like the thread is being shut down.
									return;
								}
							}
							attempt = this.connector.getAttempt();
						}

						try {
							InputStream insock = this.parent.socket.getInputStream();
							bis = new BufferedInputStream(insock, 1024);
						} catch (IOException ex) {
							logger.error("could not open input stream on socket {}", ex.getLocalizedMessage());
							failInStream(bis, attempt);
							break;
						}    
						if (bis == null) break;
						this.state = START;
						break;

					case RESTART:
					case START:
						try {
							int temp = bis.read(byteToReadBuffer);
							if (temp < 0) {
								logger.error("START: end of socket");
								failInStream(bis, attempt);
								this.state = WAIT_CONNECT;
								break; // read error - end of connection
							}
						} catch (SocketTimeoutException ex) {
							// the following checks the heart-stamp 
							// TODO no pace-maker messages are sent, this could be added if needed.
							long elapsedTime = System.currentTimeMillis() - this.connector.getHeartStamp();
							if (parent.flatLineTime < elapsedTime) {
								logger.warn("heart timeout : {}", elapsedTime);
								failInStream(bis, attempt);
								this.state = WAIT_RECONNECT;  // essentially the same as WAIT_CONNECT 
								break; 
							}
							this.state = RESTART;
							break;
						} catch (IOException ex) {
							logger.error("START: read error {}", ex.getLocalizedMessage());
							failInStream(bis, attempt);
							this.state = WAIT_CONNECT;
							break; // read error - set our value back to wait for connect
						}
						this.state = STARTED;
						break;

					case STARTED:  // look for the size
					{
						ByteBuffer bbuf = ByteBuffer.wrap(byteToReadBuffer);
						bbuf.order(this.parent.endian);
						bytesToRead = bbuf.getInt();

						if (bytesToRead < 0) break; // bad read keep trying
						if (bytesToRead > 100000) {
							logger.warn("message too large {} wrong size!!, we will be out of sync, disconnect ", bytesToRead);
							failInStream(bis, attempt);
							this.state = WAIT_CONNECT;
							break;
						}
						this.state = SIZED;
					}
					break;
					case SIZED: // look for the checksum
					{
						try {
							bis.read(checksumBuffer, 0, 4);
						} catch (SocketTimeoutException ex) {
							logger.trace("timeout on socket");
							continue;
						} catch (IOException e) {
							logger.trace("SIZED: read error");
							failInStream(bis, attempt);
							this.state = WAIT_CONNECT;
							break;
						}
						ByteBuffer bbuf = ByteBuffer.wrap(checksumBuffer);
						bbuf.order(this.parent.endian);
						checksum =  bbuf.getLong();

						message = new byte[bytesToRead];

						logger.info("checksum {} {}", checksumBuffer, checksum);
						bytesRead = 0;
						this.state = CHECKED;
					} 
					break;
					case CHECKED: // read the message
						while (bytesRead < bytesToRead) {
							try {
								int temp = bis.read(message, bytesRead, bytesToRead - bytesRead);
								bytesRead += (temp >= 0) ? temp : 0;
							} catch (SocketTimeoutException ex) {
								logger.trace("timeout on socket");
								continue;
							} catch (IOException ex) {
								logger.trace("CHECKED: read error");
								this.state = WAIT_CONNECT;
								failInStream(bis, attempt);
								break;
							}
						}
						if (bytesRead < bytesToRead) {
							failInStream(bis, attempt);
							this.state = WAIT_CONNECT;
							break;
						}
						this.state = DELIVER;
						break;
					case DELIVER: // deliver the message to the gateway
						this.handler.deliver(message, checksum);
						this.connector.resetHeartStamp();
						message = null;
						this.state = START;
						break;
					}
				}
			} catch (Exception ex) {
				logger.warn("interupted writing messages {}",ex.getLocalizedMessage());
				this.state = EXCEPTION;
				ex.printStackTrace();
			}
			logger.warn("sender thread exiting ...");
		}

		private void shutdown(BufferedInputStream bis) {
			logger.debug("no longer listening, thread closing");
			try { bis.close(); } catch (IOException e) {}
			return;
		}
	}

	// ********** UTILITY METHODS ****************

	/**
	 * A routine to get the local ip address
	 * TODO use this someplace
	 * 
	 * @return
	 */
	public String getLocalIpAddress() {
		logger.trace("Thread <{}>::getLocalIpAddress", Thread.currentThread().getId());
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) { 
						return inetAddress.getHostAddress().toString(); 
					}
				}
			}
		} catch (SocketException ex) {
			logger.error( ex.toString());
		}
		return null;
	}

}
