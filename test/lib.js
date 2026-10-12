'use strict';
/* Shared helpers for server/economy tests: a full app on a random port, a fake Telegram, a fake clock, scripted players. */
const http = require('http');
const { build } = require('../server/config.js');
const { createApp } = require('../server/app.js');
const TG = require('../server/tg.js');
const E = require('../src/engine.js');
const { createSyncClient } = require('../src/sync_client.js');
const { startMock } = require('./mock-telegram.js');

const BOT_TOKEN = '123456:TEST-TOKEN-abcdefghijklmnopqrstuvwxyz';
// noon (UTC) of the real current day: session tokens are checked against the real clock
const T0 = (() => { const d = new Date(); return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), 12, 0, 0); })();

async function makeEnv(envExtra = {}) {
  const mock = await startMock({});
  const env = Object.assign({
    BOT_TOKEN, BOT_USERNAME: 'od_test_bot', APP_SHORTNAME: 'play', ADMIN_KEY: 'adminkey123', PUBLIC_URL: 'https://game.example.test',
    CHANNEL: '@od_news', CHANNEL_URL: 'https://t.me/od_news', DB_PATH: ':memory:', TG_API_BASE: mock.url, BOT_MODE: 'off', SESSION_SECRET: 'test-secret',
  }, envExtra);
  const cfg = build(env);
  const clock = { t: T0 };
  const { ctx, handler } = createApp(cfg, { now: () => clock.t, log: () => {} });
  const server = http.createServer(handler);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;
  return {
    cfg, ctx, base, mock, clock,
    advance(ms) { clock.t += ms; },
    async close() { await new Promise((r) => server.close(r)); ctx.db.close(); await mock.close(); },
  };
}

async function httpJson(base, method, path, body, headers = {}) {
  const res = await fetch(base + path, {
    method,
    headers: Object.assign({ 'content-type': 'application/json' }, headers),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let j = null;
  try { j = await res.json(); } catch { /* not json */ }
  return { status: res.status, json: j };
}

// tiny deterministic RNG for scripts (separate from the game's own RNG)
function lcg(seed) {
  let s = seed >>> 0;
  return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296);
}

class Player {
  constructor(env, id, o = {}) {
    this.env = env; this.id = id; this.o = o;
    this.user = { id, first_name: o.name || 'P' + id, language_code: o.lang || 'en', is_premium: !!o.premium, username: o.username };
    this.rand = lcg(id * 7919 + 13);
    this.token = null; this.cl = null; this.eco = null; this.last = null;
    this.tp = { n: 0, mean: 0, m2: 0 }; this.lastAtt = null;
  }
  // a tap attempt (accepted or not) as the real client would measure it
  attempt(ms) {
    const t = this.cl.S.tk * 250 + ms;
    if (this.lastAtt !== null) {
      const d = t - this.lastAtt;
      if (d > 0 && d <= 5000) { const x = this.tp; x.n++; const dl = d - x.mean; x.mean += dl / x.n; x.m2 += dl * (d - x.mean); }
    }
    this.lastAtt = t;
    return this.cl.act('tap', ms);
  }
  headers() { return this.token ? { authorization: 'Bearer ' + this.token } : {}; }
  async auth(startParam, extra = {}) {
    const initData = extra.initData || TG.signInitData(BOT_TOKEN, this.user, { start_param: startParam }, this.env.clock.t);
    const r = await httpJson(this.env.base, 'POST', '/api/auth', { initData, platform: this.o.platform || 'android', fp: this.o.fp || ('dev-' + this.id), tz: extra.tz });
    if (r.json && r.json.token) {
      this.token = r.json.token;
      const S = E.deserialize(r.json.state);
      this.cl = createSyncClient(S, E);
      this.cl.seq = r.json.seq;
      this.eco = r.json.eco;
    }
    this.last = r.json;
    return r;
  }
  get S() { return this.cl.S; }
  api(method, path, body) { return httpJson(this.env.base, method, path, body, this.headers()); }
  // advance local game by n ticks (engine) while the fake wall clock moves the same amount
  tick(n) { E.advanceTicks(this.cl.S, n); this.env.advance(n * E.TICK * 1000); }
  async sync(opts = {}) {
    const body = this.cl.prepare();
    if (!opts.noTp) body.tp = [this.tp.n, this.tp.mean, this.tp.m2];
    this.tp = { n: 0, mean: 0, m2: 0 };
    if (opts.mutate) opts.mutate(body);
    const r = await this.api('POST', '/api/sync', body);
    if (r.status !== 200 || !r.json) { this.cl.fail(); return r; }
    this.cl.apply(r.json);
    if (r.json.eco) this.eco = r.json.eco;
    this.last = r.json;
    return r;
  }
  // scripted "reasonable human" policy for n ticks; tap rhythm is jittered unless o.regular
  play(ticks, o = {}) {
    const S = this.cl.S;
    const regular = o.regular || this.o.regular;
    for (let i = 0; i < ticks; i++) {
      const ev = E.eventView(this.cl.S);
      if (ev && i % 8 === 0) {
        const ch = ev.choices.find((c) => !c.disabled);
        if (ch) this.cl.act('ev', ch.i);
      }
      if (!this.cl.S.project && i % 4 === 0) {
        const t = E.tierUnlocked(this.cl.S, 'indie') ? 'indie' : 'mini';
        this.cl.act('new', 'puzzle', t, '');
      }
      // taps
      const taps = regular ? 1 : (this.rand() < 0.55 ? 1 + Math.floor(this.rand() * 3) : 0);
      for (let k = 0; k < taps; k++) this.attempt(regular ? 100 : Math.floor(this.rand() * 250));
      if (i % 12 === 0) this.buySomething();
      if (i % 20 === 0) this.claimAll();
      this.tick(1);
    }
  }
  buySomething() {
    const S = this.cl.S, c = this.cl;
    for (const r of E.ROLES) if (S.staff[r].lvl === 0 && S.cash >= 140 * 1.2) return c.act('hire', r).ok;
    const nf = E.nextFloor(S);
    if (nf) { const f = E.FLOORS.find((x) => x.id === nf); if (S.cash >= f.price * 1.1) return c.act('flr', nf).ok; }
    for (const it of E.ITEMS) if (!S.items[it.id] && S.cash >= it.price * 1.2) return c.act('buy', it.id).ok;
    for (const r of E.ROLES) if (S.staff[r].lvl > 0 && S.staff[r].lvl < 10 && S.cash >= E.upCost(r, S.staff[r].lvl) * 1.5) return c.act('ups', r).ok;
    if (S.you.lvl < 10 && S.cash >= E.youUpCost(S.you.lvl) * 1.5) return c.act('upy').ok;
    return false;
  }
  claimAll() {
    const S = this.cl.S;
    for (const g of E.GOALS) if (S.goals[g.id] === 1) this.cl.act('clm', g.id);
    for (const d of E.DAILY) if (!S.dq.done[d.id] && E.dailyProgress(S, d) >= d.need) this.cl.act('dly', d.id);
  }
}

let passed = 0, failed = 0;
const failures = [];
async function test(name, fn) {
  try { await fn(); passed++; console.log('  ✓', name); } catch (e) { failed++; failures.push(name); console.log('  ✗', name, '\n    ', (e && e.stack || e).toString().split('\n').slice(0, 4).join('\n     ')); }
}
function summary() {
  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed) { console.log('FAILED:', failures.join(' | ')); process.exitCode = 1; }
}

module.exports = { BOT_TOKEN, T0, makeEnv, httpJson, Player, lcg, test, summary, E, TG, createSyncClient };
