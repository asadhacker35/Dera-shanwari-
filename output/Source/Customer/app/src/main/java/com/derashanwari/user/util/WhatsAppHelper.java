package com.derashanwari.user.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.derashanwari.user.model.Order;
import com.derashanwari.user.model.OrderItem;
import com.derashanwari.user.model.RestaurantSettings;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WhatsAppHelper {
    private WhatsAppHelper() {}

    public static String buildOrderMessage(Order order, RestaurantSettings settings) {
        String restaurantName = (settings != null && ValidationUtil.isNonEmpty(settings.getRestaurantName()))
                ? settings.getRestaurantName() : "Dera Shanwari";

        StringBuilder sb = new StringBuilder();
        sb.append("*").append(restaurantName).append(" - New Order*\n");
        sb.append("Order ID: *").append(order.getOrderId()).append("*\n");
        sb.append("Customer: ").append(order.getCustomerName()).append("\n");
        sb.append("Phone: ").append(order.getCustomerPhone()).append("\n");
        sb.append("Address: ").append(order.getDeliveryAddress()).append("\n");
        if (ValidationUtil.isNonEmpty(order.getNotes())) {
            sb.append("Notes: ").append(order.getNotes()).append("\n");
        }
        sb.append("\n*Items:*\n");
        for (OrderItem item : order.getItems()) {
            sb.append("- ").append(item.getProductName())
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(CurrencyUtil.formatPkr(item.getLineTotal())).append("\n");
        }
        sb.append("\nSubtotal: ").append(CurrencyUtil.formatPkr(order.getSubtotal())).append("\n");
        sb.append("Delivery Charges: ").append(CurrencyUtil.formatPkr(order.getDeliveryFee())).append("\n");
        sb.append("*Grand Total: ").append(CurrencyUtil.formatPkr(order.getTotalAmount())).append("*\n");
        sb.append("Payment Method: ").append(order.getPaymentMethod());
        return sb.toString();
    }

    public static void openWhatsAppWithMessage(Context context, String rawNumber, String message) {
        String cleanNumber = normalizeWhatsAppNumber(rawNumber);
        try {
            String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.name());
            String url = "https://wa.me/" + cleanNumber + "?text=" + encoded;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "WhatsApp is not available on this device. Message prepared.", Toast.LENGTH_LONG).show();
        }
    }

    public static String normalizeWhatsAppNumber(String phone) {
        if (phone == null) return "923001112233";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            return "92" + digits.substring(1);
        }
        if (digits.startsWith("92")) {
            return digits;
        }
        return "92" + digits;
    }
}
