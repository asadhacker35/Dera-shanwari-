package com.derashanwari.rider.repository;

import com.derashanwari.rider.model.AdminUser;
import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.Rider;

public interface AuthRepository {
    interface AuthCallback<T> {
        void onSuccess(T user);
        void onError(String message);
    }

    void loginAdmin(String identifier, String pinOrPassword, AuthCallback<AdminUser> callback);
    void sendCustomerOtp(String phone, AuthCallback<String> callback);
    void verifyCustomerOtp(String phone, String otp, String name, String address, AuthCallback<Customer> callback);
    void loginRider(String phone, String pin, AuthCallback<Rider> callback);

    AdminUser getCurrentAdmin();
    Customer getCurrentCustomer();
    Rider getCurrentRider();
    void logout();
}
