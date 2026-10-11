'use strict';
/* Admin panel: /admin (page) + /admin/api/* (protected by the ADMIN_KEY header). */
const crypto = require('crypto');
const U = require('./users.js');
const Eco = require('./economy.js');
const AB = require('./antibot.js');

const json = (res, status, obj) => {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
  res.end(JSON.stringify(obj));
};
function safeEq(a, b) {
  const x = crypto.createHash('sha256').update(String(a)).digest();
  const y = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(x, y);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let s = '';
    req.on('data', (c) => { s += c; if (s.length > 20000) { reject(new Error('large')); req.destroy(); } });
    req.on('end', () => resolve(s));
    req.on('error', reject);
  });
}
const csvCell = (v) => {
  const s = v == null ? '' : String(v);
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
};

function stats(ctx, price) {
  const { db } = ctx;
  const now = Date.now();
  const dk = U.dayKey(now);
  const one = (sql, ...p) => db.get(sql, ...p);
  const risk = {};
  for (const r of db.all('SELECT risk_state AS s, COUNT(*) AS n FROM users WHERE deleted=0 GROUP BY risk_state')) risk[r.s] = r.n;
  const revRows = db.all('SELECT * FROM revenue ORDER BY day DESC LIMIT 14');
  const y = revRows.find((r) => r.day === dk - 1);
  const pie = Eco.pieFor(ctx, 0, y ? y.usd : 0, price || 0);
  return {
    users: one('SELECT COUNT(*) AS n FROM users WHERE deleted=0').n,
    dau: one('SELECT COUNT(*) AS n FROM users WHERE last_seen>?', now - 86400e3).n,
    wau: one('SELECT COUNT(*) AS n FROM users WHERE last_seen>?', now - 7 * 86400e3).n,
    newToday: one('SELECT COUNT(*) AS n FROM users WHERE created_at>=?', dk * U.DAY).n,
    ptsTotal: Eco.r1(one('SELECT COALESCE(SUM(pts),0) AS s FROM users').s),
    ptsToday: Eco.r1(one('SELECT COALESCE(SUM(pts),0) AS s FROM ledger WHERE day=?', dk).s),
    risk,
    refs: { total: one('SELECT COUNT(*) AS n FROM users WHERE ref1 IS NOT NULL').n, active: one('SELECT COUNT(*) AS n FROM users WHERE ref_active=1').n },
    wallets: { bound: one('SELECT COUNT(*) AS n FROM users WHERE wallet IS NOT NULL').n, verified: one('SELECT COUNT(*) AS n FROM users WHERE wallet_ok=1').n },
    revenue: revRows.map((r) => ({ day: r.day, date: new Date(r.day * U.DAY).toISOString().slice(0, 10), stars: r.stars, usd: Math.round(r.usd * 100) / 100, n: r.n })),
    pie: { price: price || 0, yesterdayUsd: y ? y.usd : 0, ...pie },
    top: db.all('SELECT id,name,username,country,pts,ref_pts,risk_state,risk,wallet FROM users WHERE deleted=0 ORDER BY pts DESC LIMIT 20'),
    dist: [1, 10, 50, 200, 1000].map((t) => ({ over: t, n: one('SELECT COUNT(*) AS n FROM users WHERE pts>=? AND deleted=0', t).n })),
    countries: db.all(`SELECT COALESCE(country,'?') AS c, COUNT(*) AS n,
        SUM(CASE WHEN last_seen>? THEN 1 ELSE 0 END) AS d1, SUM(CASE WHEN last_seen>? THEN 1 ELSE 0 END) AS d7,
        SUM(CASE WHEN created_at>=? THEN 1 ELSE 0 END) AS today, SUM(CASE WHEN id IN (SELECT DISTINCT uid FROM purchases) THEN 1 ELSE 0 END) AS payers,
        ROUND(AVG(pts),1) AS avgPts
      FROM users WHERE deleted=0 GROUP BY COALESCE(country,'?') ORDER BY n DESC LIMIT 100`, now - 86400e3, now - 7 * 86400e3, dk * U.DAY),
  };
}
function userDetail(ctx, id) {
  const u = U.getUser(ctx, id);
  if (!u) return null;
  const { db } = ctx;
  return {
    user: { ...u, act: undefined, challenge: undefined },
    events: db.all('SELECT ts,kind,score,meta FROM risk_events WHERE uid=? ORDER BY id DESC LIMIT 30', id),
    ledger: db.all('SELECT day,kind,ref,raw,pts,n FROM ledger WHERE uid=? ORDER BY day DESC LIMIT 60', id),
    beh: db.all('SELECT day,hours,taps,n,mean,m2,syncs,actions,secs FROM beh WHERE uid=? ORDER BY day DESC LIMIT 7', id),
    signals: AB.signalsFor(ctx, u, Date.now()),
    friends: Eco.friendCounts(ctx, id),
    fps: db.all('SELECT kind,hash,ts FROM fps WHERE uid=?', id),
  };
}


