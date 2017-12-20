package edu.northwestern.cbits.anthracite;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;

/**
 * Created by cjkarr on 12/19/2017.
 */

@RequiresApi(Build.VERSION_CODES.O)
public class LogJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Intent intent = new Intent(this, LogService.class);
        LogService.enqueueWork(this, LogService.class, LogService.JOB_ID, intent);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return true;
    }
}
