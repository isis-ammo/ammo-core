/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.logger.ui;

import java.io.FileNotFoundException;
import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.TextView;
import android.widget.Toast;
import ch.qos.logback.classic.Level;
import edu.vu.isis.ammo.core.R;

public class FileLogViewer extends LogViewerBase {

	private FileLogReader mLogReader;

	private int numEntriesToSave;

	public final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			mAdapter.remove(mAdapter.getItem(0));
			mAdapter.notifyDataSetChanged();
		}

	};

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		final Display display = getWindowManager().getDefaultDisplay();
		final int width = display.getWidth();
		final int height = display.getHeight();
		final int largestDimension = Math.max(width, height);

		final LayoutInflater inflater = getLayoutInflater();
		final View row = inflater.inflate(R.layout.log_display_row, null);
		final TextView tv = (TextView) row.findViewById(R.id.log_display_row);

		final float textSize = tv.getTextSize();
		final int linesOnScreen = (int) (largestDimension / textSize);
		numEntriesToSave = linesOnScreen / 3;
		final int numLines = numEntriesToSave + linesOnScreen;

		String filepath = processIntent();
		if (filepath == null) {
			logger.error("Received intent without String extra for filepath");
			Toast.makeText(this, "Received invalid Intent", Toast.LENGTH_LONG);
			return;
		}

		try {
			super.mLogReader = this.mLogReader = new FileLogReader(this,
					mHandler, filepath, numLines);
		} catch (FileNotFoundException e) {
			String errorMsg = "Could not find file: " + filepath;
			Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
			logger.error(errorMsg);
			e.printStackTrace();
			return;
		} catch (IOException e) {
			String errorMsg = "Error reading from file: " + filepath;
			Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
			logger.error(errorMsg);
			e.printStackTrace();
			return;
		}

		mAdapter = new LogElementAdapter(this, R.layout.log_display_row);
		mListView.setAdapter(mAdapter);
		mAdapter.addAll(mLogReader.fillDown());

		// TODO: Take out this level setting
		((ch.qos.logback.classic.Logger) logger).setLevel(Level.DEBUG);
		logger.debug("Adapter count: {}  Spread limit: {}",
				mAdapter.getCount(), mLogReader.getSpreadLimit());
		assert (mAdapter.getCount() == mLogReader.getSpreadLimit());

		mListView.setOnScrollListener(new FileOnScrollListener());

	}

	private String processIntent() {
		Object obj = getIntent().getExtras().get(EXTRA_NAME);
		if (obj instanceof String) {
			return (String) obj;
		} else {
			return null;
		}
	}

	private void loadDown(int firstVisiblePosition, int lastVisiblePosition) {

		// If we were already at the end of the file, notify the user, then
		// get out
		if (mLogReader.atEndOfFile()) {
			Toast.makeText(this, "End of file", Toast.LENGTH_SHORT).show();
			return;
		}

		final Object survivor = mListView.getItemAtPosition(firstVisiblePosition
				- numEntriesToSave);
		final LogElement firstVisible = (LogElement) mListView
				.getItemAtPosition(firstVisiblePosition);
		Object firstInList = mListView.getItemAtPosition(0);
		LogElement nextElement = mLogReader.scrollDown();

		// We loop until we reach the line we want to keep or until we reach
		// the end of the file.  
		while ((firstInList != survivor)
				&& (nextElement != FileLogReader.END_OF_FILE)) {
			mAdapter.remove((LogElement) firstInList);
			mAdapter.add(nextElement);
			firstInList = mListView.getItemAtPosition(0);
			nextElement = mLogReader.scrollDown();
		}

		logger.debug("Adapter count: {}  Spread limit: {}",
				mAdapter.getCount(), mLogReader.getSpreadLimit());

		assert (mAdapter.getCount() == mLogReader.getSpreadLimit());

		mListView.setSelection(mAdapter.getPosition(firstVisible));

	}

	private void loadUp(int firstVisiblePosition, int lastVisiblePosition) {

		// If we were already at the beginning of the file, notify the user,
		// then get out
		if (mLogReader.atBegOfFile()) {
			Toast.makeText(this, "Beginning of file", Toast.LENGTH_SHORT)
					.show();
			return;
		}

		final Object survivor = mListView.getItemAtPosition(lastVisiblePosition
				+ numEntriesToSave);
		final LogElement firstVisible = (LogElement) mListView
				.getItemAtPosition(firstVisiblePosition);
		Object lastInList = mListView
				.getItemAtPosition(mAdapter.getCount() - 1);
		LogElement nextElement = mLogReader.scrollUp();

		// We loop until we reach the line we want to keep or until we reach
		// the beginning of the file.
		while ((lastInList != survivor)
				&& (nextElement != FileLogReader.BEG_OF_FILE)) {
			mAdapter.remove((LogElement) lastInList);
			mAdapter.insert(nextElement, 0);
			lastInList = mListView.getItemAtPosition(mAdapter.getCount() - 1);
			nextElement = mLogReader.scrollUp();
		}

		logger.debug("Adapter count: {}  Spread limit: {}",
				mAdapter.getCount(), mLogReader.getSpreadLimit());
		assert (mAdapter.getCount() == mLogReader.getSpreadLimit());

		mListView.setSelection(mAdapter.getPosition(firstVisible));

	}

	protected class FileOnScrollListener extends
			LogViewerBase.MyOnScrollListener {

		private FileLogViewer parent = FileLogViewer.this;

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			super.onScrollStateChanged(view, scrollState);

			final int firstVisiblePosition = parent.mListView
					.getFirstVisiblePosition();
			final int totalItemCount = parent.mAdapter.getCount();
			final int lastVisiblePosition = parent.mListView
					.getLastVisiblePosition();

			final boolean atEndOfList = (lastVisiblePosition + 1 == totalItemCount);
			final boolean atBeginningOfList = (firstVisiblePosition == 0);

			if (atEndOfList) {
				parent.loadDown(firstVisiblePosition, lastVisiblePosition);
			} else if (atBeginningOfList) {
				parent.loadUp(firstVisiblePosition, lastVisiblePosition);
			}
		}

	}

}
