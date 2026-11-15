'use strict';
/* All settings come from environment variables (see .env.example). */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DEFAULT_PLANS = [
  // owner gets ~$0.013 per star; Railway traffic costs $0.05/GB (~$1-2 per active user a month)
  { id: 'm1', days: 30, stars: 199, usd: 2.99 },
  { id: 'm3', days: 90, stars: 499, usd: 7.49 },
  { id: 'y1', days: 365, stars: 1690, usd: 24.99 },
];

function load(e = process.env) {
  const botToken = e.BOT_TOKEN || '';
  let plans = DEFAULT_PLANS;
  if (e.PLANS) plans = JSON.parse(e.PLANS);
  return {
    port: Number(e.PORT) || 8080,
    publicUrl: (e.PUBLIC_URL || '').replace(/\/+$/, ''),
    brand: e.BRAND || 'YURK',
    botToken,
    botUsername: (e.BOT_USERNAME || '').replace(/^@/, ''),
    adminIds: (e.ADMIN_IDS || '').split(',').map((s) => Number(s.trim())).filter((n) => n > 0),
    adminKey: e.ADMIN_KEY || '',
    support: e.SUPPORT || '',
    // @CryptoBot -> Crypto Pay -> Create App -> API token. Empty = crypto hidden
    cryptoToken: e.CRYPTOBOT_TOKEN || '',
    cryptoTestnet: e.CRYPTOBOT_TESTNET === '1',
    cryptoAssets: e.CRYPTOBOT_ASSETS || 'USDT,TON,BTC,ETH,LTC,TRX',
    // card provider token from @BotFather -> Payments (YooKassa etc.). Empty = cards shown as 'soon'
    cardToken: e.CARD_PROVIDER_TOKEN || '',
    cardCurrency: e.CARD_CURRENCY || 'RUB',
    cardRate: Number(e.CARD_RATE || 100), // card currency units per 1 USD
    dbPath: e.DB_PATH || (fs.existsSync('/data') ? '/data/vpn.db' : path.join(__dirname, '..', 'data', 'vpn.db')),
    trialDays: Number(e.TRIAL_DAYS ?? 3),
    refDays: Number(e.REF_DAYS ?? 7),
    plans,
    // a node running as a Railway service (see apps/vpn/railway-node); TCP is "host:port" of its TCP proxy
    railwayNode: e.RAILWAY_NODE_SECRET && e.RAILWAY_NODE_HOST ? {
      name: e.RAILWAY_NODE_NAME || '🌐 Railway', secret: e.RAILWAY_NODE_SECRET, host: e.RAILWAY_NODE_HOST,
      rhost: (e.RAILWAY_NODE_TCP || '').split(':')[0] || '', rport: Number((e.RAILWAY_NODE_TCP || '').split(':')[1]) || 0,
    } : null,
    // Telegram sends this back in X-Telegram-Bot-Api-Secret-Token, so we know a webhook call is real
    hookSecret: botToken ? crypto.createHash('sha256').update('hook:' + botToken).digest('hex').slice(0, 48) : '',
  };
}

module.exports = { load, DEFAULT_PLANS };
