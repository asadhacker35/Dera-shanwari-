'use strict';

const http = require('node:http');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 8787);
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0444103939';
const GOOGLE_CERTS_URL =
  'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';

let cachedCerts = null;
let certsExpireAt = 0;

function base64UrlDecode(str) {
  const normalized = str.replace(/-/g, '+').replace(/_/g, '/');
  const pad = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
  return Buffer.from(normalized + pad, 'base64');
}

async function getGooglePublicCerts() {
  const now = Date.now();
  if (cachedCerts && now < certsExpireAt) {
    return cachedCerts;
  }
  const res = await fetch(GOOGLE_CERTS_URL);
  if (!res.ok) {
    throw new Error(`Unable to load Firebase public certificates (${res.status})`);
  }
  const cacheControl = res.headers.get('cache-control') || '';
  const match = cacheControl.match(/max-age=(\d+)/i);
  const maxAgeSec = match ? Number(match[1]) : 3600;
  cachedCerts = await res.json();
  certsExpireAt = now + maxAgeSec * 1000;
  return cachedCerts;
}

async function verifyFirebaseIdToken(idToken, certsOverride) {
  if (!idToken || typeof idToken !== 'string') {
    throw new Error('Missing Firebase ID token');
  }
  const parts = idToken.split('.');
  if (parts.length !== 3) {
    throw new Error('Malformed JWT');
  }
  const header = JSON.parse(base64UrlDecode(parts[0]).toString('utf8'));
  const payload = JSON.parse(base64UrlDecode(parts[1]).toString('utf8'));
  const signatureBuf = base64UrlDecode(parts[2]);

  if (header.alg !== 'RS256' || !header.kid) {
    throw new Error('Unsupported JWT algorithm');
  }
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.aud !== FIREBASE_PROJECT_ID) {
    throw new Error('Token audience mismatch');
  }
  if (payload.iss !== `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`) {
    throw new Error('Token issuer mismatch');
  }
  if (!payload.sub || typeof payload.sub !== 'string') {
    throw new Error('Token missing subject');
  }
  if (typeof payload.exp !== 'number' || payload.exp <= nowSec) {
    throw new Error('Token expired');
  }
  if (typeof payload.iat !== 'number' || payload.iat > nowSec + 300) {
    throw new Error('Token issued in the future');
  }

  const certs = certsOverride || (await getGooglePublicCerts());
  const pem = certs[header.kid];
  if (!pem) {
    throw new Error('Signing certificate not found for kid');
  }

  const verifier = crypto.createVerify('RSA-SHA256');
  verifier.update(`${parts[0]}.${parts[1]}`);
  verifier.end();
  if (!verifier.verify(pem, signatureBuf)) {
    throw new Error('Invalid Firebase ID token signature');
  }
  return payload;
}

function createImageKitAuthParams(privateKey, publicKey, ttlSeconds = 1800) {
  if (!privateKey || typeof privateKey !== 'string' || !privateKey.trim()) {
    throw new Error('IMAGEKIT_PRIVATE_KEY is not configured on the server');
  }
  const clampedTtl = Math.max(60, Math.min(3500, Number(ttlSeconds) || 1800));
  const token = crypto.randomUUID();
  const expire = Math.floor(Date.now() / 1000) + clampedTtl;
  const signature = crypto
    .createHmac('sha1', privateKey.trim())
    .update(`${token}${expire}`)
    .digest('hex');

  return {
    token,
    expire,
    signature,
    publicKey: (publicKey || '').trim(),
  };
}

function sendJson(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

async function readJsonBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 64 * 1024) throw new Error('Payload too large');
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf8').trim();
  return raw ? JSON.parse(raw) : {};
}

function createServer(options = {}) {
  const getPrivateKey = () =>
    options.privateKey !== undefined ? options.privateKey : process.env.IMAGEKIT_PRIVATE_KEY || '';
  const getPublicKey = () =>
    options.publicKey !== undefined ? options.publicKey : process.env.IMAGEKIT_PUBLIC_KEY || '';
  const verifyToken = options.verifyFirebaseIdToken || verifyFirebaseIdToken;
  const fetchImpl = options.fetchImpl || fetch;

  return http.createServer(async (req, res) => {
    try {
      const reqUrl = new URL(req.url || '/', 'http://localhost');
      if (req.method === 'GET' && reqUrl.pathname === '/health') {
        return sendJson(res, 200, { ok: true, service: 'dera-shanwari-imagekit-auth' });
      }

      const authHeader = req.headers.authorization || '';
      const bearer = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : '';
      let decoded;
      try {
        decoded = await verifyToken(bearer);
      } catch (err) {
        return sendJson(res, 401, { error: 'Unauthorized', message: err.message });
      }

      if (req.method === 'GET' && (reqUrl.pathname === '/auth' || reqUrl.pathname === '/imagekit-auth')) {
        const priv = getPrivateKey();
        if (!priv.trim()) {
          return sendJson(res, 503, { error: 'Server misconfigured: missing IMAGEKIT_PRIVATE_KEY' });
        }
        const params = createImageKitAuthParams(priv, getPublicKey());
        return sendJson(res, 200, { ...params, uid: decoded.sub });
      }

      if (
        (req.method === 'DELETE' && reqUrl.pathname.startsWith('/file/')) ||
        (req.method === 'POST' && reqUrl.pathname === '/delete')
      ) {
        const priv = getPrivateKey();
        if (!priv.trim()) {
          return sendJson(res, 503, { error: 'Server misconfigured: missing IMAGEKIT_PRIVATE_KEY' });
        }
        let fileId = '';
        if (req.method === 'DELETE') {
          fileId = decodeURIComponent(reqUrl.pathname.slice('/file/'.length)).trim();
        } else {
          const body = await readJsonBody(req);
          fileId = String(body.fileId || '').trim();
        }
        if (!fileId || !/^[a-zA-Z0-9_-]{4,128}$/.test(fileId)) {
          return sendJson(res, 400, { error: 'Invalid ImageKit fileId' });
        }

        const basic = Buffer.from(`${priv.trim()}:`).toString('base64');
        const ikRes = await fetchImpl(`https://api.imagekit.io/v1/files/${encodeURIComponent(fileId)}`, {
          method: 'DELETE',
          headers: { Authorization: `Basic ${basic}` },
        });
        if (!ikRes.ok && ikRes.status !== 404) {
          const errText = await ikRes.text();
          return sendJson(res, ikRes.status, { error: 'ImageKit delete failed', detail: errText });
        }
        return sendJson(res, 200, { deleted: true, fileId });
      }

      return sendJson(res, 404, { error: 'Not found' });
    } catch (err) {
      return sendJson(res, 500, { error: 'Internal server error', message: err.message });
    }
  });
}

if (require.main === module) {
  const server = createServer();
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`ImageKit auth server listening on port ${PORT}`);
  });
}

module.exports = {
  createImageKitAuthParams,
  verifyFirebaseIdToken,
  createServer,
};
