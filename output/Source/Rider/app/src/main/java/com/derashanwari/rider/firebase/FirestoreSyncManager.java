package com.derashanwari.rider.firebase;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.derashanwari.rider.MainActivity;
import com.derashanwari.rider.config.ImageKitConfig;
import com.derashanwari.rider.model.AdminUser;
import com.derashanwari.rider.model.Banner;
import com.derashanwari.rider.model.Category;
import com.derashanwari.rider.model.ChatMessage;
import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.GroupChatMember;
import com.derashanwari.rider.model.GroupChatMessage;
import com.derashanwari.rider.model.NotificationItem;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderEvent;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Payment;
import com.derashanwari.rider.model.PaymentMethodConfig;
import com.derashanwari.rider.model.Product;
import com.derashanwari.rider.model.RestaurantSettings;
import com.derashanwari.rider.model.Reward;
import com.derashanwari.rider.model.Rider;
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

public class FirestoreSyncManager {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_RIDER = "RIDER";

    public interface DataChangeListener { void onDataChanged(); }
    public interface AlertListener { void onNewNotification(NotificationItem item); }
    public interface TimelineListener { void onTimelineUpdated(List<OrderEvent> events); }

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

    private FirestoreSyncManager(Context context) { this.appContext = context.getApplicationContext(); }

    public static synchronized FirestoreSyncManager getInstance(Context context) {
        if (instance == null) instance = new FirestoreSyncManager(context);
        return instance;
    }

    public void addDataChangeListener(DataChangeListener l) { if (l != null && !uiListeners.contains(l)) uiListeners.add(l); }
    public void removeDataChangeListener(DataChangeListener l) { uiListeners.remove(l); }
    public void addAlertListener(AlertListener l) { if (l != null && !alertListeners.contains(l)) alertListeners.add(l); }
    public void removeAlertListener(AlertListener l) { alertListeners.remove(l); }

