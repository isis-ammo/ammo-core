package edu.vu.isis.ammo.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Environment;

/**
 * Helps cut down on the time to allocate byte buffers
 * and reduces the amount of GC the vm has to do when
 * passing message buffers within the system.
 * 
 * @author mriley
 */
public final class ByteBufferPool {

	private static final Logger logger = LoggerFactory.getLogger("util.bufferpool");
	private static final int MIN_BUFFER_SIZE = 100*1024;
	private static final int MAX_POOL_SIZE = 100;
	private static final int MIN_POOL_SIZE = 20;
	private static final int MAX_NODE_AGE = 60 * 1000 * 10;	
	private static final File MEMCACHE_DIR;	
	
	static {
		if( System.getProperty("android.vm.dexfile") != null ) {
			File externalStorageDirectory = Environment.getExternalStorageDirectory();
			MEMCACHE_DIR = new File(externalStorageDirectory,"support/edu.vu.isis.ammo.core/mem/");
		} else {
			MEMCACHE_DIR = new File(System.getProperty("java.io.tmpdir"),"support/edu.vu.isis.ammo.core/mem/");
		}
		if( !MEMCACHE_DIR.exists() && !MEMCACHE_DIR.mkdirs() ) {
			throw new RuntimeException("Failed to create cache dir " + MEMCACHE_DIR);
		}
	}
	
	private static final ByteBufferPool instance = new ByteBufferPool();

	public static ByteBufferPool getInstance() {
		return instance;
	}

	private final AtomicLong count = new AtomicLong();
	private final File rootDir;
	private int hitCount;
	private int missCount;
	Node head;

	public ByteBufferPool() {
		clean(MEMCACHE_DIR);
		File sessionDir = new File(MEMCACHE_DIR, UUID.randomUUID().toString());
		if( !sessionDir.exists() && !sessionDir.mkdirs() ) {
			throw new RuntimeException("Failed to create session dir " + sessionDir);
		}
		rootDir = sessionDir;
	}
	
	public int getMinBufferSize() {
		return MIN_BUFFER_SIZE;
	}
	
	public int getHitCount() {
		return hitCount;
	}
	
	public int getMissCount() {
		return missCount;
	}
	
	public int getNodeCount() {
		return Node.nodeCount;
	}
	
	public int getByteSize() {
		return Node.poolSize;
	}
	
	public void dispose() {
		if( head != null ) {
			head.dispose(this);
		}
		head = null;
		hitCount = 0;
		missCount = 0;
	}

	public synchronized ByteBufferAdapter allocate( int size ) {
		if( head != null ) {
			ByteBufferAdapter reuse = head.obtain(size, this);
			if( reuse != null ) {
				hitCount++;
				return reuse;
			}
		}
		missCount++;
		File file = new File(rootDir, count.incrementAndGet() + ".mem");
		int cap = (size / MIN_BUFFER_SIZE + 1) * MIN_BUFFER_SIZE;
		try {
			MMapByteBufferAdapter adapter = 
					new MMapByteBufferAdapter(new RandomAccessFile(file, "rw"), file, cap);
			//			ByteBufferAdapter adapter = new NioByteBufferAdapter(ByteBuffer.allocateDirect(cap));
			//			ByteBufferAdapter adapter = new NioByteBufferAdapter(ByteBuffer.allocate(cap));
			adapter.position(0);
			adapter.limit(size);
			return adapter;
		} catch (Exception e) {
			throw new RuntimeException("Failed to allocate " + file + " of size " + size, e);			
		}
	}

	public synchronized boolean release( ByteBufferAdapter buffer ) {
		if( buffer.isPoolable() ) {
			Node.release(buffer, this);
			return true;
		}
		return false;
	}

	public void dumpStats() {
		logger.error("BufferPool: hits=" + hitCount + ", misses=" + missCount 
				+ ", nodes_total=" + Node.nodeCount
				+ ", size=" + Node.poolSize);
	}

	private static final class Node {
		static int nodeCount;
		static int poolSize;

		private long useTime = System.currentTimeMillis();
		private Node next;
		private Node previous;
		private ByteBufferAdapter byteBuffer;

		public Node(ByteBufferAdapter buffer) {
			nodeCount++;
			poolSize += buffer.capacity(); 
			byteBuffer = buffer;
		}