/* ---- ready-made channel posts (HTML, Telegram subset) ---- */
const DRAFTS = [
  { id: 'launch_ru', title: 'Запуск (RU)', text: '🏢 <b>Office Dreams запущена!</b>\n\nПострой свою неоновую игровую студию прямо в Telegram:\n🎮 выпускай игры и хиты\n👥 нанимай команду\n🏙 открывай этажи — от подвала до офиса CEO\n\n💎 За реальный прогресс начисляются <b>очки аирдропа</b> — в будущем они обменяются на токен <b>OFFICE</b> в сети TON.\n\n🚀 Сезон 0 уже начался — чем раньше начнёшь, тем больше успеешь!' },
  { id: 'launch_uk', title: 'Запуск (UK)', text: '🏢 <b>Office Dreams запущено!</b>\n\nПобудуй свою неонову ігрову студію просто в Telegram:\n🎮 випускай ігри й хіти\n👥 наймай команду\n🏙 відкривай поверхи — від підвалу до офісу CEO\n\n💎 За справжній прогрес нараховуються <b>бали аірдропу</b> — у майбутньому вони обміняються на токен <b>OFFICE</b> у мережі TON.\n\n🚀 Сезон 0 уже почався — що раніше почнеш, то більше встигнеш!' },
  { id: 'launch_en', title: 'Launch (EN)', text: '🏢 <b>Office Dreams is live!</b>\n\nBuild your own neon game studio right inside Telegram:\n🎮 ship games and hits\n👥 hire a team\n🏙 open floors — from the basement to the CEO office\n\n💎 Real progress earns <b>airdrop points</b> — later exchanged for the <b>OFFICE</b> token on TON.\n\n🚀 Season 0 has started — the earlier you join, the more you get!' },
  { id: 'points_ru', title: 'Как заработать очки (RU)', text: '💎 <b>Как заработать очки аирдропа</b>\n\n1️⃣ Выпускай игры — чем выше оценка, тем больше очков\n2️⃣ Нанимай и прокачивай команду\n3️⃣ Отвечай на квесты и забирай цели\n4️⃣ Отмечайся каждый день — серия до 40 очков\n5️⃣ Смотри рекламу — энергия, деньги и бонусные очки\n6️⃣ Приглашай друзей — 10% от их очков\n\n⚠️ Боты и мультиаккаунты получают ноль. Играй честно — получай больше!' },
  { id: 'points_uk', title: 'Як заробити бали (UK)', text: '💎 <b>Як заробити бали аірдропу</b>\n\n1️⃣ Випускай ігри — що вища оцінка, то більше балів\n2️⃣ Наймай і прокачуй команду\n3️⃣ Відповідай на квести й забирай цілі\n4️⃣ Відмічайся щодня — серія до 40 балів\n5️⃣ Дивись рекламу — енергія, гроші й бонусні бали\n6️⃣ Запрошуй друзів — 10% від їхніх балів\n\n⚠️ Боти й мультиакаунти отримують нуль. Грай чесно — отримуй більше!' },
  { id: 'friends_ru', title: 'Друзья (RU)', text: '👥 <b>Зови друзей — получай больше</b>\n\n🎁 За каждого активного друга: <b>$500 и 2 кристалла</b> в игре\n💎 Плюс <b>10%</b> от его очков и <b>2%</b> от очков его друзей — навсегда\n\nДруг становится активным, когда поиграет 2 дня и выпустит игру.\nСвою ссылку найдёшь во вкладке «Аирдроп → Друзья».' },
  { id: 'token_ru', title: 'Токен OFFICE (RU)', text: '🪙 <b>Что такое OFFICE</b>\n\nOFFICE — собственный токен игры в сети <b>TON</b>.\n\n📊 1 000 000 000 токенов, выпуск один раз\n👥 45% — игрокам (ежедневный пул)\n🎁 5% — ранний аирдроп за очки Сезона 0\n🔒 Токены команды открываются 4 года\n\nОчки → токены после завершения Сезона 0. Подробности — в игре, вкладка «Аирдроп → Токен».\n\n<i>Очки не являются деньгами и не гарантируют выплат.</i>' },
  { id: 'official_ru', title: 'Официальный бот (RU)', text: '🏢 <b>Office Dreams — официальная игра</b>\n\n✅ Это единственный официальный бот\n⚠️ Других ботов нет — остерегайтесь подделок!\n\n📢 Канал проекта: @officedream\n💬 Вопросы и предложения: @vtmjs' },
  { id: 'official_uk', title: 'Офіційний бот (UK)', text: '🏢 <b>Office Dreams — офіційна гра</b>\n\n✅ Це єдиний офіційний бот\n⚠️ Інших ботів немає — остерігайтеся підробок!\n\n📢 Канал проєкту: @officedream\n💬 Питання та пропозиції: @vtmjs' },
];
function appLink(ctx) {
  const c = ctx.cfg;
  return c.botUsername ? (c.appShort ? `https://t.me/${c.botUsername}/${c.appShort}` : `https://t.me/${c.botUsername}`) : '';
}

