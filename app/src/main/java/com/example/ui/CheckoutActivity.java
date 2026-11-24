package com.example.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.firebase.FirebaseConfig;
import com.example.model.Customer;
import com.example.model.Order;
import com.example.model.OrderItem;
import com.example.model.PaymentMethodConfig;
import com.example.model.RestaurantSettings;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.util.CurrencyUtil;
import com.example.util.ValidationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CheckoutActivity extends AppCompatActivity {
    private EditText etName, etPhone, etAddress, etNotes;
    private RadioGroup rgPayment;
    private MaterialCardView cardPaymentInstructions;
    private TextView tvPaymentAccountInfo;
    private List<PaymentMethodConfig> enabledMethods;
    private PaymentMethodConfig selectedMethod;
    private double subtotal, deliveryFee, totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.et_customer_name);
        etPhone = findViewById(R.id.et_customer_phone);
        etAddress = findViewById(R.id.et_delivery_address);
        etNotes = findViewById(R.id.et_order_notes);
        rgPayment = findViewById(R.id.rg_payment_methods);
        cardPaymentInstructions = findViewById(R.id.card_payment_instructions);
        tvPaymentAccountInfo = findViewById(R.id.tv_payment_account_info);

        Customer customer = RepositoryProvider.auth(this).getCurrentCustomer();
        if (customer != null) {
            etName.setText(customer.getName());
            etPhone.setText(customer.getPhone());
            etAddress.setText(customer.getAddress());
        }

        CartManager cart = CartManager.getInstance(this);
        RestaurantSettings settings = RepositoryProvider.settings(this).getRestaurantSettings();
        subtotal = cart.getSubtotal();
        deliveryFee = settings != null ? settings.getDeliveryCharges() : 150.0;
        totalAmount = subtotal + deliveryFee;

        ((TextView) findViewById(R.id.tv_summary_subtotal)).setText("Subtotal: " + CurrencyUtil.formatPkr(subtotal));
        ((TextView) findViewById(R.id.tv_summary_delivery)).setText("Delivery Charges: " + CurrencyUtil.formatPkr(deliveryFee));
        ((TextView) findViewById(R.id.tv_summary_total)).setText("Grand Total: " + CurrencyUtil.formatPkr(totalAmount));

        setupPaymentMethods();

        findViewById(R.id.btn_place_order).setOnClickListener(v -> placeOrder());
    }

    private void setupPaymentMethods() {
        enabledMethods = RepositoryProvider.settings(this).getEnabledPaymentMethods();
        rgPayment.removeAllViews();
        for (int i = 0; i < enabledMethods.size(); i++) {
            PaymentMethodConfig pm = enabledMethods.get(i);
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(pm.getDisplayName());
            rb.setTag(pm);
            rb.setPadding(8, 16, 8, 16);
            rgPayment.addView(rb);
            if (i == 0) {
                rb.setChecked(true);
                onMethodSelected(pm);
            }
        }

        rgPayment.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = findViewById(checkedId);
            if (rb != null && rb.getTag() instanceof PaymentMethodConfig) {
                onMethodSelected((PaymentMethodConfig) rb.getTag());
            }
        });
    }

    private void onMethodSelected(PaymentMethodConfig pm) {
        selectedMethod = pm;
        if (PaymentMethodConfig.METHOD_COD.equalsIgnoreCase(pm.getDisplayName())) {
            cardPaymentInstructions.setVisibility(View.GONE);
        } else {
            cardPaymentInstructions.setVisibility(View.VISIBLE);
            tvPaymentAccountInfo.setText(pm.getDisplayName()
                    + "\nAccount Title: " + pm.getAccountTitle()
                    + "\nAccount Number: " + pm.getAccountNumber());
        }
    }

    private void placeOrder() {
        CartManager cart = CartManager.getInstance(this);
        List<OrderItem> items = cart.getItems();
        if (items.isEmpty()) {
            Toast.makeText(this, "Cart is empty. Cannot place empty order.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (!ValidationUtil.isNonEmpty(name)) {
            etName.setError("Customer name is required");
            return;
        }
        if (!ValidationUtil.isValidPhone(phone)) {
            etPhone.setError("Valid mobile number required (03XXXXXXXXX)");
            return;
        }
        if (!ValidationUtil.isNonEmpty(address)) {
            etAddress.setError("Delivery address is required");
            return;
        }
        if (selectedMethod == null) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        String customerId;
        if (RepositoryProvider.isTestMode()) {
            Customer current = RepositoryProvider.auth(this).getCurrentCustomer();
            customerId = current != null ? current.getId() : "cust_1";
        } else {
            customerId = FirebaseConfig.getAuth(this).getUid();
            if (customerId == null || customerId.isEmpty()) {
                Toast.makeText(this, "Please sign in with Google before placing an order.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, AuthActivity.class));
                return;
            }
        }
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setCustomerName(name);
        order.setCustomerPhone(ValidationUtil.normalizePhone(phone));
        order.setDeliveryAddress(address);
        order.setNotes(notes);
        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTotalAmount(totalAmount);
        order.setPaymentMethod(selectedMethod.getDisplayName());

        Order saved = RepositoryProvider.orders(this).placeOrder(order);
        cart.clear();

        Toast.makeText(this, "Order " + saved.getOrderId() + " placed!", Toast.LENGTH_LONG).show();
        Intent i = new Intent(this, OrderDetailActivity.class);
        i.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, saved.getOrderId());
        startActivity(i);
        finish();
    }
}