		/**
		 * Obtain a byte buffer large enough to hold data of the given
		 * size and limit the buffer to the given size.
		 * 
		 * @param size
		 * @return
		 */
		public ByteBufferAdapter obtain(int size, ByteBufferPool pool ) {
			if( size <= byteBuffer.capacity() ) {
				unlink(pool);
				byteBuffer.clear();
				byteBuffer.limit(size);
				return byteBuffer;
			} else if( next != null ) {
				return next.obtain(size, pool);
			}
			return null;
		}

		/**
		 * Replace this buffer with the given buffer if the given buffer 
		 * is larger (i.e. more reusable) than this buffer.  Otherwise,
		 * pass the call to the next node.  
		 * 
		 * @param buffer The new buffer
		 * @return The replaced buffer or the passed buffer if no buffer was replaced
		 */
		public ByteBufferAdapter replace( ByteBufferAdapter buffer ) {
			if( buffer.capacity() > byteBuffer.capacity() ) {
				ByteBufferAdapter old = byteBuffer;
				poolSize -= byteBuffer.capacity();
				byteBuffer = buffer;
				useTime = System.currentTimeMillis();
				poolSize += byteBuffer.capacity();
				return old;
			} else if( next != null ) {
				return next.replace(buffer);
			}
			return buffer;
		}

		/**
		 * Return tre if the adapter is already in the pool.  This helps us ensure
		 * that we can't double free a buffer.
		 * 
		 * @param adapter
		 * @return
		 */
		public boolean isInPool( ByteBufferAdapter adapter ) {
			return adapter == byteBuffer || (next != null && next.isInPool(adapter));
		}

		/**
		 * Releases the buffer back into the pool and returns the
		 * new head.  If there is no more room in the pool, attempts to
		 * replace a node with a smaller capacity (a node with a 
		 * smaller capacity may be non-reusable).
		 * 
		 * @param buffer
		 * @param head
		 * @return
		 */
		public static void release( ByteBufferAdapter buffer, ByteBufferPool pool ) {
			if( pool.head == null || !pool.head.isInPool(buffer) ) {
				if( nodeCount < MAX_POOL_SIZE ) {
					Node next = new Node(buffer);
					next.next = pool.head;
					if( pool.head != null ) {
						pool.head.previous = next;
					}
					pool.head = next;
					pool.head.reap(System.currentTimeMillis(), pool);
				} else {
					pool.head.reap(System.currentTimeMillis(), pool);
					free(buffer, pool);
				}
			}
		}

		/**
		 * Replace nodes until we find the smallest then free the
		 * smallest.  This allows us to keep around the largest nodes.
		 * 
		 * @param buffer
		 * @param pool
		 */
		static void free( ByteBufferAdapter buffer, ByteBufferPool pool) {
			ByteBufferAdapter replaced = null;
			for( ;; ) {
				replaced = pool.head.replace(buffer);
				if( replaced == buffer ) {
					// if the buffer didn't replace another buffer than
					// its smaller than any buffer in the pool.  This 
					// means we're done so free it and return.
					buffer.free();
					return;
				} else {
					// the buffer replaced a smaller buffer.  Loop back
					// around with the smaller buffer to see if there are
					// any others even smaller
					buffer = replaced;
				}
			}
		}

		/**
		 * Unlinks a node from the pool.  We could just mark the node
		 * as "in use" but this may cause some trouble if a node is
		 * used but never released.  This way, we keep no reference to
		 * buffers in use and will never leak memory.
		 */
		void unlink( ByteBufferPool pool ) {
			nodeCount--;
			poolSize -= byteBuffer.capacity();

			if( previous != null ) {
				previous.next = next;
			}
			if( next != null ) {
				next.previous = previous;
			}
			if( pool.head == this ) {
				pool.head = next;
			}
		}

		/**
		 * Delete nodes we no longer need
		 */
		void reap( long now, ByteBufferPool pool ) {
			if( next != null ) {
				next.reap(now, pool);
			}
			if( nodeCount > MIN_POOL_SIZE ) {
				if( this != pool.head ) {
					if( now - useTime > MAX_NODE_AGE ) {
						unlink(pool);
						free(byteBuffer, pool);
					}
				}
			}
		}
		
		/**
		 * Delete all nodes
		 */
		void dispose( ByteBufferPool pool ) {
			if( next != null ) {
				next.dispose( pool);
			}
			unlink(pool);
			byteBuffer.free();
		}
	}

	static void clean( File dir ) {
		if( dir.isDirectory() ) {
			File[] children = dir.listFiles();
			if( children != null ) {
				for( File c : children ) {
					clean(c);
				}
			}
		}
		dir.delete();
	}
}