function create(ctx) {
  return async function admin(req, res, url, ip) {
    const p = url.pathname;
    if (p === '/admin' || p === '/admin/') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
      return res.end(PAGE);
    }
    if (!p.startsWith('/admin/api/')) return json(res, 404, { ok: false });
    if (!ctx.cfg.adminKey) return json(res, 503, { ok: false, err: 'ADMIN_KEY is not set' });
    const bucket = 'adminfail:' + ip;
    const e = ctx.rate.get(bucket);
    if (e && Date.now() - e.t < 60000 && e.n >= 10) return json(res, 429, { ok: false, err: 'rate' });
    if (!safeEq(req.headers['x-admin-key'] || url.searchParams.get('key') || '', ctx.cfg.adminKey)) {
      const now = Date.now();
      const cur = ctx.rate.get(bucket);
      if (!cur || now - cur.t > 60000) ctx.rate.set(bucket, { t: now, n: 1 }); else cur.n++;
      return json(res, 401, { ok: false, err: 'key' });
    }
    const now = Date.now();
    try {
      if (p === '/admin/api/stats') return json(res, 200, { ok: true, ...stats(ctx, Number(url.searchParams.get('price')) || 0) });
      if (p === '/admin/api/risk') {
        const st = url.searchParams.get('state') || 'watch';
        const rows = ctx.db.all('SELECT id,name,username,country,pts,risk,risk_state,risk_note,created_at,last_seen,premium,platform,ref1,days_played FROM users WHERE risk_state=? AND deleted=0 ORDER BY risk DESC LIMIT 200', st);
        return json(res, 200, { ok: true, rows });
      }
      if (p === '/admin/api/user' && req.method === 'GET') {
        const d = userDetail(ctx, Number(url.searchParams.get('id')));
        return d ? json(res, 200, { ok: true, ...d }) : json(res, 404, { ok: false, err: 'nouser' });
      }
      if (p === '/admin/api/user' && req.method === 'POST') {
        const b = JSON.parse((await readBody(req)) || '{}');
        const u = U.getUser(ctx, Number(b.id));
        if (!u) return json(res, 404, { ok: false, err: 'nouser' });
        const map = { ban: 'banned', quarantine: 'quarantine', watch: 'watch', clear: 'ok' };
        if (b.action === 'unlock') { u.risk_note = null; AB.evaluate(ctx, u, now); }
        else if (map[b.action]) {
          u.risk_state = map[b.action];
          u.risk_note = 'admin:' + b.action;
          if (b.action === 'clear') u.risk_hard = 0;
        } else return json(res, 400, { ok: false, err: 'action' });
        AB.logRisk(ctx, u.id, 'admin_' + b.action, 0, null, now);
        Eco.persistUser(ctx, u);
        return json(res, 200, { ok: true, state: u.risk_state });
      }
      if (p === '/admin/api/drafts') {
        let check = 'CHANNEL not set';
        if (ctx.cfg.channel) {
          const me = await ctx.tg.call('getMe', {});
          const r = me && me.ok ? await ctx.tg.call('getChatMember', { chat_id: ctx.cfg.channel, user_id: me.result.id }) : me;
          check = r && r.ok ? (r.result.status === 'administrator' ? 'ok: bot is admin of ' + ctx.cfg.channel : 'bot is "' + r.result.status + '" — make it an ADMIN of ' + ctx.cfg.channel) : 'error: ' + ((r && r.description) || 'no response');
        }
        return json(res, 200, { ok: true, drafts: DRAFTS, channel: ctx.cfg.channel, check, lastErr: ctx.cache.channelErr || null, link: appLink(ctx) });
      }
      if (p === '/admin/api/post' && req.method === 'POST') {
        const b = JSON.parse((await readBody(req)) || '{}');
        const text = String(b.text || '').slice(0, 4000);
        if (!ctx.cfg.channel) return json(res, 400, { ok: false, err: 'CHANNEL is not set' });
        if (!text.trim()) return json(res, 400, { ok: false, err: 'empty' });
        const link = appLink(ctx);
        const markup = b.button && link ? { inline_keyboard: [[{ text: String(b.buttonText || '▶️ Играть / Play').slice(0, 40), url: link }]] } : undefined;
        let r;
        if (b.photo && ctx.cfg.publicUrl) {
          r = await ctx.tg.call('sendPhoto', { chat_id: ctx.cfg.channel, photo: ctx.cfg.publicUrl + '/cover-640x360.png', caption: text.slice(0, 1024), parse_mode: 'HTML', reply_markup: markup });
        } else {
          r = await ctx.tg.call('sendMessage', { chat_id: ctx.cfg.channel, text, parse_mode: 'HTML', link_preview_options: { is_disabled: true }, reply_markup: markup });
        }
        if (!r.ok) return json(res, 502, { ok: false, err: r.description || 'telegram' });
        return json(res, 200, { ok: true, id: r.result && r.result.message_id });
      }
      if (p === '/admin/api/sweep' && req.method === 'POST') return json(res, 200, { ok: true, ...AB.sweep(ctx, now, 5000) });
      if (p === '/admin/api/snapshot') {
        const rows = ctx.db.all("SELECT id,username,name,country,wallet,wallet_ok,pts,ref_pts,risk_state FROM users WHERE deleted=0 AND pts>0 AND risk_state IN ('ok','watch') ORDER BY pts DESC");
        if (url.searchParams.get('fmt') === 'json') return json(res, 200, { ok: true, season: ctx.cfg.season, at: now, rows });
        const csv = ['tg_id,username,country,wallet,wallet_verified,points,referral_points,risk_state']
          .concat(rows.map((r) => [r.id, r.username, r.country, r.wallet, r.wallet_ok, r.pts, r.ref_pts, r.risk_state].map(csvCell).join(','))).join('\n');
        res.writeHead(200, { 'content-type': 'text/csv; charset=utf-8', 'content-disposition': 'attachment; filename="officedreams-season' + ctx.cfg.season.id + '-snapshot.csv"' });
        return res.end(csv);
      }
      return json(res, 404, { ok: false, err: 'route' });
    } catch (err) {
      ctx.log('admin error', err && err.stack || err);
      return json(res, 500, { ok: false, err: 'server' });
    }
  };
}

