package org.doodlebook.myappusage;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

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

            String screenOff = "";
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                screenOff = "off";
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                screenOff = "on";
            }
            Log.d("error", screenOff);
            sendDataToServer(screenOff);
        }
    }

    public static String PHONE_INFO = Build.MODEL + " " + Build.VERSION.RELEASE;

    private void sendDataToServer(String data) {
        OkHttpClient client;
        client = new OkHttpClient();

//        String url = "http://192.168.1.99:5000";  // GET method
        String url = "http://192.168.1.99:5000/api"; // POST method
        RequestBody body = new FormBody.Builder()
                .add("type", "app_usage")
                .add("data", PHONE_INFO + "," + data)
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
