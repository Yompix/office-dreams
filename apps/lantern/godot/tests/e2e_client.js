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
      // байт длины строки тоже может оказаться «{» (JSON ровно 123 байта) — пробуем с каждой «{»
      const j = buf.lastIndexOf('}');
      for (let i = buf.indexOf('{'); i >= 0 && i < j; i = buf.indexOf('{', i + 1)) {
        try { const js = JSON.parse(buf.subarray(i, j + 1).toString()); clearTimeout(t); s.destroy(); resolve(js); return; } catch {}
      }
    });
    s.on('error', () => { clearTimeout(t); resolve(null); });
  });
}

// Настоящий клиент Bedrock (gophertunnel, вход по RakNet как телефон из вкладки «Друзья») — если задан NNCLIENT
function bedrockSpawn(port, name, how = 'rak') {
  if (!process.env.NNCLIENT) return Promise.resolve(null);
  const { execFile } = require('child_process');
  return new Promise((resolve) => {
    execFile(process.env.NNCLIENT, [how, '127.0.0.1:' + port, name], { timeout: 70000 }, (err, out, errOut) => {
      log('Bedrock-клиент (' + how + '):', out.trim(), String(errOut).split('\n').filter((l) => /level=(WARN|ERROR)/.test(l)).slice(-5).join('\n'));
      // HTTP-вход Geyser без настоящего Xbox-токена отвечает 401 — значит, запрос дошёл до Geyser через мост
      resolve(/RESULT spawned/.test(out) ? true : (how === 'http' && / 401 /.test(out) ? 'geyser-401' : false));
    });
  });
}

async function javaBot(port, version) {
  const mineflayer = require('mineflayer');
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'Bot' + String(version || 'new').replace(/\./g, '_').slice(0, 10), version: version || false, auth: 'offline' });
  const spawned = await new Promise((resolve) => {
    const t = setTimeout(() => resolve(false), 90000);
    bot.once('spawn', () => { clearTimeout(t); resolve(true); });
    bot.once('kicked', (why) => { log('kicked', version, JSON.stringify(why)); clearTimeout(t); resolve(false); });
    bot.once('error', (e) => { log('error', version, e.message); clearTimeout(t); resolve(false); });
  });
  if (spawned) {
    log('бот', version, 'зашёл в мир через relay: позиция', bot.entity.position, 'версия', bot.version);
    bot.chat('Привет из LANTERN e2e! ' + bot.version);
    await sleep(3000);
    bot.quit();
    await sleep(1500);
  } else { try { bot.end(); } catch {} }
  return spawned;
}

// 1.6: долгая игра (LONGPLAY секунд) — ходьба, прыжки, ломать/ставить блоки, чат; все версии одновременно.
// RELAY_RESTART=1 — посреди игры перезапускаем relay (как при выкладке на Railway): игра не должна оборваться.
function restartRelay() {
  const { spawn } = require('child_process');
  const pidFile = path.join(dir, 'relay.pid');
  const pid = Number(fs.readFileSync(pidFile, 'utf8'));
  log('перезапуск relay (SIGTERM', pid + ')');
  try { process.kill(pid, 'SIGTERM'); } catch (e) { log('kill', e.message); }
  setTimeout(() => {
    const out = fs.openSync(path.join(dir, 'relay2.log'), 'a');
    const p = spawn(process.execPath, [path.join(process.env.RELAY_DIR, 'server.js')], { env: process.env, stdio: ['ignore', out, out], detached: true });
    fs.writeFileSync(pidFile, String(p.pid));
    p.unref();
    log('relay снова запущен', p.pid);
  }, Number(process.env.RELAY_DOWN_MS || 1500));
}

async function longPlay(port, version, secs) {
  const mineflayer = require('mineflayer');
  const name = ('L' + String(version || 'new').replace(/\./g, '_')).slice(0, 12);
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version: version || false, auth: 'offline' });
  let ended = null;
  bot.on('kicked', (why) => { ended = ended || 'kicked ' + JSON.stringify(why).slice(0, 300); });
  bot.on('end', (why) => { ended = ended || 'end ' + why; });
  bot.on('error', (e) => { ended = ended || 'error ' + e.message; });
  const spawned = await new Promise((resolve) => {
    const t = setTimeout(() => resolve(false), 120000);
    bot.once('spawn', () => { clearTimeout(t); resolve(true); });
    bot.once('end', () => { clearTimeout(t); resolve(false); });
  });
  if (!spawned) return { ok: false, why: ended || 'no spawn' };
  let Item = null;
  try { Item = require('prismarine-item')(bot.registry || bot.version); } catch {}
  const t0 = Date.now();
  let i = 0, dug = 0, placed = 0, chats = 0;
  const dirs = ['forward', 'left', 'back', 'right'];
  while (Date.now() - t0 < secs * 1000 && !ended) {
    i++;
    bot.setControlState(dirs[i % 4], true);
    if (i % 3 === 0) bot.setControlState('jump', true);
    await sleep(1200);
    bot.clearControlStates();
    try {
      const b = bot.blockAt(bot.entity.position.offset(1, -1, 0).floored());
      if (!process.env.NOBUILD && b && b.name !== 'air' && b.name !== 'bedrock' && bot.canDigBlock(b)) { await Promise.race([bot.dig(b, true), sleep(5000)]); dug++; }
    } catch (e) { /* блок мог исчезнуть */ }
    try {
      if (!process.env.NOBUILD && Item && bot.creative && bot.registry && bot.registry.itemsByName.dirt) {
        await Promise.race([bot.creative.setInventorySlot(36, new Item(bot.registry.itemsByName.dirt.id, 1)), sleep(3000)]);
        const below = bot.blockAt(bot.entity.position.offset(1, -2, 0).floored());
        if (below && below.name !== 'air') { await Promise.race([bot.placeBlock(below, new (require('vec3').Vec3)(0, 1, 0)), sleep(3000)]); placed++; }
      }
    } catch (e) { /* не страшно: ставить блок рядом может мешать сам бот */ }
    if (i % 8 === 0) { bot.chat('LANTERN ' + bot.version + ' #' + i); chats++; }
    await sleep(400);
  }
  const res = { ok: !ended, secs: Math.round((Date.now() - t0) / 1000), steps: i, dug, placed, chats, version: bot.version, why: ended };
  if (!ended) { bot.quit(); await sleep(1000); }
  return res;
}

