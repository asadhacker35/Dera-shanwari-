package com.derashanwari.rider.repository.demo;

import android.content.Context;
import android.content.SharedPreferences;

import com.derashanwari.rider.config.ImageKitConfig;
import com.derashanwari.rider.model.AdminUser;
import com.derashanwari.rider.model.Banner;
import com.derashanwari.rider.model.Category;
import com.derashanwari.rider.model.ChatMessage;
import com.derashanwari.rider.model.Customer;
import com.derashanwari.rider.model.GroupChatMember;
import com.derashanwari.rider.model.GroupChatMessage;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderEvent;
import com.derashanwari.rider.model.OrderItem;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Payment;
import com.derashanwari.rider.model.PaymentMethodConfig;
import com.derashanwari.rider.model.Product;
import com.derashanwari.rider.model.ProductVariant;
import com.derashanwari.rider.model.RestaurantSettings;
import com.derashanwari.rider.model.Reward;
import com.derashanwari.rider.model.Rider;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DemoDatabaseManager {
    private static final String PREF_NAME = "dera_shanwari_demo_db";
    private static final String KEY_DB_JSON = "db_state_v2";
    private static final String KEY_CURRENT_ADMIN = "session_admin";
    private static final String KEY_CURRENT_CUSTOMER = "session_customer";
    private static final String KEY_CURRENT_RIDER = "session_rider";

    private static DemoDatabaseManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private DemoState state;

    public static class DemoState {
        public List<Product> products = new ArrayList<>();
        public List<Category> categories = new ArrayList<>();
        public List<Banner> banners = new ArrayList<>();
        public List<Customer> customers = new ArrayList<>();
        public List<AdminUser> admins = new ArrayList<>();
        public List<Rider> riders = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public List<Payment> payments = new ArrayList<>();
        public List<PaymentMethodConfig> paymentMethods = new ArrayList<>();
        public RestaurantSettings settings;
        public List<Reward> rewards = new ArrayList<>();
        public List<Reward> redeemableOffers = new ArrayList<>();
        public List<ChatMessage> chats = new ArrayList<>();
        public List<GroupChatMessage> groupMessages = new ArrayList<>();
        public List<GroupChatMember> groupMembers = new ArrayList<>();
        public long demoGroupLastReadAt = 0L;
        public String demoCurrentUid = "";
        public int nextOrderSeq = 1005;
    }

    private DemoDatabaseManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadOrSeed();
    }

    public static synchronized DemoDatabaseManager getInstance(Context context) {
        if (instance == null) instance = new DemoDatabaseManager(context);
        return instance;
    }

    public synchronized DemoState getState() { return state; }
    public synchronized void save() { prefs.edit().putString(KEY_DB_JSON, gson.toJson(state)).apply(); }
    public synchronized void resetDemoData() { state = createSeedState(); save(); }

    public AdminUser getSessionAdmin() { String json = prefs.getString(KEY_CURRENT_ADMIN, null); return json == null ? null : gson.fromJson(json, AdminUser.class); }
    public void setSessionAdmin(AdminUser admin) { if (admin == null) prefs.edit().remove(KEY_CURRENT_ADMIN).apply(); else prefs.edit().putString(KEY_CURRENT_ADMIN, gson.toJson(admin)).apply(); }
    public Customer getSessionCustomer() { String json = prefs.getString(KEY_CURRENT_CUSTOMER, null); return json == null ? null : gson.fromJson(json, Customer.class); }
    public void setSessionCustomer(Customer customer) { if (customer == null) prefs.edit().remove(KEY_CURRENT_CUSTOMER).apply(); else prefs.edit().putString(KEY_CURRENT_CUSTOMER, gson.toJson(customer)).apply(); }
    public Rider getSessionRider() { String json = prefs.getString(KEY_CURRENT_RIDER, null); return json == null ? null : gson.fromJson(json, Rider.class); }
    public void setSessionRider(Rider rider) { if (rider == null) prefs.edit().remove(KEY_CURRENT_RIDER).apply(); else prefs.edit().putString(KEY_CURRENT_RIDER, gson.toJson(rider)).apply(); }

    private void loadOrSeed() {
        String json = prefs.getString(KEY_DB_JSON, null);
        if (json != null) {
            try {
                state = gson.fromJson(json, DemoState.class);
                if (state != null && state.products != null && !state.products.isEmpty()) return;
            } catch (Exception ignored) {}
        }
        state = createSeedState();
        save();
    }

    private DemoState createSeedState() {
        DemoState s = new DemoState();
        long now = System.currentTimeMillis();
        s.settings = new RestaurantSettings("Dera Shanwari", ImageKitConfig.IMG_MUTTON_KARAHI,
                "03001112233", "923001112233", "Main University Road, Tehkal, Peshawar",
                "12:00 PM", "12:30 AM", 150.0, 500.0, 10);
        s.admins.add(new AdminUser("admin_1", "Tariq Shanwari", "admin@derashanwari.pk", "03001112233", "Owner", true));
        s.categories.addAll(Arrays.asList(
                new Category("cat_karahi", "Karahi", ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari", true, 1),
                new Category("cat_tikka", "Chicken Tikka", ImageKitConfig.IMG_TIKKA, "img_food_tikka_bbq", true, 2),
                new Category("cat_bbq", "BBQ", ImageKitConfig.IMG_BBQ_PLATTER, "img_food_tikka_bbq", true, 3),
                new Category("cat_burger", "Burger", ImageKitConfig.IMG_ZINGER, "img_food_burger_wrap", true, 4),
                new Category("cat_shawarma", "Shawarma", ImageKitConfig.IMG_SHAWARMA, "img_food_burger_wrap", true, 5),
                new Category("cat_pizza", "Pizza", ImageKitConfig.IMG_PIZZA, "img_food_burger_wrap", true, 6),
                new Category("cat_drinks", "Drinks", ImageKitConfig.IMG_MINT_MARGARITA, "img_food_beverages", true, 7)
        ));
        s.banners.addAll(Arrays.asList(
                new Banner("ban_1", "Signature Mutton Shanwari Karahi", "Sizzling fresh tomatoes!", ImageKitConfig.IMG_BANNER_KARAHI, "img_banner_shanwari", true, 1),
                new Banner("ban_2", "Charcoal BBQ Platter Nights", "Served hot with garlic naan.", ImageKitConfig.IMG_BANNER_BBQ, "img_food_tikka_bbq", true, 2)
        ));
        Product p1 = new Product("prod_1", "Mutton Shanwari Karahi", "Authentic Peshawari lamb karahi.", 3400, 3150, ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari", "cat_karahi", "Karahi", true, true, true, now - 86400000L);
        p1.setVariants(Arrays.asList(new ProductVariant("half_kg", "Half Kg", 1800, 1650, true, "", "500g"), new ProductVariant("full_kg", "Full 1 Kg", 3400, 3150, true, "", "1 Kg")));
        s.products.add(p1);
        s.customers.addAll(Arrays.asList(
                new Customer("cust_1", "Usman Tariq", "03009876543", "House 14, Street 6, Hayatabad Phase 2, Peshawar", true, 240, now - 604800000L),
                new Customer("cust_2", "Ayesha Khan", "03331234567", "Flat 4B, Gulbahar Heights, Peshawar", true, 120, now - 432000000L)
        ));
        s.riders.addAll(Arrays.asList(
                new Rider("rider_1", "Bilal Khan", "03125556677", "Honda CD-70 (PSH-4821)", "", true, 18, 5400.0),
                new Rider("rider_2", "Hamza Afridi", "03214445566", "United 125 (PSH-9910)", "", true, 11, 3300.0)
        ));
        s.paymentMethods.addAll(Arrays.asList(
                new PaymentMethodConfig(PaymentMethodConfig.METHOD_COD, "Cash on Delivery", "Pay Rider at Doorstep", "N/A", true, "Pay cash upon delivery.")
        ));

        Order ord1 = new Order();
        ord1.setOrderId("ORD-1001"); ord1.setCustomerId("cust_1"); ord1.setCustomerName("Usman Tariq");
        ord1.setCustomerPhone("03009876543"); ord1.setDeliveryAddress("House 14, Street 6, Hayatabad Phase 2, Peshawar");
        ord1.setItems(Arrays.asList(new OrderItem("prod_1", "Mutton Shanwari Karahi", "full_kg", "Full 1 Kg", 3150, 1, "", "img_banner_shanwari")));
        ord1.setSubtotal(3150); ord1.setDeliveryFee(150); ord1.setTotalAmount(3300);
        ord1.setPaymentMethod(PaymentMethodConfig.METHOD_COD); ord1.setPaymentStatus("Paid"); ord1.setOrderStatus(OrderStatus.DELIVERED);
        ord1.setRiderId("rider_1"); ord1.setRiderName("Bilal Khan");
        ord1.setCreatedAt(now - 3600000L); ord1.setUpdatedAt(now - 1800000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 3600000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_DELIVERED, now - 1800000L);

        Order ord3 = new Order();
        ord3.setOrderId("ORD-1003"); ord3.setCustomerId("cust_1"); ord3.setCustomerName("Usman Tariq");
        ord3.setCustomerPhone("03009876543"); ord3.setDeliveryAddress("House 14, Street 6, Hayatabad Phase 2, Peshawar");
        ord3.setNotes("Call on arrival at main gate");
        ord3.setItems(Arrays.asList(new OrderItem("prod_6", "Crispy Zinger Crunch Burger", "regular", "Regular", 580, 2, "", "img_food_burger_wrap")));
        ord3.setSubtotal(1160); ord3.setDeliveryFee(150); ord3.setTotalAmount(1310);
        ord3.setPaymentMethod(PaymentMethodConfig.METHOD_COD); ord3.setPaymentStatus("Pending"); ord3.setOrderStatus(OrderStatus.ASSIGNED);
        ord3.setRiderId("rider_1"); ord3.setRiderName("Bilal Khan");
        ord3.setCreatedAt(now - 1200000L); ord3.setUpdatedAt(now - 900000L);
        ord3.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 1200000L);
        ord3.putTimestampIfAbsent(OrderEvent.TS_RIDER_ASSIGNED, now - 900000L);

        s.orders.addAll(Arrays.asList(ord3, ord1));
        s.groupMessages.addAll(Arrays.asList(
                new GroupChatMessage("gmsg_1", "admin_1", "Tariq Shanwari", "ADMIN", "",
                        "Welcome to Dera Shanwari Official Community!", "", now - 1200000L),
                new GroupChatMessage("gmsg_2", "rider_1", "Bilal Khan", "RIDER", "",
                        "Riders ready for evening rush.", "", now - 900000L)
        ));
        s.groupMembers.addAll(Arrays.asList(
                new GroupChatMember("admin_1", "Tariq Shanwari", "ADMIN", "", true, now - 60000L, now - 60000L),
                new GroupChatMember("rider_1", "Bilal Khan", "RIDER", "", true, now - 120000L, now - 120000L)
        ));
        return s;
    }
}
