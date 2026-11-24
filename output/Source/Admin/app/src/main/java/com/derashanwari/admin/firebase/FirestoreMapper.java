package com.derashanwari.admin.firebase;

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
import com.derashanwari.admin.model.OrderItem;
import com.derashanwari.admin.model.Payment;
import com.derashanwari.admin.model.PaymentMethodConfig;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.model.ProductVariant;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.model.Reward;
import com.derashanwari.admin.model.Rider;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Firestore DocumentSnapshots <-> Java POJOs while maintaining strict Firestore Rules compliance.
 */
public final class FirestoreMapper {
    private FirestoreMapper() {}

    public static long readMillis(DocumentSnapshot doc, String field) {
        return toMillis(doc.get(field), System.currentTimeMillis());
    }

    public static long readMillisOrZero(DocumentSnapshot doc, String field) {
        return toMillis(doc.get(field), 0L);
    }

    public static long toMillis(Object val, long fallback) {
        if (val instanceof Timestamp) {
            return ((Timestamp) val).toDate().getTime();
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return fallback;
    }

    public static String str(DocumentSnapshot doc, String field, String fallback) {
        String s = doc.getString(field);
        return s != null ? s : fallback;
    }

    public static double dbl(DocumentSnapshot doc, String field, double fallback) {
        Double d = doc.getDouble(field);
        if (d != null) return d;
        Object raw = doc.get(field);
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        return fallback;
    }

    public static int integer(DocumentSnapshot doc, String field, int fallback) {
        Long l = doc.getLong(field);
        if (l != null) return l.intValue();
        Object raw = doc.get(field);
        if (raw instanceof Number) return ((Number) raw).intValue();
        return fallback;
    }

    public static boolean bool(DocumentSnapshot doc, String field, boolean fallback) {
        Boolean b = doc.getBoolean(field);
        return b != null ? b : fallback;
    }

    @SuppressWarnings("unchecked")
    public static Product toProduct(DocumentSnapshot d) {
        Product p = new Product(
                str(d, "id", d.getId()),
                str(d, "name", ""),
                str(d, "description", ""),
                dbl(d, "price", 0),
                dbl(d, "discountPrice", 0),
                str(d, "imageUrl", ""),
                str(d, "localDrawableName", "img_banner_shanwari"),
                str(d, "categoryId", ""),
                str(d, "categoryName", ""),
                bool(d, "available", true),
                bool(d, "featured", false),
                bool(d, "popular", false),
                readMillis(d, "createdAt")
        );
        p.setImageFileId(str(d, "imageFileId", ""));

        List<ProductVariant> variants = new ArrayList<>();
        Object rawVariants = d.get("variants");
        if (rawVariants instanceof List) {
            for (Object entry : (List<?>) rawVariants) {
                if (entry instanceof Map) {
                    Map<String, Object> vm = (Map<String, Object>) entry;
                    String vid = vm.get("id") != null ? String.valueOf(vm.get("id")) : "";
                    String vname = vm.get("name") != null ? String.valueOf(vm.get("name")) : "";
                    double vprice = vm.get("price") instanceof Number ? ((Number) vm.get("price")).doubleValue() : 0;
                    double vdisc = vm.get("discountPrice") instanceof Number ? ((Number) vm.get("discountPrice")).doubleValue() : 0;
                    boolean vavail = !(vm.get("available") instanceof Boolean) || (Boolean) vm.get("available");
                    String vimg = vm.get("imageUrl") != null ? String.valueOf(vm.get("imageUrl")) : "";
                    String vdesc = vm.get("description") != null ? String.valueOf(vm.get("description")) : "";
                    if (!vname.isEmpty() && vprice > 0) {
                        variants.add(new ProductVariant(vid, vname, vprice, vdisc, vavail, vimg, vdesc));
                    }
                }
            }
        }
        p.setVariants(variants);
        return p;
    }

    public static Map<String, Object> fromProduct(Product p, boolean isCreate) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", safe(p.getName(), "Dish"));
        m.put("description", safe(p.getDescription(), ""));
        double basePrice = p.getPrice();
        if (basePrice <= 0 && p.hasVariants()) {
            basePrice = p.getStartingPrice();
        }
        m.put("price", Math.max(1.0, basePrice));
        double disc = p.getDiscountPrice();
        m.put("discountPrice", (disc >= 0 && disc < Math.max(1.0, basePrice)) ? disc : 0.0);
        m.put("imageUrl", safe(p.getImageUrl(), ""));
        m.put("imageFileId", safe(p.getImageFileId(), ""));
        m.put("localDrawableName", safe(p.getLocalDrawableName(), "img_banner_shanwari"));
        m.put("categoryId", safe(p.getCategoryId(), "cat_karahi"));
        m.put("categoryName", safe(p.getCategoryName(), "Karahi"));
        m.put("available", p.isAvailable());
        m.put("featured", p.isFeatured());
        m.put("popular", p.isPopular());

        List<Map<String, Object>> variantList = new ArrayList<>();
        if (p.getVariants() != null) {
            for (ProductVariant v : p.getVariants()) {
                if (v == null || v.getName() == null || v.getName().trim().isEmpty()) continue;
                Map<String, Object> vm = new HashMap<>();
                vm.put("id", sanitizeId(v.getId() != null && !v.getId().isEmpty() ? v.getId() : v.getName()));
                vm.put("name", v.getName().trim());
                vm.put("price", v.getPrice());
                vm.put("discountPrice", v.getDiscountPrice());
                vm.put("available", v.isAvailable());
                vm.put("imageUrl", safe(v.getImageUrl(), ""));
                vm.put("description", safe(v.getDescription(), ""));
                variantList.add(vm);
            }
        }
        m.put("variants", variantList);

        if (isCreate) m.put("createdAt", FieldValue.serverTimestamp());
        else m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Category toCategory(DocumentSnapshot d) {
        Category c = new Category(
                str(d, "id", d.getId()),
                str(d, "name", ""),
                str(d, "imageUrl", ""),
                str(d, "localDrawableName", "img_food_tikka_bbq"),
                bool(d, "available", true),
                integer(d, "sortOrder", 0)
        );
        c.setImageFileId(str(d, "imageFileId", ""));
        return c;
    }

    public static Map<String, Object> fromCategory(Category c, boolean isCreate) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", safe(c.getName(), "Category"));
        m.put("imageUrl", safe(c.getImageUrl(), ""));
        m.put("imageFileId", safe(c.getImageFileId(), ""));
        m.put("localDrawableName", safe(c.getLocalDrawableName(), "img_food_tikka_bbq"));
        m.put("available", c.isAvailable());
        m.put("sortOrder", c.getSortOrder());
        if (isCreate) m.put("createdAt", FieldValue.serverTimestamp());
        else m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Banner toBanner(DocumentSnapshot d) {
        Banner b = new Banner(
                str(d, "id", d.getId()),
                str(d, "title", ""),
                str(d, "subtitle", ""),
                str(d, "imageUrl", ""),
                str(d, "localDrawableName", "img_banner_shanwari"),
                bool(d, "active", true),
                integer(d, "sortOrder", 0)
        );
        b.setImageFileId(str(d, "imageFileId", ""));
        return b;
    }

    public static Map<String, Object> fromBanner(Banner b, boolean isCreate) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId());
        m.put("title", safe(b.getTitle(), "Promo"));
        m.put("subtitle", safe(b.getSubtitle(), ""));
        m.put("imageUrl", safe(b.getImageUrl(), ""));
        m.put("imageFileId", safe(b.getImageFileId(), ""));
        m.put("localDrawableName", safe(b.getLocalDrawableName(), "img_banner_shanwari"));
        m.put("active", b.isActive());
        m.put("sortOrder", b.getSortOrder());
        if (isCreate) m.put("createdAt", FieldValue.serverTimestamp());
        else m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Order toOrder(DocumentSnapshot d) {
        Order o = new Order();
        o.setOrderId(str(d, "orderId", d.getId()));
        o.setCustomerId(str(d, "customerId", ""));
        o.setCustomerName(str(d, "customerName", ""));
        o.setCustomerPhone(str(d, "customerPhone", ""));
        o.setDeliveryAddress(str(d, "deliveryAddress", ""));
        o.setNotes(str(d, "notes", ""));
        o.setSubtotal(dbl(d, "subtotal", 0));
        o.setDeliveryFee(dbl(d, "deliveryFee", 0));
        o.setDiscountAmount(dbl(d, "discountAmount", 0));
        o.setTotalAmount(dbl(d, "totalAmount", 0));
        o.setPaymentMethod(str(d, "paymentMethod", "Cash on Delivery"));
        o.setPaymentStatus(str(d, "paymentStatus", "Pending"));
        o.setOrderStatus(str(d, "orderStatus", "Pending"));
        o.setRiderId(str(d, "riderId", ""));
        o.setRiderName(str(d, "riderName", ""));
        o.setPointsEarned(integer(d, "pointsEarned", 0));
        o.setCreatedAt(readMillis(d, "createdAt"));
        o.setUpdatedAt(readMillis(d, "updatedAt"));
        o.setPrintCount(integer(d, "printCount", 0));
        o.setLastPrintedAt(readMillisOrZero(d, "lastPrintedAt"));
        o.setLastPrintedBy(str(d, "lastPrintedBy", ""));

        Map<String, Long> tsMap = new HashMap<>();
        Object rawTs = d.get("timestamps");
        if (rawTs instanceof Map) {
            Map<String, Object> tm = (Map<String, Object>) rawTs;
            for (Map.Entry<String, Object> e : tm.entrySet()) {
                long ms = toMillis(e.getValue(), 0L);
                if (ms > 0L) tsMap.put(e.getKey(), ms);
            }
        }
        if (!tsMap.containsKey(OrderEvent.TS_PLACED) && o.getCreatedAt() > 0L) {
            tsMap.put(OrderEvent.TS_PLACED, o.getCreatedAt());
        }
        o.setTimestamps(tsMap);

        List<OrderItem> items = new ArrayList<>();
        Object rawItems = d.get("items");
        if (rawItems instanceof List) {
            for (Object entry : (List<?>) rawItems) {
                if (entry instanceof Map) {
                    Map<String, Object> im = (Map<String, Object>) entry;
                    String pid = im.get("productId") != null ? String.valueOf(im.get("productId")) : "";
                    String pname = im.get("productName") != null ? String.valueOf(im.get("productName")) : "";
                    String vid = im.get("variantId") != null ? String.valueOf(im.get("variantId")) : "";
                    String vname = im.get("variantName") != null ? String.valueOf(im.get("variantName")) : "";
                    double price = im.get("unitPrice") instanceof Number ? ((Number) im.get("unitPrice")).doubleValue() : 0;
                    int qty = im.get("quantity") instanceof Number ? ((Number) im.get("quantity")).intValue() : 1;
                    String img = im.get("imageUrl") != null ? String.valueOf(im.get("imageUrl")) : "";
                    String draw = im.get("localDrawableName") != null ? String.valueOf(im.get("localDrawableName")) : "img_banner_shanwari";
                    items.add(new OrderItem(pid, pname, vid, vname, price, qty, img, draw));
                }
            }
        }
        o.setItems(items);
        return o;
    }

