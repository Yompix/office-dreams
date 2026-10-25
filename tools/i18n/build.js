// Сборка переводов в игру: node tools/i18n/build.js [es pt de uk]
//   1) словари интерфейса и сюжета → блок DICT в public/games/nightshift/i18n.js (из tools/i18n/<язык>.json + extra.json);
//   2) тексты реплик → tools/story/lines.json (поле <язык>: по нему озвучивает tools/story/voice.py);
//   3) для языков без своей озвучки (VOICE в i18n.js = 'en') — timing_<язык>.json: английские записи и длительности,
//      текст на языке игры, время слов разложено по длине слов (субтитры подсвечиваются, губы двигаются по буквам).
const fs = require('fs'), path = require('path'), R = path.join(__dirname, '../..'), G = path.join(R, 'public/games/nightshift');
const I18N = path.join(G, 'i18n.js');
let src = fs.readFileSync(I18N, 'utf8');
const VOICE = eval('(' + src.match(/const VOICE = (\{[^}]*\})/)[1] + ')');
const langs = process.argv.slice(2).length ? process.argv.slice(2) : ['es', 'pt', 'de', 'fr', 'it', 'pl', 'tr', 'uk', 'zh', 'ja', 'ko'].filter(l => fs.existsSync(path.join(__dirname, l + '.json')));
const extra = fs.existsSync(path.join(__dirname, 'extra.json')) ? JSON.parse(fs.readFileSync(path.join(__dirname, 'extra.json'))) : {};
const lines = JSON.parse(fs.readFileSync(path.join(R, 'tools/story/lines.json')));
const EN_T = JSON.parse(fs.readFileSync(path.join(G, 'timing_en.json')));
// слова так же, как их считает chunks() в engine.js и режет voice.py: знаки препинания и дефис режут токен на части
// n23m — реплика n23 («Да. Это я.») чужим голосом в трубке: текст тот же
const ALIAS = { n23m: 'n23' };
const words = t => t.split(/\s+/).filter(Boolean).flatMap(s => s.replace(/[.,:;—?!…«»"()\-¿¡„“”]/g, ' ').split(/\s+/).filter(Boolean));
// китайский и японский пишутся без пробелов: «слово» субтитра — кусок фразы до знака препинания (знак остаётся в куске), длинный кусок делится по ≤9 знаков
const CJK_P = '，。！？：；、…—」』）”’,.!?:;', CJK_O = '「『（“‘';
const cjkWords = t => { const s = t.replace(/\s+/g, ''), out = []; let cur = '';
  for (const ch of s) { if (CJK_O.includes(ch) && cur && !CJK_O.includes(cur.slice(-1))) { out.push(cur); cur = ''; } cur += ch; if (CJK_P.includes(ch)) { out.push(cur); cur = ''; } }
  if (cur) out.push(cur);
  const merged = []; for (const w of out) { if (merged.length && [...w].every(c => CJK_P.includes(c))) merged[merged.length - 1] += w; else merged.push(w); }
  return merged.flatMap(w => { const a = [...w], n = Math.ceil(a.length / 9), k = Math.ceil(a.length / n), o = []; for (let i = 0; i < a.length; i += k) o.push(a.slice(i, i + k).join('')); return o; }); };
const out = {};
for (const L of langs) {
  const T = JSON.parse(fs.readFileSync(path.join(__dirname, L + '.json'), 'utf8'));
  out[L] = Object.assign({}, T.ui, extra[L] || {});
  for (const k in T.lines) if (lines[k]) lines[k][L] = T.lines[k];
  if (VOICE[L] === 'en' && !process.env.NOTIM) {   // NOTIM=1 — только словари (тайминги не трогать: например, пока идёт озвучка языка)
    const tim = {};
    for (const k in EN_T) {
      const en = EN_T[k], t = T.lines[k] || T.lines[ALIAS[k]]; if (!t || !en.dur) { tim[k] = en; continue; }
      const ws = (/^(zh|ja)$/.test(L) ? cjkWords : words)(t.replace(/^\s*—\s*/, '')), w0 = en.w && en.w.length ? en.w[0][0] : .1, w1 = Math.max(w0 + .3, en.dur * .9 - .15);
      const wt = ws.map(s => s.length + 2), sum = wt.reduce((a, b) => a + b, 0) || 1; let acc = 0;
      tim[k] = Object.assign({}, en, { t, ew: en.w, w: ws.map((s, i) => { const at = w0 + (w1 - w0) * acc / sum; acc += wt[i]; return [+at.toFixed(2), s]; }) });   // ew — английские слова записи: по ним склейки и реакции (cutAt, react) встают ровно на звук
    }
    fs.writeFileSync(path.join(G, `timing_${L}.json`), JSON.stringify(tim));
  }
  console.log(L, 'строк', Object.keys(out[L]).length, 'реплик', Object.keys(T.lines).length, VOICE[L] === 'en' ? '(озвучка — английские записи, timing_' + L + '.json)' : '(своя озвучка)');
}
fs.writeFileSync(path.join(R, 'tools/story/lines.json'), JSON.stringify(lines, null, 1));
const q = s => JSON.stringify(s);
const block = 'const DICT = {\n' + Object.keys(out).map(L => `${L}: {\n` + Object.keys(out[L]).map(k => `  ${q(k)}: ${q(out[L][k])},`).join('\n') + '\n},').join('\n') + '\n};';
src = src.replace(/(\/\/ ==== DICT: tools\/i18n\/build\.js ====\n)[\s\S]*?(\n\/\/ ==== \/DICT ====)/, (m, a, b) => a + block + b);
fs.writeFileSync(I18N, src);
console.log('i18n.js:', (src.length / 1024).toFixed(0), 'КБ');
