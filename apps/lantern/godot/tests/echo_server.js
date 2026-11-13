// Эхо-сервер для замера задержки моста LANTERN (tests/latency.sh).
//  TCP <порт>: эхо; строка «BLAST <мегабайт>\n» — в ответ столько мегабайт данных (проверка скорости «как загрузка чанков»).
//  UDP <порт>: эхо датаграмм; RakNet-пинг (0x01/0x02) — понг, чтобы мост считал мир живым.
// node tests/echo_server.js <tcp_port> <udp_port>
const net = require('net'), dgram = require('dgram');
const TP = +(process.argv[2] || 25599), UP = +(process.argv[3] || 19199);
const MAGIC = Buffer.from('00ffff00fefefefefdfdfdfd12345678', 'hex'), GUID = Buffer.from('0102030405060708', 'hex');
net.createServer((c) => {
  c.setNoDelay(true);
  c.on('data', (d) => {
    if (d.length < 32 && d.toString('latin1').startsWith('BLAST ')) {
      let left = (+d.toString('latin1').slice(6) || 1) * 1024 * 1024;
      const chunk = Buffer.alloc(32768, 0x5a);
      const pump = () => { while (left > 0) { const b = left >= chunk.length ? chunk : chunk.subarray(0, left); left -= b.length; if (!c.write(b)) return c.once('drain', pump); } };
      return pump();
    }
    c.write(d);
  });
  c.on('error', () => {});
}).listen(TP, '0.0.0.0', () => console.log('tcp echo', TP));
const u = dgram.createSocket('udp4');
u.on('message', (m, r) => {
  if (m[0] === 0x01 || m[0] === 0x02) {
    const s = Buffer.from(`MCPE;bench;712;1.21.50;0;8;${GUID.readBigUInt64BE()};bench;Survival;1;${UP};${UP + 1};`), len = Buffer.alloc(2); len.writeUInt16BE(s.length);
    return u.send(Buffer.concat([Buffer.from([0x1c]), m.subarray(1, 9), GUID, MAGIC, len, s]), r.port, r.address);
  }
  u.send(m, r.port, r.address);
});
u.bind(UP, '0.0.0.0', () => console.log('udp echo', UP));
