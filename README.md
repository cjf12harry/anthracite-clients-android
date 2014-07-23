To use this library, follow these steps:

Step 1: Verify that the following is within your app's AndroidManifest.xml file:

```
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Step 2: Add the following service and provider within the `<application />` element:

```
    <provider android:name="edu.northwestern.cbits.anthracite.LogContentProvider" android:authorities="my.package.name.logging" android:exported="false" />
    <service android:enabled="true" android:name="edu.northwestern.cbits.anthracite.LogService" android:exported="false">
        <intent-filter>
            <action android:name="my.package.name.UPLOAD_LOGS_INTENT" />
        </intent-filter>
    </service>
```

Replace instances of `my.package.name` with the package name of your Android app. For example, if the app's package name is `com.example.app`, the lines in the manifest will be:

```
    <provider android:name="edu.northwestern.cbits.anthracite.LogContentProvider" android:authorities="com.example.app.logging" android:exported="false" />
    <service android:enabled="true" android:name="edu.northwestern.cbits.anthracite.LogService" android:exported="false">
        <intent-filter>
            <action android:name="com.example.app.UPLOAD_LOGS_INTENT" />
        </intent-filter>
    </service>
```

The Anthracite client library uses the app's package name to dynamically construct content providers and services used to log events.
