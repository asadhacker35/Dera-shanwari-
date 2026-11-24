# Dera Shanwari — Shared Architecture & Cloud Firestore Schema

All three native **Java + XML** Android applications (**Admin**, **Customer**, and **Rider**) share an identical domain model, repository interface layer, white-gradient glassmorphism design system, and **Firebase Cloud Firestore** real-time schema contract.

---

## 1. Repository & Sync Architecture

UI components (Activities, Fragments, Adapters, Dialogs) interact strictly with repository interfaces resolved through `RepositoryProvider`:

| Interface | Production Implementation | Test Implementation | Responsibilities |
|---|---|---|---|
| `AuthRepository` | `CloudAuthRepository` | `DemoAuthRepository` | Google Sign-In via Jetpack `CredentialManager` + `FirebaseAuth`, admin role verification, customer/rider profile linking, sign-out |
| `ProductRepository` | `CloudProductRepository` | `DemoProductRepository` | Real-time menu catalog streams and CRUD for Products, Categories, and Promotional Banners |
| `OrderRepository` | `CloudOrderRepository` | `DemoOrderRepository` | Order creation, live status transitions, rider assignment, COD/wallet payment records & verification |
| `RiderRepository` | `CloudRiderRepository` | `DemoRiderRepository` | Rider directory, create/update/toggle availability, delivery counts & earnings tracking |
| `CustomerRepository` | `CloudCustomerRepository` | `DemoCustomerRepository` | Customer directory, delivery profile edits, loyalty points ledger & reward redemptions |
| `SettingsRepository` | `CloudSettingsRepository` | `DemoSettingsRepository` | Restaurant profile, hours, delivery charges, minimum order, payment method configs (`COD`, `JazzCash`, `EasyPaisa`) |
| `ChatRepository` | `CloudChatRepository` | `DemoChatRepository` | Real-time per-order chat stream between Customer, Admin, and assigned Rider |

### Live Cloud Firestore Sync (`FirestoreSyncManager`)
- Connects to Firebase project **`gen-lang-client-0444103939`** and named Cloud Firestore Enterprise database **`ai-studio-android-derashan-f3d4b6f1-665c-405a-94ee-cf62c31e8672`** (configured via `res/values/firebase_applet_config.xml` and `FirebaseConfig.getFirestore(context)`).
- Maintains real-time `addSnapshotListener` subscriptions across shared collections so menu changes made in **Admin** immediately appear in **Customer**, new customer orders trigger immediate real-time Admin alerts & `RealTimeNotificationHelper` notifications, and order assignments/status updates stream live to **Rider** and **Customer**.
- `RepositoryProvider.DEMO_MODE` is `false` in `FirebaseConfig.java`; local `Demo*` repositories are reserved strictly for fast offline JVM Robolectric unit tests (`RepositoryProvider.enableTestModeForRobolectric()`).

---

## 2. White-Gradient Glassmorphism UI System

All three apps use a unified frosted light-glass design language built strictly with Android XML shapes, gradients, and `ViewBinding`:
- **`bg_glass_canvas.xml`**: 135° luminous pearl-ice to warm-ivory backdrop (`#FFF3F7FE -> #FFFFFAF4 -> #FFFFE9D6`).
- **`bg_glass_card.xml`**: 135° frosted white-to-ice translucent surface (`#FDFFFFFF -> #EEFFFFFF -> #DEF1F6FF`) with `20dp` rounded corners and a `1.5dp #FFFFFFFF` specular highlight border.
- **`bg_glass_card_orange.xml`**: 135° frosted white-to-peach accent card (`#FDFFFFFF -> #F0FFF6EE -> #E2FFE6D0`) with a soft warm border.
- **`bg_glass_header.xml` & `bg_glass_bottom_nav.xml`**: Frosted white-gradient top headers and bottom navigation capsules with crisp specular white rims.
- **`bg_glass_pill.xml`, `bg_pill_orange.xml`, `bg_glass_button_white.xml`, `bg_badge_discount.xml`**: Frosted capsules, saffron CTA gradient pills, white-glass secondary buttons, and warm gold discount badges.

---

## 3. Shared Data Models (`model/`)