const PAGE = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Office Dreams · Admin</title>
<style>
:root{--bg:#0a0f2e;--card:#121a4a;--fg:#e8ecff;--mut:#8f9ad6;--acc:#ffd23f;--ok:#40e0a0;--bad:#ff5c8a;--line:#26327a}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.4 system-ui,sans-serif;padding:16px;max-width:1100px;margin:auto}
h1{font-size:20px;margin:0 0 12px;color:var(--acc)}h2{font-size:15px;margin:22px 0 8px;color:var(--mut);text-transform:uppercase;letter-spacing:.06em}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px}
.k{font-size:11px;color:var(--mut);text-transform:uppercase}.v{font-size:22px;font-weight:700}
input,button,select{background:#0c133a;color:var(--fg);border:1px solid var(--line);border-radius:8px;padding:8px 10px;font:inherit}
button{cursor:pointer}button:hover{border-color:var(--acc)}button.b{color:var(--bad)}button.g{color:var(--ok)}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:6px 8px;border-bottom:1px solid var(--line);font-size:13px;vertical-align:top}
th{color:var(--mut);font-weight:600}.w{overflow-x:auto}.pill{padding:1px 8px;border-radius:99px;font-size:11px;background:#1c2766}
.ok{color:var(--ok)}.watch{color:var(--acc)}.quarantine,.banned{color:var(--bad)}code{color:var(--acc)}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:6px 0}
</style></head><body>
<h1>Office Dreams · Admin</h1>
<div class="row"><input id="key" type="password" placeholder="ADMIN_KEY" size="28"><button id="go">Open</button><span id="msg" class="pill" hidden></span></div>
<div id="out"></div>
<script>
const $=(s)=>document.querySelector(s);const esc=(s)=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
let KEY=sessionStorage.getItem('odk')||'';$('#key').value=KEY;
async function api(path,opt){const r=await fetch('/admin/api/'+path,Object.assign({headers:{'x-admin-key':KEY,'content-type':'application/json'}},opt||{}));const j=await r.json().catch(()=>({ok:false}));if(!r.ok||j.ok===false)throw new Error(j.err||r.status);return j}
const DN=(()=>{try{return new Intl.DisplayNames(['uk','en'],{type:'region'})}catch(e){return null}})();
function ctry(c){if(!c||c==='?')return '❓ unknown';const f=String.fromCodePoint(...[...c].map(x=>127397+x.charCodeAt(0)));let n=c;try{n=DN?DN.of(c):c}catch(e){}return f+' '+esc(n)}
function say(t){const m=$('#msg');m.hidden=!t;m.textContent=t||''}
const tbl=(head,rows)=>'<div class="w"><table><tr>'+head.map(h=>'<th>'+h+'</th>').join('')+'</tr>'+rows.map(r=>'<tr>'+r.map(c=>'<td>'+c+'</td>').join('')+'</tr>').join('')+'</table></div>';
async function load(){try{say('loading…');const s=await api('stats');say('');
const kv=(k,v)=>'<div class="card"><div class="k">'+k+'</div><div class="v">'+v+'</div></div>';
let h='<h2>Overview</h2><div class="grid">'+kv('Players',s.users)+kv('DAU',s.dau)+kv('WAU',s.wau)+kv('New today',s.newToday)+kv('Points total',s.ptsTotal)+kv('Points today',s.ptsToday)+kv('Friends (active)',s.refs.total+' ('+s.refs.active+')')+kv('Wallets (verified)',s.wallets.bound+' ('+s.wallets.verified+')')+'</div>';
h+='<h2>Risk states</h2><div class="grid">'+['ok','watch','quarantine','banned'].map(k=>kv(k,s.risk[k]||0)).join('')+'</div>';
h+='<h2>Stars revenue (last 14 days)</h2>'+tbl(['Date','Purchases','Stars','USD (net est.)'],s.revenue.map(r=>[r.date,r.n,r.stars,'$'+r.usd]));
h+='<h2>Emission brake preview (after token launch)</h2><div class="card">Token price (USD): <input id="price" size="8" value="'+(s.pie.price||'')+'" placeholder="0.001"> <button id="pp">Calculate</button><br>Yesterday net revenue: <b>$'+(Math.round(s.pie.yesterdayUsd*100)/100)+'</b> → schedule <b>'+s.pie.schedule+'</b>, brake <b>'+s.pie.brake+'</b> → daily pie <b>'+s.pie.pie+'</b> tokens</div>';
h+='<h2>Countries</h2>'+tbl(['Country','Players','Active 24h','Active 7d','New today','Paying','Avg points'],s.countries.map(r=>[ctry(r.c),r.n,r.d1,r.d7,r.today,r.payers,r.avgPts]));
h+='<h2>Top players</h2>'+tbl(['ID','Name','Country','Points','From refs','Risk','Wallet'],s.top.map(u=>[u.id,esc(u.name)+(u.username?' @'+esc(u.username):''),ctry(u.country),u.pts,u.ref_pts,'<span class="'+u.risk_state+'">'+u.risk_state+' ('+u.risk+')</span>',esc((u.wallet||'').slice(0,14))]));
h+='<h2>Review queue</h2><div class="row"><select id="st"><option>watch</option><option>quarantine</option><option>banned</option></select><button id="rq">Show</button><button id="sw">Run sweep</button></div><div id="rqo"></div>';
h+='<h2>Player lookup</h2><div class="row"><input id="uid" placeholder="Telegram ID" size="16"><button id="lk">Find</button></div><div id="uo"></div>';
h+='<h2>Channel post</h2><div class="card"><div class="row"><select id="dr"></select><button id="dru">Use draft</button></div><textarea id="ptx" rows="9" style="width:100%;background:#0c133a;color:var(--fg);border:1px solid var(--line);border-radius:8px;padding:8px;font:inherit"></textarea><div class="row"><label><input type="checkbox" id="pbtn" checked> ▶️ Play button</label><input id="pbt" value="▶️ Играть / Play" size="18"><label><input type="checkbox" id="pph" checked> picture</label><button id="ppub" class="g">Publish to channel</button><span id="pch" class="pill"></span></div><div class="k">HTML allowed: &lt;b&gt; &lt;i&gt; &lt;a href&gt; &lt;code&gt;. Picture posts: max 1024 characters.</div></div>';
h+='<h2>Airdrop snapshot</h2><div class="row"><button id="csv">Download CSV</button><button id="jsn">Open JSON</button></div>';
$('#out').innerHTML=h;
$('#pp').onclick=async()=>{const p=$('#price').value;const s2=await api('stats?price='+encodeURIComponent(p));alert('daily pie: '+s2.pie.pie+' (schedule '+s2.pie.schedule+', brake '+s2.pie.brake+')')};
$('#rq').onclick=queue;$('#sw').onclick=async()=>{const r=await api('sweep',{method:'POST'});say('checked '+r.checked+', changed '+r.changed)};
$('#lk').onclick=()=>lookup($('#uid').value);
posts();
$('#csv').onclick=()=>dl('snapshot?fmt=csv');$('#jsn').onclick=()=>dl('snapshot?fmt=json');
queue()}catch(e){say(e.message==='key'?'wrong key':'error: '+e.message)}}
let DR=[];async function posts(){try{const d=await api('drafts');DR=d.drafts;$('#dr').innerHTML=DR.map((x,i)=>'<option value="'+i+'">'+esc(x.title)+'</option>').join('');$('#pch').textContent=d.channel?('→ '+d.channel+' · '+d.check):'CHANNEL not set';$('#dru').onclick=()=>{$('#ptx').value=DR[$('#dr').value].text};$('#ptx').value=DR[0]?DR[0].text:'';
$('#ppub').onclick=async()=>{if(!confirm('Publish to '+d.channel+'?'))return;try{const r=await api('post',{method:'POST',body:JSON.stringify({text:$('#ptx').value,button:$('#pbtn').checked,buttonText:$('#pbt').value,photo:$('#pph').checked})});say('posted #'+r.id)}catch(e){say('error: '+e.message)}}}catch(e){}}
async function dl(p){const r=await fetch('/admin/api/'+p,{headers:{'x-admin-key':KEY}});const b=await r.blob();const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=p.includes('json')?'snapshot.json':'snapshot.csv';a.click()}
async function queue(){const r=await api('risk?state='+$('#st').value);$('#rqo').innerHTML=tbl(['ID','Name','Country','Pts','Risk','Why','Days','Act'],r.rows.map(u=>[u.id,esc(u.name),ctry(u.country),u.pts,u.risk,esc(u.risk_note||''),u.days_played,btns(u.id)]));wire()}
function btns(id){return ['clear','watch','quarantine','ban'].map(a=>'<button data-id="'+id+'" data-a="'+a+'" class="'+(a==='clear'?'g':a==='ban'?'b':'')+'">'+a+'</button>').join(' ')}
function wire(){document.querySelectorAll('button[data-a]').forEach(b=>b.onclick=async()=>{await api('user',{method:'POST',body:JSON.stringify({id:b.dataset.id,action:b.dataset.a})});say(b.dataset.id+' → '+b.dataset.a);queue()})}
async function lookup(id){try{const d=await api('user?id='+encodeURIComponent(id));const u=d.user;
$('#uo').innerHTML='<div class="card"><b>'+esc(u.name)+'</b> '+(u.username?'@'+esc(u.username):'')+' · id <code>'+u.id+'</code> · '+u.risk_state+' ('+u.risk+') · points <b>'+u.pts+'</b> · lang '+u.lang+' · platform '+esc(u.platform)+(u.premium?' · ⭐premium':'')+'<br>signals: <code>'+esc(JSON.stringify(d.signals))+'</code><br>friends: '+d.friends.total+' (active '+d.friends.active+') · days played '+u.days_played+' · streak '+u.streak+'<div class="row">'+btns(u.id)+'<button data-id="'+u.id+'" data-a="unlock">auto</button></div></div><h2>Risk events</h2>'+tbl(['When','Kind','Score','Meta'],d.events.map(e=>[new Date(e.ts).toISOString().slice(0,16),e.kind,e.score,esc(e.meta||'')]))+'<h2>Ledger</h2>'+tbl(['Day','Kind','Ref','Raw','Pts','N'],d.ledger.map(l=>[l.day,l.kind,l.ref,Math.round(l.raw*100)/100,Math.round(l.pts*100)/100,l.n]));wire()}catch(e){say('not found')}}
$('#go').onclick=()=>{KEY=$('#key').value.trim();sessionStorage.setItem('odk',KEY);load()};
if(KEY)load();
</script></body></html>`;

module.exports = { create, stats, userDetail };
