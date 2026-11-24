package com.derashanwari.admin.ui.fragment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.config.ImageKitConfig;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.Banner;
import com.derashanwari.admin.model.Category;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.model.ProductVariant;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.repository.ProductRepository;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.adapter.AdminCatalogAdapter;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.ImageKitUploader;
import com.derashanwari.admin.util.ImageLoaderUtil;
import com.derashanwari.admin.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminCatalogFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private static final int TAB_PRODUCTS = 0;
    private static final int TAB_CATEGORIES = 1;
    private static final int TAB_BANNERS = 2;

    private interface ImagePickReceiver {
        void onUriSelected(Uri uri);
    }

    private int currentTab = TAB_PRODUCTS;
    private ProductRepository repo;
    private AdminCatalogAdapter adapter;
    private MaterialButton btnProducts, btnCategories, btnBanners;
    private ImagePickReceiver activePickReceiver;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null && activePickReceiver != null) {
                    activePickReceiver.onUriSelected(uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null && activePickReceiver != null) {
                    activePickReceiver.onUriSelected(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    launchCameraInternal();
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), "Camera permission is required to capture photos", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_catalog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = RepositoryProvider.products(requireContext());

        btnProducts = view.findViewById(R.id.btn_tab_products);
        btnCategories = view.findViewById(R.id.btn_tab_categories);
        btnBanners = view.findViewById(R.id.btn_tab_banners);

        RecyclerView rv = view.findViewById(R.id.rv_catalog);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminCatalogAdapter(new ArrayList<>(), new AdminCatalogAdapter.OnCatalogActionListener() {
            @Override
            public void onEdit(String id) {
                if (currentTab == TAB_PRODUCTS) showProductDialog(repo.getProductById(id));
                else if (currentTab == TAB_CATEGORIES) showCategoryDialog(findCategory(id));
                else showBannerDialog(findBanner(id));
            }

            @Override
            public void onDelete(String id) {
                if (currentTab == TAB_PRODUCTS) {
                    Product existing = repo.getProductById(id);
                    if (existing != null && !existing.getImageFileId().isEmpty()) {
                        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
                        ImageKitUploader.deleteRemoteImage(requireContext(), existing.getImageFileId(), s, null);
                    }
                    repo.deleteProduct(id);
                } else if (currentTab == TAB_CATEGORIES) {
                    Category existing = findCategory(id);
                    if (existing != null && !existing.getImageFileId().isEmpty()) {
                        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
                        ImageKitUploader.deleteRemoteImage(requireContext(), existing.getImageFileId(), s, null);
                    }
                    repo.deleteCategory(id);
                } else {
                    Banner existing = findBanner(id);
                    if (existing != null && !existing.getImageFileId().isEmpty()) {
                        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
                        ImageKitUploader.deleteRemoteImage(requireContext(), existing.getImageFileId(), s, null);
                    }
                    repo.deleteBanner(id);
                }
                refresh();
                Toast.makeText(requireContext(), "Item deleted from cloud", Toast.LENGTH_SHORT).show();
            }
        });
        rv.setAdapter(adapter);

        btnProducts.setOnClickListener(v -> switchTab(TAB_PRODUCTS));
        btnCategories.setOnClickListener(v -> switchTab(TAB_CATEGORIES));
        btnBanners.setOnClickListener(v -> switchTab(TAB_BANNERS));

        view.findViewById(R.id.fab_add_catalog).setOnClickListener(v -> {
            if (currentTab == TAB_PRODUCTS) showProductDialog(null);
            else if (currentTab == TAB_CATEGORIES) showCategoryDialog(null);
            else showBannerDialog(null);
        });

        switchTab(TAB_PRODUCTS);
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
    public void onDataChanged() {
        if (!isAdded()) return;
        refresh();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        int active = ContextCompat.getColor(requireContext(), R.color.admin_orange);
        int idle = ContextCompat.getColor(requireContext(), R.color.admin_surface_elevated);
        int white = ContextCompat.getColor(requireContext(), R.color.admin_white);
        int dark = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);

        btnProducts.setBackgroundColor(tab == TAB_PRODUCTS ? active : idle);
        btnProducts.setTextColor(tab == TAB_PRODUCTS ? white : dark);

        btnCategories.setBackgroundColor(tab == TAB_CATEGORIES ? active : idle);
        btnCategories.setTextColor(tab == TAB_CATEGORIES ? white : dark);

        btnBanners.setBackgroundColor(tab == TAB_BANNERS ? active : idle);
        btnBanners.setTextColor(tab == TAB_BANNERS ? white : dark);

        refresh();
    }

    private void refresh() {
        List<AdminCatalogAdapter.CatalogRow> rows = new ArrayList<>();
        if (currentTab == TAB_PRODUCTS) {
            for (Product p : repo.getAllProducts()) {
                String priceStr = (p.hasVariants() ? "From " : "")
                        + CurrencyUtil.formatPkr(p.getStartingPrice())
                        + (p.hasDiscount() ? " (" + p.getDiscountPercent() + "% OFF)" : "");
                String variantTag = p.hasVariants() ? " • " + p.getVariants().size() + " variants" : "";
                rows.add(new AdminCatalogAdapter.CatalogRow(
                        p.getId(), p.getName(),
                        p.getCategoryName() + " • " + (p.isAvailable() ? "Available" : "Unavailable")
                                + (p.isFeatured() ? " • Featured" : "") + variantTag,
                        priceStr, p.getImageUrl(), p.getLocalDrawableName()));
            }
        } else if (currentTab == TAB_CATEGORIES) {
            for (Category c : repo.getAllCategories()) {
                rows.add(new AdminCatalogAdapter.CatalogRow(
                        c.getId(), c.getName(),
                        c.isAvailable() ? "Visible in Customer Menu" : "Hidden",
                        "Order #" + c.getSortOrder(), c.getImageUrl(), c.getLocalDrawableName()));
            }
        } else {
            for (Banner b : repo.getAllBanners()) {
                rows.add(new AdminCatalogAdapter.CatalogRow(
                        b.getId(), b.getTitle(), b.getSubtitle(),
                        b.isActive() ? "Active Banner" : "Inactive",
                        b.getImageUrl(), b.getLocalDrawableName()));
            }
        }
        adapter.updateRows(rows);
    }

    private Category findCategory(String id) {
        for (Category c : repo.getAllCategories()) if (c.getId().equals(id)) return c;
        return null;
    }

    private Banner findBanner(String id) {
        for (Banner b : repo.getAllBanners()) if (b.getId().equals(id)) return b;
        return null;
    }

    private void requestCamera(ImagePickReceiver receiver) {
        activePickReceiver = receiver;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCameraInternal();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void requestGallery(ImagePickReceiver receiver) {
        activePickReceiver = receiver;
        galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCameraInternal() {
        try {
            File file = File.createTempFile("catalog_", ".jpg", requireContext().getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Unable to start camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static class ImageState {
        String url;
        String fileId;
        boolean uploading;

        ImageState(String url, String fileId) {
            this.url = url != null ? url : "";
            this.fileId = fileId != null ? fileId : "";
        }
    }

    private void bindUploaderControls(ImageView ivPreview, ProgressBar pb, TextView tvStatus,
                                      View btnCam, View btnGal, View btnRemove,
                                      ImageState state, String folder, String prefix, String fallbackDrawable) {
        ImageLoaderUtil.loadImage(ivPreview, state.url, fallbackDrawable);
        tvStatus.setText(state.url.isEmpty() ? "No photo uploaded yet" : "Photo ready (" + (state.fileId.isEmpty() ? "Cloud" : "ImageKit") + ")");

        ImagePickReceiver receiver = uri -> {
            ivPreview.setImageURI(uri);
            pb.setVisibility(View.VISIBLE);
            pb.setProgress(10);
            tvStatus.setText("Preparing upload...");
            state.uploading = true;
            String oldFileId = state.fileId;
            RestaurantSettings settings = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
            ImageKitUploader.uploadUri(requireContext(), uri, folder, prefix, settings,
                    new ImageKitUploader.UploadCallback() {
                        @Override
                        public void onProgress(int percent, String stageLabel) {
                            if (!isAdded()) return;
                            pb.setProgress(percent);
                            tvStatus.setText(stageLabel);
                        }

                        @Override
                        public void onSuccess(ImageKitUploader.UploadResult result) {
                            state.uploading = false;
                            state.url = result.url;
                            state.fileId = result.fileId;
                            if (!isAdded()) return;
                            pb.setVisibility(View.GONE);
                            tvStatus.setText("Uploaded to ImageKit!");
                            ImageLoaderUtil.loadImage(ivPreview, result.url, fallbackDrawable);
                            if (!oldFileId.isEmpty() && !oldFileId.equals(result.fileId)) {
                                ImageKitUploader.deleteRemoteImage(requireContext(), oldFileId, settings, null);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            state.uploading = false;
                            if (!isAdded()) return;
                            pb.setVisibility(View.GONE);
                            tvStatus.setText("Upload failed: " + message);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        }
                    });
        };

        btnCam.setOnClickListener(v -> requestCamera(receiver));
        btnGal.setOnClickListener(v -> requestGallery(receiver));
        btnRemove.setOnClickListener(v -> {
            if (!state.fileId.isEmpty()) {
                RestaurantSettings settings = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
                ImageKitUploader.deleteRemoteImage(requireContext(), state.fileId, settings, null);
            }
            state.url = "";
            state.fileId = "";
            ImageLoaderUtil.loadImage(ivPreview, "", fallbackDrawable);
            tvStatus.setText("Photo removed (will use fallback or new upload)");
        });
    }

    private void showProductDialog(@Nullable Product existing) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_product, null);
        ImageView ivPreview = v.findViewById(R.id.iv_prod_preview);
        ProgressBar pbUpload = v.findViewById(R.id.pb_prod_upload);
        TextView tvUploadStatus = v.findViewById(R.id.tv_prod_upload_status);
        EditText etName = v.findViewById(R.id.et_prod_name);
        EditText etDesc = v.findViewById(R.id.et_prod_desc);
        EditText etPrice = v.findViewById(R.id.et_prod_price);
        EditText etDisc = v.findViewById(R.id.et_prod_discount);
        Spinner spCat = v.findViewById(R.id.spinner_prod_category);
        LinearLayout containerVariants = v.findViewById(R.id.container_prod_variants);
        SwitchCompat swAvail = v.findViewById(R.id.sw_prod_available);
        SwitchCompat swFeat = v.findViewById(R.id.sw_prod_featured);
        SwitchCompat swPop = v.findViewById(R.id.sw_prod_popular);

        ImageState imgState = new ImageState(
                existing != null ? existing.getImageUrl() : "",
                existing != null ? existing.getImageFileId() : "");
        bindUploaderControls(
                ivPreview, pbUpload, tvUploadStatus,
                v.findViewById(R.id.btn_prod_camera),
                v.findViewById(R.id.btn_prod_gallery),
                v.findViewById(R.id.btn_prod_remove_photo),
                imgState, ImageKitConfig.FOLDER_PRODUCTS, "product",
                existing != null ? existing.getLocalDrawableName() : "img_banner_shanwari");

        List<Category> cats = repo.getAllCategories();
        List<String> catNames = new ArrayList<>();
        int selCat = 0;
        for (int i = 0; i < cats.size(); i++) {
            catNames.add(cats.get(i).getName());
            if (existing != null && cats.get(i).getId().equals(existing.getCategoryId())) selCat = i;
        }
        spCat.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, catNames));
        if (!catNames.isEmpty()) spCat.setSelection(selCat);

        List<ProductVariant> draftVariants = new ArrayList<>();
        if (existing != null && existing.getVariants() != null) {
            for (ProductVariant pv : existing.getVariants()) {
                if (pv != null) {
                    draftVariants.add(new ProductVariant(
                            pv.getId(), pv.getName(), pv.getPrice(), pv.getDiscountPrice(),
                            pv.isAvailable(), pv.getImageUrl(), pv.getDescription()));
                }
            }
        }

        Runnable renderVariants = new Runnable() {
            @Override
            public void run() {
                renderVariantRows(containerVariants, draftVariants, this);
            }
        };
        renderVariants.run();

        v.findViewById(R.id.btn_add_variant).setOnClickListener(btn ->
                showVariantEditorDialog(null, draftVariants, renderVariants));

        if (existing != null) {
            etName.setText(existing.getName());
            etDesc.setText(existing.getDescription());
            etPrice.setText(String.valueOf(existing.getPrice()));
            etDisc.setText(String.valueOf(existing.getDiscountPrice()));
            swAvail.setChecked(existing.isAvailable());
            swFeat.setChecked(existing.isFeatured());
            swPop.setChecked(existing.isPopular());
        }

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add Product" : "Edit Product")
                .setView(v)
                .setPositiveButton("Save to Cloud", null)
                .setNegativeButton("Cancel", (d, w) -> activePickReceiver = null)
                .create();

        dlg.setOnShowListener(dialogInterface -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (imgState.uploading) {
                Toast.makeText(requireContext(), "Please wait for image upload to finish", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = etName.getText().toString().trim();
            if (!ValidationUtil.isNonEmpty(name)) {
                Toast.makeText(requireContext(), "Product name required", Toast.LENGTH_SHORT).show();
                return;
            }

            double price = 0, disc = 0;
            String priceStr = etPrice.getText().toString().trim();
            String discTxt = etDisc.getText().toString().trim();
            try {
                if (!priceStr.isEmpty()) price = Double.parseDouble(priceStr);
                if (!discTxt.isEmpty()) disc = Double.parseDouble(discTxt);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Enter valid numeric prices", Toast.LENGTH_SHORT).show();
                return;
            }

            if (price <= 0 && !draftVariants.isEmpty()) {
                double minVar = Double.MAX_VALUE;
                for (ProductVariant pv : draftVariants) {
                    if (pv.getPrice() > 0) minVar = Math.min(minVar, pv.getPrice());
                }
                if (minVar != Double.MAX_VALUE) price = minVar;
            }

            if (!ValidationUtil.isValidPrice(price) || !ValidationUtil.isValidDiscountPrice(price, disc)) {
                Toast.makeText(requireContext(), "Enter valid base price (or add variants) and discount < price", Toast.LENGTH_SHORT).show();
                return;
            }

            Category chosenCat = cats.isEmpty() ? null : cats.get(spCat.getSelectedItemPosition());
            Product p = existing != null ? existing : new Product();
            p.setName(name);
            p.setDescription(etDesc.getText().toString().trim());
            p.setPrice(price);
            p.setDiscountPrice(disc);
            p.setImageUrl(imgState.url.isEmpty() ? "" : ImageKitConfig.buildTransformedUrl(imgState.url, 900, 700));
            p.setImageFileId(imgState.fileId);
            if (p.getLocalDrawableName() == null || p.getLocalDrawableName().isEmpty()) {
                p.setLocalDrawableName("img_banner_shanwari");
            }
            if (chosenCat != null) {
                p.setCategoryId(chosenCat.getId());
                p.setCategoryName(chosenCat.getName());
            }
            p.setVariants(new ArrayList<>(draftVariants));
            p.setAvailable(swAvail.isChecked());
            p.setFeatured(swFeat.isChecked());
            p.setPopular(swPop.isChecked());
            repo.saveProduct(p);
            refresh();
            activePickReceiver = null;
            dlg.dismiss();
            Toast.makeText(requireContext(), "Saved & synced live to Customer App!", Toast.LENGTH_SHORT).show();
        }));
        dlg.show();
    }

    private void renderVariantRows(LinearLayout container, List<ProductVariant> variants, Runnable refreshCallback) {
        container.removeAllViews();
        if (variants.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No variants added. Single base price will be used.");
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_text_muted));
            empty.setTextSize(12f);
            container.addView(empty);
            return;
        }
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);
        int danger = ContextCompat.getColor(requireContext(), R.color.admin_danger);
        for (int i = 0; i < variants.size(); i++) {
            ProductVariant pv = variants.get(i);
            final int index = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 8, 8, 8);
            row.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_glass_card));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 6);
            row.setLayoutParams(lp);

            TextView tv = new TextView(requireContext());
            String priceText = CurrencyUtil.formatPkr(pv.getEffectivePrice())
                    + (pv.hasDiscount() ? " (was " + CurrencyUtil.formatPkr(pv.getPrice()) + ")" : "");
            tv.setText(pv.getName() + " — " + priceText + (pv.isAvailable() ? "" : " [Off]"));
            tv.setTextColor(textPrimary);
            tv.setTextSize(12.5f);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);
            tv.setOnClickListener(v -> showVariantEditorDialog(pv, variants, refreshCallback));
            row.addView(tv);

            TextView btnDel = new TextView(requireContext());
            btnDel.setText("Remove");
            btnDel.setTextColor(danger);
            btnDel.setTextSize(12f);
            btnDel.setPadding(12, 4, 4, 4);
            btnDel.setOnClickListener(v -> {
                variants.remove(index);
                refreshCallback.run();
            });
            row.addView(btnDel);

            container.addView(row);
        }
    }

    private void showVariantEditorDialog(@Nullable ProductVariant existing, List<ProductVariant> list,
                                         Runnable onUpdated) {
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_product_variant, null);
        EditText etVName = dv.findViewById(R.id.et_variant_name);
        EditText etVPrice = dv.findViewById(R.id.et_variant_price);
        EditText etVDisc = dv.findViewById(R.id.et_variant_discount);
        EditText etVDesc = dv.findViewById(R.id.et_variant_desc);
        SwitchCompat swVAvail = dv.findViewById(R.id.sw_variant_available);

        if (existing != null) {
            etVName.setText(existing.getName());
            etVPrice.setText(String.valueOf(existing.getPrice()));
            etVDisc.setText(String.valueOf(existing.getDiscountPrice()));
            etVDesc.setText(existing.getDescription());
            swVAvail.setChecked(existing.isAvailable());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add Variant" : "Edit Variant")
                .setView(dv)
                .setPositiveButton("Apply", (d, w) -> {
                    String vName = etVName.getText().toString().trim();
                    if (!ValidationUtil.isNonEmpty(vName)) {
                        Toast.makeText(requireContext(), "Variant name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double vPrice, vDisc = 0;
                    try {
                        vPrice = Double.parseDouble(etVPrice.getText().toString().trim());
                        String dTxt = etVDisc.getText().toString().trim();
                        if (!dTxt.isEmpty()) vDisc = Double.parseDouble(dTxt);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Enter valid numeric variant price", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!ValidationUtil.isValidPrice(vPrice) || !ValidationUtil.isValidDiscountPrice(vPrice, vDisc)) {
                        Toast.makeText(requireContext(), "Invalid variant price or discount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (existing != null) {
                        existing.setName(vName);
                        existing.setPrice(vPrice);
                        existing.setDiscountPrice(vDisc);
                        existing.setDescription(etVDesc.getText().toString().trim());
                        existing.setAvailable(swVAvail.isChecked());
                    } else {
                        list.add(new ProductVariant(
                                "var_" + UUID.randomUUID().toString().substring(0, 8),
                                vName, vPrice, vDisc, swVAvail.isChecked(), "",
                                etVDesc.getText().toString().trim()));
                    }
                    onUpdated.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCategoryDialog(@Nullable Category existing) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category, null);
        ImageView ivPreview = v.findViewById(R.id.iv_cat_preview);
        ProgressBar pbUpload = v.findViewById(R.id.pb_cat_upload);
        TextView tvStatus = v.findViewById(R.id.tv_cat_upload_status);
        EditText etName = v.findViewById(R.id.et_cat_name);
        SwitchCompat swAvail = v.findViewById(R.id.sw_cat_available);

        ImageState imgState = new ImageState(
                existing != null ? existing.getImageUrl() : "",
                existing != null ? existing.getImageFileId() : "");
        bindUploaderControls(
                ivPreview, pbUpload, tvStatus,
                v.findViewById(R.id.btn_cat_camera),
                v.findViewById(R.id.btn_cat_gallery),
                v.findViewById(R.id.btn_cat_remove_photo),
                imgState, ImageKitConfig.FOLDER_CATEGORIES, "cat",
                existing != null ? existing.getLocalDrawableName() : "img_food_tikka_bbq");

        if (existing != null) {
            etName.setText(existing.getName());
            swAvail.setChecked(existing.isAvailable());
        }

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add Category" : "Edit Category")
                .setView(v)
                .setPositiveButton("Save to Cloud", null)
                .setNegativeButton("Cancel", (d, w) -> activePickReceiver = null)
                .create();

        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (imgState.uploading) {
                Toast.makeText(requireContext(), "Please wait for image upload to finish", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = etName.getText().toString().trim();
            if (!ValidationUtil.isNonEmpty(name)) {
                Toast.makeText(requireContext(), "Category name required", Toast.LENGTH_SHORT).show();
                return;
            }
            Category c = existing != null ? existing : new Category();
            c.setName(name);
            c.setImageUrl(imgState.url.isEmpty() ? "" : ImageKitConfig.buildTransformedUrl(imgState.url, 700, 700));
            c.setImageFileId(imgState.fileId);
            if (c.getLocalDrawableName() == null || c.getLocalDrawableName().isEmpty()) {
                c.setLocalDrawableName("img_food_tikka_bbq");
            }
            c.setAvailable(swAvail.isChecked());
            if (c.getSortOrder() <= 0) c.setSortOrder(repo.getAllCategories().size() + 1);
            repo.saveCategory(c);
            refresh();
            activePickReceiver = null;
            dlg.dismiss();
            Toast.makeText(requireContext(), "Category synced live!", Toast.LENGTH_SHORT).show();
        }));
        dlg.show();
    }

    private void showBannerDialog(@Nullable Banner existing) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_banner, null);
        ImageView ivPreview = v.findViewById(R.id.iv_ban_preview);
        ProgressBar pbUpload = v.findViewById(R.id.pb_ban_upload);
        TextView tvStatus = v.findViewById(R.id.tv_ban_upload_status);
        EditText etTitle = v.findViewById(R.id.et_ban_title);
        EditText etSub = v.findViewById(R.id.et_ban_subtitle);
        SwitchCompat swActive = v.findViewById(R.id.sw_ban_active);

        ImageState imgState = new ImageState(
                existing != null ? existing.getImageUrl() : "",
                existing != null ? existing.getImageFileId() : "");
        bindUploaderControls(
                ivPreview, pbUpload, tvStatus,
                v.findViewById(R.id.btn_ban_camera),
                v.findViewById(R.id.btn_ban_gallery),
                v.findViewById(R.id.btn_ban_remove_photo),
                imgState, ImageKitConfig.FOLDER_BANNERS, "banner",
                existing != null ? existing.getLocalDrawableName() : "img_banner_shanwari");

        if (existing != null) {
            etTitle.setText(existing.getTitle());
            etSub.setText(existing.getSubtitle());
            swActive.setChecked(existing.isActive());
        }

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add Banner" : "Edit Banner")
                .setView(v)
                .setPositiveButton("Save to Cloud", null)
                .setNegativeButton("Cancel", (d, w) -> activePickReceiver = null)
                .create();

        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (imgState.uploading) {
                Toast.makeText(requireContext(), "Please wait for image upload to finish", Toast.LENGTH_SHORT).show();
                return;
            }
            String title = etTitle.getText().toString().trim();
            if (!ValidationUtil.isNonEmpty(title)) {
                Toast.makeText(requireContext(), "Banner title required", Toast.LENGTH_SHORT).show();
                return;
            }
            Banner b = existing != null ? existing : new Banner();
            b.setTitle(title);
            b.setSubtitle(etSub.getText().toString().trim());
            b.setImageUrl(imgState.url.isEmpty() ? "" : ImageKitConfig.buildTransformedUrl(imgState.url, 1200, 600));
            b.setImageFileId(imgState.fileId);
            if (b.getLocalDrawableName() == null || b.getLocalDrawableName().isEmpty()) {
                b.setLocalDrawableName("img_banner_shanwari");
            }
            b.setActive(swActive.isChecked());
            if (b.getSortOrder() <= 0) b.setSortOrder(repo.getAllBanners().size() + 1);
            repo.saveBanner(b);
            refresh();
            activePickReceiver = null;
            dlg.dismiss();
            Toast.makeText(requireContext(), "Banner synced live!", Toast.LENGTH_SHORT).show();
        }));
        dlg.show();
    }
}
