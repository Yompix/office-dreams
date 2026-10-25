// Исходник для перевода игры на новые языки: реплики (tools/story/lines.json) + словарь интерфейса и сюжета (i18n.js, EN).
//   node tools/i18n/make_source.js → tools/i18n/source.json
//   { lines: {ключ: {who, ru, en, tag}}, ui: {русская строка: английская}, section: {русская строка: раздел словаря} }
const fs = require('fs'), path = require('path'), R = path.join(__dirname, '../..');
const src = fs.readFileSync(path.join(R, 'public/games/nightshift/i18n.js'), 'utf8');
const body = src.match(/const EN = \{([\s\S]*?)\n\};/)[1];
const EN = eval('({' + body + '})');
// раздел каждой строки — по комментарию «// …» над ней (надписи в 3D-мире должны оставаться короткими)
const section = {}; let cur = '';
for (const ln of body.split('\n')) {
  const c = ln.match(/^\s*\/\/\s*(.*)$/); if (c) { cur = c[1].trim(); continue; }
  try { const part = eval('({' + ln.replace(/,\s*$/, '') + '})'); for (const k in part) section[k] = cur; } catch {}
}
const lines = JSON.parse(fs.readFileSync(path.join(R, 'tools/story/lines.json')));
fs.writeFileSync(path.join(__dirname, 'source.json'), JSON.stringify({ lines, ui: EN, section }, null, 1));
console.log('реплик', Object.keys(lines).length, 'строк интерфейса', Object.keys(EN).length, 'с разделом', Object.keys(section).length);
