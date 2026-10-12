'use strict';
/* Office Dreams server entry point:  node server/index.js */
const http = require('http');
const cfg = require('./config.js');
const { createApp } = require('./app.js');
const Bot = require('./bot.js');
const AB = require('./antibot.js');
const PvP = require('./pvp.js');
const TonPay = require('./tonpay.js');

function main() {
  if (!cfg.botToken && !cfg.devAuth) {
    console.warn('⚠  BOT_TOKEN is not set — players cannot sign in. Copy .env.example to .env and fill it in (see README).');
  }
  if (cfg.devAuth) console.warn('⚠  DEV_AUTH=1: unsigned logins are accepted. Never use this in production.');
  const { ctx, handler } = createApp(cfg);
  const server = http.createServer(handler);
  server.keepAliveTimeout = 65000;
  server.headersTimeout = 70000;
  server.listen(cfg.port, cfg.host, () => ctx.log(`Office Dreams listening on http://${cfg.host}:${cfg.port}  (public: ${cfg.publicUrl || 'not set'})`));

  const timers = [];
  // housekeeping: rate-limit table, anti-bot sweep, WAL checkpoint
  timers.push(setInterval(() => {
    const now = Date.now();
    for (const [k, v] of ctx.rate) if (now - v.t > 120000) ctx.rate.delete(k);
  }, 60000));
  timers.push(setInterval(() => { try { PvP.sweep(ctx); } catch (e) { ctx.log('pvp sweep', e.message); } }, 5000));
  timers.push(setInterval(() => { try { const r = AB.sweep(ctx); if (r.changed) ctx.log('antibot sweep', r); } catch (e) { ctx.log('sweep error', e); } }, 5 * 60000));
  timers.push(setInterval(() => { try { ctx.db.raw.exec('PRAGMA wal_checkpoint(PASSIVE)'); } catch (_) { /* ignore */ } }, 10 * 60000));
  // TON payments: credit transfers that arrived after the player closed the game
  timers.push(setInterval(() => { TonPay.sweep(ctx).then((r) => { if (r.paid) ctx.log('ton payments credited', r); }).catch((e) => ctx.log('ton sweep', e.message)); }, 60000));

  let poller = null;
  if (cfg.botToken && cfg.botMode !== 'off') {
    (async () => {
      const r = await Bot.setup(ctx);
      ctx.log('bot setup:', JSON.stringify(r).slice(0, 400));
      if (cfg.botMode === 'polling') poller = Bot.startPolling(ctx);
    })().catch((e) => ctx.log('bot setup failed', e));
  }

  const shutdown = () => {
    ctx.log('shutting down');
    timers.forEach(clearInterval);
    if (poller) poller.stop();
    server.close(() => { ctx.db.close(); process.exit(0); });
    setTimeout(() => process.exit(0), 5000).unref();
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

if (require.main === module) main();
module.exports = { main };
