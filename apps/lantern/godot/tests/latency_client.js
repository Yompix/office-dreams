// Замер добавленной задержки и скорости моста LANTERN (tests/latency.sh).
// node tests/latency_client.js <tcp через мост> <udp через мост> <tcp напрямую> <udp напрямую> [N=1000] [МБ=40]
const net = require('net'), dgram = require('dgram');
const [VT, VU, DT, DU] = process.argv.slice(2, 6).map(Number);
const N = +(process.argv[6] || 1000), MB = +(process.argv[7] || 40);
const now = () => Number(process.hrtime.bigint()) / 1e6;
const pct = (a, p) => a[Math.min(a.length - 1, Math.floor(a.length * p))];
const stat = (a) => { a.sort((x, y) => x - y); return { p50: pct(a, 0.5), p95: pct(a, 0.95), p99: pct(a, 0.99), max: a[a.length - 1] }; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function tcpRtt(port, n) {
  return new Promise((res, rej) => {
    const c = net.connect(port, '127.0.0.1'); c.setNoDelay(true);
    const out = []; let t0 = 0, got = 0, i = 0;
    const payload = Buffer.alloc(64, 7);
    const send = () => { got = 0; t0 = now(); c.write(payload); };
    c.on('connect', () => setTimeout(send, 300));
    c.on('data', (d) => { got += d.length; if (got >= payload.length) { out.push(now() - t0); if (++i >= n) { c.destroy(); res(out); } else setTimeout(send, 2); } });
    c.on('error', rej);
    setTimeout(() => rej(new Error('tcp timeout ' + port + ' after ' + i)), 60000).unref();
  });
}
function udpRtt(port, n) {
  return new Promise((res, rej) => {
    const u = dgram.createSocket('udp4'); const out = []; let t0 = 0, i = 0, timer = null, lost = 0;
    const payload = Buffer.alloc(64, 0x84);   // 0x84 — «данные RakNet»: мост пересылает как есть
    const send = () => { t0 = now(); u.send(payload, port, '127.0.0.1'); timer = setTimeout(() => { lost++; if (lost > 50) { u.close(); rej(new Error('udp lost ' + port)); } else send(); }, 500); };
    u.on('message', () => { clearTimeout(timer); out.push(now() - t0); if (++i >= n) { u.close(); res({ out, lost }); } else setTimeout(send, 2); });
    u.bind(0, () => setTimeout(send, 300));
  });
}
function blast(port, mb) {
  return new Promise((res, rej) => {
    const c = net.connect(port, '127.0.0.1'); let got = 0, t0 = 0, last = 0, stall = 0; const want = mb * 1024 * 1024;
    c.on('connect', () => { t0 = last = now(); c.write('BLAST ' + mb + '\n'); });
    c.on('data', (d) => { const t = now(); if (t - last > stall) stall = t - last; last = t; got += d.length; if (got >= want) { c.destroy(); res({ mbps: mb / ((t - t0) / 1000), stall }); } });
    c.on('error', rej);
    setTimeout(() => rej(new Error('blast timeout, got ' + got)), 120000).unref();
  });
}
(async () => {
  const r = {};
  const dt = stat(await tcpRtt(DT, N)), vt = stat(await tcpRtt(VT, N));
  const du = await udpRtt(DU, N), vu = await udpRtt(VU, N);
  const dus = stat(du.out), vus = stat(vu.out);
  const add = (v, d) => ({ p50: +(v.p50 - d.p50).toFixed(2), p95: +(v.p95 - d.p95).toFixed(2), p99: +(v.p99 - d.p99).toFixed(2), max: +(v.max - d.max).toFixed(2) });
  r.tcp_added_ms = add(vt, dt); r.udp_added_ms = add(vus, dus); r.udp_lost = vu.lost;
  r.tcp_direct_ms = { p50: +dt.p50.toFixed(2), p99: +dt.p99.toFixed(2) };
  await sleep(300);
  const b = await blast(VT, MB);
  r.throughput_mb_s = +b.mbps.toFixed(1); r.longest_stall_ms = +b.stall.toFixed(0);
  r.ok = r.tcp_added_ms.p50 < 2 && r.tcp_added_ms.p99 < 10 && r.udp_added_ms.p50 < 2 && r.udp_added_ms.p99 < 10 && r.throughput_mb_s >= 5;
  console.log(JSON.stringify(r));
  process.exit(r.ok || process.env.LAT_NOFAIL ? 0 : 1);
})().catch((e) => { console.log(JSON.stringify({ ok: false, error: String(e.message || e) })); process.exit(1); });
