package com.derashanwari.admin.repository.cloud;

import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.Banner;
import com.derashanwari.admin.model.Category;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CloudProductRepository implements ProductRepository {
    private final FirestoreSyncManager sync;

    public CloudProductRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override
    public List<Product> getAllProducts() {
        return sync.getProducts();
    }

    @Override
    public List<Product> getAvailableProducts() {
        List<Product> res = new ArrayList<>();
        for (Product p : sync.getProducts()) {
            if (p.isAvailable()) res.add(p);
        }
        return res;
    }

    @Override
    public List<Product> getFeaturedProducts() {
        List<Product> res = new ArrayList<>();
        for (Product p : sync.getProducts()) {
            if (p.isAvailable() && p.isFeatured()) res.add(p);
        }
        return res;
    }

    @Override
    public List<Product> getPopularProducts() {
        List<Product> res = new ArrayList<>();
        for (Product p : sync.getProducts()) {
            if (p.isAvailable() && p.isPopular()) res.add(p);
        }
        return res;
    }

    @Override
    public List<Product> getProductsByCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty() || "ALL".equalsIgnoreCase(categoryId)) {
            return getAvailableProducts();
        }
        List<Product> res = new ArrayList<>();
        for (Product p : sync.getProducts()) {
            if (p.isAvailable() && (categoryId.equals(p.getCategoryId())
                    || categoryId.equalsIgnoreCase(p.getCategoryName()))) {
                res.add(p);
            }
        }
        return res;
    }

    @Override
    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) return getAvailableProducts();
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Product> res = new ArrayList<>();
        for (Product p : sync.getProducts()) {
            if (p.isAvailable() && (
                    (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(q))
                            || (p.getDescription() != null && p.getDescription().toLowerCase(Locale.ROOT).contains(q))
                            || (p.getCategoryName() != null && p.getCategoryName().toLowerCase(Locale.ROOT).contains(q)))) {
                res.add(p);
            }
        }
        return res;
    }

    @Override
    public Product getProductById(String productId) {
        if (productId == null) return null;
        for (Product p : sync.getProducts()) {
            if (productId.equals(p.getId())) return p;
        }
        return null;
    }

    @Override
    public void saveProduct(Product product) {
        sync.saveProduct(product);
    }

    @Override
    public void deleteProduct(String productId) {
        sync.deleteProduct(productId);
    }

    @Override
    public List<Category> getAllCategories() {
        return sync.getCategories();
    }

    @Override
    public List<Category> getAvailableCategories() {
        List<Category> res = new ArrayList<>();
        for (Category c : sync.getCategories()) {
            if (c.isAvailable()) res.add(c);
        }
        return res;
    }

    @Override
    public void saveCategory(Category category) {
        sync.saveCategory(category);
    }

    @Override
    public void deleteCategory(String categoryId) {
        sync.deleteCategory(categoryId);
    }

    @Override
    public List<Banner> getAllBanners() {
        return sync.getBanners();
    }

    @Override
    public List<Banner> getActiveBanners() {
        List<Banner> res = new ArrayList<>();
        for (Banner b : sync.getBanners()) {
            if (b.isActive()) res.add(b);
        }
        return res;
    }

    @Override
    public void saveBanner(Banner banner) {
        sync.saveBanner(banner);
    }

    @Override
    public void deleteBanner(String bannerId) {
        sync.deleteBanner(bannerId);
    }
}
