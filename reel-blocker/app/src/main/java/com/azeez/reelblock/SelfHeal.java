package com.azeez.reelblock;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the blocker back on if realme (or anyone) turns it off.
 * Needs WRITE_SECURE_SETTINGS, which only the one-time USB setup can grant.
 */
final class SelfHeal {
    private static final int JOB_ID = 1;
    private static final long STALE_MS = 30L * 60 * 1000;

    private SelfHeal() {}

    static boolean canHeal(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Runs every 15 minutes and after every restart. */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, HealJob.class))
                .setPeriodic(15L * 60 * 1000)
                .setPersisted(true)
                .build());
    }

    static void ensureRunning(Context c) {
        if (!canHeal(c)) return;
        // After a finished 24h unlock the user may turn it off. Respect that.
        if (Lock.isLocked(c) && !Lock.isProtecting(c) && Lock.setupRemainingMs(c) == 0) return;
        try {
            ContentResolver r = c.getContentResolver();
            ComponentName me = new ComponentName(c, BlockerService.class);
            List<String> others = new ArrayList<>();
            boolean listed = false;
            String cur = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null) {
                for (String s : cur.split(":")) {
                    if (s.isEmpty()) continue;
                    if (me.equals(ComponentName.unflattenFromString(s))) listed = true;
                    else others.add(s);
                }
            }
            boolean stale = System.currentTimeMillis() - Status.lastHeartbeat(c) > STALE_MS;
            if (listed && !stale) return;

            String without = String.join(":", others);
            others.add(me.flattenToString());
            String with = String.join(":", others);
            if (listed) {
                // Listed but silent: realme stopped it. Remove and add again to restart it.
                Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without);
            }
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, with);
            Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            DebugLog.add(c, listed ? "self-heal: restarted the service" : "self-heal: turned the service back on");
        } catch (RuntimeException e) {
            DebugLog.add(c, "self-heal error: " + e);
        }
    }
}
