package com.example.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

/**
 * Structured Firestore error logger emitting JSON context without leaking credentials.
 */
public final class FirestoreErrorLogger {
    private static final String TAG = "DeraFirestore";

    private FirestoreErrorLogger() {}

    public static void logError(String operationType, String path, Throwable error) {
        try {
            FirebaseUser user = null;
            try {
                user = FirebaseAuth.getInstance().getCurrentUser();
            } catch (Exception ignored) {
            }
            JSONObject authInfo = new JSONObject();
            authInfo.put("userId", user != null ? user.getUid() : JSONObject.NULL);
            authInfo.put("email", user != null && user.getEmail() != null ? user.getEmail() : JSONObject.NULL);
            authInfo.put("emailVerified", user != null && user.isEmailVerified());

            JSONObject payload = new JSONObject();
            payload.put("error", error != null && error.getMessage() != null ? error.getMessage() : "Unknown Firestore error");
            payload.put("operationType", operationType);
            payload.put("path", path);
            payload.put("authInfo", authInfo);

            Log.e(TAG, "Firestore Error: " + payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Firestore Error [" + operationType + " " + path + "]", error);
        }
    }
}
