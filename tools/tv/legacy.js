#!/usr/bin/env node
/* Hush Till Six — копия скриптов игры для старых браузеров (ТВ-приставки Android, в т.ч. 32-битные, со старым WebView/Chrome < 80).
   Игра написана на современном JS (`??`, `catch {}` и т.п.) — старый браузер не может её даже прочитать (чёрный экран).
   Этот скрипт переводит каждый скрипт игры в ES2017 (esbuild) и кладёт в public/games/nightshift/legacy/ с тем же путём.
   Подмену делает compat.js: если браузер не смог прочитать скрипт (SyntaxError), сразу после него подгружается копия из legacy/.
   Новые браузеры legacy/ не трогают вовсе.

     node tools/tv/legacy.js           → пересобрать (нужен esbuild: cd tools/tv && npm i)
     node tools/tv/legacy.js --check   → только проверить, что копии свежие (без esbuild; входит в npm test)

   ВАЖНО: поправил engine.js / scenes.js / pad.js / … — запусти `node tools/tv/legacy.js`, иначе приставки получат старую версию
   (npm test об этом напомнит). */
'use strict';
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const GAME = path.join(__dirname, '../../public/games/nightshift'), OUT = path.join(GAME, 'legacy');
const TARGET = ['chrome58', 'firefox57', 'safari11'];

// какие скрипты: всё, что index.html грузит тегом <script src>, + то, что игра подгружает сама
function scripts() {
  const html = fs.readFileSync(path.join(GAME, 'index.html'), 'utf8'), list = [];
  for (const m of html.matchAll(/<script src="([^"?]+)(?:\?[^"]*)?"/g)) if (!/^(https?:)?\/\//.test(m[1]) && m[1] !== 'compat.js') list.push(m[1]);
  for (const extra of ['qa.js']) if (!list.includes(extra) && fs.existsSync(path.join(GAME, extra))) list.push(extra);
  return list;
}
const hash = s => crypto.createHash('sha1').update(s).digest('hex').slice(0, 16);
const HEAD = h => `/* legacy: ES2017-копия, источник sha1:${h} — не править, пересобрать: node tools/tv/legacy.js */\n`;

function check() {
  const bad = [];
  for (const f of scripts()) {
    const src = fs.readFileSync(path.join(GAME, f), 'utf8'), out = path.join(OUT, f);
    const head = fs.existsSync(out) ? fs.readFileSync(out, 'utf8').slice(0, 200) : '';
    if (!head.includes('sha1:' + hash(src))) bad.push(f);
  }
  return bad;
}

if (process.argv.includes('--check')) {
  const bad = check();
  if (bad.length) { console.error('legacy/ устарел для: ' + bad.join(', ') + '\n  → node tools/tv/legacy.js   (ТВ-приставки и старые браузеры получат старую версию игры)'); process.exit(1); }
  console.log('legacy/: копии для старых браузеров свежие (' + scripts().length + ' скриптов)');
  process.exit(0);
}

let esbuild;
try { esbuild = require('esbuild'); } catch (e) { console.error('нет esbuild: cd tools/tv && npm i'); process.exit(1); }
let n = 0, warn = 0;
for (const f of scripts()) {
  const src = fs.readFileSync(path.join(GAME, f), 'utf8');
  const r = esbuild.transformSync(src, { loader: 'js', target: TARGET, format: undefined, minifyWhitespace: true, legalComments: 'inline', logLevel: 'silent' });
  for (const w of r.warnings) { warn++; console.warn('  ' + f + ': ' + w.text); }
  const out = path.join(OUT, f);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, HEAD(hash(src)) + r.code);
  n++;
}
console.log(`legacy/: ${n} скриптов → ES2017 (${TARGET.join(', ')})` + (warn ? `, предупреждений ${warn}` : ''));
