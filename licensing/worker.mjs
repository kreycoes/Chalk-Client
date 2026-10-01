// Chalk licensing: tokens/passwords from Minecraft never reach this Worker.
const DAY = 86400000;
const json = (body, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store' } });
const random = () => Array.from(crypto.getRandomValues(new Uint8Array(20)), b => b.toString(16).padStart(2, '0')).join('');
const hash = async value => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))), b => b.toString(16).padStart(2, '0')).join('').toUpperCase();

export default {
  async fetch(request, env) {
    try {
      const path = new URL(request.url).pathname;
      if (request.method === 'GET' && path === '/health') return json({ service: 'chalk-license', version: 1 });
      if (request.method !== 'POST') return json({ error: 'Not found' }, 404);
      if (!['/challenge', '/activate', '/check'].includes(path)) return json({ error: 'Not found' }, 404);
      const now = Date.now();
      const bucket = Math.floor(now / 60000);
      const source = await hash('chalk-rate:' + (request.headers.get('CF-Connecting-IP') || 'unknown') + ':' + bucket);
      const rate = await env.DB.prepare(`INSERT INTO license_rate (source,bucket,count) VALUES (?,?,1)
        ON CONFLICT(source) DO UPDATE SET count=count+1 RETURNING count`).bind(source, bucket).first();
      if (rate.count > 30) return json({ error: 'Too many requests' }, 429);
      await env.DB.prepare('DELETE FROM license_rate WHERE bucket<?').bind(bucket - 2).run();
      if (Number(request.headers.get('Content-Length') || 0) > 4096) return json({ error: 'Too large' }, 413);
      const text = await request.text();
      if (text.length > 4096) return json({ error: 'Too large' }, 413);
      let body;
      try { body = JSON.parse(text); } catch { return json({ error: 'Invalid JSON' }, 400); }
      if (!body || typeof body !== 'object') return json({ error: 'Invalid request' }, 400);
      if (path === '/challenge') {
        if (!/^[A-Za-z0-9_]{1,16}$/.test(body.username || '')) return json({ error: 'Invalid username' }, 400);
        const code = String(body.code || '').trim().toUpperCase().replaceAll('-', '');
        if (!/^[A-Z0-9]{8,12}$/.test(code)) return json({ error: 'Invalid code' }, 403);
        const codeHash = await hash(code);
        const license = await env.DB.prepare('SELECT edition FROM licenses WHERE code_hash=? AND active=1').bind(codeHash).first();
        if (!license) return json({ error: 'Invalid code' }, 403);
        const challenge = random();
        await env.DB.batch([
          env.DB.prepare('DELETE FROM license_challenges WHERE expires_at<?').bind(now),
          env.DB.prepare('INSERT INTO license_challenges (challenge,username,code_hash,expires_at) VALUES (?,?,?,?)').bind(challenge, body.username, codeHash, now + 120000)
        ]);
        return json({ challenge, edition: license.edition });
      }
      if (path === '/activate') {
        if (!/^[a-f0-9]{40}$/.test(body.challenge || '')) return json({ error: 'Invalid challenge' }, 400);
        // Consume once before the upstream request. A failed authentication needs a new challenge.
        const challenge = await env.DB.prepare('DELETE FROM license_challenges WHERE challenge=? AND expires_at>? RETURNING *').bind(body.challenge, now).first();
        if (!challenge) return json({ error: 'Challenge expired' }, 403);
        const url = new URL('https://sessionserver.mojang.com/session/minecraft/hasJoined');
        url.searchParams.set('username', challenge.username);
        url.searchParams.set('serverId', challenge.challenge);
        const response = await fetch(url, { signal: AbortSignal.timeout(10000), redirect: 'error' });
        if (response.status !== 200) return json({ error: 'Minecraft account verification failed' }, 403);
        const profile = await response.json();
        if (!/^[a-f0-9]{32}$/i.test(profile.id || '') || String(profile.name).toLowerCase() !== challenge.username.toLowerCase()) return json({ error: 'Invalid Minecraft profile' }, 403);
        const account = await hash('chalk-account-v1:' + profile.id.toLowerCase());
        // Conditional INSERT is atomic: two concurrent accounts cannot claim a one-account code.
        await env.DB.prepare(`INSERT OR IGNORE INTO license_activations
          (code_hash,account_key,activated_at,expires_at,last_seen_at)
          SELECT code_hash,?,?,CASE WHEN edition='beta' THEN ? ELSE NULL END,?
          FROM licenses WHERE code_hash=? AND active=1 AND
          (max_accounts IS NULL OR (SELECT COUNT(*) FROM license_activations WHERE code_hash=licenses.code_hash)<max_accounts)`)
          .bind(account, now, now + DAY, now, challenge.code_hash).run();
        const activation = await env.DB.prepare(`SELECT a.expires_at,l.edition FROM license_activations a
          JOIN licenses l ON l.code_hash=a.code_hash WHERE a.code_hash=? AND a.account_key=? AND l.active=1`)
          .bind(challenge.code_hash, account).first();
        if (!activation) return json({ error: 'License already assigned or disabled' }, 403);
        if (activation.expires_at !== null && activation.expires_at <= now) return json({ error: 'Beta expired', expired: true }, 403);
        const token = random() + random();
        await env.DB.batch([
          env.DB.prepare('DELETE FROM license_sessions WHERE valid_until<?').bind(now),
          env.DB.prepare('INSERT INTO license_sessions (token_hash,code_hash,account_key,valid_until) VALUES (?,?,?,?)')
            .bind(await hash(token), challenge.code_hash, account, now + 3600000)
        ]);
        return json({ token, edition: activation.edition, remainingMillis: activation.expires_at === null ? -1 : activation.expires_at - now });
      }
      if (path === '/check') {
        if (!/^[a-f0-9]{80}$/.test(body.token || '')) return json({ error: 'Invalid session' }, 403);
        const activation = await env.DB.prepare(`SELECT a.expires_at,l.edition FROM license_sessions s
          JOIN licenses l ON l.code_hash=s.code_hash JOIN license_activations a ON a.code_hash=s.code_hash AND a.account_key=s.account_key
          WHERE s.token_hash=? AND s.valid_until>? AND l.active=1`).bind(await hash(body.token), now).first();
        if (!activation) return json({ error: 'Authentication required' }, 403);
        if (activation.expires_at !== null && activation.expires_at <= now) return json({ error: 'Beta expired', expired: true }, 403);
        return json({ edition: activation.edition, remainingMillis: activation.expires_at === null ? -1 : activation.expires_at - now });
      }
      return json({ error: 'Not found' }, 404);
    } catch {
      return json({ error: 'License service temporarily unavailable' }, 503);
    }
  }
};
