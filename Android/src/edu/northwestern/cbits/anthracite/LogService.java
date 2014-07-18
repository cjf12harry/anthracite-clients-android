package edu.northwestern.cbits.anthracite;

import android.app.IntentService;
import android.content.Intent;

public class LogService extends IntentService 
{
	public static final String UPLOAD_LOGS_INTENT = "edu.northwestern.cbits.anthracite.UPLOAD_LOGS_INTENT";

	public LogService(String name) 
	{
		super(name);
	}

	protected void onHandleIntent(Intent intent) 
	{
		// TODO Auto-generated method stub

	}
}
