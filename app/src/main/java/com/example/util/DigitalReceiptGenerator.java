package com.example.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.pdf.PrintedPdfDocument;
import android.provider.MediaStore;

import com.example.model.Order;
import com.example.model.OrderItem;
import com.example.model.RestaurantSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Generates branded Dera Shanwari PDF digital receipts using Android native PdfDocument & PrintManager.
 */
public final class DigitalReceiptGenerator {
    private static final int PAGE_WIDTH = 420;

    private DigitalReceiptGenerator() {}

    public static PdfDocument buildReceiptPdf(Order order, RestaurantSettings settings) {
        int itemCount = order != null && order.getItems() != null ? order.getItems().size() : 0;
        int pageHeight = Math.max(620, 430 + (itemCount * 38));

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        drawReceiptPage(page.getCanvas(), PAGE_WIDTH, order, settings);
        document.finishPage(page);
        return document;
    }

    public static Uri saveReceiptPdfToDownloads(Context context, Order order, RestaurantSettings settings) throws Exception {
        String fileName = "DeraShanwari_Receipt_" + (order != null ? order.getOrderId() : System.currentTimeMillis()) + ".pdf";
        PdfDocument doc = buildReceiptPdf(order, settings);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DeraShanwari");
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Unable to create Downloads entry.");
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("Unable to open output stream.");
                    doc.writeTo(os);
                }
                return uri;
            } else {
                File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (dir == null) dir = context.getFilesDir();
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    doc.writeTo(fos);
                }
                return Uri.fromFile(out);
            }
        } finally {
            doc.close();
        }
    }

    public static File createShareablePdfFile(Context context, Order order, RestaurantSettings settings) throws Exception {
        File cacheDir = new File(context.getCacheDir(), "receipts");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File file = new File(cacheDir, "Receipt_" + (order != null ? order.getOrderId() : "order") + ".pdf");
        PdfDocument doc = buildReceiptPdf(order, settings);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.writeTo(fos);
        } finally {
            doc.close();
        }
        return file;
    }

    public static String buildShareText(Order order, RestaurantSettings settings) {
        String brand = settings != null && settings.getRestaurantName() != null
                ? settings.getRestaurantName() : "Dera Shanwari";
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(brand).append(" - Official Receipt*\n");
        if (settings != null && settings.getPhone() != null && !settings.getPhone().isEmpty()) {
            sb.append("Tel: ").append(settings.getPhone()).append("\n");
        }
        sb.append("------------------------------\n");
        sb.append("Order: ").append(order.getOrderId()).append("\n");
        sb.append("Placed: ").append(TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp())).append("\n");
        sb.append("Status: ").append(order.getOrderStatus()).append("\n");
        sb.append("Customer: ").append(order.getCustomerName()).append("\n");
        sb.append("Address: ").append(order.getDeliveryAddress()).append("\n");
        sb.append("------------------------------\n");
        for (OrderItem item : order.getItems()) {
            sb.append(item.getQuantity()).append("x ")
                    .append(item.getDisplayName())
                    .append(" - ").append(CurrencyUtil.formatPkr(item.getLineTotal())).append("\n");
        }
        sb.append("------------------------------\n");
        sb.append("Subtotal: ").append(CurrencyUtil.formatPkr(order.getSubtotal())).append("\n");
        sb.append("Delivery: ").append(CurrencyUtil.formatPkr(order.getDeliveryFee())).append("\n");
        if (order.getDiscountAmount() > 0) {
            sb.append("Discount: -").append(CurrencyUtil.formatPkr(order.getDiscountAmount())).append("\n");
        }
        sb.append("*Grand Total: ").append(CurrencyUtil.formatPkr(order.getTotalAmount())).append("*\n");
        sb.append("Payment: ").append(order.getPaymentMethod()).append(" (").append(order.getPaymentStatus()).append(")\n");
        sb.append("\nThank you for ordering from ").append(brand).append("!");
        return sb.toString();
    }

    public static void printOrderReceipt(Activity activity, Order order, RestaurantSettings settings) {
        PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) return;
        String jobName = "DeraShanwari_Receipt_" + order.getOrderId();
        printManager.print(jobName, new PrintDocumentAdapter() {
            private PrintedPdfDocument pdfDocument;

            @Override
            public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                 CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
                pdfDocument = new PrintedPdfDocument(activity, newAttributes);
                if (cancellationSignal.isCanceled()) {
                    callback.onLayoutCancelled();
                    return;
                }
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName + ".pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();
                callback.onLayoutFinished(info, true);
            }

            @Override
            public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                CancellationSignal cancellationSignal, WriteResultCallback callback) {
                PdfDocument.Page page = pdfDocument.startPage(0);
                if (cancellationSignal.isCanceled()) {
                    callback.onWriteCancelled();
                    pdfDocument.close();
                    pdfDocument = null;
                    return;
                }
                drawReceiptPage(page.getCanvas(), page.getInfo().getPageWidth(), order, settings);
                pdfDocument.finishPage(page);
                try (FileOutputStream os = new FileOutputStream(destination.getFileDescriptor())) {
                    pdfDocument.writeTo(os);
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed(e.getMessage());
                } finally {
                    pdfDocument.close();
                    pdfDocument = null;
                }
            }
        }, null);
    }

    public static void drawReceiptPage(Canvas canvas, int width, Order order, RestaurantSettings settings) {
        canvas.drawColor(Color.WHITE);

        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.parseColor("#FF6B00"));
        canvas.drawRect(0, 0, width, 74, headerPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(20);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);
        String brand = settings != null && settings.getRestaurantName() != null
                ? settings.getRestaurantName() : "DERA SHANWARI";
        canvas.drawText(brand.toUpperCase(), width / 2f, 34, titlePaint);

        Paint subHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        subHeader.setColor(Color.parseColor("#FFF4EB"));
        subHeader.setTextSize(11);
        subHeader.setTextAlign(Paint.Align.CENTER);
        String phone = settings != null ? settings.getPhone() : "";
        String address = settings != null ? settings.getAddress() : "";
        canvas.drawText(address + (phone.isEmpty() ? "" : " | " + phone), width / 2f, 56, subHeader);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.parseColor("#111827"));
        text.setTextSize(12);

        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setColor(Color.parseColor("#111827"));
        bold.setTextSize(12);
        bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG);
        muted.setColor(Color.parseColor("#6B7280"));
        muted.setTextSize(10.5f);

        Paint divider = new Paint();
        divider.setColor(Color.parseColor("#E5E7EB"));
        divider.setStrokeWidth(1f);

        int y = 98;
        int left = 24;
        int right = width - 24;

        canvas.drawText("Order ID: " + safe(order.getOrderId()), left, y, bold);
        y += 16;
        canvas.drawText("Placed (PKT): " + TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp()), left, y, muted);
        y += 16;
        canvas.drawText("Customer: " + safe(order.getCustomerName()) + "  |  " + safe(order.getCustomerPhone()), left, y, text);
        y += 16;
        canvas.drawText("Address: " + truncate(safe(order.getDeliveryAddress()), 52), left, y, muted);
        y += 16;
        canvas.drawText("Status: " + safe(order.getOrderStatus()) + "  |  Pay: " + safe(order.getPaymentMethod()) + " (" + safe(order.getPaymentStatus()) + ")", left, y, text);
        y += 12;
        canvas.drawLine(left, y, right, y, divider);
        y += 18;

        canvas.drawText("ITEM & VARIANT", left, y, bold);
        bold.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TOTAL", right, y, bold);
        bold.setTextAlign(Paint.Align.LEFT);
        y += 10;
        canvas.drawLine(left, y, right, y, divider);
        y += 18;

        for (OrderItem item : order.getItems()) {
            String title = item.getQuantity() + "x " + truncate(item.getDisplayName(), 36);
            canvas.drawText(title, left, y, bold);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(CurrencyUtil.formatPkr(item.getLineTotal()), right, y, text);
            text.setTextAlign(Paint.Align.LEFT);
            y += 14;
            canvas.drawText("@ " + CurrencyUtil.formatPkr(item.getUnitPrice()) + " each", left + 10, y, muted);
            y += 18;
        }

        canvas.drawLine(left, y, right, y, divider);
        y += 18;

        drawRow(canvas, left, right, y, "Subtotal", CurrencyUtil.formatPkr(order.getSubtotal()), text);
        y += 16;
        drawRow(canvas, left, right, y, "Delivery Charges", CurrencyUtil.formatPkr(order.getDeliveryFee()), text);
        y += 16;
        if (order.getDiscountAmount() > 0) {
            drawRow(canvas, left, right, y, "Discount", "-" + CurrencyUtil.formatPkr(order.getDiscountAmount()), text);
            y += 16;
        }
        canvas.drawLine(left, y, right, y, divider);
        y += 20;

        bold.setTextSize(15);
        bold.setColor(Color.parseColor("#FF6B00"));
        drawRow(canvas, left, right, y, "GRAND TOTAL", CurrencyUtil.formatPkr(order.getTotalAmount()), bold);
        y += 26;

        subHeader.setColor(Color.parseColor("#6B7280"));
        canvas.drawText("Thank you for dining with " + brand + "!", width / 2f, y, subHeader);
    }

    private static void drawRow(Canvas c, int left, int right, int y, String label, String val, Paint p) {
        Paint.Align old = p.getTextAlign();
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, left, y, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(val, right, y, p);
        p.setTextAlign(old);
    }

    private static String safe(String v) {
        return v != null ? v : "";
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }
}
