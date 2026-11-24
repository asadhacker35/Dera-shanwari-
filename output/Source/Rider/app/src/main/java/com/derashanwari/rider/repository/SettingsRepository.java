package com.derashanwari.rider.repository;

import com.derashanwari.rider.model.PaymentMethodConfig;
import com.derashanwari.rider.model.RestaurantSettings;

import java.util.List;

public interface SettingsRepository {
    RestaurantSettings getRestaurantSettings();
    void saveRestaurantSettings(RestaurantSettings settings);

    List<PaymentMethodConfig> getPaymentMethods();
    List<PaymentMethodConfig> getEnabledPaymentMethods();
    void savePaymentMethod(PaymentMethodConfig config);
}
