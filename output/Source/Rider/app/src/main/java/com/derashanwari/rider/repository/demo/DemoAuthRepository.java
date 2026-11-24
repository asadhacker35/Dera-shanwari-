package com.derashanwari.rider.repository.demo;

import com.derashanwari.rider.model.AdminUser;
import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.AuthRepository;
import com.derashanwari.rider.util.ValidationUtil;

public class DemoAuthRepository implements AuthRepository {
    public static final String DEMO_OTP = "1234";
    public static final String DEMO_ADMIN_PIN = "123456";
    public static final String DEMO_RIDER_PIN = "1234";

    private final DemoDatabaseManager db;

    public DemoAuthRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public void loginAdmin(String identifier, String pinOrPassword, AuthCallback<AdminUser> callback) {
        if (!ValidationUtil.isNonEmpty(identifier) || !ValidationUtil.isNonEmpty(pinOrPassword)) {
            callback.onError("Please enter Admin email/phone and PIN.");
            return;
        }
        String idClean = identifier.trim();
        for (AdminUser admin : db.getState().admins) {
            boolean matchesId = idClean.equalsIgnoreCase(admin.getEmail())
                    || ValidationUtil.normalizePhone(idClean).equals(ValidationUtil.normalizePhone(admin.getPhone()));
            if (matchesId && DEMO_ADMIN_PIN.equals(pinOrPassword.trim())) {
                if (!admin.isActive()) {
                    callback.onError("Admin account is disabled.");
                    return;
                }
                db.setSessionAdmin(admin);
                callback.onSuccess(admin);
                return;
            }
        }
        callback.onError("Invalid Admin credentials. Demo: admin@derashanwari.pk / PIN 123456");
    }

    @Override
    public void sendCustomerOtp(String phone, AuthCallback<String> callback) {
        if (!ValidationUtil.isValidPhone(phone)) {
            callback.onError("Enter a valid Pakistani mobile number (e.g., 03009876543).");
            return;
        }
        callback.onSuccess(DEMO_OTP);
    }

    @Override
    public void verifyCustomerOtp(String phone, String otp, String name, String address, AuthCallback<Customer> callback) {
        if (!ValidationUtil.isValidPhone(phone)) {
            callback.onError("Invalid phone number.");
            return;
        }
        if (!DEMO_OTP.equals(otp != null ? otp.trim() : "")) {
            callback.onError("Invalid Demo OTP. Please enter 1234.");
            return;
        }
        String normalized = ValidationUtil.normalizePhone(phone);
        Customer existing = null;
        for (Customer c : db.getState().customers) {
            if (ValidationUtil.normalizePhone(c.getPhone()).equals(normalized)) {
                existing = c;
                break;
            }
        }
        if (existing != null) {
            if (!existing.isActive()) {
                callback.onError("Customer account has been suspended by Admin.");
                return;
            }
            if (ValidationUtil.isNonEmpty(name)) existing.setName(name.trim());
            if (ValidationUtil.isNonEmpty(address)) existing.setAddress(address.trim());
            db.save();
            db.setSessionCustomer(existing);
            callback.onSuccess(existing);
            return;
        }
        String finalName = ValidationUtil.isNonEmpty(name) ? name.trim() : "Valued Guest";
        String finalAddress = ValidationUtil.isNonEmpty(address) ? address.trim() : "Peshawar";
        Customer created = new Customer(
                "cust_" + System.currentTimeMillis(),
                finalName,
                normalized,
                finalAddress,
                true,
                50,
                System.currentTimeMillis()
        );
        db.getState().customers.add(created);
        db.save();
        db.setSessionCustomer(created);
        callback.onSuccess(created);
    }

    @Override
    public void loginRider(String phone, String pin, AuthCallback<Rider> callback) {
        if (!ValidationUtil.isValidPhone(phone)) {
            callback.onError("Enter a valid Rider phone number (e.g., 03125556677).");
            return;
        }
        if (!DEMO_RIDER_PIN.equals(pin != null ? pin.trim() : "")) {
            callback.onError("Invalid Rider PIN. Demo PIN is 1234.");
            return;
        }
        String normalized = ValidationUtil.normalizePhone(phone);
        for (Rider r : db.getState().riders) {
            if (ValidationUtil.normalizePhone(r.getPhone()).equals(normalized)) {
                if (!r.isActive()) {
                    callback.onError("Your rider account is deactivated. Contact Admin.");
                    return;
                }
                db.setSessionRider(r);
                callback.onSuccess(r);
                return;
            }
        }
        callback.onError("Rider not found. Demo Rider: 03125556677 / PIN 1234");
    }

    @Override
    public AdminUser getCurrentAdmin() {
        return db.getSessionAdmin();
    }

    @Override
    public Customer getCurrentCustomer() {
        Customer session = db.getSessionCustomer();
        if (session == null) return null;
        for (Customer c : db.getState().customers) {
            if (c.getId().equals(session.getId())) {
                return c;
            }
        }
        return session;
    }

    @Override
    public Rider getCurrentRider() {
        Rider session = db.getSessionRider();
        if (session == null) return null;
        for (Rider r : db.getState().riders) {
            if (r.getId().equals(session.getId())) {
                return r;
            }
        }
        return session;
    }

    @Override
    public void logout() {
        db.setSessionAdmin(null);
        db.setSessionCustomer(null);
        db.setSessionRider(null);
    }
}
