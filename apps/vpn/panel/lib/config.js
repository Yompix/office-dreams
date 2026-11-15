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
    refDays: Number(e.REF_DAYS ?? 3),
    // partner program: % of what the owner really got, level 1 and level 2; min withdrawal in USD
    referral: { l1Pct: Number(e.REF1_PCT ?? 20), l2Pct: Number(e.REF2_PCT ?? 5), minOut: Number(e.REF_MIN_OUT ?? 5),
      autoMax: Number(e.REF_AUTO_MAX ?? 50) }, // withdrawals up to this many $ are paid at once; bigger ones wait for the owner
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
    // nodes running as Railway services (apps/vpn/railway-node): RAILWAY_NODE_*, RAILWAY_NODE2_*, … RAILWAY_NODE9_*
    // SECRET, HOST (https domain), TCP ("host:port" of the TCP proxy), NAME
    railwayNodes: [1, 2, 3, 4, 5, 6, 7, 8, 9].map((i) => {
      const k = (x) => e[`RAILWAY_NODE${i > 1 ? i : ''}_${x}`] || '';
      if (!k('SECRET') || !k('HOST')) return null;
      const [rhost, rport] = k('TCP').split(':');
      return { name: k('NAME') || '🌐 Railway', secret: k('SECRET'), host: k('HOST'), rhost: rhost || '', rport: Number(rport) || 0 };
    }).filter(Boolean),
    // traffic limit in GB per month by plan (0 = none). On Railway every GB costs $0.05
    quotas: { trial: 3, m1: 50, m3: 60, y1: 80, ...(e.QUOTAS ? JSON.parse(e.QUOTAS) : {}) },
    // Telegram sends this back in X-Telegram-Bot-Api-Secret-Token, so we know a webhook call is real
    hookSecret: botToken ? crypto.createHash('sha256').update('hook:' + botToken).digest('hex').slice(0, 48) : '',
  };
}

module.exports = { load, DEFAULT_PLANS };
