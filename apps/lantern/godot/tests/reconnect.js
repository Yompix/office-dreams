'use strict';
// Связь приложения с relay при выкладке и плохой сети: node tests/reconnect.js [папка данных]
// Запускает relay и «сеть с характером» (TCP-прокси) между ним и приложением (Godot --headless --autotest reconnect),
// потом: перезапуск relay (SIGTERM → {t:restart} + 4012), relay лежит DOWN секунд (как выкладка на Railway), сеть «зависла»
// (сокет открыт, данных нет — как Android/NAT), медленная сеть (+300 мс) с обрывом. Меряет, когда приложение снова в сети.
// Пороги (с): MAX_BACK — вернуться после старта relay / починки сети, MAX_DEAD — заметить «зависшую» связь.
const net = require('net');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');

const HERE = path.resolve(__dirname, '..');
const RELAY = path.resolve(HERE, '../relay/server.js');
const GODOT = process.env.GODOT || '/home/user/tools/godot/godot';
const DATA = process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'lantern-reconnect-'));
const RPORT = Number(process.env.RELAY_PORT || 18961), PPORT = RPORT + 1;
const DOWN = Number(process.env.DOWN || 30) * 1000;
const MAX_BACK = Number(process.env.MAX_BACK || 7), MAX_DEAD = Number(process.env.MAX_DEAD || 16);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
fs.mkdirSync(DATA, { recursive: true });
for (const f of ['conn.log', 'stop', 'relay.db', 'relay.db-wal', 'relay.db-shm']) try { fs.unlinkSync(path.join(DATA, f)); } catch {}

// ---- relay
let relay = null;
const env = { ...process.env, PORT: String(RPORT), QUIET: '1', LANTERN_ROOM_SECRET: 'reconnect-test', LANTERN_DB_PATH: path.join(DATA, 'relay.db') };
const ONLY = (process.env.ONLY || '1234').split('');
async function startRelay() {
  relay = spawn(process.execPath, [RELAY], { env, stdio: 'ignore' });
  for (let i = 0; i < 100; i++) { try { await fetch(`http://127.0.0.1:${RPORT}/healthz`); return Date.now(); } catch { await sleep(50); } }
  throw new Error('relay did not start');
}
function stopRelay(sig = 'SIGTERM') { return new Promise((r) => { if (!relay) return r(); relay.once('exit', r); relay.kill(sig); relay = null; }); }

// ---- сеть: normal / delay (мс на каждый кусок) / hole (данные пропадают, сокеты не закрываются)
const netw = { delay: 0, hole: false };
const pairs = new Set();
const proxy = net.createServer((c) => {
  const p = { c, u: null };
  pairs.add(p);
  const done = () => { pairs.delete(p); c.destroy(); if (p.u) p.u.destroy(); };
  c.on('error', done); c.on('close', done);
  if (netw.hole) return;          // соединение «висит»: TCP принят, дальше тишина
  p.u = net.connect(RPORT, '127.0.0.1');
  p.u.on('error', done); p.u.on('close', done);
  const pipe = (from, to) => from.on('data', (d) => {
    if (netw.hole) return;
    if (netw.delay) setTimeout(() => to.writable && to.write(d), netw.delay); else to.write(d);
  });
  pipe(c, p.u); pipe(p.u, c);
});
const cutAll = () => { for (const p of [...pairs]) { p.c.destroy(); if (p.u) p.u.destroy(); } };

// ---- журнал приложения
function states() {
  try {
    return fs.readFileSync(path.join(DATA, 'conn.log'), 'utf8').trim().split('\n').filter(Boolean)
      .map((l) => { const [ms, conn, st] = l.split(' '); return { ms: Number(ms), conn, st }; });
  } catch { return []; }
}
const last = () => { const s = states(); return s[s.length - 1] || { conn: '' }; };
async function until(pred, sec) {
  const t0 = Date.now();
  while (Date.now() - t0 < sec * 1000) { const e = states().find((x) => x.ms >= t0 - 50 && pred(x)); if (e) return e; await sleep(50); }
  return null;
}
async function waitOnline(sinceMs, sec) {
  const t0 = Date.now();
  while (Date.now() - t0 < sec * 1000) {
    const s = states(); const e = s.length && s[s.length - 1].conn === 'online' && s[s.length - 1].ms >= sinceMs ? s[s.length - 1] : null;
    if (e) return (e.ms - sinceMs) / 1000;
    await sleep(50);
  }
  return Infinity;
}

