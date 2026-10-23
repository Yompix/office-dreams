// Английская версия: какие русские строки сценария (story.js) и интерфейса (engine.js) ещё без перевода в i18n.js.
//   node tools/story/i18n_check.js   → список «путь: строка»; код выхода 1, если что-то не переведено
const fs = require('fs'), path = require('path');
const G = path.join(__dirname, '../../public/games/nightshift');
const window = {}; new Function('window', fs.readFileSync(path.join(G, 'story.js'), 'utf8'))(window);
new Function('window', 'location', 'localStorage', 'navigator', fs.readFileSync(path.join(G, 'i18n.js'), 'utf8'))(window, { search: '?lang=en' }, { getItem: () => null }, { language: 'en' });
const tr = window.tr, CYR = /[А-Яа-яЁё]/, miss = new Map();
const need = (s, where) => { if (typeof s === 'string' && CYR.test(s) && tr(s) === s && !miss.has(s)) miss.set(s, where); };
// сценарий: всё, что видно на экране (реплики с озвучкой берут текст из timing_en.json, их тут нет)
const SKIP = new Set(['img', 'go', 'win', 'lose', 'retry', 'next', 'music', 'ending', 'shift', 'fx', 'kind', 'cam', 'id', 'at', 'by', 'from', 'to']),   // ключи и ссылки, не текст
  SKIP_OBJ = new Set(['say', 'lines', 'who', 'snd', 'sfx', 'kinds', 'need', 'cams', 'props']);
(function walk(o, p) { for (const k in o) { const v = o[k]; if (typeof v === 'string' ? SKIP.has(k) : SKIP_OBJ.has(k)) continue;
  if (typeof v === 'string') need(v, p + '.' + k); else if (v && typeof v === 'object') walk(v, p + '.' + k); } })(window.STORY, 'S');
// виды странностей и смерти — тоже на экране
for (const a of Object.values(window.STORY.shift.an)) for (const k of [].concat(a.kind || [], a.kinds || [])) need(k, 'kind');
for (const o of window.STORY.overs || []) need(o, 'overs');
// интерфейс: строки в tr('…') / trf('…') и в списках, которые идут через tr
const src = fs.readFileSync(path.join(G, 'engine.js'), 'utf8');
for (const m of src.matchAll(/\btrf?\(\s*'((?:[^'\\]|\\.)*)'/g)) need(m[1].replace(/\\'/g, "'"), 'engine tr');
for (const name of ['KINDS', 'TUT_DO', 'VHS_N']) { const m = src.match(new RegExp('const ' + name + ' = ([\\[{][^;]*?[\\]}]);')); if (m) for (const s of m[1].matchAll(/'([^']*)'/g)) need(s[1], 'engine ' + name); }
{ const m = src.match(/shiftPop\(`<h3>\$\{tr\('Как играть'\)\}[\s\S]*?\]\.map/); if (m) for (const s of m[0].matchAll(/'([^']*[А-Яа-я][^']*)'/g)) need(s[1], 'engine help'); }
for (const [s, w] of miss) console.log(w.padEnd(34), '|', s);
console.log(miss.size ? `без перевода: ${miss.size}` : 'всё переведено'); process.exit(miss.size ? 1 : 0);
