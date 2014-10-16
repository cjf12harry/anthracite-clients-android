package edu.northwestern.cbits.anthracite;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class LogService extends IntentService 
{
	public static final String LOG_FORCE_UPLOAD = "edu.northwestern.cbits.anthracite.LOG_FORCE_UPLOAD";

	public LogService()
	{
		super("Anthracite");
	}

	public LogService(String name) 
	{
		super(name);
	}

	protected void onHandleIntent(Intent intent) 
	{
		final LogService me = this;
		
		final boolean force = intent.getBooleanExtra(LogService.LOG_FORCE_UPLOAD, false);

        Log.e("AN", "UPLOAD INTENT: " + force);
		
		Runnable r = new Runnable()
		{
			public void run() 
			{
				Logger.getInstance(me, null).attemptUploads(force);
			}
		};
		
		try
		{
			Thread t = new Thread(r);
			t.start();
		}
		catch (OutOfMemoryError e)
		{
			System.gc();
		}
	}
}
