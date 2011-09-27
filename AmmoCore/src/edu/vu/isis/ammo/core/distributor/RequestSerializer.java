package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;

/**
 * The purpose of these objects is lazily serialize an object.
 * Once it has been serialized once a copy is kept.
 *
 */
public class RequestSerializer {
	private static final Logger logger = LoggerFactory.getLogger("ammo:serializer");

	public interface OnReady  {
		public AmmoGatewayMessage run(Encoding encode, byte[] serialized);
	}
	public interface OnSerialize  {
		public byte[] run(Encoding encode);
	}

	public final Provider provider;
	public final Payload payload;
	private OnReady readyActor;
	private OnSerialize serializeActor;
	private AmmoGatewayMessage terse;
	private AmmoGatewayMessage json;

	private RequestSerializer(Provider provider, Payload payload) {
		this.provider = provider;
		this.payload = payload;
		this.terse = null;
		this.json = null;
		this.readyActor = new RequestSerializer.OnReady() {
			@Override
			public AmmoGatewayMessage run(Encoding encode, byte[] serialized) {
				logger.info("ready actor not defined {}", encode);
				return null;
			}
		};	
		this.serializeActor = new RequestSerializer.OnSerialize() {
			@Override
			public byte[] run(Encoding encode) {
				logger.info("serialize actor not defined {}", encode);
				return null;
			}
		};
	}

	public static RequestSerializer newInstance() {
		return new RequestSerializer(null, null);
	}
	public static RequestSerializer newInstance(Provider provider, Payload payload) {
		return new RequestSerializer(provider, payload);
	}

	public AmmoGatewayMessage act(Encoding encode) {
		switch (encode.getPayload()) {
		case JSON: 
			if (this.json != null) return this.json;
			final byte[] jsonBytes = this.serializeActor.run(encode);
			this.json = this.readyActor.run(encode, jsonBytes);
			return this.json;
		case TERSE: 
			if (this.terse == null) return this.terse;
			final byte[] terseBytes = this.serializeActor.run(encode);
			this.terse = this.readyActor.run(encode, terseBytes);
			return this.terse;
		}
		return null;
	}

	public void setAction(OnReady action) {
		this.readyActor = action;
	}

	public void setSerializer(OnSerialize onSerialize) {
		this.serializeActor = onSerialize;
	}


