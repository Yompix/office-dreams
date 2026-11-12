// сенсор: быстрые тапы по репликам не должны сами выбирать вопрос/вариант. node tap_through.js [сцена,сцена]
// На каждой сцене: телефон с сенсором, тапаем в одну точку (там, где вырастут кнопки) каждые 120 мс до появления меню, потом ещё 8 тапов «по инерции».
// Меню должно остаться на экране. Затем обычный тап по кнопке через .9 с — должен сработать.
const { chromium, devices } = require('/opt/node-tools/node_modules/playwright');
const list = (process.argv[2] || 'dg_call').split(',');
(async () => {
  const br = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  let bad = 0;
  for (const sc of list) {
    const ctx = await br.newContext({ ...devices['Pixel 5'] }), p = await ctx.newPage();
    p.on('pageerror', e => console.log('ошибка JS:', String(e).slice(0, 200)));
    await p.goto('http://127.0.0.1:8771/web/index.html?gfx=retro&lang=ru&voice=0');
    await p.waitForFunction(() => window.__ns && window.World, null, { timeout: 300000 });
    await p.waitForFunction(() => { const u = document.getElementById('ui'); return u && !u.classList.contains('hide') && u.querySelector('button'); }, null, { timeout: 120000 });   // главное меню готово (иначе игра сама вернётся в меню)
    await p.waitForTimeout(500); await p.evaluate(sc => window.__ns.startScene(sc), sc);
    const menu = () => p.evaluate(() => { const v = e => e && !e.classList.contains('hide') && e.querySelector('button'); const a = document.getElementById('act'), c = document.getElementById('choice'); return v(a) ? 'act' : v(c) ? 'choice' : ''; });
    // точка, где появится первая кнопка: узнаём после первого прохода, поэтому сначала тапаем в нижнюю треть
    const st = await p.evaluate(() => { const r = document.getElementById('stage').getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; }); let x = st[0] + st[2] / 2, y = st[1] + st[3] * .72, shown = '', n = 0;
    for (; n < 150 && !(shown = await menu()); n++) { await p.touchscreen.tap(x, y); await p.waitForTimeout(120); }
    if (!shown) { console.log(sc + ': меню не появилось за 150 тапов'); bad++; await ctx.close(); continue; }
    await p.waitForTimeout(900);   // кнопки доезжают на место
    const box = await p.evaluate(k => { const b = document.querySelector('#' + k + ' button'); const r = b.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2, b.textContent.slice(0, 40)]; }, shown);
    // повтор: перезапуск сцены и тапы ровно в место первой кнопки
    await p.evaluate(sc => window.__ns.startScene(sc), sc); shown = '';
    for (n = 0; n < 150 && !(shown = await menu()); n++) { await p.touchscreen.tap(box[0], box[1]); await p.waitForTimeout(120); }
    for (let i = 0; i < 8; i++) { await p.touchscreen.tap(box[0], box[1]); await p.waitForTimeout(110); }
    const still = await menu(); const ok1 = !!still;
    await p.waitForTimeout(900); const box2 = await p.evaluate(k => { const b = document.querySelector('#' + k + ' button'), r = b.getBoundingClientRect(), e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2); return [r.left + r.width / 2, r.top + r.height / 2, e && (e.id || e.tagName)]; }, still || shown); if (process.env.DBG) console.log('кнопка', box, box2);
    await p.touchscreen.tap(box2[0], box2[1]); await p.waitForTimeout(300);
    const after = await menu(), ok2 = !after || after !== still || true;
    const gone = await p.evaluate(k => { const e = document.getElementById(k); return e.classList.contains('hide') || !e.querySelector('button'); }, still || shown);
    console.log(`${sc}: тапов до меню ${n}; после быстрых тапов меню ${ok1 ? 'ОСТАЛОСЬ' : 'ПРОЛЕТЕЛО'}; обычный тап по «${box[2]}» ${gone ? 'сработал' : 'НЕ сработал'}`);
    if (!ok1 || !gone) bad++; await ctx.close(); }
  await br.close(); console.log(bad ? 'ПРОВАЛ: ' + bad : 'всё в порядке'); process.exit(bad ? 1 : 0);
})();
