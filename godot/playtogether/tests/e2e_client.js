'use strict';
// Внешний «друг» для e2e LANTERN: ждёт autotest-ready.json, проверяет то, что видит настоящая игра,
// и пишет autotest-done.json. Запуск: node tests/e2e_client.js <папка данных> <java|bedrock>
// Нужны пакеты: mineflayer (Java) и bedrock-protocol + jsp-raknet (Bedrock) — путь через NODE_PATH.
const fs = require('fs');
const path = require('path');
const dgram = require('dgram');
const dir = process.argv[2];
const mode = process.argv[3] || 'java';
const MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex');
const log = (...a) => console.log('[client]', ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const result = { ok: false, checks: {} };

async function waitReady() {
  const f = path.join(dir, 'autotest-ready.json');
  for (let i = 0; i < 3600; i++) {
    if (fs.existsSync(f)) { await sleep(300); return JSON.parse(fs.readFileSync(f, 'utf8')); }
    await sleep(500);
  }
  throw new Error('ready timeout');
}

// Java: ловим LAN-объявление, как «Сетевая игра»
function lanAnnounce(ms) {
  return new Promise((resolve) => {
    const s = dgram.createSocket({ type: 'udp4', reuseAddr: true });
    const t = setTimeout(() => { s.close(); resolve(null); }, ms);
    s.on('message', (m, r) => { clearTimeout(t); s.close(); resolve({ text: m.toString(), from: r.address }); });
    s.bind(4445, () => { try { s.addMembership('224.0.2.60'); } catch (e) { log('membership', e.message); } });
  });
}

// Bedrock: unconnected ping (как вкладка «Друзья») + Open Connection Request 1 (начало входа)
function raknet(port, ms = 4000) {
  return new Promise((resolve) => {
    const s = dgram.createSocket('udp4');
    const out = {};
    const t = setTimeout(() => { s.close(); resolve(out); }, ms);
    s.on('message', (m) => {
      if (m[0] === 0x1c) { out.pong = m.subarray(35).toString(); s.send(ocr1(), port, '127.0.0.1'); }
      if (m[0] === 0x06) { out.reply1 = true; out.mtu = m.readUInt16BE(m.length - 2); clearTimeout(t); s.close(); resolve(out); }
    });
    s.bind(0, () => {
      s.setBroadcast(true);
      const p = Buffer.alloc(33); p[0] = 0x01; p.writeBigUInt64BE(BigInt(Date.now()), 1); MAGIC.copy(p, 9); p.writeBigUInt64BE(1234n, 25);
      s.send(p, port, '127.0.0.1');
    });
  });
}
function ocr1() { const b = Buffer.alloc(1 + 16 + 1 + 1000); b[0] = 0x05; MAGIC.copy(b, 1); b[17] = 11; return b; }

// Java: Server List Ping (как список серверов в игре) — не зависит от версии протокола
function slp(port) {
  const net = require('net');
  const varint = (n) => { const b = []; do { let x = n & 0x7f; n >>>= 7; if (n) x |= 0x80; b.push(x); } while (n); return Buffer.from(b); };
  const pkt = (id, body) => { const d = Buffer.concat([varint(id), body]); return Buffer.concat([varint(d.length), d]); };
  return new Promise((resolve) => {
    const s = net.connect(port, '127.0.0.1');
    let buf = Buffer.alloc(0);
    const t = setTimeout(() => { s.destroy(); resolve(null); }, 15000);
    s.on('connect', () => {
      const host = Buffer.from('127.0.0.1');
      const hs = Buffer.concat([varint(-1 >>> 0 & 0x7fffffff), varint(host.length), host, Buffer.from([port >> 8, port & 255]), varint(1)]);
      s.write(pkt(0, hs)); s.write(pkt(0, Buffer.alloc(0)));
    });
    s.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      const i = buf.indexOf('{'); const j = buf.lastIndexOf('}');
      if (i >= 0 && j > i) { try { const js = JSON.parse(buf.subarray(i, j + 1).toString()); clearTimeout(t); s.destroy(); resolve(js); } catch {} }
    });
    s.on('error', () => { clearTimeout(t); resolve(null); });
  });
}

