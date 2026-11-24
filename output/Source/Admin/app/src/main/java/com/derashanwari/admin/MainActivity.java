package com.derashanwari.admin;

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

import com.derashanwari.admin.firebase.FirebaseConfig;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.firebase.RealTimeNotificationHelper;
import com.derashanwari.admin.model.NotificationItem;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.fragment.AdminCatalogFragment;
import com.derashanwari.admin.ui.fragment.AdminDashboardFragment;
import com.derashanwari.admin.ui.fragment.AdminMoreFragment;
import com.derashanwari.admin.ui.fragment.AdminOrdersFragment;
import com.derashanwari.admin.ui.fragment.AdminPeopleFragment;
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
        setContentView(R.layout.activity_admin_main);
        RepositoryProvider.init(this);
        RealTimeNotificationHelper.ensureChannel(this);

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser user = FirebaseConfig.getAuth(this).getCurrentUser();
            if (user != null) {
                RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_ADMIN, user.getUid());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        nav = findViewById(R.id.admin_bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_admin_dashboard) {
                load(new AdminDashboardFragment());
                return true;
            } else if (id == R.id.nav_admin_catalog) {
                load(new AdminCatalogFragment());
                return true;
            } else if (id == R.id.nav_admin_orders) {
                load(new AdminOrdersFragment());
                return true;
            } else if (id == R.id.nav_admin_people) {
                load(new AdminPeopleFragment());
                return true;
            } else if (id == R.id.nav_admin_more) {
                load(new AdminMoreFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_admin_dashboard);
        }
        refreshOrdersBadge();
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
        refreshOrdersBadge();
    }

    @Override
    public void onNewNotification(NotificationItem item) {
        Toast.makeText(this, item.getTitle() + ": " + item.getBody(), Toast.LENGTH_LONG).show();
        refreshOrdersBadge();
    }

    @Override
    public void onDataChanged() {
        refreshOrdersBadge();
    }

    private void refreshOrdersBadge() {
        if (nav == null) return;
        int pending = 0;
        for (Order o : RepositoryProvider.orders(this).getAllOrders()) {
            if (OrderStatus.PENDING.equalsIgnoreCase(o.getOrderStatus())) pending++;
        }
        BadgeDrawable badge = nav.getOrCreateBadge(R.id.nav_admin_orders);
        badge.setVisible(pending > 0);
        badge.setNumber(pending);
    }

    private void load(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.admin_fragment_container, fragment)
                .commit();
    }
}
