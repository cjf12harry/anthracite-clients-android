package edu.northwestern.cbits.anthracite;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class LogContentProvider extends ContentProvider 
{
	private static final int DATABASE_VERSION = 1;

	static final String APP_EVENTS_TABLE = "app_events";

	public static final String APP_EVENT_RECORDED = "recorded";
	public static final String APP_EVENT_NAME = "name";
	public static final String APP_EVENT_PAYLOAD = "payload";
	public static final String APP_EVENT_TRANSMITTED = "transmitted";
	public static final String APP_EVENT_ID = "_id";

    public LogContentProvider()
    {
    	super();
    }
    
    protected static Uri eventsUri(String authority)
    {
    	Uri u = Uri.parse("content://" + authority + "/" + APP_EVENTS_TABLE);
    	
    	return u;
    }
    
	private SQLiteDatabase _db = null;
	
	public boolean onCreate() 
	{
        final Context context = this.getContext().getApplicationContext();
        
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

	                default:
                        break;
            	}
            }
        };
        
        this._db   = helper.getWritableDatabase();

        return true;
	}

	public int delete(Uri uri, String selection, String[] selectionArgs) 
	{
        return this._db.delete(LogContentProvider.APP_EVENTS_TABLE, selection, selectionArgs);
	}

	@Override
	public String getType(Uri uri) 
	{
       	return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) 
	{
		long id = this._db.insert(LogContentProvider.APP_EVENTS_TABLE, null, values);
	            
	    return Uri.withAppendedPath(uri, "" + id);
	}


	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) 
	{
		return this._db.query(LogContentProvider.APP_EVENTS_TABLE, projection, selection, selectionArgs, null, null, sortOrder);
	}

	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) 
	{
		return this._db.update(LogContentProvider.APP_EVENTS_TABLE, values, selection, selectionArgs);
	}
}
