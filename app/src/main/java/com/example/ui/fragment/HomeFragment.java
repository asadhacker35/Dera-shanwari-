package com.example.ui.fragment;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.R;
import com.example.firebase.FirestoreSyncManager;
import com.example.model.Category;
import com.example.model.Customer;
import com.example.model.Product;
import com.example.model.RestaurantSettings;
import com.example.repository.ProductRepository;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.ui.ProductDetailActivity;
import com.example.ui.RewardsActivity;
import com.example.ui.adapter.BannerAdapter;
import com.example.ui.adapter.CategoryAdapter;
import com.example.ui.adapter.ProductAdapter;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private ProductRepository productRepo;
    private BannerAdapter bannerAdapter;
    private CategoryAdapter categoryAdapter;
    private ProductAdapter featuredAdapter;
    private ProductAdapter popularAdapter;
    private TextView tvAddressPill, tvPointsPill, tvRestaurantTitle, tvPopularHeader;
    private String selectedCategoryId = "ALL";
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
        EditText etSearch = view.findViewById(R.id.et_home_search);

        tvPointsPill.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RewardsActivity.class)));

        RecyclerView rvBanners = view.findViewById(R.id.rv_banners);
        rvBanners.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bannerAdapter = new BannerAdapter(new ArrayList<>(productRepo.getActiveBanners()));
        rvBanners.setAdapter(bannerAdapter);

        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(buildCategoryList(), cat -> {
            selectedCategoryId = cat.getId();
            applyFilter();
        });
        rvCategories.setAdapter(categoryAdapter);

        RecyclerView rvFeatured = view.findViewById(R.id.rv_featured_products);
        rvFeatured.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        ProductAdapter.OnProductActionListener productListener = new ProductAdapter.OnProductActionListener() {
            @Override
            public void onProductClick(Product product) {
                Intent i = new Intent(requireContext(), ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.getId());
                startActivity(i);
            }

            @Override
            public void onQuickAddClick(Product product) {
                CartManager.getInstance(requireContext()).addProduct(product, 1);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshCartBadge();
                }
                Toast.makeText(requireContext(), product.getName() + " added to cart", Toast.LENGTH_SHORT).show();
            }
        };
        featuredAdapter = new ProductAdapter(new ArrayList<>(productRepo.getFeaturedProducts()), true, productListener);
        rvFeatured.setAdapter(featuredAdapter);

        RecyclerView rvPopular = view.findViewById(R.id.rv_popular_products);
        rvPopular.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        if (settings != null) {
            tvRestaurantTitle.setText(settings.getRestaurantName().toUpperCase());
        }
        Customer c = RepositoryProvider.auth(requireContext()).getCurrentCustomer();
        if (c != null) {
            String addr = c.getAddress() != null && !c.getAddress().isEmpty() ? c.getAddress() : "Peshawar";
            tvAddressPill.setText("Deliver to: " + addr);
            tvPointsPill.setText(c.getLoyaltyPoints() + " pts");
        } else {
            tvAddressPill.setText("Deliver to: Peshawar");
            tvPointsPill.setText("0 pts");
        }
        bannerAdapter.updateBanners(productRepo.getActiveBanners());
        categoryAdapter.updateCategories(buildCategoryList());
        featuredAdapter.updateItems(productRepo.getFeaturedProducts());
        applyFilter();
    }

    private void applyFilter() {
        List<Product> filtered;
        if (!searchQuery.isEmpty()) {
            filtered = productRepo.searchProducts(searchQuery);
            tvPopularHeader.setText("Search Results (" + filtered.size() + ")");
        } else if (!"ALL".equals(selectedCategoryId)) {
            filtered = productRepo.getProductsByCategory(selectedCategoryId);
            tvPopularHeader.setText("Category Dishes (" + filtered.size() + ")");
        } else {
            List<Product> pop = productRepo.getPopularProducts();
            filtered = pop.isEmpty() ? productRepo.getAvailableProducts() : pop;
            tvPopularHeader.setText("Popular Dishes");
        }
        popularAdapter.updateItems(filtered);
    }
}
