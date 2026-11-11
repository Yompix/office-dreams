'use strict';
// Раздача сборок LANTERN для скачивания без GitHub: GET /download (страница), GET /download/<файл>.
// Загрузка новой сборки: PUT /download/<файл> с заголовком x-upload-key. В коде хранится только SHA-256 ключа.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { DISCLAIMER_RU, DISCLAIMER_EN } = require('./legal');

const KEY_SHA256 = process.env.LANTERN_UPLOAD_SHA256 || 'd130d573526d481f35018048f98ce477ecafd828e7b9077bc03973081759ae38';
const NAME = /^[A-Za-z0-9][A-Za-z0-9._-]{0,80}\.(apk|zip|aab)$/;
const MAX = 200 * 1048576;
const TYPES = { apk: 'application/vnd.android.package-archive', zip: 'application/zip', aab: 'application/octet-stream' };

function keyOk(k) {
  const h = crypto.createHash('sha256').update(String(k || '')).digest();
  const want = Buffer.from(KEY_SHA256, 'hex');
  return want.length === 32 && crypto.timingSafeEqual(h, want);
}

function size(n) { return (n / 1048576).toFixed(0) + ' МБ'; }

function page(files, base) {
  const root = base.replace(/\/download$/, '');
  const label = (f) => f.endsWith('.apk') ? 'Android (телефон, планшет)' : /windows/i.test(f) ? 'Windows (компьютер)' : f;
  const rows = files.filter((f) => !f.name.endsWith('.aab')).map((f) =>
    `<a class="b" href="${base}/${encodeURIComponent(f.name)}"><b>${label(f.name)}</b><span>${f.name} · ${size(f.size)}</span></a>`).join('');
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LANTERN — скачать</title><style>
body{margin:0;background:#14110d;color:#f3eadb;font:16px/1.5 system-ui,sans-serif;display:flex;justify-content:center}
main{max-width:480px;width:100%;padding:32px 16px}h1{margin:0 0 4px;font-size:28px;color:#ffb648}p{color:#b9ab95;margin:0 0 24px}
.b{display:block;background:#241e17;border:1px solid #3a2f22;border-radius:16px;padding:16px;margin:0 0 12px;color:inherit;text-decoration:none}
.b b{display:block;font-size:18px}.b span{color:#b9ab95;font-size:14px}.n{font-size:14px;color:#8d806c;margin-top:24px}
</style></head><body><main><h1>LANTERN</h1><p>Игра с друзьями по коду комнаты — для Minecraft: Java Edition и Bedrock Edition.</p>${rows || '<p>Сборок пока нет.</p>'}
<div class="n"><b>Android:</b> откройте скачанный файл и разрешите установку из этого источника. Если Play Защита пишет «неизвестное приложение» — нажмите «Подробнее» → «Всё равно установить»: так телефон отвечает на любое приложение не из Google Play.<br><br><b>Windows:</b> распакуйте архив и запустите LANTERN.exe. Если появилось «Windows защитила компьютер» — «Подробнее» → «Выполнить в любом случае»: программа пока без платной подписи.<br><br><a style="color:#ffb648" href="${root}/privacy">Конфиденциальность</a> · <a style="color:#ffb648" href="${root}/terms">Условия</a> · <a style="color:#ffb648" href="${root}/delete">Удалить профиль</a><br><br>${DISCLAIMER_RU}<br>${DISCLAIMER_EN}</div></main></body></html>`;
}

class Downloads {
  constructor(dir) {
    this.dir = dir || '';
    if (this.dir) fs.mkdirSync(this.dir, { recursive: true });
  }
  list() {
    if (!this.dir) return [];
    return fs.readdirSync(this.dir).filter((f) => NAME.test(f))
      .map((f) => ({ name: f, size: fs.statSync(path.join(this.dir, f)).size, mtime: fs.statSync(path.join(this.dir, f)).mtimeMs }))
      .sort((a, b) => b.mtime - a.mtime);
  }
  // url — без префикса /lantern; base — как этот путь виден снаружи (для ссылок на странице)
  http(req, res, url, base) {
    const u = new URL(url, 'http://x');
    if (u.pathname !== '/download' && u.pathname !== '/download/' && !u.pathname.startsWith('/download/')) return false;
    const name = decodeURIComponent(u.pathname.slice('/download/'.length));
    if (!this.dir) { res.writeHead(503); res.end('downloads off'); req.resume(); return true; }
    if (!name) {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache' });
      res.end(page(this.list(), base + '/download'));
      return true;
    }
    if (!NAME.test(name)) { res.writeHead(404); res.end(); req.resume(); return true; }
    const file = path.join(this.dir, name);
    if (req.method === 'GET' || req.method === 'HEAD') {
      let st; try { st = fs.statSync(file); } catch { res.writeHead(404); res.end('not found'); return true; }
      res.writeHead(200, { 'content-type': TYPES[name.split('.').pop()], 'content-length': st.size,
        'content-disposition': `attachment; filename="${name}"`, 'cache-control': 'no-cache' });
      if (req.method === 'HEAD') { res.end(); return true; }
      fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
      return true;
    }
    if (req.method === 'PUT' || req.method === 'DELETE') {
      if (!keyOk(req.headers['x-upload-key'])) { res.writeHead(403); res.end('forbidden'); req.resume(); return true; }
      if (req.method === 'DELETE') { fs.rmSync(file, { force: true }); res.writeHead(200); res.end('deleted'); return true; }
      const tmp = file + '.part';
      const out = fs.createWriteStream(tmp);
      let n = 0, failed = false;
      req.on('data', (c) => { n += c.length; if (n > MAX && !failed) { failed = true; out.destroy(); fs.rmSync(tmp, { force: true }); res.writeHead(413); res.end('too big'); req.destroy(); } });
      req.pipe(out);
      out.on('finish', () => { if (failed) return; fs.renameSync(tmp, file); res.writeHead(200, { 'content-type': 'application/json' }); res.end(JSON.stringify({ ok: true, name, size: n })); });
      out.on('error', () => { if (!failed) { failed = true; fs.rmSync(tmp, { force: true }); res.writeHead(500); res.end('write error'); } });
      return true;
    }
    res.writeHead(405); res.end(); req.resume();
    return true;
  }
}

module.exports = { Downloads, keyOk };
