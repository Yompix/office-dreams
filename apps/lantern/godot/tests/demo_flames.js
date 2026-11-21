'use strict';
// 2.0.3: демо-данные для скриншотов сайта (tests/site_shots.sh) — ТОЛЬКО тестовая БД локального relay.
// Выдаёт пламя (таблица owned) и надевает его (users.equip) по нику: сервер дальше проверяет всё как обычно.
//   node tests/demo_flames.js <relay.db> 'Ник=flame_id[,другие_купленные]' ...
const { DatabaseSync } = require('node:sqlite');
const [db, ...specs] = process.argv.slice(2);
const d = new DatabaseSync(db);
d.exec('PRAGMA busy_timeout=3000');
let n = 0;
for (const sp of specs) {
  const [nick, list] = sp.split('=');
  const items = list.split(',');
  for (const u of d.prepare('SELECT id, equip FROM users WHERE nick=?').all(nick)) {
    for (const it of items) d.prepare('INSERT OR IGNORE INTO owned(uid,item,ts) VALUES(?,?,?)').run(u.id, it, Date.now());
    let e = {}; try { e = JSON.parse(u.equip || '{}'); } catch {}
    e.flame = items[0];
    d.prepare('UPDATE users SET equip=?, sparks=MAX(sparks, 2000) WHERE id=?').run(JSON.stringify(e), u.id);
    n++;
  }
}
console.log('demo flames:', n);
