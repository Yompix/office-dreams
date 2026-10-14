'use strict';
/* Configuration from environment variables (and an optional .env file). No dependencies. */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function normChannel(v) {
  v = String(v || '').trim();
  if (!v) return '';
  if (/^-?\d+$/.test(v)) return v;
  const m = /(?:t(?:elegram)?\.me\/)?@?([A-Za-z0-9_]{4,})\/?$/.exec(v.replace(/^https?:\/\//, ''));
  return m ? '@' + m[1] : v;
}
function loadEnvFile(file) {
  let txt;
  try { txt = fs.readFileSync(file, 'utf8'); } catch { return; }
  for (const line of txt.split(/\r?\n/)) {
    const m = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (!m || line.trim().startsWith('#')) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    if (process.env[m[1]] === undefined) process.env[m[1]] = v;
  }
}
const ROOT = path.join(__dirname, '..');
loadEnvFile(path.join(ROOT, '.env'));

function build(env) {
  const str = (k, d = '') => (env[k] !== undefined && env[k] !== '' ? String(env[k]) : d);
  const num = (k, d) => (env[k] !== undefined && env[k] !== '' && !Number.isNaN(Number(env[k])) ? Number(env[k]) : d);
  const bool = (k, d = false) => (env[k] === undefined || env[k] === '' ? d : /^(1|true|yes|on)$/i.test(env[k]));

  const publicUrl = str('PUBLIC_URL').replace(/\/+$/, '');
  const botToken = str('BOT_TOKEN');
  const cfg = {
    root: ROOT,
    port: num('PORT', 8080),
    host: str('HOST', '0.0.0.0'),
    publicUrl,
    botToken,
    botUsername: str('BOT_USERNAME').replace(/^@/, ''),
    appShort: str('APP_SHORTNAME', 'play'),
    webhookSecret: str('WEBHOOK_SECRET') || crypto.createHash('sha256').update('wh:' + botToken).digest('hex').slice(0, 32),
    // webhook | polling | off ; default: webhook when PUBLIC_URL is https, otherwise polling
    botMode: str('BOT_MODE') || (/^https:\/\//.test(publicUrl) ? 'webhook' : botToken ? 'polling' : 'off'),
    tgApi: str('TG_API_BASE', 'https://api.telegram.org').replace(/\/+$/, ''),
    adminKey: str('ADMIN_KEY'),
    supportKey: str('SUPPORT_KEY'), // optional temporary read-only key for support (GET requests only)
    // developer test accounts: plenty of coins for testing, never earn airdrop points or appear in the top
    devIds: str('DEV_IDS').split(',').map((x) => Number(x.trim())).filter((x) => Number.isSafeInteger(x) && x > 0),
    adminTgIds: str('ADMIN_TG_IDS').split(',').map((x) => Number(x.trim())).filter(Boolean),
    sessionSecret: str('SESSION_SECRET') || crypto.createHash('sha256').update('sess:' + (botToken || crypto.randomBytes(16).toString('hex'))).digest('hex'),
    dbPath: str('DB_PATH') === ':memory:' ? ':memory:' : path.resolve(ROOT, str('DB_PATH', 'data/officedreams.sqlite')),
    devAuth: bool('DEV_AUTH', false), // NEVER enable in production: accepts unsigned users
    trustProxy: bool('TRUST_PROXY', false),
    channel: normChannel(str('CHANNEL') || str('CHANNEL_URL')), // e.g. @officedreams_news (bot must be admin of the channel)
    channelUrl: str('CHANNEL_URL'),
    chatUrl: str('CHAT_URL'),
    xUrl: str('X_URL'),
    season: { id: num('SEASON_ID', 0), name: str('SEASON_NAME', 'Season 0') },
    tokenSymbol: str('TOKEN_SYMBOL', 'OFFICE'),
    // the launched token on Solana (SPL mint address): live price from Jupiter + DexScreener, buy button, holdings
    token: { addr: str('TOKEN_ADDRESS'), rpc: str('SOLANA_RPC', 'https://api.mainnet-beta.solana.com') },
    // airdrop of the whole supply by Season 0 points: starts by itself when the launch fund reaches DONATE_GOAL_USD;
    // AIRDROP_SECRET = secret key of a separate distributor wallet that holds the tokens (base58 as exported from Phantom)
    airdrop: { supply: num('AIRDROP_SUPPLY', 100000000), decimals: num('TOKEN_DECIMALS', 6), secret: str('AIRDROP_SECRET'), auto: str('AIRDROP_AUTO', '1') !== '0' },
    turnstile: { site: str('TURNSTILE_SITEKEY'), secret: str('TURNSTILE_SECRET') },
    starUsd: num('STAR_USD', 0.013),
    // donations toward the token launch: any amount from the minimum up; shown as a shared progress bar
    donate: { minStars: num('DONATE_MIN_STARS', 100), maxStars: num('DONATE_MAX_STARS', 10000), goalUsd: num('DONATE_GOAL_USD', 5000) },
    // regions closed to the game (ISO country codes, from the device time zone) and Telegram app languages
    blockedCountries: str('BLOCKED_COUNTRIES', 'IN,ID,PK,BD,NG').toUpperCase().split(',').map((x) => x.trim()).filter(Boolean),
    blockedLangs: str('BLOCKED_LANGS', 'hi,id,ur,bn,ha,yo,ig').toLowerCase().split(',').map((x) => x.trim()).filter(Boolean),
    // pay with SOL (Solana Pay): the shop wallet that receives transfers; prices follow the Stars price list
    solPay: {
      wallet: str('SOL_PAY_WALLET'),
      usd: num('SOL_USD', 150), // fallback price of 1 SOL in USD; the live price comes from Jupiter
      starUsd: num('SOL_STAR_USD', 0.016), // what a player pays for one Star, so SOL prices match
    },
    eco: {
      dailyD: num('POINTS_DAILY_D', 300), // diminishing-returns scale per day (≈ practical daily cap)
      passBoost: num('PASS_BOOST', 1.25),
      // supporters (any Stars purchase in the last N days) get a small bonus on gameplay points — never points for money directly
      supporterBoost: num('SUPPORTER_BOOST', 1.1),
      supporterDays: num('SUPPORTER_DAYS', 30),
      refL1: num('REF_L1', 0.10),
      refL2: num('REF_L2', 0), // second level off by default: one simple rule is easier to explain
      refDailyCap: num('REF_DAILY_CAP', 150),
      refActivationBonus: num('REF_ACTIVATION_PTS', 25),
      refActivationsPerDay: num('REF_ACTIVATIONS_PER_DAY', 5),
      refActivationMax: num('REF_ACTIVATION_MAX', 100),
      inviteCash: num('INVITE_CASH', 500),
      inviteGems: num('INVITE_GEMS', 2),
      welcomeCash: num('WELCOME_CASH', 300),
      // emission brake parameters (used after the token launch; shown in admin)
      pieShare: num('PIE_REVENUE_SHARE', 0.5),
      pieYear1: num('PIE_YEAR1', 616438),
    },
    // rewarded ads (Adsgram). Rewards are small and capped: the token pie is fixed anyway, ads only add a share of it + revenue
    ads: {
      // provider: Monetag when MONETAG_ZONE is set, otherwise Adsgram when ADSGRAM_BLOCK_ID is set
      provider: str('MONETAG_ZONE') ? 'monetag' : str('ADSGRAM_BLOCK_ID') ? 'adsgram' : '',
      blockId: str('MONETAG_ZONE') || str('ADSGRAM_BLOCK_ID'),
      sdk: str('MONETAG_SDK_URL', 'https://libtl.com/sdk.js'),
      perDay: num('ADS_PER_DAY', 8),
      cooldownSec: num('ADS_COOLDOWN_SEC', 45),
      pts: num('ADS_POINTS', 4),
      usdPerView: num('ADS_USD_PER_VIEW', 0.001),
      callbackKey: str('ADS_CALLBACK_KEY') || str('MONETAG_POSTBACK_KEY'), // if set, rewards are granted only by the ad network's server callback
    },
    minGameMs: 2000,
    limits: { syncPerMin: 40, authPerMinIp: 20, apiPerMin: 90 },
  };
  return cfg;
}
const cfg = build(process.env);
cfg.build = build;
module.exports = cfg;
