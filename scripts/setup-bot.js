'use strict';
/* Checks the configuration and registers everything with Telegram:
 *   node scripts/setup-bot.js
 * - verifies BOT_TOKEN (getMe) and that BOT_USERNAME matches
 * - sets commands + descriptions (EN/RU/UK), the "Play" menu button and the webhook
 * - checks that the channel task can be verified (the bot must be an admin of CHANNEL)
 * - prints the links to share
 */
const cfg = require('../server/config.js');
const { createContext } = require('../server/app.js');
const Bot = require('../server/bot.js');

(async () => {
  const problems = [];
  const warn = [];
  if (!cfg.botToken) problems.push('BOT_TOKEN is empty — get it from @BotFather (/newbot).');
  if (!cfg.botUsername) problems.push('BOT_USERNAME is empty — your bot username without @.');
  if (!/^https:\/\//.test(cfg.publicUrl)) problems.push('PUBLIC_URL must be the https:// address of this server (Telegram opens Mini Apps only over HTTPS).');
  if (!cfg.adminKey || cfg.adminKey.length < 12) warn.push('ADMIN_KEY is empty or short — the /admin panel will be unusable or weak.');
  if (cfg.devAuth) problems.push('DEV_AUTH is on — turn it off (DEV_AUTH=0) on a public server!');
  if (!process.env.SESSION_SECRET) warn.push('SESSION_SECRET is not set — a value derived from BOT_TOKEN is used (fine, but set your own random string).');
  const [maj, min] = process.versions.node.split('.').map(Number);
  if (maj < 22 || (maj === 22 && min < 13)) problems.push('Node.js ' + process.versions.node + ' is too old: need 22.13 or newer (built-in SQLite).');

  if (problems.length) {
    console.log('\n✖ Fix these first:\n  - ' + problems.join('\n  - '));
    if (warn.length) console.log('\n⚠ Also:\n  - ' + warn.join('\n  - '));
    process.exit(1);
  }
  const ctx = createContext(cfg, { log: () => {} });
  const me = await ctx.tg.call('getMe');
  if (!me.ok) { console.log('✖ Telegram rejected BOT_TOKEN:', me.description || me.error || 'network error'); process.exit(1); }
  if (me.result.username.toLowerCase() !== cfg.botUsername.toLowerCase()) {
    console.log(`✖ BOT_USERNAME (${cfg.botUsername}) does not match the token's bot (@${me.result.username}).`);
    process.exit(1);
  }
  console.log('✓ Bot: @' + me.result.username);
  const r = await Bot.setup(ctx);
  console.log('✓ Commands and descriptions set (EN/RU/UK)');
  if (r.menu) console.log(r.menu.ok ? '✓ Menu button "Play" → ' + cfg.publicUrl : '✖ Menu button: ' + r.menu.description);
  if (r.webhook) console.log(r.webhook.ok ? '✓ Webhook (' + cfg.botMode + ')' : '✖ Webhook: ' + r.webhook.description);
  if (cfg.channel) {
    const c = await ctx.tg.call('getChatMember', { chat_id: cfg.channel, user_id: me.result.id });
    const st = c.ok ? c.result.status : '';
    if (st === 'administrator' || st === 'creator') console.log('✓ Channel ' + cfg.channel + ': the bot is an admin, the subscribe task can be verified');
    else warn.push(`Channel ${cfg.channel}: add the bot as an ADMIN of the channel, otherwise the "subscribe" task cannot be verified.`);
  }
  const app = cfg.appShort ? `https://t.me/${cfg.botUsername}/${cfg.appShort}` : '';
  console.log('\nLinks:');
  console.log('  Bot:       https://t.me/' + cfg.botUsername);
  if (app) console.log('  Game:      ' + app + '   (create it in @BotFather → /newapp, short name "' + cfg.appShort + '", URL ' + cfg.publicUrl + ')');
  console.log('  Admin:     ' + cfg.publicUrl + '/admin');
  if (warn.length) console.log('\n⚠ Warnings:\n  - ' + warn.join('\n  - '));
  ctx.db.close();
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
