package edu.northwestern.cbits.anthracite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class LogContentProvider extends ContentProvider 
{
    private UriMatcher _matcher = new UriMatcher(UriMatcher.NO_MATCH);

	private static final int DATABASE_VERSION = 2;

	static final String APP_EVENTS_TABLE = "app_events";
	static final String APP_UPLOADS_TABLE = "app_uploads";

	public static final String APP_EVENT_RECORDED = "recorded";
	public static final String APP_EVENT_NAME = "name";
	public static final String APP_EVENT_PAYLOAD = "payload";
	public static final String APP_EVENT_TRANSMITTED = "transmitted";
	public static final String APP_EVENT_ID = "_id";

	public static final String APP_UPLOAD_URI = "uri";
	public static final String APP_UPLOAD_RECORDED = "recorded";
	public static final String APP_UPLOAD_TRANSMITTED = "transmitted";
	public static final String APP_UPLOAD_PAYLOAD = "payload";
	public static final String APP_UPLOAD_ID = "_id";

	private static final int APP_EVENTS = 0;
	private static final int APP_UPLOADS = 1;

    public LogContentProvider()
    {
    	super();
    }
    
    protected static Uri eventsUri(Context context) throws NameNotFoundException
    {
		PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		
		Uri u = Uri.parse("content://" + info.packageName + ".logging/" + APP_EVENTS_TABLE);
    	
    	return u;
    }
    
	public static Uri uploadsUri(Context context) throws NameNotFoundException
	{
		PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		
		Uri u = Uri.parse("content://" + info.packageName + ".logging/" + APP_UPLOADS_TABLE);
    	
    	return u;
	}

	private SQLiteDatabase _db = null;
	
	public boolean onCreate() 
	{
        final Context context = this.getContext().getApplicationContext();

		try 
		{
			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

			final String authority = info.packageName + ".logging";

	   		this._matcher.addURI(authority, APP_EVENTS_TABLE, APP_EVENTS);
	   		this._matcher.addURI(authority, APP_UPLOADS_TABLE, APP_UPLOADS);
		}
		catch (NameNotFoundException e) 
		{
			e.printStackTrace();
		}

        SQLiteOpenHelper helper = new SQLiteOpenHelper(context, "event_log.db", null, LogContentProvider.DATABASE_VERSION)
        {
            public void onCreate(SQLiteDatabase db) 
            {
            	db.execSQL(context.getString(R.string.db_create_events_log_table));

	            this.onUpgrade(db, 0, LogContentProvider.DATABASE_VERSION);
            }

            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
            {
            	switch (oldVersion)
            	{
	                case 0:
	
	                case 1:
	                	db.execSQL(context.getString(R.string.db_create_uploads_log_table));
	                default:
                        break;
            	}
            }
        };
        
        this._db = helper.getWritableDatabase();

        return true;
	}

	public int delete(Uri uri, String selection, String[] selectionArgs) 
	{
        switch(this._matcher.match(uri))
        {
	        case LogContentProvider.APP_EVENTS:
	        	return this._db.delete(LogContentProvider.APP_EVENTS_TABLE, selection, selectionArgs);
	        case LogContentProvider.APP_UPLOADS:
	        	return this._db.delete(LogContentProvider.APP_UPLOADS_TABLE, selection, selectionArgs);
        }
        
        return 0;
	}

	@Override
	public String getType(Uri uri) 
	{
       	return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) 
	{
		long id = 0;
		
        switch(this._matcher.match(uri))
        {
	        case LogContentProvider.APP_EVENTS:
	            id = this._db.insert(LogContentProvider.APP_EVENTS_TABLE, null, values);
	        case LogContentProvider.APP_UPLOADS:
	            id = this._db.insert(LogContentProvider.APP_UPLOADS_TABLE, null, values);
        }
	            
	    return Uri.withAppendedPath(uri, "" + id);
	}


	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) 
	{
        switch(this._matcher.match(uri))
        {
	        case LogContentProvider.APP_EVENTS:
	        	return this._db.query(LogContentProvider.APP_EVENTS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
	        case LogContentProvider.APP_UPLOADS:
	        	return this._db.query(LogContentProvider.APP_UPLOADS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
        }
        
        return null;
	}

	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) 
	{
        switch(this._matcher.match(uri))
        {
	        case LogContentProvider.APP_EVENTS:
	    		return this._db.update(LogContentProvider.APP_EVENTS_TABLE, values, selection, selectionArgs);
	        case LogContentProvider.APP_UPLOADS:
	    		return this._db.update(LogContentProvider.APP_UPLOADS_TABLE, values, selection, selectionArgs);
        }
        
        return 0;
	}
}