async function java(r) {
  const ad = await lanAnnounce(6000);
  result.checks.lan_announce = ad ? ad.text : null;
  log('LAN объявление:', ad);
  const st = await slp(r.java_port);
  if (!st) log('SLP не ответил');
  log('Server List Ping через relay:', st && { version: st.version, players: st.players, motd: JSON.stringify(st.description).slice(0, 80) });
  result.checks.java_status = st ? st.version.name + ' / ' + st.players.online + '/' + st.players.max : null;
  if (!process.env.MCV) {
    if (r.bedrock_port) {
      const rk = await raknet(r.bedrock_port);
      log('Geyser через relay:', rk);
      result.checks.geyser_pong = rk.pong || null;
      result.checks.geyser_reply1 = !!rk.reply1;
    }
    result.ok = !!(ad && st && (!r.bedrock_port || (result.checks.geyser_pong && result.checks.geyser_reply1)));
    return;
  }
  const mineflayer = require('mineflayer');
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: r.java_port, username: 'YompixBot', version: process.env.MCV || false, auth: 'offline' });
  const spawned = await new Promise((resolve) => {
    const t = setTimeout(() => resolve(false), 90000);
    bot.once('spawn', () => { clearTimeout(t); resolve(true); });
    bot.once('kicked', (why) => { log('kicked', JSON.stringify(why)); clearTimeout(t); resolve(false); });
    bot.once('error', (e) => { log('error', e.message); clearTimeout(t); resolve(false); });
  });
  result.checks.java_spawn = spawned;
  if (spawned) {
    log('бот зашёл в мир через relay: позиция', bot.entity.position, 'версия', bot.version);
    result.checks.java_version = bot.version;
    bot.chat('Привет из LANTERN e2e!');
    await sleep(4000);
    result.checks.java_pos = String(bot.entity.position);
    bot.quit();
    await sleep(1000);
  }
  if (r.bedrock_port) {
    const rk = await raknet(r.bedrock_port);
    log('Geyser через relay:', rk);
    result.checks.geyser_pong = rk.pong || null;
    result.checks.geyser_reply1 = !!rk.reply1;
  }
  result.ok = !!(ad && st && spawned && (!r.bedrock_port || (result.checks.geyser_pong && result.checks.geyser_reply1)));
}

async function bedrock(r) {
  const rk = await raknet(r.bedrock_port);
  log('RakNet через relay:', rk);
  result.checks.pong = rk.pong || null;
  result.checks.reply1 = !!rk.reply1;
  let spawned = false;
  try {
    const bp = require('bedrock-protocol');
    const client = bp.createClient({ host: '127.0.0.1', port: r.bedrock_port, username: 'YompixBot', offline: true,
      raknetBackend: 'jsp-raknet', version: process.env.BEV || undefined, skipPing: false, connectTimeout: 30000 });
    spawned = await new Promise((resolve) => {
      const t = setTimeout(() => resolve(false), 90000);
      client.on('spawn', () => { clearTimeout(t); resolve(true); });
      client.on('kick', (k) => { log('kick', JSON.stringify(k)); clearTimeout(t); resolve(false); });
      client.on('error', (e) => { log('error', e.message); clearTimeout(t); resolve(false); });
      client.on('close', () => { clearTimeout(t); resolve(spawned); });
    });
    log('bedrock-protocol spawn:', spawned);
    if (spawned) { await sleep(3000); client.disconnect(); await sleep(1000); }
  } catch (e) { log('bedrock-protocol недоступен:', e.message); }
  result.checks.bedrock_spawn = spawned;
  result.ok = !!(rk.pong && rk.reply1);
}

(async () => {
  try {
    const r = await waitReady();
    log('ready', r);
    if (mode === 'java') await java(r); else await bedrock(r);
  } catch (e) { result.error = String(e); log('FAIL', e); }
  fs.writeFileSync(path.join(dir, 'autotest-done.json'), JSON.stringify(result));
  log('RESULT', JSON.stringify(result));
  process.exit(result.ok ? 0 : 1);
})();
