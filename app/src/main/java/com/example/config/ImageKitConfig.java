package com.example.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized ImageKit.io CDN, upload endpoint & curated dish photography configuration for Dera Shanwari.
 * Never store private ImageKit API keys or signature secrets in client apps.
 */
public final class ImageKitConfig {
    public static final String BASE_CDN_ENDPOINT = "https://ik.imagekit.io/s1igzgwfr/";
    public static final String UPLOAD_ENDPOINT = "https://upload.imagekit.io/api/v1/files/upload";
    public static final String FOLDER_PRODUCTS = "products/";
    public static final String FOLDER_CATEGORIES = "categories/";
    public static final String FOLDER_BANNERS = "banners/";
    public static final String FOLDER_BRAND = "brand/";
    public static final String FOLDER_CHAT = "chat/";

    public static final String IMG_MUTTON_KARAHI = "https://images.unsplash.com/photo-1631515243349-e0cb75fb8d3a?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_CHICKEN_KARAHI = "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_TIKKA = "https://images.unsplash.com/photo-1599487488170-d11ec9c172f0?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_BBQ_PLATTER = "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_SEEKH_KABAB = "https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_ZINGER = "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_SHAWARMA = "https://images.unsplash.com/photo-1626700051175-6818013e1d4f?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_PIZZA = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_MINT_MARGARITA = "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_MANGO_LASSI = "https://images.unsplash.com/photo-1546173159-315724a31696?auto=format&fit=crop&w=900&q=80";
    public static final String IMG_BANNER_KARAHI = "https://images.unsplash.com/photo-1585937421612-70a008356fbe?auto=format&fit=crop&w=1200&q=80";
    public static final String IMG_BANNER_BBQ = "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=1200&q=80";

    private ImageKitConfig() {}

    public static Map<String, String> getPresetOnlineImages() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Karahi Special", IMG_MUTTON_KARAHI);
        map.put("Chicken Karahi", IMG_CHICKEN_KARAHI);
        map.put("Chicken Tikka", IMG_TIKKA);
        map.put("BBQ Platter", IMG_BBQ_PLATTER);
        map.put("Seekh Kabab", IMG_SEEKH_KABAB);
        map.put("Zinger Burger", IMG_ZINGER);
        map.put("Arabian Shawarma", IMG_SHAWARMA);
        map.put("Tikka Pizza", IMG_PIZZA);
        map.put("Mint Margarita", IMG_MINT_MARGARITA);
        map.put("Mango Lassi", IMG_MANGO_LASSI);
        return map;
    }

    public static String buildTransformedUrl(String rawUrl, int widthPx, int heightPx) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = BASE_CDN_ENDPOINT + (trimmed.startsWith("/") ? trimmed.substring(1) : trimmed);
        }
        if (trimmed.contains("ik.imagekit.io") && !trimmed.contains("?tr=")) {
            return trimmed + "?tr=w-" + widthPx + ",h-" + heightPx + ",fo-auto,q-80";
        }
        return trimmed;
    }

    public static boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("local://");
    }
}
