package com.azeez.reelblock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart or an update, make sure the blocker is on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SelfHeal.ensureRunning(context);
        SelfHeal.schedule(context);
    }
}
