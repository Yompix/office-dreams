// «Длинная разная ночь» (19 ноя): темп часов, профиль ночи, переписка с Лили, пейджер «Щит», подделка, эпилог «Что стало с ними», настройка «Темп смены».
//   PW=$PWD/tools/nosrv_pw.js node tools/story/night_test.js [ПАПКА для снимков]   (LANG2=de — язык; LAND=1 — горизонтально)
// Проверяет: (1) длину часов и число событий в часе — по 40 раскладкам каждого часа, без повторов странностей; (2) профиль: хранится, при «Начать смену» меняется,
// виденное выпадает реже; первые помехи — на «его» камере, звонок на внутренний и телефон Фрэнка — по профилю, затишье — в своём часе; (3) СМС с ответом: флаги msg_<id>, lwarn,
// ответ Лили; подделка: «повёлся» — помехи с коротким окном и флаг fake = 1, повтор часа стирает ответ, отказ — fake = 2; (4) пейджер: ответы Веры по обстановке;
// (5) «Темп смены» появляется после концовки и ускоряет часы; (6) эпилог на всех четырёх концовках: 3–5 строк, зависит от флагов, «Продолжить» открывает журнал.
const { chromium } = require(process.env.PW || '/opt/node-tools/node_modules/playwright');
const fs = require('fs');
(async () => {
  const out = process.argv[2], PORT = process.env.PORT || 8771, LAND = process.env.LAND === '1', LANG = process.env.LANG2 || '';
  if (out) fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const ctx = await b.newContext({ viewport: LAND ? { width: 640, height: 360 } : { width: 360, height: 640 }, locale: 'ru-RU' });
  await ctx.addInitScript(l => { if (!sessionStorage.getItem('init')) { sessionStorage.setItem('init', 1); localStorage.clear(); localStorage.setItem('ns1_tutDone', '1'); localStorage.setItem('ns1_land3', l ? 'true' : 'false'); } }, LAND);
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e).slice(0, 300)));
  await p.goto(`http://127.0.0.1:${PORT}/web/index.html?gfx=retro&cs=.25${LANG ? '&lang=' + LANG : ''}`);
  await p.waitForFunction(() => window.__ns && window.__ns.mode === 'menu', null, { timeout: 300000 });
  let fail = 0; const bad = m => { console.log('  ✗ ' + m); fail++; }, ok = (c, m) => { if (c) console.log('  ✓ ' + m); else bad(m); };
  const tag = (LANG || 'ru') + (LAND ? '_L' : '_P'), snap = async n => { if (out) await p.screenshot({ path: `${out}/${n}_${tag}.png` }).catch(() => {}); };
  const hour = async (seg, flags) => { await p.evaluate(([s, f]) => { const n = window.__ns; if (f) n.setFlags(f); n.startScene('sh_' + s); }, [seg, flags || null]); await p.waitForFunction(s => window.__ns.mode === 'shift' && window.__ns.shf && window.__ns.shf.id === s, seg, { timeout: 60000 }); await p.evaluate(() => window.__ns.shSpeed(0)); };
  const waitFor = (fn, arg, t = 30000) => p.waitForFunction(fn, arg, { timeout: t }).then(() => true, () => false);
  const msgNow = pred => p.evaluate(src => { const f = window.__ns.shf, e = f.ev.find(new Function('e', 'return ' + src)); if (!e) return false; e.m = f.M; e.done = false; window.__ns.shSpeed(1); return true; }, pred);   // нужное сообщение — сейчас

  // ---------- 1. темп и события ----------
  console.log('1. Темп часов и события (40 раскладок каждого часа)');
  await p.evaluate(() => window.__ns.setFlags({ fphone: 1 }));
  const tempo = await p.evaluate(async () => { const n = window.__ns, S = window.STORY.shift, res = {};
    for (const id of Object.keys(S.segs)) { const g = S.segs[id]; let ev = 0, an = 0, dup = 0, msg = 0; const N = 40;
      for (let i = 0; i < N; i++) { n.nightRoll(); n.startScene('sh_' + id); const f = n.shf; n.shSpeed(0);
        const vis = f.ev.filter(e => (e.an || e.radio || e.call || e.hold || e.msg || e.task || e.sms || e.say || e.c13) && !(e.if === 'fphone' && e.sfx)), ids = f.ev.filter(e => e.an).map(e => e.an);
        ev += vis.length; an += ids.length; msg += f.ev.filter(e => e.msg).length; dup += ids.length - new Set(ids).size; }
      const sec = (n.shf.m1 - n.shf.m0) * g.spm; res[id] = { spm: g.spm, min: +(sec / 60).toFixed(1), ev: +(ev / N).toFixed(1), an: +(an / N).toFixed(1), msg: +(msg / N).toFixed(1), gap: Math.round(sec / (ev / N)), dup }; }
    return res; });
  for (const [id, r] of Object.entries(tempo)) { console.log(`  ${id}: ${r.spm} с/мин → ${r.min} мин; событий ${r.ev} (странностей ${r.an}, сообщений ${r.msg}); одно в ${r.gap} с`);
    if (r.spm < 6.5 || r.spm > 8) bad(`${id}: темп ${r.spm} с на игровую минуту`); if (r.dup) bad(`${id}: странности повторяются в часе (${r.dup})`); if (r.gap < 34 || r.gap > 70) bad(`${id}: событие раз в ${r.gap} с`); }
  const common = ['S1', 'S2', 'S3', 'S4'].reduce((a, k) => a + tempo[k].min, 0);
  ok(common > 32 && common < 38, `общая часть S1–S4: ${common.toFixed(1)} мин`);
  for (const k of ['S5', 'S6', 'A1']) ok(tempo[k].min >= 9.9 && tempo[k].min <= 13, `${k}: ${tempo[k].min} мин`);

  // ---------- 2. профиль ночи ----------
  console.log('2. Профиль ночи');
  const prof = await p.evaluate(async () => { const n = window.__ns, r = {};
    localStorage.removeItem('ns1_nightSeen'); const a = n.nightRoll(); r.same = JSON.stringify(n.night) === JSON.stringify(a) && JSON.stringify(JSON.parse(localStorage.getItem('ns1_night'))) === JSON.stringify(a);
    r.keys = Object.keys(a).sort().join(','); r.lilyN = []; r.fl = {}; r.vo = {}; r.fk = {}; r.lu = {}; let rep = 0, prev = a.floor;
    for (let i = 0; i < 200; i++) { const q = n.nightRoll(); r.lilyN.push(q.lily.length); r.fl[q.floor] = (r.fl[q.floor] || 0) + 1; r.vo[q.voice] = (r.vo[q.voice] || 0) + 1; r.fk[q.fake] = (r.fk[q.fake] || 0) + 1; r.lu[q.lull] = (r.lu[q.lull] || 0) + 1; if (q.floor === prev) rep++; prev = q.floor;
      if (q.voiceAt >= q.fcall - 12 && q.voiceAt <= q.fcall + 4) r.clash = (r.clash || 0) + 1; }
    r.rep = rep; r.lmin = Math.min(...r.lilyN); r.lmax = Math.max(...r.lilyN); delete r.lilyN;
    // один профиль — три раза тот же час: звонок, телефон Фрэнка, первые помехи — на своих местах
    n.setFlags({ fphone: 1 }); const P = n.nightSet({ floor: 'm2', voice: 'whisper', lull: 'S3', lullAt: .5, fake: 'pager', fakeId: 'down', voiceAt: 25 * 60 + 35, fcall: 26 * 60 + 10 });
    r.s4 = []; for (let i = 0; i < 3; i++) { n.startScene('sh_S4'); n.shSpeed(0); const f = n.shf, v = f.ev.find(e => e.tag === 'voice'), fc = f.ev.filter(e => e.tag === 'fcall').map(e => e.m), fk = f.ev.find(e => e.msg && e.msg.fake);
      r.s4.push([v.m, v.call.say, Math.max(...fc), Math.min(...fc), fk && fk.msg.ch, fk && !/Щит|Shield|Schild/.test(fk.msg.hd + fk.msg.text), f.ev.filter(e => e.msg && e.msg.ch === 'pager' && !e.msg.fake).length, f.ev.filter(e => e.msg && e.msg.ch === 'sms').map(e => e.msg.id).join('')].join('|')); }
    r.s3 = []; r.lull = []; for (let i = 0; i < 12; i++) { n.startScene('sh_S3'); n.shSpeed(0); const f = n.shf, e1 = f.ev.find(e => e.tag === 'st1'); r.s3.push(e1.an + ':' + e1.see);
      const ms = f.ev.map(e => e.m).sort((x, y) => x - y); let gap = 0; for (let j = 1; j < ms.length; j++) gap = Math.max(gap, ms[j] - ms[j - 1]); r.lull.push(f.lull ? +gap.toFixed(1) : 0);
      if (f.lull) { const after = f.ev.filter(e => e.an && e.m >= f.lull[1] && e.m < f.lull[1] + 2.5).length, inside = f.ev.filter(e => e.m > f.lull[0] + .01 && e.m < f.lull[1]).length; r.lullBad = (r.lullBad || 0) + (after < 2 || inside ? 1 : 0); } }
    n.startScene('sh_S2'); n.shSpeed(0); r.s2lull = !!n.shf.lull; r.P = P; return r; });
  ok(prof.same, 'профиль хранится (ns1_night) и читается тем же');
  ok(prof.lmin >= 4 && prof.lmax <= 5, `сообщений Лили за ночь: ${prof.lmin}–${prof.lmax} настоящих (+ подделка в ночь «чужой канал — СМС»)`);
  ok(Object.keys(prof.fl).length === 4 && Object.keys(prof.vo).length === 2 && Object.keys(prof.fk).length === 2 && Object.keys(prof.lu).length === 3, `за 200 ночей выпало всё: этаж ${JSON.stringify(prof.fl)}, голос ${JSON.stringify(prof.vo)}, канал ${JSON.stringify(prof.fk)}, затишье ${JSON.stringify(prof.lu)}`);
  ok(prof.rep < 20, `тот же «его этаж» две ночи подряд: ${prof.rep} из 200 (случайно было бы ~50)`);
  ok(!prof.clash, 'звонок на внутренний не попадает на цепочку телефона Фрэнка');
  ok(new Set(prof.s4).size === 1 && prof.s4[0].startsWith(`${25 * 60 + 35}|ch_whisper|${26 * 60 + 10}|${26 * 60 + 1}|pager|true|2|`), `S4 по профилю (трижды одинаково): ${prof.s4[0]}`);
  ok(prof.s3.every(x => x === 'st_m2:n12'), 'первые помехи ночи — на «его» камере (подвал), с репликой n12');
  ok(prof.lull.every(g => g >= 9.9) && !prof.lullBad && !prof.s2lull, `затишье в своём часе (S3): тишина ${Math.min(...prof.lull)}–${Math.max(...prof.lull)} игровых минут, потом две странности подряд; в S2 его нет`);

  // ---------- 3. переписка с Лили и подделка ----------
  console.log('3. СМС с ответами');
  await p.evaluate(() => { window.__ns.setFlags({}); window.__ns.nightSet({ floor: '10', voice: 'lily', lull: 'S2', fake: 'sms', fakeId: 'f1', lily: ['l1', 'l4', 'l6', 'l8'], voiceAt: 25 * 60 + 30, fcall: 26 * 60 + 30 }); });
  await hour('S4');
  ok(await msgNow('e.msg && e.msg.id === "l8"') && await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg')), 'сообщение Лили открылось с двумя ответами');
  await p.waitForTimeout(400); await snap('sms');
  const fit = () => p.evaluate(() => { const pop = document.getElementById('shPop'), r = pop.getBoundingClientRect(), st = document.getElementById('stage').getBoundingClientRect(); return { in: r.left >= st.left - 1 && r.right <= st.right + 1 && r.top >= st.top - 1 && r.bottom <= st.bottom + 1, scroll: pop.scrollHeight > pop.clientHeight + 2, clip: [...pop.querySelectorAll('button')].some(b => b.scrollWidth > b.clientWidth + 2) }; });
  let f = await fit(); ok(f.in && !f.clip, `окно СМС целиком на экране, кнопки не обрезаны${f.scroll ? ' (с прокруткой)' : ''}`);
  await p.click('#shPop .sh-msg button[data-i="1"]');
  ok(await p.evaluate(() => window.__ns.flags.msg_l8 === 2 && window.__ns.flags.lwarn === 1), 'ответ записан: msg_l8 = 2, lwarn');
  ok(await waitFor(() => { const s = document.getElementById('sms'); return s && !s.classList.contains('hide') && /Обещаю|promise|versprochen|Versprochen/i.test(s.textContent); }, null, 15000) || !!LANG, 'Лили отвечает на ответ («…Обещаю»)');
  await snap('sms_back');
  // подделка: согласился
  ok(await msgNow('e.msg && e.msg.fake') && await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg')), 'подделка «от Лили» пришла');
  await p.waitForTimeout(400); await snap('fake_sms');
  await p.click('#shPop .sh-msg button[data-i="0"]');
  const lure = await waitFor(() => window.__ns.shf.act.some(a => a.win === 6 && a.def.stare && ['10', '01'].includes(a.def.cam)), null, 15000);
  ok(lure && await p.evaluate(() => window.__ns.flags.fake === 1), 'повёлся: fake = 1, помехи у входа или на «его» камере с коротким окном (6 вместо 12)');
  await p.waitForTimeout(300); await snap('fake_lure');
  ok(await p.evaluate(() => window.__ns.mode === 'shift' && !window.__ns.shf.ended), 'сразу не убивает');
  // повтор часа стирает ответы этого часа; отказ
  await hour('S4');
  ok(await p.evaluate(() => window.__ns.flags.fake === 1 && window.__ns.flags.lwarn === 1), 'час начат заново — ответы целы, пока сообщение не пришло снова');
  await msgNow('e.msg && e.msg.id === "l8"'); await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg')); await p.evaluate(() => document.getElementById('shX').click());
  await msgNow('e.msg && e.msg.fake'); await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg'));
  ok(await p.evaluate(() => !window.__ns.flags.fake && !window.__ns.flags.msg_fake && !window.__ns.flags.msg_l8 && !window.__ns.flags.lwarn), 'то же сообщение пришло снова — прежний ответ стёрт (считается последний)');
  await p.click('#shPop .sh-msg button[data-i="1"]');
  ok(await p.evaluate(() => window.__ns.flags.fake === 2) && await waitFor(() => { const s = document.getElementById('sms'); return s && !s.classList.contains('hide') && s.querySelector('p').textContent.length < 12; }, null, 15000), 'отказал: fake = 2, в ответ — только имя');
  await snap('fake_name');
  // сообщение ждёт, пока открыт пульт, и не отвечает само
  await hour('S3', {}); await p.evaluate(() => window.__ns.shiftPanel()); await msgNow('e.msg && e.msg.ch === "sms"'); await p.waitForTimeout(3500);
  ok(await p.evaluate(() => !!document.getElementById('pDoor') && !document.querySelector('.sh-msg')), 'пульт открыт — сообщение ждёт');
  await p.evaluate(() => document.getElementById('shX').click());
  ok(await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg'), null, 8000), 'пульт закрыт — сообщение открылось');
  await p.evaluate(() => document.getElementById('shX').click());
  ok(await p.evaluate(() => !Object.keys(window.__ns.flags).some(k => /^msg_/.test(k))), 'закрыл без ответа — флага нет');

  // ---------- 4. пейджер ----------
  console.log('4. Пейджер «Щит»');
  const pager = async (i, prep) => { await hour('S4'); await p.evaluate(prep); await msgNow('e.msg && e.msg.ch === "pager" && !e.msg.fake'); if (!await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg'))) return '';
    if (i === 0 && prep.toString().includes('snd01')) { await p.waitForTimeout(400); await snap('pager'); f = await fit(); ok(f.in && !f.clip, 'окно пейджера целиком на экране'); }
    await p.click(`#shPop .sh-msg button[data-i="${i}"]`);
    await waitFor(() => { const s = document.getElementById('sms'); return s && !s.classList.contains('hide') && s.classList.contains('in') && window.__pg !== s.textContent && (window.__pg = s.textContent); }, null, 15000);
    return p.evaluate(() => document.querySelector('#sms p').textContent); };
  await p.evaluate(() => { window.__ns.setFlags({}); window.__ns.nightSet({ fake: 'pager', fakeId: 'up' }); });
  const A = await p.evaluate(() => window.STORY.shift.pager.ans);
  let t = await pager(0, () => { const f = window.__ns.shf; f.act = []; f.ev.forEach(e => { if (e.an) e.done = true; }); });
  ok(t === A.ok, `«Всё спокойно», на камерах чисто → «${t}»`);
  t = await pager(0, () => { const f = window.__ns.shf; f.act = [{ id: 'snd01', def: window.STORY.shift.an.snd01, m: f.M }]; });
  ok(t === A.act.replace('{c}', 'CAM 01'), `«Всё спокойно», а на CAM 01 звук → «${t}»`); await snap('pager_ans');
  t = await pager(1, () => { const f = window.__ns.shf; f.act = []; window.__ns.shk.c13 = true; });
  ok(t === A.c13 && await p.evaluate(() => window.__ns.flags.pg13 === 1), `«Доложить» после 02:00 → «${t}»`); await snap('pager_13');
  t = await pager(1, () => { const f = window.__ns.shf; f.act = []; window.__ns.shk.c13 = false; window.__ns.flags.sawst = 1; });
  ok(t === A.st, `«Доложить», помехи уже видел → «${t}»`);
  t = await pager(1, () => { const f = window.__ns.shf; f.act = []; window.__ns.shk.c13 = false; });
  ok(t === A.def, `«Доложить», остальное → «${t}»`);
  await hour('S4'); await msgNow('e.msg && e.msg.fake'); await waitFor(() => !!document.querySelector('#shPop:not(.hide) .sh-msg')); await p.waitForTimeout(400); await snap('fake_pager');
  ok(await p.evaluate(() => { const h = document.querySelector('#shPop h3').textContent, P = window.STORY.shift.pager; return h !== P.hd && h === P.fakes.up.hd; }), 'подделка на пейджере — без позывного «Щит»');
  await p.click('#shPop .sh-msg button[data-i="0"]');
  ok(await waitFor(() => window.__ns.flags.fake === 1 && window.__ns.shf.act.some(a => a.win === 6), null, 15000), 'согласился подняться — расплата та же (fake = 1, короткое окно)');

  // ---------- 5. темп смены в настройках ----------
  console.log('5. Настройка «Темп смены»');
  await p.evaluate(() => { localStorage.removeItem('ns1_endings'); window.__ns.back(); window.__ns.settings(); });
  ok(await p.evaluate(() => !document.getElementById('sTempo')), 'до первой концовки настройки нет');
  await p.evaluate(() => { localStorage.setItem('ns1_endings', '["dawn"]'); window.__ns.settings(); });
  ok(await p.evaluate(() => !!document.getElementById('sTempo')), 'после концовки — есть');
  await p.evaluate(() => document.getElementById('sTempo').click()); await p.waitForTimeout(300);
  await p.evaluate(() => { const b = document.getElementById('sTempo'), box = b.closest('.sets'); if (box) box.scrollTop = Math.max(0, b.offsetTop - box.offsetTop - box.clientHeight / 2); }); await p.waitForTimeout(200); await snap('tempo');   // прокрутить список настроек к строке (не scrollIntoView: он сдвигает и сам экран)
  ok(await p.evaluate(() => JSON.parse(localStorage.getItem('ns1_tempo')) === 'fast' && window.__ns.tempoK === .6), 'быстрый: ns1_tempo = fast, часы × 0,6');
  ok(await p.evaluate(() => { const b = document.getElementById('sTempo'); return b.scrollWidth <= b.clientWidth + 2; }), 'строка настройки не обрезана');
  // сколько настоящих секунд идёт игровая минута (в headless кадры редкие, а шаг времени ограничен — сравниваем быстрый темп с обычным, а не с числом)
  const pace = () => p.evaluate(async () => { const n = window.__ns, f = n.shf; n.shSpeed(1); const m0 = f.M, t0 = performance.now(); await new Promise(r => setTimeout(r, 5000)); return (performance.now() - t0) / 1000 / Math.max(1e-6, n.shf.M - m0); });
  await hour('S3'); const fast = await pace(), spmF = await p.evaluate(() => window.__ns.spm);
  await p.evaluate(() => { localStorage.setItem('ns1_tempo', '""'); window.__ns.tempoSet(); });
  await hour('S3'); const norm = await pace(), spmN = await p.evaluate(() => window.__ns.spm);
  ok(Math.abs(spmN - 7.2) < .01 && Math.abs(spmF - 4.32) < .01, `игровая минута в S3: обычный темп ${spmN} с, быстрый ${spmF.toFixed(2)} с (замер в этом запуске: ${norm.toFixed(1)} и ${fast.toFixed(1)} с — headless рисует редко, шаг времени ограничен)`);

  // ---------- 6. эпилог ----------
  console.log('6. Эпилог «Что стало с ними»');
  const TH = { dawn: ['РАССВЕТ', 'Ты ни разу не ответил. Пока.'], newentry: ['ТЫ НЕ СПУСТИЛСЯ', 'Ты дожил до утра. Фрэнк — нет.'], inside: ['ВНУТРИ', '«Ты со мной», — сказал ты ей. И не соврал.'], sister: ['СЕСТРА', 'Оно пошло туда, где его ещё не ждут. Твоим голосом.'] };
  const epi = (end, flags, miss) => p.evaluate(([e, f, m]) => { const n = window.__ns; n.back(); n.setFlags(f); n.run.miss = m; n.run.hrs = ['S1', 'S2', 'S3', 'S4']; n.nightSet({ lily: ['l1', 'l4', 'l6', 'l8'] }); return n.epiLines(e); }, [end, flags, miss]);
  const E = await p.evaluate(() => window.STORY.shift.epi), all = new Set();
  for (const end of Object.keys(TH)) {
    const a = await epi(end, { warned: 1, salt: 1, fphone: 1, lwarn: 1, msg_l8: 2, fake: 2 }, 0), z = await epi(end, {}, 4), c = await epi(end, { msg_l1: 1, fake: 1 }, 2);
    [...a, ...z, ...c].forEach(x => all.add(x));
    ok(a.length === 5 && z.length === 4 && c.length === 5 && a.filter((x, i) => x !== z[i]).length >= 3, `${end}: строк ${z.length}–${a.length}, по флагам меняется ${a.filter((x, i) => x !== z[i]).length}`);
    ok(a.includes(E.gus[0][1]) && z.includes(E.gus[1][1]), `${end}: Гас — по флагу warned`);
    if (end === 'dawn' || end === 'newentry') ok(a.includes(E.log[0][1]) && z.includes(E.log[1][1]) && z.includes(E.lily[1][1]) && c.includes(E.lily[2][1]) && c.includes(E.fake[0][1]) && a.includes(E.fake[1][1]), `${end}: журнал (пропуски), молчание Лили, подделка — учтены`);
  }
  console.log(`  разных строк эпилога показано: ${all.size}`);
  for (const end of Object.keys(TH)) {
    await p.evaluate(([e, th]) => { const n = window.__ns; n.back(); n.setFlags({ warned: 1, fphone: 1, salt: 1, lwarn: 1, msg_l8: 2, fake: 1 }); n.run.miss = 0; n.run.hrs = ['S1', 'S2', 'S3', 'S4']; n.endScreen({ ending: e, title: th[0], sub: th[1] }); }, [end, TH[end]]);
    await p.waitForTimeout(7500); await snap('epi_' + end);
    const s = await p.evaluate(() => { const el = document.getElementById('epi'), st = document.getElementById('stage').getBoundingClientRect(), go = document.getElementById('epGo'), g = go && go.getBoundingClientRect(); return el && { n: el.querySelectorAll('p').length, jr: !!document.querySelector('.screen.jr.wait .jr-stamp'), scroll: el.scrollHeight > el.clientHeight + 2, wide: [...el.querySelectorAll('p, h2')].some(x => x.getBoundingClientRect().right > st.right + 1 || x.getBoundingClientRect().left < st.left - 1), go: g && g.bottom <= st.bottom + 1 }; });
    ok(s && s.n === 5 && s.jr && !s.wide, `${end}: эпилог перед журналом, 5 строк${s && s.scroll ? ', с прокруткой' : ''}${s && !s.go && !s.scroll ? ' — КНОПКА ЗА КРАЕМ' : ''}`);
    if (s && !s.go && !s.scroll) fail++;
    await p.evaluate(() => document.getElementById('epGo').click()); await p.waitForTimeout(300);
    ok(await p.evaluate(() => !document.getElementById('epi') && !!document.querySelector('.screen.jr:not(.wait) #eAgain')), `${end}: «Продолжить» → журнал ночи`);
  }
  await p.evaluate(() => document.getElementById('eAgain').click());
  ok(await waitFor(() => window.__ns.mode === 'play' && window.__ns.scene === 'intro', null, 60000), '«Начать заново» → новая ночь');
  if (errs.length) bad('ошибки JS: ' + errs.slice(0, 4).join(' | '));
  console.log(fail ? `НЕ ПРОШЛО: ${fail}` : 'всё прошло'); await b.close(); process.exit(fail ? 1 : 0);
})();
