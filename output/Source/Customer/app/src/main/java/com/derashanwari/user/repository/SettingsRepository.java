package com.derashanwari.user.repository;

import com.derashanwari.user.model.PaymentMethodConfig;
import com.derashanwari.user.model.RestaurantSettings;

import java.util.List;

public interface SettingsRepository {
    RestaurantSettings getRestaurantSettings();
    void saveRestaurantSettings(RestaurantSettings settings);

    List<PaymentMethodConfig> getPaymentMethods();
    List<PaymentMethodConfig> getEnabledPaymentMethods();
    void savePaymentMethod(PaymentMethodConfig config);
}
