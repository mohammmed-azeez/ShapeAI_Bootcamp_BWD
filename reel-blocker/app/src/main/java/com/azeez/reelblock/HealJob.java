package com.azeez.reelblock;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic check that the blocker is still on. */
public class HealJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        SelfHeal.ensureRunning(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
