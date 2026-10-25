// Обходы и разговоры (explore / talk) в настоящей игре: точки стоят на предметах кадра и не наезжают друг на друга,
// темы открываются по условиям (что нашёл, что спросил), сцены возвращают обратно, выход ведёт дальше; телефон Фрэнка в 02:20.
//   node tools/story/act_test.js   [SHOTS=папка — снимки обхода и разговоров, вертикально и горизонтально]
const { chromium } = require('/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const shots = process.env.SHOTS; if (shots) fs.mkdirSync(shots, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  let fail = 0; const ok = (c, m) => { console.log((c ? '  ✓ ' : '  ✗ ') + m); if (!c) fail++; };
  for (const [land, vp] of [[0, { width: 432, height: 768 }], [1, { width: 960, height: 540 }]]) {
    console.log(land ? 'горизонтально' : 'вертикально');
    const p = await b.newPage({ viewport: vp, locale: 'ru-RU' });
    const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300))); p.on('console', m => m.type() === 'error' && errs.push(m.text().slice(0, 300)));
    await p.addInitScript(l => { localStorage.setItem('ns1_land3', l ? 'true' : 'false'); localStorage.setItem('ns1_tutDone', '1'); }, land);
    await p.goto('http://127.0.0.1:' + (process.env.PORT || 8771) + '/nightshift/index.html?gfx=retro');
    await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 });
    const st = () => p.evaluate(() => ({ m: window.__ns.mode, s: window.__ns.scene }));
    const play = async () => { for (let i = 0; i < 120; i++) { await p.waitForTimeout(150); const s = await st(); if (s.m !== 'play') return s; await p.evaluate(() => window.__ns.skipBeat()); } return st(); };
    // точка: центр кольца (стоит на предмете) и прямоугольник всей кнопки (кольцо + подпись) — подписи не должны наезжать на соседние точки
    const items = async () => { await p.waitForFunction(() => { const s = [...document.querySelectorAll('#act .spot')]; return !s.length || s.every(b => b.style.visibility === 'visible'); }, null, { timeout: 8000 }).catch(() => {}); await p.waitForTimeout(400); return p.evaluate(() => [...document.querySelectorAll('#act button')].map(x => { const r = (x.querySelector('i') || x).getBoundingClientRect(), R = (x.querySelector('span') || x).getBoundingClientRect();
      return { t: x.textContent.trim(), i: x.dataset.i, spot: x.classList.contains('spot'), x: r.x + r.width / 2, y: r.y + r.height / 2, Q: [r.left, r.top, r.right, r.bottom], R: [R.left, R.top, R.right, R.bottom] }; })); };
    const click = async t => { await p.evaluate(t => { const x = [...document.querySelectorAll('#act button')].find(y => y.textContent.trim().endsWith(t)); if (x) x.click(); }, t); return play(); };
    const snap = async n => { if (shots) { await p.waitForTimeout(600); await p.screenshot({ path: `${shots}/${n}_${land ? 'L' : 'P'}.png`, timeout: 120000 }).catch(() => {}); } };
    const spotsOk = (sp, n) => { const inV = sp.every(s => s.x > 10 && s.x < vp.width - 10 && s.y > 10 && s.y < vp.height - 10);
      let near = 1e9, hit = ''; for (let i = 0; i < sp.length; i++) for (let j = i + 1; j < sp.length; j++) { near = Math.min(near, Math.hypot(sp[i].x - sp[j].x, sp[i].y - sp[j].y));
        const X = (a, c) => a[0] < c[2] && c[0] < a[2] && a[1] < c[3] && c[1] < a[3];
        if (X(sp[i].R, sp[j].R) || X(sp[i].R, sp[j].Q) || X(sp[j].R, sp[i].Q)) hit = sp[i].t + ' / ' + sp[j].t; }
      const cut = sp.filter(s => s.R[0] < 0 || s.R[2] > vp.width); ok(!cut.length, 'подписи не выходят за край' + (cut.length ? ': ' + cut.map(s => s.t).join(', ') : ''));
      ok(sp.length === n && inV, `${n} точек на экране (${sp.map(s => s.t).join(', ')})`); ok(near > 44 && !hit, `точки и подписи не наезжают (ближе всего ${near | 0} px${hit ? '; наезжают: ' + hit : ''})`); };
    // --- 21:06, будка: не спрашивал сменщика → fb00; все точки → флаги; выход → смена S1
    await p.evaluate(() => window.__ns.setFlags({}));
    await p.evaluate(() => window.__ns.startScene('booth')); let s = await play();
    ok(s.m === 'explore', 'будка: обход (' + s.m + ')');
    let it = await items(); spotsOk(it.filter(x => x.spot), 4); await snap('booth');
    for (const t of ['Куртка Фрэнка', 'Фото под стеклом', 'Ящик стола']) { s = await click(t); ok(s.m === 'explore' && s.s === 'booth', `«${t}» → вернулся к обходу`); }
    it = await items(); ok(it.filter(x => x.spot).length === 1, 'осмотренные точки пропали');
    const fl = await p.evaluate(() => window.__ns.flags); ok(fl.salt && fl.photo && fl.fphone && !fl.asked, 'флаги: соль, фото, телефон Фрэнка');
    s = await click('Журнал'); ok(s.m === 'shift', 'всё осмотрено → сам садится за мониторы (' + s.m + ')');
    // --- 22:00, звонок дневного: «Оно — это кто?» только после «Зачем стучал»
    await p.evaluate(() => window.__ns.startScene('dg_call')); s = await play();
    ok(s.m === 'talk', 'звонок: разговор (' + s.m + ')'); it = await items(); await snap('dg_call');
    ok(it.length === 4 && !it.some(x => x.t.includes('Оно')), 'тем 3 + «до утра», «Оно» пока нет');
    s = await click('Зачем ты стучал в стекло?'); it = await items(); ok(s.m === 'talk' && it.some(x => x.t.includes('Оно')), 'после «стучал» открылось «Оно — это кто?»');
    await p.keyboard.press('1'); s = await play(); ok(s.m === 'talk', 'тема цифрой с клавиатуры');
    s = await click('«Ладно. До утра»'); ok(s.m === 'explore' && s.s === 'round1', 'до утра → обход подвала (' + s.s + ')');
    // --- 22:05, подвал: «Позвать» только после «Постучать»
    it = await items(); spotsOk(it.filter(x => x.spot), 3); await snap('round1');
    s = await click('Постучать'); it = await items(); ok(it.some(x => x.t.includes('Позвать')), 'после стука можно позвать');
    s = await click('Подняться наверх'); ok(s.m === 'shift', 'наверх → смена S2 (' + s.m + ')');
    // --- 01:00, Гас: с фото — «это Марта?», без фото и со словами дневного — «жена тоже работала?»
    await p.evaluate(() => window.__ns.setFlags({ photo: 1 })); await p.evaluate(() => window.__ns.startScene('round2')); s = await play(); it = await items(); await snap('round2');
    ok(s.m === 'talk' && it.some(x => x.t.includes('Марта')) && !it.some(x => x.t.includes('жена')), 'видел фото → спросить про Марту');
    await p.evaluate(() => window.__ns.setFlags({ 'v:dg_gus': 1 })); await p.evaluate(() => window.__ns.startScene('round2')); s = await play(); it = await items();
    ok(it.some(x => x.t.includes('жена')) && !it.some(x => x.t.includes('Марта')), 'фото не видел, про вдовца знает → спросить про жену');
    s = await click('Фрэнк что-нибудь говорил перед тем, как пропасть?'); s = await click('Кто вас зовёт?'); s = await click('«Что бы она ни говорила — не отвечайте»');
    ok((await p.evaluate(() => window.__ns.flags)).warned, 'Гас пообещал (флаг warned)');
    s = await click('Пора в будку'); ok(s.m === 'talk' && s.s === 'ly_call', 'в будку → звонит Лили (' + s.s + ')'); await snap('ly_call');
    s = await click('«Спи давай. Всё сдашь»'); ok(s.m === 'shift', 'Лили → смена S4');
    // --- 04:30, серверная: обход (3 точки) → разговор с Фрэнком (тема про Гаса — только после gs_calls) → «до шести» → смена S6
    await p.evaluate(() => window.__ns.setFlags({})); await p.evaluate(() => window.__ns.startScene('srv_wait')); s = await play();
    ok(s.m === 'explore' && s.s === 'srv_wait', 'серверная: обход (' + s.m + ')'); it = await items(); spotsOk(it.filter(x => x.spot), 3); await snap('srv_room');
    for (const t of ['Круг соли', 'Кулер и сухари']) { s = await click(t); ok(s.m === 'explore' && s.s === 'srv_wait', `«${t}» → вернулся к обходу`); }
    s = await click('Листы на стойке'); ok(s.m === 'talk' && s.s === 'srv_talk', 'всё осмотрено → сам сел рядом с Фрэнком, разговор (' + s.m + ' ' + s.s + ')'); it = await items(); await snap('srv_talk');
    ok(it.length === 3 && !it.some(x => x.t.includes('Гас')), 'тем 2 + «до шести»; про Гаса пока нет (' + it.length + ')');
    s = await click('Это правда была она?'); ok(s.m === 'talk' && s.s === 'srv_talk', 'тема → вернулся к разговору');
    await p.evaluate(() => window.__ns.setFlags({ 'v:gs_calls': 1 })); await p.evaluate(() => window.__ns.startScene('srv_talk')); s = await play(); it = await items();
    ok(it.some(x => x.t.includes('Гас')), 'после разговора с Гасом есть тема про Гаса');
    s = await click('Посидеть с ним до шести'); ok(s.m === 'shift', 'до шести → смена S6 (' + s.m + ')');
    // --- 02:20, телефон Фрэнка (если поставил на зарядку): звонок, сбросить — жив, ответить — смерть
    for (const take of [0, 1]) {
      await p.evaluate(() => { window.__ns.setFlags({ fphone: 1 }); window.__ns.startScene('sh_S4'); });
      await p.waitForFunction(() => window.__ns.mode === 'shift' && window.__ns.shf, null, { timeout: 30000 });
      await p.evaluate(() => { const f = window.__ns.shf, e0 = f.ev.find(e => e.sfx), M = e0.m - 2; f.gm = M - f.m0; for (const e of f.ev) if (e.m < M) e.done = true; });   // сразу к 02:14
      await p.evaluate(() => window.__ns.shSpeed(4));
      const got = await p.waitForFunction(() => { const h = document.querySelector('#shPop h3'); return h && /Фрэнка/.test(h.textContent) && document.getElementById('cTake'); }, null, { timeout: 90000 }).then(() => true, () => false);
      ok(got, 'телефон Фрэнка звонит');
      if (got && !take) { await p.click('#cNo'); await p.waitForTimeout(1500); ok((await st()).m === 'shift', 'сбросил — смена идёт дальше'); ok((await p.evaluate(() => window.__ns.flags)).fpcall, 'видел сообщение «Фрэнки, сынок…» (fpcall)'); }
      if (got && take) { await p.click('#cTake'); await p.waitForTimeout(800); s = await st(); ok(s.s === 'over_fmom', 'ответил — смерть (' + s.s + ')'); }
    }
    ok(!errs.length, 'ошибок страницы нет' + (errs.length ? ': ' + errs.slice(0, 3).join(' | ') : ''));
    await p.close();
  }
  console.log(fail ? `НЕ ПРОШЛО: ${fail}` : 'всё прошло'); await b.close(); process.exit(fail ? 1 : 0);
})();
