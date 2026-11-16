const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_owner";
const ADMIN_EMAIL = "harishpatelh450@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

function validOrder(uid, overrides = {}) {
  return {
    orderNumber: "ARV-2026-1001",
    userId: uid,
    customerName: "Alice Sharma",
    phone: "9876543210",
    deliveryAddress: "14 Aravalli Enclave, Udaipur",
    itemSummary: "Royal Steel 3-Door Almirah x1",
    itemSku: "ARV-STL-01",
    quantity: 1,
    customDimensions: "78 x 42 x 22 in",
    subtotalInr: 22500,
    gstInr: 4050,
    totalInr: 26550,
    paymentMethod: "UPI",
    paymentStatus: "pending",
    trackingStage: "Order Confirmed",
    estimatedDays: 7,
    createdAt: nowTs(),
    updatedAt: nowTs(),
    ...overrides,
  };
}

function validMeasurement(uid, overrides = {}) {
  return {
    userId: uid,
    customerName: "Alice Sharma",
    phone: "9876543210",
    address: "14 Aravalli Enclave, Udaipur",
    furnitureType: "PVC Kitchen",
    roomArea: "Main Kitchen",
    widthFt: 10.5,
    heightFt: 8.0,
    depthFt: 2.0,
    preferredDate: "2026-11-20",
    preferredSlot: "10:00 AM - 12:00 PM",
    status: "scheduled",
    createdAt: nowTs(),
    updatedAt: nowTs(),
    ...overrides,
  };
}

function validInventoryItem(overrides = {}) {
  return {
    sku: "ARV-STL-01",
    name: "Royal 3-Door Steel Almirah",
    category: "Steel Almirah",
    material: "20-Gauge Powder Coated Steel",
    dimensions: "78 x 42 x 22 in",
    priceInr: 22500,
    stockQty: 12,
    reorderLevel: 3,
    imageKey: "img_steel_almirah",
    description: "Heavy duty powder coated steel wardrobe with locker and mirror.",
    isAvailable: true,
    createdAt: nowTs(),
    updatedAt: nowTs(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read orders, measurements, or inventory", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("measurements").get());
  await assertFails(unauthDb.collection("inventory").get());
});

test("Customer can create and query own order, but cannot query all orders or read Bob's order", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("orders").doc("bob_order").set(validOrder(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("orders").doc("alice_order").set(validOrder(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("orders").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("orders").get());
  await assertFails(aliceDb.collection("orders").doc("bob_order").get());
});

test("Shadow update & state shortcutting rejected on customer order", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("orders").doc("alice_order").set(validOrder(ALICE_UID)));

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("orders").doc("alice_order").update({
      paymentStatus: "paid_upi",
      isVerified: true,
      updatedAt: nowTs(),
    })
  );

  // Customer cannot skip trackingStage to Delivered
  await assertFails(
    aliceDb.collection("orders").doc("alice_order").update({
      trackingStage: "Delivered",
      updatedAt: nowTs(),
    })
  );

  // Customer CAN update paymentStatus from pending to paid_upi
  await assertSucceeds(
    aliceDb.collection("orders").doc("alice_order").update({
      paymentMethod: "UPI",
      paymentStatus: "paid_upi",
      paymentRef: "UTR123456789",
      updatedAt: nowTs(),
    })
  );
});

test("Email spoofing with unverified email cannot gain admin privileges", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("inventory").doc("item_1").set(validInventoryItem()));
});

test("Verified shop owner admin can create inventory and advance order tracking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("orders").doc("ord_1").set(validOrder(ALICE_UID)));

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("inventory").doc("item_1").set(validInventoryItem()));
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1").update({
      trackingStage: "In Fabrication",
      updatedAt: nowTs(),
    })
  );
});

test("Customer cannot inflate inventory stock or change price, only decrement on checkout", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("inventory").doc("item_1").set(validInventoryItem());
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("inventory").doc("item_1").update({
      stockQty: 99,
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    aliceDb.collection("inventory").doc("item_1").update({
      priceInr: 1,
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("inventory").doc("item_1").update({
      stockQty: 11,
      isAvailable: true,
      updatedAt: nowTs(),
    })
  );
});

test("Measurement isolation and self-role escalation prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("measurements").doc("meas_1").set(validMeasurement(ALICE_UID))
  );
  await assertFails(bobDb.collection("measurements").doc("meas_1").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      role: "admin",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});
