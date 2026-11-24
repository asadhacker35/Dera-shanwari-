package com.derashanwari.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.admin.MainActivity;
import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirebaseConfig;
import com.derashanwari.admin.firebase.FirestoreMapper;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.firebase.GoogleSignInHelper;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.google.firebase.auth.FirebaseUser;

public class AdminSplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_splash);
        RepositoryProvider.init(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            if (RepositoryProvider.isTestMode()) {
                boolean loggedIn = RepositoryProvider.auth(this).getCurrentAdmin() != null;
                startActivity(new Intent(this, loggedIn ? MainActivity.class : AdminLoginActivity.class));
                finish();
                return;
            }

            FirebaseUser fbUser = FirebaseConfig.getAuth(this).getCurrentUser();
            if (fbUser == null) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                finish();
                return;
            }

            String uid = fbUser.getUid();
            FirebaseConfig.getFirestore(this)
                    .collection(FirebaseConfig.COL_ADMINS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (isFinishing()) return;
                        if (doc.exists()) {
                            AdminUser admin = FirestoreMapper.toAdmin(doc);
                            if (!admin.isActive()) {
                                Toast.makeText(this, "This admin account is deactivated.", Toast.LENGTH_LONG).show();
                                GoogleSignInHelper.signOut(this);
                                startActivity(new Intent(this, AdminLoginActivity.class));
                                finish();
                                return;
                            }
                            RepositoryProvider.sync(this).setCurrentAdmin(admin);
                            RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_ADMIN, uid);
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                            return;
                        }
                        startActivity(new Intent(this, AdminLoginActivity.class));
                        finish();
                    })
                    .addOnFailureListener(err -> {
                        if (isFinishing()) return;
                        startActivity(new Intent(this, AdminLoginActivity.class));
                        finish();
                    });
        }, 700);
    }
}
