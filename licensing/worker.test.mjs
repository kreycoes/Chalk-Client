import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const db = new DatabaseSync(':memory:');
db.exec(`CREATE TABLE licenses(code_hash TEXT PRIMARY KEY,edition TEXT,active INTEGER,max_accounts INTEGER);
CREATE TABLE license_activations(code_hash TEXT,account_key TEXT,activated_at INTEGER,expires_at INTEGER,last_seen_at INTEGER,PRIMARY KEY(code_hash,account_key));`);
db.exec(readFileSync(new URL('./migration.sql', import.meta.url), 'utf8'));
const hash = async s => Buffer.from(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))).toString('hex').toUpperCase();
db.prepare('INSERT INTO licenses VALUES (?,?,1,?)').run(await hash('BETATEST'), 'beta', null);
db.prepare('INSERT INTO licenses VALUES (?,?,1,?)').run(await hash('TESTCODE1234'), 'lifetime', 1);
const env = { DB: { prepare(sql) {
  let args = [];
  const stmt = { bind(...a) { args = a; return stmt; }, async first() { return db.prepare(sql).get(...args) || null; }, async run() { return db.prepare(sql).run(...args); } };
  return stmt;
}, async batch(statements) { const results = []; for (const s of statements) results.push(await s.run()); return results; } } };
let profileId = '12345678901234567890123456789012';
globalThis.fetch = async () => Response.json({ id: profileId, name: 'Tester' });
async function call(path, body) {
  const response = await worker.fetch(new Request('https://test' + path, { method: 'POST', body: JSON.stringify(body), headers: { 'CF-Connecting-IP': '127.0.0.1' } }), env);
  return { status: response.status, body: await response.json() };
}
async function activate(code) {
  const challenge = await call('/challenge', { username: 'Tester', code });
  assert.equal(challenge.status, 200);
  const result = await call('/activate', { challenge: challenge.body.challenge });
  assert.equal((await call('/activate', { challenge: challenge.body.challenge })).status, 403, 'No replay');
  return result;
}
assert.equal((await call('/challenge', { username: 'Tester', code: 'INVALID0' })).status, 403);
const beta = await activate('BETATEST');
assert.equal(beta.status, 200);
assert.ok(beta.body.remainingMillis > 86300000);
assert.equal((await call('/check', { token: beta.body.token })).status, 200);
const first = db.prepare('SELECT activated_at FROM license_activations').get().activated_at;
await activate('BETATEST');
assert.equal(db.prepare('SELECT activated_at FROM license_activations').get().activated_at, first, 'Reinstall preserves activation');
db.exec('UPDATE license_activations SET expires_at=1');
assert.equal((await activate('BETATEST')).body.expired, true);
assert.equal((await call('/check', { token: beta.body.token })).body.expired, true);
assert.equal((await activate('TESTCODE1234')).status, 200);
profileId = '22345678901234567890123456789012';
assert.equal((await activate('TESTCODE1234')).status, 403, 'Lifetime bound to first account');
for (let i = 0; i < 31; i++) await call('/check', { token: 'bad' });
assert.equal((await call('/check', { token: 'bad' })).status, 429);
console.log('PASS: invalid code, verified activation, replay prevention, unchanged trial start, expiry, lifetime account binding, rate limit. Mojang responses mocked.');
