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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDealPayload(uid = ALICE_UID, id = "deal_001") {
  return {
    id,
    title: "Q4 Retail Distribution Agreement",
    clientName: "Acme Retail Chain",
    currencyCode: "USD",
    dealValue: 180000.0,
    interestRatePct: 14.5,
    tenureMonths: 24,
    frequency: "MONTHLY",
    periodicPayment: 8684.2,
    ltv: 240000.0,
    ltvCacRatio: 4.8,
    marginPct: 38.5,
    markupPct: 62.6,
    roiPct: 145.0,
    paybackMonths: 7.4,
    updatedAt: Date.now(),
    createdBy: uid,
    hasPlanFactData: true,
    planFactPlanPrice: 100.0,
    planFactPlanVolume: 1000,
    planFactPlanCost: 60.0,
    planFactPlanDays: 30,
    planFactActualPrice: 96.0,
    planFactActualVolume: 1050,
    planFactActualCost: 63.0,
    planFactActualDays: 48,
    planFactActualPenalties: 500.0,
    planFactCapitalRate: 18.0,
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user deals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("deals").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("deals")
      .doc("deal_001")
      .set(validDealPayload(ALICE_UID, "deal_001"))
  );
});

test("Authenticated owner can create, read, list, update, and delete a valid deal", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const dealRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("deals")
    .doc("deal_001");

  await assertSucceeds(dealRef.set(validDealPayload(ALICE_UID, "deal_001")));
  await assertSucceeds(dealRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("deals").get()
  );

  // Full overwrite update matching DealRepository.saveDeal
  await assertSucceeds(
    dealRef.set({
      ...validDealPayload(ALICE_UID, "deal_001"),
      title: "Updated Q4 Retail Agreement",
    })
  );

  await assertSucceeds(dealRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, write, or delete Alice deals", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("deals")
      .doc("deal_001")
      .set(validDealPayload(ALICE_UID, "deal_001"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDealRef = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("deals")
    .doc("deal_001");

  await assertFails(aliceDealRef.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("deals").get()
  );
  await assertFails(aliceDealRef.set(validDealPayload(BOB_UID, "deal_001")));
  await assertFails(aliceDealRef.delete());
});

test("Rejects identity spoofing, ghost fields, invalid types, oversized strings, and invalid doc IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const dealsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("deals");

  // Spoofed createdBy
  await assertFails(
    dealsCol.doc("deal_spoof").set(validDealPayload(BOB_UID, "deal_spoof"))
  );

  // Ghost field (shadow write)
  await assertFails(
    dealsCol.doc("deal_ghost").set({
      ...validDealPayload(ALICE_UID, "deal_ghost"),
      isAdmin: true,
    })
  );

  // Invalid numeric type / negative dealValue
  await assertFails(
    dealsCol.doc("deal_bad_num").set({
      ...validDealPayload(ALICE_UID, "deal_bad_num"),
      dealValue: -500,
    })
  );
  await assertFails(
    dealsCol.doc("deal_str_num").set({
      ...validDealPayload(ALICE_UID, "deal_str_num"),
      dealValue: "180000",
    })
  );

  // Oversized title (> 150 chars)
  await assertFails(
    dealsCol.doc("deal_big_title").set({
      ...validDealPayload(ALICE_UID, "deal_big_title"),
      title: "X".repeat(151),
    })
  );

  // Invalid frequency enum
  await assertFails(
    dealsCol.doc("deal_bad_freq").set({
      ...validDealPayload(ALICE_UID, "deal_bad_freq"),
      frequency: "DAILY",
    })
  );

  // Mismatched document ID vs payload id
  await assertFails(
    dealsCol.doc("deal_actual").set(validDealPayload(ALICE_UID, "deal_other"))
  );
});

test("Denies access to legacy/unlisted collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("shared_deals")
      .doc("deal_001")
      .set(validDealPayload(ALICE_UID, "deal_001"))
  );
});
