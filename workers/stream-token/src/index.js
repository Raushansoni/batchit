/**
 * BatchIt free Stream token Worker.
 *
 * POST /token
 *   Authorization: Bearer <Firebase ID token>
 *   Body JSON (optional): { "name": "...", "image": "..." }
 *
 * Returns: { token, userId, name, image }
 *
 * Secrets (wrangler secret put ...):
 *   STREAM_API_KEY, STREAM_API_SECRET, FIREBASE_PROJECT_ID
 */

const FIREBASE_JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") {
      return cors(json({ ok: true, service: "batchit-stream-token" }));
    }

    if (request.method !== "POST" || url.pathname !== "/token") {
      return cors(json({ error: "Not found" }, 404));
    }

    try {
      const apiKey = required(env.STREAM_API_KEY, "STREAM_API_KEY");
      const apiSecret = required(env.STREAM_API_SECRET, "STREAM_API_SECRET");
      const projectId = required(env.FIREBASE_PROJECT_ID, "FIREBASE_PROJECT_ID");

      const authHeader = request.headers.get("Authorization") || "";
      const idToken = authHeader.startsWith("Bearer ")
        ? authHeader.slice(7).trim()
        : "";
      if (!idToken) {
        return cors(json({ error: "Missing Bearer Firebase ID token" }, 401));
      }

      const firebaseUser = await verifyFirebaseIdToken(idToken, projectId);
      const body = await safeJson(request);
      const name =
        (body && body.name) ||
        firebaseUser.name ||
        firebaseUser.email ||
        firebaseUser.user_id;
      const image = (body && body.image) || firebaseUser.picture || "";
      const userId = sanitizeStreamUserId(firebaseUser.user_id);

      await upsertStreamUser(apiKey, apiSecret, {
        id: userId,
        name,
        image,
      });

      const token = await createStreamUserToken(userId, apiSecret);
      return cors(json({ token, userId, name, image }));
    } catch (error) {
      const message = error && error.message ? error.message : "Token mint failed";
      const status = /unauth|token|audience|issuer|expired|signature|kid/i.test(
        message
      )
        ? 401
        : 500;
      return cors(json({ error: message }, status));
    }
  },
};

function required(value, name) {
  if (!value) throw new Error(`Missing Worker secret: ${name}`);
  return value;
}

function cors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  return new Response(response.body, {
    status: response.status,
    headers,
  });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function safeJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

/** Stream user IDs: letters, numbers, underscore, hyphen, @, only. */
function sanitizeStreamUserId(uid) {
  return String(uid).replace(/[^a-zA-Z0-9@_-]/g, "_");
}

/**
 * Verify a Firebase Auth ID token (not a Google OAuth id_token).
 * Uses Google's JWKS for securetoken@system.gserviceaccount.com.
 */
async function verifyFirebaseIdToken(idToken, projectId) {
  const parts = String(idToken).split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid Firebase ID token format");
  }

  const header = JSON.parse(base64UrlToString(parts[0]));
  const payload = JSON.parse(base64UrlToString(parts[1]));

  if (header.alg !== "RS256") {
    throw new Error("Unexpected Firebase token algorithm");
  }
  if (!header.kid) {
    throw new Error("Firebase token missing kid");
  }

  const now = Math.floor(Date.now() / 1000);
  if (payload.exp && Number(payload.exp) < now) {
    throw new Error("Firebase token expired");
  }
  if (payload.iat && Number(payload.iat) > now + 60) {
    throw new Error("Firebase token issued in the future");
  }
  if (payload.aud !== projectId) {
    throw new Error("Firebase token audience mismatch");
  }
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) {
    throw new Error("Firebase token issuer mismatch");
  }
  if (!payload.sub || typeof payload.sub !== "string") {
    throw new Error("Firebase token missing subject");
  }

  const jwksRes = await fetch(FIREBASE_JWKS_URL);
  if (!jwksRes.ok) {
    throw new Error("Could not fetch Firebase public keys");
  }
  const jwks = await jwksRes.json();
  const jwk = (jwks.keys || []).find((key) => key.kid === header.kid);
  if (!jwk) {
    throw new Error("Firebase token kid not found");
  }

  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const data = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
  const signature = base64UrlToBuffer(parts[2]);
  const valid = await crypto.subtle.verify(
    { name: "RSASSA-PKCS1-v1_5" },
    cryptoKey,
    signature,
    data
  );
  if (!valid) {
    throw new Error("Invalid Firebase ID token signature");
  }

  return {
    user_id: payload.user_id || payload.sub,
    email: payload.email,
    name: payload.name,
    picture: payload.picture,
  };
}

async function createStreamUserToken(userId, apiSecret) {
  const now = Math.floor(Date.now() / 1000);
  return signHs256(
    {
      user_id: userId,
      iat: now,
      // Stream tokens without exp remain valid until revoked / secret rotate.
    },
    apiSecret
  );
}

async function createStreamServerToken(apiSecret) {
  return signHs256({}, apiSecret);
}

async function upsertStreamUser(apiKey, apiSecret, user) {
  const serverToken = await createStreamServerToken(apiSecret);
  const res = await fetch(
    `https://chat.stream-io-api.com/users?api_key=${encodeURIComponent(apiKey)}`,
    {
      method: "POST",
      headers: {
        Authorization: serverToken,
        "stream-auth-type": "jwt",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        users: {
          [user.id]: {
            id: user.id,
            name: user.name,
            image: user.image,
          },
        },
      }),
    }
  );
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Stream upsert failed (${res.status}): ${text}`);
  }
}

async function signHs256(payload, secret) {
  const header = { alg: "HS256", typ: "JWT" };
  const enc = new TextEncoder();
  const data = `${base64Url(JSON.stringify(header))}.${base64Url(
    JSON.stringify(payload)
  )}`;
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, enc.encode(data));
  return `${data}.${base64UrlFromBuffer(signature)}`;
}

function base64Url(text) {
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlFromBuffer(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlToString(input) {
  return new TextDecoder().decode(base64UrlToBuffer(input));
}

function base64UrlToBuffer(input) {
  const padded = input.replace(/-/g, "+").replace(/_/g, "/");
  const padLength = (4 - (padded.length % 4)) % 4;
  const base64 = padded + "=".repeat(padLength);
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}
