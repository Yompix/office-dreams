// Субтитры трейлера для YouTube / VK (.srt): реплики + надписи правил. YouTube индексирует текст субтитров — это помогает поиску и рекомендациям.
//   node tools/trailer/srt.js TIMELINE.json > trailer.srt
const fs = require('fs');
const TL = JSON.parse(fs.readFileSync(process.argv[2]));
const items = TL.subs.map(s => ({ at: s.at, end: s.end, text: s.text }));
for (const c of TL.cards) if (c.kind === 'rule' || c.kind === 'big') items.push({ at: c.at, end: c.at + c.dur, text: c.lines.join(' ') });
items.sort((a, b) => a.at - b.at);
const ts = t => { const ms = Math.round(t * 1000), p = (n, w = 2) => String(n).padStart(w, '0'); return `${p(Math.floor(ms / 3600000))}:${p(Math.floor(ms / 60000) % 60)}:${p(Math.floor(ms / 1000) % 60)},${p(ms % 1000, 3)}`; };
process.stdout.write(items.map((s, i) => `${i + 1}\n${ts(s.at)} --> ${ts(Math.max(s.at + .5, s.end))}\n${s.text.replace(/[’‘]/g, "'")}\n`).join('\n'));
