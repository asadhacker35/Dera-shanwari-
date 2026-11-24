package com.derashanwari.admin.ui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.Customer;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.Rider;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.adapter.AdminPeopleAdapter;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class AdminPeopleFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private boolean showingRiders = true;
    private AdminPeopleAdapter adapter;
    private MaterialButton btnRiders, btnCustomers;
    private FloatingActionButton fabAddRider;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_people, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnRiders = view.findViewById(R.id.btn_tab_riders);
        btnCustomers = view.findViewById(R.id.btn_tab_customers);
        fabAddRider = view.findViewById(R.id.fab_add_rider);

        RecyclerView rv = view.findViewById(R.id.rv_people);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminPeopleAdapter(new ArrayList<>(), new AdminPeopleAdapter.OnPersonActionListener() {
            @Override
            public void onToggleActive(String id, boolean currentActive) {
                if (showingRiders) {
                    RepositoryProvider.riders(requireContext()).setRiderActive(id, !currentActive);
                } else {
                    RepositoryProvider.customers(requireContext()).setCustomerActive(id, !currentActive);
                }
                refresh();
            }

            @Override
            public void onClick(String id) {
                if (showingRiders) showRiderDetails(id);
                else showCustomerOrderHistory(id);
            }
        });
        rv.setAdapter(adapter);

        btnRiders.setOnClickListener(v -> switchMode(true));
        btnCustomers.setOnClickListener(v -> switchMode(false));
        fabAddRider.setOnClickListener(v -> showRiderDialog(null));

        switchMode(true);
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

    private void switchMode(boolean riders) {
        showingRiders = riders;
        int active = ContextCompat.getColor(requireContext(), R.color.admin_orange);
        int idle = ContextCompat.getColor(requireContext(), R.color.admin_surface_elevated);
        int white = ContextCompat.getColor(requireContext(), R.color.admin_white);
        int dark = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);

        btnRiders.setBackgroundColor(riders ? active : idle);
        btnRiders.setTextColor(riders ? white : dark);
        btnCustomers.setBackgroundColor(!riders ? active : idle);
        btnCustomers.setTextColor(!riders ? white : dark);
        fabAddRider.setVisibility(riders ? View.VISIBLE : View.GONE);
        refresh();
    }

    private void refresh() {
        List<AdminPeopleAdapter.PersonRow> rows = new ArrayList<>();
        if (showingRiders) {
            for (Rider r : RepositoryProvider.riders(requireContext()).getAllRiders()) {
                boolean linked = ValidationUtil.isNonEmpty(r.getLinkedUid()) && !r.getLinkedUid().startsWith("rider_");
                String linkTag = linked ? "[App Linked]" : "[Pending Rider Sign-In]";
                rows.add(new AdminPeopleAdapter.PersonRow(
                        r.getId(), r.getName() + " (" + r.getPhone() + ")",
                        r.getVehicleInfo() + " • " + r.getTotalDeliveries() + " deliveries • "
                                + CurrencyUtil.formatPkr(r.getTotalEarnings()) + " • " + linkTag,
                        r.isActive()));
            }
        } else {
            for (Customer c : RepositoryProvider.customers(requireContext()).getAllCustomers()) {
                rows.add(new AdminPeopleAdapter.PersonRow(
                        c.getId(), c.getName() + " (" + c.getPhone() + ")",
                        c.getAddress() + " • " + c.getLoyaltyPoints() + " pts",
                        c.isActive()));
            }
        }
        adapter.updateRows(rows);
    }

    private void showRiderDetails(String riderId) {
        Rider r = RepositoryProvider.riders(requireContext()).getRiderById(riderId);
        if (r == null) return;
        List<Order> riderOrders = new ArrayList<>(RepositoryProvider.orders(requireContext()).getOrdersByRider(r.getId()));
        if (ValidationUtil.isNonEmpty(r.getLinkedUid()) && !r.getLinkedUid().equals(r.getId())) {
            for (Order lo : RepositoryProvider.orders(requireContext()).getOrdersByRider(r.getLinkedUid())) {
                boolean dup = false;
                for (Order existing : riderOrders) {
                    if (existing.getOrderId().equals(lo.getOrderId())) { dup = true; break; }
                }
                if (!dup) riderOrders.add(lo);
            }
        }
        boolean linked = ValidationUtil.isNonEmpty(r.getLinkedUid()) && !r.getLinkedUid().startsWith("rider_");
        StringBuilder sb = new StringBuilder();
        sb.append("Phone: ").append(r.getPhone())
                .append(ValidationUtil.isNonEmpty(r.getEmail()) ? "\nGoogle Email: " + r.getEmail() : "")
                .append("\nApp Status: ").append(linked ? "Linked (" + r.getLinkedUid() + ")" : "Pending Rider Sign-In")
                .append("\nVehicle: ").append(r.getVehicleInfo())
                .append("\nDeliveries: ").append(r.getTotalDeliveries())
                .append("\nEarnings: ").append(CurrencyUtil.formatPkr(r.getTotalEarnings()))
                .append("\n\nAssigned / History Orders (").append(riderOrders.size()).append("):\n");
        for (Order o : riderOrders) {
            sb.append("• ").append(o.getOrderId()).append(" [").append(o.getOrderStatus()).append("] - ")
                    .append(CurrencyUtil.formatPkr(o.getTotalAmount())).append("\n");
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(r.getName())
                .setMessage(sb.toString())
                .setPositiveButton("Edit Rider", (d, w) -> showRiderDialog(r))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showCustomerOrderHistory(String customerId) {
        Customer c = RepositoryProvider.customers(requireContext()).getCustomerById(customerId);
        if (c == null) return;
        List<Order> orders = RepositoryProvider.orders(requireContext()).getOrdersByCustomer(customerId);
        StringBuilder sb = new StringBuilder();
        sb.append("Phone: ").append(c.getPhone())
                .append("\nAddress: ").append(c.getAddress())
                .append("\nLoyalty Points: ").append(c.getLoyaltyPoints())
                .append("\n\nOrders (").append(orders.size()).append("):\n");
        for (Order o : orders) {
            sb.append("• ").append(o.getOrderId()).append(" [").append(o.getOrderStatus()).append("] ")
                    .append(CurrencyUtil.formatPkr(o.getTotalAmount())).append("\n");
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(c.getName())
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showRiderDialog(@Nullable Rider existing) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rider, null);
        EditText etName = v.findViewById(R.id.et_rider_name);
        EditText etPhone = v.findViewById(R.id.et_rider_phone);
        EditText etEmail = v.findViewById(R.id.et_rider_email);
        EditText etVehicle = v.findViewById(R.id.et_rider_vehicle);
        SwitchCompat swActive = v.findViewById(R.id.sw_rider_active);

        if (existing != null) {
            etName.setText(existing.getName());
            etPhone.setText(existing.getPhone());
            etEmail.setText(existing.getEmail());
            etVehicle.setText(existing.getVehicleInfo());
            swActive.setChecked(existing.isActive());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add Rider" : "Edit Rider")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String email = etEmail.getText().toString().trim().toLowerCase();
                    String vehicle = etVehicle.getText().toString().trim();
                    if (!ValidationUtil.isNonEmpty(name) || !ValidationUtil.isValidPhone(phone)) {
                        Toast.makeText(requireContext(), "Valid rider name and phone required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Rider r = existing != null ? existing : new Rider();
                    r.setName(name);
                    r.setPhone(ValidationUtil.normalizePhone(phone));
                    r.setEmail(email);
                    r.setVehicleInfo(vehicle);
                    r.setActive(swActive.isChecked());
                    RepositoryProvider.riders(requireContext()).saveRider(r);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
