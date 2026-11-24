package com.example.repository;

import com.example.model.PaymentMethodConfig;
import com.example.model.RestaurantSettings;

import java.util.List;

public interface SettingsRepository {
    RestaurantSettings getRestaurantSettings();
    void saveRestaurantSettings(RestaurantSettings settings);

    List<PaymentMethodConfig> getPaymentMethods();
    List<PaymentMethodConfig> getEnabledPaymentMethods();
    void savePaymentMethod(PaymentMethodConfig config);
}
