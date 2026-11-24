package com.derashanwari.user.repository;

import com.derashanwari.user.model.Order;
import com.derashanwari.user.model.OrderEvent;
import com.derashanwari.user.model.Payment;

import java.util.List;

public interface OrderRepository {
    List<Order> getAllOrders();
    List<Order> getOrdersByCustomer(String customerId);
    List<Order> getOrdersByRider(String riderId);
    List<Order> getOrdersByStatus(String status);
    Order getOrderById(String orderId);

    Order placeOrder(Order order);
    boolean updateOrderStatus(String orderId, String newStatus);
    boolean assignRider(String orderId, String riderId, String riderName);

    boolean advanceOrderStage(String orderId, String newStatus, String eventType,
                              String timestampKey, String userId, String userRole,
                              String userName, String notes);
    List<OrderEvent> getTimelineForOrder(String orderId);
    boolean recordPrint(String orderId, String printedBy);

    List<Payment> getAllPayments();
    void recordPayment(Payment payment);
    boolean updatePaymentStatus(String paymentId, String status);
}
