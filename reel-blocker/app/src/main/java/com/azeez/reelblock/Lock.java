package com.azeez.reelblock;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lock state. While locked, ReelBlock blocks the Settings pages that could turn it off.
 * The lock turns on by itself after a short setup window. Unlocking needs a 24 hour wait.
 */
final class Lock {
    static final long SETUP_WINDOW_MS = 10L * 60 * 1000;
    static final long COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private static final String PREFS = "lock";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_STARTS_AT = "starts_at";
    private static final String KEY_REQUESTED_AT = "requested_at";

    private Lock() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Called when the service first starts. Locks after the setup window, only once ever. */
    static void autoLockOnFirstRun(Context c) {
        if (prefs(c).contains(KEY_LOCKED)) return;
        prefs(c).edit()
                .putBoolean(KEY_LOCKED, true)
                .putLong(KEY_STARTS_AT, System.currentTimeMillis() + SETUP_WINDOW_MS)
                .putLong(KEY_REQUESTED_AT, 0)
                .apply();
    }

    static boolean isLocked(Context c) {
        return prefs(c).getBoolean(KEY_LOCKED, false);
    }

    static long requestedAt(Context c) {
        return prefs(c).getLong(KEY_REQUESTED_AT, 0);
    }

    /** Time left before the lock turns on by itself. 0 if it is already on. */
    static long setupRemainingMs(Context c) {
        if (!isLocked(c)) return 0;
        return Math.max(0, prefs(c).getLong(KEY_STARTS_AT, 0) - System.currentTimeMillis());
    }

    /** True while Settings pages for ReelBlock must be blocked. */
    static boolean isProtecting(Context c) {
        if (!isLocked(c) || setupRemainingMs(c) > 0) return false;
        long at = requestedAt(c);
        return at == 0 || System.currentTimeMillis() < at + COOLDOWN_MS;
    }

    /** True while an unlock was requested and the 24 hour wait is still running. */
    static boolean isCoolingDown(Context c) {
        return requestedAt(c) != 0 && isProtecting(c);
    }

    static long remainingMs(Context c) {
        long at = requestedAt(c);
        if (at == 0) return COOLDOWN_MS;
        return Math.max(0, at + COOLDOWN_MS - System.currentTimeMillis());
    }

    /** Lock right now, with no setup window. */
    static void lock(Context c) {
        prefs(c).edit()
                .putBoolean(KEY_LOCKED, true)
                .putLong(KEY_STARTS_AT, 0)
                .putLong(KEY_REQUESTED_AT, 0)
                .apply();
    }

    static void requestUnlock(Context c) {
        if (requestedAt(c) == 0) {
            prefs(c).edit().putLong(KEY_REQUESTED_AT, System.currentTimeMillis()).apply();
        }
    }
}
