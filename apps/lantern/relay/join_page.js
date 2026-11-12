'use strict';
// 1.7: одна ссылка на игру — GET /j/<код или номер комнаты>. Её видно как ссылку в любом мессенджере (lantern:// — нет).
// Страница: название игры, хозяин, игроков x/y, «Открыть в LANTERN» (Android — intent://, ПК — lantern://join?code=),
// «Скачать LANTERN». Друг: нажал ссылку → LANTERN → Minecraft → в мире.
const { DISCLAIMER_RU, DISCLAIMER_EN } = require('./legal');

const PKG = 'com.yompix.lantern';
const MODE_RU = { survival: 'Выживание', creative: 'Творчество', minigame: 'Мини-игра', pvp: 'PvP', roleplay: 'Ролевая', other: '' };
const EDITION_RU = { java: 'Java (ПК)', bedrock: 'Телефоны, Windows, консоли', both: 'Для всех устройств' };
// палитры обложки по режиму (как в приложении: scripts/ui/cover.gd)
const PALETTE = { survival: ['#0b2a33', '#1f7a6b', '#7ee0b0'], creative: ['#1d1440', '#7a4bd6', '#ff9ad1'], pvp: ['#2a0f14', '#c8452c', '#ffb36b'],
  minigame: ['#0d2440', '#1fa6c9', '#ffe066'], roleplay: ['#0d1440', '#3d56c9', '#9db4ff'], other: ['#161a3a', '#4a4f9c', '#ffb648'] };

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** Код из пути: 6 знаков (или свой код Pro 3–8) либо номер из 8 цифр; иначе ''. */
function codeFromPath(p) {
  const m = /^\/j\/([A-Za-z0-9-]{3,9})\/?$/.exec(p);
  if (!m) return '';
  const c = m[1].toUpperCase().replace(/-/g, '');
  return /^[0-9A-Z]{3,8}$/.test(c) ? c : '';
}

function info(room, social) {
  if (!room || room.closed) return null;
  let owner = null;
  try { owner = room.ownerId && social ? social.pub(social.user(room.ownerId)) : null; } catch { owner = null; }
  const cap = room.cap || (room.meta && room.meta.slots) || 10;
  return { code: room.code, num: room.num || '', title: (room.meta && room.meta.title) || room.name || 'Мир', host: owner ? owner.nick : '',
    players: room.joiners.size + 1, cap, mode: room.mode || 'other', edition: room.edition, locked: !!room.passHash, online: !!room.host };
}

