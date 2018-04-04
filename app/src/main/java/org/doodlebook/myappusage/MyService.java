package org.doodlebook.myappusage;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.IOException;
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
    OkHttpClient client;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        myServiceReceiver = new MyServiceReceiver();
        client = new OkHttpClient();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(getApplicationContext(), "MyService: onStartCommand", Toast.LENGTH_LONG).show();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(myServiceReceiver, intentFilter);

//        postDataToServer("debug: start service");

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
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                screenState = "on";
            }
            String usageData = getAppUsageStats(screenState);
            String data = Build.MODEL + " " + Build.VERSION.RELEASE
                    + "," + screenState + "," + usageData;
//            Toast.makeText(getApplicationContext(), data, Toast.LENGTH_LONG).show();

            if (canAccessWebServer() && isMyWebServerAvailable())
                postDataToServer(data);
            else {
//                saveDataLocally(data);  // do this later
//                Toast.makeText(getApplicationContext(), "HomeWifi not available", Toast.LENGTH_LONG).show();
            }
        }
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
            lStringBuilder.append(lUsageStats.getPackageName());
            lStringBuilder.append("|");
            lStringBuilder.append(lUsageStats.getLastTimeUsed());
            lStringBuilder.append("|");
            lStringBuilder.append(lUsageStats.getTotalTimeInForeground() / 1000);
            lStringBuilder.append(";");
        }
        return lStringBuilder.toString();
    }

    private void postDataToServer(String data) {
        OkHttpClient client;
        client = new OkHttpClient();

//        String url = "http://192.168.1.99:5000";  // GET method
        String url = "http://192.168.1.99:5000/api"; // POST method
        RequestBody body = new FormBody.Builder()
                .add("type", "app_usage")
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

}
