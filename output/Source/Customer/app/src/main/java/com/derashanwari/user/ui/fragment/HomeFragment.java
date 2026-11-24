package com.derashanwari.user.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.user.MainActivity;
import com.derashanwari.user.R;
import com.derashanwari.user.firebase.FirebaseConfig;
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.model.Category;
import com.derashanwari.user.model.Customer;
import com.derashanwari.user.model.Product;
import com.derashanwari.user.model.RestaurantSettings;
import com.derashanwari.user.repository.ProductRepository;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.repository.demo.CartManager;
import com.derashanwari.user.ui.GroupChatActivity;
import com.derashanwari.user.ui.ProductDetailActivity;
import com.derashanwari.user.ui.RewardsActivity;
import com.derashanwari.user.ui.adapter.BannerAdapter;
import com.derashanwari.user.ui.adapter.CategoryAdapter;
import com.derashanwari.user.ui.adapter.ProductAdapter;
import com.derashanwari.user.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private ProductRepository productRepo;
    private BannerAdapter bannerAdapter;
    private CategoryAdapter categoryAdapter;
    private ProductAdapter featuredAdapter;
    private ProductAdapter recommendedAdapter;
    private ProductAdapter recentAdapter;
    private ProductAdapter popularAdapter;
    private TextView tvAddressPill, tvPointsPill, tvRestaurantTitle, tvPopularHeader, tvGroupChatBadge;
    private String selectedCategoryId = "ALL";
    private String selectedCategoryName = "All";
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productRepo = RepositoryProvider.products(requireContext());

        tvRestaurantTitle = view.findViewById(R.id.tv_restaurant_title);
        tvAddressPill = view.findViewById(R.id.tv_delivery_address_pill);
        tvPointsPill = view.findViewById(R.id.tv_points_pill);
        tvPopularHeader = view.findViewById(R.id.tv_popular_header);
        tvGroupChatBadge = view.findViewById(R.id.tv_home_group_chat_badge);
        EditText etSearch = view.findViewById(R.id.et_home_search);

        view.findViewById(R.id.btn_home_group_chat).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GroupChatActivity.class)));
        tvPointsPill.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RewardsActivity.class)));

        RecyclerView rvBanners = view.findViewById(R.id.rv_banners);
        rvBanners.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bannerAdapter = new BannerAdapter(new ArrayList<>(productRepo.getActiveBanners()));
        rvBanners.setAdapter(bannerAdapter);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int catColumns = widthDp >= 600 ? 4 : 3;
        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new GridLayoutManager(requireContext(), catColumns));
        categoryAdapter = new CategoryAdapter(buildCategoryList(), true, cat -> {
            selectedCategoryId = cat.getId();
            selectedCategoryName = cat.getName();
            applyFilter();
        });
        rvCategories.setAdapter(categoryAdapter);

        ProductAdapter.OnProductActionListener productListener = new ProductAdapter.OnProductActionListener() {
            @Override
            public void onProductClick(Product product) {
                openProductDetail(product);
            }

            @Override
            public void onQuickAddClick(Product product) {
                if (product.hasVariants() && product.getAvailableVariants().size() > 1) {
                    openProductDetail(product);
                    return;
                }
                CartManager.getInstance(requireContext()).addProduct(product, product.getDefaultVariant(), 1);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshCartBadge();
                }
                Toast.makeText(requireContext(), product.getName() + " added to cart", Toast.LENGTH_SHORT).show();
            }
        };

        RecyclerView rvFeatured = view.findViewById(R.id.rv_featured_products);
        rvFeatured.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new ProductAdapter(new ArrayList<>(), true, productListener);
        rvFeatured.setAdapter(featuredAdapter);

        RecyclerView rvRecommended = view.findViewById(R.id.rv_recommended_products);
        rvRecommended.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recommendedAdapter = new ProductAdapter(new ArrayList<>(), true, productListener);
        rvRecommended.setAdapter(recommendedAdapter);

        RecyclerView rvRecent = view.findViewById(R.id.rv_recent_products);
        rvRecent.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recentAdapter = new ProductAdapter(new ArrayList<>(), true, productListener);
        rvRecent.setAdapter(recentAdapter);

        int dishColumns = widthDp >= 600 ? 2 : 1;
        RecyclerView rvPopular = view.findViewById(R.id.rv_popular_products);
        rvPopular.setLayoutManager(new GridLayoutManager(requireContext(), dishColumns));
        popularAdapter = new ProductAdapter(new ArrayList<>(), false, productListener);
        rvPopular.setAdapter(popularAdapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim() : "";
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        refreshAll();
    }

    private void openProductDetail(Product product) {
        Intent i = new Intent(requireContext(), ProductDetailActivity.class);
        i.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.getId());
        startActivity(i);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).addDataChangeListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).removeDataChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refreshAll();
    }

    private List<Category> buildCategoryList() {
        List<Category> cats = new ArrayList<>();
        cats.add(new Category("ALL", "All", "", "ic_dera_emblem", true, 0));
        cats.addAll(productRepo.getAvailableCategories());
        return cats;
    }

    private void refreshAll() {
        RestaurantSettings settings = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        if (settings != null && ValidationUtil.isNonEmpty(settings.getRestaurantName())) {
            tvRestaurantTitle.setText(settings.getRestaurantName().toUpperCase());
        }
        Customer c = RepositoryProvider.auth(requireContext()).getCurrentCustomer();
        if (c != null) {
            String addr = ValidationUtil.isNonEmpty(c.getAddress()) ? c.getAddress() : "Peshawar";
            tvAddressPill.setText("Deliver to: " + addr);
            tvPointsPill.setText(c.getLoyaltyPoints() + " pts");
        } else {
            tvAddressPill.setText("Deliver to: Peshawar");
            tvPointsPill.setText("0 pts");
        }

        int unread = RepositoryProvider.chat(requireContext()).getUnreadGroupCount();
        if (unread > 0) {
            tvGroupChatBadge.setVisibility(View.VISIBLE);
            tvGroupChatBadge.setText(String.valueOf(unread));
        } else {
            tvGroupChatBadge.setVisibility(View.GONE);
        }

        bannerAdapter.updateBanners(productRepo.getActiveBanners());
        categoryAdapter.updateCategories(buildCategoryList());
        featuredAdapter.updateItems(productRepo.getFeaturedProducts());

        List<Product> recommended = new ArrayList<>(productRepo.getPopularProducts());
        if (recommended.isEmpty()) {
            recommended.addAll(productRepo.getAvailableProducts());
        }
        recommendedAdapter.updateItems(recommended);

        List<Product> recent = new ArrayList<>(productRepo.getAvailableProducts());
        Collections.sort(recent, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        if (recent.size() > 10) {
            recent = new ArrayList<>(recent.subList(0, 10));
        }
        recentAdapter.updateItems(recent);

        applyFilter();
    }

    private String resolveCurrentUserId(Customer c) {
        if (!RepositoryProvider.isTestMode()) {
            String uid = FirebaseConfig.getAuth(requireContext()).getUid();
            if (ValidationUtil.isNonEmpty(uid)) return uid;
        }
        return c != null && ValidationUtil.isNonEmpty(c.getId()) ? c.getId() : "cust_1";
    }

    private void applyFilter() {
        List<Product> filtered;
        if (!searchQuery.isEmpty()) {
            filtered = productRepo.searchProducts(searchQuery);
            tvPopularHeader.setText("Search Results (" + filtered.size() + ")");
        } else if (!"ALL".equals(selectedCategoryId)) {
            filtered = productRepo.getProductsByCategory(selectedCategoryId);
            tvPopularHeader.setText(selectedCategoryName + " (" + filtered.size() + ")");
        } else {
            List<Product> pop = productRepo.getPopularProducts();
            filtered = pop.isEmpty() ? productRepo.getAvailableProducts() : pop;
            tvPopularHeader.setText("Popular Dishes");
        }
        popularAdapter.updateItems(filtered);
    }
}
