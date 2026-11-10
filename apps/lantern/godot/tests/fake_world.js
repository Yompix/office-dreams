// Поддельный «мой мир из Minecraft» для автотеста ownworld (tests/ownworld.sh):
//  Bedrock: UDP-порт (19132) отвечает на RakNet unconnected ping (MOTD мира), остальные датаграммы — эхо «ECHO:…».
//  Java: объявление «Открыть для сети» в 224.0.2.60:4445 + TCP-эхо на порту мира.
// node tests/fake_world.js <папка данных> [bedrock_port] [java_port]
// Ждёт файл <папка>/own-start (чтобы сначала снять экран «мир не найден»); файл own-mute — мир «замолкает».
const dgram = require('dgram'), net = require('net'), fs = require('fs'), path = require('path');
const DATA = process.argv[2] || '/tmp/own';
const BPORT = +(process.argv[3] || 19132), JPORT = +(process.argv[4] || 25599);
const MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex');
const GUID = Buffer.from('0102030405060708', 'hex');
const has = (f) => fs.existsSync(path.join(DATA, f));
function log(...a) { console.log(new Date().toISOString(), ...a); }

function start() {
  const motd = `MCPE;Стив;712;1.21.50;1;8;${GUID.readBigUInt64BE()};Тестовый мир;Survival;1;${BPORT};${BPORT + 1};`;
  const u = dgram.createSocket({ type: 'udp4', reuseAddr: false });
  u.on('message', (m, r) => {
    if (m[0] === 0x01 || m[0] === 0x02) {
      if (has('own-mute')) return;
      const s = Buffer.from(motd, 'utf8'), len = Buffer.alloc(2); len.writeUInt16BE(s.length);
      u.send(Buffer.concat([Buffer.from([0x1c]), m.subarray(1, 9), GUID, MAGIC, len, s]), r.port, r.address);
      return;
    }
    log('bedrock data', m.length, 'from', r.address + ':' + r.port);
    u.send(Buffer.concat([Buffer.from('ECHO:'), m]), r.port, r.address);
  });
  u.bind(BPORT, '0.0.0.0', () => log('bedrock world on', BPORT));
  net.createServer((c) => { log('java tcp from', c.remoteAddress); c.on('data', (d) => c.write(Buffer.concat([Buffer.from('ECHO:'), d]))); c.on('error', () => {}); })
    .listen(JPORT, '0.0.0.0', () => log('java world on', JPORT));
  const a = dgram.createSocket({ type: 'udp4', reuseAddr: true });
  a.bind(0, () => {
    a.setMulticastLoopback(true);
    setInterval(() => { if (!has('own-mute')) a.send(`[MOTD]Стив - Мир Java[/MOTD][AD]${JPORT}[/AD]`, 4445, '224.0.2.60'); }, 1500);
  });
}
const t = setInterval(() => { if (has('own-start')) { clearInterval(t); start(); } }, 200);
setTimeout(() => process.exit(0), 10 * 60_000);
