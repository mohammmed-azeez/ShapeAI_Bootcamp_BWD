package com.azeez.reelblock;

import android.content.Context;
import android.content.SharedPreferences;

/** When the blocker service last started and last saw any app, to tell if it is really running. */
final class Status {
    private static final String PREFS = "status";
    private static final String KEY_STARTED = "started";
    private static final String KEY_HEARTBEAT = "heartbeat";

    private Status() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void serviceStarted(Context c) {
        long now = System.currentTimeMillis();
        prefs(c).edit().putLong(KEY_STARTED, now).putLong(KEY_HEARTBEAT, now).apply();
    }

    static void heartbeat(Context c) {
        prefs(c).edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply();
    }

    static long startedAt(Context c) {
        return prefs(c).getLong(KEY_STARTED, 0);
    }

    static long lastHeartbeat(Context c) {
        return prefs(c).getLong(KEY_HEARTBEAT, 0);
    }
}
