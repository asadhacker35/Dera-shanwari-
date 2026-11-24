package com.example;

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

import com.example.firebase.FirebaseConfig;
import com.example.firebase.FirestoreSyncManager;
import com.example.firebase.RealTimeNotificationHelper;
import com.example.model.NotificationItem;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.ui.fragment.AccountFragment;
import com.example.ui.fragment.CartFragment;
import com.example.ui.fragment.HomeFragment;
import com.example.ui.fragment.MenuFragment;
import com.example.ui.fragment.OrdersFragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements FirestoreSyncManager.AlertListener {
    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RepositoryProvider.init(this);
        RealTimeNotificationHelper.ensureChannel(this);

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser user = FirebaseConfig.getAuth(this).getCurrentUser();
            if (user != null) {
                RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_CUSTOMER, user.getUid());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_menu) {
                loadFragment(new MenuFragment());
                return true;
            } else if (id == R.id.nav_cart) {
                loadFragment(new CartFragment());
                return true;
            } else if (id == R.id.nav_orders) {
                loadFragment(new OrdersFragment());
                return true;
            } else if (id == R.id.nav_account) {
                loadFragment(new AccountFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        refreshCartBadge();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).addAlertListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeAlertListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCartBadge();
        refreshOrdersBadge();
    }

    @Override
    public void onNewNotification(NotificationItem item) {
        Toast.makeText(this, item.getTitle() + ": " + item.getBody(), Toast.LENGTH_LONG).show();
        refreshOrdersBadge();
    }

    public void openMenuTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_menu);
    }

    public void openOrdersTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_orders);
    }

    public void refreshCartBadge() {
        if (bottomNav == null) return;
        int count = CartManager.getInstance(this).getTotalItemCount();
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_cart);
        badge.setVisible(count > 0);
        badge.setNumber(count);
    }

    public void refreshOrdersBadge() {
        if (bottomNav == null || RepositoryProvider.isTestMode()) return;
        int unread = RepositoryProvider.sync(this).getUnreadNotificationCount();
        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_orders);
        badge.setVisible(unread > 0);
        badge.setNumber(unread);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
