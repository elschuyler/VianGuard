package org.eu.exodusprivacynetguard.util;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.provider.Settings;
import android.text.format.Formatter;

import java.util.Calendar;

/**
 * === NETSPEED UTILITY CLASS ===
 * Shared helper methods for network speed calculation and formatting.
 * Used by both overlay and notification speed services.
 */
public class NetSpeedUtils {

    // === Constants ===
    public static final long UPDATE_INTERVAL_MS = 1000; // 1 second updates
    public static final long UNSUPPORTED = TrafficStats.UNSUPPORTED;

    // === Prevent instantiation ===
    private NetSpeedUtils() {}

    /**
     * Calculate instantaneous speed in bytes/second
     * @param currentBytes Current total bytes (Rx or Tx)
     * @param lastBytes Previous total bytes
     * @param deltaTimeMs Time delta in milliseconds
     * @return Speed in bytes/sec, or UNSUPPORTED if invalid
     */
    public static long calculateSpeed(long currentBytes, long lastBytes, long deltaTimeMs) {
        if (currentBytes == UNSUPPORTED || lastBytes == UNSUPPORTED) {
            return UNSUPPORTED;
        }
        if (deltaTimeMs <= 0) {
            return 0;
        }
        
        long delta = currentBytes - lastBytes;
        if (delta < 0) {
            // Counter reset (e.g., device reboot) - treat as zero
            delta = 0;
        }
        
        // Convert to bytes/sec: (bytes * 1000) / ms
        return (delta * 1000) / deltaTimeMs;
    }

    /**     * Format network speed (1000-based, network standard)
     * @param bytesPerSec Speed in bytes per second
     * @return Formatted string (e.g., "2.4 MB/s")
     */
    public static String formatNetSpeed(long bytesPerSec) {
        if (bytesPerSec < 0 || bytesPerSec == UNSUPPORTED) {
            return "0 B/s";
        }
        if (bytesPerSec < 1_000) {
            return bytesPerSec + " B/s";
        }
        if (bytesPerSec < 1_000_000) {
            return String.format("%.1f KB/s", bytesPerSec / 1_000f);
        }
        if (bytesPerSec < 1_000_000_000) {
            return String.format("%.1f MB/s", bytesPerSec / 1_000_000f);
        }
        return String.format("%.1f GB/s", bytesPerSec / 1_000_000_000f);
    }

    /**
     * Format data size (1024-based, Android storage standard)
     * @param context Application context
     * @param bytes Total bytes
     * @return Formatted string (e.g., "1.2 GB")
     */
    public static String formatDataSize(Context context, long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        return Formatter.formatShortFileSize(context, bytes);
    }

    /**
     * Check if overlay permission is granted
     * @param context Application context
     * @return true if SYSTEM_ALERT_WINDOW is granted
     */
    public static boolean canDrawOverlay(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true; // Pre-Marshmallow doesn't require explicit permission
        }
        return Settings.canDrawOverlays(context);
    }

    /**
     * Check if day has changed (for daily total reset at midnight)     * @param lastDayOfYear Previous day of year
     * @return true if day has changed
     */
    public static boolean hasDayChanged(int lastDayOfYear) {
        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_YEAR);
        return currentDay != lastDayOfYear;
    }

    /**
     * Get current day of year
     * @return Day of year (1-366)
     */
    public static int getCurrentDayOfYear() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Get current time in milliseconds
     * @return System.currentTimeMillis()
     */
    public static long getCurrentTimeMs() {
        return System.currentTimeMillis();
    }
}
