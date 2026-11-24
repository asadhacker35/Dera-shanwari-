package com.derashanwari.user.ui;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.derashanwari.user.R;
import com.derashanwari.user.model.Product;
import com.derashanwari.user.model.ProductVariant;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.repository.demo.CartManager;
import com.derashanwari.user.util.CurrencyUtil;
import com.derashanwari.user.util.ImageLoaderUtil;
import com.derashanwari.user.util.ValidationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProductDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";

    private Product product;
    private ProductVariant selectedVariant;
    private List<ProductVariant> variants;
    private int quantity = 1;

    private ImageView ivPhoto;
    private TextView tvQty, tvDiscount, tvEffective, tvOriginal;
    private LinearLayout layoutVariantsSection, containerVariants;
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

        ivPhoto = findViewById(R.id.iv_product);
        tvDiscount = findViewById(R.id.tv_discount_badge);
        TextView tvCat = findViewById(R.id.tv_category);
        TextView tvName = findViewById(R.id.tv_name);
        tvEffective = findViewById(R.id.tv_effective_price);
        tvOriginal = findViewById(R.id.tv_original_price);
        TextView tvDesc = findViewById(R.id.tv_description);
        layoutVariantsSection = findViewById(R.id.layout_variants_section);
        containerVariants = findViewById(R.id.container_product_variants);
        tvQty = findViewById(R.id.tv_qty);
        btnAdd = findViewById(R.id.btn_add_to_cart);

        tvCat.setText(product.getCategoryName());
        tvName.setText(product.getName());
        tvDesc.setText(product.getDescription());

        variants = product.getAvailableVariants();
        if (product.hasVariants()) {
            layoutVariantsSection.setVisibility(View.VISIBLE);
            if (!variants.isEmpty()) {
                selectedVariant = variants.get(0);
            } else {
                variants = product.getVariants();
                selectedVariant = null;
            }
            renderVariantChoices();
        } else {
            layoutVariantsSection.setVisibility(View.GONE);
            selectedVariant = null;
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
            if (product.hasVariants() && selectedVariant == null) {
                Toast.makeText(this, "Please select an available option", Toast.LENGTH_SHORT).show();
                return;
            }
            CartManager.getInstance(this).addProduct(product, selectedVariant, quantity);
            String label = product.getName()
                    + (selectedVariant != null && ValidationUtil.isNonEmpty(selectedVariant.getName())
                    ? " (" + selectedVariant.getName() + ")" : "");
            Toast.makeText(this, quantity + " x " + label + " added to cart", Toast.LENGTH_SHORT).show();
            finish();
        });

        bindSelectedPricingAndImage();
        updateQtyUi();
    }

    private void renderVariantChoices() {
        containerVariants.removeAllViews();
        int textPrimary = ContextCompat.getColor(this, R.color.text_primary);
        int textSecondary = ContextCompat.getColor(this, R.color.text_secondary);
        int orangeDark = ContextCompat.getColor(this, R.color.brand_orange_dark);

        for (ProductVariant variant : variants) {
            boolean isSelected = selectedVariant != null && variant.getId().equals(selectedVariant.getId());
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 12);
            row.setLayoutParams(lp);
            row.setBackground(ContextCompat.getDrawable(this,
                    isSelected ? R.drawable.bg_glass_card_orange : R.drawable.bg_glass_card));
            row.setClickable(variant.isAvailable());
            row.setAlpha(variant.isAvailable() ? 1.0f : 0.55f);

            TextView nameView = new TextView(this);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            String suffix = variant.isAvailable() ? "" : " (Out of stock)";
            nameView.setText((isSelected ? "● " : "○ ") + variant.getName() + suffix);
            nameView.setTextSize(14f);
            nameView.setTextColor(isSelected ? orangeDark : textPrimary);
            row.addView(nameView);

            TextView priceView = new TextView(this);
            StringBuilder priceText = new StringBuilder(CurrencyUtil.formatPkr(variant.getEffectivePrice()));
            if (variant.hasDiscount()) {
                priceText.append(" (-").append(variant.getDiscountPercent()).append("%)");
            }
            priceView.setText(priceText.toString());
            priceView.setTextSize(14f);
            priceView.setTextColor(isSelected ? orangeDark : textSecondary);
            row.addView(priceView);

            if (variant.isAvailable()) {
                row.setOnClickListener(v -> {
                    selectedVariant = variant;
                    renderVariantChoices();
                    bindSelectedPricingAndImage();
                    updateQtyUi();
                });
            }
            containerVariants.addView(row);
        }
    }

    private void bindSelectedPricingAndImage() {
        String imgUrl = (selectedVariant != null && ValidationUtil.isNonEmpty(selectedVariant.getImageUrl()))
                ? selectedVariant.getImageUrl()
                : product.getImageUrl();
        ImageLoaderUtil.loadImage(ivPhoto, imgUrl, product.getLocalDrawableName());

        double effective = selectedVariant != null ? selectedVariant.getEffectivePrice() : product.getEffectivePrice();
        double original = selectedVariant != null ? selectedVariant.getPrice() : product.getPrice();
        boolean hasDiscount = selectedVariant != null ? selectedVariant.hasDiscount() : product.hasDiscount();
        int discountPct = selectedVariant != null ? selectedVariant.getDiscountPercent() : product.getDiscountPercent();

        tvEffective.setText(CurrencyUtil.formatPkr(effective));
        if (hasDiscount && discountPct > 0) {
            tvDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText(discountPct + "% OFF");
            tvOriginal.setVisibility(View.VISIBLE);
            tvOriginal.setText(CurrencyUtil.formatPkr(original));
            tvOriginal.setPaintFlags(tvOriginal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tvDiscount.setVisibility(View.GONE);
            tvOriginal.setVisibility(View.GONE);
        }
    }

    private void updateQtyUi() {
        tvQty.setText(String.valueOf(quantity));
        if (product.hasVariants() && selectedVariant == null) {
            btnAdd.setEnabled(false);
            btnAdd.setText("Unavailable");
            return;
        }
        btnAdd.setEnabled(true);
        double unit = selectedVariant != null ? selectedVariant.getEffectivePrice() : product.getEffectivePrice();
        double total = unit * quantity;
        String suffix = (selectedVariant != null && ValidationUtil.isNonEmpty(selectedVariant.getName()))
                ? " (" + selectedVariant.getName() + ")" : "";
        btnAdd.setText("Add to Cart" + suffix + " • " + CurrencyUtil.formatPkr(total));
    }
}
