// Автопроверка ПК-версии: xvfb-run -a -s "-screen 0 1920x1200x24" node tools/desktop/check.js ПАПКА_LINUX_ТЕСТ ПАПКА_СНИМКОВ
// (сборка: node tools/desktop/build.js linux --test --steam --out … → ПАПКА/steam-linux; без --steam — ПАПКА/hush-till-six-linux, проверка Steam пропускается)
// Проверяет: окно и меню, «Выйти из игры» есть, доната нет, звук идёт, сцена играет без ошибок; ПК-настройки (весь экран, звук в фоне);
// геймпад (поддельный navigator.getGamepads): титул, меню, настройки, реплики, пауза, выбор, удержание «НЕ ДЫШИ», смерть → экран смерти, камеры смены LB/RB;
// достижения пишутся (achievements.json); Steam: steam_appid.txt без запущенного Steam — игра всё равно идёт; окно помнит размер и место;
// сохранения переживают перезапуск и копируются в cloud/save.json, более новая копия (Steam Cloud с другого ПК) подхватывается; «Выйти из игры» закрывает программу.
// Снимки интерфейса с рамкой геймпада — 1280×800 (Steam Deck) и 1920×1080.
const path = require('path'), fs = require('fs');
const { _electron } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const [dir, out] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
const exe = path.join(dir, 'hush-till-six'), flags = ['--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'];   // в контейнере: root и нет видеокарты
const prof = path.join(out, 'profile'); fs.rmSync(prof, { recursive: true, force: true });   // свой профиль игрока: проверка с чистого листа
// «Ретро» и без обучения: SwiftShader в контейнере рисует «Кино» по кадру в секунды. Заодно проверка, что сохранения из cloud/save.json подхватываются с первого запуска
fs.mkdirSync(path.join(prof, 'cloud'), { recursive: true }); fs.writeFileSync(path.join(prof, 'cloud/save.json'), JSON.stringify({ t: Date.now(), ls: { ns1_gfx: '"retro"', ns1_tutDone: '1' } }));
const steamBuild = fs.existsSync(path.join(dir, 'resources/app.asar.unpacked/node_modules/steamworks.js')), appid = path.join(dir, 'steam_appid.txt');
const fail = m => { console.log('✗', m); process.exitCode = 1; }, ok = m => console.log('✓', m), check = (c, m, bad) => c ? ok(m) : fail(bad || m);
const J = f => { try { return JSON.parse(fs.readFileSync(path.join(prof, f), 'utf8')); } catch { return null; } };
let log = '';
async function open(errs) {
  const app = await _electron.launch({ executablePath: exe, args: flags, env: Object.assign({}, process.env, { NS_WINDOWED: '1', NS_USERDATA: prof, NS_QUERY: 'cs=0.5' }), timeout: 120000 });   // cs=0.5 — холст вдвое меньше: в контейнере SwiftShader и чужая нагрузка
  app.process().stdout.on('data', d => { log += d; }); app.process().stderr.on('data', d => { log += d; });
  const p = await app.firstWindow();
  p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  p.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 200)); });
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  await p.evaluate(() => {   // поддельный геймпад: кнопки и стики задаёт проверка
    const b = () => ({ pressed: false, touched: false, value: 0 }), pad = { id: 'Test pad (STANDARD GAMEPAD)', index: 0, connected: true, mapping: 'standard', timestamp: 0, buttons: Array.from({ length: 17 }, b), axes: [0, 0, 0, 0] };
    window.__fakePad = pad; Object.defineProperty(navigator, 'getGamepads', { configurable: true, value: () => [pad, null, null, null] });
  });
  return { app, p };
}
const A = 0, B = 1, LB = 4, RB = 5, START = 9, UP = 12, DOWN = 13;
const btn = (p, i, on) => p.evaluate(([i, on]) => { const b = window.__fakePad.buttons[i]; b.pressed = on; b.value = on ? 1 : 0; }, [i, on]);
const frames = (p, n) => p.evaluate(n => new Promise(r => { const f0 = window.__pad.frames; const w = () => window.__pad.frames - f0 >= n ? r() : setTimeout(w, 15); w(); }), n);   // pad.js опрашивает геймпад раз в кадр
async function press(p, i, hold = 0) { await btn(p, i, true); await frames(p, 1); if (hold) await p.waitForTimeout(hold); await btn(p, i, false); await frames(p, 2); await p.waitForTimeout(120); }
const curId = p => p.evaluate(() => { const c = window.__pad && window.__pad.cur; return c ? (c.id || c.className || c.tagName) + (c.textContent ? ':' + c.textContent.trim().slice(0, 24) : '') : null; });
const until = (p, fn, arg, ms = 60000) => p.waitForFunction(fn, arg, { timeout: ms, polling: 100 }).then(() => true, () => false);
async function skipTo(p, mode, n = 60) { for (let i = 0; i < n; i++) { if (await p.evaluate(() => window.__ns.mode) === mode) return true; await p.evaluate(() => window.__ns.skipBeat()); await p.waitForTimeout(700); } return false; }
// рамка встала в новом слое и A уже «взведена» (pad.js не жмёт кнопку, появившуюся меньше 0,3 с назад)
const padReady = async (p, sel) => { const okk = await until(p, s => window.__pad.cur && window.__pad.cur.matches(s), sel, 120000); await frames(p, 2); await p.waitForTimeout(500); return okk; };
const winBounds = app => app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getBounds());
const shot = (p, f) => p.screenshot({ path: path.join(out, f), timeout: 240000 });

