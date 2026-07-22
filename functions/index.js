/**
 * BatchIt Cloud Functions
 *
 * Configure Stream credentials via:
 *   firebase functions:config:set stream.key="..." stream.secret="..."
 * or environment variables STREAM_API_KEY / STREAM_API_SECRET.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { StreamChat } = require("stream-chat");

admin.initializeApp();

const db = admin.firestore();
const bucket = admin.storage().bucket();

/**
 * Resolve Stream API credentials from functions.config() or process.env.
 * Fallbacks are documentation placeholders — replace before production deploy.
 */
function getStreamCredentials() {
  const config = functions.config();
  const apiKey =
    process.env.STREAM_API_KEY ||
    (config.stream && config.stream.key) ||
    "STREAM_API_KEY";
  const apiSecret =
    process.env.STREAM_API_SECRET ||
    (config.stream && config.stream.secret) ||
    "STREAM_API_SECRET";
  return { apiKey, apiSecret };
}

function getStreamClient() {
  const { apiKey, apiSecret } = getStreamCredentials();
  return StreamChat.getInstance(apiKey, apiSecret);
}

function requireAuth(context) {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required."
    );
  }
  return context.auth.uid;
}

/**
 * Upsert the authenticated user in Stream Chat and return a user token.
 */
exports.createStreamUserAndGetToken = functions.https.onCall(
  async (data, context) => {
    const uid = requireAuth(context);
    const name = (data && data.name) || context.auth.token.name || uid;
    const image = (data && data.image) || context.auth.token.picture || "";

    const client = getStreamClient();
    await client.upsertUser({
      id: uid,
      name,
      image,
    });

    const token = client.createToken(uid);
    return { token, userId: uid, name, image };
  }
);

/**
 * Return a Stream Chat token for the authenticated user (no upsert).
 */
exports.getStreamUserToken = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const client = getStreamClient();
  const token = client.createToken(uid);
  return { token, userId: uid };
});

/**
 * Hourly job: delete expired Firestore status docs and their Storage files.
 */
exports.expireStatuses = functions.pubsub
  .schedule("every 60 minutes")
  .onRun(async () => {
    const now = admin.firestore.Timestamp.now();
    const snapshot = await db
      .collection("statuses")
      .where("expiresAt", "<", now)
      .get();

    if (snapshot.empty) {
      console.log("expireStatuses: no expired statuses");
      return null;
    }

    const batch = db.batch();
    const deleteFilePromises = [];

    snapshot.docs.forEach((doc) => {
      const data = doc.data();
      batch.delete(doc.ref);

      if (data.mediaUrl && typeof data.mediaUrl === "string") {
        // Prefer storagePath when present; otherwise try parsing Storage URL.
        const storagePath =
          data.storagePath ||
          `statuses/${data.userId}/${doc.id}`;
        deleteFilePromises.push(
          bucket
            .file(storagePath)
            .delete()
            .catch((err) => {
              console.warn(
                `expireStatuses: failed to delete ${storagePath}`,
                err.message
              );
            })
        );
      }
    });

    await Promise.all([batch.commit(), ...deleteFilePromises]);
    console.log(`expireStatuses: removed ${snapshot.size} statuses`);
    return null;
  });

/**
 * Delete the caller's account: Firestore user, Stream user, and Storage files.
 */
exports.deleteUserAccount = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);

  // Firestore user document
  await db
    .collection("users")
    .doc(uid)
    .delete()
    .catch((err) => {
      console.warn("deleteUserAccount: firestore user", err.message);
    });

  // User's statuses
  const statuses = await db
    .collection("statuses")
    .where("userId", "==", uid)
    .get();
  const batch = db.batch();
  statuses.docs.forEach((doc) => batch.delete(doc.ref));
  if (!statuses.empty) {
    await batch.commit();
  }

  // Storage under statuses/{uid}/ and users/{uid}/
  const prefixes = [`statuses/${uid}/`, `users/${uid}/`];
  for (const prefix of prefixes) {
    const [files] = await bucket.getFiles({ prefix }).catch(() => [[]]);
    await Promise.all(
      files.map((file) =>
        file.delete().catch((err) => {
          console.warn(`deleteUserAccount: storage ${file.name}`, err.message);
        })
      )
    );
  }

  // Stream Chat user
  try {
    const client = getStreamClient();
    await client.deleteUser(uid, {
      mark_messages_deleted: true,
      hard_delete: true,
    });
  } catch (err) {
    console.warn("deleteUserAccount: stream user", err.message);
  }

  // Firebase Auth user
  await admin
    .auth()
    .deleteUser(uid)
    .catch((err) => {
      console.warn("deleteUserAccount: auth user", err.message);
    });

  return { success: true };
});
