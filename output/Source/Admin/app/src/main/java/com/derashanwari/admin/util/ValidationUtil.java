package com.derashanwari.admin.util;

import java.util.regex.Pattern;

public final class ValidationUtil {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?92|0)?3\\d{9}$");

    private ValidationUtil() {}

    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        return PHONE_PATTERN.matcher(cleaned).matches();
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+92")) {
            return "0" + cleaned.substring(3);
        }
        if (cleaned.startsWith("92") && cleaned.length() == 12) {
            return "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    public static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isValidPrice(double price) {
        return price > 0.0 && !Double.isNaN(price) && !Double.isInfinite(price);
    }

    public static boolean isValidDiscountPrice(double price, double discountPrice) {
        if (discountPrice <= 0.0) return true; // 0 means no discount
        return discountPrice < price;
    }

    public static boolean isValidQuantity(int qty) {
        return qty >= 1 && qty <= 99;
    }
}
