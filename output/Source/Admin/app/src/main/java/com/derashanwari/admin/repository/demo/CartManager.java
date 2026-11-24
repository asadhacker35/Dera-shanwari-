package com.derashanwari.admin.repository.demo;

import android.content.Context;
import android.content.SharedPreferences;

import com.derashanwari.admin.model.OrderItem;
import com.derashanwari.admin.model.Product;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Local persistent Shopping Cart manager for the Customer App.
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
        if (json != null) {
            try {
                Type type = new TypeToken<List<OrderItem>>() {}.getType();
                items = gson.fromJson(json, type);
            } catch (Exception ignored) {}
        }
        if (items == null) items = new ArrayList<>();
    }

    private void save() {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply();
    }

    public synchronized List<OrderItem> getItems() {
        return new ArrayList<>(items);
    }

    public synchronized void addProduct(Product product, int quantity) {
        if (product == null || quantity <= 0) return;
        for (OrderItem item : items) {
            if (item.getProductId().equals(product.getId())) {
                item.setQuantity(Math.min(99, item.getQuantity() + quantity));
                save();
                return;
            }
        }
        items.add(new OrderItem(
                product.getId(),
                product.getName(),
                product.getEffectivePrice(),
                quantity,
                product.getImageUrl(),
                product.getLocalDrawableName()
        ));
        save();
    }

    public synchronized void updateQuantity(String productId, int newQty) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getProductId().equals(productId)) {
                if (newQty <= 0) {
                    items.remove(i);
                } else {
                    items.get(i).setQuantity(Math.min(99, newQty));
                }
                save();
                return;
            }
        }
    }

    public synchronized void removeItem(String productId) {
        updateQuantity(productId, 0);
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
}
