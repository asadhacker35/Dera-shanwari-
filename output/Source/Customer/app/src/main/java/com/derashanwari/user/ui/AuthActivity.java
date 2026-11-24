package com.derashanwari.user.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.user.MainActivity;
import com.derashanwari.user.R;
import com.derashanwari.user.firebase.FirebaseConfig;
import com.derashanwari.user.firebase.FirestoreMapper;
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.firebase.GoogleSignInHelper;
import com.derashanwari.user.model.Customer;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseUser;

public class AuthActivity extends AppCompatActivity {
    private EditText etName, etPhone, etAddress;
    private LinearLayout layoutSignInStep, layoutProfileStep;
    private TextView tvBanner;
    private MaterialButton btnGoogleSignIn, btnSaveProfile;
    private FirebaseUser signedInUser;
    private Customer existingCustomer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        tvBanner = findViewById(R.id.tv_auth_status_banner);
        layoutSignInStep = findViewById(R.id.layout_signin_step);
        layoutProfileStep = findViewById(R.id.layout_profile_step);
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etAddress = findViewById(R.id.et_address);
        btnGoogleSignIn = findViewById(R.id.btn_google_signin);
        btnSaveProfile = findViewById(R.id.btn_save_profile);

        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
        btnSaveProfile.setOnClickListener(v -> saveProfileAndEnter());

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser existing = FirebaseConfig.getAuth(this).getCurrentUser();
            if (existing != null) {
                onGoogleUserAuthenticated(existing);
            } else {
                GoogleSignInHelper.trySilentAutoSignIn(this, new GoogleSignInHelper.GoogleAuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        onGoogleUserAuthenticated(user);
                    }
                    @Override public void onCancelled(String message) {}
                    @Override public void onError(String message) {}
                });
            }
        }
    }

    private void startGoogleSignIn() {
        btnGoogleSignIn.setEnabled(false);
        tvBanner.setText("Opening Google Account Selector...");
        GoogleSignInHelper.signInInteractive(this, new GoogleSignInHelper.GoogleAuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                btnGoogleSignIn.setEnabled(true);
                onGoogleUserAuthenticated(user);
            }

            @Override
            public void onCancelled(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvBanner.setText(message != null && !message.isEmpty()
                        ? message : "Sign-in cancelled. Tap Continue with Google to sign in.");
            }

            @Override
            public void onError(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvBanner.setText(message);
                Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onGoogleUserAuthenticated(FirebaseUser user) {
        this.signedInUser = user;
        String uid = user.getUid();
        tvBanner.setText("Signed in as " + (user.getEmail() != null ? user.getEmail() : uid) + ". Loading profile...");

        FirebaseConfig.getFirestore(this)
                .collection(FirebaseConfig.COL_CUSTOMERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        RepositoryProvider.sync(this).markCustomerPersisted(uid);
                        Customer c = FirestoreMapper.toCustomer(doc);
                        this.existingCustomer = c;
                        if (!c.isActive()) {
                            tvBanner.setText("Your customer account has been suspended. Contact restaurant.");
                            GoogleSignInHelper.signOut(this);
                            return;
                        }
                        if (ValidationUtil.isValidPhone(c.getPhone()) && ValidationUtil.isNonEmpty(c.getAddress())) {
                            RepositoryProvider.sync(this).setCurrentCustomer(c);
                            RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_CUSTOMER, uid);
                            openMain();
                            return;
                        }
                        etName.setText(c.getName());
                        etPhone.setText(c.getPhone());
                        etAddress.setText(c.getAddress());
                    } else {
                        this.existingCustomer = null;
                        if (user.getDisplayName() != null) etName.setText(user.getDisplayName());
                    }
                    layoutSignInStep.setVisibility(View.GONE);
                    layoutProfileStep.setVisibility(View.VISIBLE);
                    tvBanner.setText("Please confirm your mobile number & delivery address once.");
                })
                .addOnFailureListener(err -> {
                    if (user.getDisplayName() != null) etName.setText(user.getDisplayName());
                    layoutSignInStep.setVisibility(View.GONE);
                    layoutProfileStep.setVisibility(View.VISIBLE);
                    tvBanner.setText("Enter your delivery details to complete setup.");
                });
    }

    private void saveProfileAndEnter() {
        if (signedInUser == null) return;
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (!ValidationUtil.isNonEmpty(name)) {
            etName.setError("Full name required");
            return;
        }
        if (!ValidationUtil.isValidPhone(phone)) {
            etPhone.setError("Enter valid mobile number (e.g. 03009876543)");
            return;
        }
        if (!ValidationUtil.isNonEmpty(address)) {
            etAddress.setError("Delivery address required");
            return;
        }

        int points = existingCustomer != null ? existingCustomer.getLoyaltyPoints() : 50;
        long createdAt = existingCustomer != null ? existingCustomer.getCreatedAt() : System.currentTimeMillis();
        Customer customer = new Customer(
                signedInUser.getUid(),
                name,
                signedInUser.getEmail() != null ? signedInUser.getEmail() : "",
                ValidationUtil.normalizePhone(phone),
                address,
                true,
                points,
                createdAt
        );
        RepositoryProvider.customers(this).saveCustomer(customer);
        RepositoryProvider.sync(this).setCurrentCustomer(customer);
        RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_CUSTOMER, signedInUser.getUid());
        Toast.makeText(this, "Welcome to Dera Shanwari, " + name + "!", Toast.LENGTH_SHORT).show();
        openMain();
    }

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
