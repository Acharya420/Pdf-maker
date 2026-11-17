import * as admin from "firebase-admin";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

admin.initializeApp();

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-pdfmaker-dcb36df0-08dc-4927-833f-13c41b1da3a7";

const db = getFirestore(admin.app(), DATABASE_ID);

async function assertAdmin(auth?: { uid: string; token: Record<string, unknown> }) {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const email = auth.token.email as string | undefined;
  const emailVerified = auth.token.email_verified === true;
  if (email === "soumyadipacharya481@gmail.com" && emailVerified) {
    return;
  }
  const adminDoc = await db.collection("admins").doc(auth.uid).get();
  if (!adminDoc.exists && auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "Admin privileges required.");
  }
}

export const sendPushNotification = onCall(async (request) => {
  await assertAdmin(request.auth);
  const { topic, title, body } = request.data as {
    topic?: string;
    title?: string;
    body?: string;
  };
  if (!title || !body) {
    throw new HttpsError("invalid-argument", "Notification title and body are required.");
  }
  const targetTopic = (topic || "all_users").replace(/[^a-zA-Z0-9_-]/g, "");
  const messageId = await admin.messaging().send({
    topic: targetTopic,
    notification: { title, body },
  });
  return { success: true, messageId };
});

export const setUserAccountStatus = onCall(async (request) => {
  await assertAdmin(request.auth);
  const { targetUid, status } = request.data as {
    targetUid?: string;
    status?: "active" | "suspended";
  };
  if (!targetUid || (status !== "active" && status !== "suspended")) {
    throw new HttpsError("invalid-argument", "Valid targetUid and status required.");
  }
  await admin.auth().updateUser(targetUid, { disabled: status === "suspended" });
  await db.collection("users").doc(targetUid).update({
    status,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return { success: true, targetUid, status };
});

export const setAdminRole = onCall(async (request) => {
  await assertAdmin(request.auth);
  const { targetUid, email, role, enabled } = request.data as {
    targetUid?: string;
    email?: string;
    role?: "super_admin" | "moderator";
    enabled?: boolean;
  };
  if (!targetUid) {
    throw new HttpsError("invalid-argument", "targetUid is required.");
  }
  if (enabled) {
    await admin.auth().setCustomUserClaims(targetUid, { admin: true, role: role || "moderator" });
    await db.collection("admins").doc(targetUid).set({
      uid: targetUid,
      email: email || "",
      role: role || "moderator",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  } else {
    await admin.auth().setCustomUserClaims(targetUid, { admin: false });
    await db.collection("admins").doc(targetUid).delete();
  }
  return { success: true };
});
