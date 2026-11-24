package com.example.repository.cloud;

import com.example.firebase.FirestoreSyncManager;
import com.example.model.Customer;
import com.example.model.Reward;
import com.example.repository.CustomerRepository;

import java.util.ArrayList;
import java.util.List;

public class CloudCustomerRepository implements CustomerRepository {
    private final FirestoreSyncManager sync;

    public CloudCustomerRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override
    public List<Customer> getAllCustomers() {
        return sync.getCustomers();
    }

    @Override
    public Customer getCustomerById(String customerId) {
        if (customerId == null) return null;
        Customer current = sync.getCurrentCustomer();
        if (current != null && customerId.equals(current.getId())) return current;
        for (Customer c : sync.getCustomers()) {
            if (customerId.equals(c.getId())) return c;
        }
        return null;
    }

    @Override
    public Customer getCustomerByPhone(String phone) {
        if (phone == null) return null;
        for (Customer c : sync.getCustomers()) {
            if (phone.equals(c.getPhone())) return c;
        }
        return null;
    }

    @Override
    public void saveCustomer(Customer customer) {
        sync.saveCustomer(customer);
    }

    @Override
    public void setCustomerActive(String customerId, boolean active) {
        sync.setCustomerActive(customerId, active);
    }

    @Override
    public List<Reward> getRewardsForCustomer(String customerId) {
        List<Reward> res = new ArrayList<>();
        for (Reward r : sync.getRewardHistory()) {
            if (customerId != null && customerId.equals(r.getCustomerId())) {
                res.add(r);
            }
        }
        return res;
    }

    @Override
    public List<Reward> getRedeemableOffers() {
        return sync.getRedeemableOffers();
    }

    @Override
    public void addLoyaltyPoints(String customerId, int points, String title, String orderId) {
        sync.addLoyaltyPoints(customerId, points, title, orderId);
    }

    @Override
    public boolean redeemOffer(String customerId, Reward offer) {
        return sync.redeemOffer(customerId, offer);
    }
}