    public static Map<String, Object> fromOrderCreate(Order o) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderId", o.getOrderId());
        m.put("customerId", safe(o.getCustomerId(), ""));
        m.put("customerName", safe(o.getCustomerName(), "Customer"));
        m.put("customerPhone", safe(o.getCustomerPhone(), "03000000000"));
        m.put("deliveryAddress", safe(o.getDeliveryAddress(), "Peshawar"));
        m.put("notes", safe(o.getNotes(), ""));
        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (OrderItem item : o.getItems()) {
            Map<String, Object> im = new HashMap<>();
            im.put("productId", safe(item.getProductId(), ""));
            im.put("productName", safe(item.getProductName(), ""));
            im.put("variantId", safe(item.getVariantId(), ""));
            im.put("variantName", safe(item.getVariantName(), ""));
            im.put("unitPrice", item.getUnitPrice());
            im.put("quantity", item.getQuantity());
            im.put("imageUrl", safe(item.getImageUrl(), ""));
            im.put("localDrawableName", safe(item.getLocalDrawableName(), "img_banner_shanwari"));
            itemsList.add(im);
        }
        m.put("items", itemsList);
        m.put("subtotal", o.getSubtotal());
        m.put("deliveryFee", o.getDeliveryFee());
        m.put("discountAmount", o.getDiscountAmount());
        m.put("totalAmount", o.getTotalAmount());
        m.put("paymentMethod", safe(o.getPaymentMethod(), "Cash on Delivery"));
        m.put("paymentStatus", safe(o.getPaymentStatus(), "Pending"));
        m.put("orderStatus", safe(o.getOrderStatus(), "Pending"));
        m.put("riderId", safe(o.getRiderId(), ""));
        m.put("riderName", safe(o.getRiderName(), ""));
        m.put("pointsEarned", o.getPointsEarned());
        Map<String, Object> tsMap = new HashMap<>();
        tsMap.put(OrderEvent.TS_PLACED, FieldValue.serverTimestamp());
        m.put("timestamps", tsMap);
        m.put("printCount", 0);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    public static OrderEvent toTimelineEvent(DocumentSnapshot d) {
        return new OrderEvent(
                str(d, "id", d.getId()),
                str(d, "orderId", ""),
                str(d, "eventType", d.getId()),
                readMillis(d, "timestamp"),
                str(d, "userId", ""),
                str(d, "userRole", "ADMIN"),
                str(d, "userName", "Staff"),
                str(d, "notes", "")
        );
    }

