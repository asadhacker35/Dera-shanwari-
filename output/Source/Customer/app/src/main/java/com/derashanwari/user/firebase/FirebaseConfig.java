package com.derashanwari.user.firebase;

import android.content.Context;

import com.derashanwari.user.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Centralized Cloud Firestore & FirebaseAuth configuration shared across Admin, Customer, and Rider apps.
 * Always connects to the named Firestore Enterprise database via R.string.firestore_database_id.
 */
public final class FirebaseConfig {
    public static final boolean DEMO_MODE = false;
    public static final String BOOTSTRAPPED_ADMIN_EMAIL = "asadhacker770@gmail.com";

    public static final String COL_ADMINS = "admins";
    public static final String COL_CUSTOMERS = "customers";
    public static final String COL_RIDERS = "riders";
    public static final String COL_CATEGORIES = "categories";
    public static final String COL_PRODUCTS = "products";
    public static final String COL_BANNERS = "banners";
    public static final String COL_ORDERS = "orders";
    public static final String SUB_TIMELINE = "timeline";
    public static final String COL_PAYMENTS = "payments";
    public static final String COL_SETTINGS = "settings";
    public static final String DOC_RESTAURANT_SETTINGS = "restaurant";
    public static final String COL_PAYMENT_METHODS = "payment_methods";
    public static final String COL_CHATS = "chats";
    public static final String COL_CHAT_ROOMS = "chatRooms";
    public static final String DOC_MAIN_GROUP = "mainGroup";
    public static final String SUB_MESSAGES = "messages";
    public static final String SUB_MEMBERS = "members";
    public static final String COL_NOTIFICATIONS = "notifications";
    public static final String COL_REWARDS = "rewards";

    private FirebaseConfig() {}

    public static FirebaseFirestore getFirestore(Context context) {
        ensureInitialized(context);
        String databaseId = context.getString(R.string.firestore_database_id);
        return FirebaseFirestore.getInstance(databaseId);
    }

    public static FirebaseAuth getAuth(Context context) {
        ensureInitialized(context);
        return FirebaseAuth.getInstance();
    }

    public static void ensureInitialized(Context context) {
        Context appCtx = context.getApplicationContext();
        if (FirebaseApp.getApps(appCtx).isEmpty()) {
            FirebaseApp.initializeApp(appCtx);
        }
    }
}
