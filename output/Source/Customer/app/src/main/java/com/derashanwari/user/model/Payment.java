package com.derashanwari.user.model;

import java.io.Serializable;

public class Payment implements Serializable {
    private String id;
    private String orderId;
    private String customerId;
    private String method;
    private String accountTitle;
    private String accountNumber;
    private double amount;
    private String status;
    private long timestamp;

    public Payment() {}

    public Payment(String id, String orderId, String method, String accountTitle,
                   String accountNumber, double amount, String status, long timestamp) {
        this(id, orderId, "", method, accountTitle, accountNumber, amount, status, timestamp);
    }

    public Payment(String id, String orderId, String customerId, String method, String accountTitle,
                   String accountNumber, double amount, String status, long timestamp) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId != null ? customerId : "";
        this.method = method;
        this.accountTitle = accountTitle;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getAccountTitle() { return accountTitle; }
    public void setAccountTitle(String accountTitle) { this.accountTitle = accountTitle; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
