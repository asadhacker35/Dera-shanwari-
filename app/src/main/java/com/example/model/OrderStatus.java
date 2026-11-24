package com.example.model;

import java.util.Arrays;
import java.util.List;

public final class OrderStatus {
    public static final String PENDING = "Pending";
    public static final String CONFIRMED = "Confirmed";
    public static final String ASSIGNED = "Assigned";
    public static final String RIDER_ACCEPTED = "Rider Accepted";
    public static final String SENT_TO_KITCHEN = "Sent to Kitchen";
    public static final String PREPARING = "Preparing";
    public static final String READY = "Ready";
    public static final String PICKED_UP = "Picked Up";
    public static final String OUT_FOR_DELIVERY = "Out for Delivery";
    public static final String DELIVERED = "Delivered";
    public static final String CANCELLED = "Cancelled";

    private OrderStatus() {}

    public static List<String> allAdminStatuses() {
        return Arrays.asList(
                PENDING, CONFIRMED, ASSIGNED, RIDER_ACCEPTED,
                SENT_TO_KITCHEN, PREPARING, READY, OUT_FOR_DELIVERY,
                DELIVERED, CANCELLED
        );
    }

    public static List<String> riderProgressionStatuses() {
        return Arrays.asList(ASSIGNED, RIDER_ACCEPTED, SENT_TO_KITCHEN, OUT_FOR_DELIVERY, DELIVERED);
    }

    public static List<String> customerTrackingSteps() {
        return Arrays.asList(
                PENDING, CONFIRMED, ASSIGNED, RIDER_ACCEPTED,
                SENT_TO_KITCHEN, PREPARING, READY, OUT_FOR_DELIVERY, DELIVERED
        );
    }

    public static boolean isCompletedOrCancelled(String status) {
        return DELIVERED.equalsIgnoreCase(status) || CANCELLED.equalsIgnoreCase(status);
    }

    public static String eventTypeForStatus(String status) {
        if (status == null) return "";
        if (PENDING.equalsIgnoreCase(status)) return OrderEvent.ORDER_PLACED;
        if (CONFIRMED.equalsIgnoreCase(status)) return OrderEvent.ORDER_CONFIRMED;
        if (ASSIGNED.equalsIgnoreCase(status) || "Assigned to Rider".equalsIgnoreCase(status)) return OrderEvent.RIDER_ASSIGNED;
        if (RIDER_ACCEPTED.equalsIgnoreCase(status)) return OrderEvent.RIDER_ACCEPTED;
        if (SENT_TO_KITCHEN.equalsIgnoreCase(status)) return OrderEvent.SENT_TO_KITCHEN;
        if (PREPARING.equalsIgnoreCase(status)) return OrderEvent.PREPARATION_STARTED;
        if (READY.equalsIgnoreCase(status)) return OrderEvent.PREPARATION_COMPLETED;
        if (OUT_FOR_DELIVERY.equalsIgnoreCase(status) || PICKED_UP.equalsIgnoreCase(status)) return OrderEvent.OUT_FOR_DELIVERY;
        if (DELIVERED.equalsIgnoreCase(status)) return OrderEvent.DELIVERED;
        if (CANCELLED.equalsIgnoreCase(status)) return OrderEvent.CANCELLED;
        return "";
    }

    public static String timestampKeyForStatus(String status) {
        return OrderEvent.timestampKeyForEvent(eventTypeForStatus(status));
    }

    public static boolean canRiderTransition(String currentStatus, String targetStatus) {
        if (targetStatus == null || currentStatus == null) return false;
        if (isCompletedOrCancelled(currentStatus)) return false;
        if (RIDER_ACCEPTED.equalsIgnoreCase(targetStatus)) {
            return ASSIGNED.equalsIgnoreCase(currentStatus) || "Assigned to Rider".equalsIgnoreCase(currentStatus);
        }
        if (SENT_TO_KITCHEN.equalsIgnoreCase(targetStatus)) {
            return ASSIGNED.equalsIgnoreCase(currentStatus)
                    || "Assigned to Rider".equalsIgnoreCase(currentStatus)
                    || RIDER_ACCEPTED.equalsIgnoreCase(currentStatus);
        }
        if (OUT_FOR_DELIVERY.equalsIgnoreCase(targetStatus)) {
            return RIDER_ACCEPTED.equalsIgnoreCase(currentStatus)
                    || SENT_TO_KITCHEN.equalsIgnoreCase(currentStatus)
                    || PREPARING.equalsIgnoreCase(currentStatus)
                    || READY.equalsIgnoreCase(currentStatus)
                    || PICKED_UP.equalsIgnoreCase(currentStatus);
        }
        if (DELIVERED.equalsIgnoreCase(targetStatus)) {
            return OUT_FOR_DELIVERY.equalsIgnoreCase(currentStatus)
                    || READY.equalsIgnoreCase(currentStatus)
                    || PICKED_UP.equalsIgnoreCase(currentStatus);
        }
        return false;
    }

    public static String nextRiderStatus(String currentStatus) {
        if (currentStatus == null || isCompletedOrCancelled(currentStatus)) return null;
        if (ASSIGNED.equalsIgnoreCase(currentStatus) || "Assigned to Rider".equalsIgnoreCase(currentStatus)) {
            return RIDER_ACCEPTED;
        }
        if (RIDER_ACCEPTED.equalsIgnoreCase(currentStatus)) {
            return SENT_TO_KITCHEN;
        }
        if (SENT_TO_KITCHEN.equalsIgnoreCase(currentStatus)
                || PREPARING.equalsIgnoreCase(currentStatus)
                || READY.equalsIgnoreCase(currentStatus)
                || PICKED_UP.equalsIgnoreCase(currentStatus)) {
            return OUT_FOR_DELIVERY;
        }
        if (OUT_FOR_DELIVERY.equalsIgnoreCase(currentStatus)) {
            return DELIVERED;
        }
        return null;
    }

    public static String nextRiderActionLabel(String currentStatus) {
        String next = nextRiderStatus(currentStatus);
        if (next == null) return "Order Completed";
        if (RIDER_ACCEPTED.equals(next)) return "Accept Order";
        if (SENT_TO_KITCHEN.equals(next)) return "Send to Kitchen";
        if (OUT_FOR_DELIVERY.equals(next)) return "Mark Out for Delivery";
        if (DELIVERED.equals(next)) return "Mark Delivered";
        return "Update Status";
    }
}
