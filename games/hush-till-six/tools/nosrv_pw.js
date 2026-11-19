// Playwright без локального сервера: страницы http://127.0.0.1:<порт>/… получают файлы игры прямо с диска (перехват запросов),
// порт никто не слушает. Подключение к любому тесту/инструменту, который делает require(process.env.PW || 'playwright'):
//   PW=$PWD/tools/nosrv_pw.js node tools/story/path_test.js
// Корень — папка games/hush-till-six (как у `python3 -m http.server 8771`), NOSRV_ROOT=… — другой.
const pw = require('/opt/node-tools/node_modules/playwright'), fs = require('fs'), path = require('path');
const ROOT = path.resolve(process.env.NOSRV_ROOT || path.join(__dirname, '..'));
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.glb': 'model/gltf-binary', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.mp4': 'video/mp4', '.wasm': 'application/wasm',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8', '.webmanifest': 'application/manifest+json', '.ktx2': 'image/ktx2' };
async function serve(route) {
  try { const u = new URL(route.request().url()); let f = path.join(ROOT, decodeURIComponent(u.pathname));
    if (!f.startsWith(ROOT)) return route.fulfill({ status: 403, body: '' });
    if (fs.existsSync(f) && fs.statSync(f).isDirectory()) f = path.join(f, 'index.html');
    if (!fs.existsSync(f)) return route.fulfill({ status: 404, body: '' });
    const buf = fs.readFileSync(f), type = MIME[path.extname(f).toLowerCase()] || 'application/octet-stream', rg = /bytes=(\d+)-(\d*)/.exec(route.request().headers().range || '');
    if (rg) { const a = +rg[1], b = rg[2] ? +rg[2] : buf.length - 1; return route.fulfill({ status: 206, body: buf.subarray(a, b + 1), contentType: type, headers: { 'content-range': `bytes ${a}-${b}/${buf.length}`, 'accept-ranges': 'bytes' } }); }
    return route.fulfill({ status: 200, body: buf, contentType: type, headers: { 'accept-ranges': 'bytes', 'cache-control': 'no-store' } });
  } catch (e) { try { await route.fulfill({ status: 500, body: String(e) }); } catch {} }
}
const RE = /^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?\//;
function patchBrowser(b) {
  const nc = b.newContext.bind(b);
  b.newContext = async o => { const c = await nc(o); await c.route(RE, serve); return c; };
  b.newPage = async o => { const c = await b.newContext(o); return c.newPage(); };
  return b;
}
const wrap = bt => new Proxy(bt, { get: (t, k) => k === 'launch' ? async o => patchBrowser(await t.launch(o)) : typeof t[k] === 'function' ? t[k].bind(t) : t[k] });
module.exports = Object.assign({}, pw, { chromium: wrap(pw.chromium), firefox: wrap(pw.firefox), webkit: wrap(pw.webkit) });
