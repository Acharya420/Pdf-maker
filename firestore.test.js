const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-pdfmaker-dcb36df0-08dc-4927-833f-13c41b1da3a7";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "soumyadipacharya481@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function dbFor(uid, tokenOptions = {}) {
  return testEnv.authenticatedContext(uid, tokenOptions).firestore();
}

function unauthDb() {
  return testEnv.unauthenticatedContext().firestore();
}

test("Unauthenticated caller cannot read users, pdf_records, app_config, announcements, or feedback", async () => {
  const db = unauthDb();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("pdf_records").get());
  await assertFails(db.collection("app_config").doc("global").get());
  await assertFails(db.collection("announcements").where("isActive", "==", true).get());
  await assertFails(db.collection("feedback").where("userId", "==", ALICE_UID).get());
});

test("User can create and read own profile, but cannot spoof status or read another user", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const validProfile = {
    userId: ALICE_UID,
    displayName: "Alice",
    email: "alice@example.com",
    preferredTheme: "dark",
    preferredLanguage: "en",
    pdfsCreatedCount: 0,
    totalBytesProcessed: 0,
    status: "active",
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user isolation
  const bobDb = dbFor(BOB_UID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Cannot create profile with suspended status or ghost field
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validProfile,
      userId: BOB_UID,
      status: "suspended",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...validProfile,
      userId: BOB_UID,
      isAdmin: true,
    })
  );
});

test("User can create, list, update, delete own pdf_records; Bob cannot access Alice's", async () => {
  const aliceDb = dbFor(ALICE_UID);
  const record = {
    userId: ALICE_UID,
    fileName: "Invoice_2026.pdf",
    operation: "image_to_pdf",
    pageCount: 3,
    fileSizeBytes: 245000,
    isFavorite: false,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("pdf_records").doc("rec_1").set(record)
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("pdf_records").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("pdf_records").doc("rec_1").update({
      isFavorite: true,
      updatedAt: new Date(),
    })
  );

  const bobDb = dbFor(BOB_UID);
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("pdf_records").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("pdf_records").doc("rec_1").delete()
  );
});

test("Feedback: user can submit open feedback and read own; only admin can resolve or read all", async () => {
  const aliceDb = dbFor(ALICE_UID);
  await assertSucceeds(
    aliceDb.collection("feedback").doc("fb_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      category: "feature",
      subject: "Dark glass mode",
      message: "Love the PDF compressor!",
      appVersion: "1.0",
      status: "open",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // User cannot inject adminReply or resolved status on create
  await assertFails(
    aliceDb.collection("feedback").doc("fb_bad").set({
      userId: ALICE_UID,
      category: "bug",
      subject: "Bad",
      message: "Spoof",
      appVersion: "1.0",
      status: "resolved",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Query alignment
  await assertSucceeds(aliceDb.collection("feedback").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("feedback").get());

  // Verified admin can read all and resolve
  const adminDb = dbFor("admin_uid_1", { email: ADMIN_EMAIL, email_verified: true });
  await assertSucceeds(adminDb.collection("feedback").get());
  await assertSucceeds(
    adminDb.collection("feedback").doc("fb_1").update({
      status: "resolved",
      adminReply: "Added in v1.1!",
      updatedAt: new Date(),
    })
  );

  // Unverified email spoof must fail
  const spoofAdminDb = dbFor("spoof_uid", { email: ADMIN_EMAIL, email_verified: false });
  await assertFails(spoofAdminDb.collection("feedback").get());
});

test("App config & announcements: signed-in users can read active, only admin can write", async () => {
  const adminDb = dbFor("admin_uid_1", { email: ADMIN_EMAIL, email_verified: true });
  await assertSucceeds(
    adminDb.collection("app_config").doc("global").set({
      maintenanceMode: false,
      maintenanceMessage: "",
      latestVersionCode: 1,
      latestVersionName: "1.0",
      minSupportedVersionCode: 1,
      forceUpdate: false,
      releaseNotes: "Initial launch",
      enableImageToPdf: true,
      enableCompress: true,
      enableMerge: true,
      enableSplit: true,
      enableOrganizer: true,
      enablePasswordProtect: true,
      updatedAt: new Date(),
    })
  );

  const aliceDb = dbFor(ALICE_UID);
  await assertSucceeds(aliceDb.collection("app_config").doc("global").get());
  await assertFails(
    aliceDb.collection("app_config").doc("global").update({ maintenanceMode: true, updatedAt: new Date() })
  );

  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_1").set({
      title: "Welcome",
      message: "Offline PDF Maker is live",
      severity: "info",
      isActive: true,
      createdBy: "admin_uid_1",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("announcements").where("isActive", "==", true).get());
  await assertFails(aliceDb.collection("announcements").get());
});
