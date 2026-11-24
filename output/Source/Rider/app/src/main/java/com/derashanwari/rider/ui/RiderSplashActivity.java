package com.derashanwari.rider.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.rider.MainActivity;
import com.derashanwari.rider.R;
import com.derashanwari.rider.firebase.FirebaseConfig;
import com.derashanwari.rider.firebase.FirestoreMapper;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.firebase.GoogleSignInHelper;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.util.ValidationUtil;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RiderSplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_splash);
        RepositoryProvider.init(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            if (RepositoryProvider.isTestMode()) {
                boolean loggedIn = RepositoryProvider.auth(this).getCurrentRider() != null;
                startActivity(new Intent(this, loggedIn ? MainActivity.class : RiderLoginActivity.class));
                finish();
                return;
            }

            FirebaseUser fbUser = FirebaseConfig.getAuth(this).getCurrentUser();
            if (fbUser == null) {
                startActivity(new Intent(this, RiderLoginActivity.class));
                finish();
                return;
            }

            String uid = fbUser.getUid();
            FirebaseFirestore db = FirebaseConfig.getFirestore(this);
            db.collection(FirebaseConfig.COL_RIDERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (isFinishing()) return;
                        if (doc.exists()) {
                            handleRiderDoc(FirestoreMapper.toRider(doc), uid);
                            return;
                        }
                        db.collection(FirebaseConfig.COL_RIDERS)
                                .whereEqualTo("linkedUid", uid)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(snap -> {
                                    if (isFinishing()) return;
                                    if (snap != null && !snap.isEmpty()) {
                                        handleRiderDoc(FirestoreMapper.toRider(snap.getDocuments().get(0)), uid);
                                    } else {
                                        startActivity(new Intent(this, RiderLoginActivity.class));
                                        finish();
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    if (isFinishing()) return;
                                    startActivity(new Intent(this, RiderLoginActivity.class));
                                    finish();
                                });
                    })
                    .addOnFailureListener(err -> {
                        if (isFinishing()) return;
                        startActivity(new Intent(this, RiderLoginActivity.class));
                        finish();
                    });
        }, 700);
    }

    private void handleRiderDoc(Rider r, String uid) {
        RepositoryProvider.sync(this).markRiderPersisted(r.getId());
        if (!r.isActive()) {
            Toast.makeText(this, "Your rider account is deactivated.", Toast.LENGTH_LONG).show();
            GoogleSignInHelper.signOut(this);
            startActivity(new Intent(this, RiderLoginActivity.class));
            finish();
            return;
        }
        if (ValidationUtil.isValidPhone(r.getPhone()) && ValidationUtil.isNonEmpty(r.getVehicleInfo())) {
            RepositoryProvider.sync(this).setCurrentRider(r);
            RepositoryProvider.sync(this).startSync(FirestoreSyncManager.ROLE_RIDER, uid);
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        startActivity(new Intent(this, RiderLoginActivity.class));
        finish();
    }
}
