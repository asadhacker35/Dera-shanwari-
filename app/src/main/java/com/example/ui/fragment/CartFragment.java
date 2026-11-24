package com.example.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.MainActivity;
import com.example.R;
import com.example.model.OrderItem;
import com.example.model.RestaurantSettings;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.ui.CheckoutActivity;
import com.example.ui.adapter.CartAdapter;
import com.example.util.CurrencyUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CartFragment extends Fragment {
    private CartAdapter adapter;
    private LinearLayout layoutEmpty;
    private View cardSummary;
    private RecyclerView rvCart;
    private TextView tvSubtotal, tvDelivery, tvTotal;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        layoutEmpty = view.findViewById(R.id.layout_cart_empty);
        cardSummary = view.findViewById(R.id.card_cart_summary);
        rvCart = view.findViewById(R.id.rv_cart_items);
        tvSubtotal = view.findViewById(R.id.tv_cart_subtotal);
        tvDelivery = view.findViewById(R.id.tv_cart_delivery);
        tvTotal = view.findViewById(R.id.tv_cart_total);
        MaterialButton btnBrowse = view.findViewById(R.id.btn_browse_menu);
        MaterialButton btnCheckout = view.findViewById(R.id.btn_checkout);

        rvCart.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CartAdapter(new ArrayList<>(), (item, newQty) -> {
            CartManager.getInstance(requireContext()).updateQuantity(item.getProductId(), newQty);
            refreshCart();
        });
        rvCart.setAdapter(adapter);

        btnBrowse.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openMenuTab();
            }
        });

        btnCheckout.setOnClickListener(v -> {
            CartManager cart = CartManager.getInstance(requireContext());
            if (cart.getTotalItemCount() <= 0) {
                Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            RestaurantSettings settings = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
            double minOrder = settings != null ? settings.getMinimumOrderAmount() : 0;
            if (cart.getSubtotal() < minOrder) {
                Toast.makeText(requireContext(),
                        "Minimum order amount is " + CurrencyUtil.formatPkr(minOrder),
                        Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(requireContext(), CheckoutActivity.class));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCart();
    }

    private void refreshCart() {
        if (!isAdded() || getContext() == null) return;
        CartManager cart = CartManager.getInstance(requireContext());
        List<OrderItem> items = cart.getItems();
        adapter.updateItems(items);

        boolean empty = items.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCart.setVisibility(empty ? View.GONE : View.VISIBLE);
        cardSummary.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            RestaurantSettings settings = RepositoryProvider.settings(requireContext()).getRestaurantSettings();
            double delivery = settings != null ? settings.getDeliveryCharges() : 150.0;
            double subtotal = cart.getSubtotal();
            double total = subtotal + delivery;
            tvSubtotal.setText("Subtotal: " + CurrencyUtil.formatPkr(subtotal));
            tvDelivery.setText("Delivery Charges: " + CurrencyUtil.formatPkr(delivery));
            tvTotal.setText("Grand Total: " + CurrencyUtil.formatPkr(total));
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshCartBadge();
        }
    }
}
