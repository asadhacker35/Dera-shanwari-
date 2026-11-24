package com.example.repository.cloud;

import com.example.firebase.FirestoreSyncManager;
import com.example.model.PaymentMethodConfig;
import com.example.model.RestaurantSettings;
import com.example.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;

public class CloudSettingsRepository implements SettingsRepository {
    private final FirestoreSyncManager sync;

    public CloudSettingsRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override
    public RestaurantSettings getRestaurantSettings() {
        return sync.getSettings();
    }

    @Override
    public void saveRestaurantSettings(RestaurantSettings settings) {
        sync.saveSettings(settings);
    }

    @Override
    public List<PaymentMethodConfig> getPaymentMethods() {
        return sync.getPaymentMethods();
    }

    @Override
    public List<PaymentMethodConfig> getEnabledPaymentMethods() {
        List<PaymentMethodConfig> res = new ArrayList<>();
        for (PaymentMethodConfig pm : sync.getPaymentMethods()) {
            if (pm.isEnabled()) res.add(pm);
        }
        return res;
    }

    @Override
    public void savePaymentMethod(PaymentMethodConfig config) {
        sync.savePaymentMethod(config);
    }
}
