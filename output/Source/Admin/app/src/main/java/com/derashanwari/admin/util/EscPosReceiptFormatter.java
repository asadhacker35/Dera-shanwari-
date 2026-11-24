package com.derashanwari.admin.util;

import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderItem;
import com.derashanwari.admin.model.RestaurantSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats 80mm (48-column) ESC/POS thermal receipt payloads tailored for the
 * Black Copper BC-87AC thermal printer with automatic paper feed & partial cut.
 */
public final class EscPosReceiptFormatter {
    public static final int LINE_WIDTH_80MM = 48;

    public static final byte[] CMD_INIT = new byte[]{0x1B, 0x40};
    public static final byte[] CMD_ALIGN_LEFT = new byte[]{0x1B, 0x61, 0x00};
    public static final byte[] CMD_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01};
    public static final byte[] CMD_BOLD_ON = new byte[]{0x1B, 0x45, 0x01};
    public static final byte[] CMD_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00};
    public static final byte[] CMD_DOUBLE_SIZE = new byte[]{0x1D, 0x21, 0x11};
    public static final byte[] CMD_NORMAL_SIZE = new byte[]{0x1D, 0x21, 0x00};
    public static final byte[] CMD_FEED_AND_CUT = new byte[]{0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00};

    private EscPosReceiptFormatter() {}

    public static byte[] formatOrderReceipt(Order order, RestaurantSettings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(CMD_INIT);

            String brand = settings != null && settings.getRestaurantName() != null
                    && !settings.getRestaurantName().trim().isEmpty()
                    ? settings.getRestaurantName().trim().toUpperCase()
                    : "DERA SHANWARI";
            String address = settings != null && settings.getAddress() != null ? settings.getAddress().trim() : "";
            String phone = settings != null && settings.getPhone() != null ? settings.getPhone().trim() : "";

            out.write(CMD_ALIGN_CENTER);
            out.write(CMD_BOLD_ON);
            out.write(CMD_DOUBLE_SIZE);
            writeLine(out, brand);
            out.write(CMD_NORMAL_SIZE);
            out.write(CMD_BOLD_OFF);
            if (!address.isEmpty()) writeWrappedLines(out, address, LINE_WIDTH_80MM);
            if (!phone.isEmpty()) writeLine(out, "Tel: " + phone);
            writeLine(out, repeat('-', LINE_WIDTH_80MM));

            out.write(CMD_ALIGN_LEFT);
            out.write(CMD_BOLD_ON);
            writeLeftRight(out, "Order: " + safe(order.getOrderId()), "Print #" + (order.getPrintCount() + 1));
            out.write(CMD_BOLD_OFF);
            writeLine(out, "Placed: " + TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp()));
            writeLine(out, "Status: " + safe(order.getOrderStatus()));
            writeLine(out, "Customer: " + safe(order.getCustomerName()));
            if (!safe(order.getCustomerPhone()).isEmpty()) {
                writeLine(out, "Phone: " + safe(order.getCustomerPhone()));
            }
            if (!safe(order.getDeliveryAddress()).isEmpty()) {
                writeWrappedLines(out, "Addr: " + safe(order.getDeliveryAddress()), LINE_WIDTH_80MM);
            }
            String riderLabel = safe(order.getRiderName()).isEmpty() ? "Not Assigned" : safe(order.getRiderName());
            writeLine(out, "Rider: " + riderLabel);
            if (!safe(order.getNotes()).isEmpty()) {
                writeWrappedLines(out, "Notes: " + safe(order.getNotes()), LINE_WIDTH_80MM);
            }

            writeLine(out, repeat('-', LINE_WIDTH_80MM));
            out.write(CMD_BOLD_ON);
            writeLeftRight(out, "QTY  ITEM / VARIANT", "AMOUNT");
            out.write(CMD_BOLD_OFF);
            writeLine(out, repeat('-', LINE_WIDTH_80MM));

            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    if (item == null) continue;
                    String left = item.getQuantity() + "x " + item.getDisplayName();
                    String right = CurrencyUtil.formatPkr(item.getLineTotal());
                    List<String> lines = wrapText(left, LINE_WIDTH_80MM - right.length() - 1);
                    if (lines.isEmpty()) {
                        writeLeftRight(out, left, right);
                    } else {
                        writeLeftRight(out, lines.get(0), right);
                        for (int i = 1; i < lines.size(); i++) {
                            writeLine(out, "   " + lines.get(i));
                        }
                    }
                    writeLine(out, "   @ " + CurrencyUtil.formatPkr(item.getUnitPrice()) + " each");
                }
            }

            writeLine(out, repeat('-', LINE_WIDTH_80MM));
            writeLeftRight(out, "Subtotal:", CurrencyUtil.formatPkr(order.getSubtotal()));
            writeLeftRight(out, "Delivery Fee:", CurrencyUtil.formatPkr(order.getDeliveryFee()));
            if (order.getDiscountAmount() > 0) {
                writeLeftRight(out, "Discount:", "-" + CurrencyUtil.formatPkr(order.getDiscountAmount()));
            }
            writeLine(out, repeat('=', LINE_WIDTH_80MM));
            out.write(CMD_BOLD_ON);
            writeLeftRight(out, "GRAND TOTAL:", CurrencyUtil.formatPkr(order.getTotalAmount()));
            out.write(CMD_BOLD_OFF);
            writeLine(out, repeat('=', LINE_WIDTH_80MM));
            writeLeftRight(out, "Payment: " + safe(order.getPaymentMethod()), safe(order.getPaymentStatus()));

            out.write(CMD_ALIGN_CENTER);
            writeLine(out, "");
            writeLine(out, "Printed: " + TimeFormatUtil.formatDateTimePkt(System.currentTimeMillis()));
            writeLine(out, "Thank you for dining with " + brand + "!");
            out.write(CMD_FEED_AND_CUT);
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    public static byte[] formatTestTicket(RestaurantSettings settings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(CMD_INIT);
            out.write(CMD_ALIGN_CENTER);
            out.write(CMD_BOLD_ON);
            out.write(CMD_DOUBLE_SIZE);
            String brand = settings != null && settings.getRestaurantName() != null
                    ? settings.getRestaurantName().toUpperCase() : "DERA SHANWARI";
            writeLine(out, brand);
            out.write(CMD_NORMAL_SIZE);
            out.write(CMD_BOLD_OFF);
            writeLine(out, "Black Copper BC-87AC USB OTG Test");
            writeLine(out, "80mm (48-Column) Receipt Printer");
            writeLine(out, repeat('-', LINE_WIDTH_80MM));
            out.write(CMD_ALIGN_LEFT);
            writeLine(out, "123456789012345678901234567890123456789012345678");
            writeLeftRight(out, "Paper Width:", "80mm / 48 cols");
            writeLeftRight(out, "Auto Cutter:", "ENABLED (GS V 66 0)");
            writeLeftRight(out, "PKT Time:", TimeFormatUtil.formatTimePkt(System.currentTimeMillis()));
            writeLine(out, repeat('-', LINE_WIDTH_80MM));
            out.write(CMD_ALIGN_CENTER);
            out.write(CMD_BOLD_ON);
            writeLine(out, "PRINTER CONNECTION OK");
            out.write(CMD_BOLD_OFF);
            out.write(CMD_FEED_AND_CUT);
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }

    private static void writeLeftRight(ByteArrayOutputStream out, String left, String right) throws IOException {
        String l = left != null ? left : "";
        String r = right != null ? right : "";
        int spaces = LINE_WIDTH_80MM - l.length() - r.length();
        if (spaces < 1) {
            int maxLeft = Math.max(4, LINE_WIDTH_80MM - r.length() - 1);
            if (l.length() > maxLeft) l = l.substring(0, maxLeft);
            spaces = Math.max(1, LINE_WIDTH_80MM - l.length() - r.length());
        }
        writeLine(out, l + repeat(' ', spaces) + r);
    }

    private static void writeWrappedLines(ByteArrayOutputStream out, String text, int width) throws IOException {
        for (String line : wrapText(text, width)) {
            writeLine(out, line);
        }
    }

    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 1) return lines;
        String remaining = text.trim();
        while (remaining.length() > maxWidth) {
            int split = remaining.lastIndexOf(' ', maxWidth);
            if (split <= 0) split = maxWidth;
            lines.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) lines.add(remaining);
        return lines;
    }

    private static void writeLine(ByteArrayOutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes(StandardCharsets.US_ASCII));
    }

    private static String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) sb.append(ch);
        return sb.toString();
    }

    private static String safe(String v) {
        return v != null ? v.trim() : "";
    }
}
