package com.derashanwari.admin.firebase;

import android.util.Log;

/**
 * Local notification / event log helper used by Robolectric test repositories.
 * Live apps dispatch real-time cross-app alerts via Firestore `notifications` + RealTimeNotificationHelper.
 */
public final class FcmNotificationBridge {
    private static final String TAG = "DeraNotifBridge";

    private FcmNotificationBridge() {}

    public static void notifyOrderStatusChanged(String orderId, String newStatus, String customerId) {
        Log.d(TAG, "Order " + orderId + " status -> " + newStatus + " (customer=" + customerId + ")");
    }

    public static void notifyRiderAssigned(String orderId, String riderId) {
        Log.d(TAG, "Rider " + riderId + " assigned to order " + orderId);
    }

    public static void notifyAdminNewOrder(String orderId, double totalAmount) {
        Log.d(TAG, "New order " + orderId + " Rs." + totalAmount);
    }
}
