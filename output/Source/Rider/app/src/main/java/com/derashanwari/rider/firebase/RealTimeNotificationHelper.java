package com.derashanwari.rider.firebase;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.derashanwari.rider.R;

/**
 * Displays high-priority local Android notifications triggered by real-time Firestore snapshot updates.
 */
public final class RealTimeNotificationHelper {
    public static final String CHANNEL_ID = "dera_shanwari_realtime_alerts";
    private static final String CHANNEL_NAME = "Dera Shanwari Live Alerts";

    private RealTimeNotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Real-time order updates, dispatch alerts, and admin notifications");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void showAlert(Context context, String notificationId, String title, String body, Intent launchIntent) {
        if (context == null) return;
        ensureChannel(context);
        if (!hasNotificationPermission(context)) return;

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = launchIntent != null
                ? PendingIntent.getActivity(context, Math.abs(notificationId.hashCode()), launchIntent, flags)
                : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_orders)
                .setContentTitle(title != null ? title : context.getString(R.string.app_name))
                .setContentText(body != null ? body : "")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body != null ? body : ""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(Math.abs(notificationId.hashCode()), builder.build());
        }
    }
}