    public static Map<String, Object> fromTimelineEventCreate(OrderEvent e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", sanitizeId(e.getId()));
        m.put("orderId", sanitizeId(e.getOrderId()));
        m.put("eventType", e.getEventType());
        m.put("timestamp", FieldValue.serverTimestamp());
        m.put("userId", safe(e.getUserId(), ""));
        m.put("userRole", safe(e.getUserRole(), "ADMIN"));
        m.put("userName", safe(e.getUserName(), "Staff"));
        m.put("notes", safe(e.getNotes(), ""));
        return m;
    }

    public static GroupChatMember toGroupMember(DocumentSnapshot d) {
        return new GroupChatMember(
                str(d, "uid", d.getId()),
                str(d, "name", "Member"),
                str(d, "role", "CUSTOMER"),
                str(d, "profileImageUrl", ""),
                bool(d, "online", false),
                readMillisOrZero(d, "lastSeenAt"),
                readMillisOrZero(d, "lastReadAt")
        );
    }

    public static Map<String, Object> fromGroupMember(GroupChatMember gm, boolean updateReadAtNow) {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", safe(gm.getUid(), ""));
        m.put("name", safe(gm.getName(), "Member"));
        m.put("role", safe(gm.getRole(), "CUSTOMER"));
        m.put("profileImageUrl", safe(gm.getProfileImageUrl(), ""));
        m.put("online", gm.isOnline());
        m.put("lastSeenAt", FieldValue.serverTimestamp());
        if (updateReadAtNow || gm.getLastReadAt() <= 0L) {
            m.put("lastReadAt", FieldValue.serverTimestamp());
        } else {
            long clamped = Math.min(gm.getLastReadAt(), System.currentTimeMillis() - 500L);
            m.put("lastReadAt", new Timestamp(new Date(Math.max(1000L, clamped))));
        }
        return m;
    }

