package edu.northwestern.cbits.anthracite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class Logger 
{
	private static final String EVENT_TYPE = "event_type";
	private static final String TIMESTAMP = "timestamp";
	private static final String LATITUDE = "latitude";
	private static final String LONGITUDE = "longitude";
	private static final String ALTITUDE = "altitude";
	private static final String TIME_DRIFT = "time_drift";
	
	private static final String CONTENT_OBJECT = "content_object";
	public static final String USER_ID = "user_id";
	private static final String STACKTRACE = "stacktrace";
	private static final String APP_VERSION = "version";
	private static final String APP_VERSION_CODE = "version_code";
	private static final String APP_PACKAGE = "package";
	private static final String JSON = "json";

	private static final String EXCEPTION_EVENT = "java_exception";

	private static final String LOGGER_URI = "edu.northwestern.cbits.anthracite.LOGGER_URI";

	public static final String LOGGER_ENABLED = "edu.northwestern.cbits.anthracite.LOGGER_ENABLED";
	private static final boolean LOGGER_ENABLED_DEFAULT = false;

	public static final String LOGGER_LOCATION_ENABLED = "edu.northwestern.cbits.anthracite.LOGGER_LOCATION_ENABLED";
	private static final boolean LOGGER_LOCATION_ENABLED_DEFAULT = false;

	private static final String INTERVAL = "edu.northwestern.cbits.anthracite.INTERVAL";
	private static final long DEFAULT_INTERVAL = 300;
	
	private static final boolean ONLY_WIFI_DEFAULT = true;
	private static final String ONLY_WIFI = "edu.northwestern.cbits.anthracite.ONLY_WIFI";
	
	private static final boolean LIBERAL_SSL_DEFAULT = false;
	private static final String LIBERAL_SSL = "edu.northwestern.cbits.anthracite.LIBERAL_SSL";
	
	private static final boolean DEBUG_DEFAULT = false;
	private static final String DEBUG = "edu.northwestern.cbits.anthracite.DEBUG";

	private static Logger _sharedInstance = null;
	
	private boolean _uploading = false;
	
	private Context _context = null;
	private long _lastUpload = System.currentTimeMillis();
	private String _userId;
	
	public Logger(Context context, String userId) 
	{
		this._context = context;
		
		try 
		{
			AlarmManager alarms = (AlarmManager) this._context.getSystemService(Context.ALARM_SERVICE);

			PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			
			Intent intent = new Intent(info.packageName + ".UPLOAD_LOGS_INTENT");
			PendingIntent pending = PendingIntent.getService(this._context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			
			alarms.setInexactRepeating(AlarmManager.RTC, 0, 60000, pending);
		} 
		catch (NameNotFoundException e) 
		{
			e.printStackTrace();
		}
		
		this._userId = userId;
	}

	public static Logger getInstance(Context context, String userId)
	{
		if (Logger._sharedInstance != null)
			return Logger._sharedInstance;
		
		if (context != null)
			Logger._sharedInstance = new Logger(context.getApplicationContext(), userId);
		
		return Logger._sharedInstance;
	}
	
	public boolean log(String event, Map<String, Object> payload)
	{
		long now = System.currentTimeMillis();

		if (payload == null)
			payload = new HashMap<String, Object>();
		
		try
		{
			PackageInfo info = this._context.getPackageManager().getPackageInfo(this._context.getPackageName(), 0);
			
			payload.put(Logger.APP_VERSION, info.versionName);
			payload.put(Logger.APP_VERSION_CODE, info.versionCode);
			payload.put(Logger.APP_PACKAGE, this._context.getPackageName());
		}
		catch (NameNotFoundException e)
		{
			Logger.getInstance(this._context, this._userId).logException(e);
		}
		
		payload.put("os_version", Build.VERSION.RELEASE);
		payload.put("os", "android");

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);
		
		if (prefs.getBoolean(Logger.LOGGER_ENABLED, Logger.LOGGER_ENABLED_DEFAULT))
		{
			String endpointUri = prefs.getString(Logger.LOGGER_URI, null);
			
			if (endpointUri != null)
			{
				if (prefs.getBoolean(Logger.LOGGER_LOCATION_ENABLED, Logger.LOGGER_LOCATION_ENABLED_DEFAULT))
				{
					LocationManager lm = (LocationManager) this._context.getSystemService(Context.LOCATION_SERVICE);
				
					Location lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
					
					Location backupLocation = null;
				
					if (lastLocation != null && now - lastLocation.getTime() > (1000 * 60 * 60))
					{
						backupLocation = lastLocation;
					
						lastLocation = null;
					}
					
					if (lastLocation == null)
						lastLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
					
					if (lastLocation == null)
						lastLocation = backupLocation;
					
					if (lastLocation != null)
					{
						payload.put(Logger.LATITUDE, lastLocation.getLatitude());
						payload.put(Logger.LONGITUDE, lastLocation.getLongitude());
						payload.put(Logger.ALTITUDE, lastLocation.getAltitude());
						payload.put(Logger.TIME_DRIFT, now - lastLocation.getTime());
					}
				}

				payload.put(Logger.EVENT_TYPE, event);
				payload.put(Logger.TIMESTAMP, now / 1000);
				
				if (payload.containsKey(Logger.USER_ID) == false)
					payload.put(Logger.USER_ID, this._userId);
				
				try 
				{
					ContentValues values = new ContentValues();
					values.put(LogContentProvider.APP_EVENT_RECORDED, System.currentTimeMillis());
					values.put(LogContentProvider.APP_EVENT_NAME, event);
	
					JSONObject jsonEvent = new JSONObject();
	
					for (String key : payload.keySet())
					{
						jsonEvent.put(key, payload.get(key));
					}

					jsonEvent.put(Logger.CONTENT_OBJECT, new JSONObject(jsonEvent.toString()));
					
					values.put(LogContentProvider.APP_EVENT_PAYLOAD, jsonEvent.toString());
					
					this._context.getContentResolver().insert(LogContentProvider.eventsUri(this._context), values);
					
					return true;
				} 
				catch (JSONException e) 
				{
					this.logException(e);
				} 
				catch (NameNotFoundException e) 
				{
					this.logException(e);
				}
			}
		}

		return false;
	}

	public void attemptUploads(boolean force) 
	{
		if (force)
			this._lastUpload = 0;
		
		if (this._uploading)
			return;
		
		long now = System.currentTimeMillis();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);
		
		long interval = prefs.getLong(Logger.INTERVAL, Logger.DEFAULT_INTERVAL);
		
		if (now - this._lastUpload < interval)
			return;

		this._lastUpload = now;
		
		boolean restrictWifi = true;
		
		restrictWifi = prefs.getBoolean(Logger.ONLY_WIFI, Logger.ONLY_WIFI_DEFAULT);
		
		if (restrictWifi && WiFiHelper.wifiAvailable(this._context) == false)
			return;

		this._uploading = true;

		String endpointUri = prefs.getString(Logger.LOGGER_URI, null);
		
		if (endpointUri != null)
		{
			try 
			{
				URI siteUri = new URI(endpointUri);

				SchemeRegistry registry = new SchemeRegistry();
				registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
				
				SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();

				if (prefs.getBoolean(Logger.LIBERAL_SSL, Logger.LIBERAL_SSL_DEFAULT))
				{
			        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			        trustStore.load(null, null);

			        socketFactory = new LiberalSSLSocketFactory(trustStore);								
				}

				registry.register(new Scheme("https", socketFactory, 443));

				String selection = LogContentProvider.APP_EVENT_TRANSMITTED + " = ?";
				String[] args = { "" + 0 };
				
				Cursor c = this._context.getContentResolver().query(LogContentProvider.eventsUri(this._context), null, selection, args, LogContentProvider.APP_EVENT_RECORDED);
				
				while (c.moveToNext())
				{
					try 
					{
						AndroidHttpClient androidClient = AndroidHttpClient.newInstance("Anthracite Event Logger", this._context);
						ThreadSafeClientConnManager mgr = new ThreadSafeClientConnManager(androidClient.getParams(), registry);

						HttpClient httpClient = new DefaultHttpClient(mgr, androidClient.getParams());
						androidClient.close();
						
						String payload = c.getString(c.getColumnIndex(LogContentProvider.APP_EVENT_PAYLOAD));
						
						HttpPost httpPost = new HttpPost(siteUri);
						
						List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
						nameValuePairs.add(new BasicNameValuePair(Logger.JSON, payload.toString()));
						HttpEntity entity = new UrlEncodedFormEntity(nameValuePairs, HTTP.US_ASCII);

						httpPost.setEntity(entity);

						httpClient.execute(httpPost);
						HttpResponse response = httpClient.execute(httpPost);

						HttpEntity httpEntity = response.getEntity();
						
						if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
							Log.e("LOG", "Log upload result: " + EntityUtils.toString(httpEntity));
						else
							EntityUtils.toString(httpEntity);
						
						mgr.shutdown();

						ContentValues values = new ContentValues();
						values.put(LogContentProvider.APP_EVENT_TRANSMITTED, System.currentTimeMillis());

						String updateWhere = LogContentProvider.APP_EVENT_ID + " = ?";
						String[] updateArgs = { "" + c.getLong(c.getColumnIndex(LogContentProvider.APP_EVENT_ID)) };

						this._context.getContentResolver().update(LogContentProvider.eventsUri(this._context), values, updateWhere, updateArgs);
					}
					catch (IOException e) 
					{
						e.printStackTrace();
					} 
					catch (NameNotFoundException e) 
					{
						e.printStackTrace();
					}
				}

				c.close();
				
				selection = LogContentProvider.APP_EVENT_TRANSMITTED + " != ?";
				
				this._context.getContentResolver().delete(LogContentProvider.eventsUri(this._context), selection, args);
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
		}
		
		this._uploading = false;
	}

	public void logException(Throwable e) 
	{
		e.printStackTrace();

		Map<String, Object> payload = new HashMap<String, Object>();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		
		e.printStackTrace(out);
		
		out.close();
		
		String stacktrace = baos.toString();
		
		payload.put(Logger.STACKTRACE, stacktrace);
		
		this.log(Logger.EXCEPTION_EVENT, payload);
	}
	
	private void setBoolean(String key, boolean value)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

		Editor e = prefs.edit();
		e.putBoolean(key, value);
		e.commit();
	}
	
	public void setEnabled(boolean enabled)
	{
		this.setBoolean(Logger.LOGGER_ENABLED, enabled);
	}

	public boolean getEnabled()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);
		
		return prefs.getBoolean(Logger.LOGGER_ENABLED, Logger.LOGGER_ENABLED_DEFAULT);
	}

	public void setIncludeLocation(boolean include)
	{
		this.setBoolean(Logger.LOGGER_LOCATION_ENABLED, include);
	}

	public void setWifiOnly(boolean wifiOnly)
	{
		this.setBoolean(Logger.ONLY_WIFI, wifiOnly);
	}

	public void setDebug(boolean debug)
	{
		this.setBoolean(Logger.DEBUG, debug);
	}

	public void setLiberalSsl(boolean liberal)
	{
		this.setBoolean(Logger.LIBERAL_SSL, liberal);
	}

	public void setUploadUri(Uri uri)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

		Editor e = prefs.edit();
		e.putString(Logger.LOGGER_URI, uri.toString());
		e.commit();
	}

	public Uri getUploadUri()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);
		
		if (prefs.contains(Logger.LOGGER_URI))
			return Uri.parse(prefs.getString(Logger.LOGGER_URI, null));
		
		return null;
	}

	public void setUploadInterval(long interval)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

		Editor e = prefs.edit();
		e.putLong(Logger.INTERVAL, interval);
		e.commit();
	}
}
