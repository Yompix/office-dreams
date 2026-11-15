'use strict';
/* All settings come from environment variables (see .env.example). */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DEFAULT_PLANS = [
  { id: 'm1', days: 30, stars: 150 },
  { id: 'm3', days: 90, stars: 400 },
  { id: 'y1', days: 365, stars: 1400 },
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
    dbPath: e.DB_PATH || (fs.existsSync('/data') ? '/data/vpn.db' : path.join(__dirname, '..', 'data', 'vpn.db')),
    trialDays: Number(e.TRIAL_DAYS ?? 3),
    refDays: Number(e.REF_DAYS ?? 7),
    plans,
    // Telegram sends this back in X-Telegram-Bot-Api-Secret-Token, so we know a webhook call is real
    hookSecret: botToken ? crypto.createHash('sha256').update('hook:' + botToken).digest('hex').slice(0, 48) : '',
  };
}

module.exports = { load, DEFAULT_PLANS };
