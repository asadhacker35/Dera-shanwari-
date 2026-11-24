# Firebase Cloud Firestore & ImageKit Integration Guide

All three **Dera Shanwari** Android applications (**Admin**, **Customer**, and **Rider**) are pre-wired to a shared live **Firebase Cloud Firestore** project and **ImageKit.io** CDN without requiring custom middleware servers.

---

## Part A: Firebase Project & Authentication Configuration

### 1. Connected Firebase Project
- **Firebase Project ID:** `gen-lang-client-0444103939` (`project_number: 617932624019`)
- **Named Firestore Database:** `ai-studio-android-derashan-f3d4b6f1-665c-405a-94ee-cf62c31e8672` (declared in `res/values/firebase_applet_config.xml`)
- **Registered Android Apps in `app/google-services.json`:**
  - `com.derashanwari.admin` — Admin Management App (`output/Source/Admin/app/google-services.json`)
  - `com.derashanwari.user` — Customer App (`output/Source/Customer/app/google-services.json`)
  - `com.derashanwari.rider` — Rider App (`output/Source/Rider/app/google-services.json`)
  - `com.aistudio.derashanwari.kxmpqz` — AI Studio streaming preview mirror (`app/google-services.json`)
- **Signing SHA-1 Registered:** `2d74376c25e23fcd10bb2f1dbd282e9f0b40d045` (matching shared `debug.keystore`)

### 2. Google Sign-In via Jetpack CredentialManager
All three apps authenticate with real Firebase Auth accounts (`FirebaseConfig.DEMO_MODE = false`) using `GoogleSignInHelper`:
- Uses `androidx.credentials.CredentialManager` with `GetSignInWithGoogleOption` (interactive button) and `GetGoogleIdOption` (silent authorized re-entry).
- Exchanges the Google ID Token (`R.string.default_web_client_id`, client ID `617932624019-mako7kg7c1dui8umk74b696p9t24mvgt.apps.googleusercontent.com`) via `GoogleAuthProvider.getCredential(...)` into `FirebaseAuth`.
- **Admin Authorization:** `firestore.rules` authorizes the bootstrapped owner email (`asadhacker770@gmail.com`) and any UID registered in the `admins` collection.
- **Rider Linking:** Admin creates/approves rider entries in `riders`; upon Google Sign-In in the Rider app, `CloudAuthRepository` links the rider profile by UID/email (`linkedUid`).
- **Customer Onboarding:** Any signed-in Google account creates/syncs its own `customers/{uid}` document.

### 3. Real-Time Cross-App Flows
- **Admin -> Customer:** Publishing or editing a `Product`, `Category`, `Banner`, `PaymentMethodConfig`, or `RestaurantSettings` document in Admin updates `FirestoreSyncManager` snapshot listeners in Customer in real time.
- **Customer -> Admin:** Placing an order creates an `orders/{orderId}` (`Pending`) document, a `payments/{paymentId}` record, and an `ADMIN` notification document, triggering live dashboard updates and `RealTimeNotificationHelper` alerts in the Admin app.
- **Admin -> Rider -> Customer:** Assigning a rider updates `riderId` and `orderStatus`; Rider delivery actions (`Out for Delivery`, `Delivered`, `Paid`) sync immediately back to Customer tracking and Admin analytics.

### 4. Rebuilding / Customizing in Android Studio
1. Open any standalone directory (`output/Source/Admin`, `output/Source/Customer`, or `output/Source/Rider`) in Android Studio (or Android mobile IDE supporting AGP `9.1.1` / Gradle `9.3.1` / Java 11).
2. If you later switch to your own release keystore or Firebase project:
   - Register your SHA-1/SHA-256 certificates in Firebase Console for `com.derashanwari.admin`, `com.derashanwari.user`, and `com.derashanwari.rider`.
   - Replace `app/google-services.json` and update `res/values/firebase_applet_config.xml` (`firestore_database_id`).
   - Deploy `firestore.rules` to your Firestore database.

---

## Part B: ImageKit.io CDN Configuration

Dera Shanwari stores public HTTPS image URLs directly in Firestore (`Product`, `Category`, `Banner`, `Rider`, and `RestaurantSettings`) rather than embedding heavy media inside APKs.

### Security Rule
> **NEVER** ship your ImageKit **Private API Key** inside any Android project or APK. Keep only your public URL endpoint in `config/ImageKitConfig.java`.

### Setup & Responsive URL Transforms
- **Configured Endpoint:** `ImageKitConfig.BASE_CDN_ENDPOINT = "https://ik.imagekit.io/s1igzgwfr/"`
- **Organized Folders:** `products/`, `categories/`, `banners/`, `brand/`
- **Automatic Optimization:** `ImageKitConfig.buildTransformedUrl(rawUrl, widthPx, heightPx)` automatically appends `?tr=w-<w>,h-<h>,fo-auto,q-80` for `ik.imagekit.io` assets, and `ImageLoaderUtil` renders images with Glide plus crisp fallbacks when offline.
- **Admin Catalog Selection:** When creating or editing dishes/banners in the Admin app, admins can paste any `https://ik.imagekit.io/s1igzgwfr/...` asset URL or pick one of the curated online dish photography presets built into `ImageKitConfig.getPresetOnlineImages()`.