(async () => {
  const errs = [];
  if (steamBuild) fs.writeFileSync(appid, '480\n');   // как у разработчика: Steam не запущен — игра должна пойти и так
  let { app, p } = await open(errs);
  ok('окно и меню: ' + await p.title() + ', адрес ' + p.url());
  const st = await p.evaluate(() => ({ desk: !!window.nsDesktop, quit: !!document.getElementById('mQuit'), sup: !!document.querySelector('.msup'), pad: !!window.__pad }));
  check(st.desk, 'мостик nsDesktop есть', 'нет window.nsDesktop'); check(st.quit, 'кнопка «Выйти из игры» есть', 'нет кнопки «Выйти из игры»');
  check(!st.sup, 'доната в меню нет', 'в меню кнопка доната'); check(st.pad, 'pad.js загружен', 'нет pad.js (window.__pad)');
  console.log('  кадров в секунду в меню:', (await p.evaluate(() => new Promise(r => { const f0 = window.__pad.frames, t0 = performance.now(); setTimeout(() => r((window.__pad.frames - f0) / (performance.now() - t0) * 1000), 2000); }))).toFixed(1));
  check(await p.evaluate(() => JSON.parse(localStorage.getItem('ns1_gfx'))) === 'retro', 'сохранения из cloud/save.json подхвачены при первом запуске', 'cloud/save.json не подхватился при первом запуске');
  if (steamBuild) { log += fs.existsSync(path.join(prof, 'steam.log')) ? fs.readFileSync(path.join(prof, 'steam.log'), 'utf8') : ''; const m = /Steam: [^\n]*/.exec(log); check(m && /без Steam/.test(m[0]) && !/Cannot find module|cannot open shared object/i.test(m[0]), 'Steam не запущен — игра идёт без него (' + (m ? m[0].slice(0, 110) : 'нет строки в логе') + ')', 'Steam: ' + (m ? m[0] : 'нет строки в логе')); }
  // геймпад: титул → меню → настройки → назад
  const gated = await p.evaluate(() => !!document.querySelector('#ui .title.gate'));   // под автотестом титул может открыться сам (navigator.webdriver)
  if (gated) await press(p, A); else await press(p, DOWN), await press(p, UP);
  await p.waitForTimeout(700);
  check(await p.evaluate(() => !!document.querySelector('#ui .title.open') && document.body.classList.contains('pad')), gated ? 'геймпад: A открыл меню, рамка включена' : 'титул открылся сам, рамка включена', 'геймпад: меню не открылось');
  const c0 = await curId(p); check(/mNew|mCont/.test(c0 || ''), 'геймпад: рамка на первой кнопке меню (' + c0 + ')', 'геймпад: рамка не на первой кнопке: ' + c0);
  await press(p, DOWN); const c1 = await curId(p); check(c1 && c1 !== c0, 'геймпад: вниз — следующая кнопка (' + c1 + ')', 'геймпад: рамка не сдвинулась: ' + c1);
  for (let i = 0; i < 4 && !/mSet/.test(await curId(p) || ''); i++) await press(p, DOWN);
  await shot(p, '1menu_pad.png');
  await press(p, A); await p.waitForTimeout(500);
  const sets = await p.evaluate(() => ({ full: !!document.getElementById('sFull'), bg: !!document.getElementById('sBg'), txt: (document.getElementById('sBg') || {}).textContent }));
  check(sets.full && sets.bg, 'настройки ПК: «Во весь экран» и «Звук в фоне» (' + sets.txt + ')', 'нет строк «Во весь экран»/«Звук в фоне» в настройках');
  check(/sLang/.test(await curId(p) || ''), 'геймпад: в настройках рамка на первой строке', 'геймпад: в настройках рамка не на месте: ' + await curId(p));
  await p.click('#sBg'); await p.waitForTimeout(400);
  check((J('settings.json') || {}).bg === true, '«Звук в фоне» сохранился (settings.json)', '«Звук в фоне» не дошёл до settings.json: ' + JSON.stringify(J('settings.json')));
  await p.click('#sBg'); await p.waitForTimeout(300);
  await press(p, B); await p.waitForTimeout(400);
  check(await p.evaluate(() => !!document.querySelector('#ui .title.open .mlist')), 'геймпад: B — назад в меню', 'геймпад: B не вернул в меню');
  // сохранения и окно: запомнить
  await p.evaluate(() => { localStorage.setItem('ns1_desk_probe', JSON.stringify(42)); localStorage.setItem('ns1_tutDone', '1'); });
  await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setBounds({ x: 60, y: 40, width: 1180, height: 700 })); await p.waitForTimeout(1200);
  const b0 = await winBounds(app);
  // сцена, звук, реплики геймпадом
  await p.evaluate(() => window.__ns.startScene('intro')); await p.waitForTimeout(3500);
  const ac = await p.evaluate(() => window.__ns.audio); check(ac === 'running', 'звук идёт', 'звук: ' + ac);
  const bi0 = await p.evaluate(() => window.__ns.bi); for (let i = 0; i < 4; i++) { await press(p, A); await p.waitForTimeout(500); }
  const bi1 = await p.evaluate(() => window.__ns.bi); check(bi1 > bi0, `геймпад: A листает кадры (${bi0} → ${bi1})`, `геймпад: A не листает кадры (${bi0} → ${bi1})`);
  await frames(p, 2); await shot(p, '2intro.png');   // после склейки кадр сначала чёрный — пара кадров, чтобы снимок был с картинкой
  await press(p, START); await p.waitForTimeout(400);
  const pz = await p.evaluate(() => !document.getElementById('pz').classList.contains('hide') && window.__ns.userPause);
  check(pz && /pzGo/.test(await curId(p) || ''), 'геймпад: Start — пауза, рамка на «Продолжить»', 'геймпад: пауза не открылась');
  await press(p, A); await p.waitForTimeout(400);
  check(await p.evaluate(() => document.getElementById('pz').classList.contains('hide') && !window.__ns.userPause), 'геймпад: A — продолжить', 'геймпад: пауза не закрылась');
  // выбор: рамка, вниз, A
  await p.evaluate(() => window.__ns.startScene('intro', true));
  check(await skipTo(p, 'choice'), 'выбор появился', 'выбор не появился');
  await padReady(p, '#choice button'); const ch0 = await curId(p); await press(p, DOWN); const ch1 = await curId(p);
  check(/^pfocus:1/.test(ch0 || '') && /^pfocus:2/.test(ch1 || ''), `геймпад: рамка по вариантам (${ch0} → ${ch1})`, `геймпад: рамка в выборе: ${ch0} → ${ch1}`);
  await press(p, A); await p.waitForTimeout(500);
  const sc = await p.evaluate(() => window.__ns.scene); check(sc === 'keys_ask', 'геймпад: A выбрал второй вариант (' + sc + ')', 'геймпад: выбор не тот: ' + sc);
  // удержание «НЕ ДЫШИ»: держать A — кнопка зажата; отпустить раньше времени — смерть №5 → экран смерти, достижение
  await p.evaluate(() => window.__ns.startScene('hide', true));
  check(await skipTo(p, 'mg') && await until(p, () => document.querySelector('#mg .bigbtn')), 'мини-игра «НЕ ДЫШИ» открылась', 'мини-игра «НЕ ДЫШИ» не открылась');
  await padReady(p, '.bigbtn'); await btn(p, A, true); await frames(p, 3); await p.waitForTimeout(1200);
  check(await p.evaluate(() => !!document.querySelector('#mg .bigbtn.held')), 'геймпад: держать A — «НЕ ДЫШИ» зажата', 'геймпад: удержание не сработало');
  await shot(p, '3hold.png');
  await btn(p, A, false);
  check(await until(p, () => window.__ns.scene && window.__ns.scene.startsWith('over_'), null, 15000), 'отпустил раньше времени — смерть (' + await p.evaluate(() => window.__ns.scene) + ')', 'отпустил — смерти нет');
  check(await skipTo(p, 'end'), 'экран смерти', 'экран смерти не показался');
  await p.waitForTimeout(800); check(/oRetry/.test(await curId(p) || ''), 'геймпад: на экране смерти рамка на «Попробовать снова»', 'рамка: ' + await curId(p));
  await p.waitForTimeout(500); const ach = J('achievements.json') || [];
  check(ach.includes('DEATH_05'), 'достижение записано: ' + ach.join(', '), 'достижения не записаны: ' + JSON.stringify(ach));
  // смена: LB/RB — камеры, X — записать (окно), B — закрыть
  await p.evaluate(() => window.__ns.startScene('sh_S1'));
  check(await until(p, () => window.__ns.mode === 'shift' && window.__ns.shf), 'смена началась', 'смена не началась');
  await p.waitForTimeout(800); const cam0 = await p.evaluate(() => window.__ns.shf.cam);
  await press(p, RB); const cam1 = await p.evaluate(() => window.__ns.shf.cam); await press(p, LB); const cam2 = await p.evaluate(() => window.__ns.shf.cam);
  check(cam1 !== cam0 && cam2 === cam0, `геймпад: RB/LB — камеры ${cam0} → ${cam1} → ${cam2}`, `геймпад: камеры не переключаются ${cam0} → ${cam1} → ${cam2}`);
  await press(p, 2); await p.waitForTimeout(300);
  const pop = await p.evaluate(() => { const e = document.getElementById('shPop'); return !!e && !e.classList.contains('hide'); }); const pc = await curId(p);
  check(pop && pc && !/sh-x/.test(pc), 'геймпад: X — «Записать», рамка в окне (' + pc + ')', 'геймпад: X не открыл запись');
  await shot(p, '4shift_log.png');
  await press(p, B); await p.waitForTimeout(300);
  check(await p.evaluate(() => document.getElementById('shPop').classList.contains('hide')), 'геймпад: B закрыл окно смены', 'геймпад: B не закрыл окно смены');
  // снимки интерфейса: Steam Deck 1280×800 и 1920×1080
  for (const [w, h] of [[1280, 800], [1920, 1080]]) {
    await app.evaluate(({ BrowserWindow }, [w, h]) => BrowserWindow.getAllWindows()[0].setContentSize(w, h), [w, h]); await p.waitForTimeout(900);
    await press(p, LB); await shot(p, `5shift_${w}x${h}.png`);
  }
  await app.evaluate(({ BrowserWindow }, b) => BrowserWindow.getAllWindows()[0].setBounds(b), b0); await p.waitForTimeout(1200);
  await app.close();
  // после выхода: файлы профиля
  const cfg = J('settings.json') || {}, cl = J('cloud/save.json');
  check(cfg.win && cfg.win.width === b0.width && cfg.win.height === b0.height, `размер окна записан (${cfg.win && cfg.win.width}×${cfg.win && cfg.win.height})`, 'размер окна не записан: ' + JSON.stringify(cfg));
  check(cl && cl.ls && cl.ls.ns1_desk_probe === '42', 'сохранения скопированы в cloud/save.json', 'нет копии сохранений: ' + JSON.stringify(cl).slice(0, 200));
  if (cl) { cl.t = Date.now() + 60000; cl.ls.ns1_desk_probe = '43'; fs.writeFileSync(path.join(prof, 'cloud/save.json'), JSON.stringify(cl)); }   // «Steam Cloud принёс сохранение с другого ПК»
  ({ app, p } = await open(errs));
  const b1 = await winBounds(app);
  check(b1.x === b0.x && b1.y === b0.y && b1.width === b0.width && b1.height === b0.height, `окно на прежнем месте: ${b1.width}×${b1.height} @ ${b1.x},${b1.y}`, `окно не на месте: было ${JSON.stringify(b0)}, стало ${JSON.stringify(b1)}`);
  const v = await p.evaluate(() => localStorage.getItem('ns1_desk_probe'));
  check(v === '43', 'сохранения после перезапуска — из более новой копии (Steam Cloud)', 'сохранения после перезапуска: ' + v);
  for (const [w, h] of [[1280, 800], [1920, 1080]]) {
    await app.evaluate(({ BrowserWindow }, [w, h]) => BrowserWindow.getAllWindows()[0].setContentSize(w, h), [w, h]); await p.waitForTimeout(700);
    if (w === 1280 && await p.evaluate(() => !!document.querySelector('#ui .title.gate'))) { await press(p, A); await p.waitForTimeout(600); } else if (w === 1280) await press(p, DOWN);
    await shot(p, `6menu_${w}x${h}.png`);
  }
  for (let i = 0; i < 5 && !/mSet/.test(await curId(p) || ''); i++) await press(p, DOWN);
  await press(p, A); await p.waitForTimeout(500); await shot(p, '7settings_1920x1080.png');
  await press(p, B); await p.waitForTimeout(500);
  const closed = new Promise(r => app.on('close', () => r(true)));
  await p.click('#mQuit').catch(() => {});
  (await Promise.race([closed, new Promise(r => setTimeout(() => r(false), 15000))])) ? ok('«Выйти из игры» закрыло программу') : (fail('программа не закрылась'), await app.close());
  if (steamBuild) fs.rmSync(appid, { force: true });
  // «shader ?: …» — отладочная строка движка (R.debug.onShaderError), когда SwiftShader в контейнере не собирает шейдер: это работа агента графики, не обёртки
  const sh = errs.filter(e => /^console: shader /.test(e)), real = errs.filter(e => !/favicon/.test(e) && !/^console: shader /.test(e));
  if (sh.length) console.log(`  (шейдеры под SwiftShader: ${sh.length} строк «shader ?:» — не ошибка ПК-обёртки)`);
  real.length ? fail('ошибки на странице:\n  ' + real.join('\n  ')) : ok('ошибок на странице нет');
})().catch(e => { if (steamBuild) fs.rmSync(appid, { force: true }); fail(e.stack || e); process.exit(1); });
