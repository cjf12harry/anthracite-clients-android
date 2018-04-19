/* Copyright © 2018 by Northwestern University. All Rights Reserved. */

package edu.northwestern.cbits.anthracite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
    private static final String APP_DEVICE_MANUFACTURER = "manufacturer";
    private static final String APP_DEVICE_MODEL = "model";
    
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

    private static final boolean HEARTBEAT_DEFAULT = false;
    private static final String HEARTBEAT = "edu.northwestern.cbits.anthracite.HEARTBEAT";

    private static final String RAILS_MODE = "edu.northwestern.cbits.anthracite.RAILS_MODE";
    private static final boolean RAILS_MODE_DEFAULT = false;

    private static final boolean ONLY_CHARGING_DEFAULT = false;
    private static final String ONLY_CHARGING = "edu.northwestern.cbits.anthracite.ONLY_CHARGING";

    private static final boolean LOG_CONNECTION_ERRORS_DEFAULT = false;
    private static final String LOG_CONNECTION_ERRORS = "edu.northwestern.cbits.anthracite.LOG_CONNECTION_ERRORS";

    private static final String LOGGER_USER_AGENT = "edu.northwestern.cbits.anthracite.LOGGER_USER_AGENT";
    private static final String LOGGER_USER_AGENT_DEFAULT = "Anthracite Event Logger";

    private static Logger _sharedInstance = null;

    private boolean _uploading = false;

    private Context _context = null;
    private long _lastUpload = 0;
    private String _userId;

    public Logger(Context context, String userId)
    {
        this._context = context;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Intent intent = new Intent(context, LogService.class);
                LogService.enqueueWork(context, LogService.class, LogService.JOB_ID, intent);
            } else {
                try
                {
                    AlarmManager alarms = (AlarmManager) this._context.getSystemService(Context.ALARM_SERVICE);

                    PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

                    Intent intent = new Intent(info.packageName + ".UPLOAD_LOGS_INTENT");
                    intent.setClassName(context, LogService.class.getCanonicalName());

                    PendingIntent pending = PendingIntent.getService(this._context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

                    // Setting all reminders for alls using Anthracite client to run on the same 5 minute
                    // intervals to save battery.

                    long next = System.currentTimeMillis() + 300000;

                    next = next - (next % 300000);

                    alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, 300000, pending);
                }
                catch (NameNotFoundException e)
                {
                    e.printStackTrace();
                }
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

    public void updateUserId(String userId) {
        this._userId = userId;
    }

    @SuppressLint({"BadHostnameVerifier", "TrustAllX509TrustManager", "MissingPermission"})
    public boolean log(String event, Map<String, Object> payload)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

        if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
            Log.e("LOG", "Log event: " + event);

        long now = System.currentTimeMillis();

        if (payload == null)
            payload = new HashMap<>();

        try
        {
            PackageInfo info = this._context.getPackageManager().getPackageInfo(this._context.getPackageName(), 0);

            payload.put(Logger.APP_VERSION, info.versionName);
            payload.put(Logger.APP_VERSION_CODE, info.versionCode);
            payload.put(Logger.APP_PACKAGE, this._context.getPackageName());

            payload.put(Logger.APP_DEVICE_MANUFACTURER, Build.MANUFACTURER);
            payload.put(Logger.APP_DEVICE_MODEL, Build.MODEL);
        }
        catch (NameNotFoundException e)
        {
            Logger.getInstance(this._context, this._userId).logException(e);
        }

        payload.put("os_version", Build.VERSION.RELEASE);
        payload.put("os", "android");

        long utcOffset = TimeZone.getDefault().getOffset(System.currentTimeMillis());

        payload.put("utc_offset_ms", utcOffset);

        if (prefs.getBoolean(Logger.LOGGER_ENABLED, Logger.LOGGER_ENABLED_DEFAULT))
        {
            String endpointUri = prefs.getString(Logger.LOGGER_URI, null);

            if (endpointUri != null)
            {
                if (prefs.getBoolean(Logger.LOGGER_LOCATION_ENABLED, Logger.LOGGER_LOCATION_ENABLED_DEFAULT))
                {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M )
                    {
                        if (ContextCompat.checkSelfPermission(this._context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this._context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            LocationManager lm = (LocationManager) this._context.getSystemService(Context.LOCATION_SERVICE);

                            Location lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                            Location backupLocation = null;

                            if (lastLocation != null && now - lastLocation.getTime() > (1000 * 60 * 60)) {
                                backupLocation = lastLocation;

                                lastLocation = null;
                            }

                            if (lastLocation == null)
                                lastLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                            if (lastLocation == null)
                                lastLocation = backupLocation;

                            if (lastLocation != null) {
                                payload.put(Logger.LATITUDE, lastLocation.getLatitude());
                                payload.put(Logger.LONGITUDE, lastLocation.getLongitude());
                                payload.put(Logger.ALTITUDE, lastLocation.getAltitude());
                                payload.put(Logger.TIME_DRIFT, now - lastLocation.getTime());
                            }
                        }
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
                        Object value = payload.get(key);

                        if (value instanceof List)
                        {
                            List<Object> list = (List<Object>) value;

                            JSONArray jsonArray = new JSONArray();

                            for (Object item : list)
                                jsonArray.put(item);

                            value = jsonArray;
                        }

                        jsonEvent.put(key, value);
                    }

                    jsonEvent.put(Logger.CONTENT_OBJECT, new JSONObject(jsonEvent.toString()));

                    values.put(LogContentProvider.APP_EVENT_PAYLOAD, jsonEvent.toString());

                    this._context.getContentResolver().insert(LogContentProvider.eventsUri(this._context), values);

                    return true;
                }
                catch (JSONException | NameNotFoundException e)
                {
                    this.logException(e);
                }
            }
        }

        return false;
    }

    public void attemptUploads(final boolean force)
    {
        final Logger me = this;

        if (this._uploading)
            return;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me._context);

        if (prefs.getBoolean(Logger.LOGGER_ENABLED, Logger.LOGGER_ENABLED_DEFAULT) == false)
            return;

        if (prefs.getBoolean(Logger.HEARTBEAT, Logger.HEARTBEAT_DEFAULT))
        {
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("source", "Logger.attemptUploads");
            payload.put("force", force);

            this.log("heartbeat", payload);
        }

        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                if (force)
                    me._lastUpload = 0;

                if (me._uploading)
                    return;

                long now = System.currentTimeMillis();

                long interval = prefs.getLong(Logger.INTERVAL, Logger.DEFAULT_INTERVAL);

                if (now - me._lastUpload < interval)
                    return;

                me._lastUpload = now;

                boolean restrictWifi = prefs.getBoolean(Logger.ONLY_WIFI, Logger.ONLY_WIFI_DEFAULT);

                if (restrictWifi && WiFiHelper.wifiAvailable(me._context) == false)
                    return;

                boolean restrictCharging = prefs.getBoolean(Logger.ONLY_CHARGING, Logger.ONLY_CHARGING_DEFAULT);

                if (restrictCharging && PowerHelper.isPluggedIn(me._context) == false)
                    return;

                boolean logConnectionErrors = prefs.getBoolean(Logger.LOG_CONNECTION_ERRORS, Logger.LOG_CONNECTION_ERRORS_DEFAULT);

                me._uploading = true;

                String endpointUri = prefs.getString(Logger.LOGGER_URI, null);
                final String userAgent = prefs.getString(Logger.LOGGER_USER_AGENT, Logger.LOGGER_USER_AGENT_DEFAULT);

                if (endpointUri != null)
                {
                    try
                    {
                        URI siteUri = new URI(endpointUri);

                        OkHttpClient client = null;

                        final SSLContext sslContext = SSLContext.getInstance("SSL");

                        if (prefs.getBoolean(Logger.LIBERAL_SSL, Logger.LIBERAL_SSL_DEFAULT))
                        {
                            X509TrustManager trustAll = new X509TrustManager() {
                                @Override
                                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                                }

                                @Override
                                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                                }

                                @Override
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return new java.security.cert.X509Certificate[]{};
                                }
                            };

                            final TrustManager[] trustAllCerts = new TrustManager[] {
                                    trustAll
                            };

                            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                            OkHttpClient.Builder builder = new OkHttpClient.Builder();

                            builder.sslSocketFactory(sslSocketFactory, trustAll);
                            builder.addNetworkInterceptor(new Interceptor() {
                                @Override
                                public Response intercept(Chain chain) throws IOException {
                                    Request originalRequest = chain.request();
                                    Request requestWithUserAgent = originalRequest.newBuilder()
                                            .header("User-Agent", userAgent)
                                            .build();
                                    return chain.proceed(requestWithUserAgent);
                                }
                            });

                            builder.hostnameVerifier(new HostnameVerifier() {
                                @Override
                                public boolean verify(String hostname, SSLSession session) {
                                    return true;
                                }
                            });

                            client = builder.build();
                        } else {
                            OkHttpClient.Builder builder = new OkHttpClient.Builder();
                            builder.addNetworkInterceptor(new Interceptor() {
                                @Override
                                public Response intercept(Chain chain) throws IOException {
                                    Request originalRequest = chain.request();
                                    Request requestWithUserAgent = originalRequest.newBuilder()
                                            .header("User-Agent", userAgent)
                                            .build();
                                    return chain.proceed(requestWithUserAgent);
                                }
                            });

                            client = new OkHttpClient();
                        }

                        String selection = LogContentProvider.APP_EVENT_TRANSMITTED + " = ?";
                        String[] args = { "" + 0 };

                        Cursor c = me._context.getContentResolver().query(LogContentProvider.eventsUri(me._context), null, selection, args, LogContentProvider.APP_EVENT_RECORDED);

                        if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
                            Log.e("LOG", "Log endpoint: " + siteUri);

                        int failCount = 0;

                        for (int i = 0; i < 250 && c.moveToNext() && failCount < 8; i++)
                        {
                            try
                            {
                                if (prefs.getBoolean(Logger.RAILS_MODE, Logger.RAILS_MODE_DEFAULT))
                                {
                                    String payload = c.getString(c.getColumnIndex(LogContentProvider.APP_EVENT_PAYLOAD));

                                    JSONObject payloadJson = new JSONObject(payload);

                                    JSONObject submission = new JSONObject();

                                    Date emitted = new Date((payloadJson.getLong("timestamp") * 1000));

                                    TimeZone tz = TimeZone.getTimeZone("UTC");
                                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'000'");
                                    df.setTimeZone(tz);

                                    JSONObject event = new JSONObject();

                                    event.put("date_emitted", df.format(emitted));
                                    event.put("payload", payload);
                                    event.put("kind", payloadJson.getString("event_type"));
                                    event.put("user_ID", payloadJson.getString("user_id"));

                                    submission.put("event", event);

                                    Request request = new Request.Builder()
                                            .url(endpointUri)
                                            .post(RequestBody.create(MediaType.parse("application/json"), event.toString(2)))
                                            .build();

                                    Response response = client.newCall(request).execute();

                                    String responseContent = response.body().string();

                                    if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
                                        Log.e("LOG", "Log upload result: " + responseContent + " (" + c.getCount() + " remaining)");

                                    JSONObject statusJson = new JSONObject(responseContent);

                                    if ((statusJson.has("status") && "success".equalsIgnoreCase(statusJson.getString("status"))) || (statusJson.has("result") && "success".equalsIgnoreCase(statusJson.getString("result"))) || (statusJson.has("invalid") && "invalid".equalsIgnoreCase(statusJson.getString("result"))))
                                    {
                                        ContentValues values = new ContentValues();
                                        values.put(LogContentProvider.APP_EVENT_TRANSMITTED, System.currentTimeMillis());

                                        String updateWhere = LogContentProvider.APP_EVENT_ID + " = ?";
                                        String[] updateArgs =
                                        { "" + c.getLong(c.getColumnIndex(LogContentProvider.APP_EVENT_ID)) };

                                        me._context.getContentResolver().update(LogContentProvider.eventsUri(me._context), values, updateWhere, updateArgs);
                                    }
                                    else
                                        failCount += 1;
                                }
                                else
                                {
                                    String payload = c.getString(c.getColumnIndex(LogContentProvider.APP_EVENT_PAYLOAD));

                                    RequestBody formBody = new FormBody.Builder()
                                            .add(Logger.JSON, payload)
                                            .build();

                                    Request request = new Request.Builder()
                                            .url(endpointUri)
                                            .post(formBody)
                                            .build();

                                    Response response = client.newCall(request).execute();

                                    String responseContent = response.body().string();

                                    Cursor remaining = me._context.getContentResolver().query(LogContentProvider.eventsUri(me._context), null, selection, args, LogContentProvider.APP_EVENT_RECORDED);

                                    if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
                                        Log.e("LOG", "Log upload result: " + responseContent + " (" + remaining.getCount() + " remaining)");

                                    remaining.close();

                                    JSONObject statusJson = new JSONObject(responseContent);

                                    if ((statusJson.has("status") && "success".equalsIgnoreCase(statusJson.getString("status"))) || (statusJson.has("result") && "success".equalsIgnoreCase(statusJson.getString("result"))))
                                    {
                                        ContentValues values = new ContentValues();
                                        values.put(LogContentProvider.APP_EVENT_TRANSMITTED, System.currentTimeMillis());

                                        String updateWhere = LogContentProvider.APP_EVENT_ID + " = ?";
                                        String[] updateArgs =
                                        { "" + c.getLong(c.getColumnIndex(LogContentProvider.APP_EVENT_ID)) };

                                        me._context.getContentResolver().update(LogContentProvider.eventsUri(me._context), values, updateWhere, updateArgs);
                                    }
                                }
                            }
                            catch (UnknownHostException | NameNotFoundException e)
                            {
                                failCount += 1;

                                if (logConnectionErrors)
                                    me.logException(e);
                            }
                            catch (IOException e)
                            {
                                failCount += 1;

                                me.logException(e);
                            }
                            catch (JSONException e)
                            {
                                failCount += 1;

                                // Don't log - will cause cascading failure... (80k+ e-mails FTW)
                            }
                            catch (Exception e)
                            {
                                failCount += 1;

                                me.logException(e);
                            }
                        }

                        c.close();

                        selection = LogContentProvider.APP_EVENT_TRANSMITTED + " != ?";

                        me._context.getContentResolver().delete(LogContentProvider.eventsUri(me._context), selection, args);

                        selection = LogContentProvider.APP_UPLOAD_TRANSMITTED + " = ?";

                        c = me._context.getContentResolver().query(LogContentProvider.uploadsUri(me._context), null, selection, args, LogContentProvider.APP_UPLOAD_RECORDED);

                        while (c.moveToNext())
                        {
                            String payload = c.getString(c.getColumnIndex(LogContentProvider.APP_UPLOAD_PAYLOAD));
                            String uploadUri = c.getString(c.getColumnIndex(LogContentProvider.APP_UPLOAD_URI));

                            try
                            {
                                JSONObject payloadJson = new JSONObject(payload);

                                Request request = new Request.Builder()
                                        .url(endpointUri)
                                        .post(RequestBody.create(MediaType.parse("application/json"), payloadJson.toString(2)))
                                        .build();

                                Response response = client.newCall(request).execute();

                                String responseContent = response.body().string();

                                int status = response.code();

                                if (status >= 200 && status < 300)
                                {
                                    ContentValues values = new ContentValues();
                                    values.put(LogContentProvider.APP_UPLOAD_TRANSMITTED, System.currentTimeMillis());

                                    String updateWhere = LogContentProvider.APP_UPLOAD_ID + " = ?";
                                    String[] updateArgs =
                                    { "" + c.getLong(c.getColumnIndex(LogContentProvider.APP_UPLOAD_ID)) };

                                    me._context.getContentResolver().update(LogContentProvider.uploadsUri(me._context), values, updateWhere, updateArgs);
                                }

                                if (prefs.getBoolean(Logger.DEBUG, Logger.DEBUG_DEFAULT))
                                    Log.e("LOG", "Upload transmission result: " + responseContent + " (" + c.getLong(c.getColumnIndex(LogContentProvider.APP_UPLOAD_ID)) + ")");
                            }
                            catch (UnknownHostException | NameNotFoundException e)
                            {
                                if (logConnectionErrors)
                                    me.logException(e);
                            } catch (IOException e)
                            {
                                me.logException(e);
                            }
                        }

                        c.close();

                        selection = LogContentProvider.APP_UPLOAD_TRANSMITTED + " != ?";

                        me._context.getContentResolver().delete(LogContentProvider.eventsUri(me._context), selection, args);
                    }
                    catch (OutOfMemoryError | Exception e)
                    {
                        me.logException(e);
                    }
                }

                me._uploading = false;
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            r.run();
        } else {
            Thread t = new Thread(r);

            try {
                t.start();
            } catch (OutOfMemoryError e) {
                System.gc();

                this.logException(e);
            }
        }
    }

    public int pendingEventsCount()
    {
        int count = -1;

        String selection = LogContentProvider.APP_EVENT_TRANSMITTED + " != ?";
        String[] args = { "1" };

        try
        {
            Cursor c = this._context.getContentResolver().query(LogContentProvider.eventsUri(this._context), null, selection, args, null);

            count = c.getCount();

            c.close();
        }
        catch (NameNotFoundException e)
        {

        }

        return count;
    }

    public void logException(Throwable e)
    {
        e.printStackTrace();

        Map<String, Object> payload = new HashMap<>();

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
        e.apply();
    }

    public void setEnabled(boolean enabled)
    {
        this.setBoolean(Logger.LOGGER_ENABLED, enabled);
    }

    public void setRailsMode(boolean enableRailsMode)
    {
        this.setBoolean(Logger.RAILS_MODE, enableRailsMode);
    }

    public void setLogConnectionErrors(boolean logConnectionErrors)
    {
        this.setBoolean(Logger.LOG_CONNECTION_ERRORS, logConnectionErrors);
    }

    public boolean getLogConnectionErrors()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

        return prefs.getBoolean(Logger.LOG_CONNECTION_ERRORS, Logger.LOG_CONNECTION_ERRORS_DEFAULT);
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

    public void setChargingOnly(boolean chargingOnly)
    {
        this.setBoolean(Logger.ONLY_CHARGING, chargingOnly);
    }

    public void setDebug(boolean debug)
    {
        this.setBoolean(Logger.DEBUG, debug);
    }

    public void setHeartbeat(boolean heartbeat)
    {
        this.setBoolean(Logger.HEARTBEAT, heartbeat);
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
        e.apply();
    }

    public void setUserAgent(String userAgent)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

        Editor e = prefs.edit();
        e.putString(Logger.LOGGER_USER_AGENT, userAgent);
        e.apply();
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
        e.apply();
    }

    public boolean postJsonContent(JSONObject content, Uri destination)
    {
        long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(LogContentProvider.APP_UPLOAD_PAYLOAD, content.toString());
        values.put(LogContentProvider.APP_UPLOAD_URI, destination.toString());
        values.put(LogContentProvider.APP_UPLOAD_RECORDED, now);

        Uri u = null;

        try
        {
            u = this._context.getContentResolver().insert(LogContentProvider.uploadsUri(this._context), values);
        }
        catch (NameNotFoundException e)
        {
            this.logException(e);
        }

        return (u != null);
    }

    public static String getSystemUserId(Context context)
    {
        String userId = null;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (ContextCompat.checkSelfPermission(context, "android.permissions.GET_ACCOUNTS") == PackageManager.PERMISSION_GRANTED)) {
            AccountManager manager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
            Account[] list = manager.getAccountsByType("com.google");

            if (list.length == 0)
                list = manager.getAccounts();

            if (list.length > 0)
                userId = list[0].name;
        }

        return userId;
    }
}
