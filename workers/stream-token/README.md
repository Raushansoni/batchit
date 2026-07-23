# BatchIt Stream Token Worker (free)

Mints Stream Chat user JWTs after verifying a Firebase ID token.  
Replaces Firebase Cloud Functions — no Blaze billing required.

## Setup

```bash
cd workers/stream-token
npm install
npx wrangler login
```

Set secrets (from Stream dashboard + Firebase project id `batchit-prod`):

```bash
npx wrangler secret put STREAM_API_KEY
npx wrangler secret put STREAM_API_SECRET
npx wrangler secret put FIREBASE_PROJECT_ID
# value: batchit-prod
```

Deploy:

```bash
npx wrangler deploy
```

Copy the Worker URL (e.g. `https://batchit-stream-token.<you>.workers.dev`) into root `secrets.properties`:

```properties
STREAM_API_KEY=your_stream_key
STREAM_TOKEN_URL=https://batchit-stream-token.<you>.workers.dev
```

## API

`POST /token`  
Header: `Authorization: Bearer <Firebase ID token>`  
Body (optional): `{ "name": "Ada", "image": "https://..." }`  

Response: `{ "token", "userId", "name", "image" }`
