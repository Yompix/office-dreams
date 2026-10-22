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
const bad = [], warn = [], sfx = new Set(), used = new Set();
const go = (from, to) => { if (!to || to === '@save') return; used.add(to); if (!scenes[to]) bad.push(`${from} → нет сцены «${to}»`); };
for (const [id, sc] of Object.entries(scenes)) {
  (sc.beats || []).forEach((b, i) => {
    if (b.img && !shots.has(b.img)) bad.push(`${id}[${i}]: нет кадра «${b.img}»`);
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
}
// смена: переходы после часов, ссылки на странности, реплики
const SH = S.shift || { segs: {}, an: {} };
for (const [id, sc] of Object.entries(scenes)) if (sc.then && sc.then.shift && !SH.segs[sc.then.shift]) bad.push(`${id}: нет смены «${sc.then.shift}»`);
for (const [id, sg] of Object.entries(SH.segs)) {
  go('смена ' + id, sg.next);
  for (const e of sg.events) { if (e.an && !SH.an[e.an]) bad.push(`смена ${id}: нет странности «${e.an}»`);
    for (const k of [e.say, ...(e.radio || []), e.call && e.call.say, e.call && e.call.after, e.task && e.task.say].filter(Boolean)) if (!tim.ru[k]) warn.push(`смена ${id}: реплики «${k}» нет в timing ru${SH.lines && SH.lines[k] ? ' (пока текстом)' : ''}`);
    if (e.call) go('смена ' + id, e.call.die); }
  for (const a of (sg.rnd || {}).pool || []) if (!SH.an[a]) bad.push(`смена ${id}: нет странности «${a}» (случайные)`);
}
for (const [id, a] of Object.entries(SH.an)) { if (a.say && !tim.ru[a.say]) warn.push(`странность ${id}: реплики «${a.say}» нет в timing ru`); if (a.cam && !['01', '02', '03', '04', '07', '10', 'm2'].includes(a.cam)) bad.push(`странность ${id}: нет камеры ${a.cam}`); }
for (const c of ['01', '02', '03', '04', '07', '10', '_m2']) if (!shots.has('cam' + c)) bad.push(`нет кадра камеры cam${c}`);
for (const c of S.cams || []) { if (c.img && !shots.has(c.img)) bad.push(`cams ${c.id}: нет кадра «${c.img}»`); if (c.over) go('cams', c.over); if (c.say && !tim.ru[c.say]) warn.push(`cams ${c.id}: нет реплики ${c.say}`); }
used.add(S.start);
const orphan = Object.keys(scenes).filter(k => !used.has(k));
const noSfx = [...sfx].filter(k => !fs.existsSync(path.join(G, 'snd', k + '.mp3')));
console.log(`сцен ${Object.keys(scenes).length}, кадров в scenes.js ${shots.size}`);
if (orphan.length) console.log('сцены, в которые никто не ведёт:', orphan.join(', '));
if (noSfx.length) console.log('звуков нет (сделать в ElevenLabs):', noSfx.join(', '));
for (const w of warn) console.log('  ~', w);
for (const b of bad) console.log('  ✗', b);
console.log(bad.length ? `ОШИБОК: ${bad.length}` : 'переходы и кадры в порядке');
process.exit(bad.length ? 1 : 0);
