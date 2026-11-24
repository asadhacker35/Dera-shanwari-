package com.derashanwari.user.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.derashanwari.user.R;
import com.derashanwari.user.model.Order;
import com.derashanwari.user.model.OrderItem;
import com.derashanwari.user.model.RestaurantSettings;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.util.CurrencyUtil;
import com.derashanwari.user.util.DigitalReceiptGenerator;
import com.derashanwari.user.util.TimeFormatUtil;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;

public class DigitalReceiptActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private Order order;
    private RestaurantSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digital_receipt);

        MaterialToolbar tb = findViewById(R.id.toolbar_receipt);
        tb.setNavigationOnClickListener(v -> finish());

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        order = RepositoryProvider.orders(this).getOrderById(orderId);
        settings = RepositoryProvider.settings(this).getRestaurantSettings();
        if (order == null) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindReceiptView();

        findViewById(R.id.btn_receipt_save_pdf).setOnClickListener(v -> {
            try {
                DigitalReceiptGenerator.saveReceiptPdfToDownloads(this, order, settings);
                Toast.makeText(this, "PDF saved to Downloads/DeraShanwari", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_receipt_share_pdf).setOnClickListener(v -> {
            try {
                File pdf = DigitalReceiptGenerator.createShareablePdfFile(this, order, settings);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdf);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/pdf");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.putExtra(Intent.EXTRA_SUBJECT, "Dera Shanwari Receipt - " + order.getOrderId());
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share Receipt PDF"));
            } catch (Exception e) {
                Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_receipt_share_text).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, DigitalReceiptGenerator.buildShareText(order, settings));
            startActivity(Intent.createChooser(share, "Share Receipt via WhatsApp / Text"));
        });

        findViewById(R.id.btn_receipt_system_print).setOnClickListener(v ->
                DigitalReceiptGenerator.printOrderReceipt(this, order, settings));
    }

    private void bindReceiptView() {
        String brand = settings != null && settings.getRestaurantName() != null
                ? settings.getRestaurantName() : "DERA SHANWARI";
        String address = settings != null && settings.getAddress() != null ? settings.getAddress() : "";
        String phone = settings != null && settings.getPhone() != null ? settings.getPhone() : "";

        ((TextView) findViewById(R.id.tv_receipt_brand)).setText(brand.toUpperCase());
        ((TextView) findViewById(R.id.tv_receipt_branch)).setText(
                address + (phone.isEmpty() ? "" : "\nTel: " + phone));

        String meta = "Order ID: " + order.getOrderId()
                + "\nPlaced (PKT): " + TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp())
                + "\nStatus: " + order.getOrderStatus()
                + "\nCustomer: " + order.getCustomerName() + " (" + order.getCustomerPhone() + ")"
                + "\nAddress: " + order.getDeliveryAddress()
                + "\nPayment: " + order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")"
                + "\nRider: " + (order.getRiderName() != null && !order.getRiderName().isEmpty()
                ? order.getRiderName() : "Not Assigned");
        ((TextView) findViewById(R.id.tv_receipt_meta)).setText(meta);

        StringBuilder itemsSb = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            itemsSb.append(item.getQuantity()).append("x ")
                    .append(item.getDisplayName())
                    .append(" — ").append(CurrencyUtil.formatPkr(item.getLineTotal()))
                    .append("\n   @ ").append(CurrencyUtil.formatPkr(item.getUnitPrice())).append(" each\n");
        }
        ((TextView) findViewById(R.id.tv_receipt_items)).setText(itemsSb.toString().trim());

        StringBuilder totalsSb = new StringBuilder();
        totalsSb.append("Subtotal: ").append(CurrencyUtil.formatPkr(order.getSubtotal()))
                .append("\nDelivery Charges: ").append(CurrencyUtil.formatPkr(order.getDeliveryFee()));
        if (order.getDiscountAmount() > 0) {
            totalsSb.append("\nDiscount: -").append(CurrencyUtil.formatPkr(order.getDiscountAmount()));
        }
        totalsSb.append("\n\nGRAND TOTAL: ").append(CurrencyUtil.formatPkr(order.getTotalAmount()));
        ((TextView) findViewById(R.id.tv_receipt_totals)).setText(totalsSb.toString());
    }
}
