package com.derashanwari.admin.ui.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

import com.derashanwari.admin.R;
import com.derashanwari.admin.config.ImageKitConfig;
import com.derashanwari.admin.firebase.FirebaseConfig;
import com.derashanwari.admin.firebase.FirestoreErrorLogger;
import com.derashanwari.admin.firebase.FirestoreMapper;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.firebase.GoogleSignInHelper;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.model.Payment;
import com.derashanwari.admin.model.PaymentMethodConfig;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.AdminLoginActivity;
import com.derashanwari.admin.ui.AdminOrderReportActivity;
import com.derashanwari.admin.ui.GroupChatActivity;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.EscPosReceiptFormatter;
import com.derashanwari.admin.util.ImageKitUploader;
import com.derashanwari.admin.util.ImageLoaderUtil;
import com.derashanwari.admin.util.OrderReportCalculator;
import com.derashanwari.admin.util.TimeFormatUtil;
import com.derashanwari.admin.util.UsbThermalPrinterManager;
import com.derashanwari.admin.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminMoreFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private int currentSection = 0;
    private View secPayments, secReports, secSettings;
    private MaterialButton btnPay, btnRep, btnSet, btnGroupChat;
    private LinearLayout containerMethods, containerRecords;
    private TextView tvReports, tvPrinterStatus, tvLogoStatus;
    private ImageView ivLogoPreview;
    private ProgressBar pbLogoUpload;
    private EditText etName, etPhone, etWa, etAddr, etOpen, etClose, etDeliv, etMin,
            etNewAdminUid, etNewAdminEmail, etIkKey, etIkEndpoint;
    private String draftLogoUrl = "";
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) uploadLogoUri(uri);
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                    uploadLogoUri(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) launchLogoCamera();
                else if (isAdded()) {
                    Toast.makeText(requireContext(), "Camera permission needed", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        secPayments = view.findViewById(R.id.section_payments);
        secReports = view.findViewById(R.id.section_reports);
        secSettings = view.findViewById(R.id.section_settings);

        btnPay = view.findViewById(R.id.btn_more_payments);
        btnRep = view.findViewById(R.id.btn_more_reports);
        btnSet = view.findViewById(R.id.btn_more_settings);
        btnGroupChat = view.findViewById(R.id.btn_more_group_chat);

        containerMethods = view.findViewById(R.id.container_payment_methods);
        containerRecords = view.findViewById(R.id.container_payment_records);
        tvReports = view.findViewById(R.id.tv_reports_summary);
        tvPrinterStatus = view.findViewById(R.id.tv_printer_status);
        tvLogoStatus = view.findViewById(R.id.tv_logo_upload_status);
        ivLogoPreview = view.findViewById(R.id.iv_set_logo_preview);
        pbLogoUpload = view.findViewById(R.id.pb_logo_upload);

        etName = view.findViewById(R.id.et_set_name);
        etPhone = view.findViewById(R.id.et_set_phone);
        etWa = view.findViewById(R.id.et_set_whatsapp);
        etAddr = view.findViewById(R.id.et_set_address);
        etOpen = view.findViewById(R.id.et_set_open);
        etClose = view.findViewById(R.id.et_set_close);
        etDeliv = view.findViewById(R.id.et_set_delivery);
        etMin = view.findViewById(R.id.et_set_min_order);
        etIkKey = view.findViewById(R.id.et_set_imagekit_key);
        etIkEndpoint = view.findViewById(R.id.et_set_imagekit_endpoint);
        etNewAdminUid = view.findViewById(R.id.et_new_admin_uid);
        etNewAdminEmail = view.findViewById(R.id.et_new_admin_email);

        btnGroupChat.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GroupChatActivity.class)));
        view.findViewById(R.id.btn_open_detailed_report).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AdminOrderReportActivity.class)));

        btnPay.setOnClickListener(v -> showSection(0));
        btnRep.setOnClickListener(v -> showSection(1));
        btnSet.setOnClickListener(v -> showSection(2));

        view.findViewById(R.id.btn_set_logo_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                launchLogoCamera();
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA);
            }
        });
        view.findViewById(R.id.btn_set_logo_gallery).setOnClickListener(v ->
                galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        view.findViewById(R.id.btn_set_logo_remove).setOnClickListener(v -> {
            draftLogoUrl = "";
            ImageLoaderUtil.loadImage(ivLogoPreview, "", "ic_dera_emblem");
            tvLogoStatus.setText("Logo cleared (tap Save to update cloud)");
        });

        view.findViewById(R.id.btn_printer_reconnect).setOnClickListener(v -> refreshPrinterStatus());
        view.findViewById(R.id.btn_printer_test).setOnClickListener(v -> runTestPrint());

        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());
        view.findViewById(R.id.btn_seed_cloud_catalog).setOnClickListener(v -> {
            if (!RepositoryProvider.isTestMode()) {
                RepositoryProvider.sync(requireContext()).seedDefaultCatalogToFirestore();
                Toast.makeText(requireContext(), "Default menu & online images synced to Cloud Firestore!", Toast.LENGTH_LONG).show();
            }
        });
        view.findViewById(R.id.btn_add_admin_uid).setOnClickListener(v -> addAuthorizedAdmin());
        view.findViewById(R.id.btn_admin_logout).setOnClickListener(v -> {
            RepositoryProvider.auth(requireContext()).logout();
            if (!RepositoryProvider.isTestMode()) {
                GoogleSignInHelper.signOut(requireContext());
            }
            Intent i = new Intent(requireContext(), AdminLoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        showSection(0);
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
        refreshChatBadge();
        if (currentSection == 2) refreshPrinterStatus();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refreshChatBadge();
        if (currentSection == 0) bindPayments();
        else if (currentSection == 1) bindReports();
    }

    private void refreshChatBadge() {
        if (btnGroupChat == null) return;
        int unread = RepositoryProvider.chat(requireContext()).getUnreadGroupCount();
        btnGroupChat.setText(unread > 0 ? "Group Chat (" + unread + ")" : "Group Chat");
    }

    private void showSection(int index) {
        currentSection = index;
        int active = ContextCompat.getColor(requireContext(), R.color.admin_orange);
        int idle = ContextCompat.getColor(requireContext(), R.color.admin_surface_elevated);
        int white = ContextCompat.getColor(requireContext(), R.color.admin_white);
        int dark = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);

        btnPay.setBackgroundColor(index == 0 ? active : idle);
        btnPay.setTextColor(index == 0 ? white : dark);
        btnRep.setBackgroundColor(index == 1 ? active : idle);
        btnRep.setTextColor(index == 1 ? white : dark);
        btnSet.setBackgroundColor(index == 2 ? active : idle);
        btnSet.setTextColor(index == 2 ? white : dark);

        secPayments.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        secReports.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        secSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        if (index == 0) bindPayments();
        else if (index == 1) bindReports();
        else bindSettings();
    }

    private void bindPayments() {
        containerMethods.removeAllViews();
        int textPrimary = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);
        int orangeDark = ContextCompat.getColor(requireContext(), R.color.admin_orange_dark);
        int white = ContextCompat.getColor(requireContext(), R.color.admin_white);

        for (PaymentMethodConfig pm : RepositoryProvider.settings(requireContext()).getPaymentMethods()) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_glass_card));
            card.setElevation(4f);
            card.setPadding(28, 24, 28, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 18);
            card.setLayoutParams(lp);

            SwitchCompat sw = new SwitchCompat(requireContext());
            sw.setText(pm.getDisplayName());
            sw.setTextColor(orangeDark);
            sw.setChecked(pm.isEnabled());
            card.addView(sw);

            EditText etTitle = new EditText(requireContext());
            etTitle.setHint("Account Title");
            etTitle.setText(pm.getAccountTitle());
            etTitle.setTextColor(textPrimary);
            card.addView(etTitle);

            EditText etNum = new EditText(requireContext());
            etNum.setHint("Account Number");
            etNum.setText(pm.getAccountNumber());
            etNum.setTextColor(textPrimary);
            card.addView(etNum);

            MaterialButton btnSave = new MaterialButton(requireContext());
            btnSave.setText("Save " + pm.getDisplayName());
            btnSave.setTextColor(white);
            btnSave.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.admin_orange));
            btnSave.setOnClickListener(v -> {
                pm.setEnabled(sw.isChecked());
                pm.setAccountTitle(etTitle.getText().toString().trim());
                pm.setAccountNumber(etNum.getText().toString().trim());
                RepositoryProvider.settings(requireContext()).savePaymentMethod(pm);
                Toast.makeText(requireContext(), pm.getDisplayName() + " updated", Toast.LENGTH_SHORT).show();
            });
            card.addView(btnSave);
            containerMethods.addView(card);
        }

        containerRecords.removeAllViews();
        for (Payment p : RepositoryProvider.orders(requireContext()).getAllPayments()) {
            TextView tv = new TextView(requireContext());
            tv.setText(p.getOrderId() + " • " + p.getMethod() + " • "
                    + CurrencyUtil.formatPkr(p.getAmount()) + " [" + p.getStatus() + "] (Tap to mark Paid)");
            tv.setTextColor(textPrimary);
            tv.setPadding(24, 20, 24, 20);
            tv.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_glass_card));
            tv.setElevation(2f);
            tv.setOnClickListener(v -> {
                RepositoryProvider.orders(requireContext()).updatePaymentStatus(p.getId(), "Paid");
                bindPayments();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 10);
            tv.setLayoutParams(lp);
            containerRecords.addView(tv);
        }
    }

    private void bindReports() {
        List<Order> orders = RepositoryProvider.orders(requireContext()).getAllOrders();
        List<Order> todayList = OrderReportCalculator.filterByDateRange(
                orders, TimeFormatUtil.startOfDayPkt(0), TimeFormatUtil.endOfDayPkt(0));
        OrderReportCalculator.OrderReportSummary todaySum =
                OrderReportCalculator.calculate(todayList, System.currentTimeMillis());

        long now = System.currentTimeMillis();
        double daily = 0, weekly = 0, monthly = 0, codTotal = 0, jcTotal = 0, epTotal = 0;
        int completed = 0, cancelled = 0;

        for (Order o : orders) {
            if (OrderStatus.CANCELLED.equalsIgnoreCase(o.getOrderStatus())) {
                cancelled++;
                continue;
            }
            if (OrderStatus.DELIVERED.equalsIgnoreCase(o.getOrderStatus())) {
                completed++;
            }
            long age = now - o.getPlacedTimestamp();
            if (age <= 86400000L) daily += o.getTotalAmount();
            if (age <= 7L * 86400000L) weekly += o.getTotalAmount();
            if (age <= 30L * 86400000L) monthly += o.getTotalAmount();

            if (PaymentMethodConfig.METHOD_COD.equalsIgnoreCase(o.getPaymentMethod())) codTotal += o.getTotalAmount();
            else if (PaymentMethodConfig.METHOD_JAZZCASH.equalsIgnoreCase(o.getPaymentMethod())) jcTotal += o.getTotalAmount();
            else epTotal += o.getTotalAmount();
        }

        tvReports.setText(
                "TODAY (PKT) PERFORMANCE\n"
                        + "• Today Orders: " + todaySum.totalOrders + " (Delayed >45m: " + todaySum.delayedOrders + ")\n"
                        + "• Avg Completion Today: " + (todaySum.avgTotalCompletionMs > 0
                        ? TimeFormatUtil.formatDuration(todaySum.avgTotalCompletionMs) : "No completed samples") + "\n\n"
                        + "SALES SUMMARY\n"
                        + "• Daily Sales (24h): " + CurrencyUtil.formatPkr(daily) + "\n"
                        + "• Weekly Sales (7d): " + CurrencyUtil.formatPkr(weekly) + "\n"
                        + "• Monthly Sales (30d): " + CurrencyUtil.formatPkr(monthly) + "\n\n"
                        + "ORDER OUTCOMES\n"
                        + "• Completed Orders: " + completed + "\n"
                        + "• Cancelled Orders: " + cancelled + "\n"
                        + "• Total Orders: " + orders.size() + "\n\n"
                        + "PAYMENT BREAKDOWN\n"
                        + "• Cash on Delivery: " + CurrencyUtil.formatPkr(codTotal) + "\n"
                        + "• JazzCash: " + CurrencyUtil.formatPkr(jcTotal) + "\n"
                        + "• EasyPaisa: " + CurrencyUtil.formatPkr(epTotal));
    }

    private void bindSettings() {
        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        if (s != null) {
            etName.setText(s.getRestaurantName());
            draftLogoUrl = s.getLogoUrl() != null ? s.getLogoUrl() : "";
            ImageLoaderUtil.loadImage(ivLogoPreview, draftLogoUrl, "ic_dera_emblem");
            tvLogoStatus.setText(draftLogoUrl.isEmpty() ? "Using bundled Dera Shanwari emblem" : "Cloud logo active");
            etPhone.setText(s.getPhone());
            etWa.setText(s.getWhatsappNumber());
            etAddr.setText(s.getAddress());
            etOpen.setText(s.getOpeningHours());
            etClose.setText(s.getClosingHours());
            etDeliv.setText(String.valueOf(s.getDeliveryCharges()));
            etMin.setText(String.valueOf(s.getMinimumOrderAmount()));
            etIkKey.setText(s.getImagekitPublicKey());
            etIkEndpoint.setText(s.getImagekitAuthEndpoint());
        }
        refreshPrinterStatus();
    }

    private void refreshPrinterStatus() {
        if (tvPrinterStatus != null && isAdded()) {
            tvPrinterStatus.setText(UsbThermalPrinterManager.describeConnectedPrinter(requireContext()));
        }
    }

    private void runTestPrint() {
        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        byte[] testBytes = EscPosReceiptFormatter.formatTestTicket(s);
        Toast.makeText(requireContext(), "Printing 80mm test ticket...", Toast.LENGTH_SHORT).show();
        UsbThermalPrinterManager.printBytes(requireContext(), testBytes, new UsbThermalPrinterManager.PrintCallback() {
            @Override
            public void onSuccess(String deviceLabel) {
                if (!isAdded()) return;
                refreshPrinterStatus();
                Toast.makeText(requireContext(), "Test ticket printed on " + deviceLabel, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                refreshPrinterStatus();
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchLogoCamera() {
        try {
            File file = File.createTempFile("logo_", ".jpg", requireContext().getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadLogoUri(Uri uri) {
        ivLogoPreview.setImageURI(uri);
        pbLogoUpload.setVisibility(View.VISIBLE);
        pbLogoUpload.setProgress(10);
        tvLogoStatus.setText("Uploading logo...");

        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        s.setImagekitPublicKey(etIkKey.getText().toString().trim());
        s.setImagekitAuthEndpoint(etIkEndpoint.getText().toString().trim());

        ImageKitUploader.uploadUri(requireContext(), uri, ImageKitConfig.FOLDER_BRAND, "logo", s,
                new ImageKitUploader.UploadCallback() {
                    @Override
                    public void onProgress(int percent, String stageLabel) {
                        if (!isAdded()) return;
                        pbLogoUpload.setProgress(percent);
                        tvLogoStatus.setText(stageLabel);
                    }

                    @Override
                    public void onSuccess(ImageKitUploader.UploadResult result) {
                        draftLogoUrl = result.url;
                        if (!isAdded()) return;
                        pbLogoUpload.setVisibility(View.GONE);
                        tvLogoStatus.setText("Logo uploaded! Tap Save below.");
                        ImageLoaderUtil.loadImage(ivLogoPreview, draftLogoUrl, "ic_dera_emblem");
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        pbLogoUpload.setVisibility(View.GONE);
                        tvLogoStatus.setText("Upload failed: " + message);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveSettings() {
        String name = etName.getText().toString().trim();
        if (!ValidationUtil.isNonEmpty(name)) {
            Toast.makeText(requireContext(), "Restaurant name required", Toast.LENGTH_SHORT).show();
            return;
        }
        double delivery, minOrder;
        try {
            delivery = Double.parseDouble(etDeliv.getText().toString().trim());
            minOrder = Double.parseDouble(etMin.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Enter valid numeric charges", Toast.LENGTH_SHORT).show();
            return;
        }
        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        s.setRestaurantName(name);
        s.setLogoUrl(draftLogoUrl);
        s.setPhone(etPhone.getText().toString().trim());
        s.setWhatsappNumber(etWa.getText().toString().trim());
        s.setAddress(etAddr.getText().toString().trim());
        s.setOpeningHours(etOpen.getText().toString().trim());
        s.setClosingHours(etClose.getText().toString().trim());
        s.setDeliveryCharges(delivery);
        s.setMinimumOrderAmount(minOrder);
        s.setImagekitPublicKey(etIkKey.getText().toString().trim());
        s.setImagekitAuthEndpoint(etIkEndpoint.getText().toString().trim());
        RepositoryProvider.settings(requireContext()).saveRestaurantSettings(s);
        Toast.makeText(requireContext(), "Settings saved & synced live!", Toast.LENGTH_SHORT).show();
    }

    private void addAuthorizedAdmin() {
        String uid = FirestoreMapper.sanitizeId(etNewAdminUid.getText().toString().trim());
        String email = etNewAdminEmail.getText().toString().trim().toLowerCase();
        if (!ValidationUtil.isNonEmpty(uid) || !ValidationUtil.isNonEmpty(email) || !email.contains("@")) {
            Toast.makeText(requireContext(), "Enter valid Staff Firebase UID and Google Email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (RepositoryProvider.isTestMode()) return;
        Map<String, Object> adminMap = new HashMap<>();
        adminMap.put("id", uid);
        adminMap.put("name", email);
        adminMap.put("email", email);
        adminMap.put("phone", "");
        adminMap.put("role", "ADMIN");
        adminMap.put("active", true);
        adminMap.put("createdAt", FieldValue.serverTimestamp());
        FirebaseConfig.getFirestore(requireContext())
                .collection(FirebaseConfig.COL_ADMINS)
                .document(uid)
                .set(adminMap)
                .addOnSuccessListener(unused -> {
                    etNewAdminUid.setText("");
                    etNewAdminEmail.setText("");
                    Toast.makeText(requireContext(), "Authorized Admin UID added!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    FirestoreErrorLogger.logError("create", "admins/" + uid, e);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
