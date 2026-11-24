package com.example.repository;

import android.content.Context;

import com.example.firebase.FirebaseConfig;
import com.example.firebase.FirestoreSyncManager;
import com.example.repository.cloud.CloudAuthRepository;
import com.example.repository.cloud.CloudChatRepository;
import com.example.repository.cloud.CloudCustomerRepository;
import com.example.repository.cloud.CloudOrderRepository;
import com.example.repository.cloud.CloudProductRepository;
import com.example.repository.cloud.CloudRiderRepository;
import com.example.repository.cloud.CloudSettingsRepository;
import com.example.repository.demo.DemoAuthRepository;
import com.example.repository.demo.DemoChatRepository;
import com.example.repository.demo.DemoCustomerRepository;
import com.example.repository.demo.DemoDatabaseManager;
import com.example.repository.demo.DemoOrderRepository;
import com.example.repository.demo.DemoProductRepository;
import com.example.repository.demo.DemoRiderRepository;
import com.example.repository.demo.DemoSettingsRepository;

/**
 * Central service locator for all repositories.
 * Supplies live Cloud Firestore repositories in production APKs and allows Robolectric tests
 * to opt into fast offline in-memory repositories via enableTestModeForRobolectric().
 */
public final class RepositoryProvider {
    private static boolean testMode = false;

    private static AuthRepository authRepository;
    private static ProductRepository productRepository;
    private static OrderRepository orderRepository;
    private static RiderRepository riderRepository;
    private static CustomerRepository customerRepository;
    private static SettingsRepository settingsRepository;
    private static ChatRepository chatRepository;

    private RepositoryProvider() {}

    public static synchronized void enableTestModeForRobolectric() {
        testMode = true;
        resetForTests();
    }

    public static synchronized boolean isTestMode() {
        return testMode;
    }

    public static synchronized void init(Context context) {
        if (testMode) {
            DemoDatabaseManager.getInstance(context.getApplicationContext());
        } else {
            FirebaseConfig.ensureInitialized(context.getApplicationContext());
            FirestoreSyncManager.getInstance(context.getApplicationContext());
        }
    }

    public static FirestoreSyncManager sync(Context context) {
        return FirestoreSyncManager.getInstance(context.getApplicationContext());
    }

    public static synchronized AuthRepository auth(Context context) {
        if (authRepository == null) {
            authRepository = testMode
                    ? new DemoAuthRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudAuthRepository(context);
        }
        return authRepository;
    }

    public static synchronized ProductRepository products(Context context) {
        if (productRepository == null) {
            productRepository = testMode
                    ? new DemoProductRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudProductRepository(FirestoreSyncManager.getInstance(context));
        }
        return productRepository;
    }

    public static synchronized OrderRepository orders(Context context) {
        if (orderRepository == null) {
            orderRepository = testMode
                    ? new DemoOrderRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudOrderRepository(FirestoreSyncManager.getInstance(context));
        }
        return orderRepository;
    }

    public static synchronized RiderRepository riders(Context context) {
        if (riderRepository == null) {
            riderRepository = testMode
                    ? new DemoRiderRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudRiderRepository(FirestoreSyncManager.getInstance(context));
        }
        return riderRepository;
    }

    public static synchronized CustomerRepository customers(Context context) {
        if (customerRepository == null) {
            customerRepository = testMode
                    ? new DemoCustomerRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudCustomerRepository(FirestoreSyncManager.getInstance(context));
        }
        return customerRepository;
    }

    public static synchronized SettingsRepository settings(Context context) {
        if (settingsRepository == null) {
            settingsRepository = testMode
                    ? new DemoSettingsRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudSettingsRepository(FirestoreSyncManager.getInstance(context));
        }
        return settingsRepository;
    }

    public static synchronized ChatRepository chat(Context context) {
        if (chatRepository == null) {
            chatRepository = testMode
                    ? new DemoChatRepository(DemoDatabaseManager.getInstance(context))
                    : new CloudChatRepository(FirestoreSyncManager.getInstance(context));
        }
        return chatRepository;
    }

    public static synchronized void resetForTests() {
        authRepository = null;
        productRepository = null;
        orderRepository = null;
        riderRepository = null;
        customerRepository = null;
        settingsRepository = null;
        chatRepository = null;
    }
}
