package edu.northwestern.cbits.anthracite;

import android.app.IntentService;
import android.content.Intent;

public class LogService extends IntentService 
{
	private static final String LOG_FORCE_UPLOAD = "edu.northwestern.cbits.anthracite.LOG_FORCE_UPLOAD";

	public LogService(String name) 
	{
		super(name);
	}

	protected void onHandleIntent(Intent intent) 
	{
		final LogService me = this;
		
		final boolean force = intent.getBooleanExtra(LogService.LOG_FORCE_UPLOAD, false);
		
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
