'use strict';
/* Minimal Telegram Bot API client. */
function client(token) {
  return {
    async call(method, params = {}) {
      const r = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(params),
        signal: AbortSignal.timeout(method === 'getUpdates' ? 60000 : 15000),
      });
      const j = await r.json().catch(() => ({ ok: false, description: 'bad json' }));
      if (!j.ok) throw new Error(`${method}: ${j.description || r.status}`);
      return j.result;
    },
  };
}
module.exports = { client };
