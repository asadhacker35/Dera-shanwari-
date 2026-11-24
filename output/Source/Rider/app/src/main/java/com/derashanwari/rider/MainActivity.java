package com.derashanwari.rider;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.derashanwari.rider.firebase.FirebaseConfig;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.firebase.RealTimeNotificationHelper;
import com.derashanwari.rider.model.NotificationItem;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.ui.fragment.RiderDashboardFragment;
import com.derashanwari.rider.ui.fragment.RiderOrdersFragment;
import com.derashanwari.rider.ui.fragment.RiderProfileFragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity
        implements FirestoreSyncManager.AlertListener, FirestoreSyncManager.DataChangeListener {
    private BottomNavigationView nav;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_main);
        RepositoryProvider.init(this);
        RealTimeNotificationHelper.ensureChannel(this);

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser user = FirebaseConfig.getAuth(this).getCurrentUser();
            if (user != null) {
                RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_RIDER, user.getUid());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        nav = findViewById(R.id.rider_bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_rider_dashboard) {
                load(new RiderDashboardFragment());
                return true;
            } else if (id == R.id.nav_rider_orders) {
                load(RiderOrdersFragment.newInstance(false));
                return true;
            } else if (id == R.id.nav_rider_history) {
                load(RiderOrdersFragment.newInstance(true));
                return true;
            } else if (id == R.id.nav_rider_profile) {
                load(new RiderProfileFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_rider_dashboard);
        }
        refreshAssignedBadge();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).addAlertListener(this);
            RepositoryProvider.sync(this).addDataChangeListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeAlertListener(this);
            RepositoryProvider.sync(this).removeDataChangeListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAssignedBadge();
    }

    @Override
    public void onNewNotification(NotificationItem item) {
        Toast.makeText(this, item.getTitle() + ": " + item.getBody(), Toast.LENGTH_LONG).show();
        refreshAssignedBadge();
    }

    @Override
    public void onDataChanged() {
        refreshAssignedBadge();
    }

    private void refreshAssignedBadge() {
        if (nav == null) return;
        Rider r = RepositoryProvider.auth(this).getCurrentRider();
        String riderId = r != null ? r.getId() : null;
        if (riderId == null && !RepositoryProvider.isTestMode()) {
            riderId = FirebaseConfig.getAuth(this).getUid();
        }
        int active = 0;
        if (riderId != null) {
            for (Order o : RepositoryProvider.orders(this).getOrdersByRider(riderId)) {
                if (!OrderStatus.isCompletedOrCancelled(o.getOrderStatus())) active++;
            }
        }
        BadgeDrawable badge = nav.getOrCreateBadge(R.id.nav_rider_orders);
        badge.setVisible(active > 0);
        badge.setNumber(active);
    }

    private void load(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.rider_fragment_container, fragment)
                .commit();
    }
}
