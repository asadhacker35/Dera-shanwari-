package com.derashanwari.admin.repository;

import com.derashanwari.admin.model.PaymentMethodConfig;
import com.derashanwari.admin.model.RestaurantSettings;

import java.util.List;

public interface SettingsRepository {
    RestaurantSettings getRestaurantSettings();
    void saveRestaurantSettings(RestaurantSettings settings);

    List<PaymentMethodConfig> getPaymentMethods();
    List<PaymentMethodConfig> getEnabledPaymentMethods();
    void savePaymentMethod(PaymentMethodConfig config);
}
