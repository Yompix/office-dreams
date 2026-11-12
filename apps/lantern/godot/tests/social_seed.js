'use strict';
// Боты-друзья для скриншотов LANTERN: регистрируются, дружат с игроком из приложения, хостят открытые миры, пишут в чат.
// node tests/social_seed.js ws://127.0.0.1:PORT/ws <файл с тегом игрока>
const WebSocket = require(require.resolve('ws', { paths: [__dirname + '/../../relay', __dirname + '/../../../relay'] }));
const fs = require('fs');
const crypto = require('crypto');
const [url, tagFile] = process.argv.slice(2);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const tx = (w, o) => w.send(JSON.stringify(o));
function conn() {
  return new Promise((res) => {
    const w = new WebSocket(url);
    w.inbox = [];
    w.on('message', (d, b) => { if (!b) w.inbox.push(JSON.parse(d)); });
    w.on('open', () => res(w));
  });
}
async function bot(nick) {
  const w = await conn();
  w.token = crypto.randomBytes(32).toString('hex');
  tx(w, { t: 'register', token: w.token, nick });
  while (!w.inbox.find((x) => x.t === 'welcome')) await sleep(20);
  w.me = w.inbox.find((x) => x.t === 'welcome').me;
  return w;
}
(async () => {
  while (!fs.existsSync(tagFile)) await sleep(200);
  const tag = fs.readFileSync(tagFile, 'utf8').trim();
  const bots = [await bot('Алекс'), await bot('Маша_Кубик'), await bot('Кирилл'), await bot('Нюта')];
  for (const b of bots.slice(0, 3)) tx(b, { t: 'friend_add', tag });
  const rooms = [
    [0, 'java', { title: 'Выживание с нуля', tags: ['выживание'], version: '1.21.11', slots: 10 }, 'survival'],
    [1, 'both', { title: 'Новоград: город мечты', tags: ['новоград', 'творчество'], version: '26.3', slots: 20 }, 'creative'],
    [3, 'bedrock', { title: 'Мини-игры у Нюты', tags: ['мини-игры', 'pvp'], version: '', slots: 8 }, 'minigame'],
  ];
  const hosts = [];
  for (const [bi, ed, pub, mode] of rooms) {
    const h = await conn();
    // 1.7: режим игры — у каждой комнаты своя палитра обложки
    tx(h, { t: 'host', v: 1, edition: ed, name: pub.title, public: pub, token: bots[bi].token, settings: { mode, cap: pub.slots, password: '' } });
    while (!h.inbox.find((m) => m.t === 'room')) await sleep(20);
    h.code = h.inbox.find((m) => m.t === 'room').code;
    hosts.push(h);
  }
  for (let i = 0; i < 5; i++) { const g = await conn(); tx(g, { t: 'join', v: 1, code: hosts[i % 3].code, token: bots[2].token }); }
  tx(bots[1], { t: 'presence', activity: 'hosting', world: rooms[1][2].title, edition: 'both', version: '26.3', code: hosts[1].code });
  tx(bots[0], { t: 'presence', activity: 'playing', world: rooms[0][2].title, edition: 'java', code: hosts[0].code });
  for (let i = 0; i < 150; i++) {
    const st = [...bots[0].inbox].reverse().find((m) => m.t === 'state' && m.friends && m.friends.length);
    if (st) {
      const fid = st.friends[0].id;
      tx(bots[0], { t: 'dm', to: fid, text: 'Привет! Заходи к нам в «Выживание с нуля», мы строим замок' });
      await sleep(300);
      tx(bots[0], { t: 'dm', to: fid, text: 'Жми «Присоединиться» у меня в списке :)' });
      break;
    }
    await sleep(200);
  }
  fs.writeFileSync(tagFile + '.ok', 'ok');
  console.log('seed ok');
  setInterval(() => {}, 1000);
})();
