// Проверка сценария «Молчи до шести»: все переходы, кадры, реплики и звуки существуют.
//   node tools/story/check_story.js
// Кадры берутся из scenes.js (SH.имя = … и «имя: { loc: …» в таблице), реплики — из timing.json / timing_en.json,
// звуки — файлы snd/<ключ>.mp3. Пишет, чего не хватает; код выхода 1, если сломан переход или нет кадра.
const fs = require('fs'), path = require('path');
const G = path.join(__dirname, '../../public/games/nightshift');
const window = {}; new Function('window', fs.readFileSync(path.join(G, 'story.js'), 'utf8'))(window);
const S = window.STORY, scenes = S.scenes;
const src = fs.readFileSync(path.join(G, 'scenes.js'), 'utf8');
const shots = new Set([...src.matchAll(/^SH\.([a-z_0-9]+) = /gm)].map(m => m[1]).concat([...src.matchAll(/^  ([a-z_0-9]+): \{ loc: /gm)].map(m => m[1])));
const alias = (src.match(/const ALIAS = \{([^}]*)\}/) || [, ''])[1];
for (const m of alias.matchAll(/([a-z_0-9]+):/g)) shots.add(m[1]);
const tim = { ru: JSON.parse(fs.readFileSync(path.join(G, 'timing.json'), 'utf8')), en: JSON.parse(fs.readFileSync(path.join(G, 'timing_en.json'), 'utf8')) };
const timX = {}; for (const f of fs.readdirSync(G)) { const m = /^timing_([a-z]{2})\.json$/.exec(f); if (m && m[1] !== 'en') timX[m[1]] = JSON.parse(fs.readFileSync(path.join(G, f), 'utf8')); }   // другие языки (uk…): склейки по словам — и на них
const bad = [], warn = [], sfx = new Set(), used = new Set();
const go = (from, to) => { if (!to || to === '@save') return; used.add(to); if (!scenes[to]) bad.push(`${from} → нет сцены «${to}»`); };
for (const [id, sc] of Object.entries(scenes)) {
  (sc.beats || []).forEach((b, i) => {
    if (b.img && !shots.has(b.img)) bad.push(`${id}[${i}]: нет кадра «${b.img}»`);
    for (const c of b.cams || []) if (c[4] && !shots.has(c[4])) bad.push(`${id}[${i}]: нет кадра плана «${c[4]}»`);
    if (b.cutAt) { if ((b.cams || []).length !== b.cutAt.length + 1) bad.push(`${id}[${i}]: cutAt ${b.cutAt.length} склеек, а планов ${(b.cams || []).length}`);
      const k = (b.say || [])[0]; for (const [l, T] of [...Object.entries(tim), ...Object.entries(timX)]) for (const re of b.cutAt) if (k && T[k] && !(T[k].ew || T[k].w || []).some(w => new RegExp(re, 'i').test(w[1]))) warn.push(`${id}[${i}]: склейка «${re}» — нет такого слова в ${k} (${l})`); }
    // кивок/«нет» к слову: [что, [реплика, слово-regexp, доля], секунд] — реплика в кадре, слово есть на каждом языке
    for (const [who, r] of Object.entries(b.react || {})) if (Array.isArray(r[1])) { const [k, re] = r[1];
      if (!(b.say || []).includes(k)) bad.push(`${id}[${i}]: react ${who} — реплики «${k}» нет в кадре`);
      else for (const [l, T] of [...Object.entries(tim), ...Object.entries(timX)]) if (T[k] && !(T[k].ew || T[k].w || []).some(w => new RegExp(re, 'i').test(w[1]))) warn.push(`${id}[${i}]: react ${who} «${re}» — нет такого слова в ${k} (${l}), будет по доле реплики`); }
    for (const k of b.say || []) {
      for (const l of ['ru', 'en']) if (!tim[l][k]) warn.push(`${id}[${i}]: реплики «${k}» нет в timing ${l}`);
      if (!fs.existsSync(path.join(G, 'snd', k + '.mp3'))) warn.push(`${id}[${i}]: нет файла snd/${k}.mp3`);
    }
    for (const s of b.sfx || []) sfx.add(s[0]);
  });
  const th = sc.then || {};
  go(id, th.goto); go(id, th.camgame); go(id, th.win); go(id, th.lose); go(id, th.retry);
  (th.opts || []).forEach(o => go(id, o.go));
  if (th.img && !shots.has(th.img)) bad.push(`${id}.then: нет кадра «${th.img}»`);
  for (const k of th.calls || []) if (!tim.ru[k]) warn.push(`${id}.then: реплики «${k}» нет в timing ru`);
  if (th.ending && !S.endings[th.ending]) bad.push(`${id}: концовки «${th.ending}» нет в endings`);
  if (th.over && !S.overs.includes(th.over)) bad.push(`${id}: смерти «${th.over}» нет в overs`);
  // обход и разговор: кадр, точки/темы, выход; сцены точек возвращаются сюда (then.back)
  for (const [kind, A] of [['explore', th.explore], ['talk', th.talk]]) if (A) {
    if (A.img && !shots.has(A.img)) bad.push(`${id}.${kind}: нет кадра «${A.img}»`);
    for (const x of A.spots || A.topics || []) { go(id, x.go); if (scenes[x.go] && (scenes[x.go].then || {}).back !== id) warn.push(`${id}.${kind}: «${x.go}» не возвращается сюда (then.back)`); }
    const lv = A.leave || A.bye; if (!lv) bad.push(`${id}.${kind}: нет выхода (leave/bye)`); else go(id, lv.go);
  }
  if (th.back) { if (!scenes[th.back]) bad.push(`${id} → back: нет сцены «${th.back}»`); else if (!((scenes[th.back].then || {}).explore || (scenes[th.back].then || {}).talk)) bad.push(`${id} → back «${th.back}»: там нет обхода/разговора`); }
}
// флаги: что проверяется (if) должно где-то ставиться (set; v:сцена — осмотренная точка или тема)
{ const setF = new Set(), ifF = [], vis = new Set();
  const walk = (o, where) => { if (!o || typeof o !== 'object') return; if (Array.isArray(o)) return o.forEach(x => walk(x, where));
    if (o.set) [].concat(o.set).forEach(f => setF.add(f)); if (o.if) [].concat(o.if).forEach(f => ifF.push([f.replace(/^!/, ''), where]));
    if ((o.spots || o.topics)) (o.spots || o.topics).forEach(x => vis.add('v:' + x.go));
    for (const k in o) if (typeof o[k] === 'object') walk(o[k], where); };
  for (const [id, sc] of Object.entries(scenes)) walk(sc, id); walk(S.shift, 'смена');
  for (const [f, where] of ifF) if (!setF.has(f) && !vis.has(f)) bad.push(`${where}: условие «${f}» — такой флаг нигде не ставится`);
  const usedF = new Set(ifF.map(x => x[0])), src2 = fs.readFileSync(path.join(G, 'engine.js'), 'utf8');
  for (const f of setF) if (!usedF.has(f) && !src2.includes('flags.' + f)) warn.push(`флаг «${f}» ставится, но ни на что не влияет`); }
