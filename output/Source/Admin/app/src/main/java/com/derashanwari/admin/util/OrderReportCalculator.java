package com.derashanwari.admin.util;

import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderEvent;
import com.derashanwari.admin.model.OrderStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes real operational performance metrics from live/saved Order timestamps.
 * Never fabricates or hardcodes metrics; stages without samples yield 0ms.
 */
public final class OrderReportCalculator {
    public static final long DELAYED_THRESHOLD_MS = 45L * 60L * 1000L;

    public static class OrderReportSummary {
        public int totalOrders;
        public int completedOrders;
        public int pendingOrders;
        public int cancelledOrders;
        public int delayedOrders;

        public long avgConfirmMs;
        public long avgRiderAcceptMs;
        public long avgKitchenPrepMs;
        public long avgDeliveryMs;
        public long avgTotalCompletionMs;
        public double totalRevenue;
    }

    private OrderReportCalculator() {}

    public static List<Order> filterByDateRange(List<Order> allOrders, long startInclusive, long endInclusive) {
        List<Order> filtered = new ArrayList<>();
        if (allOrders == null) return filtered;
        for (Order o : allOrders) {
            if (o == null) continue;
            long placed = o.getPlacedTimestamp();
            if (TimeFormatUtil.isWithinRange(placed, startInclusive, endInclusive)) {
                filtered.add(o);
            }
        }
        return filtered;
    }

    public static OrderReportSummary calculate(List<Order> orders, long nowMillis) {
        OrderReportSummary summary = new OrderReportSummary();
        if (orders == null || orders.isEmpty()) return summary;

        long confirmSum = 0; int confirmCnt = 0;
        long acceptSum = 0; int acceptCnt = 0;
        long prepSum = 0; int prepCnt = 0;
        long delivSum = 0; int delivCnt = 0;
        long totalSum = 0; int totalCnt = 0;

        for (Order o : orders) {
            if (o == null) continue;
            summary.totalOrders++;

            String status = o.getOrderStatus();
            if (OrderStatus.DELIVERED.equalsIgnoreCase(status)) {
                summary.completedOrders++;
                summary.totalRevenue += o.getTotalAmount();
            } else if (OrderStatus.CANCELLED.equalsIgnoreCase(status)) {
                summary.cancelledOrders++;
            } else {
                summary.pendingOrders++;
            }

            long placed = o.getPlacedTimestamp();
            long confirmed = o.getTimestampForKey(OrderEvent.TS_CONFIRMED);
            long assigned = o.getTimestampForKey(OrderEvent.TS_RIDER_ASSIGNED);
            long accepted = o.getTimestampForKey(OrderEvent.TS_RIDER_ACCEPTED);
            long sentKitchen = o.getTimestampForKey(OrderEvent.TS_SENT_TO_KITCHEN);
            long prepStart = o.getTimestampForKey(OrderEvent.TS_PREP_STARTED);
            long prepDone = o.getTimestampForKey(OrderEvent.TS_PREP_COMPLETED);
            long outDeliv = o.getTimestampForKey(OrderEvent.TS_OUT_FOR_DELIVERY);
            long delivered = o.getTimestampForKey(OrderEvent.TS_DELIVERED);

            if (placed > 0 && confirmed >= placed) {
                confirmSum += (confirmed - placed);
                confirmCnt++;
            }
            if (assigned > 0 && accepted >= assigned) {
                acceptSum += (accepted - assigned);
                acceptCnt++;
            }
            long kitchenStart = prepStart > 0 ? prepStart : sentKitchen;
            if (kitchenStart > 0 && prepDone >= kitchenStart) {
                prepSum += (prepDone - kitchenStart);
                prepCnt++;
            }
            if (outDeliv > 0 && delivered >= outDeliv) {
                delivSum += (delivered - outDeliv);
                delivCnt++;
            }
            if (placed > 0 && delivered >= placed) {
                long duration = delivered - placed;
                totalSum += duration;
                totalCnt++;
                if (duration > DELAYED_THRESHOLD_MS) summary.delayedOrders++;
            } else if (!OrderStatus.isCompletedOrCancelled(status)
                    && placed > 0 && (nowMillis - placed) > DELAYED_THRESHOLD_MS) {
                summary.delayedOrders++;
            }
        }

        summary.avgConfirmMs = confirmCnt > 0 ? confirmSum / confirmCnt : 0L;
        summary.avgRiderAcceptMs = acceptCnt > 0 ? acceptSum / acceptCnt : 0L;
        summary.avgKitchenPrepMs = prepCnt > 0 ? prepSum / prepCnt : 0L;
        summary.avgDeliveryMs = delivCnt > 0 ? delivSum / delivCnt : 0L;
        summary.avgTotalCompletionMs = totalCnt > 0 ? totalSum / totalCnt : 0L;
        return summary;
    }
}
