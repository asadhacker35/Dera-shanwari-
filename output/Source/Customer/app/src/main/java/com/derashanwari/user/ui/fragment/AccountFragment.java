package com.derashanwari.user.ui.fragment;

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

import com.derashanwari.user.R;
import com.derashanwari.user.firebase.FirebaseConfig;
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.firebase.GoogleSignInHelper;
import com.derashanwari.user.model.Customer;
import com.derashanwari.user.model.RestaurantSettings;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.ui.AuthActivity;
import com.derashanwari.user.ui.GroupChatActivity;
import com.derashanwari.user.ui.RewardsActivity;
import com.derashanwari.user.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;

public class AccountFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private TextView tvName, tvPhone, tvAddress, tvPoints, tvRestaurantInfo, tvChatBadge;

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
        tvChatBadge = view.findViewById(R.id.tv_account_chat_badge);

        MaterialButton btnEdit = view.findViewById(R.id.btn_edit_profile);
        View cardLoyalty = view.findViewById(R.id.card_loyalty);
        View cardGroupChat = view.findViewById(R.id.card_account_group_chat);
        MaterialButton btnLogout = view.findViewById(R.id.btn_logout);

        btnEdit.setOnClickListener(v -> showEditDialog());
        cardLoyalty.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RewardsActivity.class)));
        cardGroupChat.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GroupChatActivity.class)));

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
        int unread = RepositoryProvider.chat(requireContext()).getUnreadGroupCount();
        tvChatBadge.setText(unread > 0 ? unread + " new" : "Open");

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
