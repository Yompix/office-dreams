'use strict';
/* Regional prices. The region comes from the Telegram language: IP is useless for a VPN
   (a connected user shows the server's country). Tiers can be overridden with the PRICES env (JSON). */

const DEFAULT_TIERS = {
  // owner gets ~$0.013 per star; Railway traffic costs $0.05/GB (~$1-2 per active user a month)
  high: [{ id: 'm1', days: 30, stars: 299, usd: 4.99 }, { id: 'm3', days: 90, stars: 749, usd: 12.99 }, { id: 'y1', days: 365, stars: 3290, usd: 49.99 }],
  mid: [{ id: 'm1', days: 30, stars: 199, usd: 2.99 }, { id: 'm3', days: 90, stars: 499, usd: 7.49 }, { id: 'y1', days: 365, stars: 1990, usd: 29.99 }],
  low: [{ id: 'm1', days: 30, stars: 149, usd: 1.99 }, { id: 'm3', days: 90, stars: 379, usd: 4.99 }, { id: 'y1', days: 365, stars: 1590, usd: 21.99 }],
};

const TIER_OF_LANG = {
  // richer countries
  de: 'high', fr: 'high', it: 'high', nl: 'high', sv: 'high', no: 'high', nb: 'high', da: 'high', fi: 'high', ja: 'high', ko: 'high', he: 'high',
  // lower purchasing power, big VPN markets
  uk: 'low', fa: 'low', tr: 'low', uz: 'low', ky: 'low', tg: 'low', tk: 'low', az: 'low', hy: 'low', ka: 'low',
  id: 'low', hi: 'low', vi: 'low', bn: 'low', ur: 'low', my: 'low', ms: 'low', fil: 'low',
  // everyone else (ru, en, es, pt, zh, ar, kk, be, pl …) — mid
};

const tierOf = (lang) => TIER_OF_LANG[String(lang || '').slice(0, 2)] || 'mid';
const TIERS = Object.keys(DEFAULT_TIERS);

module.exports = { DEFAULT_TIERS, TIER_OF_LANG, TIERS, tierOf };
