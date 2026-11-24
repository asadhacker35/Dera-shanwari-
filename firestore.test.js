const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('/usr/local/lib/node_modules/@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
  serverTimestamp,
  Timestamp,
} = require('/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/firestore');

const PROJECT_ID = 'gen-lang-client-0444103939';

let testEnv;

function getDb(authCtx) {
  return authCtx.firestore();
}

test.before(async () => {
  const host = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8085';
  const [h, p] = host.split(':');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: h,
      port: Number(p),
      rules: fs.readFileSync(path.join(__dirname, 'firestore.rules'), 'utf8'),
    },
  });
});

test.after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

test('Unauthenticated users are denied on every collection and group chat', async () => {
  const unauth = getDb(testEnv.unauthenticatedContext());
  await assertFails(getDocs(collection(unauth, 'products')));
  await assertFails(getDocs(collection(unauth, 'orders')));
  await assertFails(getDoc(doc(unauth, 'customers/cust1')));
  await assertFails(getDoc(doc(unauth, 'notifications/notif1')));
  await assertFails(getDocs(collection(unauth, 'chatRooms/mainGroup/messages')));
  await assertFails(getDocs(collection(unauth, 'chatRooms/mainGroup/members')));
});

test('Bootstrapped Admin can manage variant products, categories, and settings while customers cannot', async () => {
  const adminCtx = testEnv.authenticatedContext('admin_uid_1', {
    email: 'asadhacker770@gmail.com',
    email_verified: true,
  });
  const custCtx = testEnv.authenticatedContext('cust_uid_1', {
    email: 'usman@example.com',
    email_verified: true,
  });

  const adminDb = getDb(adminCtx);
  const custDb = getDb(custCtx);

  await assertSucceeds(setDoc(doc(adminDb, 'categories/cat_pizza'), {
    id: 'cat_pizza',
    name: 'Pizza',
    imageUrl: 'https://ik.imagekit.io/s1igzgwfr/pizza.jpg',
    imageFileId: 'ik_cat_pizza_1',
    localDrawableName: 'img_food_burger_wrap',
    available: true,
    sortOrder: 1,
    createdAt: serverTimestamp(),
  }));

  await assertSucceeds(setDoc(doc(adminDb, 'products/prod_pizza_1'), {
    id: 'prod_pizza_1',
    name: 'Chicken Tikka Pizza',
    description: 'Smoky chicken tikka chunks on hand-tossed dough',
    price: 450,
    discountPrice: 0,
    imageUrl: 'https://ik.imagekit.io/s1igzgwfr/tikka_pizza.jpg',
    imageFileId: 'ik_prod_pizza_1',
    localDrawableName: 'img_food_burger_wrap',
    categoryId: 'cat_pizza',
    categoryName: 'Pizza',
    available: true,
    featured: true,
    popular: true,
    variants: [
      { id: 'small', name: 'Small', price: 450, discountPrice: 0, available: true, imageUrl: '', description: '7 inch' },
      { id: 'medium', name: 'Medium', price: 750, discountPrice: 0, available: true, imageUrl: '', description: '10 inch' },
      { id: 'large', name: 'Large', price: 1050, discountPrice: 0, available: true, imageUrl: '', description: '14 inch' },
    ],
    createdAt: serverTimestamp(),
  }));

  await assertSucceeds(setDoc(doc(adminDb, 'settings/restaurant'), {
    restaurantName: 'Dera Shanwari',
    logoUrl: 'https://ik.imagekit.io/s1igzgwfr/logo.png',
    phone: '03001112233',
    whatsappNumber: '923001112233',
    address: 'University Road, Peshawar',
    openingHours: '12:00 PM',
    closingHours: '12:30 AM',
    deliveryCharges: 150,
    minimumOrderAmount: 500,
    loyaltyPointsPercent: 10,
    imagekitPublicKey: 'public_key_example',
    imagekitAuthEndpoint: 'https://auth.example.com',
  }));

  await assertSucceeds(getDocs(collection(custDb, 'products')));
  await assertSucceeds(getDoc(doc(custDb, 'settings/restaurant')));

  await assertFails(deleteDoc(doc(custDb, 'products/prod_pizza_1')));
  await assertFails(setDoc(doc(custDb, 'admins/cust_uid_1'), {
    id: 'cust_uid_1',
    name: 'Evil',
    email: 'usman@example.com',
    phone: '03000000000',
    role: 'Owner',
    active: true,
    createdAt: serverTimestamp(),
  }));
});

