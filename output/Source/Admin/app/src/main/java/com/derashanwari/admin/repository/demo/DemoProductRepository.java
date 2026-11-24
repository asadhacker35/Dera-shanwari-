package com.derashanwari.admin.repository.demo;

import com.derashanwari.admin.model.Banner;
import com.derashanwari.admin.model.Category;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

public class DemoProductRepository implements ProductRepository {
    private final DemoDatabaseManager db;

    public DemoProductRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Product> getAllProducts() {
        return new ArrayList<>(db.getState().products);
    }

    @Override
    public List<Product> getAvailableProducts() {
        List<Product> result = new ArrayList<>();
        for (Product p : db.getState().products) {
            if (p.isAvailable()) result.add(p);
        }
        return result;
    }

    @Override
    public List<Product> getFeaturedProducts() {
        List<Product> result = new ArrayList<>();
        for (Product p : db.getState().products) {
            if (p.isAvailable() && p.isFeatured()) result.add(p);
        }
        return result;
    }

    @Override
    public List<Product> getPopularProducts() {
        List<Product> result = new ArrayList<>();
        for (Product p : db.getState().products) {
            if (p.isAvailable() && p.isPopular()) result.add(p);
        }
        return result;
    }

    @Override
    public List<Product> getProductsByCategory(String categoryId) {
        if (categoryId == null || "ALL".equalsIgnoreCase(categoryId)) {
            return getAvailableProducts();
        }
        List<Product> result = new ArrayList<>();
        for (Product p : db.getState().products) {
            if (p.isAvailable() && categoryId.equals(p.getCategoryId())) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAvailableProducts();
        }
        String lower = query.trim().toLowerCase();
        List<Product> result = new ArrayList<>();
        for (Product p : db.getState().products) {
            if (!p.isAvailable()) continue;
            if ((p.getName() != null && p.getName().toLowerCase().contains(lower))
                    || (p.getDescription() != null && p.getDescription().toLowerCase().contains(lower))
                    || (p.getCategoryName() != null && p.getCategoryName().toLowerCase().contains(lower))) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public Product getProductById(String productId) {
        for (Product p : db.getState().products) {
            if (p.getId().equals(productId)) return p;
        }
        return null;
    }

    @Override
    public void saveProduct(Product product) {
        if (product.getId() == null || product.getId().trim().isEmpty()) {
            product.setId("prod_" + System.currentTimeMillis());
            product.setCreatedAt(System.currentTimeMillis());
        }
        List<Product> list = db.getState().products;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(product.getId())) {
                list.set(i, product);
                db.save();
                return;
            }
        }
        list.add(0, product);
        db.save();
    }

    @Override
    public void deleteProduct(String productId) {
        List<Product> list = db.getState().products;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(productId)) {
                list.remove(i);
                db.save();
                return;
            }
        }
    }

    @Override
    public List<Category> getAllCategories() {
        return new ArrayList<>(db.getState().categories);
    }

    @Override
    public List<Category> getAvailableCategories() {
        List<Category> result = new ArrayList<>();
        for (Category c : db.getState().categories) {
            if (c.isAvailable()) result.add(c);
        }
        return result;
    }

    @Override
    public void saveCategory(Category category) {
        if (category.getId() == null || category.getId().trim().isEmpty()) {
            category.setId("cat_" + System.currentTimeMillis());
        }
        List<Category> list = db.getState().categories;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(category.getId())) {
                list.set(i, category);
                db.save();
                return;
            }
        }
        list.add(category);
        db.save();
    }

    @Override
    public void deleteCategory(String categoryId) {
        List<Category> list = db.getState().categories;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(categoryId)) {
                list.remove(i);
                db.save();
                return;
            }
        }
    }

    @Override
    public List<Banner> getAllBanners() {
        return new ArrayList<>(db.getState().banners);
    }

    @Override
    public List<Banner> getActiveBanners() {
        List<Banner> result = new ArrayList<>();
        for (Banner b : db.getState().banners) {
            if (b.isActive()) result.add(b);
        }
        return result;
    }

    @Override
    public void saveBanner(Banner banner) {
        if (banner.getId() == null || banner.getId().trim().isEmpty()) {
            banner.setId("ban_" + System.currentTimeMillis());
        }
        List<Banner> list = db.getState().banners;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(banner.getId())) {
                list.set(i, banner);
                db.save();
                return;
            }
        }
        list.add(0, banner);
        db.save();
    }

    @Override
    public void deleteBanner(String bannerId) {
        List<Banner> list = db.getState().banners;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(bannerId)) {
                list.remove(i);
                db.save();
                return;
            }
        }
    }
}
