package com.example.ui.fragment;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.R;
import com.example.firebase.FirestoreSyncManager;
import com.example.firebase.GoogleSignInHelper;
import com.example.model.Customer;
import com.example.model.RestaurantSettings;
import com.example.repository.RepositoryProvider;
import com.example.ui.AuthActivity;
import com.example.ui.RewardsActivity;
import com.example.util.ValidationUtil;
import com.example.util.WhatsAppHelper;
import com.google.android.material.button.MaterialButton;

public class AccountFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private TextView tvName, tvPhone, tvAddress, tvPoints, tvRestaurantInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvName = view.findViewById(R.id.tv_account_name);
        tvPhone = view.findViewById(R.id.tv_account_phone);
        tvAddress = view.findViewById(R.id.tv_account_address);
        tvPoints = view.findViewById(R.id.tv_account_points);
        tvRestaurantInfo = view.findViewById(R.id.tv_restaurant_info);

        MaterialButton btnEdit = view.findViewById(R.id.btn_edit_profile);
        View cardLoyalty = view.findViewById(R.id.card_loyalty);
        View cardSupport = view.findViewById(R.id.card_customer_support);
        MaterialButton btnLogout = view.findViewById(R.id.btn_logout);

        btnEdit.setOnClickListener(v -> showEditDialog());
        cardLoyalty.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RewardsActivity.class)));

        cardSupport.setOnClickListener(v -> showSupportDialog());

        btnLogout.setOnClickListener(v -> {
            RepositoryProvider.auth(requireContext()).logout();
            if (!RepositoryProvider.isTestMode()) {
                GoogleSignInHelper.signOut(requireContext());
            }
            Intent i = new Intent(requireContext(), AuthActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    private void showSupportDialog() {
        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        String phone = s != null && ValidationUtil.isNonEmpty(s.getPhone()) ? s.getPhone() : "+923001112233";
        String waNumber = s != null && ValidationUtil.isNonEmpty(s.getWhatsappNumber()) ? s.getWhatsappNumber() : phone;

        new AlertDialog.Builder(requireContext())
                .setTitle("Dera Shanwari Support")
                .setMessage("Need help with your order, reservation, or catering? Contact our restaurant helpline directly:")
                .setPositiveButton("Call Admin", (d, w) -> WhatsAppHelper.openPhoneDialer(requireContext(), phone))
                .setNeutralButton("WhatsApp Chat", (d, w) -> WhatsAppHelper.openWhatsAppWithMessage(requireContext(), waNumber, "Hello Dera Shanwari, I need help with my order / inquiry."))
                .setNegativeButton("Cancel", null)
                .show();
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
        bindData();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        bindData();
    }

    private void bindData() {
        Customer c = RepositoryProvider.auth(requireContext()).getCurrentCustomer();
        if (c != null) {
            tvName.setText(c.getName());
            String contact = ValidationUtil.isNonEmpty(c.getPhone()) ? c.getPhone() : c.getEmail();
            tvPhone.setText(contact != null ? contact : "");
            tvAddress.setText("Saved Address: " + (c.getAddress() != null ? c.getAddress() : ""));
            tvPoints.setText(c.getLoyaltyPoints() + " Loyalty Points");
        }
        RestaurantSettings s = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
        if (s != null) {
            tvRestaurantInfo.setText(s.getRestaurantName()
                    + "\nAddress: " + s.getAddress()
                    + "\nPhone: " + s.getPhone()
                    + "\nHours: " + s.getOpeningHours() + " - " + s.getClosingHours());
        }
    }

    private void showEditDialog() {
        Customer c = RepositoryProvider.auth(requireContext()).getCurrentCustomer();
        if (c == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null);
        EditText etName = dialogView.findViewById(R.id.et_edit_name);
        EditText etAddr = dialogView.findViewById(R.id.et_edit_address);
        etName.setText(c.getName());
        etAddr.setText(c.getAddress());

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Profile & Address")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newAddr = etAddr.getText().toString().trim();
                    if (!ValidationUtil.isNonEmpty(newName) || !ValidationUtil.isNonEmpty(newAddr)) {
                        Toast.makeText(requireContext(), "Name and address are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    c.setName(newName);
                    c.setAddress(newAddr);
                    if (RepositoryProvider.isTestMode()) {
                        RepositoryProvider.customers(requireContext()).saveCustomer(c);
                    } else {
                        RepositoryProvider.sync(requireContext()).updateCustomerNameAddress(c.getId(), newName, newAddr);
                    }
                    bindData();
                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
