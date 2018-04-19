/* Copyright © 2018 by Northwestern University. All Rights Reserved. */

package edu.northwestern.cbits.anthracite;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

public class LogService extends JobIntentService
{
    public static final String LOG_FORCE_UPLOAD = "edu.northwestern.cbits.anthracite.LOG_FORCE_UPLOAD";

    public static final int JOB_ID = 0;
    public static final int PERIODIC_JOB_ID = 1;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        final LogService me = this;

        final boolean force = intent.getBooleanExtra(LogService.LOG_FORCE_UPLOAD, false);

        Logger.getInstance(me, null).attemptUploads(force);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            JobScheduler jobScheduler = (JobScheduler) this.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (jobScheduler.getPendingJob(LogService.PERIODIC_JOB_ID) == null) {
                ComponentName component = new ComponentName(this, LogJobService.class);

                JobInfo.Builder builder = new JobInfo.Builder(LogService.PERIODIC_JOB_ID, component)
                        .setPeriodic(15 * 60 * 1000);

                jobScheduler.schedule(builder.build());
            }
        }
    }
}
