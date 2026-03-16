package org.eu.exodusprivacynetguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import org.eu.exodusprivacynetguard.ActivityMain;
import org.eu.exodusprivacynetguard.R;
import org.eu.exodusprivacynetguard.util.NetSpeedUtils;

/**
 * === NETSPEED NOTIFICATION SERVICE ===
 * Updates NetGuard's foreground notification with live speed + daily data total.
 * Does NOT require overlay permission - uses existing notification channel.
 * 
 * Features:
 * - Live ↑↓ speed updated every 1 second
 * - Daily total data usage (resets at midnight)
 * - Appends to existing NetGuard notification text
 * - Tapping notification opens NetGuard main activity
 */
public class NetSpeedNotificationService extends Service {

    // === Speed Tracking State ===
    private long lastRx = 0;
    private long lastTx = 0;
    private long lastTime = 0;
    private int lastDayOfYear = -1;
    private long todayRx = 0;
    private long todayTx = 0;

    // === Update Loop ===
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            if (isRunning) {
                handler.postDelayed(this, NetSpeedUtils.UPDATE_INTERVAL_MS);
            }
        }
    };

    // === Service State ===
    private boolean isRunning = false;
    private int notificationId = 1; // NetGuard's default notification ID

    // === Notification Channel ===
    private static final String CHANNEL_ID = "netspeed_notification_channel";
    private static final int FOREGROUND_ID = 2002;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();
        startForegroundWithNotification();
        initSpeedTracking();
        handler.post(updateRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Get notification ID from intent if provided
        if (intent != null) {
            notificationId = intent.getIntExtra("notification_id", 1);
        }
        return START_STICKY;
    }

    /**
     * Create notification channel for Android 8+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "NetSpeed Notification",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows live network speed in notification");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Start foreground service with notification
     */
    private void startForegroundWithNotification() {
        Intent intent = new Intent(this, ActivityMain.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetGuard Active")
            .setContentText("Firewall protecting • Speed monitor running")
            .setSmallIcon(R.drawable.ic_network_speed)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();

        try {
            startForeground(FOREGROUND_ID, notification);
        } catch (SecurityException e) {
            stopSelf();
        }
    }

    /**
     * Initialize speed tracking variables
     */
    private void initSpeedTracking() {
        lastTime = NetSpeedUtils.getCurrentTimeMs();
        lastRx = TrafficStats.getTotalRxBytes();
        lastTx = TrafficStats.getTotalTxBytes();
        lastDayOfYear = NetSpeedUtils.getCurrentDayOfYear();
        todayRx = 0;
        todayTx = 0;
    }

    /**
     * Update notification with speed + daily total
     */
    private void updateNotification() {
        long now = NetSpeedUtils.getCurrentTimeMs();
        long currentRx = TrafficStats.getTotalRxBytes();
        long currentTx = TrafficStats.getTotalTxBytes();

        // Skip if TrafficStats unsupported
        if (currentRx == NetSpeedUtils.UNSUPPORTED || currentTx == NetSpeedUtils.UNSUPPORTED) {
            return;
        }

        long deltaTime = now - lastTime;
        if (deltaTime <= 0) {
            return;
        }

        // === Calculate instantaneous speed ===
        long downSpeed = NetSpeedUtils.calculateSpeed(currentRx, lastRx, deltaTime);
        long upSpeed = NetSpeedUtils.calculateSpeed(currentTx, lastTx, deltaTime);

        // === Reset daily total at midnight ===
        if (NetSpeedUtils.hasDayChanged(lastDayOfYear)) {
            todayRx = 0;
            todayTx = 0;
            lastDayOfYear = NetSpeedUtils.getCurrentDayOfYear();
        }

        // === Accumulate daily total ===
        if (downSpeed >= 0) {
            todayRx += (downSpeed * deltaTime) / 1000;
        }
        if (upSpeed >= 0) {
            todayTx += (upSpeed * deltaTime) / 1000;
        }

        // === Format: "• ↓2.4 MB/s ↑0.8 MB/s • Today: 1.2 GB" ===
        String speedText = String.format(" • ↓%s ↑%s • Today: %s",
            NetSpeedUtils.formatNetSpeed(downSpeed),
            NetSpeedUtils.formatNetSpeed(upSpeed),
            NetSpeedUtils.formatDataSize(this, todayRx + todayTx)
        );

        // === Update notification ===
        updateNotificationText(speedText);

        // === Update tracking values ===
        lastRx = currentRx;
        lastTx = currentTx;
        lastTime = now;
    }

    /**
     * Update the notification text with speed info
     */
    private void updateNotificationText(String speedText) {
        Intent intent = new Intent(this, ActivityMain.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String fullText = "NetGuard Active" + speedText;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetGuard Active")
            .setContentText(fullText)
            .setSmallIcon(R.drawable.ic_network_speed)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();

        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(notificationId, notification);
            }
        } catch (SecurityException e) {
            // Notification update failed - stop service
            isRunning = false;
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        
        // Stop update loop
        handler.removeCallbacksAndMessages(null);
        
        // Stop foreground service
        try {
            stopForeground(true);
        } catch (SecurityException e) {
            // Ignore
        }
        
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    }
