package edu.northwestern.cbits.anthracite;

import android.app.IntentService;
import android.content.Intent;

public class LogService extends IntentService {
    public static final String LOG_FORCE_UPLOAD = "edu.northwestern.cbits.anthracite.LOG_FORCE_UPLOAD";

    public LogService() {
        super("Anthracite");
    }

    public LogService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final LogService me = this;

        final boolean force = intent.getBooleanExtra(
                LogService.LOG_FORCE_UPLOAD, false);

        Logger.getInstance(me, null).attemptUploads(force);
    }
}
