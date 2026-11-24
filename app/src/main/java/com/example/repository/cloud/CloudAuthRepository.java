package com.example.repository.cloud;

import android.content.Context;

import com.example.firebase.FirebaseConfig;
import com.example.firebase.FirestoreSyncManager;
import com.example.model.AdminUser;
import com.example.model.Customer;
import com.example.model.Rider;
import com.example.repository.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

public class CloudAuthRepository implements AuthRepository {
    private final Context appContext;
    private final FirestoreSyncManager sync;

    public CloudAuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.sync = FirestoreSyncManager.getInstance(appContext);
    }

    @Override
    public void loginAdmin(String identifier, String pinOrPassword, AuthCallback<AdminUser> callback) {
        callback.onError("Please sign in with your authorized Google account.");
    }

    @Override
    public void sendCustomerOtp(String phone, AuthCallback<String> callback) {
        callback.onError("Use Google Sign-In for secure authentication.");
    }

    @Override
    public void verifyCustomerOtp(String phone, String otp, String name, String address, AuthCallback<Customer> callback) {
        callback.onError("Use Google Sign-In for secure authentication.");
    }

    @Override
    public void loginRider(String phone, String pin, AuthCallback<Rider> callback) {
        callback.onError("Please sign in with your Google account.");
    }

    @Override
    public AdminUser getCurrentAdmin() {
        AdminUser current = sync.getCurrentAdmin();
        if (current != null) return current;
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fbUser == null) return null;
        return new AdminUser(
                fbUser.getUid(),
                fbUser.getDisplayName() != null ? fbUser.getDisplayName() : "Dera Admin",
                fbUser.getEmail() != null ? fbUser.getEmail() : "",
                "",
                "Admin",
                true
        );
    }

    @Override
    public Customer getCurrentCustomer() {
        Customer current = sync.getCurrentCustomer();
        if (current != null) return current;
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fbUser == null) return null;
        return new Customer(
                fbUser.getUid(),
                fbUser.getDisplayName() != null ? fbUser.getDisplayName() : "Valued Customer",
                fbUser.getEmail() != null ? fbUser.getEmail() : "",
                "",
                "",
                true,
                0,
                System.currentTimeMillis()
        );
    }

    @Override
    public Rider getCurrentRider() {
        Rider current = sync.getCurrentRider();
        if (current != null) return current;
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fbUser == null) return null;
        return new Rider(
                fbUser.getUid(),
                fbUser.getDisplayName() != null ? fbUser.getDisplayName() : "Dera Rider",
                "",
                "",
                "",
                true,
                0,
                0.0
        );
    }

    @Override
    public void logout() {
        sync.stopSync();
        FirebaseConfig.getAuth(appContext).signOut();
    }
}
