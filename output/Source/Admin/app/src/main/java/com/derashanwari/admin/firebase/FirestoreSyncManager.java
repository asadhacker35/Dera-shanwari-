package com.derashanwari.admin.firebase;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.derashanwari.admin.MainActivity;
import com.derashanwari.admin.config.ImageKitConfig;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.model.Banner;
import com.derashanwari.admin.model.Category;
import com.derashanwari.admin.model.ChatMessage;
import com.derashanwari.admin.model.Customer;
import com.derashanwari.admin.model.GroupChatMember;
import com.derashanwari.admin.model.GroupChatMessage;
import com.derashanwari.admin.model.NotificationItem;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderEvent;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.model.Payment;
import com.derashanwari.admin.model.PaymentMethodConfig;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.model.ProductVariant;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.model.Reward;
import com.derashanwari.admin.model.Rider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time Cloud Firestore synchronization engine shared across Admin, Customer, and Rider apps.
 * All Firestore reads/writes are gated behind FirebaseAuth Google Sign-In and use named database_id.
 */
public class FirestoreSyncManager {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_RIDER = "RIDER";

    public interface DataChangeListener {
        void onDataChanged();
    }

    public interface AlertListener {
        void onNewNotification(NotificationItem item);
    }

    public interface TimelineListener {
        void onTimelineUpdated(List<OrderEvent> events);
    }

    private static FirestoreSyncManager instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ListenerRegistration> activeListeners = new ArrayList<>();
    private final List<DataChangeListener> uiListeners = new CopyOnWriteArrayList<>();
    private final List<AlertListener> alertListeners = new CopyOnWriteArrayList<>();
    private final Set<String> seenNotifIds = new HashSet<>();

    private String currentRole = "";
    private String currentUid = "";
    private boolean notificationsPrimed = false;
    private boolean catalogSeeded = false;

    private final List<Product> products = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<Rider> riders = new ArrayList<>();
    private final List<AdminUser> admins = new ArrayList<>();
    private final List<PaymentMethodConfig> paymentMethods = new ArrayList<>();
    private final List<ChatMessage> chats = new ArrayList<>();
    private final List<GroupChatMessage> groupMessages = new ArrayList<>();
    private final List<GroupChatMember> groupMembers = new ArrayList<>();
    private long myGroupLastReadAt = 0L;
    private final List<Reward> rewardHistory = new ArrayList<>();
    private final List<Reward> redeemableOffers = new ArrayList<>();
    private final List<NotificationItem> notifications = new ArrayList<>();
    private final Set<String> persistedCustomerIds = new HashSet<>();
    private final Set<String> persistedRiderIds = new HashSet<>();
    private RestaurantSettings settings = defaultSettings();

    private AdminUser currentAdmin;
    private Customer currentCustomer;
    private Rider currentRider;

