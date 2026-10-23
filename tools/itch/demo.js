// Бесплатная часть для itch.io: из копии игры вырезается всё после выбора у стекла (сцена glass2, 03:30) —
// сцены, часы смены и их реплики (snd/*.mp3, snd/en/*.mp3, строки timing*.json). Вместо выбора у стекла — экран «полная версия» (then.demo в engine.js).
//   node tools/itch/demo.js ПАПКА [цена] [ссылка]   ПАПКА — копия public/games/nightshift (правится на месте)
// Ссылка на покупку: если не задана, игра берёт страницу автора из referrer (https://АВТОР.itch.io) + /hush-till-six — там кнопка «Buy Now».
const fs = require('fs'), path = require('path');
const [D, price = '$3', url = ''] = process.argv.slice(2), GATE = 'glass2';
const read = f => fs.readFileSync(path.join(D, f), 'utf8');
const window = {}; new Function('window', read('story.js'))(window);
const S = window.STORY, segs = S.shift.segs;
// сцены, которые движок зовёт по имени сам (смерти в смене: пропуски, «засмотрелся», CAM 13, не запер вход);
// over_k8 («Фрэнк ушёл на голос») — только в часе S6, в демо его нет (нет сцены — движок уходит в меню)
const LATE = new Set(['over_k8']);
const fixed = [...read('engine.js').matchAll(/'((?:over|sh)_[a-zA-Z0-9_]+)'/g)].map(m => m[1]).filter(k => S.scenes[k] && !LATE.has(k));
// какие сцены и часы смены достижимы с начала, если у стекла история обрывается
const keep = new Set(), hours = new Set(), queue = [S.start, ...fixed];
const scan = (v, out) => { if (typeof v === 'string') out.push(v); else if (Array.isArray(v)) v.forEach(x => scan(x, out)); else if (v && typeof v === 'object') Object.values(v).forEach(x => scan(x, out)); return out; };
while (queue.length) {
  const id = queue.shift(); if (keep.has(id) || !S.scenes[id]) continue; keep.add(id);
  if (id === GATE) continue;
  const th = S.scenes[id].then || {};
  const hr = th.shift || (id.startsWith('sh_') && segs[id.slice(3)] ? id.slice(3) : null);
  if (hr && segs[hr] && !hours.has(hr)) { hours.add(hr); queue.push('sh_' + hr, ...scan(segs[hr], [])); }
  queue.push(...scan(th, []));
}
S.scenes[GATE].then = { demo: 1 };
for (const id of Object.keys(S.scenes)) if (!keep.has(id)) delete S.scenes[id];
for (const h of Object.keys(segs)) if (!hours.has(h)) delete segs[h];
// реплики: все ключи таймингов, что встречаются в оставшемся сценарии и в коде движка (пролог, обучение, рация)
const T = JSON.parse(read('timing.json')), TE = JSON.parse(read('timing_en.json'));
const used = new Set(scan(S, []).filter(k => T[k] || TE[k]));
for (const m of read('engine.js').matchAll(/'([a-z]{1,3}\d{2}[a-z]?)'/g)) if (T[m[1]] || TE[m[1]]) used.add(m[1]);
for (let i = 1; i <= 15; i++) used.add('p' + String(i).padStart(2, '0'));   // пролог: SNDS в engine.js (собирается в цикле)
for (const k of Object.keys(S.shift.lines || {})) if (!used.has(k)) delete S.shift.lines[k];   // тексты реплик смены — только оставшихся часов
let cut = 0;
for (const [f, J] of [['timing.json', T], ['timing_en.json', TE]]) {
  for (const k of Object.keys(J)) if (!used.has(k)) {
    delete J[k];
    for (const mp of [path.join(D, 'snd', k + '.mp3'), path.join(D, 'snd/en', k + '.mp3')]) if (fs.existsSync(mp)) { fs.unlinkSync(mp); cut++; }
  }
  fs.writeFileSync(path.join(D, f), JSON.stringify(J));
}
fs.writeFileSync(path.join(D, 'story.js'), '/* Hush Till Six — бесплатная часть (до 03:30). Полный сценарий — в полной версии. */\nwindow.STORY = ' + JSON.stringify(S) + ';\n');
// флаг демо для движка: экран «полная версия», кнопка в меню
const idx = read('index.html'), tag = `<script>window.NS_DEMO = ${JSON.stringify(url ? { price, url } : { price })};</script>\n`;
if (!idx.includes('window.NS_DEMO')) fs.writeFileSync(path.join(D, 'index.html'), idx.replace('<script src="i18n.js', tag + '<script src="i18n.js'));
console.log(`демо: сцен ${keep.size}, часы ${[...hours].join(' ')}, реплик ${used.size}, удалено mp3 ${cut}`);
