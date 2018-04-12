package org.doodlebook.myappusage;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyService extends Service {

    MyServiceReceiver myServiceReceiver;
//    OkHttpClient client;

    SensorManager sensorManager;
    Sensor sensorAccelerometer, sensorMagnetic, sensorGyro;

    HashMap<Integer, Long> sensorPrevmillis = new HashMap<Integer, Long>();
    HashMap<Integer, String> sensorTypeName = new HashMap<Integer, String>();
    StringBuilder dataSensor;
    long listenCount;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        Toast.makeText(getApplicationContext(), "MyService: onStartCommand", Toast.LENGTH_LONG).show();

        myServiceReceiver = new MyServiceReceiver();

        setupSensors();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(myServiceReceiver, intentFilter);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Toast.makeText(getApplicationContext(), "MyService: onDestroy", Toast.LENGTH_LONG).show();
        unregisterReceiver(myServiceReceiver);
        super.onDestroy();
    }

    private class MyServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            String screenState = "";
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                screenState = "off";
                sensorManager.unregisterListener(sensorEventListener);
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                screenState = "on";
                registerSensor();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                screenState = "onn";
                registerSensor();
            }
            String usageData = getAppUsageStats(screenState);
            String data = Build.MODEL + " " + Build.VERSION.RELEASE
                    + "," + screenState + "," + usageData;

            postDataToServer("app_usage", data);
        }
    }

    private void registerSensor() {
        sensorManager.registerListener(sensorEventListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, sensorGyro, SensorManager.SENSOR_DELAY_NORMAL);

        long prev_millis = System.currentTimeMillis();
        sensorPrevmillis.put(Sensor.TYPE_ACCELEROMETER, prev_millis);
        sensorPrevmillis.put(Sensor.TYPE_MAGNETIC_FIELD, prev_millis);
        sensorPrevmillis.put(Sensor.TYPE_GYROSCOPE, prev_millis);

        listenCount = 0;
        dataSensor = new StringBuilder();
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        sensorTypeName.put(Sensor.TYPE_ACCELEROMETER, "ACC");
        sensorTypeName.put(Sensor.TYPE_MAGNETIC_FIELD, "MAG");
        sensorTypeName.put(Sensor.TYPE_GYROSCOPE, "GYR");
    }

    public SensorEventListener sensorEventListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        public void onSensorChanged(SensorEvent event) {
            int sensorType = event.sensor.getType();  // 1: accelerator, 2: magnetic, 4: gyroscope, ??: light
            long curr_millis = System.currentTimeMillis();
            if (curr_millis - sensorPrevmillis.get(sensorType) < 3000) // 1000 = 1 second
                return;
            sensorPrevmillis.put(sensorType, curr_millis);

            float[] xyz = event.values;

            String record = sensorTypeName.get(sensorType);
            for (int i=0; i<3; i++)
                record += "|" + String.format("%.2f", xyz[i]);
            record += ";";

            Log.d("info", listenCount + "   \n" + record + "\n");
            dataSensor.append(record);

            if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
                listenCount++;
                if (listenCount%10 == 0) {
                    dataSensor.append("," + Build.MODEL + " " + Build.VERSION.RELEASE + "," + System.currentTimeMillis());
                    if (listenCount==5 || listenCount==10 || listenCount==20) {
                        postDataToServer("posture_xyz", dataSensor.toString());
                        if (listenCount==20) {
                            sensorManager.unregisterListener(sensorEventListener);
                        }
                    }
                    dataSensor.setLength(0);
                }
            }
        }
    };



    private String getAppUsageStats(String screenState) {
        long duration;
        if (screenState.equals("on")) {
//            duration = TimeUnit.HOURS.toMillis(12);
            duration = TimeUnit.MINUTES.toMillis(10);
        } else {
            duration = TimeUnit.MINUTES.toMillis(60);
        }

        UsageStatsManager lUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> lUsageStatsList = lUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                System.currentTimeMillis() - duration,
                System.currentTimeMillis() + duration);

        StringBuilder lStringBuilder = new StringBuilder();

        for (UsageStats lUsageStats:lUsageStatsList) {
            int totalTimeInForeground = (int) lUsageStats.getTotalTimeInForeground() / 1000;
            if (totalTimeInForeground<1) {
                continue;
            }
            lStringBuilder.append(lUsageStats.getPackageName());
            lStringBuilder.append("|");
            lStringBuilder.append(lUsageStats.getLastTimeUsed());
            lStringBuilder.append("|");
            lStringBuilder.append(totalTimeInForeground);
            lStringBuilder.append(";");
        }
        return lStringBuilder.toString();
    }

    private void postDataToServer(String task, String data) {
        if (canAccessWebServer() && isMyWebServerAvailable())
            doPostDataToServer(task, data);
        else { // I should save data here for debug: what happens when no data received on my server
//                Toast.makeText(getApplicationContext(), "HomeWifi not available", Toast.LENGTH_LONG).show();
//                saveDataLocally(data);  // do this later
        }
    }

    private void doPostDataToServer(String task, String data) {
        OkHttpClient client;
        client = new OkHttpClient();

//        String url = "http://192.168.1.99:5000";  // GET method
        String url = "http://192.168.1.99:5000/api"; // POST method
        RequestBody body = new FormBody.Builder()
                .add("type", task)
                .add("data", data)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)  // if needs to "GET", just comment this line
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                Log.d("TAG", "hundan onFailure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("TAG", response.body().string());
            }
        });

    }

    private boolean isMyWebServerAvailable() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 192.168.1.99");
            int     exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException | InterruptedException e) { e.printStackTrace(); }
        return false;
    }

    private boolean canAccessWebServer() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting() &&
                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

/*
    private final String HOME_BSSID = "48:5d:36:25:e1:5a"; // FiOS-SCGDC
    private boolean isHomeWifiAvailable() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifi.startScan();
        List<ScanResult> wifiScanList = wifi.getScanResults();

        for (ScanResult scanResult : wifiScanList) {
            String ssid = scanResult.SSID;
            String bssid = scanResult.BSSID;
//            Log.d("SSID", ssid + "   " + bssid);
            if (bssid.equals(HOME_BSSID)) {
                Log.d("FOUND", ssid + "   " + bssid);
                return true;
            }
        }
        return false;
    }
*/


}
