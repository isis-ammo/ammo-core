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

package edu.vu.isis.ammo.core.distributor.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.provider.PresenceSchema;

/**
 * CursorAdapter used by AmmoCore to display its tables in a human-readable
 * format. Each row of the table view is formatted a certain way based on the
 * disposition of the corresponding row in the content provider's table.
 */
public class PresenceTableViewAdapter extends DistributorTableViewAdapter
{
    public static final Logger logger = LoggerFactory.getLogger("ui.dist.presence");

    public PresenceTableViewAdapter(Context context, int layout, Cursor cursor)
    {
        super(context, layout, cursor);
    }

    // ===========================================================
    // UI Management
    // ===========================================================
    // Override this to set disposition field.

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // super.bindView(view, context, cursor);
        {
            final TextView tv = (TextView) view.findViewById(R.id.dist_presence_view_item_first);
            final long first = cursor.getLong(cursor.getColumnIndex(PresenceSchema.FIRST.field));
            tv.setText(SDF.format(first));
        }
        {
            final TextView tv = (TextView) view.findViewById(R.id.dist_presence_view_item_latest);
            final long latest = cursor.getLong(cursor.getColumnIndex(PresenceSchema.LATEST.field));
            tv.setText(SDF.format(latest));
        }
        {
            final TextView tv = (TextView) view.findViewById(R.id.dist_presence_view_item_device);
            tv.setText(cursor.getString(cursor.getColumnIndex(PresenceSchema.ORIGIN.field)));
        }
        {
            final TextView tv = (TextView) view.findViewById(R.id.dist_presence_view_item_operator);
            tv.setText(cursor.getString(cursor.getColumnIndex(PresenceSchema.OPERATOR.field)));
        }
        {
            final TextView tv = (TextView) view.findViewById(R.id.dist_presence_view_item_state);
            tv.setText(cursor.getString(cursor.getColumnIndex(PresenceSchema.STATE.field)));
        }
    }

}
