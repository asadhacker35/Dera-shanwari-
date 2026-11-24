package com.derashanwari.rider.repository;

import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.Reward;

import java.util.List;

public interface CustomerRepository {
    List<Customer> getAllCustomers();
    Customer getCustomerById(String customerId);
    Customer getCustomerByPhone(String phone);
    void saveCustomer(Customer customer);
    void setCustomerActive(String customerId, boolean active);

    List<Reward> getRewardsForCustomer(String customerId);
    List<Reward> getRedeemableOffers();
    void addLoyaltyPoints(String customerId, int points, String title, String orderId);
    boolean redeemOffer(String customerId, Reward offer);
}
