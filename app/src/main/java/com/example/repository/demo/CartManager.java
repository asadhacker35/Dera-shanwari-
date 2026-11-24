package com.example.repository.demo;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.model.OrderItem;
import com.example.model.Product;
import com.example.model.ProductVariant;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Local persistent Shopping Cart manager for the Customer App supporting multi-variant dishes.
 * Same productId with different variantIds are stored as separate line items.
 */
public class CartManager {
    private static final String PREF_NAME = "dera_shanwari_cart";
    private static final String KEY_ITEMS = "cart_items";

    private static CartManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<OrderItem> items;

    private CartManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized CartManager getInstance(Context context) {
        if (instance == null) {
            instance = new CartManager(context);
        }
        return instance;
    }

    private void load() {
        String json = prefs.getString(KEY_ITEMS, null);
        List<OrderItem> loaded = null;
        if (json != null) {
            try {
                Type type = new TypeToken<List<OrderItem>>() {}.getType();
                loaded = gson.fromJson(json, type);
            } catch (Exception ignored) {}
        }
        items = new ArrayList<>();
        if (loaded != null) {
            for (OrderItem item : loaded) {
                if (item != null && item.getProductId() != null) {
                    items.add(item);
                }
            }
        }
    }

    private void save() {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply();
    }

    public synchronized List<OrderItem> getItems() {
        return new ArrayList<>(items);
    }

    public synchronized void addProduct(Product product, int quantity) {
        if (product == null) return;
        addProduct(product, product.getDefaultVariant(), quantity);
    }

    public synchronized void addProduct(Product product, ProductVariant variant, int quantity) {
        if (product == null || product.getId() == null || quantity <= 0) return;
        String variantId = variant != null && variant.getId() != null ? variant.getId() : "";
        String variantName = variant != null && variant.getName() != null ? variant.getName() : "";
        double unitPrice = variant != null ? variant.getEffectivePrice() : product.getEffectivePrice();
        String imgUrl = (variant != null && variant.getImageUrl() != null && !variant.getImageUrl().trim().isEmpty())
                ? variant.getImageUrl() : product.getImageUrl();

        for (OrderItem item : items) {
            if (Objects.equals(item.getProductId(), product.getId())
                    && Objects.equals(safeVariant(item.getVariantId()), variantId)) {
                item.setQuantity(Math.min(99, item.getQuantity() + quantity));
                save();
                return;
            }
        }
        items.add(new OrderItem(
                product.getId(),
                product.getName(),
                variantId,
                variantName,
                unitPrice,
                quantity,
                imgUrl,
                product.getLocalDrawableName()
        ));
        save();
    }

    public synchronized void updateQuantity(String productId, int newQty) {
        updateQuantity(productId, null, newQty);
    }

    public synchronized void updateQuantity(String productId, String variantId, int newQty) {
        if (productId == null) return;
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            boolean productMatch = Objects.equals(item.getProductId(), productId);
            boolean variantMatch = variantId == null || Objects.equals(safeVariant(item.getVariantId()), safeVariant(variantId));
            if (productMatch && variantMatch) {
                if (newQty <= 0) {
                    items.remove(i);
                } else {
                    item.setQuantity(Math.min(99, newQty));
                }
                save();
                return;
            }
        }
    }

    public synchronized void removeItem(String productId) {
        updateQuantity(productId, null, 0);
    }

    public synchronized void removeItem(String productId, String variantId) {
        updateQuantity(productId, variantId, 0);
    }

    public synchronized void clear() {
        items.clear();
        save();
    }

    public synchronized int getTotalItemCount() {
        int count = 0;
        for (OrderItem item : items) count += item.getQuantity();
        return count;
    }

    public synchronized double getSubtotal() {
        double sum = 0.0;
        for (OrderItem item : items) sum += item.getLineTotal();
        return sum;
    }

    private static String safeVariant(String v) {
        return v != null ? v : "";
    }
}
