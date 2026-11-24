package com.example.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats UTC epoch timestamps into Pakistan Standard Time (PKT, Asia/Karachi, UTC+05:00),
 * computes stage durations, live elapsed timers, and PKT calendar range boundaries.
 */
public final class TimeFormatUtil {
    public static final TimeZone PKT_ZONE = TimeZone.getTimeZone("Asia/Karachi");

    private TimeFormatUtil() {}

    public static String formatDateTimePkt(long utcMillis) {
        if (utcMillis <= 0L) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US);
        sdf.setTimeZone(PKT_ZONE);
        return sdf.format(new Date(utcMillis)) + " PKT";
    }

    public static String formatShortDateTimePkt(long utcMillis) {
        if (utcMillis <= 0L) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US);
        sdf.setTimeZone(PKT_ZONE);
        return sdf.format(new Date(utcMillis));
    }

    public static String formatTimePkt(long utcMillis) {
        if (utcMillis <= 0L) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
        sdf.setTimeZone(PKT_ZONE);
        return sdf.format(new Date(utcMillis));
    }

    public static String formatDateOnlyPkt(long utcMillis) {
        if (utcMillis <= 0L) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.US);
        sdf.setTimeZone(PKT_ZONE);
        return sdf.format(new Date(utcMillis));
    }

    public static String formatDuration(long durationMs) {
        if (durationMs <= 0L) return "0s";
        long totalSec = durationMs / 1000L;
        long hours = totalSec / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm %02ds", hours, mins, secs);
        }
        if (mins > 0) {
            return String.format(Locale.US, "%dm %02ds", mins, secs);
        }
        return String.format(Locale.US, "%ds", secs);
    }

    public static String formatElapsedTimer(long startUtcMillis, long nowMillis) {
        if (startUtcMillis <= 0L || nowMillis <= startUtcMillis) return "00:00";
        long totalSec = (nowMillis - startUtcMillis) / 1000L;
        long hours = totalSec / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
        }
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    public static long startOfDayPkt(int daysAgo) {
        Calendar cal = Calendar.getInstance(PKT_ZONE, Locale.US);
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long endOfDayPkt(int daysAgo) {
        Calendar cal = Calendar.getInstance(PKT_ZONE, Locale.US);
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    public static boolean isWithinRange(long utcMillis, long startInclusive, long endInclusive) {
        if (utcMillis <= 0L) return false;
        return utcMillis >= startInclusive && utcMillis <= endInclusive;
    }
}
