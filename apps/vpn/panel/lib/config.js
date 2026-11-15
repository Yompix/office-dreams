'use strict';
/* All settings come from environment variables (see .env.example). */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const { DEFAULT_TIERS } = require('./regions.js');
const DEFAULT_PLANS = DEFAULT_TIERS.mid;

function load(e = process.env) {
  const botToken = e.BOT_TOKEN || '';
  // PRICES = {"high":[...],"mid":[...],"low":[...]} (each plan: id, days, stars, usd); PLANS (old) = the mid tier
  const tiers = { ...DEFAULT_TIERS, ...(e.PRICES ? JSON.parse(e.PRICES) : {}) };
  if (e.PLANS) tiers.mid = JSON.parse(e.PLANS);
  const plans = tiers.mid;
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
    tiers,
    // money report: what the owner really gets and what Railway costs
    money: {
      starUsd: Number(e.STAR_USD || 0.013),          // owner's share per Telegram Star
      cryptoFee: Number(e.CRYPTO_FEE || 0.03),       // CryptoBot fee
      cardFee: Number(e.CARD_FEE || 0.04),           // card provider fee
      egressUsdPerGb: Number(e.EGRESS_USD_GB || 0.05), // Railway traffic price
      fixedUsd: Number(e.COST_FIXED_USD || 5),       // per month: Railway plan + CPU/RAM of panel and node
      extraUsd: Number(e.COST_EXTRA_USD || 0),       // per month: VPS, domain…
    },
    // card payments only for these Telegram languages (the card provider works in cardCurrency)
    cardLangs: (e.CARD_LANGS || 'ru,be,kk').split(',').map((s) => s.trim()),
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
