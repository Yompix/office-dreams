// Проверка перевода: node tools/i18n/check.js es|pt|de|uk  (файл tools/i18n/<язык>.json: { lines: {ключ: текст}, ui: {русская строка: текст} })
// Все ключи на месте, тире в начале реплик (по нему движок отличает «вслух» от рассказа), подстановки {n}, переносы строк, отступы,
// длина надписей в 3D-мире (маленькие таблички), алфавит языка.
const fs = require('fs'), path = require('path');
const L = process.argv[2], S = require('./source.json'), T = JSON.parse(fs.readFileSync(path.join(__dirname, L + '.json'), 'utf8'));
const err = [], warn = [], cyr = /[Ѐ-ӿ]/, lat = /[A-Za-z]/;
const dash = s => /^\s*—/.test(s), ph = s => (s.match(/\{\w+\}/g) || []).sort().join(','), nl = s => (s.match(/\n/g) || []).length, lead = s => s.match(/^ */)[0].length;
for (const k in S.lines) {
  const t = (T.lines || {})[k], en = S.lines[k].en;
  if (typeof t !== 'string' || !t.trim()) { err.push(`реплика ${k}: нет перевода`); continue; }
  if (dash(t) !== dash(en)) err.push(`реплика ${k}: тире в начале ${dash(en) ? 'нужно' : 'не нужно'}`);
  if (L === 'uk' ? !cyr.test(t) : cyr.test(t)) err.push(`реплика ${k}: не тот алфавит`);
  const r = t.length / Math.max(1, en.length); if (r > 1.6 || r < .5) warn.push(`реплика ${k}: длина ×${r.toFixed(2)} к английской`);
}
for (const k of Object.keys(T.lines || {})) if (!(k in S.lines)) err.push(`лишняя реплика ${k}`);
for (const k in S.ui) {
  const t = (T.ui || {})[k], en = S.ui[k];
  if (typeof t !== 'string') { err.push(`строка «${k.slice(0, 40)}»: нет перевода`); continue; }
  if (ph(t) !== ph(en)) err.push(`строка «${k.slice(0, 40)}»: подстановки ${ph(en) || '—'} → ${ph(t) || '—'}`);
  if (nl(t) !== nl(en)) err.push(`строка «${k.slice(0, 40)}»: переносов ${nl(en)} → ${nl(t)}`);
  if (lead(t) !== lead(en)) warn.push(`строка «${k.slice(0, 40)}»: отступ ${lead(en)} → ${lead(t)}`);
  if (L !== 'uk' && cyr.test(t)) err.push(`строка «${k.slice(0, 40)}»: кириллица`);
  if (L === 'uk' && lat.test(en.replace(/CAM|REC|IR|MIC|HUSH|TILL|SIX|itch\.io|Steam|Android|[A-Z]{2,}\b/g, '')) && !cyr.test(t) && /[a-z]{3}/.test(en)) warn.push(`строка «${k.slice(0, 40)}»: без кириллицы`);
  if (S.section[k] === 'надписи в 3D-мире (текстуры)' && t.length > en.length + 3) err.push(`табличка «${en}» → «${t}»: длиннее английской на ${t.length - en.length} (не влезет)`);
}
for (const k of Object.keys(T.ui || {})) if (!(k in S.ui)) err.push(`лишняя строка «${k.slice(0, 40)}»`);
console.log(`${L}: реплик ${Object.keys(T.lines || {}).length}/${Object.keys(S.lines).length}, строк ${Object.keys(T.ui || {}).length}/${Object.keys(S.ui).length}, ошибок ${err.length}, замечаний ${warn.length}`);
for (const e of err.slice(0, 40)) console.log('  ✗', e);
for (const w of warn.slice(0, 15)) console.log('  ·', w);
process.exit(err.length ? 1 : 0);
