package com.example.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.MainActivity;
import com.example.R;
import com.example.firebase.FirebaseConfig;
import com.example.firebase.FirestoreMapper;
import com.example.firebase.FirestoreSyncManager;
import com.example.firebase.GoogleSignInHelper;
import com.example.model.Customer;
import com.example.repository.RepositoryProvider;
import com.example.util.ValidationUtil;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        RepositoryProvider.init(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            if (RepositoryProvider.isTestMode()) {
                boolean loggedIn = RepositoryProvider.auth(this).getCurrentCustomer() != null;
                startActivity(new Intent(this, loggedIn ? MainActivity.class : AuthActivity.class));
                finish();
                return;
            }

            FirebaseUser fbUser = FirebaseConfig.getAuth(this).getCurrentUser();
            if (fbUser == null) {
                startActivity(new Intent(this, AuthActivity.class));
                finish();
                return;
            }

            String uid = fbUser.getUid();
            FirebaseConfig.getFirestore(this)
                    .collection(FirebaseConfig.COL_CUSTOMERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (isFinishing()) return;
                        if (doc.exists()) {
                            RepositoryProvider.sync(this).markCustomerPersisted(uid);
                            Customer c = FirestoreMapper.toCustomer(doc);
                            if (!c.isActive()) {
                                Toast.makeText(this, "Your customer account is suspended.", Toast.LENGTH_LONG).show();
                                GoogleSignInHelper.signOut(this);
                                startActivity(new Intent(this, AuthActivity.class));
                                finish();
                                return;
                            }
                            if (ValidationUtil.isValidPhone(c.getPhone()) && ValidationUtil.isNonEmpty(c.getAddress())) {
                                RepositoryProvider.sync(this).setCurrentCustomer(c);
                                RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_CUSTOMER, uid);
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                                return;
                            }
                        }
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                    })
                    .addOnFailureListener(err -> {
                        if (isFinishing()) return;
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                    });
        }, 700);
    }
}