    private FirestoreSyncManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized FirestoreSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new FirestoreSyncManager(context);
        }
        return instance;
    }

    public void addDataChangeListener(DataChangeListener l) {
        if (l != null && !uiListeners.contains(l)) uiListeners.add(l);
    }

    public void removeDataChangeListener(DataChangeListener l) {
        uiListeners.remove(l);
    }

    public void addAlertListener(AlertListener l) {
        if (l != null && !alertListeners.contains(l)) alertListeners.add(l);
    }

    public void removeAlertListener(AlertListener l) {
        alertListeners.remove(l);
    }

    private void notifyUi() {
        mainHandler.post(() -> {
            for (DataChangeListener l : uiListeners) {
                l.onDataChanged();
            }
        });
    }

    public synchronized void startSync(String role, String uid) {
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fbUser == null || uid == null || uid.isEmpty()) return;
        if (role.equals(currentRole) && uid.equals(currentUid) && !activeListeners.isEmpty()) return;

        stopSync();
        currentRole = role;
        currentUid = uid;
        notificationsPrimed = false;
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);

        // Shared catalog & config streams
        activeListeners.add(db.collection(FirebaseConfig.COL_CATEGORIES)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", FirebaseConfig.COL_CATEGORIES, err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            categories.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) categories.add(FirestoreMapper.toCategory(d));
                            categories.sort(Comparator.comparingInt(Category::getSortOrder));
                        }
                        notifyUi();
                    }
                }));

        activeListeners.add(db.collection(FirebaseConfig.COL_PRODUCTS)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", FirebaseConfig.COL_PRODUCTS, err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            products.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) products.add(FirestoreMapper.toProduct(d));
                            products.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        }
                        if (ROLE_ADMIN.equals(currentRole) && products.isEmpty() && !catalogSeeded) {
                            catalogSeeded = true;
                            seedDefaultCatalogToFirestore();
                        }
                        notifyUi();
                    }
                }));

        activeListeners.add(db.collection(FirebaseConfig.COL_BANNERS)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", FirebaseConfig.COL_BANNERS, err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            banners.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) banners.add(FirestoreMapper.toBanner(d));
                            banners.sort(Comparator.comparingInt(Banner::getSortOrder));
                        }
                        notifyUi();
                    }
                }));

        activeListeners.add(db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_RESTAURANT_SETTINGS)
                .addSnapshotListener((doc, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("get", "settings/restaurant", err);
                        return;
                    }
                    if (doc != null && doc.exists()) {
                        synchronized (FirestoreSyncManager.this) {
                            settings = FirestoreMapper.toSettings(doc);
                        }
                        notifyUi();
                    }
                }));

        activeListeners.add(db.collection(FirebaseConfig.COL_PAYMENT_METHODS)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", FirebaseConfig.COL_PAYMENT_METHODS, err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            paymentMethods.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) paymentMethods.add(FirestoreMapper.toPaymentMethod(d));
                        }
                        notifyUi();
                    }
                }));

        // Rewards: Admins read all; Customers read offers + own earned/redeemed history
        if (ROLE_ADMIN.equals(role)) {
            activeListeners.add(db.collection(FirebaseConfig.COL_REWARDS).addSnapshotListener((snap, err) -> {
                if (err != null) {
                    FirestoreErrorLogger.logError("list", FirebaseConfig.COL_REWARDS, err);
                    return;
                }
                if (snap != null) parseRewards(snap.getDocuments());
            }));
        } else if (ROLE_CUSTOMER.equals(role)) {
            activeListeners.add(db.collection(FirebaseConfig.COL_REWARDS)
                    .whereEqualTo("type", "REDEEMABLE_OFFER")
                    .addSnapshotListener((snap, err) -> {
                        if (err != null) {
                            FirestoreErrorLogger.logError("list", "rewards/offers", err);
                            return;
                        }
                        if (snap != null) {
                            synchronized (FirestoreSyncManager.this) {
                                redeemableOffers.clear();
                                for (DocumentSnapshot d : snap.getDocuments()) redeemableOffers.add(FirestoreMapper.toReward(d));
                            }
                            notifyUi();
                        }
                    }));
            activeListeners.add(db.collection(FirebaseConfig.COL_REWARDS)
                    .whereEqualTo("customerId", uid)
                    .addSnapshotListener((snap, err) -> {
                        if (err != null) {
                            FirestoreErrorLogger.logError("list", "rewards/customer", err);
                            return;
                        }
                        if (snap != null) {
                            synchronized (FirestoreSyncManager.this) {
                                rewardHistory.clear();
                                for (DocumentSnapshot d : snap.getDocuments()) {
                                    Reward r = FirestoreMapper.toReward(d);
                                    if (!"REDEEMABLE_OFFER".equals(r.getType())) rewardHistory.add(r);
                                }
                                rewardHistory.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                            }
                            notifyUi();
                        }
                    }));
        }

        // Role-scoped streams
        if (ROLE_ADMIN.equals(role)) {
            attachAdminStreams(db);
        } else if (ROLE_CUSTOMER.equals(role)) {
            attachCustomerStreams(db, uid);
        } else if (ROLE_RIDER.equals(role)) {
            attachRiderStreams(db, uid);
        }

        // Universal Official Group Chat shared across Admin, Customer, and Rider
        attachGroupChatStreams(db, uid);
    }

    private void attachGroupChatStreams(FirebaseFirestore db, String uid) {
        activeListeners.add(db.collection(FirebaseConfig.COL_CHAT_ROOMS)
                .document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MESSAGES)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", "chatRooms/mainGroup/messages", err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            groupMessages.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                groupMessages.add(FirestoreMapper.toGroupMessage(d));
                            }
                            groupMessages.sort(Comparator.comparingLong(GroupChatMessage::getCreatedAt));
                        }
                        notifyUi();
                    }
                }));

        activeListeners.add(db.collection(FirebaseConfig.COL_CHAT_ROOMS)
                .document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MEMBERS)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", "chatRooms/mainGroup/members", err);
                        return;
                    }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            groupMembers.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                GroupChatMember gm = FirestoreMapper.toGroupMember(d);
                                groupMembers.add(gm);
                                if (uid.equals(gm.getUid()) && gm.getLastReadAt() > myGroupLastReadAt) {
                                    myGroupLastReadAt = gm.getLastReadAt();
                                }
                            }
                        }
                        notifyUi();
                    }
                }));
    }

    private void attachAdminStreams(FirebaseFirestore db) {
        activeListeners.add(db.collection(FirebaseConfig.COL_ORDERS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_ORDERS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    orders.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) orders.add(FirestoreMapper.toOrder(d));
                    orders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_PAYMENTS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_PAYMENTS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    payments.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) payments.add(FirestoreMapper.toPayment(d));
                    payments.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_CUSTOMERS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_CUSTOMERS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    customers.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Customer c = FirestoreMapper.toCustomer(d);
                        customers.add(c);
                        persistedCustomerIds.add(c.getId());
                    }
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_RIDERS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_RIDERS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    riders.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Rider r = FirestoreMapper.toRider(d);
                        riders.add(r);
                        persistedRiderIds.add(r.getId());
                    }
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_ADMINS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_ADMINS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    admins.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) admins.add(FirestoreMapper.toAdmin(d));
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_CHATS).addSnapshotListener((snap, err) -> {
            if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_CHATS, err); return; }
            if (snap != null) {
                synchronized (this) {
                    chats.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) chats.add(FirestoreMapper.toChat(d));
                    chats.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
                }
                notifyUi();
            }
        }));
        activeListeners.add(db.collection(FirebaseConfig.COL_NOTIFICATIONS)
                .whereEqualTo("targetRole", ROLE_ADMIN)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_NOTIFICATIONS, err); return; }
                    if (snap != null) handleNotificationSnapshot(snap.getDocuments());
                }));
    }

    private void attachCustomerStreams(FirebaseFirestore db, String uid) {
        activeListeners.add(db.collection(FirebaseConfig.COL_CUSTOMERS).document(uid)
                .addSnapshotListener((doc, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("get", "customers/" + uid, err); return; }
                    if (doc != null && doc.exists()) {
                        synchronized (this) {
                            currentCustomer = FirestoreMapper.toCustomer(doc);
                            persistedCustomerIds.add(currentCustomer.getId());
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_ORDERS)
                .whereEqualTo("customerId", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_ORDERS, err); return; }
                    if (snap != null) {
                        synchronized (this) {
                            orders.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) orders.add(FirestoreMapper.toOrder(d));
                            orders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_CHATS)
                .whereEqualTo("customerId", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_CHATS, err); return; }
                    if (snap != null) {
                        synchronized (this) {
                            chats.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) chats.add(FirestoreMapper.toChat(d));
                            chats.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_NOTIFICATIONS)
                .whereEqualTo("targetUid", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_NOTIFICATIONS, err); return; }
                    if (snap != null) handleNotificationSnapshot(snap.getDocuments());
                }));
    }

    private void attachRiderStreams(FirebaseFirestore db, String uid) {
        activeListeners.add(db.collection(FirebaseConfig.COL_RIDERS)
                .whereEqualTo("linkedUid", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", "riders/linkedUid=" + uid, err); return; }
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        synchronized (this) {
                            currentRider = FirestoreMapper.toRider(doc);
                            persistedRiderIds.add(currentRider.getId());
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_ORDERS)
                .whereEqualTo("riderId", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_ORDERS, err); return; }
                    if (snap != null) {
                        synchronized (this) {
                            orders.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) orders.add(FirestoreMapper.toOrder(d));
                            orders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_CHATS)
                .whereEqualTo("riderId", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_CHATS, err); return; }
                    if (snap != null) {
                        synchronized (this) {
                            chats.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) chats.add(FirestoreMapper.toChat(d));
                            chats.sort(Comparator.comparingLong(ChatMessage::getTimestamp));
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_NOTIFICATIONS)
                .whereEqualTo("targetUid", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", FirebaseConfig.COL_NOTIFICATIONS, err); return; }
                    if (snap != null) handleNotificationSnapshot(snap.getDocuments());
                }));
    }

    private void handleNotificationSnapshot(List<DocumentSnapshot> docs) {
        List<NotificationItem> parsed = new ArrayList<>();
        List<NotificationItem> freshAlerts = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            NotificationItem n = FirestoreMapper.toNotification(d);
            parsed.add(n);
            if (notificationsPrimed && !n.isRead() && !seenNotifIds.contains(n.getId())) {
                freshAlerts.add(n);
            }
            seenNotifIds.add(n.getId());
        }
        parsed.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        synchronized (this) {
            notifications.clear();
            notifications.addAll(parsed);
        }
        notificationsPrimed = true;
        for (NotificationItem alert : freshAlerts) {
            Intent launch = new Intent(appContext, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            String alertId = alert.getId() != null ? alert.getId() : "0";
            RealTimeNotificationHelper.showAlert(appContext, alertId, alert.getTitle(), alert.getBody(), launch);
            mainHandler.post(() -> {
                for (AlertListener l : alertListeners) l.onNewNotification(alert);
            });
        }
        notifyUi();
    }

    private void parseRewards(List<DocumentSnapshot> docs) {
        synchronized (this) {
            rewardHistory.clear();
            redeemableOffers.clear();
            for (DocumentSnapshot d : docs) {
                Reward r = FirestoreMapper.toReward(d);
                if ("REDEEMABLE_OFFER".equals(r.getType())) redeemableOffers.add(r);
                else rewardHistory.add(r);
            }
            rewardHistory.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        }
        notifyUi();
    }

    public synchronized void stopSync() {
        for (ListenerRegistration reg : activeListeners) {
            try { reg.remove(); } catch (Exception ignored) {}
        }
        activeListeners.clear();
        seenNotifIds.clear();
        persistedCustomerIds.clear();
        persistedRiderIds.clear();
        notificationsPrimed = false;
        currentRole = "";
        currentUid = "";
        currentAdmin = null;
        currentCustomer = null;
        currentRider = null;
        groupMessages.clear();
        groupMembers.clear();
        myGroupLastReadAt = 0L;
    }

    // ---- Session accessors ----
    public synchronized AdminUser getCurrentAdmin() { return currentAdmin; }
    public synchronized void setCurrentAdmin(AdminUser admin) { this.currentAdmin = admin; }
    public synchronized Customer getCurrentCustomer() { return currentCustomer; }
    public synchronized void setCurrentCustomer(Customer c) { this.currentCustomer = c; }
    public synchronized void markCustomerPersisted(String id) {
        if (id != null && !id.isEmpty()) persistedCustomerIds.add(id);
    }
    public synchronized Rider getCurrentRider() { return currentRider; }
    public synchronized void setCurrentRider(Rider r) { this.currentRider = r; }
    public synchronized void markRiderPersisted(String id) {
        if (id != null && !id.isEmpty()) persistedRiderIds.add(id);
    }

    // ---- Read accessors ----
    public synchronized List<Product> getProducts() { return new ArrayList<>(products); }
    public synchronized List<Category> getCategories() { return new ArrayList<>(categories); }
    public synchronized List<Banner> getBanners() { return new ArrayList<>(banners); }
    public synchronized List<Order> getOrders() { return new ArrayList<>(orders); }
    public synchronized List<Payment> getPayments() { return new ArrayList<>(payments); }
    public synchronized List<Customer> getCustomers() { return new ArrayList<>(customers); }
    public synchronized List<Rider> getRiders() { return new ArrayList<>(riders); }
    public synchronized List<PaymentMethodConfig> getPaymentMethods() {
        return paymentMethods.isEmpty() ? defaultPaymentMethods() : new ArrayList<>(paymentMethods);
    }
    public synchronized RestaurantSettings getSettings() { return settings != null ? settings : defaultSettings(); }
    public synchronized List<ChatMessage> getChats() { return new ArrayList<>(chats); }
    public synchronized List<GroupChatMessage> getGroupMessages() { return new ArrayList<>(groupMessages); }
    public synchronized List<GroupChatMember> getGroupMembers() { return new ArrayList<>(groupMembers); }
    public synchronized int getUnreadGroupChatCount() {
        int count = 0;
        for (GroupChatMessage m : groupMessages) {
            if (!currentUid.equals(m.getSenderId()) && m.getCreatedAt() > myGroupLastReadAt) {
                count++;
            }
        }
        return count;
    }
    public synchronized List<Reward> getRewardHistory() { return new ArrayList<>(rewardHistory); }
    public synchronized List<Reward> getRedeemableOffers() { return new ArrayList<>(redeemableOffers); }
    public synchronized List<NotificationItem> getNotifications() { return new ArrayList<>(notifications); }
    public synchronized int getUnreadNotificationCount() {
        int c = 0;
        for (NotificationItem n : notifications) if (!n.isRead()) c++;
        return c;
    }

    // ---- Writes synced to Cloud Firestore ----
    public synchronized void saveProduct(Product product) {
        boolean isCreate = product.getId() == null || product.getId().trim().isEmpty();
        if (isCreate) {
            product.setId("prod_" + System.currentTimeMillis());
            product.setCreatedAt(System.currentTimeMillis());
            products.add(0, product);
        } else {
            boolean found = false;
            for (int i = 0; i < products.size(); i++) {
                if (products.get(i).getId().equals(product.getId())) {
                    products.set(i, product);
                    found = true;
                    break;
                }
            }
            if (!found) isCreate = true;
        }
        String id = FirestoreMapper.sanitizeId(product.getId());
        product.setId(id);
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (isCreate) {
            db.collection(FirebaseConfig.COL_PRODUCTS).document(id)
                    .set(FirestoreMapper.fromProduct(product, true))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "products/" + id, e));
        } else {
            db.collection(FirebaseConfig.COL_PRODUCTS).document(id)
                    .update(FirestoreMapper.fromProduct(product, false))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "products/" + id, e));
        }
        notifyUi();
    }

    public synchronized void deleteProduct(String productId) {
        products.removeIf(p -> p.getId().equals(productId));
        String id = FirestoreMapper.sanitizeId(productId);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_PRODUCTS).document(id)
                .delete().addOnFailureListener(e -> FirestoreErrorLogger.logError("delete", "products/" + id, e));
        notifyUi();
    }

    public synchronized void saveCategory(Category cat) {
        boolean isCreate = cat.getId() == null || cat.getId().trim().isEmpty();
        if (isCreate) {
            cat.setId("cat_" + System.currentTimeMillis());
            categories.add(cat);
        } else {
            boolean found = false;
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).getId().equals(cat.getId())) {
                    categories.set(i, cat);
                    found = true;
                    break;
                }
            }
            if (!found) isCreate = true;
        }
        String id = FirestoreMapper.sanitizeId(cat.getId());
        cat.setId(id);
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (isCreate) {
            db.collection(FirebaseConfig.COL_CATEGORIES).document(id)
                    .set(FirestoreMapper.fromCategory(cat, true))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "categories/" + id, e));
        } else {
            db.collection(FirebaseConfig.COL_CATEGORIES).document(id)
                    .update(FirestoreMapper.fromCategory(cat, false))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "categories/" + id, e));
        }
        notifyUi();
    }

    public synchronized void deleteCategory(String categoryId) {
        categories.removeIf(c -> c.getId().equals(categoryId));
        String id = FirestoreMapper.sanitizeId(categoryId);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_CATEGORIES).document(id)
                .delete().addOnFailureListener(e -> FirestoreErrorLogger.logError("delete", "categories/" + id, e));
        notifyUi();
    }

    public synchronized void saveBanner(Banner b) {
        boolean isCreate = b.getId() == null || b.getId().trim().isEmpty();
        if (isCreate) {
            b.setId("ban_" + System.currentTimeMillis());
            banners.add(b);
        } else {
            boolean found = false;
            for (int i = 0; i < banners.size(); i++) {
                if (banners.get(i).getId().equals(b.getId())) {
                    banners.set(i, b);
                    found = true;
                    break;
                }
            }
            if (!found) isCreate = true;
        }
        String id = FirestoreMapper.sanitizeId(b.getId());
        b.setId(id);
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (isCreate) {
            db.collection(FirebaseConfig.COL_BANNERS).document(id)
                    .set(FirestoreMapper.fromBanner(b, true))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "banners/" + id, e));
        } else {
            db.collection(FirebaseConfig.COL_BANNERS).document(id)
                    .update(FirestoreMapper.fromBanner(b, false))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "banners/" + id, e));
        }
        notifyUi();
    }

    public synchronized void deleteBanner(String bannerId) {
        banners.removeIf(b -> b.getId().equals(bannerId));
        String id = FirestoreMapper.sanitizeId(bannerId);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_BANNERS).document(id)
                .delete().addOnFailureListener(e -> FirestoreErrorLogger.logError("delete", "banners/" + id, e));
        notifyUi();
    }

    public synchronized Order placeOrder(Order order) {
        String uid = FirebaseConfig.getAuth(appContext).getUid();
        if (uid == null || uid.isEmpty()) uid = order.getCustomerId();
        long now = System.currentTimeMillis();
        String orderId = "ORD-" + now;
        order.setOrderId(orderId);
        order.setCustomerId(uid);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        if (order.getRiderId() == null) order.setRiderId("");
        if (order.getRiderName() == null) order.setRiderName("");
        if (PaymentMethodConfig.METHOD_COD.equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("Pending");
        } else {
            order.setPaymentStatus("Pending Verification");
        }
        int earned = (int) Math.max(10, Math.round((order.getTotalAmount() / 100.0) * Math.max(1, settings.getPointsPerHundredRs())));
        order.setPointsEarned(earned);

        order.putTimestampIfAbsent(OrderEvent.TS_PLACED, now);
        String actorName = order.getCustomerName() != null && !order.getCustomerName().isEmpty()
                ? order.getCustomerName() : "Customer";
        OrderEvent placedEvt = new OrderEvent(
                OrderEvent.ORDER_PLACED, orderId, OrderEvent.ORDER_PLACED, now,
                uid, ROLE_CUSTOMER, actorName, "Order placed"
        );
        order.addTimelineEventIfAbsent(placedEvt);

        orders.add(0, order);
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .set(FirestoreMapper.fromOrderCreate(order))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "orders/" + orderId, e));

        db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .collection(FirebaseConfig.SUB_TIMELINE).document(OrderEvent.ORDER_PLACED)
                .set(FirestoreMapper.fromTimelineEventCreate(placedEvt))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "orders/" + orderId + "/timeline/ORDER_PLACED", e));

        Payment payment = new Payment(
                "pay_" + orderId, orderId, uid, order.getPaymentMethod(),
                order.getCustomerName(), order.getCustomerPhone(),
                order.getTotalAmount(), order.getPaymentStatus(), now
        );
        payments.add(0, payment);
        db.collection(FirebaseConfig.COL_PAYMENTS).document(payment.getId())
                .set(FirestoreMapper.fromPaymentCreate(payment))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "payments/" + payment.getId(), e));

        addLoyaltyPoints(uid, earned, "Order " + orderId + " Completed", orderId);

        // Real-time alert to Admin App
        sendNotification(
                ROLE_ADMIN, "", uid, orderId,
                "New Order " + orderId,
                order.getCustomerName() + " placed a " + order.getPaymentMethod() + " order for Rs. " + Math.round(order.getTotalAmount()),
                "NEW_ORDER"
        );
        notifyUi();
        return order;
    }

    public synchronized boolean updateOrderStatus(String orderId, String newStatus) {
        String evtType = OrderStatus.eventTypeForStatus(newStatus);
        String tsKey = OrderStatus.timestampKeyForStatus(newStatus);
        String actorUid = FirebaseConfig.getAuth(appContext).getUid();
        if (actorUid == null) actorUid = currentUid;
        String actorRole = !currentRole.isEmpty() ? currentRole : ROLE_ADMIN;
        String actorName = resolveActorDisplayName(actorRole);
        return advanceOrderStage(orderId, newStatus, evtType, tsKey, actorUid, actorRole, actorName, "");
    }

    public synchronized boolean advanceOrderStage(String orderId, String newStatus, String eventType,
                                                  String timestampKey, String userId, String userRole,
                                                  String userName, String notes) {
        Order target = null;
        for (Order o : orders) {
            if (o.getOrderId().equals(orderId)) { target = o; break; }
        }
        if (target == null) return false;

        String oldStatus = target.getOrderStatus();
        long now = System.currentTimeMillis();
        if (newStatus != null && !newStatus.isEmpty()) {
            target.setOrderStatus(newStatus);
        }
        target.setUpdatedAt(now);

        Map<String, Object> updates = new HashMap<>();
        if (newStatus != null && !newStatus.isEmpty()) {
            updates.put("orderStatus", newStatus);
        }
        boolean writeTimestamp = timestampKey != null && !timestampKey.isEmpty() && !target.hasTimestamp(timestampKey);
        if (writeTimestamp) {
            target.putTimestampIfAbsent(timestampKey, now);
            updates.put("timestamps." + timestampKey, FieldValue.serverTimestamp());
        }
        if (OrderStatus.DELIVERED.equalsIgnoreCase(newStatus)
                && !OrderStatus.DELIVERED.equalsIgnoreCase(oldStatus)) {
            target.setPaymentStatus("Paid");
            updates.put("paymentStatus", "Paid");
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .update(updates)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "orders/" + orderId, e));

        if (eventType != null && !eventType.isEmpty() && !hasTimelineEvent(target, eventType)) {
            String uid = (userId != null && !userId.isEmpty()) ? userId : currentUid;
            String role = (userRole != null && !userRole.isEmpty()) ? userRole : (!currentRole.isEmpty() ? currentRole : ROLE_ADMIN);
            String uname = (userName != null && !userName.isEmpty()) ? userName : resolveActorDisplayName(role);
            OrderEvent evt = new OrderEvent(eventType, orderId, eventType, now, uid, role, uname, notes);
            target.addTimelineEventIfAbsent(evt);
            db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                    .collection(FirebaseConfig.SUB_TIMELINE).document(eventType)
                    .set(FirestoreMapper.fromTimelineEventCreate(evt))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "orders/" + orderId + "/timeline/" + eventType, e));
        }

        if (OrderStatus.DELIVERED.equalsIgnoreCase(newStatus)
                && !OrderStatus.DELIVERED.equalsIgnoreCase(oldStatus)) {
            syncPaymentPaidForOrder(db, orderId);
            if (target.getRiderId() != null && !target.getRiderId().isEmpty()) {
                incrementRiderDeliveryStats(target.getRiderId(), target.getDeliveryFee() > 0 ? target.getDeliveryFee() : 150.0);
            }
        }

        String sender = FirebaseConfig.getAuth(appContext).getUid();
        if (sender != null && newStatus != null && !newStatus.isEmpty()) {
            if (target.getCustomerId() != null && !target.getCustomerId().isEmpty() && !sender.equals(target.getCustomerId())) {
                sendNotification(
                        ROLE_CUSTOMER, target.getCustomerId(), sender, orderId,
                        "Order " + orderId + " • " + newStatus,
                        "Your Dera Shanwari order status is now: " + newStatus,
                        "ORDER_STATUS"
                );
            }
            if (ROLE_RIDER.equals(currentRole)) {
                sendNotification(
                        ROLE_ADMIN, "", sender, orderId,
                        "Rider Update • " + orderId,
                        (target.getRiderName() != null ? target.getRiderName() : "Rider") + " marked order " + newStatus,
                        "RIDER_UPDATE"
                );
            }
        }
        notifyUi();
        return true;
    }

    public synchronized boolean assignRider(String orderId, String riderId, String riderName) {
        Order target = null;
        for (Order o : orders) {
            if (o.getOrderId().equals(orderId)) { target = o; break; }
        }
        if (target == null) return false;
        long now = System.currentTimeMillis();
        target.setRiderId(riderId);
        target.setRiderName(riderName);
        target.setOrderStatus(OrderStatus.ASSIGNED);
        target.setUpdatedAt(now);

        Map<String, Object> updates = new HashMap<>();
        updates.put("riderId", riderId);
        updates.put("riderName", riderName);
        updates.put("orderStatus", OrderStatus.ASSIGNED);
        if (!target.hasTimestamp(OrderEvent.TS_RIDER_ASSIGNED)) {
            target.putTimestampIfAbsent(OrderEvent.TS_RIDER_ASSIGNED, now);
            updates.put("timestamps." + OrderEvent.TS_RIDER_ASSIGNED, FieldValue.serverTimestamp());
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .update(updates)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "orders/" + orderId, e));

        if (!hasTimelineEvent(target, OrderEvent.RIDER_ASSIGNED)) {
            String uid = FirebaseConfig.getAuth(appContext).getUid();
            if (uid == null) uid = currentUid;
            OrderEvent evt = new OrderEvent(
                    OrderEvent.RIDER_ASSIGNED, orderId, OrderEvent.RIDER_ASSIGNED, now,
                    uid != null ? uid : "", ROLE_ADMIN, resolveActorDisplayName(ROLE_ADMIN),
                    "Assigned to " + riderName
            );
            target.addTimelineEventIfAbsent(evt);
            db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                    .collection(FirebaseConfig.SUB_TIMELINE).document(OrderEvent.RIDER_ASSIGNED)
                    .set(FirestoreMapper.fromTimelineEventCreate(evt))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "orders/" + orderId + "/timeline/RIDER_ASSIGNED", e));
        }

        String sender = FirebaseConfig.getAuth(appContext).getUid();
        if (sender != null) {
            sendNotification(ROLE_RIDER, riderId, sender, orderId,
                    "New Delivery Assigned: " + orderId,
                    "Deliver to " + target.getCustomerName() + " at " + target.getDeliveryAddress(),
                    "RIDER_ASSIGNED");
            if (target.getCustomerId() != null && !target.getCustomerId().isEmpty()) {
                sendNotification(ROLE_CUSTOMER, target.getCustomerId(), sender, orderId,
                        "Rider Assigned: " + riderName,
                        "Rider " + riderName + " is assigned to order " + orderId,
                        "ORDER_STATUS");
            }
        }
        notifyUi();
        return true;
    }

    public synchronized boolean updateOrder(Order updatedOrder) {
        if (updatedOrder == null || updatedOrder.getOrderId() == null) return false;
        boolean found = false;
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getOrderId().equals(updatedOrder.getOrderId())) {
                orders.set(i, updatedOrder);
                found = true;
                break;
            }
        }
        if (!found) return false;

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        Map<String, Object> map = FirestoreMapper.fromOrder(updatedOrder, false);
        db.collection(FirebaseConfig.COL_ORDERS).document(updatedOrder.getOrderId())
                .set(map)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "orders/" + updatedOrder.getOrderId(), e));
        notifyUi();
        return true;
    }

    public synchronized boolean deleteOrder(String orderId) {
        if (orderId == null) return false;
        orders.removeIf(o -> o.getOrderId().equals(orderId));
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .delete()
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("delete", "orders/" + orderId, e));
        notifyUi();
        return true;
    }

    public synchronized boolean recordOrderPrint(String orderId, String printedBy) {
        Order target = null;
        for (Order o : orders) {
            if (o.getOrderId().equals(orderId)) { target = o; break; }
        }
        if (target == null) return false;
        long now = System.currentTimeMillis();
        target.setPrintCount(target.getPrintCount() + 1);
        target.setLastPrintedAt(now);
        target.setLastPrintedBy(printedBy != null ? printedBy : resolveActorDisplayName(ROLE_ADMIN));

        Map<String, Object> updates = new HashMap<>();
        updates.put("printCount", FieldValue.increment(1));
        updates.put("lastPrintedAt", FieldValue.serverTimestamp());
        updates.put("lastPrintedBy", target.getLastPrintedBy());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .update(updates)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "orders/" + orderId + "/print", e));
        notifyUi();
        return true;
    }

    public ListenerRegistration subscribeOrderTimeline(String orderId, TimelineListener listener) {
        if (orderId == null || orderId.isEmpty() || listener == null) return null;
        return FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .collection(FirebaseConfig.SUB_TIMELINE)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        FirestoreErrorLogger.logError("list", "orders/" + orderId + "/timeline", err);
                        return;
                    }
                    if (snap != null) {
                        List<OrderEvent> events = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            events.add(FirestoreMapper.toTimelineEvent(d));
                        }
                        events.sort(Comparator.comparingLong(OrderEvent::getTimestamp));
                        synchronized (FirestoreSyncManager.this) {
                            for (Order o : orders) {
                                if (orderId.equals(o.getOrderId())) {
                                    o.setTimeline(events);
                                    for (OrderEvent ev : events) {
                                        String key = OrderEvent.timestampKeyForEvent(ev.getEventType());
                                        if (!key.isEmpty()) o.putTimestampIfAbsent(key, ev.getTimestamp());
                                    }
                                    break;
                                }
                            }
                        }
                        mainHandler.post(() -> listener.onTimelineUpdated(events));
                    }
                });
    }

    private boolean hasTimelineEvent(Order order, String eventType) {
        if (order == null || eventType == null) return false;
        for (OrderEvent e : order.getTimeline()) {
            if (eventType.equals(e.getEventType())) return true;
        }
        return false;
    }

    private String resolveActorDisplayName(String role) {
        if (ROLE_ADMIN.equals(role) && currentAdmin != null && currentAdmin.getName() != null) {
            return currentAdmin.getName();
        }
        if (ROLE_RIDER.equals(role) && currentRider != null && currentRider.getName() != null) {
            return currentRider.getName();
        }
        if (ROLE_CUSTOMER.equals(role) && currentCustomer != null && currentCustomer.getName() != null) {
            return currentCustomer.getName();
        }
        FirebaseUser fb = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fb != null && fb.getDisplayName() != null && !fb.getDisplayName().isEmpty()) {
            return fb.getDisplayName();
        }
        if (ROLE_ADMIN.equals(role)) return "Dera Admin";
        if (ROLE_RIDER.equals(role)) return "Delivery Rider";
        return "Customer";
    }

    public synchronized boolean updatePaymentStatus(String paymentId, String status) {
        for (Payment p : payments) {
            if (p.getId().equals(paymentId)) {
                p.setStatus(status);
                Map<String, Object> m = new HashMap<>();
                m.put("status", status);
                m.put("updatedAt", FieldValue.serverTimestamp());
                FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_PAYMENTS).document(paymentId)
                        .update(m).addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "payments/" + paymentId, e));
                notifyUi();
                return true;
            }
        }
        return false;
    }

    private void syncPaymentPaidForOrder(FirebaseFirestore db, String orderId) {
        boolean updatedKnown = false;
        for (Payment p : payments) {
            if (orderId.equals(p.getOrderId())) {
                p.setStatus("Paid");
                updatedKnown = true;
                Map<String, Object> pm = new HashMap<>();
                pm.put("status", "Paid");
                pm.put("updatedAt", FieldValue.serverTimestamp());
                db.collection(FirebaseConfig.COL_PAYMENTS).document(p.getId())
                        .update(pm)
                        .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "payments/" + p.getId(), e));
            }
        }
        if (!updatedKnown) {
            String detPayId = "pay_" + orderId;
            Map<String, Object> pm = new HashMap<>();
            pm.put("status", "Paid");
            pm.put("updatedAt", FieldValue.serverTimestamp());
            db.collection(FirebaseConfig.COL_PAYMENTS).document(detPayId)
                    .update(pm)
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "payments/" + detPayId, e));
        }
    }

    public synchronized void saveCustomer(Customer c) {
        if (c.getId() == null || c.getId().isEmpty()) return;
        boolean exists = persistedCustomerIds.contains(c.getId());
        for (int i = 0; i < customers.size(); i++) {
            if (customers.get(i).getId().equals(c.getId())) {
                customers.set(i, c);
                exists = true;
                break;
            }
        }
        if (currentCustomer != null && currentCustomer.getId().equals(c.getId())) currentCustomer = c;
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (!exists) {
            customers.add(c);
            db.collection(FirebaseConfig.COL_CUSTOMERS).document(c.getId())
                    .set(FirestoreMapper.fromCustomerCreate(c))
                    .addOnSuccessListener(unused -> persistedCustomerIds.add(c.getId()))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "customers/" + c.getId(), e));
        } else {
            Map<String, Object> m = new HashMap<>();
            m.put("name", c.getName());
            m.put("email", c.getEmail());
            m.put("phone", c.getPhone());
            m.put("address", c.getAddress());
            m.put("loyaltyPoints", c.getLoyaltyPoints());
            m.put("updatedAt", FieldValue.serverTimestamp());
            db.collection(FirebaseConfig.COL_CUSTOMERS).document(c.getId())
                    .update(m)
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "customers/" + c.getId(), e));
        }
        notifyUi();
    }

    public synchronized void updateCustomerNameAddress(String customerId, String name, String address) {
        if (customerId == null || customerId.isEmpty()) return;
        if (currentCustomer != null && currentCustomer.getId().equals(customerId)) {
            currentCustomer.setName(name);
            currentCustomer.setAddress(address);
        }
        for (Customer c : customers) {
            if (c.getId().equals(customerId)) {
                c.setName(name);
                c.setAddress(address);
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("address", address);
        m.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_CUSTOMERS).document(customerId)
                .update(m)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "customers/" + customerId, e));
        notifyUi();
    }

    public synchronized void setCustomerActive(String customerId, boolean active) {
        for (Customer c : customers) if (c.getId().equals(customerId)) c.setActive(active);
        Map<String, Object> m = new HashMap<>();
        m.put("active", active);
        m.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_CUSTOMERS).document(customerId)
                .update(m).addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "customers/" + customerId, e));
        notifyUi();
    }

    public synchronized void saveRider(Rider r) {
        if (r.getEmail() != null) {
            r.setEmail(r.getEmail().trim().toLowerCase());
        }
        boolean isCreate = r.getId() == null || r.getId().trim().isEmpty();
        if (isCreate) {
            r.setId("rider_" + System.currentTimeMillis());
            if (r.getLinkedUid() == null || r.getLinkedUid().isEmpty()) {
                r.setLinkedUid(r.getId());
            }
            riders.add(r);
        } else {
            boolean found = persistedRiderIds.contains(r.getId());
            for (int i = 0; i < riders.size(); i++) {
                if (riders.get(i).getId().equals(r.getId())) {
                    riders.set(i, r);
                    found = true;
                    break;
                }
            }
            if (!found) {
                isCreate = true;
                riders.add(r);
            }
        }
        if (currentRider != null && currentRider.getId().equals(r.getId())) currentRider = r;
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (isCreate) {
            db.collection(FirebaseConfig.COL_RIDERS).document(r.getId())
                    .set(FirestoreMapper.fromRiderCreate(r))
                    .addOnSuccessListener(unused -> persistedRiderIds.add(r.getId()))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "riders/" + r.getId(), e));
        } else {
            Map<String, Object> m = new HashMap<>();
            m.put("name", r.getName());
            m.put("email", r.getEmail());
            m.put("phone", r.getPhone());
            m.put("vehicleInfo", r.getVehicleInfo());
            m.put("profileImageUrl", r.getProfileImageUrl());
            m.put("active", r.isActive());
            m.put("totalDeliveries", r.getTotalDeliveries());
            m.put("totalEarnings", r.getTotalEarnings());
            m.put("updatedAt", FieldValue.serverTimestamp());
            db.collection(FirebaseConfig.COL_RIDERS).document(r.getId())
                    .update(m).addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "riders/" + r.getId(), e));
        }
        notifyUi();
    }

    public synchronized void setRiderActive(String riderId, boolean active) {
        for (Rider r : riders) if (r.getId().equals(riderId)) r.setActive(active);
        if (currentRider != null && currentRider.getId().equals(riderId)) currentRider.setActive(active);
        Map<String, Object> m = new HashMap<>();
        m.put("active", active);
        m.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_RIDERS).document(riderId)
                .update(m).addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "riders/" + riderId, e));
        notifyUi();
    }

    public synchronized void incrementRiderDeliveryStats(String riderId, double earning) {
        Rider target = (currentRider != null && (currentRider.getId().equals(riderId)
                || (currentRider.getLinkedUid() != null && currentRider.getLinkedUid().equals(riderId))))
                ? currentRider : null;
        if (target == null) {
            for (Rider r : riders) {
                if (r.getId().equals(riderId) || (r.getLinkedUid() != null && r.getLinkedUid().equals(riderId))) {
                    target = r;
                    break;
                }
            }
        }
        if (target == null) return;
        target.setTotalDeliveries(target.getTotalDeliveries() + 1);
        target.setTotalEarnings(target.getTotalEarnings() + earning);

        Map<String, Object> m = new HashMap<>();
        m.put("totalDeliveries", target.getTotalDeliveries());
        m.put("totalEarnings", target.getTotalEarnings());
        m.put("updatedAt", FieldValue.serverTimestamp());
        String docId = target.getId();
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_RIDERS).document(docId)
                .update(m).addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "riders/" + docId, e));
        notifyUi();
    }

    public synchronized void saveSettings(RestaurantSettings s) {
        this.settings = s;
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_RESTAURANT_SETTINGS)
                .set(FirestoreMapper.fromSettings(s))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("set", "settings/restaurant", e));
        notifyUi();
    }

    public synchronized void savePaymentMethod(PaymentMethodConfig pm) {
        String code = FirestoreMapper.sanitizeId(pm.getMethodId());
        pm.setMethodId(code);
        boolean found = false;
        for (int i = 0; i < paymentMethods.size(); i++) {
            if (paymentMethods.get(i).getMethodId().equals(code)) {
                paymentMethods.set(i, pm);
                found = true;
                break;
            }
        }
        if (!found) paymentMethods.add(pm);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_PAYMENT_METHODS)
                .document(code)
                .set(FirestoreMapper.fromPaymentMethod(pm))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("set", "payment_methods/" + code, e));
        notifyUi();
    }

    public synchronized void sendChatMessage(ChatMessage msg) {
        String id = "msg_" + System.currentTimeMillis();
        msg.setId(id);
        msg.setTimestamp(System.currentTimeMillis());
        if (msg.getSenderId() == null || msg.getSenderId().isEmpty()) {
            String uid = FirebaseConfig.getAuth(appContext).getUid();
            msg.setSenderId(uid != null ? uid : "");
        }
        // Attach order's customerId & riderId so both participants can read via rules
        for (Order o : orders) {
            if (o.getOrderId().equals(msg.getOrderId())) {
                if (msg.getCustomerId() == null || msg.getCustomerId().isEmpty()) msg.setCustomerId(o.getCustomerId());
                if (msg.getRiderId() == null || msg.getRiderId().isEmpty()) msg.setRiderId(o.getRiderId());
                break;
            }
        }
        chats.add(msg);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_CHATS).document(id)
                .set(FirestoreMapper.fromChat(msg))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "chats/" + id, e));
        notifyUi();
    }

    public synchronized void sendGroupChatMessage(String text, String imageUrl) {
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        String uid = fbUser != null ? fbUser.getUid() : currentUid;
        if (uid == null || uid.isEmpty()) return;
        String safeText = text != null ? text.trim() : "";
        String safeImg = imageUrl != null ? imageUrl.trim() : "";
        if (safeText.isEmpty() && safeImg.isEmpty()) return;

        String role = !currentRole.isEmpty() ? currentRole : ROLE_CUSTOMER;
        String name = resolveActorDisplayName(role);
        String avatar = "";
        if (ROLE_RIDER.equals(role) && currentRider != null) {
            avatar = currentRider.getProfileImageUrl();
        }
        String msgId = "gmsg_" + System.currentTimeMillis() + "_" + Math.abs(uid.hashCode() % 1000);
        GroupChatMessage msg = new GroupChatMessage(msgId, uid, name, role, avatar, safeText, safeImg, System.currentTimeMillis());
        groupMessages.add(msg);
        myGroupLastReadAt = Math.max(myGroupLastReadAt, msg.getCreatedAt());

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_CHAT_ROOMS).document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MESSAGES).document(msgId)
                .set(FirestoreMapper.fromGroupMessage(msg))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "chatRooms/mainGroup/messages/" + msgId, e));
        markGroupChatRead();
        notifyUi();
    }

    public synchronized void deleteGroupChatMessage(String messageId) {
        if (messageId == null || messageId.isEmpty() || !ROLE_ADMIN.equals(currentRole)) return;
        groupMessages.removeIf(m -> messageId.equals(m.getId()));
        FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_CHAT_ROOMS).document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MESSAGES).document(messageId)
                .delete()
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("delete", "chatRooms/mainGroup/messages/" + messageId, e));
        notifyUi();
    }

    public synchronized void setGroupPresence(boolean online) {
        updateGroupMemberRecord(online, false);
    }

    public synchronized void markGroupChatRead() {
        myGroupLastReadAt = System.currentTimeMillis();
        updateGroupMemberRecord(true, true);
        notifyUi();
    }

    private void updateGroupMemberRecord(boolean online, boolean markReadNow) {
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        String uid = fbUser != null ? fbUser.getUid() : currentUid;
        if (uid == null || uid.isEmpty()) return;
        String role = !currentRole.isEmpty() ? currentRole : ROLE_CUSTOMER;
        String name = resolveActorDisplayName(role);
        String avatar = (ROLE_RIDER.equals(role) && currentRider != null) ? currentRider.getProfileImageUrl() : "";

        long now = System.currentTimeMillis();
        long readAt = markReadNow ? now : (myGroupLastReadAt > 0L ? myGroupLastReadAt : now);
        GroupChatMember member = new GroupChatMember(uid, name, role, avatar, online, now, readAt);
        boolean found = false;
        for (int i = 0; i < groupMembers.size(); i++) {
            if (uid.equals(groupMembers.get(i).getUid())) {
                groupMembers.set(i, member);
                found = true;
                break;
            }
        }
        if (!found) groupMembers.add(member);

        FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_CHAT_ROOMS).document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MEMBERS).document(uid)
                .set(FirestoreMapper.fromGroupMember(member, markReadNow))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("set", "chatRooms/mainGroup/members/" + uid, e));
    }

    public synchronized void addLoyaltyPoints(String customerId, int points, String title, String orderId) {
        if (customerId == null || customerId.isEmpty()) return;
        Reward r = new Reward("rew_" + System.currentTimeMillis(), customerId, title,
                "Loyalty points earned", points, "EARNED", false, 0, orderId, System.currentTimeMillis());
        rewardHistory.add(0, r);
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_REWARDS).document(r.getId())
                .set(FirestoreMapper.fromRewardCreate(r))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "rewards/" + r.getId(), e));

        if (currentCustomer != null && currentCustomer.getId().equals(customerId)) {
            currentCustomer.setLoyaltyPoints(currentCustomer.getLoyaltyPoints() + points);
        }
        Map<String, Object> pm = new HashMap<>();
        pm.put("loyaltyPoints", FieldValue.increment(points));
        pm.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(FirebaseConfig.COL_CUSTOMERS).document(customerId)
                .update(pm)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "customers/" + customerId, e));
        notifyUi();
    }

    public synchronized boolean redeemOffer(String customerId, Reward offer) {
        if (currentCustomer == null || !currentCustomer.getId().equals(customerId)) return false;
        if (offer == null || offer.getMinPointsRequired() <= 0) return false;
        if (currentCustomer.getLoyaltyPoints() < offer.getMinPointsRequired()) return false;
        currentCustomer.setLoyaltyPoints(currentCustomer.getLoyaltyPoints() - offer.getMinPointsRequired());

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        Map<String, Object> pm = new HashMap<>();
        pm.put("loyaltyPoints", FieldValue.increment(-offer.getMinPointsRequired()));
        pm.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(FirebaseConfig.COL_CUSTOMERS).document(customerId)
                .update(pm)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "customers/" + customerId, e));

        Reward redeemed = new Reward("red_" + System.currentTimeMillis(), customerId,
                "Redeemed: " + offer.getTitle(), offer.getDescription(),
                -offer.getMinPointsRequired(), "REDEEMED", false, 0, "", System.currentTimeMillis());
        rewardHistory.add(0, redeemed);
        db.collection(FirebaseConfig.COL_REWARDS).document(redeemed.getId())
                .set(FirestoreMapper.fromRewardCreate(redeemed))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "rewards/" + redeemed.getId(), e));
        notifyUi();
        return true;
    }

    public synchronized void sendNotification(String targetRole, String targetUid, String senderId,
                                              String orderId, String title, String body, String type) {
        if (senderId == null || senderId.isEmpty()) return;
        String id = "notif_" + System.currentTimeMillis() + "_" + Math.abs((targetRole + orderId).hashCode() % 1000);
        NotificationItem item = new NotificationItem(id, targetRole, targetUid, senderId, orderId, title, body, type, false, System.currentTimeMillis());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_NOTIFICATIONS).document(id)
                .set(FirestoreMapper.fromNotificationCreate(item))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "notifications/" + id, e));
    }

    public synchronized void markAllNotificationsRead() {
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        for (NotificationItem n : notifications) {
            if (!n.isRead()) {
                n.setRead(true);
                Map<String, Object> m = new HashMap<>();
                m.put("read", true);
                m.put("updatedAt", FieldValue.serverTimestamp());
                db.collection(FirebaseConfig.COL_NOTIFICATIONS).document(n.getId()).update(m);
            }
        }
        notifyUi();
    }

    /**
     * Populates Cloud Firestore with authentic Dera Shanwari menu, categories, banners,
     * payment methods, restaurant settings, and redeemable offers when invoked by an authorized Admin.
     */
    public synchronized void seedDefaultCatalogToFirestore() {
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        long now = System.currentTimeMillis();

        saveSettings(defaultSettings());
        for (PaymentMethodConfig pm : defaultPaymentMethods()) {
            savePaymentMethod(pm);
        }

        List<Category> seedCats = Arrays.asList(
                new Category("cat_karahi", "Karahi", ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari", true, 1),
                new Category("cat_tikka", "Chicken Tikka", ImageKitConfig.IMG_TIKKA, "img_food_tikka_bbq", true, 2),
                new Category("cat_bbq", "BBQ", ImageKitConfig.IMG_BBQ_PLATTER, "img_food_tikka_bbq", true, 3),
                new Category("cat_burger", "Burger", ImageKitConfig.IMG_ZINGER, "img_food_burger_wrap", true, 4),
                new Category("cat_shawarma", "Shawarma", ImageKitConfig.IMG_SHAWARMA, "img_food_burger_wrap", true, 5),
                new Category("cat_pizza", "Pizza", ImageKitConfig.IMG_PIZZA, "img_food_burger_wrap", true, 6),
                new Category("cat_drinks", "Drinks", ImageKitConfig.IMG_MINT_MARGARITA, "img_food_beverages", true, 7)
        );
        for (Category c : seedCats) {
            db.collection(FirebaseConfig.COL_CATEGORIES).document(c.getId()).set(FirestoreMapper.fromCategory(c, true));
        }

        List<Banner> seedBans = Arrays.asList(
                new Banner("ban_1", "Signature Mutton Shanwari Karahi",
                        "Sizzling fresh tomatoes, black pepper & pure desi ghee - Rs. 250 Off Full Karahi!",
                        ImageKitConfig.IMG_BANNER_KARAHI, "img_banner_shanwari", true, 1),
                new Banner("ban_2", "Charcoal BBQ Platter Nights",
                        "Malai Boti, Seekh Kabab & Chicken Tikka served piping hot with garlic naan.",
                        ImageKitConfig.IMG_BANNER_BBQ, "img_food_tikka_bbq", true, 2)
        );
        for (Banner b : seedBans) {
            db.collection(FirebaseConfig.COL_BANNERS).document(b.getId()).set(FirestoreMapper.fromBanner(b, true));
        }

        Product p1 = new Product("prod_1", "Mutton Shanwari Karahi",
                "Authentic Peshawari lamb karahi cooked in cast-iron wok with fresh tomatoes, green chilies, and julienned ginger.",
                3400, 3150, ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari", "cat_karahi", "Karahi", true, true, true, now);
        p1.setVariants(Arrays.asList(
                new ProductVariant("half_kg", "Half Kg", 1800, 1650, true, "", "500g Bone-in Mutton"),
                new ProductVariant("full_kg", "Full 1 Kg", 3400, 3150, true, "", "1 Kg Family Wok")
        ));

        Product p2 = new Product("prod_2", "Chicken Shanwari Karahi",
                "Tender bone-in chicken simmered in rich tomato gravy with crushed black pepper and fresh coriander.",
                1950, 1750, ImageKitConfig.IMG_CHICKEN_KARAHI, "img_banner_shanwari", "cat_karahi", "Karahi", true, true, true, now - 1000);
        p2.setVariants(Arrays.asList(
                new ProductVariant("half", "Half", 1050, 950, true, "", "Serves 2"),
                new ProductVariant("full", "Full", 1950, 1750, true, "", "Serves 4")
        ));

        Product p3 = new Product("prod_3", "Charcoal Chicken Tikka",
                "Smoky charcoal-grilled chicken marinated in Shanwari spices, served with mint raita.",
                520, 480, ImageKitConfig.IMG_TIKKA, "img_food_tikka_bbq", "cat_tikka", "Chicken Tikka", true, true, true, now - 2000);
        p3.setVariants(Arrays.asList(
                new ProductVariant("leg", "Leg Piece", 480, 440, true, "", "Quarter Leg"),
                new ProductVariant("chest", "Chest Piece", 520, 480, true, "", "Quarter Chest"),
                new ProductVariant("full_chicken", "Full Chicken", 1900, 1750, true, "", "4 Quarters")
        ));

        Product p4 = new Product("prod_4", "Dera Special BBQ Platter",
                "Seekh Kababs, Chicken Tikka, Malai Boti, Roghni Naan, and fresh salad.",
                2200, 1990, ImageKitConfig.IMG_BBQ_PLATTER, "img_food_tikka_bbq", "cat_bbq", "BBQ", true, true, true, now - 3000);
        p4.setVariants(Arrays.asList(
                new ProductVariant("half_platter", "Half Platter (2 Persons)", 1350, 1250, true, "", "2 Seekh, 4 Boti, 1 Naan"),
                new ProductVariant("full_platter", "Full Platter (4 Persons)", 2200, 1990, true, "", "4 Seekh, 1 Tikka, 6 Boti, 2 Naan")
        ));

        Product p5 = new Product("prod_5", "Beef Seekh Kabab",
                "Juicy hand-minced beef seekh kababs seasoned with roasted cumin and grilled to perfection.",
                780, 0, ImageKitConfig.IMG_SEEKH_KABAB, "img_food_tikka_bbq", "cat_bbq", "BBQ", true, false, true, now - 4000);
        p5.setVariants(Arrays.asList(
                new ProductVariant("half_doz", "4 Pcs", 780, 0, true, "", "1 Serving"),
                new ProductVariant("full_doz", "8 Pcs", 1500, 1400, true, "", "Sharing Pack")
        ));

        Product p6 = new Product("prod_6", "Crispy Zinger Crunch Burger",
                "Golden crispy chicken fillet with aged cheddar, crisp lettuce, and smoky house mayo in brioche bun.",
                650, 580, ImageKitConfig.IMG_ZINGER, "img_food_burger_wrap", "cat_burger", "Burger", true, true, true, now - 5000);
        p6.setVariants(Arrays.asList(
                new ProductVariant("regular", "Regular", 650, 580, true, "", "Single Crispy Fillet"),
                new ProductVariant("double", "Double Decker", 920, 850, true, "", "Double Fillet + Double Cheese")
        ));

        Product p7 = new Product("prod_7", "Arabian Charcoal Chicken Shawarma",
                "Flame-grilled chicken strips wrapped in warm pita with garlic toum and pickled veggies.",
                480, 420, ImageKitConfig.IMG_SHAWARMA, "img_food_burger_wrap", "cat_shawarma", "Shawarma", true, false, true, now - 6000);
        p7.setVariants(Arrays.asList(
                new ProductVariant("regular", "Regular Wrap", 480, 420, true, "", "Classic Pita Wrap"),
                new ProductVariant("large", "Large Platter", 720, 650, true, "", "Open Platter + Fries")
        ));

        Product p8 = new Product("prod_8", "Shanwari Tikka Crown Pizza",
                "Hand-tossed crust loaded with smoky chicken tikka chunks, mozzarella, jalapenos, and cheese.",
                1650, 1450, ImageKitConfig.IMG_PIZZA, "img_food_burger_wrap", "cat_pizza", "Pizza", true, true, false, now - 7000);
        p8.setVariants(Arrays.asList(
                new ProductVariant("small", "Small", 750, 680, true, "", "7-inch Pan"),
                new ProductVariant("medium", "Medium", 1150, 1050, true, "", "10-inch Pan"),
                new ProductVariant("large", "Large", 1650, 1450, true, "", "14-inch Family")
        ));

        Product p9 = new Product("prod_9", "Fresh Mint Margarita",
                "Chilled sparkling lime cooler blended with garden-fresh mint leaves and Himalayan black salt.",
                280, 0, ImageKitConfig.IMG_MINT_MARGARITA, "img_food_beverages", "cat_drinks", "Drinks", true, false, true, now - 8000);
        p9.setVariants(Arrays.asList(
                new ProductVariant("single", "Single Glass", 280, 0, true, "", "400ml Chilled"),
                new ProductVariant("pitcher", "Family Pitcher", 780, 720, true, "", "1.2 Liter")
        ));

        Product p10 = new Product("prod_10", "Creamy Peshawari Mango Lassi",
                "Thick chilled yogurt smoothie blended with sweet Sindhri mangoes and pistachio crumble.",
                350, 310, ImageKitConfig.IMG_MANGO_LASSI, "img_food_beverages", "cat_drinks", "Drinks", true, false, false, now - 9000);

        List<Product> seedProds = Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
        for (Product p : seedProds) {
            db.collection(FirebaseConfig.COL_PRODUCTS).document(p.getId()).set(FirestoreMapper.fromProduct(p, true));
        }

        List<Reward> offers = Arrays.asList(
                new Reward("offer_1", "", "Free Fresh Mint Margarita", "Redeem 150 points for a chilled Mint Margarita on your next order.", 150, "REDEEMABLE_OFFER", true, 150, "", now),
                new Reward("offer_2", "", "Free Beef Seekh Kabab (Half)", "Redeem 220 points for 2 smoky charcoal Seekh Kababs.", 220, "REDEEMABLE_OFFER", true, 220, "", now),
                new Reward("offer_3", "", "Rs. 400 Off Shanwari Karahi", "Redeem 400 points for instant Rs. 400 off any Full Karahi.", 400, "REDEEMABLE_OFFER", true, 400, "", now)
        );
        for (Reward r : offers) {
            db.collection(FirebaseConfig.COL_REWARDS).document(r.getId()).set(FirestoreMapper.fromRewardCreate(r));
        }
    }

    private static RestaurantSettings defaultSettings() {
        return new RestaurantSettings(
                "Dera Shanwari",
                ImageKitConfig.IMG_MUTTON_KARAHI,
                "03001112233",
                "923001112233",
                "Main University Road, Tehkal, Peshawar",
                "12:00 PM",
                "12:30 AM",
                150.0,
                500.0,
                10
        );
    }

    private static List<PaymentMethodConfig> defaultPaymentMethods() {
        return Arrays.asList(
                new PaymentMethodConfig("cod", PaymentMethodConfig.METHOD_COD, "Pay Rider at Doorstep", "N/A", true, "Pay cash upon delivery."),
                new PaymentMethodConfig("jazzcash", PaymentMethodConfig.METHOD_JAZZCASH, "Dera Shanwari Foods", "0300-1112233", true, "Send total to JazzCash and share screenshot on WhatsApp."),
                new PaymentMethodConfig("easypaisa", PaymentMethodConfig.METHOD_EASYPAISA, "Dera Shanwari Kitchen", "0345-8889900", true, "Send total to EasyPaisa and share screenshot on WhatsApp.")
        );
    }
}
