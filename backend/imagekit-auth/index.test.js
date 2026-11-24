'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { createImageKitAuthParams, verifyFirebaseIdToken, createServer } = require('./index');

test('createImageKitAuthParams produces valid HMAC-SHA1 signature and short expire', () => {
  const result = createImageKitAuthParams('private_test_secret', 'public_test_key', 900);
  assert.ok(result.token && result.token.length >= 16);
  assert.equal(result.publicKey, 'public_test_key');
  const nowSec = Math.floor(Date.now() / 1000);
  assert.ok(result.expire > nowSec + 800 && result.expire <= nowSec + 905);

  const expectedSig = crypto
    .createHmac('sha1', 'private_test_secret')
    .update(`${result.token}${result.expire}`)
    .digest('hex');
  assert.equal(result.signature, expectedSig);
});

test('verifyFirebaseIdToken validates RSA signature, audience, and issuer', async () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const nowSec = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: 'kid_1' })).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({
      aud: 'gen-lang-client-0444103939',
      iss: 'https://securetoken.google.com/gen-lang-client-0444103939',
      sub: 'admin_uid_1',
      exp: nowSec + 3600,
      iat: nowSec - 10,
      email: 'asadhacker770@gmail.com',
      email_verified: true,
    })
  ).toString('base64url');
  const signer = crypto.createSign('RSA-SHA256');
  signer.update(`${header}.${payload}`);
  signer.end();
  const sig = signer.sign(privateKey).toString('base64url');
  const token = `${header}.${payload}.${sig}`;

  const pem = publicKey.export({ type: 'spki', format: 'pem' });
  const decoded = await verifyFirebaseIdToken(token, { kid_1: pem });
  assert.equal(decoded.sub, 'admin_uid_1');
  assert.equal(decoded.email, 'asadhacker770@gmail.com');

  await assert.rejects(() => verifyFirebaseIdToken(token, { other: pem }), /not found/);
});

test('HTTP server rejects unauthenticated requests and returns signed auth when verified', async () => {
  const server = createServer({
    privateKey: 'private_test_key',
    publicKey: 'public_test_key',
    verifyFirebaseIdToken: async (tok) => {
      if (tok !== 'good-token') throw new Error('Invalid token');
      return { sub: 'uid_admin' };
    },
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  try {
    const unauth = await fetch(`http://127.0.0.1:${port}/auth`);
    assert.equal(unauth.status, 401);

    const authed = await fetch(`http://127.0.0.1:${port}/auth`, {
      headers: { Authorization: 'Bearer good-token' },
    });
    assert.equal(authed.status, 200);
    const body = await authed.json();
    assert.ok(body.signature);
    assert.ok(body.token);
    assert.equal(body.publicKey, 'public_test_key');
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
