package com.derashanwari.rider.repository.demo;

import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.Reward;
import com.derashanwari.rider.repository.CustomerRepository;
import com.derashanwari.rider.util.ValidationUtil;

import java.util.ArrayList;
import java.util.List;

public class DemoCustomerRepository implements CustomerRepository {
    private final DemoDatabaseManager db;

    public DemoCustomerRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Customer> getAllCustomers() {
        return new ArrayList<>(db.getState().customers);
    }

    @Override
    public Customer getCustomerById(String customerId) {
        for (Customer c : db.getState().customers) {
            if (c.getId().equals(customerId)) return c;
        }
        return null;
    }

    @Override
    public Customer getCustomerByPhone(String phone) {
        String normalized = ValidationUtil.normalizePhone(phone);
        for (Customer c : db.getState().customers) {
            if (ValidationUtil.normalizePhone(c.getPhone()).equals(normalized)) return c;
        }
        return null;
    }

    @Override
    public void saveCustomer(Customer customer) {
        List<Customer> list = db.getState().customers;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(customer.getId())) {
                list.set(i, customer);
                db.setSessionCustomer(customer);
                db.save();
                return;
            }
        }
        list.add(customer);
        db.save();
    }

    @Override
    public void setCustomerActive(String customerId, boolean active) {
        Customer c = getCustomerById(customerId);
        if (c != null) {
            c.setActive(active);
            db.save();
        }
    }

    @Override
    public List<Reward> getRewardsForCustomer(String customerId) {
        List<Reward> result = new ArrayList<>();
        for (Reward r : db.getState().rewards) {
            if (customerId != null && customerId.equals(r.getCustomerId())) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<Reward> getRedeemableOffers() {
        return new ArrayList<>(db.getState().redeemableOffers);
    }

    @Override
    public void addLoyaltyPoints(String customerId, int points, String title, String orderId) {
        Customer c = getCustomerById(customerId);
        if (c != null) {
            c.setLoyaltyPoints(c.getLoyaltyPoints() + points);
            db.getState().rewards.add(0, new Reward(
                    "rew_" + System.currentTimeMillis(),
                    customerId,
                    title,
                    "Points credited",
                    points,
                    "EARNED",
                    false,
                    0,
                    orderId,
                    System.currentTimeMillis()
            ));
            db.save();
        }
    }

    @Override
    public boolean redeemOffer(String customerId, Reward offer) {
        Customer c = getCustomerById(customerId);
        if (c == null || offer == null || c.getLoyaltyPoints() < offer.getMinPointsRequired()) {
            return false;
        }
        c.setLoyaltyPoints(c.getLoyaltyPoints() - offer.getMinPointsRequired());
        db.getState().rewards.add(0, new Reward(
                "rew_" + System.currentTimeMillis(),
                customerId,
                "Redeemed: " + offer.getTitle(),
                offer.getDescription(),
                -offer.getMinPointsRequired(),
                "REDEEMED",
                false,
                offer.getMinPointsRequired(),
                "",
                System.currentTimeMillis()
        ));
        db.setSessionCustomer(c);
        db.save();
        return true;
    }
}
