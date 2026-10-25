// Языки, сохранения и повторы — то, что ломалось незаметно для русских тестов:
//   • на каждом языке запись странности в журнал засчитывается (kind/kinds — ключи, trStory их не переводит), кнопка «НЕ ДЫШИ» подписана на языке игры;
//   • счётчик смертей хранит русские названия — смерть на английском видна в русской игре; в списке языков — только готовые (ru, en, uk);
//   • повтор часа: звонок снова звонит (копия события), рация голосом Гаса в S5 → смерть «Ты отозвался» без прыжка часов назад и «снова» — тот же час;
//   • «Продолжить» после перезагрузки: пульт (вход, лифт) и Гас на седьмом — как в конце прошлого часа; A1: «потом на десятом» — когда загорелся десятый.
//   node tools/story/lang_save_test.js   (PORT=8771 — сервер: cd public/games && python3 -m http.server 8771 --bind 127.0.0.1)
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
(async () => {
  const PORT = process.env.PORT || 8771, URL = l => `http://127.0.0.1:${PORT}/nightshift/index.html?gfx=retro&cs=${process.env.CS || .25}` + (l ? '&lang=' + l : '');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await b.newContext({ viewport: { width: 960, height: 540 }, locale: 'ru-RU' });
  let fail = 0; const ok = (c, m) => { console.log((c ? '  ✓ ' : '  ✗ ') + m); if (!c) fail++; };
  const errs = [];
  await ctx.addInitScript(() => { if (!sessionStorage.getItem('init')) { sessionStorage.setItem('init', 1); localStorage.clear(); localStorage.setItem('ns1_tutDone', '1'); } });
  const p = await ctx.newPage(); p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  const open = async l => { await p.goto(URL(l)); await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 240000 }); };
  const shift = async id => { await p.evaluate(s => window.__ns.startScene('sh_' + s), id); await p.waitForFunction(s => window.__ns.mode === 'shift' && window.__ns.shf && window.__ns.shf.id === s, id, { timeout: 30000 }); await p.evaluate(() => window.__ns.shSpeed(0)); };
  const die = async id => { await p.evaluate(id => window.__ns.startScene(id), id);
    for (let i = 0; i < 12 && (await p.evaluate(() => window.__ns.mode)) !== 'end'; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(500); } };
  // перевести часы смены и дождаться кадра, в котором час это увидел (события до этой минуты уже случились): в программной отрисовке кадр бывает и в 2–3 с
  const jump = async at => { const [h, m] = at.split(':').map(Number), M = (h < 12 ? h + 24 : h) * 60 + m;
    await p.evaluate(M => { const f = window.__ns.shf; f.gm = M - f.m0; }, M);
    await p.waitForFunction(M => !window.__ns.shf || window.__ns.shf.M >= M - .001, M, { timeout: 60000 }).catch(() => {}); };
  for (const L of ['en', 'uk', 'ru']) {
    console.log('язык', L);
    await open(L);
    ok(await p.evaluate(l => window.NS_LANG === l, L), `NS_LANG = ${L}`);
    // запись в журнал: дверь на 10-м, звук на 10-м, кошка у входа (kinds)
    await shift('S1');
    for (const [an, cam, kind] of [['door10', '10', 'Дверь'], ['snd10', '10', 'Звук'], ['cat', '01', 'Кто-то']]) {
      await p.evaluate(an => { const f = window.__ns.shf; f.act = []; f.ev.push({ m: f.M - .01, an }); }, an);
      await p.waitForFunction(an => window.__ns.shf.act.some(a => a.id === an), an, { timeout: 10000 }).catch(() => {});
      await p.evaluate(c => window.__ns.shiftCam(c), cam); await p.waitForTimeout(700);
      await p.evaluate(() => window.__ns.shiftLog());
      await p.evaluate(k => { const i = ['Свет', 'Дверь', 'Лифт', 'Помехи', 'Кто-то', 'Звук', 'Другое'].indexOf(k); document.querySelectorAll('#shPop .sh-grid button')[i].click(); }, kind);
      await p.waitForTimeout(1900);
      ok(await p.evaluate(an => !window.__ns.shf.act.some(a => a.id === an), an), `${L}: «${kind}» на камере ${cam} засчитано (${an})`);
    }
    // «НЕ ДЫШИ» под стойкой: подпись кнопки на языке игры
    await p.evaluate(() => window.__ns.startScene('hide', true));
    for (let i = 0; i < 12 && (await p.evaluate(() => window.__ns.mode)) !== 'mg'; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(400); }
    await p.waitForFunction(() => document.getElementById('hb'), null, { timeout: 30000 }).catch(() => {});
    const hb = await p.evaluate(() => (document.getElementById('hb') || {}).textContent || '');
    ok(hb && (L === 'ru' || L === 'uk' ? true : !/[А-Яа-яЁё]/.test(hb)), `${L}: кнопка удержания «${hb}»`);
    // смерть: в сохранении — русское название
    await die('over_k6');
    const ov = await p.evaluate(() => JSON.parse(localStorage.getItem('ns1_overs') || '[]'));
    ok(ov.includes('ТЕМНОТА ДОГНАЛА') && ov.length === 1, `${L}: смерть записана по-русски ${JSON.stringify(ov)}`);
    const cnt = await p.evaluate(() => (document.querySelector('.screen.over small') || {}).textContent || '');
    ok(/\b1\b/.test(cnt), `${L}: на экране смерти «${cnt.trim()}»`);
    // в списке языков — только готовые
    const langs = await p.evaluate(() => window.NS_LANGS.join(','));
    ok(langs === 'ru,en,uk', `${L}: языки в настройках ${langs}`);
  }
  // старое сохранение (до правки смерть записывалась на языке игры): та же смерть по-английски и по-русски — одна
  await p.evaluate(() => localStorage.setItem('ns1_overs', JSON.stringify(['THE DARK CAUGHT UP'])));
  await open('en'); await die('over_k6');
  const c2 = await p.evaluate(() => [(document.querySelector('.screen.over small') || {}).textContent || '', localStorage.getItem('ns1_overs')]);
  ok(/Deaths found: 1 of/.test(c2[0]), `en: старая английская запись и новая русская — одна смерть («${c2[0].trim()}», ${c2[1]})`);
  await open('ru');
  // повтор часа S4: звонок «Итан?.. Это ты?» — ответил, умер, снова — звонок снова звонит, сценарий не помечен
  await shift('S4'); await jump('01:39'); await p.evaluate(() => window.__ns.shSpeed(4));
  await p.waitForFunction(() => document.getElementById('cTake'), null, { timeout: 60000 });
  await p.click('#cTake'); await p.waitForFunction(() => document.getElementById('cYes'), null, { timeout: 30000 }); await p.click('#cYes');
  await p.waitForFunction(() => window.__ns.scene === 'over_phone', null, { timeout: 10000 }).catch(() => {});
  ok(await p.evaluate(() => window.__ns.scene === 'over_phone'), 'S4: ответил «Да. Это я» — смерть «Ты назвался»');
  ok(await p.evaluate(() => !window.STORY.shift.segs.S4.events.find(e => e.call).call.taken), 'S4: в сценарии звонок не помечен «снят» (повтор — снова звонит)');
  // S5: рация голосом Гаса → «Ты отозвался»: часы — 04:4x, а не 03:06; «Попробовать снова» — час S5
  await shift('S5'); await jump('04:41'); await p.evaluate(() => window.__ns.shSpeed(4));
  await p.waitForFunction(() => document.getElementById('cTake'), null, { timeout: 60000 });
  await p.click('#cTake'); await p.waitForFunction(() => document.getElementById('cYes'), null, { timeout: 30000 }); await p.click('#cYes');
  await p.waitForFunction(() => window.__ns.scene === 'over_k3', null, { timeout: 10000 }).catch(() => {});
  const clk = await p.evaluate(() => document.getElementById('clock').textContent);
  ok(/^04:4\d/.test(clk), `S5 → «Ты отозвался»: часы ${clk}`);
  for (let i = 0; i < 12 && (await p.evaluate(() => window.__ns.mode)) !== 'end'; i++) { await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(500); }
  await p.evaluate(() => document.getElementById('oRetry').click()); await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 }).catch(() => {});
  ok(await p.evaluate(() => window.__ns.shf && window.__ns.shf.id === 'S5'), '«Попробовать снова» — снова час S5 (а не рация в 03:05)');
  // повтор часа после блэкаута — со светом
  await p.evaluate(() => { window.__ns.shk.st.blackout = 1; window.__ns.startScene('sh_S5'); }); await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 });
  ok(await p.evaluate(() => !window.__ns.shk.st.blackout), 'S5 заново — свет на этажах не погашен с начала часа');
  // «Продолжить» после перезагрузки: конец S2 → S3 (вход заперт, лифт стоит, Гас на седьмом)
  await shift('S2'); await jump('23:57'); await p.waitForTimeout(800);   // события часа (и случайный «вход открылся сам») — уже прошли
  await p.evaluate(() => { const f = window.__ns.shf, k = window.__ns.shk; f.act = []; k.door = 1; k.lift = 1; window.__ns.shSpeed(30); });
  await p.waitForFunction(() => window.__ns.shf && window.__ns.shf.id === 'S3', null, { timeout: 30000 }).catch(() => {});
  await open('ru');
  await p.evaluate(() => window.__ns.begin(JSON.parse(localStorage.getItem('ns1_save')))); await p.waitForFunction(() => window.__ns.mode === 'shift', null, { timeout: 30000 });
  const k3 = await p.evaluate(() => ({ id: window.__ns.shf.id, door: window.__ns.shk.door, lift: window.__ns.shk.lift, gus: window.__ns.shProps && window.__ns.shProps.gus }));
  ok(k3.id === 'S3' && k3.door === 1 && k3.lift === 1 && k3.gus === 1, `«Продолжить» в S3: ${JSON.stringify(k3)}`);
  // A1: «На седьмом загорелся свет. Потом на десятом» — когда загорелся десятый
  await shift('A1'); await p.evaluate(() => { const f = window.__ns.shf; f.ev = f.ev.filter(e => !e.an || /^light/.test(e.an)); f.act = []; window.__ns.shk.lights = {}; });   // без случайных странностей (занятая камера 10 откладывает свет — и реплику с ним)
  await jump('04:12'); await p.waitForTimeout(400);
  const a = await p.evaluate(() => ({ n40: window.__ns.shf.vq.some(v => v.k === 'n40'), l10: !!window.__ns.shk.lights[10] }));
  await jump('04:16'); await p.waitForTimeout(600);
  const z = await p.evaluate(() => ({ n40: window.__ns.shf.vq.some(v => v.k === 'n40'), l10: !!window.__ns.shk.lights[10] || window.__ns.shf.act.some(x => x.id === 'light10') }));
  ok(!a.n40 && !a.l10 && z.n40 && z.l10, `A1: в 04:12 ни реплики, ни света на 10-м (${JSON.stringify(a)}), в 04:16 — вместе (${JSON.stringify(z)})`);
  // язык посреди часа (☰ → Настройки → Язык): после перезагрузки тот же час и минута, Гас на седьмом, вход заперт
  await shift('S2'); await jump('22:31'); await p.waitForTimeout(900); await p.evaluate(() => { window.__ns.shk.door = 1; });
  const before = await p.evaluate(() => ({ M: Math.floor(window.__ns.shf.M), gus: window.__ns.shk.st.gus }));
  await p.evaluate(() => { document.getElementById('bMenu').click(); document.getElementById('pzSet').click(); document.getElementById('sLang').click(); document.querySelector('[data-l="en"]').click(); });
  // после перезагрузки — экран «Нажми, чтобы продолжить» (не загрузка: у той полоса #loadbar); жмём по нему (у #ui своей высоты нет — тап доходит от экрана)
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'load' && document.querySelector('.screen.boot') && !document.getElementById('loadbar'), null, { timeout: 300000 }).catch(() => {});
  await p.click('.screen.boot'); await p.waitForFunction(() => { const f = window.__ns.mode === 'shift' && window.__ns.shf; return f && f.gm > 0 && Math.abs(f.M - f.m0 - f.gm) < .5; }, null, { timeout: 60000 }).catch(() => {});   // и кадр, в котором час увидел восстановленную минуту
  const after = await p.evaluate(() => ({ lang: window.NS_LANG, id: window.__ns.shf && window.__ns.shf.id, M: window.__ns.shf && Math.floor(window.__ns.shf.M), gus: window.__ns.shk.st.gus, door: window.__ns.shk.door }));
  ok(after.lang === 'en' && after.id === 'S2' && Math.abs(after.M - before.M) <= 1 && after.gus === 1 && after.door === 1, `язык посреди часа: до ${JSON.stringify(before)}, после ${JSON.stringify(after)}`);
  ok(!errs.length, 'ошибок страницы нет' + (errs.length ? ': ' + errs.slice(0, 3).join(' | ') : ''));
  console.log(fail ? `НЕ ПРОШЛО: ${fail}` : 'всё прошло'); await b.close(); process.exit(fail ? 1 : 0);
})();
