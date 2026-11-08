// Проверка Hush Till Six «как на ТВ-приставке»: Android TV, без сенсора и мыши, 1280×720, только пульт (стрелки, ОК, «Назад»).
// Два прогона: 1) новый браузер; 2) «старый браузер» — современные скрипты игры отдаются со SyntaxError, как их видит WebView < 80
//   (должны подмениться копиями из legacy/), и CSS.supports «не знает» cqw / aspect-ratio / inset (включаются шимы compat.js).
// Сервер игры: cd public/games && python3 -m http.server 8771. Запуск: node tools/tv/tv_test.js
const { chromium } = require('playwright');
const PORT = process.env.PORT || 8771, U = `http://127.0.0.1:${PORT}/nightshift/index.html?lang=ru`;
const UA = 'Mozilla/5.0 (Linux; Android 9; MiBOX4 Build/PI; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/79.0.3945.136 Safari/537.36';
const MODERN = ['i18n.js', 'world.js', 'chars2.js', 'scenes.js', 'engine.js', 'pad.js'];   // те, что старый браузер не прочтёт
let fails = 0; const ok = (c, m) => { console.log((c ? '  ✓ ' : '  ✗ ') + m); if (!c) fails++; };

async function run(br, old) {
  console.log(old ? '\n— старый браузер (WebView 79): legacy/ + шимы CSS' : '\n— новый браузер на приставке');
  const ctx = await br.newContext({ viewport: { width: 1280, height: 720 }, userAgent: UA, hasTouch: false, isMobile: false });
  const pg = await ctx.newPage();
  const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 160)));
  if (old) {
    await ctx.route(/\/nightshift\/[a-z0-9]+\.js(\?.*)?$/, route => {
      const f = new URL(route.request().url()).pathname.split('/').pop();
      if (MODERN.includes(f)) return route.fulfill({ status: 200, contentType: 'application/javascript', body: 'let a = b ?? c;\n' + '@@ старый браузер не понимает этот файл' });
      return route.continue();
    });
    await pg.addInitScript(() => { const s = CSS.supports.bind(CSS); CSS.supports = (p, v) => /cq[wh]|aspect-ratio|^inset$/.test(p + ' ' + (v || '')) ? false : s(p, v); });
  }
  await pg.goto(U);
  await pg.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 180000 });
  const st = await pg.evaluate(() => ({ tv: window.nsTV, cls: document.documentElement.className, leg: window.nsLegacy, gfx: localStorage.getItem('ns1_gfx'),
    stage: (() => { const r = document.getElementById('stage').getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)]; })(),
    u: getComputedStyle(document.getElementById('stage')).getPropertyValue('--u').trim(), land: document.body.classList.contains('land') }));
  ok(st.tv === true && /\btv\b/.test(st.cls), 'приставка узнана (nsTV, класс .tv)');
  ok(st.gfx === '"retro"', 'первый запуск на ТВ — графика «Ретро» (' + st.gfx + ')');
  ok(st.leg === (old ? MODERN.length : 0), `подменено скриптов из legacy/: ${st.leg} (ждём ${old ? MODERN.length : 0})`);
  ok(st.land && st.stage[0] === 1280 && st.stage[1] === 720, 'кадр 16:9 на весь экран: ' + st.stage.join('×'));
  if (old) ok(/px$/.test(st.u) && Math.abs(parseFloat(st.u) - 720 * .0068) < .05, 'единица интерфейса --u посчитана в px: ' + st.u);
  const cur = () => pg.evaluate(() => { const c = window.__pad.cur; return c ? (c.id || c.className) + ':' + c.textContent.trim().slice(0, 24) : null; });
  const key = async (k, wait = 450) => { await pg.keyboard.press(k); await pg.waitForTimeout(wait); };
  // титул → меню: ОК (Enter)
  if (await pg.evaluate(() => !!document.querySelector('#ui .title.gate'))) await key('Enter', 1000);
  ok(await pg.evaluate(() => !!document.querySelector('#ui .title.open')), 'ОК открыл меню');
  await key('ArrowDown');
  ok(await pg.evaluate(() => document.body.classList.contains('pad')) && !!await cur(), 'стрелка пульта включила рамку (' + await cur() + ')');
  for (let i = 0; i < 8 && !/Настрой/.test(await cur() || ''); i++) await key('ArrowDown', 300);
  ok(/Настрой/.test(await cur() || ''), 'рамка дошла до «Настройки»');
  await key('Enter', 800);
  ok(await pg.evaluate(() => !!document.querySelector('#ui .sets')), 'ОК открыл настройки');
  const shot = `${process.env.OUT || '/tmp'}/tv_${old ? 'old' : 'new'}_settings.png`; await pg.screenshot({ path: shot });
  const fit = await pg.evaluate(() => { const s = document.getElementById('stage').getBoundingClientRect(); return [...document.querySelectorAll('#ui .sets button')].filter(b => b.offsetParent).every(b => { const r = b.getBoundingClientRect(); return r.width > 10 && r.left >= s.left - 1 && r.right <= s.right + 1; }); });
  ok(fit, 'кнопки настроек внутри кадра (снимок ' + shot + ')');
  // «Назад» пульта: браузер делает шаг по истории — игра должна закрыть настройки, а не уйти со страницы
  const url0 = pg.url(); await pg.goBack({ waitUntil: 'commit' }).catch(() => {}); await pg.waitForTimeout(900);
  ok(pg.url().split('#')[0] === url0.split('#')[0] && await pg.evaluate(() => !document.querySelector('#ui .sets') && !!window.__ns), '«Назад» закрыл настройки и не ушёл со страницы');
  // начать игру: «Новая смена»/первая кнопка меню
  for (let i = 0; i < 8; i++) await key('ArrowUp', 250);
  const first = await cur(); await key('Enter', 2500);
  const m = await pg.evaluate(() => window.__ns.mode);
  ok(m !== 'menu' || !!document.querySelector('#ui .screen'), `ОК на «${first}» — игра пошла (режим ${m})`);
  for (let i = 0; i < 6; i++) await key('Enter', 700);   // листаем реплики
  await pg.goBack({ waitUntil: 'commit' }).catch(() => {}); await pg.waitForTimeout(1000);
  const pz = await pg.evaluate(() => { const p = document.getElementById('pz'); return !!(p && !p.classList.contains('hide') && p.children.length) || !!window.__ns.userPause; });
  ok(pz, '«Назад» в игре — пауза');
  await pg.screenshot({ path: `${process.env.OUT || '/tmp'}/tv_${old ? 'old' : 'new'}_pause.png` });
  ok(!errs.length, 'ошибок страницы нет' + (errs.length ? ': ' + errs.slice(0, 3).join(' | ') : ''));
  await ctx.close();
}

(async () => {
  const br = await chromium.launch();
  await run(br, false); await run(br, true);
  await br.close(); console.log(fails ? '\nОШИБОК: ' + fails : '\nвсё прошло'); process.exit(fails ? 1 : 0);
})();
