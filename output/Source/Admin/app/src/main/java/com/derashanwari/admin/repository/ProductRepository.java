package com.derashanwari.admin.repository;

import com.derashanwari.admin.model.Banner;
import com.derashanwari.admin.model.Category;
import com.derashanwari.admin.model.Product;

import java.util.List;

public interface ProductRepository {
    List<Product> getAllProducts();
    List<Product> getAvailableProducts();
    List<Product> getFeaturedProducts();
    List<Product> getPopularProducts();
    List<Product> getProductsByCategory(String categoryId);
    List<Product> searchProducts(String query);
    Product getProductById(String productId);
    void saveProduct(Product product);
    void deleteProduct(String productId);

    List<Category> getAllCategories();
    List<Category> getAvailableCategories();
    void saveCategory(Category category);
    void deleteCategory(String categoryId);

    List<Banner> getAllBanners();
    List<Banner> getActiveBanners();
    void saveBanner(Banner banner);
    void deleteBanner(String bannerId);
}