test('Order lifecycle preserves immutable timestamps and enforces role-scoped timeline events', async () => {
  const adminDb = getDb(testEnv.authenticatedContext('admin_1', {
    email: 'asadhacker770@gmail.com',
    email_verified: true,
  }));
  const custDb = getDb(testEnv.authenticatedContext('cust_1'));
  const riderDb = getDb(testEnv.authenticatedContext('rider_1'));
  const otherCustDb = getDb(testEnv.authenticatedContext('cust_99'));

  const placedTime = Timestamp.fromMillis(Date.now() - 60000);
  const orderDoc = {
    orderId: 'ORD-1700000099',
    customerId: 'cust_1',
    customerName: 'Usman Tariq',
    customerPhone: '03009876543',
    deliveryAddress: 'Hayatabad Phase 2',
    notes: 'Extra mayo',
    items: [{
      productId: 'prod_pizza_1',
      productName: 'Chicken Tikka Pizza',
      variantId: 'large',
      variantName: 'Large',
      unitPrice: 1050,
      quantity: 2,
    }],
    subtotal: 2100,
    deliveryFee: 150,
    discountAmount: 0,
    totalAmount: 2250,
    paymentMethod: 'Cash on Delivery',
    paymentStatus: 'Pending',
    orderStatus: 'Pending',
    riderId: '',
    riderName: '',
    pointsEarned: 225,
    timestamps: {
      orderPlacedAt: placedTime,
    },
    printCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  await assertSucceeds(setDoc(doc(custDb, 'orders/ORD-1700000099'), orderDoc));
  await assertSucceeds(setDoc(doc(custDb, 'orders/ORD-1700000099/timeline/ORDER_PLACED'), {
    id: 'ORDER_PLACED',
    orderId: 'ORD-1700000099',
    eventType: 'ORDER_PLACED',
    timestamp: serverTimestamp(),
    userId: 'cust_1',
    userRole: 'CUSTOMER',
    userName: 'Usman Tariq',
    notes: 'Order placed by customer',
  }));

  // Customer CANNOT forge an ADMIN timeline event
  await assertFails(setDoc(doc(custDb, 'orders/ORD-1700000099/timeline/ORDER_CONFIRMED'), {
    id: 'ORDER_CONFIRMED',
    orderId: 'ORD-1700000099',
    eventType: 'ORDER_CONFIRMED',
    timestamp: serverTimestamp(),
    userId: 'cust_1',
    userRole: 'ADMIN',
    userName: 'Fake Admin',
  }));

  // Admin prints receipt before rider assignment & confirms order
  await assertSucceeds(updateDoc(doc(adminDb, 'orders/ORD-1700000099'), {
    printCount: 1,
    lastPrintedAt: serverTimestamp(),
    lastPrintedBy: 'admin_1',
    orderStatus: 'Confirmed',
    timestamps: {
      orderPlacedAt: placedTime,
      orderConfirmedAt: serverTimestamp(),
    },
    updatedAt: serverTimestamp(),
  }));

  // Trying to overwrite orderPlacedAt fails
  await assertFails(updateDoc(doc(adminDb, 'orders/ORD-1700000099'), {
    timestamps: {
      orderPlacedAt: Timestamp.fromMillis(Date.now() - 5000),
    },
    updatedAt: serverTimestamp(),
  }));

  // Assign Rider
  const curSnap = await getDoc(doc(adminDb, 'orders/ORD-1700000099'));
  const curTs = curSnap.data().timestamps;
  await assertSucceeds(updateDoc(doc(adminDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Assigned',
    riderId: 'rider_1',
    riderName: 'Bilal Khan',
    timestamps: {
      ...curTs,
      riderAssignedAt: serverTimestamp(),
    },
    updatedAt: serverTimestamp(),
  }));

  // Assigned Rider accepts order and records RIDER_ACCEPTED event
  const riderSnap = await getDoc(doc(riderDb, 'orders/ORD-1700000099'));
  await assertSucceeds(updateDoc(doc(riderDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Rider Accepted',
    timestamps: {
      ...riderSnap.data().timestamps,
      riderAcceptedAt: serverTimestamp(),
    },
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(setDoc(doc(riderDb, 'orders/ORD-1700000099/timeline/RIDER_ACCEPTED'), {
    id: 'RIDER_ACCEPTED',
    orderId: 'ORD-1700000099',
    eventType: 'RIDER_ACCEPTED',
    timestamp: serverTimestamp(),
    userId: 'rider_1',
    userRole: 'RIDER',
    userName: 'Bilal Khan',
  }));

  // Rider sends to kitchen, Admin prepares & marks Ready, Rider delivers
  let latestTs = (await getDoc(doc(riderDb, 'orders/ORD-1700000099'))).data().timestamps;
  await assertSucceeds(updateDoc(doc(riderDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Sent to Kitchen',
    timestamps: { ...latestTs, sentToKitchenAt: serverTimestamp() },
    updatedAt: serverTimestamp(),
  }));

  latestTs = (await getDoc(doc(adminDb, 'orders/ORD-1700000099'))).data().timestamps;
  await assertSucceeds(updateDoc(doc(adminDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Preparing',
    timestamps: { ...latestTs, preparationStartedAt: serverTimestamp() },
    updatedAt: serverTimestamp(),
  }));

  latestTs = (await getDoc(doc(adminDb, 'orders/ORD-1700000099'))).data().timestamps;
  await assertSucceeds(updateDoc(doc(adminDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Ready',
    timestamps: { ...latestTs, preparationCompletedAt: serverTimestamp() },
    updatedAt: serverTimestamp(),
  }));

  latestTs = (await getDoc(doc(riderDb, 'orders/ORD-1700000099'))).data().timestamps;
  await assertSucceeds(updateDoc(doc(riderDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Out for Delivery',
    timestamps: { ...latestTs, outForDeliveryAt: serverTimestamp() },
    updatedAt: serverTimestamp(),
  }));

  latestTs = (await getDoc(doc(riderDb, 'orders/ORD-1700000099'))).data().timestamps;
  await assertSucceeds(updateDoc(doc(riderDb, 'orders/ORD-1700000099'), {
    orderStatus: 'Delivered',
    paymentStatus: 'Paid',
    timestamps: { ...latestTs, deliveredAt: serverTimestamp() },
    updatedAt: serverTimestamp(),
  }));

  // Timeline event update is immutable
  await assertFails(updateDoc(doc(riderDb, 'orders/ORD-1700000099/timeline/RIDER_ACCEPTED'), {
    userName: 'Tampered',
  }));

  // Outsider customer cannot read timeline
  await assertFails(getDocs(collection(otherCustDb, 'orders/ORD-1700000099/timeline')));
});

test('Universal Official Group Chat enforces privacy, authentic roles, and Admin moderation', async () => {
  const adminDb = getDb(testEnv.authenticatedContext('admin_1', {
    email: 'asadhacker770@gmail.com',
    email_verified: true,
  }));
  const custDb = getDb(testEnv.authenticatedContext('cust_1'));
  const riderDb = getDb(testEnv.authenticatedContext('rider_1'));

  // Privacy-safe member presence succeeds
  await assertSucceeds(setDoc(doc(custDb, 'chatRooms/mainGroup/members/cust_1'), {
    uid: 'cust_1',
    name: 'Usman Tariq',
    role: 'CUSTOMER',
    profileImageUrl: '',
    online: true,
    lastSeenAt: serverTimestamp(),
    lastReadAt: serverTimestamp(),
  }));

  // Storing private PII (phone/address) on group member fails
  await assertFails(setDoc(doc(custDb, 'chatRooms/mainGroup/members/cust_1'), {
    uid: 'cust_1',
    name: 'Usman Tariq',
    role: 'CUSTOMER',
    profileImageUrl: '',
    phone: '03009876543',
    address: 'Hayatabad',
    online: true,
    lastSeenAt: serverTimestamp(),
    lastReadAt: serverTimestamp(),
  }));

  // Customer cannot spoof ADMIN role in group chat
  await assertFails(setDoc(doc(custDb, 'chatRooms/mainGroup/messages/msg_bad_role'), {
    id: 'msg_bad_role',
    senderId: 'cust_1',
    senderName: 'Usman',
    senderRole: 'ADMIN',
    senderAvatarUrl: '',
    message: 'I am admin',
    imageUrl: '',
    createdAt: serverTimestamp(),
  }));

  // Customer sends text, Rider reads & sends image message, Admin moderates
  await assertSucceeds(setDoc(doc(custDb, 'chatRooms/mainGroup/messages/msg_c1'), {
    id: 'msg_c1',
    senderId: 'cust_1',
    senderName: 'Usman Tariq',
    senderRole: 'CUSTOMER',
    senderAvatarUrl: '',
    message: 'Assalam-o-Alaikum everyone!',
    imageUrl: '',
    createdAt: serverTimestamp(),
  }));

  await assertSucceeds(setDoc(doc(riderDb, 'chatRooms/mainGroup/messages/msg_r1'), {
    id: 'msg_r1',
    senderId: 'rider_1',
    senderName: 'Bilal Khan',
    senderRole: 'RIDER',
    senderAvatarUrl: '',
    message: '',
    imageUrl: 'https://ik.imagekit.io/s1igzgwfr/chat/photo.jpg',
    createdAt: serverTimestamp(),
  }));

  const readByAdmin = await assertSucceeds(getDocs(collection(adminDb, 'chatRooms/mainGroup/messages')));
  assert.equal(readByAdmin.size, 2);

  // Customer cannot delete Rider's message, Admin can moderate/delete
  await assertFails(deleteDoc(doc(custDb, 'chatRooms/mainGroup/messages/msg_r1')));
  await assertSucceeds(deleteDoc(doc(adminDb, 'chatRooms/mainGroup/messages/msg_r1')));
});

test('Invalid payloads and tampered timestamps fail validation', async () => {
  const adminDb = getDb(testEnv.authenticatedContext('admin_1', {
    email: 'asadhacker770@gmail.com',
    email_verified: true,
  }));

  await assertFails(setDoc(doc(adminDb, 'products/bad_prod'), {
    id: 'bad_prod',
    name: 'Negative Dish',
    description: '',
    price: -50,
    discountPrice: 0,
    imageUrl: '',
    localDrawableName: '',
    categoryId: 'cat_1',
    categoryName: 'Karahi',
    available: true,
    featured: false,
    popular: false,
    createdAt: serverTimestamp(),
  }));

  await assertFails(setDoc(doc(adminDb, 'products/future_prod'), {
    id: 'future_prod',
    name: 'Future Dish',
    description: '',
    price: 500,
    discountPrice: 0,
    imageUrl: '',
    localDrawableName: '',
    categoryId: 'cat_1',
    categoryName: 'Karahi',
    available: true,
    featured: false,
    popular: false,
    createdAt: Timestamp.fromMillis(Date.now() + 3600000),
  }));
});
