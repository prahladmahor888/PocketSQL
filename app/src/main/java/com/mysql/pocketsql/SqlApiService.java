package com.mysql.pocketsql;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mysql.pocketsql.engine.SqlApiHelper;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SqlApiService extends Service {
    private static final String CHANNEL_ID = "pocketsql_api_channel";
    private static final int NOTIFICATION_ID = 2026;
    public static final String ACTION_STOP_SERVER = "com.mysql.pocketsql.ACTION_STOP_SERVER";

    @Override
    public void onCreate() {
        super.onCreate();
        SqlApiHelper.init(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVER.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start the server
        SqlApiHelper.getApiServer().start();

        // Create notification channel
        createNotificationChannel();

        // Build notification with Stop Action
        Intent stopIntent = new Intent(this, SqlApiService.class);
        stopIntent.setAction(ACTION_STOP_SERVER);
        
        // PendingIntent flags
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, pendingFlags);

        // Open app on notification click
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, pendingFlags);

        String ip = com.mysql.pocketsql.engine.SqlApiHelper.getLocalIpAddress();
        int activePort = com.mysql.pocketsql.engine.SqlApiHelper.getApiServer().getActivePort();
        String bindError = com.mysql.pocketsql.engine.SqlApiHelper.getApiServer().getBindErrorMessage();
        String content = (bindError != null) ? "API Server bind failed: " + bindError : "API Server running on: http://" + ip + ":" + activePort + "/api/query";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PocketSQL API Server")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(mainPendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            SqlApiHelper.getApiServer().stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "PocketSQL API Server Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps the local SQL API Server running in the background.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
