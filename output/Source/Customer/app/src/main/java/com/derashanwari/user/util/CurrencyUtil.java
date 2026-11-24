package com.derashanwari.user.util;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CurrencyUtil {
    private CurrencyUtil() {}

    public static String formatPkr(double amount) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        return "Rs. " + nf.format(Math.round(amount));
    }

    public static String formatDateTime(long timestampMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
        return sdf.format(new Date(timestampMillis));
    }
}
