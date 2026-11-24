# Dera Shanwari — Native Android Suite (Java + XML)

Three independent, Android Studio-ready native **Java + XML** applications for **Dera Shanwari**, connected in real time through **Firebase Cloud Firestore**, **Jetpack CredentialManager Google Sign-In**, and **ImageKit.io CDN**, featuring a unified **White-Gradient Glassmorphism UI**.

---

## 1. Applications & Package Names

| App | Package / Application ID | Entry Launcher Activity | APK Output |
|---|---|---|---|
| **Admin Management App** | `com.derashanwari.admin` | `.ui.AdminSplashActivity` | `output/APK/Admin.apk` |
| **Customer App** | `com.derashanwari.user` | `.ui.SplashActivity` | `output/APK/Customer.apk` |
| **Rider App** | `com.derashanwari.rider` | `.ui.RiderSplashActivity` | `output/APK/Rider.apk` |

### Technology & Design Stack
- **Language:** 100% Java (zero Kotlin application code)
- **UI Framework:** Android XML Layouts (`ViewBinding`), Material Design Components (`1.12.0`), RecyclerView, ConstraintLayout, SwipeRefreshLayout
- **UI Aesthetic:** White-Gradient Glassmorphism (`bg_glass_canvas`, `bg_glass_card`, `bg_glass_card_orange`, `bg_glass_header`, `bg_glass_bottom_nav`, `bg_glass_pill`, `bg_glass_button_white`)
- **SDK Levels:** `minSdk = 24`, `compileSdk = 36`, `targetSdk = 36`
- **Build System:** AGP `9.1.1`, Gradle `9.3.1` Kotlin DSL (`build.gradle.kts`) with Version Catalog (`gradle/libs.versions.toml`), Java 11 source/target compatibility
- **Backend & Media:** Firebase Cloud Firestore (`ai-studio-android-derashan-f3d4b6f1-665c-405a-94ee-cf62c31e8672`), Firebase Auth + Jetpack CredentialManager (`GetSignInWithGoogleOption`), ImageKit CDN (`https://ik.imagekit.io/s1igzgwfr/`), Glide `4.16.0`, Gson `2.11.0`
- **Testing:** JUnit 4 + AndroidX Test + Robolectric `4.16.1`

---

## 2. Output Directory Structure

```text
output/
├── APK/
│   ├── Admin.apk
│   ├── Customer.apk
│   └── Rider.apk
├── Documentation/
│   ├── README.md
│   ├── ARCHITECTURE_AND_SCHEMA.md
│   └── FIREBASE_AND_IMAGEKIT_GUIDE.md
├── Resources/
│   ├── icons/
│   ├── images/
│   └── vectors/
├── Source/
│   ├── Admin/
│   ├── Customer/
│   └── Rider/
└── DeraShanwari_Projects.zip
```

Each folder under `output/Source/` (`Admin/`, `Customer/`, `Rider/`) is a standalone, self-contained Android Studio project with its own `.gitignore`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, `build.gradle.kts`, and `app/google-services.json`.

---

## 3. Real Firebase & Google Authentication

All three apps run live (`FirebaseConfig.DEMO_MODE = false`) against project **`gen-lang-client-0444103939`**:
- **Admin App (`com.derashanwari.admin`):** Google Sign-In verified against `admins` collection and bootstrapped owner email `asadhacker770@gmail.com`.
- **Customer App (`com.derashanwari.user`):** Google Sign-In (`customers/{uid}`) with saved phone/address checkout, live order tracking, loyalty points, and WhatsApp order support.
- **Rider App (`com.derashanwari.rider`):** Google Sign-In (`riders/{uid}` / `linkedUid`) for viewing assigned deliveries, updating delivery & payment statuses in real time, and tracking earnings.

---

## 4. Feature Summary

### Admin Management App
- **Dashboard:** Live Firestore metrics (pending, active, delivered, cancelled orders, revenue, today's sales, customer & rider counts) plus real-time incoming order alerts.
- **Orders:** Filterable order queue, status lifecycle transitions (`Pending -> Confirmed -> Preparing -> Ready -> Assigned -> Picked Up -> Out for Delivery -> Delivered` / `Cancelled`), rider assignment, payment verification, and in-order chat.
- **Catalog:** Live CRUD for Products (name, description, price, discount price, ImageKit URL or presets, category, availability, featured toggle), Categories, and Promotional Banners.
- **People:** Rider & Customer management (availability/suspension toggles, delivery & earnings history).
- **More / Operations:** Payment methods (`COD`, `JazzCash`, `EasyPaisa`), payment ledger, sales reports, and restaurant profile/hours/delivery fee settings.

### Customer App
- **Home & Menu:** Branded glass header, promotional banner carousel, search bar, category quick chips, featured & popular dishes with discount badges, quick add-to-cart, and item detail sheet.
- **Cart & Checkout:** Persistent cart items, quantity stepper, reward points redemption, delivery fee calculation, `COD` / `JazzCash` / `EasyPaisa` instructions, WhatsApp receipt support, and instant Cloud Firestore order placement + Admin notification trigger.
- **Orders & Tracking:** Real-time status updates, assigned rider info, one-tap Call / WhatsApp, and in-order chat.
- **Rewards & Account:** Loyalty points balance, redeemable offers, earned history, delivery profile editing, and Google sign-out.

### Rider App
- **Dashboard & Queue:** Assigned deliveries summary, live incoming dispatch alert card, earnings snapshot, and completed history.
- **Delivery Detail:** Customer contact actions (Dial / WhatsApp), itemized order breakdown, `Out for Delivery` / `Delivered` transitions, COD collection confirmation, and order chat.
- **Profile:** Rider vehicle/profile info, stats, and Google sign-out.

---

## 5. Opening in Android Studio, Building & Installing APKs

### Open in Android Studio (or Mobile Android IDE)
Extract `output/DeraShanwari_Projects.zip` and open any of the three independent project roots:
- `Admin/`
- `Customer/`
- `Rider/`

### Build & Run Unit Tests
Inside any standalone project root:
```bash
./gradlew assembleDebug testDebugUnitTest
# Or when using system Gradle 9.3.1 directly:
gradle assembleDebug testDebugUnitTest
```
Verified passing (`2` Robolectric unit tests passed with `0` failures and `0` errors in each project: `AdminAppRobolectricTest`, `CustomerAppRobolectricTest`, `RiderAppRobolectricTest`).

### Install Pre-built APKs via ADB
```bash
adb install -r output/APK/Admin.apk
adb install -r output/APK/Customer.apk
adb install -r output/APK/Rider.apk
```
All three packages (`com.derashanwari.admin`, `com.derashanwari.user`, `com.derashanwari.rider`) install cleanly side-by-side on the same Android device (Android 7.0+ / API 24+).
