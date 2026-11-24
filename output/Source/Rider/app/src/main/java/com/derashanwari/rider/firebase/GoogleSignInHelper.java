package com.derashanwari.rider.firebase;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.derashanwari.rider.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Google Sign-In helper implemented exclusively with Jetpack CredentialManager + FirebaseAuth.
 * Keeps GetSignInWithGoogleOption and GetGoogleIdOption in separate GetCredentialRequests
 * so the API 36 credential sheet remains fast and responsive.
 */
public final class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    public interface GoogleAuthCallback {
        void onSuccess(FirebaseUser user);
        void onCancelled(String message);
        void onError(String message);
    }

    private GoogleSignInHelper() {}

    public static void signInInteractive(@NonNull Activity activity, @NonNull GoogleAuthCallback callback) {
        FirebaseConfig.ensureInitialized(activity);
        String webClientId = activity.getString(R.string.default_web_client_id);

        GetSignInWithGoogleOption signInOption = new GetSignInWithGoogleOption.Builder(webClientId)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleCredentialResponse(activity, response, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            Log.w(TAG, "Interactive Google Sign-In cancelled or dismissed by CredentialManager", e);
                            callback.onCancelled("Sign-in cancelled. Please select a Google account to continue.");
                        } else {
                            Log.e(TAG, "Interactive Google Sign-In failed: " + e.getMessage(), e);
                            callback.onError("Google Sign-In failed: " + e.getMessage());
                        }
                    }
                }
        );
    }

    public static void trySilentAutoSignIn(@NonNull Activity activity, @NonNull GoogleAuthCallback callback) {
        FirebaseConfig.ensureInitialized(activity);
        String webClientId = activity.getString(R.string.default_web_client_id);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleCredentialResponse(activity, response, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.i(TAG, "No authorized silent Google credential available: " + e.getMessage());
                        callback.onCancelled("");
                    }
                }
        );
    }

    private static void handleCredentialResponse(Context context, GetCredentialResponse response, GoogleAuthCallback callback) {
        Credential credential = response.getCredential();
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleCred = GoogleIdTokenCredential.createFrom(credential.getData());
                AuthCredential firebaseCred = GoogleAuthProvider.getCredential(googleCred.getIdToken(), null);
                FirebaseConfig.getAuth(context).signInWithCredential(firebaseCred)
                        .addOnSuccessListener(authResult -> {
                            FirebaseUser user = authResult.getUser();
                            if (user != null) {
                                callback.onSuccess(user);
                            } else {
                                callback.onError("Firebase user missing after Google Sign-In.");
                            }
                        })
                        .addOnFailureListener(err -> {
                            Log.e(TAG, "Firebase signInWithCredential failed", err);
                            callback.onError("Authentication failed: " + err.getMessage());
                        });
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse Google ID token", e);
                callback.onError("Invalid Google ID token received.");
            }
        } else {
            callback.onError("Unexpected credential type received.");
        }
    }

    public static void signOut(@NonNull Context context) {
        try {
            FirebaseConfig.getAuth(context).signOut();
            CredentialManager cm = CredentialManager.create(context);
            cm.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    new CancellationSignal(),
                    ContextCompat.getMainExecutor(context),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(Void unused) {}
                        @Override public void onError(@NonNull ClearCredentialException e) {
                            Log.w(TAG, "clearCredentialState failed", e);
                        }
                    }
            );
        } catch (Exception e) {
            Log.w(TAG, "Sign-out cleanup note", e);
        }
    }
}
