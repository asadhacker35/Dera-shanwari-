package com.derashanwari.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.admin.MainActivity;
import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirebaseConfig;
import com.derashanwari.admin.firebase.FirestoreErrorLogger;
import com.derashanwari.admin.firebase.FirestoreMapper;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.firebase.GoogleSignInHelper;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class AdminLoginActivity extends AppCompatActivity {
    private TextView tvStatus;
    private MaterialButton btnGoogleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);
        RepositoryProvider.init(this);

        tvStatus = findViewById(R.id.tv_admin_auth_status);
        btnGoogleSignIn = findViewById(R.id.btn_google_admin_signin);
        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser existing = FirebaseConfig.getAuth(this).getCurrentUser();
            if (existing != null) {
                verifyAdminUser(existing);
            } else {
                GoogleSignInHelper.trySilentAutoSignIn(this, new GoogleSignInHelper.GoogleAuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        verifyAdminUser(user);
                    }

                    @Override
                    public void onCancelled(String message) {}

                    @Override
                    public void onError(String message) {}
                });
            }
        }
    }

    private void startGoogleSignIn() {
        btnGoogleSignIn.setEnabled(false);
        tvStatus.setText("Opening Google Account Selector...");
        GoogleSignInHelper.signInInteractive(this, new GoogleSignInHelper.GoogleAuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                btnGoogleSignIn.setEnabled(true);
                verifyAdminUser(user);
            }

            @Override
            public void onCancelled(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvStatus.setText(message != null && !message.isEmpty()
                        ? message : "Sign-in cancelled. Tap Continue with Google (Admin) to sign in.");
            }

            @Override
            public void onError(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvStatus.setText(message);
                Toast.makeText(AdminLoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void verifyAdminUser(FirebaseUser user) {
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";
        tvStatus.setText("Verifying admin authorization for " + email + "...");

        FirebaseConfig.getFirestore(this)
                .collection(FirebaseConfig.COL_ADMINS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        AdminUser admin = FirestoreMapper.toAdmin(doc);
                        if (!admin.isActive()) {
                            tvStatus.setText("This admin account is deactivated.");
                            GoogleSignInHelper.signOut(this);
                            return;
                        }
                        enterDashboard(admin, uid);
                        return;
                    }

                    if (FirebaseConfig.BOOTSTRAPPED_ADMIN_EMAIL.equalsIgnoreCase(email) && user.isEmailVerified()) {
                        AdminUser owner = new AdminUser(
                                uid,
                                user.getDisplayName() != null ? user.getDisplayName() : "Dera Shanwari Owner",
                                email,
                                "03001112233",
                                "SUPER_ADMIN",
                                true
                        );
                        Map<String, Object> adminMap = FirestoreMapper.fromAdminCreate(owner);
                        FirebaseConfig.getFirestore(this)
                                .collection(FirebaseConfig.COL_ADMINS)
                                .document(uid)
                                .set(adminMap)
                                .addOnSuccessListener(unused -> enterDashboard(owner, uid))
                                .addOnFailureListener(e -> {
                                    FirestoreErrorLogger.logError("create", "admins/" + uid, e);
                                    tvStatus.setText("Could not initialize owner profile: " + e.getMessage());
                                    Toast.makeText(this, "Admin profile write failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        tvStatus.setText("Access denied for " + email
                                + ".\nAsk the owner (" + FirebaseConfig.BOOTSTRAPPED_ADMIN_EMAIL
                                + ") to authorize UID: " + uid);
                        GoogleSignInHelper.signOut(this);
                    }
                })
                .addOnFailureListener(err -> {
                    FirestoreErrorLogger.logError("get", "admins/" + uid, err);
                    tvStatus.setText("Authorization check failed: " + err.getMessage());
                    Toast.makeText(this, "Admin check failed: " + err.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void enterDashboard(AdminUser admin, String uid) {
        RepositoryProvider.sync(this).setCurrentAdmin(admin);
        RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_ADMIN, uid);
        Toast.makeText(this, "Welcome, " + admin.getName(), Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
