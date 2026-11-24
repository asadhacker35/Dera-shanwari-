package com.derashanwari.admin.ui.fragment;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.AdminOrderDetailActivity;
import com.derashanwari.admin.ui.adapter.AdminOrderAdapter;
import com.derashanwari.admin.util.TimeFormatUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AdminOrdersFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private static final String[] FILTER_CHIPS = {
            "ALL", "NEW", "CONFIRMED", "KITCHEN", "READY", "OUT FOR DELIVERY", "DELIVERED", "CANCELLED"
    };

    private AdminOrderAdapter adapter;
    private String selectedChip = "ALL";
    private String searchQuery = "";
    private long dateStartMs = 0L;
    private long dateEndMs = Long.MAX_VALUE;
    private MaterialButton btnDateFilter;
    private final List<MaterialButton> chipButtons = new ArrayList<>();

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && adapter != null) {
                adapter.notifyTimersTick();
                timerHandler.postDelayed(this, 1000L);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnDateFilter = view.findViewById(R.id.btn_order_date_filter);
        btnDateFilter.setOnClickListener(v -> showDateFilterMenu());

        EditText etSearch = view.findViewById(R.id.et_order_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                refresh();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout chipContainer = view.findViewById(R.id.container_order_filter_chips);
        buildFilterChips(chipContainer);

        RecyclerView rv = view.findViewById(R.id.rv_admin_orders);
        int span = getResources().getConfiguration().screenWidthDp >= 560 ? 2 : 1;
        rv.setLayoutManager(new GridLayoutManager(requireContext(), span));
        adapter = new AdminOrderAdapter(new ArrayList<>(), order -> {
            Intent i = new Intent(requireContext(), AdminOrderDetailActivity.class);
            i.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getOrderId());
            startActivity(i);
        });
        rv.setAdapter(adapter);
        refresh();
    }

    private void buildFilterChips(LinearLayout container) {
        container.removeAllViews();
        chipButtons.clear();
        for (String chip : FILTER_CHIPS) {
            MaterialButton b = new MaterialButton(requireContext());
            b.setText(chip);
            b.setTextSize(11.5f);
            b.setCornerRadius(24);
            b.setPadding(24, 0, 24, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 96);
            lp.setMargins(0, 0, 12, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                selectedChip = chip;
                styleChips();
                refresh();
            });
            chipButtons.add(b);
            container.addView(b);
        }
        styleChips();
    }

    private void styleChips() {
        int activeBg = ContextCompat.getColor(requireContext(), R.color.admin_orange);
        int idleBg = ContextCompat.getColor(requireContext(), R.color.admin_surface_elevated);
        int white = ContextCompat.getColor(requireContext(), R.color.admin_white);
        int dark = ContextCompat.getColor(requireContext(), R.color.admin_text_primary);
        for (int i = 0; i < chipButtons.size(); i++) {
            boolean sel = FILTER_CHIPS[i].equals(selectedChip);
            chipButtons.get(i).setBackgroundColor(sel ? activeBg : idleBg);
            chipButtons.get(i).setTextColor(sel ? white : dark);
        }
    }

    private void showDateFilterMenu() {
        String[] choices = {"All Dates", "Today (PKT)", "Yesterday (PKT)", "Last 7 Days", "Pick PKT Date..."};
        new AlertDialog.Builder(requireContext())
                .setTitle("Filter Orders by Date")
                .setItems(choices, (d, which) -> {
                    if (which == 0) {
                        dateStartMs = 0L;
                        dateEndMs = Long.MAX_VALUE;
                        btnDateFilter.setText("All Dates");
                        refresh();
                    } else if (which == 1) {
                        dateStartMs = TimeFormatUtil.startOfDayPkt(0);
                        dateEndMs = TimeFormatUtil.endOfDayPkt(0);
                        btnDateFilter.setText("Today");
                        refresh();
                    } else if (which == 2) {
                        dateStartMs = TimeFormatUtil.startOfDayPkt(1);
                        dateEndMs = TimeFormatUtil.endOfDayPkt(1);
                        btnDateFilter.setText("Yesterday");
                        refresh();
                    } else if (which == 3) {
                        dateStartMs = TimeFormatUtil.startOfDayPkt(6);
                        dateEndMs = TimeFormatUtil.endOfDayPkt(0);
                        btnDateFilter.setText("Last 7 Days");
                        refresh();
                    } else {
                        Calendar cal = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
                        new DatePickerDialog(requireContext(), (v, year, month, day) -> {
                            Calendar s = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
                            s.set(year, month, day, 0, 0, 0);
                            s.set(Calendar.MILLISECOND, 0);
                            Calendar e = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
                            e.set(year, month, day, 23, 59, 59);
                            e.set(Calendar.MILLISECOND, 999);
                            dateStartMs = s.getTimeInMillis();
                            dateEndMs = e.getTimeInMillis();
                            btnDateFilter.setText(TimeFormatUtil.formatDateOnlyPkt(dateStartMs));
                            refresh();
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    }
                })
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
        refresh();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.postDelayed(timerRunnable, 1000L);
    }

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refresh();
    }

    private void refresh() {
        List<Order> all = RepositoryProvider.orders(requireContext()).getAllOrders();
        List<Order> filtered = new ArrayList<>();
        for (Order o : all) {
            if (!matchesChip(o.getOrderStatus(), selectedChip)) continue;
            if (dateStartMs > 0L) {
                long placed = o.getPlacedTimestamp();
                if (!TimeFormatUtil.isWithinRange(placed, dateStartMs, dateEndMs)) continue;
            }
            if (!searchQuery.isEmpty() && !matchesSearch(o, searchQuery)) continue;
            filtered.add(o);
        }
        adapter.updateOrders(filtered);
    }

    private boolean matchesChip(String status, String chip) {
        if ("ALL".equals(chip)) return true;
        if (status == null) return false;
        switch (chip) {
            case "NEW":
                return OrderStatus.PENDING.equalsIgnoreCase(status);
            case "CONFIRMED":
                return OrderStatus.CONFIRMED.equalsIgnoreCase(status)
                        || OrderStatus.ASSIGNED.equalsIgnoreCase(status)
                        || OrderStatus.RIDER_ACCEPTED.equalsIgnoreCase(status)
                        || "Assigned to Rider".equalsIgnoreCase(status);
            case "KITCHEN":
                return OrderStatus.SENT_TO_KITCHEN.equalsIgnoreCase(status)
                        || OrderStatus.PREPARING.equalsIgnoreCase(status);
            case "READY":
                return OrderStatus.READY.equalsIgnoreCase(status);
            case "OUT FOR DELIVERY":
                return OrderStatus.OUT_FOR_DELIVERY.equalsIgnoreCase(status)
                        || OrderStatus.PICKED_UP.equalsIgnoreCase(status);
            case "DELIVERED":
                return OrderStatus.DELIVERED.equalsIgnoreCase(status);
            case "CANCELLED":
                return OrderStatus.CANCELLED.equalsIgnoreCase(status);
            default:
                return status.equalsIgnoreCase(chip);
        }
    }

    private boolean matchesSearch(Order o, String q) {
        return contains(o.getOrderId(), q)
                || contains(o.getCustomerName(), q)
                || contains(o.getCustomerPhone(), q)
                || contains(o.getRiderName(), q);
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.US).contains(needle);
    }
}
