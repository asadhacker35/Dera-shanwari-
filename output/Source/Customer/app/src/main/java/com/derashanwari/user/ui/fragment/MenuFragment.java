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
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.model.Category;
import com.derashanwari.user.model.Product;
import com.derashanwari.user.repository.ProductRepository;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.repository.demo.CartManager;
import com.derashanwari.user.ui.ProductDetailActivity;
import com.derashanwari.user.ui.adapter.CategoryAdapter;
import com.derashanwari.user.ui.adapter.ProductAdapter;

import java.util.ArrayList;
import java.util.List;

public class MenuFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private ProductRepository productRepo;
    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;
    private TextView tvEmpty;
    private String selectedCategoryId = "ALL";
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productRepo = RepositoryProvider.products(requireContext());
        tvEmpty = view.findViewById(R.id.tv_menu_empty);
        EditText etSearch = view.findViewById(R.id.et_menu_search);

        RecyclerView rvCategories = view.findViewById(R.id.rv_menu_categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(buildCategories(), cat -> {
            selectedCategoryId = cat.getId();
            refreshList();
        });
        rvCategories.setAdapter(categoryAdapter);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int columns = widthDp >= 600 ? 2 : 1;
        RecyclerView rvProducts = view.findViewById(R.id.rv_menu_products);
        rvProducts.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        productAdapter = new ProductAdapter(new ArrayList<>(), false, new ProductAdapter.OnProductActionListener() {
            @Override
            public void onProductClick(Product product) {
                openDetail(product);
            }

            @Override
            public void onQuickAddClick(Product product) {
                if (product.hasVariants() && product.getAvailableVariants().size() > 1) {
                    openDetail(product);
                    return;
                }
                CartManager.getInstance(requireContext()).addProduct(product, product.getDefaultVariant(), 1);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshCartBadge();
                }
                Toast.makeText(requireContext(), product.getName() + " added to cart", Toast.LENGTH_SHORT).show();
            }
        });
        rvProducts.setAdapter(productAdapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim() : "";
                refreshList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        refreshList();
    }

    private void openDetail(Product product) {
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
        categoryAdapter.updateCategories(buildCategories());
        refreshList();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        categoryAdapter.updateCategories(buildCategories());
        refreshList();
    }

    private List<Category> buildCategories() {
        List<Category> cats = new ArrayList<>();
        cats.add(new Category("ALL", "All", "", "ic_dera_emblem", true, 0));
        cats.addAll(productRepo.getAvailableCategories());
        return cats;
    }

    private void refreshList() {
        List<Product> base = "ALL".equals(selectedCategoryId)
                ? productRepo.getAvailableProducts()
                : productRepo.getProductsByCategory(selectedCategoryId);
        List<Product> result = new ArrayList<>();
        String q = searchQuery.toLowerCase();
        for (Product p : base) {
            if (q.isEmpty() || p.getName().toLowerCase().contains(q)
                    || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q))) {
                result.add(p);
            }
        }
        productAdapter.updateItems(result);
        tvEmpty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