const results = [];
let failed = 0;
function check(name, sec, max) {
  const ok = sec <= max;
  if (!ok) failed++;
  results.push(`${ok ? '✓' : '✗'} ${name}: ${sec === Infinity ? 'не вернулось' : sec.toFixed(1) + ' с'} (порог ${max} с)`);
  console.log(results[results.length - 1]);
}

let app = null;
process.on('exit', () => { try { relay && relay.kill('SIGKILL'); } catch {} try { app && app.kill('SIGKILL'); } catch {} });
(async () => {
  await startRelay();
  await new Promise((r) => proxy.listen(PPORT, '127.0.0.1', r));
  const shots = process.env.SHOTS ? ['--pill=1', `--shots=${process.env.SHOTS}`] : [];
  const run = process.env.SHOTS ? ['xvfb-run', ['-a', '-s', '-screen 0 1280x900x24', 'env', 'LIBGL_ALWAYS_SOFTWARE=1', GODOT, '--rendering-driver', 'opengl3', '--resolution', '412x892']]
    : [GODOT, ['--headless']];
  app = spawn(run[0], [...run[1], '--path', HERE, '--', '--autotest', 'reconnect', `--data=${DATA}`,
    `--relay=ws://127.0.0.1:${PPORT}/ws`, ...(process.env.PROBE ? [`--probe=${process.env.PROBE}`] : []), ...shots],
  { stdio: ['ignore', fs.openSync(path.join(DATA, 'app.log'), 'w'), fs.openSync(path.join(DATA, 'app.log'), 'a')] });
  let up = 0;
  const first = await waitOnline(0, 40);
  if (first === Infinity) throw new Error('приложение не вышло в сеть, см. ' + path.join(DATA, 'app.log'));
  await sleep(2000);

  // 1. перезапуск relay сразу (SIGTERM → restart + 4012 → новый процесс через 0.3 с)
  if (ONLY.includes('1')) {
  await stopRelay();
  await sleep(300);
  up = await startRelay();
  check('перезапуск relay (restart + 4012), от старта нового relay', await waitOnline(up, 60), MAX_BACK);
  await sleep(2000);

  }
  // 2. relay лежит DOWN с (выкладка на Railway): сколько ждать после того, как он поднялся
  if (ONLY.includes('2')) {
  await stopRelay();
  await sleep(DOWN);
  up = await startRelay();
  check(`relay лежал ${DOWN / 1000} с, от его старта`, await waitOnline(up, 90), MAX_BACK);
  await sleep(2000);

  }
  // 3. «зависшая» сеть: сокет открыт, данных нет; новые соединения тоже висят
  if (ONLY.includes('3')) {
  const tHole = Date.now();
  netw.hole = true;
  const dead = await until((x) => x.conn !== 'online', 90);
  check('заметить зависшую связь', dead ? (dead.ms - tHole) / 1000 : Infinity, MAX_DEAD);
  await sleep(Math.max(0, 25000 - (Date.now() - tHole)));
  netw.hole = false;
  const tHeal = Date.now();
  check('сеть ожила, от починки', await waitOnline(tHeal, 90), MAX_BACK);
  await sleep(2000);

  }
  // 4. медленная сеть (+300 мс в каждую сторону) и обрыв без закрытия relay
  if (ONLY.includes('4')) {
  netw.delay = 300;
  const tCut = Date.now();
  cutAll();
  check('медленная сеть: обрыв → снова в сети', await waitOnline(tCut, 60), MAX_BACK + 3);
  netw.delay = 0;
  }

  fs.writeFileSync(path.join(DATA, 'stop'), '1');
  await new Promise((r) => { const t = setTimeout(r, 10000); app.once('exit', () => { clearTimeout(t); r(); }); });
  app = null;
  await stopRelay();
  proxy.close();
  console.log('журнал:', path.join(DATA, 'conn.log'));
  console.log(failed ? `reconnect FAIL (${failed})` : 'reconnect OK');
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('reconnect FAIL', e); process.exit(1); });
