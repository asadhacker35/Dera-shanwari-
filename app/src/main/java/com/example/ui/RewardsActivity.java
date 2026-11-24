package com.example.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.firebase.FirestoreSyncManager;
import com.example.model.Customer;
import com.example.model.Reward;
import com.example.repository.CustomerRepository;
import com.example.repository.RepositoryProvider;
import com.example.ui.adapter.RewardAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class RewardsActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    private TextView tvBalance;
    private RewardAdapter offersAdapter, historyAdapter;
    private CustomerRepository customerRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);
        customerRepo = RepositoryProvider.customers(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvBalance = findViewById(R.id.tv_points_balance);

        RecyclerView rvOffers = findViewById(R.id.rv_offers);
        rvOffers.setLayoutManager(new LinearLayoutManager(this));
        offersAdapter = new RewardAdapter(customerRepo.getRedeemableOffers(), offer -> {
            Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
            if (c == null) return;
            boolean ok = customerRepo.redeemOffer(c.getId(), offer);
            if (ok) {
                Toast.makeText(this, "Redeemed: " + offer.getTitle(), Toast.LENGTH_LONG).show();
                refresh();
            } else {
                Toast.makeText(this, "Not enough points! Need " + offer.getMinPointsRequired() + " pts.", Toast.LENGTH_SHORT).show();
            }
        });
        rvOffers.setAdapter(offersAdapter);

        RecyclerView rvHistory = findViewById(R.id.rv_rewards_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new RewardAdapter(new ArrayList<>(), null);
        rvHistory.setAdapter(historyAdapter);

        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).addDataChangeListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeDataChangeListener(this);
        }
    }

    @Override
    public void onDataChanged() {
        refresh();
    }

    private void refresh() {
        offersAdapter.updateItems(customerRepo.getRedeemableOffers());
        Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
        if (c == null) return;
        tvBalance.setText(c.getLoyaltyPoints() + " pts");
        historyAdapter.updateItems(customerRepo.getRewardsForCustomer(c.getId()));
    }
}
