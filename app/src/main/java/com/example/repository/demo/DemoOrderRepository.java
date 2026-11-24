package com.example.repository.demo;

import com.example.firebase.FcmNotificationBridge;
import com.example.model.Customer;
import com.example.model.Order;
import com.example.model.OrderEvent;
import com.example.model.OrderStatus;
import com.example.model.Payment;
import com.example.model.PaymentMethodConfig;
import com.example.model.RestaurantSettings;
import com.example.model.Reward;
import com.example.model.Rider;
import com.example.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

public class DemoOrderRepository implements OrderRepository {
    private final DemoDatabaseManager db;

    public DemoOrderRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Order> getAllOrders() {
        return new ArrayList<>(db.getState().orders);
    }

    @Override
    public List<Order> getOrdersByCustomer(String customerId) {
        List<Order> result = new ArrayList<>();
        for (Order o : db.getState().orders) {
            if (customerId != null && customerId.equals(o.getCustomerId())) {
                result.add(o);
            }
        }
        return result;
    }

    @Override
    public List<Order> getOrdersByRider(String riderId) {
        List<Order> result = new ArrayList<>();
        for (Order o : db.getState().orders) {
            if (riderId != null && riderId.equals(o.getRiderId())) {
                result.add(o);
            }
        }
        return result;
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
        if (status == null || "ALL".equalsIgnoreCase(status)) {
            return getAllOrders();
        }
        List<Order> result = new ArrayList<>();
        for (Order o : db.getState().orders) {
            if (status.equalsIgnoreCase(o.getOrderStatus())) {
                result.add(o);
            }
        }
        return result;
    }

    @Override
    public Order getOrderById(String orderId) {
        for (Order o : db.getState().orders) {
            if (o.getOrderId().equals(orderId)) return o;
        }
        return null;
    }

    @Override
    public Order placeOrder(Order order) {
        DemoDatabaseManager.DemoState state = db.getState();
        long now = System.currentTimeMillis();
        String id = "ORD-" + state.nextOrderSeq++;
        order.setOrderId(id);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.putTimestampIfAbsent(OrderEvent.TS_PLACED, now);
        order.addTimelineEventIfAbsent(new OrderEvent(
                OrderEvent.ORDER_PLACED, id, OrderEvent.ORDER_PLACED, now,
                order.getCustomerId() != null ? order.getCustomerId() : "cust_1",
                "CUSTOMER",
                order.getCustomerName() != null ? order.getCustomerName() : "Customer",
                "Order placed"
        ));

        if (PaymentMethodConfig.METHOD_COD.equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("Pending");
        } else {
            order.setPaymentStatus("Pending Verification");
        }

        RestaurantSettings settings = state.settings;
        int rate = settings != null && settings.getPointsPerHundredRs() > 0
                ? settings.getPointsPerHundredRs() : 10;
        int pts = (int) ((order.getTotalAmount() / 100.0) * rate);
        order.setPointsEarned(Math.max(10, pts));

        state.orders.add(0, order);

        String acctTitle = order.getPaymentMethod();
        String acctNum = "-";
        for (PaymentMethodConfig pm : state.paymentMethods) {
            if (pm.getDisplayName().equalsIgnoreCase(order.getPaymentMethod())) {
                acctTitle = pm.getAccountTitle();
                acctNum = pm.getAccountNumber();
                break;
            }
        }
        state.payments.add(0, new Payment(
                "pay_" + now, id, order.getPaymentMethod(),
                acctTitle, acctNum, order.getTotalAmount(),
                order.getPaymentStatus(), now
        ));

        db.save();
        FcmNotificationBridge.notifyAdminNewOrder(id, order.getTotalAmount());
        return order;
    }

    @Override
    public boolean updateOrderStatus(String orderId, String newStatus) {
        String evt = OrderStatus.eventTypeForStatus(newStatus);
        String tsKey = OrderStatus.timestampKeyForStatus(newStatus);
        return advanceOrderStage(orderId, newStatus, evt, tsKey, "staff", "ADMIN", "Dera Admin", "");
    }

