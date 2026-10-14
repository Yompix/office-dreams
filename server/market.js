'use strict';
/*
 * Token market: live price of the game token on STON.fi.
 * The server polls the STON.fi asset API (price in USD) and DexScreener (price in TON, 24h change, volume,
 * liquidity, pool link) once a minute, keeps a price point every 5 minutes for the chart (30 days), and
 * reads a player's token balance from their linked wallet (toncenter v3). Off until TOKEN_ADDRESS is set.
 */
const Ton = require('./ton.js');

const TON_ASSET = '0:' + '0'.repeat(64); // native TON in the STON.fi asset list
const POLL_MS = 60000, HIST_MS = 5 * 60000, HIST_KEEP = 30 * 86400000, BAL_TTL = 60000;
let last = null, lastAt = 0, histAt = 0, ready = false;
const balCache = new Map();

const enabled = (cfg) => !!(cfg.token && cfg.token.addr && Ton.parseAddress(cfg.token.addr));
function init(ctx) {
  if (ready) return; ready = true;
  ctx.db.raw.exec('CREATE TABLE IF NOT EXISTS price_hist (ts INTEGER PRIMARY KEY, usd REAL, ton REAL)');
}
async function getJson(url, headers) {
  const r = await fetch(url, { headers: headers || {}, signal: AbortSignal.timeout(8000) });
  if (!r.ok) throw new Error('http ' + r.status);
  return r.json();
}

async function poll(ctx, now) {
  if (!enabled(ctx.cfg)) return null;
  init(ctx);
  const addr = ctx.cfg.token.addr;
  const [st, dx, tn] = await Promise.all([
    getJson('https://api.ston.fi/v1/assets/' + encodeURIComponent(addr)).catch(() => null),
    getJson('https://api.dexscreener.com/latest/dex/tokens/' + encodeURIComponent(addr)).catch(() => null),
    getJson('https://api.ston.fi/v1/assets/' + TON_ASSET).catch(() => null),
  ]);
  const a = st && st.asset;
  // the deepest STON.fi pool of this token against TON
  const pairs = ((dx && dx.pairs) || []).filter((p) => p.chainId === 'ton' && p.baseToken && Ton.parseAddress(p.baseToken.address) && Ton.rawAddress(Ton.parseAddress(p.baseToken.address)) === Ton.rawAddress(Ton.parseAddress(addr)));
  const pair = pairs.filter((p) => p.dexId === 'stonfi').sort((x, y) => ((y.liquidity && y.liquidity.usd) || 0) - ((x.liquidity && x.liquidity.usd) || 0))[0] || pairs[0] || null;
  const usd = Number((a && (a.dex_price_usd || a.third_party_price_usd)) || (pair && pair.priceUsd) || 0);
  if (!usd) return last;
  const tonUsd = Number(tn && tn.asset && (tn.asset.dex_price_usd || tn.asset.third_party_price_usd)) || 0;
  const ton = pair && pair.quoteToken && /^(TON|WTON|pTON)$/i.test(pair.quoteToken.symbol || '') ? Number(pair.priceNative) || 0 : tonUsd ? usd / tonUsd : 0;
  last = {
    usd, ton,
    sym: (a && a.symbol) || ctx.cfg.tokenSymbol,
    name: (a && a.display_name) || '',
    img: (a && a.image_url) || (pair && pair.info && pair.info.imageUrl) || '',
    decimals: (a && Number(a.decimals)) || 9,
    ch24: pair && pair.priceChange ? Number(pair.priceChange.h24) || 0 : null,
    vol24: pair && pair.volume ? Number(pair.volume.h24) || 0 : null,
    liq: pair && pair.liquidity ? Number(pair.liquidity.usd) || 0 : null,
    fdv: pair ? Number(pair.fdv || pair.marketCap) || null : null,
    pool: pair ? pair.url : '',
  };
  lastAt = now;
  if (now - histAt >= HIST_MS - 1000) {
    histAt = now;
    ctx.db.run('INSERT OR REPLACE INTO price_hist (ts, usd, ton) VALUES (?, ?, ?)', Math.floor(now / HIST_MS) * HIST_MS, usd, ton || null);
    ctx.db.run('DELETE FROM price_hist WHERE ts < ?', now - HIST_KEEP);
  }
  return last;
}

const swapUrl = (cfg) => 'https://app.ston.fi/swap?chartVisible=false&ft=TON&tt=' + encodeURIComponent(cfg.token.addr);

/* public view: current price, last 24h / 7d points for the chart, buy and chart links */
function view(ctx, now, range) {
  if (!enabled(ctx.cfg)) return { ok: true, on: false };
  init(ctx);
  const span = range === '7d' ? 7 * 86400000 : range === '30d' ? 30 * 86400000 : 86400000;
  let hist = ctx.db.all('SELECT ts, usd FROM price_hist WHERE ts >= ? ORDER BY ts', now - span).map((r) => [r.ts, r.usd]);
  const step = Math.ceil(hist.length / 120); if (step > 1) hist = hist.filter((_, i) => i % step === 0 || i === hist.length - 1);
  return {
    ok: true, on: true, addr: ctx.cfg.token.addr, at: lastAt,
    m: last, hist, range: range || '24h',
    buy: swapUrl(ctx.cfg),
    chart: (last && last.pool) || 'https://dexscreener.com/ton/' + encodeURIComponent(ctx.cfg.token.addr),
  };
}

/* the player's token balance in the linked wallet (toncenter v3), cached for a minute */
async function balance(ctx, u, now) {
  if (!enabled(ctx.cfg) || !u || !u.wallet) return { ok: true, bal: null };
  const hit = balCache.get(u.wallet);
  if (hit && now - hit.at < BAL_TTL) return { ok: true, bal: hit.bal };
  const v3 = (ctx.cfg.token.v3 || 'https://toncenter.com/api/v3').replace(/\/+$/, '');
  const key = ctx.cfg.tonPay && ctx.cfg.tonPay.key;
  try {
    const j = await getJson(`${v3}/jetton/wallets?owner_address=${encodeURIComponent(u.wallet)}&jetton_address=${encodeURIComponent(ctx.cfg.token.addr)}&limit=1`, key ? { 'X-API-Key': key } : {});
    const w = j && j.jetton_wallets && j.jetton_wallets[0];
    const dec = (last && last.decimals) || 9;
    const bal = w ? Number(BigInt(w.balance)) / Math.pow(10, dec) : 0;
    balCache.set(u.wallet, { at: now, bal });
    if (balCache.size > 5000) balCache.clear();
    return { ok: true, bal };
  } catch (e) {
    return { ok: true, bal: hit ? hit.bal : null };
  }
}

module.exports = { enabled, poll, view, balance, swapUrl, POLL_MS };
