package org.eu.exodusprivacynetguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.eu.exodusprivacynetguard.R;
import org.eu.exodusprivacynetguard.util.NetSpeedUtils;

/**
 * === NETSPEED OVERLAY SERVICE ===
 * Displays live download/upload speed in a floating overlay at the status bar area.
 * Requires SYSTEM_ALERT_WINDOW permission.
 * 
 * Features:
 * - Live ↑↓ speed updated every 1 second
 * - Semi-transparent black background with white monospace text
 * - Positioned at top-right corner (status bar area)
 * - Non-interactive (touch events pass through to underlying apps)
 * - Runs as foreground service for persistence
 */
public class NetSpeedOverlayService extends Service {

    // === Overlay UI Components ===
    private WindowManager windowManager;
    private TextView overlayView;
    private WindowManager.LayoutParams overlayParams;

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
            updateSpeed();
            if (isRunning) {
                handler.postDelayed(this, NetSpeedUtils.UPDATE_INTERVAL_MS);
            }
        }
    };

    // === Service State ===
    private boolean isRunning = false;

    // === Foreground Notification (Android 8+) ===
    private static final int FOREGROUND_ID = 2001;
    private static final String CHANNEL_ID = "netspeed_overlay_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();
        startForegroundWithNotification();
        setupOverlay();
        initSpeedTracking();
        handler.post(updateRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service will continue running until explicitly stopped
        return START_STICKY;
    }

    /**
     * Create notification channel for Android 8+ (required for foreground service)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "NetSpeed Overlay",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Keeps speed overlay active in background");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.enableLights(false);            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Start foreground service with minimal notification (Android requirement)
     */
    private void startForegroundWithNotification() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Speed Monitor Active")
            .setContentText("Live network speed overlay is running")
            .setSmallIcon(R.drawable.ic_network_speed)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build();
        
        try {
            startForeground(FOREGROUND_ID, notification);
        } catch (SecurityException e) {
            // Foreground service restriction (Android 14+)
            stopSelf();
        }
    }

    /**
     * Setup the overlay view with proper WindowManager parameters
     */
    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        if (windowManager == null) {
            stopSelf();
            return;
        }

        overlayView = new TextView(this);
        overlayView.setBackgroundColor(0x99000000); // Semi-transparent black (60% opacity)
        overlayView.setTextColor(Color.WHITE);
        overlayView.setPadding(20, 6, 20, 6);
        overlayView.setTextSize(11f);
        overlayView.setTypeface(null, Typeface.MONOSPACE);
        overlayView.setGravity(Gravity.CENTER);
        overlayView.setSingleLine(true);
        overlayView.setShadowLayer(2f, 1f, 1f, Color.BLACK); // Text shadow for readability
        // === Critical: Overlay Layout Parameters ===
        overlayParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        // Position at top-right corner (status bar area)
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = 8;   // 8px from right edge
        overlayParams.y = 4;   // 4px below top edge (status bar area)

        try {
            windowManager.addView(overlayView, overlayParams);
        } catch (SecurityException e) {
            // SYSTEM_ALERT_WINDOW permission not granted
            stopSelf();
        } catch (IllegalStateException e) {
            // Window manager error
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
     * Update speed calculation and overlay display
     */
    private void updateSpeed() {
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

        // === Calculate instantaneous speed (bytes/sec) ===
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

        // === Format display: "↓2.4 MB/s ↑0.8 MB/s" ===
        String display = String.format("↓%s ↑%s",
            NetSpeedUtils.formatNetSpeed(downSpeed),
            NetSpeedUtils.formatNetSpeed(upSpeed)
        );

        // === Update overlay text ===
        if (overlayView != null) {
            overlayView.setText(display);
        }

        // === Update tracking values for next iteration ===
        lastRx = currentRx;
        lastTx = currentTx;
        lastTime = now;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
                // Stop update loop
        handler.removeCallbacksAndMessages(null);
        
        // Remove overlay view
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(overlayView);
            } catch (IllegalArgumentException e) {
                // View already removed - ignore
            }
        }
        
        overlayView = null;
        windowManager = null;
        
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
