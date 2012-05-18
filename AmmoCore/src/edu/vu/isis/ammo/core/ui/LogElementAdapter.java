package edu.vu.isis.ammo.core.ui;

import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class LogElementAdapter extends ArrayAdapter<LogElement> {

	
	private Context mContext;
	private int maxNumLines = 0; // 0 means unlimited lines
	
	
	public LogElementAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
		this.mContext = context;
	}
	
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		View view = super.getView(position, convertView, parent);
		
		final TextView tv = (TextView) view;
		final LogElement element = super.getItem(position);
		final LogLevel level = element.getLogLevel();

		tv.setText(element.getMessage());
		tv.setTextColor(level.getColor(mContext));
		return view;
		
	}
	
	
	public void addAll(List<LogElement> elemList) {
		synchronized(elemList) {
			
			for(LogElement e : elemList) {
				
				if(this.maxNumLines != 0 && this.maxNumLines < this.getCount()) {
					// Remove the first item in the list if we have exceeded the
					// max number of lines allowed
					super.remove(super.getItem(0));
				}
				
				super.add(e);
				
			}
			
		}
	}
	
	
	public void setMaxLines(int newMax) {
		this.maxNumLines = newMax;
	}
	
	
	
}
