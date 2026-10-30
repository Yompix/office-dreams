// Выгружает данные старой игры (public/games/nightshift) в JSON для Godot-версии (godot/nightshift/data/):
//   story.json — сценарий (story.js, русский; перевод — в Godot тем же правилом, что trStory)
//   i18n.json  — языки, чьими голосами озвучены, названия, словари EN и DICT (ключ — русская строка)
// Запуск: node tools/godot/export_data.js
const fs = require('fs'), path = require('path'), vm = require('vm');
const SRC = path.join(__dirname, '../../public/games/nightshift'), OUT = path.join(__dirname, '../../godot/nightshift/data');
fs.mkdirSync(OUT, { recursive: true });
const sandbox = () => { const w = {}; return vm.createContext({ window: w, location: { search: '' }, navigator: { language: 'ru', languages: ['ru'] },
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} }, console }); };
// сценарий
{ const ctx = sandbox(); vm.runInContext(fs.readFileSync(path.join(SRC, 'story.js'), 'utf8'), ctx);
  const S = ctx.window.STORY; fs.writeFileSync(path.join(OUT, 'story.json'), JSON.stringify(S));
  console.log('story.json:', Object.keys(S.scenes).length, 'сцен,', Object.keys(S.endings || {}).length, 'концовок,', Object.keys(S.overs || {}).length, 'смертей'); }
// языки: словари внутри модуля — открываем их наружу перед закрытием IIFE
{ let src = fs.readFileSync(path.join(SRC, 'i18n.js'), 'utf8'); const i = src.lastIndexOf('})();');
  src = src.slice(0, i) + 'window.__I18N = { LANGS, VOICE, NAMES, EN, DICT, KEYS: [...KEYS] };\n' + src.slice(i);
  const ctx = sandbox(); vm.runInContext(src, ctx); const I = ctx.window.__I18N;
  fs.writeFileSync(path.join(OUT, 'i18n.json'), JSON.stringify({ langs: I.LANGS, voice: I.VOICE, names: I.NAMES, keys: I.KEYS, en: I.EN, dict: I.DICT }));
  console.log('i18n.json:', I.LANGS.length, 'языков; EN', Object.keys(I.EN).length, 'строк; DICT', Object.entries(I.DICT).map(([k, v]) => k + ':' + Object.keys(v).length).join(' ')); }
