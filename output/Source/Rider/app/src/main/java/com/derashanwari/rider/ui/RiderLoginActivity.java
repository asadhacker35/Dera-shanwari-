package com.derashanwari.rider.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.rider.MainActivity;
import com.derashanwari.rider.R;
import com.derashanwari.rider.firebase.FirebaseConfig;
import com.derashanwari.rider.firebase.FirestoreErrorLogger;
import com.derashanwari.rider.firebase.FirestoreMapper;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.firebase.GoogleSignInHelper;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.util.ValidationUtil;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RiderLoginActivity extends AppCompatActivity {
    private EditText etName, etPhone, etVehicle;
    private LinearLayout layoutSignInStep, layoutProfileStep;
    private TextView tvStatus;
    private MaterialButton btnGoogleSignIn, btnSaveProfile;
    private FirebaseUser signedInUser;
    private Rider existingRider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_login);
        RepositoryProvider.init(this);

        tvStatus = findViewById(R.id.tv_rider_auth_status);
        layoutSignInStep = findViewById(R.id.layout_rider_signin_step);
        layoutProfileStep = findViewById(R.id.layout_rider_profile_step);
        etName = findViewById(R.id.et_rider_name);
        etPhone = findViewById(R.id.et_rider_phone);
        etVehicle = findViewById(R.id.et_rider_vehicle);
        btnGoogleSignIn = findViewById(R.id.btn_google_rider_signin);
        btnSaveProfile = findViewById(R.id.btn_save_rider_profile);

        btnGoogleSignIn.setOnClickListener(v -> startGoogleSignIn());
        btnSaveProfile.setOnClickListener(v -> saveProfileAndEnter());

        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser existing = FirebaseConfig.getAuth(this).getCurrentUser();
            if (existing != null) {
                onGoogleRiderAuthenticated(existing);
            } else {
                GoogleSignInHelper.trySilentAutoSignIn(this, new GoogleSignInHelper.GoogleAuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        onGoogleRiderAuthenticated(user);
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
                onGoogleRiderAuthenticated(user);
            }

            @Override
            public void onCancelled(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvStatus.setText(message != null && !message.isEmpty()
                        ? message : "Sign-in cancelled. Tap Continue with Google (Rider) to sign in.");
            }

            @Override
            public void onError(String message) {
                btnGoogleSignIn.setEnabled(true);
                tvStatus.setText(message);
                Toast.makeText(RiderLoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onGoogleRiderAuthenticated(FirebaseUser user) {
        this.signedInUser = user;
        this.existingRider = null;
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
        tvStatus.setText("Signed in as " + (!email.isEmpty() ? email : uid) + ". Checking rider profile...");

        FirebaseFirestore db = FirebaseConfig.getFirestore(this);
        db.collection(FirebaseConfig.COL_RIDERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        handleLoadedRider(user, FirestoreMapper.toRider(doc));
                        return;
                    }
                    db.collection(FirebaseConfig.COL_RIDERS)
                            .whereEqualTo("linkedUid", uid)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(linkedSnap -> {
                                if (linkedSnap != null && !linkedSnap.isEmpty()) {
                                    handleLoadedRider(user, FirestoreMapper.toRider(linkedSnap.getDocuments().get(0)));
                                    return;
                                }
                                if (user.isEmailVerified() && !email.isEmpty()) {
                                    db.collection(FirebaseConfig.COL_RIDERS)
                                            .whereEqualTo("email", email)
                                            .limit(1)
                                            .get()
                                            .addOnSuccessListener(emailSnap -> {
                                                if (emailSnap != null && !emailSnap.isEmpty()) {
                                                    handleLoadedRider(user, FirestoreMapper.toRider(emailSnap.getDocuments().get(0)));
                                                } else {
                                                    showProfileForm(user, null);
                                                }
                                            })
                                            .addOnFailureListener(err -> {
                                                FirestoreErrorLogger.logError("list", "riders/email", err);
                                                showProfileForm(user, null);
                                            });
                                } else {
                                    showProfileForm(user, null);
                                }
                            })
                            .addOnFailureListener(err -> {
                                FirestoreErrorLogger.logError("list", "riders/linkedUid", err);
                                showProfileForm(user, null);
                            });
                })
                .addOnFailureListener(err -> {
                    FirestoreErrorLogger.logError("get", "riders/" + uid, err);
                    showProfileForm(user, null);
                });
    }

    private void handleLoadedRider(FirebaseUser user, Rider r) {
        this.existingRider = r;
        RepositoryProvider.sync(this).markRiderPersisted(r.getId());
        if (!r.isActive()) {
            tvStatus.setText("Your rider account is currently deactivated. Contact restaurant admin.");
            GoogleSignInHelper.signOut(this);
            return;
        }
        String uid = user.getUid();
        boolean needsLinkClaim = !uid.equals(r.getLinkedUid()) && !uid.equals(r.getId());
        if (ValidationUtil.isValidPhone(r.getPhone()) && ValidationUtil.isNonEmpty(r.getVehicleInfo())) {
            if (needsLinkClaim) {
                claimEmailMatchedRider(r, uid, r.getName(), r.getPhone(), r.getVehicleInfo());
            } else {
                RepositoryProvider.sync(this).setCurrentRider(r);
                RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_RIDER, uid);
                openMain();
            }
            return;
        }
        showProfileForm(user, r);
    }

    private void showProfileForm(FirebaseUser user, Rider r) {
        if (r != null) {
            etName.setText(r.getName());
            etPhone.setText(r.getPhone());
            etVehicle.setText(r.getVehicleInfo());
        } else if (user.getDisplayName() != null) {
            etName.setText(user.getDisplayName());
        }
        layoutSignInStep.setVisibility(View.GONE);
        layoutProfileStep.setVisibility(View.VISIBLE);
        tvStatus.setText("Complete your phone & vehicle info so Admin can assign orders to you.");
    }

    private void claimEmailMatchedRider(Rider r, String uid, String name, String phone, String vehicle) {
        Map<String, Object> claim = new HashMap<>();
        claim.put("linkedUid", uid);
        claim.put("name", name);
        claim.put("phone", phone);
        claim.put("vehicleInfo", vehicle);
        claim.put("updatedAt", FieldValue.serverTimestamp());
        btnSaveProfile.setEnabled(false);
        FirebaseConfig.getFirestore(this)
                .collection(FirebaseConfig.COL_RIDERS)
                .document(r.getId())
                .update(claim)
                .addOnSuccessListener(unused -> {
                    btnSaveProfile.setEnabled(true);
                    r.setLinkedUid(uid);
                    r.setName(name);
                    r.setPhone(phone);
                    r.setVehicleInfo(vehicle);
                    RepositoryProvider.sync(this).markRiderPersisted(r.getId());
                    RepositoryProvider.sync(this).setCurrentRider(r);
                    RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_RIDER, uid);
                    Toast.makeText(this, "Welcome to Dera Fleet, " + name + "!", Toast.LENGTH_SHORT).show();
                    openMain();
                })
                .addOnFailureListener(err -> {
                    btnSaveProfile.setEnabled(true);
                    FirestoreErrorLogger.logError("update", "riders/claim/" + r.getId(), err);
                    tvStatus.setText("Failed to link rider account: " + err.getMessage());
                    Toast.makeText(this, "Could not link rider profile: " + err.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveProfileAndEnter() {
        if (signedInUser == null) return;
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String vehicle = etVehicle.getText().toString().trim();

        if (!ValidationUtil.isNonEmpty(name)) {
            etName.setError("Full name required");
            return;
        }
        if (!ValidationUtil.isValidPhone(phone)) {
            etPhone.setError("Enter valid mobile number (e.g. 03125556677)");
            return;
        }
        if (!ValidationUtil.isNonEmpty(vehicle)) {
            etVehicle.setError("Vehicle info required");
            return;
        }

        String uid = signedInUser.getUid();
        String normPhone = ValidationUtil.normalizePhone(phone);
        if (existingRider != null && !uid.equals(existingRider.getLinkedUid()) && !uid.equals(existingRider.getId())) {
            claimEmailMatchedRider(existingRider, uid, name, normPhone, vehicle);
            return;
        }

        String docId = existingRider != null ? existingRider.getId() : uid;
        String email = signedInUser.getEmail() != null ? signedInUser.getEmail().trim().toLowerCase() : "";
        String avatar = existingRider != null ? existingRider.getProfileImageUrl() : "";
        int deliveries = existingRider != null ? existingRider.getTotalDeliveries() : 0;
        double earnings = existingRider != null ? existingRider.getTotalEarnings() : 0.0;
        long createdAt = existingRider != null ? existingRider.getCreatedAt() : System.currentTimeMillis();
        Rider rider = new Rider(
                docId,
                uid,
                name,
                email,
                normPhone,
                vehicle,
                avatar,
                true,
                deliveries,
                earnings,
                createdAt
        );
        RepositoryProvider.riders(this).saveRider(rider);
        RepositoryProvider.sync(this).setCurrentRider(rider);
        RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_RIDER, uid);
        Toast.makeText(this, "Welcome to Dera Fleet, " + name + "!", Toast.LENGTH_SHORT).show();
        openMain();
    }

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