    @Override
    public boolean advanceOrderStage(String orderId, String newStatus, String eventType,
                                     String timestampKey, String userId, String userRole,
                                     String userName, String notes) {
        Order target = getOrderById(orderId);
        if (target == null) return false;

        long now = System.currentTimeMillis();
        String oldStatus = target.getOrderStatus();
        if (newStatus != null && !newStatus.isEmpty()) {
            target.setOrderStatus(newStatus);
        }
        target.setUpdatedAt(now);
        if (timestampKey != null && !timestampKey.isEmpty()) {
            target.putTimestampIfAbsent(timestampKey, now);
        }
        if (eventType != null && !eventType.isEmpty()) {
            target.addTimelineEventIfAbsent(new OrderEvent(
                    eventType, orderId, eventType, now,
                    userId != null ? userId : "", userRole != null ? userRole : "ADMIN",
                    userName != null ? userName : "Staff", notes
            ));
        }

        if (OrderStatus.DELIVERED.equalsIgnoreCase(newStatus)
                && !OrderStatus.DELIVERED.equalsIgnoreCase(oldStatus)) {
            target.setPaymentStatus("Paid");
            for (Payment p : db.getState().payments) {
                if (p.getOrderId().equals(orderId)) {
                    p.setStatus("Paid");
                }
            }
            if (target.getRiderId() != null) {
                for (Rider r : db.getState().riders) {
                    if (r.getId().equals(target.getRiderId())) {
                        r.setTotalDeliveries(r.getTotalDeliveries() + 1);
                        r.setTotalEarnings(r.getTotalEarnings() + Math.max(150.0, target.getDeliveryFee()));
                        break;
                    }
                }
            }
            if (target.getCustomerId() != null && target.getPointsEarned() > 0) {
                for (Customer c : db.getState().customers) {
                    if (c.getId().equals(target.getCustomerId())) {
                        c.setLoyaltyPoints(c.getLoyaltyPoints() + target.getPointsEarned());
                        db.getState().rewards.add(0, new Reward(
                                "rew_" + System.currentTimeMillis(),
                                c.getId(),
                                "Order " + orderId + " Delivered",
                                "Earned loyalty points for completed order",
                                target.getPointsEarned(),
                                "EARNED",
                                false,
                                0,
                                orderId,
                                System.currentTimeMillis()
                        ));
                        break;
                    }
                }
            }
        }

        db.save();
        if (newStatus != null && !newStatus.isEmpty()) {
            FcmNotificationBridge.notifyOrderStatusChanged(orderId, newStatus, target.getCustomerId());
        }
        return true;
    }

    @Override
    public List<OrderEvent> getTimelineForOrder(String orderId) {
        Order o = getOrderById(orderId);
        return o != null ? new ArrayList<>(o.getTimeline()) : new ArrayList<>();
    }

    @Override
    public boolean recordPrint(String orderId, String printedBy) {
        Order target = getOrderById(orderId);
        if (target == null) return false;
        long now = System.currentTimeMillis();
        target.setPrintCount(target.getPrintCount() + 1);
        target.setLastPrintedAt(now);
        target.setLastPrintedBy(printedBy != null ? printedBy : "Dera Admin");
        db.save();
        return true;
    }

    @Override
    public boolean submitRating(String orderId, int rating, String reviewComment) {
        Order target = getOrderById(orderId);
        if (target == null) return false;
        target.setRating(rating);
        target.setReviewComment(reviewComment != null ? reviewComment : "");
        target.setUpdatedAt(System.currentTimeMillis());
        db.save();
        return true;
    }

    @Override
    public boolean assignRider(String orderId, String riderId, String riderName) {
        Order target = getOrderById(orderId);
        if (target == null) return false;
        long now = System.currentTimeMillis();
        target.setRiderId(riderId);
        target.setRiderName(riderName);
        if (!OrderStatus.OUT_FOR_DELIVERY.equalsIgnoreCase(target.getOrderStatus())
                && !OrderStatus.DELIVERED.equalsIgnoreCase(target.getOrderStatus())) {
            target.setOrderStatus(OrderStatus.ASSIGNED);
        }
        target.putTimestampIfAbsent(OrderEvent.TS_RIDER_ASSIGNED, now);
        target.addTimelineEventIfAbsent(new OrderEvent(
                OrderEvent.RIDER_ASSIGNED, orderId, OrderEvent.RIDER_ASSIGNED, now,
                "admin_1", "ADMIN", "Dera Admin", "Assigned to " + riderName
        ));
        target.setUpdatedAt(now);
        db.save();
        FcmNotificationBridge.notifyRiderAssigned(orderId, riderId);
        return true;
    }

    @Override
    public List<Payment> getAllPayments() {
        return new ArrayList<>(db.getState().payments);
    }

    @Override
    public void recordPayment(Payment payment) {
        db.getState().payments.add(0, payment);
        db.save();
    }

    @Override
    public boolean updatePaymentStatus(String paymentId, String status) {
        for (Payment p : db.getState().payments) {
            if (p.getId().equals(paymentId)) {
                p.setStatus(status);
                Order o = getOrderById(p.getOrderId());
                if (o != null) o.setPaymentStatus(status);
                db.save();
                return true;
            }
        }
        return false;
    }
}