	/**
	 * @see deserializeToUri with which this method is symmetric.
	 */
	public static synchronized byte[] serializeFromProvider(final ContentResolver resolver, 
			final Uri tupleUri, final DistributorPolicy.Encoding encoding, final Logger logger) 
					throws FileNotFoundException, IOException {

		logger.trace("serializing using encoding {}", encoding);
		switch (encoding.getPayload()) {
		case JSON: 
		{
			logger.trace("Serialize the non-blob data");

			final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
			final Cursor tupleCursor;
			try {
				tupleCursor = resolver.query(serialUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (tupleCursor == null) return null;

			if (! tupleCursor.moveToFirst()) return null;
			if (tupleCursor.getColumnCount() < 1) return null;

			final byte[] tuple;
			final JSONObject json = new JSONObject();
			tupleCursor.moveToFirst();

			final List<String> fieldNameList = new ArrayList<String>();
			fieldNameList.add("_serial");
			for (final String name : tupleCursor.getColumnNames()) {
				final String value = tupleCursor.getString(tupleCursor.getColumnIndex(name));
				if (value == null || value.length() < 1) continue;
				try {
					json.put(name, value);
				} catch (JSONException ex) {
					logger.warn("invalid content provider {}", ex.getStackTrace());
				}
			}
			tuple = json.toString().getBytes();
			tupleCursor.close(); 

			logger.trace("Serialize the blob data (if any)");

			logger.trace("getting the names of the blob fields");
			final Uri blobUri = Uri.withAppendedPath(tupleUri, "_blob");
			final Cursor blobCursor;
			try {
				blobCursor = resolver.query(blobUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (blobCursor == null) return tuple;
			if (! blobCursor.moveToFirst()) return tuple;
			if (blobCursor.getColumnCount() < 1) return tuple;

			logger.trace("getting the blob fields");
			final int blobCount = blobCursor.getColumnCount();
			final List<String> blobFieldNameList = new ArrayList<String>(blobCount);
			final List<ByteArrayOutputStream> fieldBlobList = new ArrayList<ByteArrayOutputStream>(blobCount);
			final byte[] buffer = new byte[1024]; 
			for (int ix=0; ix < blobCursor.getColumnCount(); ix++) {
				final String fieldName = blobCursor.getColumnName(ix);
				logger.trace("processing blob {}", fieldName);
				blobFieldNameList.add(fieldName);

				final Uri fieldUri = Uri.withAppendedPath(tupleUri, blobCursor.getString(ix));
				try {
					final AssetFileDescriptor afd = resolver.openAssetFileDescriptor(fieldUri, "r");
					if (afd == null) {
						logger.warn("could not acquire file descriptor {}", serialUri);
						throw new IOException("could not acquire file descriptor "+fieldUri);
					}
					final ParcelFileDescriptor pfd = afd.getParcelFileDescriptor();

					final InputStream instream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
					final BufferedInputStream bis = new BufferedInputStream(instream);
					final ByteArrayOutputStream fieldBlob = new ByteArrayOutputStream();
					for (int bytesRead = 0; (bytesRead = bis.read(buffer)) != -1;) {
						fieldBlob.write(buffer, 0, bytesRead);
					}
					bis.close();
					fieldBlobList.add(fieldBlob);

				} catch (IOException ex) {
					logger.info("unable to create stream {} {}",serialUri, ex.getMessage());
					throw new FileNotFoundException("Unable to create stream");
				}
			}

			logger.trace("loading larger tuple buffer");
			final ByteArrayOutputStream bigTuple = new ByteArrayOutputStream();

			bigTuple.write(tuple); 
			bigTuple.write(0x0);

			for (int ix=0; ix < blobCount; ix++) {
				final String fieldName = blobFieldNameList.get(ix);
				bigTuple.write(fieldName.getBytes());
				bigTuple.write(0x0);

				final ByteArrayOutputStream fieldBlob = fieldBlobList.get(ix);
				final ByteBuffer bb = ByteBuffer.allocate(4);
				bb.order(ByteOrder.BIG_ENDIAN); 
				bb.putInt(fieldBlob.size());
				bigTuple.write(bb.array());
				bigTuple.write(fieldBlob.toByteArray());
			}
			blobCursor.close();
			final byte[] finalTuple = bigTuple.toByteArray();
			bigTuple.close();
			return finalTuple;
		}

		case TERSE: 
		{
			logger.error("terse serialization not implemented");
			return null;
		}
		// TODO custom still needs a lot of work
		// It will presume the presence of a SyncAdaptor for the content provider.
		case CUSTOM:
		default:
		{
			final Uri serialUri = Uri.withAppendedPath(tupleUri, encoding.getPayloadSuffix());
			final Cursor tupleCursor;
			try {
				tupleCursor = resolver.query(serialUri, null, null, null, null);
			} catch(IllegalArgumentException ex) {
				logger.warn("unknown content provider {}", ex.getLocalizedMessage());
				return null;
			}
			if (tupleCursor == null) return null;

			if (! tupleCursor.moveToFirst()) return null;
			if (tupleCursor.getColumnCount() < 1) return null;

			tupleCursor.moveToFirst();

			final String tupleString = tupleCursor.getString(0);
			return tupleString.getBytes();
		}
		}
	}


	/**
	 * The JSON serialization is in the following form...
	 * serialized tuple : A list of non-null bytes which serialize the tuple, 
	 *   this is provided/supplied to the ammo enabled content provider via insert/query.
	 *   The serialized tuple may be null terminated or the byte array may simply end.
	 * field blobs : A list of name:value pairs where name is the field name and value is 
	 *   the field's data blob associated with that field.
	 *   There may be more than one field blob.
	 *   
	 *   field name : A null terminated name, 
	 *   field data length : A 4 byte big-endian length, indicating the number of bytes in the data blob.
	 *   field data blob : A set of bytes whose size is that of the field data length
	 *   
	 * Note the deserializeToUri and serializeFromUri are symmetric, any change to one 
	 * will necessitate a corresponding change to the other.
	 */  
	public static synchronized Uri deserializeToProvider(final ContentResolver resolver, 
			Uri provider, Encoding encoding, byte[] data, Logger logger) {
		logger.debug("deserialize message");
		final Uri updateTuple = Uri.withAppendedPath(provider, "_deserial");
		final ByteBuffer dataBuff = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

		switch (encoding.getPayload()) {
		case JSON: 
		{
			int position = 0;
			for (; position < data.length || data[position] == (byte)0x0; position++) {
				if (position == (data.length-1)) { // last byte
					final int length = position+1;
					final byte[] payload = new byte[length];
					System.arraycopy(data, 0, payload, 0, length);
					try {
						final JSONObject input = (JSONObject) new JSONTokener(new String(payload)).nextValue();
						final ContentValues cv = new ContentValues();
						for (@SuppressWarnings("unchecked")
						final Iterator<String> iter = input.keys(); iter.hasNext();) {
							final String key = iter.next();
							cv.put(key, input.getString(key));
						}
						return resolver.insert(provider, cv);
					} catch (JSONException ex) {
						logger.warn("invalid JSON content {}", ex.getLocalizedMessage());
						return null;
					} catch (SQLiteException ex) {
						logger.warn("invalid sql insert {}", ex.getLocalizedMessage());
						return null;
					}
				}
			}
			final ContentValues cv = new ContentValues();
			final byte[] name = new byte[position];
			System.arraycopy(data, 0, name, 0, position);
			cv.put("data", new String(data));
			final Uri insertTuple = resolver.insert(updateTuple, cv);

			// process the blobs
			int start = position; 
			int length = 0;
			while (position < data.length) {
				if (data[position] != 0x0) { position++; length++; continue; }
				final String fieldName = new String(data, start, length);

				dataBuff.position(position);
				final int dataLength = dataBuff.getInt();
				start = dataBuff.position();
				final byte[] blob = new byte[dataLength];
				System.arraycopy(data, start, blob, 0, dataLength);
				final Uri fieldUri = Uri.withAppendedPath(updateTuple, fieldName);			
				try {
					final OutputStream outstream = resolver.openOutputStream(fieldUri);
					if (outstream == null) {
						logger.error( "could not open output stream to content provider: {} ",fieldUri);
						return null;
					}
					outstream.write(blob);
				} catch (FileNotFoundException ex) {
					logger.error( "blob file not found: {} {}",fieldUri, ex.getStackTrace());
				} catch (IOException ex) {
					logger.error( "error writing blob file: {} {}",fieldUri, ex.getStackTrace());
				}
			}	
			return insertTuple;
		}
		case TERSE: 
		{
			logger.error("terse deserialization not implemented");
			return null;
		}
		// TODO as with the serializer the CUSTOM section will presume for the
		// content provider the existence of a SyncAdaptor
		case CUSTOM:
		default:
		{
			// FIXME write to the custom provider address
			final Uri customProvider = encoding.extendProvider(provider);
			final ContentValues cv = new ContentValues();
			cv.put("data", data);
			return resolver.insert(customProvider, cv);
		}
		}
	}

}
