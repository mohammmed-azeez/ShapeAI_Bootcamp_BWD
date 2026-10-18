package com.azeez.reelblock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayDeque;

/**
 * Doomscroll rule: up to MAX_PER_WINDOW shorts in any WINDOW_MS is fine.
 * One more and Shorts/Reels are blocked for BLOCK_MS. New or repeated, every short counts.
 * The values are fixed in code on purpose, so they cannot be loosened in a weak moment.
 */
final class Limiter {
    static final int MAX_PER_WINDOW = 3;
    static final long WINDOW_MS = 60L * 1000;
    static final long BLOCK_MS = 15L * 60 * 1000;

    private static final String PREFS = "limiter";
    private static final String KEY_BLOCKED_UNTIL = "blocked_until";

    private final SharedPreferences prefs;
    private final ArrayDeque<Long> views = new ArrayDeque<>();

    Limiter(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    long blockedRemainingMs() {
        return Math.max(0, prefs.getLong(KEY_BLOCKED_UNTIL, 0) - System.currentTimeMillis());
    }

    boolean isBlocked() {
        return blockedRemainingMs() > 0;
    }

    /** Records one short. Returns how many were watched in the last minute, including this one. */
    int recordView() {
        long now = System.currentTimeMillis();
        while (!views.isEmpty() && now - views.peekFirst() >= WINDOW_MS) {
            views.pollFirst();
        }
        views.addLast(now);
        int count = views.size();
        if (count > MAX_PER_WINDOW) {
            prefs.edit().putLong(KEY_BLOCKED_UNTIL, now + BLOCK_MS).apply();
            views.clear();
        }
        return count;
    }
}