    private void notifyUi() {
        mainHandler.post(() -> { for (DataChangeListener l : uiListeners) l.onDataChanged(); });
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

        activeListeners.add(db.collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_RESTAURANT_SETTINGS)
                .addSnapshotListener((doc, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("get", "settings/restaurant", err); return; }
                    if (doc != null && doc.exists()) {
                        synchronized (FirestoreSyncManager.this) { settings = FirestoreMapper.toSettings(doc); }
                        notifyUi();
                    }
                }));

        if (ROLE_RIDER.equals(role)) {
            attachRiderStreams(db, uid);
        } else if (ROLE_CUSTOMER.equals(role)) {
            attachCustomerStreams(db, uid);
        } else if (ROLE_ADMIN.equals(role)) {
            attachAdminStreams(db);
        }
        attachGroupChatStreams(db, uid);
    }

    private void attachGroupChatStreams(FirebaseFirestore db, String uid) {
        activeListeners.add(db.collection(FirebaseConfig.COL_CHAT_ROOMS)
                .document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MESSAGES)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", "chatRooms/mainGroup/messages", err); return; }
                    if (snap != null) {
                        synchronized (FirestoreSyncManager.this) {
                            groupMessages.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) groupMessages.add(FirestoreMapper.toGroupMessage(d));
                            groupMessages.sort(Comparator.comparingLong(GroupChatMessage::getCreatedAt));
                        }
                        notifyUi();
                    }
                }));
        activeListeners.add(db.collection(FirebaseConfig.COL_CHAT_ROOMS)
                .document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MEMBERS)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", "chatRooms/mainGroup/members", err); return; }
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
            if (err != null) return;
            if (snap != null) {
                synchronized (this) {
                    orders.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) orders.add(FirestoreMapper.toOrder(d));
                    orders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                }
                notifyUi();
            }
        }));
    }

    private void attachCustomerStreams(FirebaseFirestore db, String uid) {
        activeListeners.add(db.collection(FirebaseConfig.COL_ORDERS)
                .whereEqualTo("customerId", uid)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) return;
                    if (snap != null) {
                        synchronized (this) {
                            orders.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) orders.add(FirestoreMapper.toOrder(d));
                            orders.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        }
                        notifyUi();
                    }
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
            if (notificationsPrimed && !n.isRead() && !seenNotifIds.contains(n.getId())) freshAlerts.add(n);
            seenNotifIds.add(n.getId());
        }
        parsed.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        synchronized (this) { notifications.clear(); notifications.addAll(parsed); }
        notificationsPrimed = true;
        for (NotificationItem alert : freshAlerts) {
            Intent launch = new Intent(appContext, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            RealTimeNotificationHelper.showAlert(appContext, alert.getId() != null ? alert.getId() : "0",
                    alert.getTitle(), alert.getBody(), launch);
            mainHandler.post(() -> { for (AlertListener l : alertListeners) l.onNewNotification(alert); });
        }
        notifyUi();
    }

    public synchronized void stopSync() {
        for (ListenerRegistration reg : activeListeners) { try { reg.remove(); } catch (Exception ignored) {} }
        activeListeners.clear(); seenNotifIds.clear(); persistedCustomerIds.clear(); persistedRiderIds.clear();
        notificationsPrimed = false; currentRole = ""; currentUid = "";
        currentAdmin = null; currentCustomer = null; currentRider = null;
        groupMessages.clear(); groupMembers.clear(); myGroupLastReadAt = 0L;
    }

    public synchronized AdminUser getCurrentAdmin() { return currentAdmin; }
    public synchronized void setCurrentAdmin(AdminUser admin) { this.currentAdmin = admin; }
    public synchronized Customer getCurrentCustomer() { return currentCustomer; }
    public synchronized void setCurrentCustomer(Customer c) { this.currentCustomer = c; }
    public synchronized void markCustomerPersisted(String id) { if (id != null && !id.isEmpty()) persistedCustomerIds.add(id); }
    public synchronized Rider getCurrentRider() { return currentRider; }
    public synchronized void setCurrentRider(Rider r) { this.currentRider = r; }
    public synchronized void markRiderPersisted(String id) { if (id != null && !id.isEmpty()) persistedRiderIds.add(id); }

    public synchronized List<Product> getProducts() { return new ArrayList<>(products); }
    public synchronized List<Category> getCategories() { return new ArrayList<>(categories); }
    public synchronized List<Banner> getBanners() { return new ArrayList<>(banners); }
    public synchronized List<Order> getOrders() { return new ArrayList<>(orders); }
    public synchronized List<Payment> getPayments() { return new ArrayList<>(payments); }
    public synchronized List<Customer> getCustomers() { return new ArrayList<>(customers); }
    public synchronized List<Rider> getRiders() { return new ArrayList<>(riders); }
    public synchronized List<PaymentMethodConfig> getPaymentMethods() { return new ArrayList<>(paymentMethods); }
    public synchronized RestaurantSettings getSettings() { return settings != null ? settings : defaultSettings(); }
    public synchronized List<ChatMessage> getChats() { return new ArrayList<>(chats); }
    public synchronized List<GroupChatMessage> getGroupMessages() { return new ArrayList<>(groupMessages); }
    public synchronized List<GroupChatMember> getGroupMembers() { return new ArrayList<>(groupMembers); }
    public synchronized int getUnreadGroupChatCount() {
        int count = 0;
        for (GroupChatMessage m : groupMessages) if (!currentUid.equals(m.getSenderId()) && m.getCreatedAt() > myGroupLastReadAt) count++;
        return count;
    }
    public synchronized List<Reward> getRewardHistory() { return new ArrayList<>(rewardHistory); }
    public synchronized List<Reward> getRedeemableOffers() { return new ArrayList<>(redeemableOffers); }
    public synchronized List<NotificationItem> getNotifications() { return new ArrayList<>(notifications); }
    public synchronized int getUnreadNotificationCount() {
        int c = 0; for (NotificationItem n : notifications) if (!n.isRead()) c++; return c;
    }

    public synchronized void saveProduct(Product p) {}
    public synchronized void deleteProduct(String id) {}
    public synchronized void saveCategory(Category c) {}
    public synchronized void deleteCategory(String id) {}
    public synchronized void saveBanner(Banner b) {}
    public synchronized void deleteBanner(String id) {}
    public synchronized Order placeOrder(Order o) { return o; }

    public synchronized void saveSettings(RestaurantSettings s) {
        this.settings = s;
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_SETTINGS)
                .document(FirebaseConfig.DOC_RESTAURANT_SETTINGS)
                .set(FirestoreMapper.fromSettings(s))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("set", "settings/restaurant", e));
        notifyUi();
    }

    public synchronized void savePaymentMethod(PaymentMethodConfig pm) {}
    public synchronized void saveCustomer(Customer c) {}
    public synchronized void setCustomerActive(String id, boolean active) {}
    public synchronized void addLoyaltyPoints(String id, int pts, String title, String orderId) {}
    public synchronized boolean redeemOffer(String id, Reward offer) { return false; }

    public synchronized boolean updateOrderStatus(String orderId, String newStatus) {
        String evtType = OrderStatus.eventTypeForStatus(newStatus);
        String tsKey = OrderStatus.timestampKeyForStatus(newStatus);
        String actorUid = FirebaseConfig.getAuth(appContext).getUid();
        if (actorUid == null) actorUid = currentUid;
        return advanceOrderStage(orderId, newStatus, evtType, tsKey, actorUid, ROLE_RIDER, resolveActorDisplayName(ROLE_RIDER), "");
    }

    public synchronized boolean advanceOrderStage(String orderId, String newStatus, String eventType,
                                                  String timestampKey, String userId, String userRole,
                                                  String userName, String notes) {
        Order target = null;
        for (Order o : orders) if (o.getOrderId().equals(orderId)) { target = o; break; }
        if (target == null) return false;

        String oldStatus = target.getOrderStatus();
        long now = System.currentTimeMillis();
        if (newStatus != null && !newStatus.isEmpty()) target.setOrderStatus(newStatus);
        target.setUpdatedAt(now);

        Map<String, Object> updates = new HashMap<>();
        if (newStatus != null && !newStatus.isEmpty()) updates.put("orderStatus", newStatus);
        if (timestampKey != null && !timestampKey.isEmpty() && !target.hasTimestamp(timestampKey)) {
            target.putTimestampIfAbsent(timestampKey, now);
            updates.put("timestamps." + timestampKey, FieldValue.serverTimestamp());
        }
        if (OrderStatus.DELIVERED.equalsIgnoreCase(newStatus) && !OrderStatus.DELIVERED.equalsIgnoreCase(oldStatus)) {
            target.setPaymentStatus("Paid");
            updates.put("paymentStatus", "Paid");
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        db.collection(FirebaseConfig.COL_ORDERS).document(orderId).update(updates)
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("update", "orders/" + orderId, e));

        if (eventType != null && !eventType.isEmpty() && !hasTimelineEvent(target, eventType)) {
            String uid = (userId != null && !userId.isEmpty()) ? userId : currentUid;
            String role = (userRole != null && !userRole.isEmpty()) ? userRole : ROLE_RIDER;
            String uname = (userName != null && !userName.isEmpty()) ? userName : resolveActorDisplayName(role);
            OrderEvent evt = new OrderEvent(eventType, orderId, eventType, now, uid, role, uname, notes);
            target.addTimelineEventIfAbsent(evt);
            db.collection(FirebaseConfig.COL_ORDERS).document(orderId)
                    .collection(FirebaseConfig.SUB_TIMELINE).document(eventType)
                    .set(FirestoreMapper.fromTimelineEventCreate(evt))
                    .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "orders/" + orderId + "/timeline/" + eventType, e));
        }

        if (OrderStatus.DELIVERED.equalsIgnoreCase(newStatus) && !OrderStatus.DELIVERED.equalsIgnoreCase(oldStatus)) {
            syncPaymentPaidForOrder(db, orderId);
            if (target.getRiderId() != null && !target.getRiderId().isEmpty()) {
                incrementRiderDeliveryStats(target.getRiderId(), target.getDeliveryFee() > 0 ? target.getDeliveryFee() : 150.0);
            }
        }

        String sender = FirebaseConfig.getAuth(appContext).getUid();
        if (sender != null && newStatus != null && !newStatus.isEmpty()) {
            if (target.getCustomerId() != null && !target.getCustomerId().isEmpty()) {
                sendNotification(ROLE_CUSTOMER, target.getCustomerId(), sender, orderId,
                        "Order " + orderId + " • " + newStatus,
                        "Your Dera Shanwari order status is now: " + newStatus, "ORDER_STATUS");
            }
            sendNotification(ROLE_ADMIN, "", sender, orderId,
                    "Rider Update • " + orderId,
                    resolveActorDisplayName(ROLE_RIDER) + " marked order " + newStatus, "RIDER_UPDATE");
        }
        notifyUi();
        return true;
    }

    public synchronized boolean assignRider(String orderId, String riderId, String riderName) { return false; }
    public synchronized boolean recordOrderPrint(String orderId, String printedBy) { return false; }

    public ListenerRegistration subscribeOrderTimeline(String orderId, TimelineListener listener) {
        if (orderId == null || orderId.isEmpty() || listener == null) return null;
        return FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_ORDERS).document(orderId)
                .collection(FirebaseConfig.SUB_TIMELINE)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) { FirestoreErrorLogger.logError("list", "orders/" + orderId + "/timeline", err); return; }
                    if (snap != null) {
                        List<OrderEvent> events = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) events.add(FirestoreMapper.toTimelineEvent(d));
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
        for (OrderEvent e : order.getTimeline()) if (eventType.equals(e.getEventType())) return true;
        return false;
    }

    private String resolveActorDisplayName(String role) {
        if (ROLE_RIDER.equals(role) && currentRider != null && currentRider.getName() != null) return currentRider.getName();
        FirebaseUser fb = FirebaseConfig.getAuth(appContext).getCurrentUser();
        if (fb != null && fb.getDisplayName() != null && !fb.getDisplayName().isEmpty()) return fb.getDisplayName();
        return "Delivery Rider";
    }

    public synchronized boolean updatePaymentStatus(String paymentId, String status) { return false; }

    private void syncPaymentPaidForOrder(FirebaseFirestore db, String orderId) {
        String detPayId = "pay_" + orderId;
        Map<String, Object> pm = new HashMap<>();
        pm.put("status", "Paid");
        pm.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(FirebaseConfig.COL_PAYMENTS).document(detPayId).update(pm);
    }

    public synchronized void saveRider(Rider r) {
        if (r.getId() == null || r.getId().trim().isEmpty()) return;
        if (currentRider != null && currentRider.getId().equals(r.getId())) currentRider = r;
        FirebaseFirestore db = FirebaseConfig.getFirestore(appContext);
        if (!persistedRiderIds.contains(r.getId())) {
            db.collection(FirebaseConfig.COL_RIDERS).document(r.getId())
                    .set(FirestoreMapper.fromRiderCreate(r))
                    .addOnSuccessListener(unused -> persistedRiderIds.add(r.getId()));
        } else {
            Map<String, Object> m = new HashMap<>();
            m.put("name", r.getName()); m.put("phone", r.getPhone()); m.put("vehicleInfo", r.getVehicleInfo());
            m.put("active", r.isActive()); m.put("updatedAt", FieldValue.serverTimestamp());
            db.collection(FirebaseConfig.COL_RIDERS).document(r.getId()).update(m);
        }
        notifyUi();
    }

    public synchronized void setRiderActive(String riderId, boolean active) {
        if (currentRider != null && currentRider.getId().equals(riderId)) currentRider.setActive(active);
        Map<String, Object> m = new HashMap<>();
        m.put("active", active); m.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_RIDERS).document(riderId).update(m);
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

    public synchronized void sendChatMessage(ChatMessage msg) {
        String id = "msg_" + System.currentTimeMillis();
        msg.setId(id); msg.setTimestamp(System.currentTimeMillis());
        if (msg.getSenderId() == null || msg.getSenderId().isEmpty()) {
            String uid = FirebaseConfig.getAuth(appContext).getUid();
            msg.setSenderId(uid != null ? uid : "");
        }
        for (Order o : orders) {
            if (o.getOrderId().equals(msg.getOrderId())) {
                if (msg.getCustomerId() == null || msg.getCustomerId().isEmpty()) msg.setCustomerId(o.getCustomerId());
                if (msg.getRiderId() == null || msg.getRiderId().isEmpty()) msg.setRiderId(o.getRiderId());
                break;
            }
        }
        chats.add(msg);
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_CHATS).document(id)
                .set(FirestoreMapper.fromChat(msg));
        notifyUi();
    }

    public synchronized void sendGroupChatMessage(String text, String imageUrl) {
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        String uid = fbUser != null ? fbUser.getUid() : currentUid;
        if (uid == null || uid.isEmpty()) return;
        String safeText = text != null ? text.trim() : "";
        String safeImg = imageUrl != null ? imageUrl.trim() : "";
        if (safeText.isEmpty() && safeImg.isEmpty()) return;
        String name = resolveActorDisplayName(ROLE_RIDER);
        String avatar = currentRider != null ? currentRider.getProfileImageUrl() : "";
        String msgId = "gmsg_" + System.currentTimeMillis() + "_" + Math.abs(uid.hashCode() % 1000);
        GroupChatMessage msg = new GroupChatMessage(msgId, uid, name, ROLE_RIDER, avatar, safeText, safeImg, System.currentTimeMillis());
        groupMessages.add(msg);
        myGroupLastReadAt = Math.max(myGroupLastReadAt, msg.getCreatedAt());
        FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_CHAT_ROOMS).document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MESSAGES).document(msgId)
                .set(FirestoreMapper.fromGroupMessage(msg))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("create", "chatRooms/mainGroup/messages/" + msgId, e));
        markGroupChatRead();
        notifyUi();
    }

    public synchronized void deleteGroupChatMessage(String messageId) {}

    public synchronized void setGroupPresence(boolean online) { updateGroupMemberRecord(online, false); }

    public synchronized void markGroupChatRead() {
        myGroupLastReadAt = System.currentTimeMillis();
        updateGroupMemberRecord(true, true);
        notifyUi();
    }

    private void updateGroupMemberRecord(boolean online, boolean markReadNow) {
        FirebaseUser fbUser = FirebaseConfig.getAuth(appContext).getCurrentUser();
        String uid = fbUser != null ? fbUser.getUid() : currentUid;
        if (uid == null || uid.isEmpty()) return;
        String name = resolveActorDisplayName(ROLE_RIDER);
        String avatar = currentRider != null ? currentRider.getProfileImageUrl() : "";
        long now = System.currentTimeMillis();
        long readAt = markReadNow ? now : (myGroupLastReadAt > 0L ? myGroupLastReadAt : now);
        GroupChatMember member = new GroupChatMember(uid, name, ROLE_RIDER, avatar, online, now, readAt);
        boolean found = false;
        for (int i = 0; i < groupMembers.size(); i++) {
            if (uid.equals(groupMembers.get(i).getUid())) { groupMembers.set(i, member); found = true; break; }
        }
        if (!found) groupMembers.add(member);
        FirebaseConfig.getFirestore(appContext)
                .collection(FirebaseConfig.COL_CHAT_ROOMS).document(FirebaseConfig.DOC_MAIN_GROUP)
                .collection(FirebaseConfig.SUB_MEMBERS).document(uid)
                .set(FirestoreMapper.fromGroupMember(member, markReadNow))
                .addOnFailureListener(e -> FirestoreErrorLogger.logError("set", "chatRooms/mainGroup/members/" + uid, e));
    }

    public synchronized void sendNotification(String targetRole, String targetUid, String senderId,
                                               String orderId, String title, String body, String type) {
        if (senderId == null || senderId.isEmpty()) return;
        String id = "notif_" + System.currentTimeMillis() + "_" + Math.abs((targetRole + orderId).hashCode() % 1000);
        NotificationItem item = new NotificationItem(id, targetRole, targetUid, senderId, orderId, title, body, type, false, System.currentTimeMillis());
        FirebaseConfig.getFirestore(appContext).collection(FirebaseConfig.COL_NOTIFICATIONS).document(id)
                .set(FirestoreMapper.fromNotificationCreate(item));
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

    private static RestaurantSettings defaultSettings() {
        return new RestaurantSettings("Dera Shanwari", ImageKitConfig.IMG_MUTTON_KARAHI,
                "03001112233", "923001112233", "Main University Road, Tehkal, Peshawar",
                "12:00 PM", "12:30 AM", 150.0, 500.0, 10);
    }
}
