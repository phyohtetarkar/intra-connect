/**
 * Copyright (c) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.genz.intraproxy.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.genz.intraproxy.MainActivity;
import com.genz.intraproxy.R;

import java.util.Objects;

/**
 * @hide
 */
public class ProxyService extends Service {

    private static ProxyServer server = null;
    private static final String NOTIFICATION_ID = "22222";

    /** Keep these values up-to-date with PacManager.java */
    public static final String KEY_PROXY = "keyProxy";
    public static final String HOST = "localhost";
    public static final String EXCL_LIST = "";

    public static final String ACTION_SERVER_STATUS = "SERVER_STATUS";
    public static final String ACTION_SERVER_STOP = "SERVER_STOP";
    public static final String ACTION_SERVER_START = "SERVER_START";

    public static final String KEY_SERVER_RUNNING = "SERVER_RUNNING";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stopServer();
            server = null;
        }
        Intent intent = new Intent(ACTION_SERVER_STOP);
        LocalBroadcastManager.getInstance(ProxyService.this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Objects.equals(intent.getAction(), ACTION_SERVER_STOP)) {
            stopForeground(true);
            stopSelf();
        } else if (Objects.equals(intent.getAction(), ACTION_SERVER_STATUS)) {
            Intent statusIntent = new Intent(ACTION_SERVER_STATUS);
            statusIntent.putExtra(KEY_SERVER_RUNNING, server != null && server.mIsRunning);
            LocalBroadcastManager.getInstance(ProxyService.this).sendBroadcast(statusIntent);
        } else if (server != null && server.mIsRunning) {
            stopForeground(true);
            stopSelf();
        } else {
            startProxyServer();
        }
        return START_STICKY;
    }

    private void startProxyServer() {
        if (server == null) {
            server = new ProxyServer();
            server.startServer();

            server.setServerStatusListener(new ProxyServer.ServerStatusListener() {
                @Override
                public void onStarted() {
                    startNotificationForeground();
                    Intent intent = new Intent(ACTION_SERVER_STATUS);
                    intent.putExtra(KEY_SERVER_RUNNING, true);
                    LocalBroadcastManager.getInstance(ProxyService.this).sendBroadcast(intent);
                }

                @Override
                public void onStopped() {
                    stopForeground(true);
                    stopSelf();
                }
            });
        }
    }

    private void startNotificationForeground() {
        createNotificationChannel();

        Intent contentIntent = new Intent(this, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentText("Proxy Server running on: " + MainActivity.getIPAddress() + ":" + ProxyServer.PROXY_PORT)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(Integer.parseInt(NOTIFICATION_ID), notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }

        }
    }
}