- `OrderStatus`: `Pending`, `Confirmed`, `Preparing`, `Ready`, `Assigned`, `Assigned to Rider`, `Picked Up`, `Out for Delivery`, `Delivered`, `Cancelled`
- `Product`: `id`, `name`, `description`, `price`, `discountPrice`, `imageUrl`, `localDrawableName`, `categoryId`, `categoryName`, `available`, `featured`, `popular`, `createdAt`
- `Category`: `id`, `name`, `imageUrl`, `localDrawableName`, `available`, `sortOrder`
- `Banner`: `id`, `title`, `subtitle`, `imageUrl`, `localDrawableName`, `active`, `sortOrder`
- `Customer`: `id`, `name`, `email`, `phone`, `address`, `active`, `loyaltyPoints`, `createdAt`
- `AdminUser`: `id`, `name`, `email`, `phone`, `role`, `active`
- `Rider`: `id`, `linkedUid`, `name`, `email`, `phone`, `vehicleInfo`, `profileImageUrl`, `active`, `totalDeliveries`, `totalEarnings`
- `OrderItem`: `productId`, `productName`, `unitPrice`, `quantity`, `notes`, `imageUrl`
- `Order`: `orderId`, `customerId`, `customerName`, `customerPhone`, `deliveryAddress`, `notes`, `items`, `subtotal`, `deliveryFee`, `totalAmount`, `paymentMethod`, `paymentStatus`, `orderStatus`, `riderId`, `riderName`, `pointsEarned`, `createdAt`, `updatedAt`
- `Payment`: `id`, `orderId`, `customerId`, `method`, `accountTitle`, `accountNumber`, `amount`, `status`, `createdAt`
- `PaymentMethodConfig`: `methodCode`, `displayName`, `accountTitle`, `accountNumber`, `enabled`, `instructions`
- `RestaurantSettings`: `restaurantName`, `logoUrl`, `phone`, `whatsappNumber`, `address`, `openingHours`, `closingHours`, `deliveryCharges`, `minimumOrderAmount`, `loyaltyPointsPercent`
- `Reward`: `id`, `customerId`, `title`, `description`, `points`, `type`, `redeemable`, `requiredPoints`, `orderId`, `createdAt`
- `ChatMessage`: `id`, `orderId`, `senderId`, `customerId`, `riderId`, `senderRole`, `senderName`, `message`, `createdAt`

---

## 4. Cloud Firestore Collections & Security Rules

Collection names are centrally defined in `firebase/FirebaseConfig.java`:

| Collection | Primary Key | Access Model (`firestore.rules`) |
|---|---|---|
| `admins` | `<adminUid>` | Signed-in owner or admin (`asadhacker770@gmail.com` or `admins/{uid}` member) |
| `customers` | `<customerUid>` | Owner read/profile update (`loyaltyPoints`, contact fields) & Admin full management |
| `riders` | `<riderId>` | Admin full management; linked rider (`linkedUid == auth.uid`) read & profile/stats updates |
| `categories` | `<categoryId>` | Authenticated read across all 3 apps; Admin create/update/delete |
| `products` | `<productId>` | Authenticated read across all 3 apps; Admin create/update/delete |
| `banners` | `<bannerId>` | Authenticated read across all 3 apps; Admin create/update/delete |
| `orders` | `<orderId>` | Customer (`customerId == auth.uid`) creates `Pending` orders and cancels `Pending`; Assigned Rider (`riderId == auth.uid`) updates `orderStatus`/`paymentStatus`; Admin manages full lifecycle |
| `payments` | `<paymentId>` | Customer creates/reads own payment records; Admin verifies & manages |
| `settings` | `restaurant` | Authenticated read; Admin write |
| `payment_methods` | `<methodCode>` | Authenticated read; Admin write |
| `chats` | `<messageId>` | Immutable messages created by signed-in participants (`senderId == auth.uid`); read by Admin or order participants |
| `notifications` | `<notificationId>` | Targeted alerts (`ADMIN`, `CUSTOMER`, `RIDER`) triggering local high-priority alerts via `RealTimeNotificationHelper` |
| `rewards` | `<rewardId>` | Shared `REDEEMABLE_OFFER` items + per-customer `EARNED` / `REDEEMED` history |
