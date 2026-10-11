'use strict';
/* A tiny fake of the Telegram Bot API for tests. Records every call. */
const http = require('http');

function startMock(opts = {}) {
  const calls = [];
  const members = opts.members || new Set(); // user ids that are channel members
  const server = http.createServer((req, res) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      const m = /^\/bot([^/]+)\/(\w+)$/.exec(req.url);
      let body = {};
      try { body = JSON.parse(raw || '{}'); } catch { /* ignore */ }
      if (!m) { res.writeHead(404); return res.end('{}'); }
      const method = m[2];
      calls.push({ method, body });
      let result = true;
      if (method === 'getMe') result = { id: 1, is_bot: true, username: 'od_test_bot' };
      else if (method === 'createInvoiceLink') result = 'https://t.me/$mockinvoice' + calls.length;
      else if (method === 'getChatMember') {
        result = { status: members.has(body.user_id) ? 'member' : 'left', user: { id: body.user_id } };
      } else if (method === 'getUpdates') result = [];
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: true, result }));
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      resolve({
        url: 'http://127.0.0.1:' + server.address().port,
        calls,
        members,
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}
module.exports = { startMock };
