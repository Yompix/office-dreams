// Оформление стрима YouTube в OBS (владелец, 26 ноя): заставки «Скоро начнём» / «Отошёл» / «Конец», оверлей поверх игры (прозрачный PNG), превью.
//   node apps/stream/make.js → apps/stream/out/*.png
const { chromium } = require('/opt/node-tools/node_modules/playwright'), path = require('path'), fs = require('fs');
const OUT = path.join(__dirname, 'out'); fs.mkdirSync(OUT, { recursive: true });
const G = '#53fc18', R = '#ff3040', BG = '#0b0e11';
const base = `*{box-sizing:border-box;margin:0}body{width:var(--w);height:var(--h);overflow:hidden;font-family:'Segoe UI',Roboto,system-ui,sans-serif;color:#fff}
.bg{position:absolute;inset:0;background:radial-gradient(ellipse at 70% 35%,rgba(83,252,24,.17),transparent 55%),radial-gradient(ellipse at 15% 90%,rgba(255,48,64,.10),transparent 50%),${BG}}
.grid{position:absolute;inset:0;background-image:linear-gradient(rgba(83,252,24,.07) 1px,transparent 1px),linear-gradient(90deg,rgba(83,252,24,.07) 1px,transparent 1px);background-size:48px 48px;mask-image:linear-gradient(180deg,transparent,#000 40%,#000 70%,transparent)}
.logo{font-weight:900;letter-spacing:.06em;line-height:.9}.logo b{color:${G};text-shadow:0 0 30px rgba(83,252,24,.6)}
.c{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:30px;text-align:center}
.sub{font-size:40px;color:#b8c0b8}.tg{font-size:36px;color:${G};font-weight:700}
.yt{display:inline-block;background:${R};color:#fff;font-weight:900;padding:6px 18px;border-radius:10px;letter-spacing:.08em}`;
const PAD = s => `<svg width="${s}" height="${s * .62}" viewBox="0 0 100 62" style="color:${G}"><path fill="currentColor" d="M24 4h52c13 0 22 10 24 24l2 18c1 9-5 15-12 15-5 0-8-3-11-7l-5-7H26l-5 7c-3 4-6 7-11 7C3 61-3 55-2 46l2-18C2 14 11 4 24 4z"/><g fill="${BG}"><rect x="19" y="23" width="18" height="6" rx="2"/><rect x="25" y="17" width="6" height="18" rx="2"/><circle cx="70" cy="20" r="4.5"/><circle cx="80" cy="30" r="4.5"/><circle cx="60" cy="30" r="4.5"/><circle cx="70" cy="40" r="4.5"/></g></svg>`;
const screen = (big, sub) => [1920, 1080, `<div class="bg"></div><div class="grid"></div><div class="c">${PAD(200)}<div class="logo" style="font-size:120px">YOMPIX<b>.</b></div>
  <div style="font-size:92px;font-weight:900;letter-spacing:.08em">${big}</div><div class="sub">${sub}</div><div class="tg">t.me/yompixgames</div></div>`];
const pages = {
  starting: screen('СТРИМ <b style="color:' + G + '">СКОРО</b> НАЧНЁТСЯ', 'Пиши в чат, откуда ты 👋'),
  brb: screen('СЕЙЧАС <b style="color:' + G + '">ВЕРНУСЬ</b>', 'Не уходи — пару минут'),
  ending: screen('СПАСИБО ЗА <b style="color:' + G + '">СТРИМ</b>', 'Подпишись и включи 🔔, чтобы не пропустить следующий'),
  // оверлей поверх игры: прозрачный, рамка камеры справа внизу (окно 480×270 — туда ставится камера), плашка ника и строка внизу
  overlay: [1920, 1080, `<div style="position:absolute;right:36px;bottom:96px;width:492px;height:282px;border:6px solid ${G};border-radius:18px;box-shadow:0 0 26px rgba(83,252,24,.55)"></div>
    <div style="position:absolute;right:36px;bottom:388px;background:${BG};border:3px solid ${G};border-radius:12px;padding:6px 18px;font-weight:900;font-size:30px;letter-spacing:.06em">YOMPIX<span style="color:${G}">.</span></div>
    <div style="position:absolute;left:0;right:0;bottom:0;height:64px;background:linear-gradient(90deg,rgba(11,14,17,.92),rgba(11,14,17,.75));border-top:3px solid ${G};display:flex;align-items:center;gap:40px;padding:0 36px;font-size:26px;font-weight:700">
    <span class="yt">● LIVE</span><span>💚 Донат: <span style="color:${G}">t.me/officedream1_bot</span></span><span>📢 Telegram: <span style="color:${G}">t.me/yompixgames</span></span><span>🎮 Hush Till Six — Google Play</span></div>`, true],
  thumbnail: [1280, 720, `<div class="bg"></div><div class="grid"></div>
    <div style="position:absolute;left:60px;top:60px"><span class="yt" style="font-size:44px">● LIVE</span></div>
    <div style="position:absolute;left:60px;bottom:70px;right:60px"><div class="logo" style="font-size:132px;line-height:1">НАЗВАНИЕ<br><b>ИГРЫ</b></div><div style="font-size:40px;color:#cfd6cf;margin-top:18px;font-weight:700">Yompix · стрим</div></div>
    <div style="position:absolute;right:60px;top:70px;transform:rotate(10deg)">${PAD(300)}</div>`],
};
(async () => {
  const b = await chromium.launch();
  for (const [k, [w, h, html, transparent]] of Object.entries(pages)) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    await p.setContent(`<html><head><style>:root{--w:${w}px;--h:${h}px}${base}${transparent ? 'body{background:transparent}' : ''}</style></head><body>${html}</body></html>`);
    await p.screenshot({ path: path.join(OUT, k + '.png'), omitBackground: !!transparent }); await p.close(); console.log(k);
  }
  await b.close();
})();