// смена: переходы после часов, ссылки на странности, реплики
const SH = S.shift || { segs: {}, an: {} };
for (const [id, sc] of Object.entries(scenes)) if (sc.then && sc.then.shift && !SH.segs[sc.then.shift]) bad.push(`${id}: нет смены «${sc.then.shift}»`);
for (const [id, sg] of Object.entries(SH.segs)) {
  go('смена ' + id, sg.next);
  for (const e of sg.events) { if (e.an && !SH.an[e.an]) bad.push(`смена ${id}: нет странности «${e.an}»`);
    for (const k of [e.say, ...(e.radio || []), e.call && e.call.say, e.call && e.call.after, e.call && e.call.noSay, e.task && e.task.say].filter(Boolean)) if (!tim.ru[k]) warn.push(`смена ${id}: реплики «${k}» нет в timing ru${SH.lines && SH.lines[k] ? ' (пока текстом)' : ''}`);
    if (e.call) go('смена ' + id, e.call.die); }
  for (const a of (sg.rnd || {}).pool || []) if (!SH.an[a]) bad.push(`смена ${id}: нет странности «${a}» (случайные)`);
}
for (const [id, a] of Object.entries(SH.an)) { if (a.say && !tim.ru[a.say]) warn.push(`странность ${id}: реплики «${a.say}» нет в timing ru`); if (a.cam && !['01', '02', '03', '04', '07', '10', 'm2'].includes(a.cam)) bad.push(`странность ${id}: нет камеры ${a.cam}`); }
for (const c of ['01', '02', '03', '04', '07', '10', '_m2']) if (!shots.has('cam' + c)) bad.push(`нет кадра камеры cam${c}`);
for (const c of S.cams || []) { if (c.img && !shots.has(c.img)) bad.push(`cams ${c.id}: нет кадра «${c.img}»`); if (c.over) go('cams', c.over); if (c.say && !tim.ru[c.say]) warn.push(`cams ${c.id}: нет реплики ${c.say}`); }
used.add(S.start);
// куда ведёт сам движок смены: входы в часы (сохранения), смерти странностей, звонков и «держи Фрэнка», CAM 13 и «три пропуска»
for (const id of Object.keys(SH.segs)) go('движок', 'sh_' + id);
for (const a of Object.values(SH.an)) if (a.die || a.stare) go('движок', a.die || 'over_stare');
for (const sg of Object.values(SH.segs)) for (const e of sg.events) for (const x of [e.call, e.hold]) if (x && x.die) go('движок', x.die);
['over_come', 'over_k1'].forEach(k => go('движок', k));
const orphan = Object.keys(scenes).filter(k => !used.has(k));
const noSfx = [...sfx].filter(k => !fs.existsSync(path.join(G, 'snd', k + '.mp3')));
console.log(`сцен ${Object.keys(scenes).length}, кадров в scenes.js ${shots.size}`);
if (orphan.length) console.log('сцены, в которые никто не ведёт:', orphan.join(', '));
if (noSfx.length) console.log('звуков нет (сделать в ElevenLabs):', noSfx.join(', '));
for (const w of warn) console.log('  ~', w);
for (const b of bad) console.log('  ✗', b);
console.log(bad.length ? `ОШИБОК: ${bad.length}` : 'переходы и кадры в порядке');
process.exit(bad.length ? 1 : 0);
