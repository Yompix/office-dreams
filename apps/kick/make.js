// Оформление канала Kick (владелец, 25 ноя): аватар, баннер, офлайн-заставка, панели. node apps/kick/make.js → apps/kick/out/*.png
const { chromium } = require('/opt/node-tools/node_modules/playwright'), path = require('path'), fs = require('fs');
const OUT = path.join(__dirname, 'out'); fs.mkdirSync(OUT, { recursive: true });
const G = '#53fc18', BG = '#0b0e11';
const base = `*{box-sizing:border-box;margin:0}body{width:var(--w);height:var(--h);overflow:hidden;background:${BG};font-family:'Segoe UI',Roboto,system-ui,sans-serif;color:#fff}
.bg{position:absolute;inset:0;background:radial-gradient(ellipse at 70% 40%,rgba(83,252,24,.18),transparent 55%),radial-gradient(ellipse at 15% 90%,rgba(83,252,24,.08),transparent 50%),${BG}}
.grid{position:absolute;inset:0;background-image:linear-gradient(rgba(83,252,24,.07) 1px,transparent 1px),linear-gradient(90deg,rgba(83,252,24,.07) 1px,transparent 1px);background-size:48px 48px;mask-image:linear-gradient(180deg,transparent,#000 40%,#000 70%,transparent)}
.logo{font-weight:900;letter-spacing:.06em;line-height:.9}.logo b{color:${G};text-shadow:0 0 30px rgba(83,252,24,.6)}
.pad{display:inline-block;color:${G}}`;
const PAD = s => `<svg class="pad" width="${s}" height="${s * .62}" viewBox="0 0 100 62"><path fill="currentColor" d="M24 4h52c13 0 22 10 24 24l2 18c1 9-5 15-12 15-5 0-8-3-11-7l-5-7H26l-5 7c-3 4-6 7-11 7C3 61-3 55-2 46l2-18C2 14 11 4 24 4z"/><g fill="${BG}"><rect x="19" y="23" width="18" height="6" rx="2"/><rect x="25" y="17" width="6" height="18" rx="2"/><circle cx="70" cy="20" r="4.5"/><circle cx="80" cy="30" r="4.5"/><circle cx="60" cy="30" r="4.5"/><circle cx="70" cy="40" r="4.5"/></g></svg>`;
const pages = {
  avatar: [800, 800, `<div class="bg"></div><div class="grid"></div><div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:26px">
    <div style="width:520px;height:520px;border-radius:50%;border:10px solid ${G};box-shadow:0 0 60px rgba(83,252,24,.5),inset 0 0 60px rgba(83,252,24,.25);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;background:#0f1418">
    ${PAD(250)}<div class="logo" style="font-size:118px">Y<b>X</b></div></div></div>`],
  banner: [1920, 480, `<div class="bg"></div><div class="grid"></div>
    <div style="position:absolute;left:640px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center;gap:14px">
    <div class="logo" style="font-size:150px">YOMPIX<b>.</b></div>
    <div style="font-size:38px;font-weight:600;letter-spacing:.22em;color:#cfd6cf">GAMES · STREAMS · CHILL</div></div>
    <div style="position:absolute;right:120px;top:50%;transform:translateY(-50%) rotate(-12deg);opacity:.9">${PAD(380)}</div>`],
  offline: [1920, 1080, `<div class="bg"></div><div class="grid"></div><div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:34px;text-align:center">
    ${PAD(240)}<div class="logo" style="font-size:170px">YOMPIX<b>.</b></div>
    <div style="font-size:64px;font-weight:800;letter-spacing:.12em"><span style="display:inline-block;width:26px;height:26px;border-radius:50%;background:#ff3b3b;margin-right:22px;vertical-align:middle;box-shadow:0 0 20px #ff3b3b"></span>OFFLINE</div>
    <div style="font-size:40px;color:#b8c0b8">Follow to get notified when I go live</div>
    <div style="font-size:34px;color:${G};font-weight:700">t.me/yompixgames</div></div>`],
};
const panel = t => [640, 200, `<div class="bg"></div><div class="grid"></div><div style="position:absolute;inset:0;display:flex;align-items:center;gap:28px;padding:0 44px">
  <div style="width:10px;height:120px;background:${G};border-radius:6px;box-shadow:0 0 24px ${G}"></div><div class="logo" style="font-size:70px;white-space:nowrap">${t}</div></div>`];
Object.assign(pages, { panel_about: panel('ABOUT <b>ME</b>'), panel_socials: panel('SOCIALS<b>.</b>'), panel_games: panel('MY <b>GAMES</b>'), panel_support: panel('SUPPORT<b>.</b>'), panel_rules: panel('CHAT <b>RULES</b>') });
(async () => {
  const b = await chromium.launch();
  for (const [k, [w, h, html]] of Object.entries(pages)) {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    await p.setContent(`<html><head><style>:root{--w:${w}px;--h:${h}px}${base}</style></head><body>${html}</body></html>`);
    await p.screenshot({ path: path.join(OUT, k + '.png') }); await p.close(); console.log(k);
  }
  await b.close();
})();