// 1.6: Java 1.7.10 (протокол 5) — сервер должен вежливо отказать сообщением «выберите 1.8.9 или новее»
function oldClient(port) {
  const net = require('net');
  const varint = (n) => { const b = []; do { let x = n & 0x7f; n >>>= 7; if (n) x |= 0x80; b.push(x); } while (n); return Buffer.from(b); };
  const str = (t) => { const b = Buffer.from(t); return Buffer.concat([varint(b.length), b]); };
  const pkt = (id, body) => { const d = Buffer.concat([varint(id), body]); return Buffer.concat([varint(d.length), d]); };
  return new Promise((resolve) => {
    const s = net.connect(port, '127.0.0.1');
    let buf = Buffer.alloc(0);
    const t = setTimeout(() => { s.destroy(); resolve(buf.toString('utf8')); }, 15000);
    s.on('connect', () => {
      s.write(pkt(0, Buffer.concat([varint(5), str('127.0.0.1'), Buffer.from([port >> 8, port & 255]), varint(2)])));
      s.write(pkt(0, str('Old1710')));
    });
    s.on('data', (d) => { buf = Buffer.concat([buf, d]); });
    s.on('close', () => { clearTimeout(t); resolve(buf.toString('utf8')); });
    s.on('error', () => {});
  });
}

async function java(r) {
  if (process.env.CHECK_OLD) {
    const msg = await oldClient(r.java_port);
    log('Java 1.7.10:', msg.slice(0, 200));
    result.checks.old_1_7_blocked = /1\.8\.9/.test(msg);
  }
  if (process.env.LONGPLAY) {
    const secs = Number(process.env.LONGPLAY);
    const vs = (process.env.MCV || 'auto').split(',').filter(Boolean);
    if (process.env.RELAY_RESTART) setTimeout(restartRelay, Math.round(secs * 1000 / 3) + 30000);
    const res = await Promise.all(vs.map((v, k) => sleep(k * 4000).then(() => longPlay(r.java_port, v === 'auto' ? false : v, secs))));
    vs.forEach((v, k) => { result.checks['long_' + v] = res[k]; log('долгая игра', v, JSON.stringify(res[k])); });
    result.ok = res.every((x) => x.ok) && result.checks.old_1_7_blocked !== false;
    return;
  }
  const ad = await lanAnnounce(6000);
  result.checks.lan_announce = ad ? ad.text : null;
  log('LAN объявление:', ad);
  const st = await slp(r.java_port);
  if (!st) log('SLP не ответил');
  log('Server List Ping через relay:', st && { version: st.version, players: st.players, motd: JSON.stringify(st.description).slice(0, 80) });
  result.checks.java_status = st ? st.version.name + ' / ' + st.players.online + '/' + st.players.max : null;
  let spawned = true;
  // MCV — версии Java-клиентов через запятую (например «1.20.1,1.12.2»); «auto» — версия сервера
  for (const v of (process.env.MCV || '').split(',').filter(Boolean)) {
    const ok = await javaBot(r.java_port, v === 'auto' ? false : v);
    result.checks['java_spawn_' + v] = ok;
    spawned = spawned && ok;
  }
  if (r.bedrock_port) {
    const rk = await raknet(r.bedrock_port);
    log('Geyser через relay:', rk);
    result.checks.geyser_pong = rk.pong || null;
    result.checks.geyser_reply1 = !!rk.reply1;
    const bs = await bedrockSpawn(r.bedrock_port, 'PhoneBot');
    if (bs !== null) result.checks.bedrock_spawn = bs;
    const bh = await bedrockSpawn(r.bedrock_port, 'PhoneHttp', 'http');
    if (bh !== null) result.checks.bedrock_http_spawn = bh;
  }
  result.ok = !!(ad && st && spawned && result.checks.bedrock_http_spawn !== false && result.checks.bedrock_spawn !== false && (!r.bedrock_port || (result.checks.geyser_pong && result.checks.geyser_reply1)));
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
