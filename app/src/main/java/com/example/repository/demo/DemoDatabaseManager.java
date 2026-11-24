package com.example.repository.demo;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.config.ImageKitConfig;
import com.example.model.AdminUser;
import com.example.model.Banner;
import com.example.model.Category;
import com.example.model.ChatMessage;
import com.example.model.Customer;
import com.example.model.GroupChatMember;
import com.example.model.GroupChatMessage;
import com.example.model.Order;
import com.example.model.OrderEvent;
import com.example.model.OrderItem;
import com.example.model.OrderStatus;
import com.example.model.Payment;
import com.example.model.PaymentMethodConfig;
import com.example.model.Product;
import com.example.model.ProductVariant;
import com.example.model.RestaurantSettings;
import com.example.model.Reward;
import com.example.model.Rider;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Local JSON-backed Demo Database persisted in SharedPreferences for tests/demo state.
 */
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
        if (instance == null) {
            instance = new DemoDatabaseManager(context);
        }
        return instance;
    }

    public synchronized DemoState getState() {
        return state;
    }

    public synchronized void save() {
        prefs.edit().putString(KEY_DB_JSON, gson.toJson(state)).apply();
    }

    public synchronized void resetDemoData() {
        state = createSeedState();
        save();
    }

    public AdminUser getSessionAdmin() {
        String json = prefs.getString(KEY_CURRENT_ADMIN, null);
        return json == null ? null : gson.fromJson(json, AdminUser.class);
    }

    public void setSessionAdmin(AdminUser admin) {
        if (admin == null) prefs.edit().remove(KEY_CURRENT_ADMIN).apply();
        else prefs.edit().putString(KEY_CURRENT_ADMIN, gson.toJson(admin)).apply();
    }

    public Customer getSessionCustomer() {
        String json = prefs.getString(KEY_CURRENT_CUSTOMER, null);
        return json == null ? null : gson.fromJson(json, Customer.class);
    }

    public void setSessionCustomer(Customer customer) {
        if (customer == null) prefs.edit().remove(KEY_CURRENT_CUSTOMER).apply();
        else prefs.edit().putString(KEY_CURRENT_CUSTOMER, gson.toJson(customer)).apply();
    }

    public Rider getSessionRider() {
        String json = prefs.getString(KEY_CURRENT_RIDER, null);
        return json == null ? null : gson.fromJson(json, Rider.class);
    }

    public void setSessionRider(Rider rider) {
        if (rider == null) prefs.edit().remove(KEY_CURRENT_RIDER).apply();
        else prefs.edit().putString(KEY_CURRENT_RIDER, gson.toJson(rider)).apply();
    }

    private void loadOrSeed() {
        String json = prefs.getString(KEY_DB_JSON, null);
        if (json != null) {
            try {
                state = gson.fromJson(json, DemoState.class);
                if (state != null && state.products != null && !state.products.isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        state = createSeedState();
        save();
    }

    private DemoState createSeedState() {
        DemoState s = new DemoState();
        long now = System.currentTimeMillis();

        s.settings = new RestaurantSettings(
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

        s.admins.add(new AdminUser(
                "admin_1", "Tariq Shanwari", "admin@derashanwari.pk", "03001112233", "Owner", true));

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
                new Banner("ban_1", "Signature Mutton Shanwari Karahi",
                        "Sizzling fresh tomatoes, black pepper & pure desi ghee - Rs. 200 Off Full Karahi!",
                        ImageKitConfig.IMG_BANNER_KARAHI, "img_banner_shanwari", true, 1),
                new Banner("ban_2", "Charcoal BBQ Platter Nights",
                        "Malai Boti, Seekh Kabab & Chicken Tikka served piping hot with garlic naan.",
                        ImageKitConfig.IMG_BANNER_BBQ, "img_food_tikka_bbq", true, 2)
        ));

        Product p1 = new Product("prod_1", "Mutton Shanwari Karahi",
                "Authentic Peshawari lamb karahi cooked in cast-iron wok with fresh tomatoes, green chilies, and julienned ginger.",
                3400, 3150, ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari",
                "cat_karahi", "Karahi", true, true, true, now - 86400000L);
        p1.setVariants(Arrays.asList(
                new ProductVariant("half_kg", "Half Kg", 1800, 1650, true, "", "500g Bone-in Mutton"),
                new ProductVariant("full_kg", "Full 1 Kg", 3400, 3150, true, "", "1 Kg Family Wok")
        ));
        Product p2 = new Product("prod_2", "Chicken Shanwari Karahi",
                "Tender bone-in chicken simmered in rich tomato gravy with crushed black pepper and fresh coriander.",
                1950, 1750, ImageKitConfig.IMG_CHICKEN_KARAHI, "img_banner_shanwari",
                "cat_karahi", "Karahi", true, true, true, now - 80000000L);
        p2.setVariants(Arrays.asList(
                new ProductVariant("half", "Half", 1050, 950, true, "", "Serves 2"),
                new ProductVariant("full", "Full", 1950, 1750, true, "", "Serves 4")
        ));
        Product p3 = new Product("prod_3", "Charcoal Chicken Tikka",
                "Smoky quarter marinated in Shanwari spices, grilled over live charcoal with mint raita.",
                520, 480, ImageKitConfig.IMG_TIKKA, "img_food_tikka_bbq",
                "cat_tikka", "Chicken Tikka", true, true, true, now - 70000000L);
        Product p4 = new Product("prod_4", "Dera Special BBQ Platter",
                "4 Seekh Kababs, 1 Chicken Tikka, 6 Malai Boti pieces, 2 Roghni Naan, and fresh salad.",
                2200, 1990, ImageKitConfig.IMG_BBQ_PLATTER, "img_food_tikka_bbq",
                "cat_bbq", "BBQ", true, true, true, now - 60000000L);
        Product p5 = new Product("prod_5", "Beef Seekh Kabab (4 Pcs)",
                "Juicy hand-minced beef seekh kababs seasoned with roasted cumin and grilled to perfection.",
                780, 0, ImageKitConfig.IMG_SEEKH_KABAB, "img_food_tikka_bbq",
                "cat_bbq", "BBQ", true, false, true, now - 50000000L);
        Product p6 = new Product("prod_6", "Crispy Zinger Crunch Burger",
                "Golden crispy chicken fillet with aged cheddar, crisp lettuce, and smoky house mayo in brioche bun.",
                650, 580, ImageKitConfig.IMG_ZINGER, "img_food_burger_wrap",
                "cat_burger", "Burger", true, true, true, now - 40000000L);
        p6.setVariants(Arrays.asList(
                new ProductVariant("regular", "Regular", 650, 580, true, "", "Single Crispy Fillet"),
                new ProductVariant("double", "Double", 920, 850, true, "", "Double Crispy Fillet")
        ));
        Product p7 = new Product("prod_7", "Arabian Charcoal Chicken Shawarma",
                "Flame-grilled chicken strips wrapped in warm pita with garlic toum and pickled veggies.",
                480, 420, ImageKitConfig.IMG_SHAWARMA, "img_food_burger_wrap",
                "cat_shawarma", "Shawarma", true, false, true, now - 30000000L);
        Product p8 = new Product("prod_8", "Shanwari Tikka Crown Pizza",
                "Hand-tossed crust loaded with smoky chicken tikka chunks, mozzarella, jalapenos, and cheese.",
                1650, 1450, ImageKitConfig.IMG_PIZZA, "img_food_burger_wrap",
                "cat_pizza", "Pizza", true, true, false, now - 25000000L);
        p8.setVariants(Arrays.asList(
                new ProductVariant("small", "Small", 750, 680, true, "", "7 inch"),
                new ProductVariant("medium", "Medium", 1150, 1050, true, "", "10 inch"),
                new ProductVariant("large", "Large", 1650, 1450, true, "", "14 inch")
        ));
        Product p9 = new Product("prod_9", "Fresh Mint Margarita",
                "Chilled sparkling lime cooler blended with garden-fresh mint leaves and Himalayan black salt.",
                280, 0, ImageKitConfig.IMG_MINT_MARGARITA, "img_food_beverages",
                "cat_drinks", "Drinks", true, false, true, now - 20000000L);
        Product p10 = new Product("prod_10", "Creamy Peshawari Mango Lassi",
                "Thick chilled yogurt smoothie blended with sweet Sindhri mangoes and pistachio crumble.",
                350, 310, ImageKitConfig.IMG_MANGO_LASSI, "img_food_beverages",
                "cat_drinks", "Drinks", true, false, false, now - 15000000L);

        s.products.addAll(Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10));

        s.customers.addAll(Arrays.asList(
                new Customer("cust_1", "Usman Tariq", "03009876543", "House 14, Street 6, Hayatabad Phase 2, Peshawar", true, 240, now - 604800000L),
                new Customer("cust_2", "Ayesha Khan", "03331234567", "Flat 4B, Gulbahar Heights, Peshawar", true, 120, now - 432000000L),
                new Customer("cust_3", "Saad Afridi", "03457654321", "Sector E-4, Phase 1, Hayatabad", true, 65, now - 172800000L)
        ));

        s.riders.addAll(Arrays.asList(
                new Rider("rider_1", "Bilal Khan", "03125556677", "Honda CD-70 (PSH-4821)", "", true, 18, 5400.0),
                new Rider("rider_2", "Hamza Afridi", "03214445566", "United 125 (PSH-9910)", "", true, 11, 3300.0),
                new Rider("rider_3", "Zain Abbas", "03337778899", "Honda CG-125 (PSH-1104)", "", false, 4, 1200.0)
        ));

        s.paymentMethods.addAll(Arrays.asList(
                new PaymentMethodConfig(PaymentMethodConfig.METHOD_COD, "Cash on Delivery",
                        "Pay Rider at Doorstep", "N/A", true, "Pay cash upon delivery."),
                new PaymentMethodConfig(PaymentMethodConfig.METHOD_JAZZCASH, "JazzCash",
                        "Dera Shanwari Foods", "0300-1112233", true, "Send total to our JazzCash number and share screenshot on WhatsApp."),
                new PaymentMethodConfig(PaymentMethodConfig.METHOD_EASYPAISA, "EasyPaisa",
                        "Dera Shanwari Kitchen", "0345-8889900", true, "Send total to our EasyPaisa account and share screenshot on WhatsApp.")
        ));

        Order ord1 = new Order();
        ord1.setOrderId("ORD-1001");
        ord1.setCustomerId("cust_1");
        ord1.setCustomerName("Usman Tariq");
        ord1.setCustomerPhone("03009876543");
        ord1.setDeliveryAddress("House 14, Street 6, Hayatabad Phase 2, Peshawar");
        ord1.setNotes("Extra ginger and green chilies please");
        ord1.setItems(Arrays.asList(
                new OrderItem("prod_1", "Mutton Shanwari Karahi", "full_kg", "Full 1 Kg", 3150, 1, "", "img_banner_shanwari"),
                new OrderItem("prod_9", "Fresh Mint Margarita", 280, 2, "", "img_food_beverages")
        ));
        ord1.setSubtotal(3710);
        ord1.setDeliveryFee(150);
        ord1.setTotalAmount(3860);
        ord1.setPaymentMethod(PaymentMethodConfig.METHOD_COD);
        ord1.setPaymentStatus("Paid");
        ord1.setOrderStatus(OrderStatus.DELIVERED);
        ord1.setRiderId("rider_1");
        ord1.setRiderName("Bilal Khan");
        ord1.setCreatedAt(now - 3600000L);
        ord1.setUpdatedAt(now - 1800000L);
        ord1.setPointsEarned(380);
        ord1.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 3600000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_CONFIRMED, now - 3480000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_RIDER_ASSIGNED, now - 3300000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_RIDER_ACCEPTED, now - 3180000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_SENT_TO_KITCHEN, now - 3100000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_PREP_STARTED, now - 3000000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_PREP_COMPLETED, now - 2400000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_OUT_FOR_DELIVERY, now - 2280000L);
        ord1.putTimestampIfAbsent(OrderEvent.TS_DELIVERED, now - 1800000L);

        Order ord2 = new Order();
        ord2.setOrderId("ORD-1002");
        ord2.setCustomerId("cust_2");
        ord2.setCustomerName("Ayesha Khan");
        ord2.setCustomerPhone("03331234567");
        ord2.setDeliveryAddress("Flat 4B, Gulbahar Heights, Peshawar");
        ord2.setNotes("Ring bell twice");
        ord2.setItems(Collections.singletonList(
                new OrderItem("prod_4", "Dera Special BBQ Platter", 1990, 1, "", "img_food_tikka_bbq")
        ));
        ord2.setSubtotal(1990);
        ord2.setDeliveryFee(150);
        ord2.setTotalAmount(2140);
        ord2.setPaymentMethod(PaymentMethodConfig.METHOD_JAZZCASH);
        ord2.setPaymentStatus("Pending Verification");
        ord2.setOrderStatus(OrderStatus.PREPARING);
        ord2.setCreatedAt(now - 1800000L);
        ord2.setUpdatedAt(now - 900000L);
        ord2.setPointsEarned(210);
        ord2.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 1800000L);
        ord2.putTimestampIfAbsent(OrderEvent.TS_CONFIRMED, now - 1650000L);
        ord2.putTimestampIfAbsent(OrderEvent.TS_PREP_STARTED, now - 900000L);

        Order ord3 = new Order();
        ord3.setOrderId("ORD-1003");
        ord3.setCustomerId("cust_1");
        ord3.setCustomerName("Usman Tariq");
        ord3.setCustomerPhone("03009876543");
        ord3.setDeliveryAddress("House 14, Street 6, Hayatabad Phase 2, Peshawar");
        ord3.setNotes("Call on arrival at main gate");
        ord3.setItems(Arrays.asList(
                new OrderItem("prod_6", "Crispy Zinger Crunch Burger", "regular", "Regular", 580, 2, "", "img_food_burger_wrap"),
                new OrderItem("prod_10", "Creamy Peshawari Mango Lassi", 310, 2, "", "img_food_beverages")
        ));
        ord3.setSubtotal(1780);
        ord3.setDeliveryFee(150);
        ord3.setTotalAmount(1930);
        ord3.setPaymentMethod(PaymentMethodConfig.METHOD_COD);
        ord3.setPaymentStatus("Pending");
        ord3.setOrderStatus(OrderStatus.ASSIGNED);
        ord3.setRiderId("rider_1");
        ord3.setRiderName("Bilal Khan");
        ord3.setCreatedAt(now - 1200000L);
        ord3.setUpdatedAt(now - 900000L);
        ord3.setPointsEarned(190);
        ord3.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 1200000L);
        ord3.putTimestampIfAbsent(OrderEvent.TS_CONFIRMED, now - 1080000L);
        ord3.putTimestampIfAbsent(OrderEvent.TS_RIDER_ASSIGNED, now - 900000L);

        Order ord4 = new Order();
        ord4.setOrderId("ORD-1004");
        ord4.setCustomerId("cust_3");
        ord4.setCustomerName("Saad Afridi");
        ord4.setCustomerPhone("03457654321");
        ord4.setDeliveryAddress("Sector E-4, Phase 1, Hayatabad");
        ord4.setNotes("Less spicy please");
        ord4.setItems(Collections.singletonList(
                new OrderItem("prod_2", "Chicken Shanwari Karahi", "full", "Full", 1750, 1, "", "img_banner_shanwari")
        ));
        ord4.setSubtotal(1750);
        ord4.setDeliveryFee(150);
        ord4.setTotalAmount(1900);
        ord4.setPaymentMethod(PaymentMethodConfig.METHOD_EASYPAISA);
        ord4.setPaymentStatus("Pending Verification");
        ord4.setOrderStatus(OrderStatus.PENDING);
        ord4.setCreatedAt(now - 420000L);
        ord4.setUpdatedAt(now - 420000L);
        ord4.setPointsEarned(190);
        ord4.putTimestampIfAbsent(OrderEvent.TS_PLACED, now - 420000L);

        s.orders.addAll(Arrays.asList(ord4, ord3, ord2, ord1));

        s.payments.addAll(Arrays.asList(
                new Payment("pay_1", "ORD-1001", PaymentMethodConfig.METHOD_COD, "Cash on Delivery", "-", 3860, "Paid", now - 82800000L),
                new Payment("pay_2", "ORD-1002", PaymentMethodConfig.METHOD_JAZZCASH, "Dera Shanwari Foods", "0300-1112233", 2140, "Pending Verification", now - 3600000L),
                new Payment("pay_3", "ORD-1003", PaymentMethodConfig.METHOD_COD, "Cash on Delivery", "-", 1930, "Pending", now - 1800000L),
                new Payment("pay_4", "ORD-1004", PaymentMethodConfig.METHOD_EASYPAISA, "Dera Shanwari Kitchen", "0345-8889900", 1900, "Pending Verification", now - 600000L)
        ));

        s.rewards.addAll(Arrays.asList(
                new Reward("rew_1", "cust_1", "Welcome Bonus", "Joined Dera Shanwari Rewards", 50, "EARNED", false, 0, "", now - 604800000L),
                new Reward("rew_2", "cust_1", "Order #ORD-1001 Completed", "Points earned on Mutton Shanwari Karahi order", 190, "EARNED", false, 0, "ORD-1001", now - 82800000L)
        ));

        s.redeemableOffers.addAll(Arrays.asList(
                new Reward("offer_1", "", "Free Fresh Mint Margarita", "Redeem 150 points for a chilled Mint Margarita on your next order.", 150, "REDEEMABLE_OFFER", true, 150, "", now),
                new Reward("offer_2", "", "Free Beef Seekh Kabab (Half)", "Redeem 220 points for 2 smoky charcoal Seekh Kababs.", 220, "REDEEMABLE_OFFER", true, 220, "", now),
                new Reward("offer_3", "", "Rs. 400 Off Shanwari Karahi", "Redeem 400 points for instant Rs. 400 off any Full Karahi.", 400, "REDEEMABLE_OFFER", true, 400, "", now)
        ));

        s.chats.addAll(Arrays.asList(
                new ChatMessage("msg_1", "ORD-1003", "admin_1", "ADMIN", "Dera Kitchen", "Your Zinger & Mango Lassi order is packed and handed to Bilal!", now - 1000000L),
                new ChatMessage("msg_2", "ORD-1003", "rider_1", "RIDER", "Bilal Khan", "Assalam-o-Alaikum, I am heading toward Hayatabad Phase 2 now.", now - 850000L)
        ));

        s.groupMessages.addAll(Arrays.asList(
                new GroupChatMessage("gmsg_1", "admin_1", "Tariq Shanwari", "ADMIN", "",
                        "Welcome to Dera Shanwari Official Community! Fresh Mutton Karahi is live now.", "", now - 1200000L),
                new GroupChatMessage("gmsg_2", "rider_1", "Bilal Khan", "RIDER", "",
                        "Riders on duty across Hayatabad & University Road.", "", now - 900000L)
        ));
        s.groupMembers.addAll(Arrays.asList(
                new GroupChatMember("admin_1", "Tariq Shanwari", "ADMIN", "", true, now - 60000L, now - 60000L),
                new GroupChatMember("rider_1", "Bilal Khan", "RIDER", "", true, now - 120000L, now - 120000L)
        ));

        return s;
    }
}
