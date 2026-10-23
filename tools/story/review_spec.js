// Список кадров сюжета для покадровой проверки: каждый кадр сцены (img) — начало, середина, конец (длина — как в игре, по репликам).
//   node tools/story/review_spec.js ПАПКА        → ПАПКА/review.json и ПАПКА/review_spec.txt (строка кадров для nightshift-shot.js)
//   FAST=8 node tools/nightshift-shot.js ПАПКА/rev "$(cat ПАПКА/review_spec.txt)"   (LAND=1 — горизонтально, GFX=cine — «Кино»)
//   python3 tools/story/review_sheets.py ПАПКА    → ПАПКА/sheets/sheet_NN.png (по 6 кадров на лист)
const fs = require('fs'), path = require('path');
const G = path.join(__dirname, '../../public/games/nightshift'), out = process.argv[2] || '.';
const window = {}; new Function('window', fs.readFileSync(path.join(G, 'story.js'), 'utf8'))(window);
const S = window.STORY, T = JSON.parse(fs.readFileSync(path.join(G, 'timing.json'), 'utf8'));
const list = [], seen = new Set();
for (const [id, sc] of Object.entries(S.scenes)) {
  const off = {};   // тот же кадр подряд — время продолжается
  (sc.beats || []).forEach((b, i) => {
    if (!b.img) return;
    let t = .15; for (const k of b.say || []) t += ((T[k] || {}).dur || 2) + .3;
    if (b.text) t += Math.max(2.2, b.text.length / 14 + 1.3);
    const d = Math.max(t + (b.pad || .3), 1.2), prev = sc.beats[i - 1], o = prev && prev.img === b.img ? (off[b.img] || 0) : 0;
    off[b.img] = o + d;
    const times = [o + .3, o + d / 2, o + d].map(x => +x.toFixed(1)), key = b.img + '@' + times.join(',');
    if (seen.has(key)) return; seen.add(key);
    list.push({ scene: id, i, img: b.img, times });
  });
}
fs.mkdirSync(out, { recursive: true });
fs.writeFileSync(path.join(out, 'review.json'), JSON.stringify(list, null, 1));
const byImg = {}; for (const o of list) (byImg[o.img] = byImg[o.img] || new Set()) && o.times.forEach(t => byImg[o.img].add(t));
fs.writeFileSync(path.join(out, 'review_spec.txt'), Object.entries(byImg).map(([k, v]) => k + '@' + [...v].sort((a, b) => a - b).join(',')).join(';'));
console.log(list.length, 'кадров,', list.reduce((a, b) => a + b.times.length, 0), 'снимков; уникальных img', Object.keys(byImg).length);