    public static GroupChatMessage toGroupMessage(DocumentSnapshot d) {
        return new GroupChatMessage(
                str(d, "id", d.getId()),
                str(d, "senderId", ""),
                str(d, "senderName", "Member"),
                str(d, "senderRole", "CUSTOMER"),
                str(d, "senderAvatarUrl", ""),
                str(d, "message", ""),
                str(d, "imageUrl", ""),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromGroupMessage(GroupChatMessage msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", sanitizeId(msg.getId()));
        m.put("senderId", safe(msg.getSenderId(), ""));
        m.put("senderName", safe(msg.getSenderName(), "Member"));
        m.put("senderRole", safe(msg.getSenderRole(), "CUSTOMER"));
        m.put("senderAvatarUrl", safe(msg.getSenderAvatarUrl(), ""));
        m.put("message", safe(msg.getMessage(), ""));
        m.put("imageUrl", safe(msg.getImageUrl(), ""));
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Customer toCustomer(DocumentSnapshot d) {
        return new Customer(
                str(d, "id", d.getId()),
                str(d, "name", "Customer"),
                str(d, "email", ""),
                str(d, "phone", ""),
                str(d, "address", ""),
                bool(d, "active", true),
                integer(d, "loyaltyPoints", 0),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromCustomerCreate(Customer c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", safe(c.getName(), "Customer"));
        m.put("email", safe(c.getEmail(), ""));
        m.put("phone", safe(c.getPhone(), ""));
        m.put("address", safe(c.getAddress(), ""));
        m.put("active", c.isActive());
        m.put("loyaltyPoints", c.getLoyaltyPoints());
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Rider toRider(DocumentSnapshot d) {
        return new Rider(
                str(d, "id", d.getId()),
                str(d, "linkedUid", d.getId()),
                str(d, "name", "Rider"),
                str(d, "email", ""),
                str(d, "phone", ""),
                str(d, "vehicleInfo", ""),
                str(d, "profileImageUrl", ""),
                bool(d, "active", true),
                integer(d, "totalDeliveries", 0),
                dbl(d, "totalEarnings", 0),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromRiderCreate(Rider r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("linkedUid", safe(r.getLinkedUid(), r.getId()));
        m.put("name", safe(r.getName(), "Rider"));
        m.put("email", safe(r.getEmail(), ""));
        m.put("phone", safe(r.getPhone(), ""));
        m.put("vehicleInfo", safe(r.getVehicleInfo(), ""));
        m.put("profileImageUrl", safe(r.getProfileImageUrl(), ""));
        m.put("active", r.isActive());
        m.put("totalDeliveries", r.getTotalDeliveries());
        m.put("totalEarnings", r.getTotalEarnings());
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static AdminUser toAdmin(DocumentSnapshot d) {
        return new AdminUser(
                str(d, "id", d.getId()),
                str(d, "name", "Admin"),
                str(d, "email", ""),
                str(d, "phone", ""),
                str(d, "role", "Admin"),
                bool(d, "active", true)
        );
    }

    public static Map<String, Object> fromAdminCreate(AdminUser a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("name", safe(a.getName(), "Admin"));
        m.put("email", safe(a.getEmail(), ""));
        m.put("phone", safe(a.getPhone(), ""));
        m.put("role", safe(a.getRole(), "Admin"));
        m.put("active", a.isActive());
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Payment toPayment(DocumentSnapshot d) {
        return new Payment(
                str(d, "id", d.getId()),
                str(d, "orderId", ""),
                str(d, "customerId", ""),
                str(d, "method", ""),
                str(d, "accountTitle", ""),
                str(d, "accountNumber", ""),
                dbl(d, "amount", 0),
                str(d, "status", "Pending"),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromPaymentCreate(Payment p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("orderId", safe(p.getOrderId(), ""));
        m.put("customerId", safe(p.getCustomerId(), ""));
        m.put("method", safe(p.getMethod(), "Cash on Delivery"));
        m.put("accountTitle", safe(p.getAccountTitle(), ""));
        m.put("accountNumber", safe(p.getAccountNumber(), ""));
        m.put("amount", p.getAmount());
        m.put("status", safe(p.getStatus(), "Pending"));
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static RestaurantSettings toSettings(DocumentSnapshot d) {
        RestaurantSettings s = new RestaurantSettings(
                str(d, "restaurantName", "Dera Shanwari"),
                str(d, "logoUrl", ""),
                str(d, "phone", "03001112233"),
                str(d, "whatsappNumber", "923001112233"),
                str(d, "address", "Main University Road, Peshawar"),
                str(d, "openingHours", "12:00 PM"),
                str(d, "closingHours", "12:30 AM"),
                dbl(d, "deliveryCharges", 150.0),
                dbl(d, "minimumOrderAmount", 500.0),
                integer(d, "loyaltyPointsPercent", 10)
        );
        s.setImagekitPublicKey(str(d, "imagekitPublicKey", ""));
        s.setImagekitAuthEndpoint(str(d, "imagekitAuthEndpoint", ""));
        return s;
    }

    public static Map<String, Object> fromSettings(RestaurantSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("restaurantName", safe(s.getRestaurantName(), "Dera Shanwari"));
        m.put("logoUrl", safe(s.getLogoUrl(), ""));
        m.put("phone", safe(s.getPhone(), ""));
        m.put("whatsappNumber", safe(s.getWhatsappNumber(), ""));
        m.put("address", safe(s.getAddress(), ""));
        m.put("openingHours", safe(s.getOpeningHours(), ""));
        m.put("closingHours", safe(s.getClosingHours(), ""));
        m.put("deliveryCharges", s.getDeliveryCharges());
        m.put("minimumOrderAmount", s.getMinimumOrderAmount());
        m.put("loyaltyPointsPercent", s.getPointsPerHundredRs());
        m.put("imagekitPublicKey", safe(s.getImagekitPublicKey(), ""));
        m.put("imagekitAuthEndpoint", safe(s.getImagekitAuthEndpoint(), ""));
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    public static PaymentMethodConfig toPaymentMethod(DocumentSnapshot d) {
        return new PaymentMethodConfig(
                str(d, "methodCode", d.getId()),
                str(d, "displayName", d.getId()),
                str(d, "accountTitle", ""),
                str(d, "accountNumber", ""),
                bool(d, "enabled", true),
                str(d, "instructions", "")
        );
    }

    public static Map<String, Object> fromPaymentMethod(PaymentMethodConfig pm) {
        String code = sanitizeId(pm.getMethodId());
        Map<String, Object> m = new HashMap<>();
        m.put("methodCode", code);
        m.put("displayName", safe(pm.getDisplayName(), code));
        m.put("accountTitle", safe(pm.getAccountTitle(), ""));
        m.put("accountNumber", safe(pm.getAccountNumber(), ""));
        m.put("enabled", pm.isEnabled());
        m.put("instructions", safe(pm.getInstructions(), ""));
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    public static ChatMessage toChat(DocumentSnapshot d) {
        return new ChatMessage(
                str(d, "id", d.getId()),
                str(d, "orderId", ""),
                str(d, "senderId", ""),
                str(d, "customerId", ""),
                str(d, "riderId", ""),
                str(d, "senderRole", "CUSTOMER"),
                str(d, "senderName", "User"),
                str(d, "message", ""),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromChat(ChatMessage c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("orderId", safe(c.getOrderId(), "general"));
        m.put("senderId", safe(c.getSenderId(), ""));
        m.put("customerId", safe(c.getCustomerId(), ""));
        m.put("riderId", safe(c.getRiderId(), ""));
        m.put("senderRole", safe(c.getSenderRole(), "CUSTOMER"));
        m.put("senderName", safe(c.getSenderName(), "User"));
        m.put("message", safe(c.getMessage(), ""));
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static NotificationItem toNotification(DocumentSnapshot d) {
        return new NotificationItem(
                str(d, "id", d.getId()),
                str(d, "targetRole", "ADMIN"),
                str(d, "targetUid", ""),
                str(d, "senderId", ""),
                str(d, "orderId", ""),
                str(d, "title", ""),
                str(d, "body", ""),
                str(d, "type", "INFO"),
                bool(d, "read", false),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromNotificationCreate(NotificationItem n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("targetRole", safe(n.getTargetRole(), "ADMIN"));
        m.put("targetUid", safe(n.getTargetUid(), ""));
        m.put("senderId", safe(n.getSenderId(), ""));
        m.put("orderId", safe(n.getOrderId(), ""));
        m.put("title", safe(n.getTitle(), "Dera Shanwari"));
        m.put("body", safe(n.getBody(), ""));
        m.put("type", safe(n.getType(), "INFO"));
        m.put("read", n.isRead());
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static Reward toReward(DocumentSnapshot d) {
        return new Reward(
                str(d, "id", d.getId()),
                str(d, "customerId", ""),
                str(d, "title", ""),
                str(d, "description", ""),
                integer(d, "points", 0),
                str(d, "type", "EARNED"),
                bool(d, "redeemable", false),
                integer(d, "requiredPoints", 0),
                str(d, "orderId", ""),
                readMillis(d, "createdAt")
        );
    }

    public static Map<String, Object> fromRewardCreate(Reward r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("customerId", safe(r.getCustomerId(), ""));
        m.put("title", safe(r.getTitle(), "Reward"));
        m.put("description", safe(r.getDescription(), ""));
        m.put("points", r.getPoints());
        m.put("type", safe(r.getType(), "EARNED"));
        m.put("redeemable", r.isRedeemable());
        m.put("requiredPoints", r.getMinPointsRequired());
        m.put("orderId", safe(r.getOrderId(), ""));
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "id_" + System.currentTimeMillis();
        }
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.isEmpty() ? "id_" + System.currentTimeMillis() : cleaned;
    }

    private static String safe(String v, String fallback) {
        return v != null ? v : fallback;
    }
}
