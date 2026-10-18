'use strict';
/* Отдельный маленький сайт с нашими играми (короткий адрес на Railway): раздаёт public/games/ с корня.
   Запуск: node tools/games-site.js (порт из PORT, по умолчанию 8080). Без базы и бота. */
const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', 'public', 'games');
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json',
  '.png': 'image/png', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.mp3': 'audio/mpeg', '.mp4': 'video/mp4',
  '.apk': 'application/vnd.android.package-archive' };
const H = { 'x-content-type-options': 'nosniff', 'referrer-policy': 'no-referrer' };
function serve(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405, H); return res.end(); }
  let p; try { p = decodeURIComponent(new URL(req.url, 'http://x').pathname); } catch { res.writeHead(400, H); return res.end(); }
  if (p === '/healthz') { res.writeHead(200, { 'content-type': 'text/plain', ...H }); return res.end('ok'); }
  if (p.startsWith('/games/')) p = p.slice(6);          // старые ссылки вида /games/nightshift/
  let rel = p.slice(1); if (rel === '' || rel.endsWith('/')) rel += 'index.html';
  const file = path.join(ROOT, path.normalize(rel));
  const nf = () => { res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8', ...H }); res.end('Not found'); };
  if (!file.startsWith(ROOT + path.sep) || !/^[\w.\-/]+$/.test(rel)) return nf();
  if (!path.extname(rel) && fs.existsSync(path.join(file, 'index.html'))) { res.writeHead(301, { location: p + '/', ...H }); return res.end(); }
  const type = MIME[path.extname(file).toLowerCase()]; let st; try { st = type && fs.statSync(file); } catch { st = null; }
  if (!st || !st.isFile()) return nf();
  res.writeHead(200, { 'content-type': type, 'content-length': st.size, 'cache-control': /^(text\/html|text\/javascript|application\/json)/.test(type) ? 'no-cache' : 'public, max-age=3600', ...H });
  if (req.method === 'HEAD') return res.end();
  fs.createReadStream(file).pipe(res);
}
if (require.main === module) http.createServer(serve).listen(+process.env.PORT || 8080, '0.0.0.0', () => console.log('games site on', process.env.PORT || 8080));
module.exports = { serve };
