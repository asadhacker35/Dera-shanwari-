package com.derashanwari.rider.repository.demo;

import com.derashanwari.rider.model.PaymentMethodConfig;
import com.derashanwari.rider.model.RestaurantSettings;
import com.derashanwari.rider.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;

public class DemoSettingsRepository implements SettingsRepository {
    private final DemoDatabaseManager db;

    public DemoSettingsRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public RestaurantSettings getRestaurantSettings() {
        return db.getState().settings;
    }

    @Override
    public void saveRestaurantSettings(RestaurantSettings settings) {
        db.getState().settings = settings;
        db.save();
    }

    @Override
    public List<PaymentMethodConfig> getPaymentMethods() {
        return new ArrayList<>(db.getState().paymentMethods);
    }

    @Override
    public List<PaymentMethodConfig> getEnabledPaymentMethods() {
        List<PaymentMethodConfig> result = new ArrayList<>();
        for (PaymentMethodConfig pm : db.getState().paymentMethods) {
            if (pm.isEnabled()) result.add(pm);
        }
        return result;
    }

    @Override
    public void savePaymentMethod(PaymentMethodConfig config) {
        List<PaymentMethodConfig> list = db.getState().paymentMethods;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getMethodId().equals(config.getMethodId())) {
                list.set(i, config);
                db.save();
                return;
            }
        }
        list.add(config);
        db.save();
    }
}
