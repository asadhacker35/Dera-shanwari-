package com.example.ui;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.model.Product;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.util.CurrencyUtil;
import com.example.util.ImageLoaderUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class ProductDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";

    private Product product;
    private int quantity = 1;
    private TextView tvQty;
    private MaterialButton btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        product = RepositoryProvider.products(this).getProductById(productId);
        if (product == null) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView iv = findViewById(R.id.iv_product);
        TextView tvDiscount = findViewById(R.id.tv_discount_badge);
        TextView tvCat = findViewById(R.id.tv_category);
        TextView tvName = findViewById(R.id.tv_name);
        TextView tvEffective = findViewById(R.id.tv_effective_price);
        TextView tvOriginal = findViewById(R.id.tv_original_price);
        TextView tvDesc = findViewById(R.id.tv_description);
        tvQty = findViewById(R.id.tv_qty);
        btnAdd = findViewById(R.id.btn_add_to_cart);

        ImageLoaderUtil.loadImage(iv, product.getImageUrl(), product.getLocalDrawableName());
        tvCat.setText(product.getCategoryName());
        tvName.setText(product.getName());
        tvDesc.setText(product.getDescription());
        tvEffective.setText(CurrencyUtil.formatPkr(product.getEffectivePrice()));

        if (product.hasDiscount()) {
            tvDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText(product.getDiscountPercent() + "% OFF");
            tvOriginal.setVisibility(View.VISIBLE);
            tvOriginal.setText(CurrencyUtil.formatPkr(product.getPrice()));
            tvOriginal.setPaintFlags(tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tvDiscount.setVisibility(View.GONE);
            tvOriginal.setVisibility(View.GONE);
        }

        findViewById(R.id.btn_qty_minus).setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateQtyUi();
            }
        });
        findViewById(R.id.btn_qty_plus).setOnClickListener(v -> {
            if (quantity < 99) {
                quantity++;
                updateQtyUi();
            }
        });

        btnAdd.setOnClickListener(v -> {
            CartManager.getInstance(this).addProduct(product, quantity);
            Toast.makeText(this, quantity + " x " + product.getName() + " added to cart", Toast.LENGTH_SHORT).show();
            finish();
        });

        updateQtyUi();
    }

    private void updateQtyUi() {
        tvQty.setText(String.valueOf(quantity));
        double total = product.getEffectivePrice() * quantity;
        btnAdd.setText("Add to Cart • " + CurrencyUtil.formatPkr(total));
    }
}
