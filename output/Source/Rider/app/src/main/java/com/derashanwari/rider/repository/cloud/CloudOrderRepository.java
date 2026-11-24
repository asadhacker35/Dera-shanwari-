package com.derashanwari.rider.repository.cloud;

import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderEvent;
import com.derashanwari.rider.model.Payment;
import com.derashanwari.rider.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

public class CloudOrderRepository implements OrderRepository {
    private final FirestoreSyncManager sync;

    public CloudOrderRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override public List<Order> getAllOrders() { return sync.getOrders(); }

    @Override
    public List<Order> getOrdersByCustomer(String customerId) {
        List<Order> res = new ArrayList<>();
        for (Order o : sync.getOrders()) if (customerId != null && customerId.equals(o.getCustomerId())) res.add(o);
        return res;
    }

    @Override
    public List<Order> getOrdersByRider(String riderId) {
        List<Order> res = new ArrayList<>();
        for (Order o : sync.getOrders()) if (riderId != null && riderId.equals(o.getRiderId())) res.add(o);
        return res;
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
        if (status == null || "ALL".equalsIgnoreCase(status)) return getAllOrders();
        List<Order> res = new ArrayList<>();
        for (Order o : sync.getOrders()) if (status.equalsIgnoreCase(o.getOrderStatus())) res.add(o);
        return res;
    }

    @Override
    public Order getOrderById(String orderId) {
        if (orderId == null) return null;
        for (Order o : sync.getOrders()) if (orderId.equals(o.getOrderId())) return o;
        return null;
    }

    @Override public Order placeOrder(Order order) { return sync.placeOrder(order); }
    @Override public boolean updateOrderStatus(String orderId, String newStatus) { return sync.updateOrderStatus(orderId, newStatus); }
    @Override public boolean assignRider(String orderId, String riderId, String riderName) { return sync.assignRider(orderId, riderId, riderName); }

    @Override
    public boolean advanceOrderStage(String orderId, String newStatus, String eventType,
                                     String timestampKey, String userId, String userRole,
                                     String userName, String notes) {
        return sync.advanceOrderStage(orderId, newStatus, eventType, timestampKey, userId, userRole, userName, notes);
    }

    @Override
    public List<OrderEvent> getTimelineForOrder(String orderId) {
        Order o = getOrderById(orderId);
        return o != null ? new ArrayList<>(o.getTimeline()) : new ArrayList<>();
    }

    @Override public boolean recordPrint(String orderId, String printedBy) { return sync.recordOrderPrint(orderId, printedBy); }
    @Override public List<Payment> getAllPayments() { return sync.getPayments(); }
    @Override public void recordPayment(Payment payment) {}
    @Override public boolean updatePaymentStatus(String paymentId, String status) { return sync.updatePaymentStatus(paymentId, status); }
}