function cover(mode, seed) {
  const [a, b, c] = PALETTE[mode] || PALETTE.other;
  let h = 0; for (const ch of String(seed)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const r = () => { h = (h * 1664525 + 1013904223) >>> 0; return h / 4294967296; };
  const hill = (base, amp, col, op) => {
    let d = `M0 200 L0 ${base}`;
    for (let x = 0; x <= 400; x += 50) d += ` Q${x + 25} ${Math.round(base - amp * (0.3 + r()))} ${x + 50} ${Math.round(base - amp * 0.4 * r())}`;
    return `<path d="${d} L400 200Z" fill="${col}" opacity="${op}"/>`;
  };
  const sx = 60 + Math.round(r() * 280), sy = 40 + Math.round(r() * 40);
  let stars = '';
  for (let i = 0; i < 18; i++) stars += `<circle cx="${Math.round(r() * 400)}" cy="${Math.round(r() * 90)}" r="${(0.6 + r()).toFixed(1)}" fill="#fff" opacity="${(0.3 + r() * 0.5).toFixed(2)}"/>`;
  return `<svg viewBox="0 0 400 200" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><defs><linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient><radialGradient id="g"><stop offset="0" stop-color="${c}" stop-opacity=".9"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient></defs><rect width="400" height="200" fill="url(#s)"/>${stars}<circle cx="${sx}" cy="${sy}" r="60" fill="url(#g)" opacity=".5"/><circle cx="${sx}" cy="${sy}" r="15" fill="${c}"/>${hill(120, 50, c, 0.25)}${hill(145, 45, b, 0.9)}${hill(170, 35, a, 1)}</svg>`;
}

function page(code, inf, base) {
  const dl = base + '/download';
  const pretty = /^\d{8}$/.test(code) ? code : (code.length === 6 ? code.slice(0, 3) + '-' + code.slice(3) : code);
  const app = `lantern://join?code=${encodeURIComponent(code)}`;
  const intent = `intent://join?code=${encodeURIComponent(code)}#Intent;scheme=lantern;package=${PKG};S.browser_fallback_url=${encodeURIComponent(dl)};end`;
  const title = inf ? inf.title : 'Игра в LANTERN';
  const ogt = inf ? `Заходи в игру «${inf.title}»` : 'Заходи играть через LANTERN';
  const mode = inf ? MODE_RU[inf.mode] || '' : '';
  const meta = inf ? [mode, EDITION_RU[inf.edition] || '', inf.locked ? 'с паролем' : ''].filter(Boolean) : [];
  const state = !inf ? '<div class="st off">Игра сейчас закрыта — попросите друга открыть её снова</div>'
    : inf.online ? `<div class="st"><i></i>Идёт игра · ${inf.players}/${inf.cap}</div>` : '<div class="st wait"><i></i>Хозяин переподключается…</div>';
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#0b0d1c"><meta name="robots" content="noindex">
<title>${esc(ogt)} — LANTERN</title>
<meta property="og:title" content="${esc(ogt)}"><meta property="og:description" content="Нажми — LANTERN откроет Minecraft и заведёт в мир. Код ${esc(pretty)}."><meta property="og:site_name" content="LANTERN">
<style>
*{box-sizing:border-box}body{margin:0;min-height:100vh;color:#f4f2ee;font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;display:flex;justify-content:center;
background:radial-gradient(90% 60% at 10% 0%,rgba(255,182,72,.22),transparent 60%),radial-gradient(80% 50% at 100% 30%,rgba(110,90,255,.18),transparent 70%),linear-gradient(#141737,#07080f 70%)}
main{width:100%;max-width:440px;padding:max(20px,env(safe-area-inset-top)) 16px 28px}
.brand{display:flex;align-items:center;gap:10px;font-weight:800;letter-spacing:.06em;font-size:15px;margin:4px 4px 16px;color:#ffd9a0}
.brand b{width:28px;height:28px;border-radius:9px;background:linear-gradient(135deg,#ffcf70,#ff8a3d);box-shadow:0 0 24px rgba(255,170,70,.7);display:block}
.card{border-radius:28px;overflow:hidden;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.14);box-shadow:0 24px 60px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.12)}
.cov{height:190px;position:relative}.cov svg{position:absolute;inset:0;width:100%;height:100%}
.cov:after{content:"";position:absolute;inset:0;background:linear-gradient(transparent 45%,rgba(10,11,26,.85))}
.num{position:absolute;left:16px;bottom:12px;z-index:1;font:600 13px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.12em;color:#fff;background:rgba(0,0,0,.35);padding:4px 10px;border-radius:99px}
.in{padding:18px 20px 22px}h1{margin:0 0 4px;font-size:30px;line-height:1.1;letter-spacing:-.02em;font-weight:800;overflow-wrap:anywhere}
.by{color:#b3b4c8;margin:0 0 12px}.pills{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 14px}.pills span{font-size:12px;font-weight:700;padding:4px 10px;border-radius:99px;background:rgba(255,255,255,.1);color:#e6e3f5}
.st{display:flex;align-items:center;gap:8px;font-weight:700;color:#8ff0b5;margin:0 0 18px}.st i{width:9px;height:9px;border-radius:9px;background:#5fd38a;box-shadow:0 0 12px #5fd38a}
.st.wait{color:#ffd27a}.st.wait i{background:#ffc857;box-shadow:0 0 12px #ffc857}.st.off{color:#ffb3b3;font-weight:600}
a.btn{display:flex;align-items:center;justify-content:center;height:58px;border-radius:20px;font-weight:800;font-size:18px;text-decoration:none;color:#1a1206;
background:linear-gradient(100deg,#ffcf70,#ffb648 40%,#ff8a3d);box-shadow:0 10px 34px rgba(255,150,60,.45),inset 0 1px 0 rgba(255,255,255,.5)}
a.sec{display:flex;align-items:center;justify-content:center;height:50px;margin-top:10px;border-radius:18px;font-weight:700;text-decoration:none;color:#f4f2ee;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.14)}
.how{margin:18px 6px 0;color:#b3b4c8;font-size:14px}.how b{color:#f4f2ee}
.d{margin:22px 6px 0;color:#7d7f99;font-size:11px;line-height:1.5}
</style></head><body><main>
<div class="brand"><b></b>LANTERN</div>
<div class="card"><div class="cov">${cover(inf ? inf.mode : 'other', code)}<div class="num">${/^\d{8}$/.test(code) ? '№ ' : 'код '}${esc(pretty)}</div></div>
<div class="in"><h1>${esc(title)}</h1>${inf && inf.host ? `<p class="by">Хозяин: ${esc(inf.host)}</p>` : '<p class="by">Игра с друзьями в Minecraft</p>'}
${meta.length ? `<div class="pills">${meta.map((m) => `<span>${esc(m)}</span>`).join('')}</div>` : ''}${state}
<a class="btn" id="open" href="${esc(app)}">Открыть в LANTERN</a>
<a class="sec" href="${esc(dl)}">Скачать LANTERN</a></div></div>
<p class="how"><b>Как это работает:</b> нажмите «Открыть в LANTERN» — приложение само запустит Minecraft и заведёт вас в мир. Нет приложения — сначала скачайте его.</p>
<p class="d">${DISCLAIMER_RU}<br>${DISCLAIMER_EN}<br>Minecraft — товарный знак Mojang Synergies AB.</p>
</main><script>
(function(){var a=document.getElementById('open');if(/Android/i.test(navigator.userAgent)){a.href=${JSON.stringify(intent)};}
a.addEventListener('click',function(){setTimeout(function(){if(!document.hidden&&!/Android/i.test(navigator.userAgent))location.href=${JSON.stringify(dl)};},2500);});})();
</script></body></html>`;
}

/** url — без префикса /lantern; find(code) → комната или undefined. true — запрос обработан. */
function http(req, res, url, base, find, social) {
  const u = new URL(url, 'http://x');
  if (!u.pathname.startsWith('/j/')) return false;
  const code = codeFromPath(u.pathname);
  if (!code || (req.method !== 'GET' && req.method !== 'HEAD')) { res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' }); res.end('Нет такой игры'); return true; }
  const body = page(code, info(find(code), social), base);
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', 'referrer-policy': 'no-referrer' });
  res.end(req.method === 'HEAD' ? undefined : body);
  return true;
}

module.exports = { http, page, info, codeFromPath, esc };
