'use strict';
/*
 * Token market: live price of the game token on Solana.
 * The server polls Jupiter (price in USD) and DexScreener (price in SOL, 24h change, volume, liquidity,
 * pool link) once a minute, keeps a price point every 5 minutes for the chart (30 days), and reads a
 * player's token balance from their Solana wallet (JSON-RPC). Off until TOKEN_ADDRESS (the mint) is set.
 */

const POLL_MS = 60000, HIST_MS = 5 * 60000, HIST_KEEP = 30 * 86400000, BAL_TTL = 60000;
const SOL_MINT = 'So11111111111111111111111111111111111111112';
let last = null, lastAt = 0, histAt = 0, ready = false;
const balCache = new Map();

/* base58 → bytes; a Solana address (wallet or mint) is 32 bytes */
const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58decode(s) {
  if (typeof s !== 'string' || !s || s.length > 64) return null;
  let n = 0n;
  for (const ch of s) { const i = B58.indexOf(ch); if (i < 0) return null; n = n * 58n + BigInt(i); }
  const out = [];
  while (n > 0n) { out.unshift(Number(n & 255n)); n >>= 8n; }
  for (const ch of s) { if (ch !== '1') break; out.unshift(0); }
  return out;
}
const isSolAddr = (s) => { s = String(s || '').trim(); const b = s.length >= 32 && s.length <= 44 ? b58decode(s) : null; return !!b && b.length === 32; };

const enabled = (cfg) => !!(cfg.token && cfg.token.addr && isSolAddr(cfg.token.addr));
function init(ctx) {
  if (ready) return; ready = true;
  ctx.db.raw.exec('CREATE TABLE IF NOT EXISTS price_hist (ts INTEGER PRIMARY KEY, usd REAL, ton REAL)');
}
async function getJson(url, opts) {
  const r = await fetch(url, Object.assign({ signal: AbortSignal.timeout(8000) }, opts || {}));
  if (!r.ok) throw new Error('http ' + r.status);
  return r.json();
}

async function poll(ctx, now) {
  if (!enabled(ctx.cfg)) return null;
  init(ctx);
  const mint = ctx.cfg.token.addr.trim();
  const [jp, dx] = await Promise.all([
    getJson('https://lite-api.jup.ag/price/v3?ids=' + mint + ',' + SOL_MINT).catch(() => null),
    getJson('https://api.dexscreener.com/latest/dex/tokens/' + mint).catch(() => null),
  ]);
  const j = jp && jp[mint], js = jp && jp[SOL_MINT];
  // the deepest Solana pool where this token is the base
  const pairs = ((dx && dx.pairs) || []).filter((p) => p.chainId === 'solana' && p.baseToken && p.baseToken.address === mint)
    .sort((x, y) => ((y.liquidity && y.liquidity.usd) || 0) - ((x.liquidity && x.liquidity.usd) || 0));
  const pair = pairs[0] || null;
  const usd = Number((j && j.usdPrice) || (pair && pair.priceUsd) || 0);
  if (!usd) return last;
  const solUsd = Number(js && js.usdPrice) || 0;
  const sol = pair && pair.quoteToken && pair.quoteToken.address === SOL_MINT ? Number(pair.priceNative) || 0 : solUsd ? usd / solUsd : 0;
  last = {
    usd, ton: sol, // 'ton' keeps the old field name: price in the chain's native coin (SOL)
    sym: (pair && pair.baseToken && pair.baseToken.symbol) || ctx.cfg.tokenSymbol,
    name: (pair && pair.baseToken && pair.baseToken.name) || '',
    img: (pair && pair.info && pair.info.imageUrl) || '',
    decimals: (j && Number(j.decimals)) || 9,
    ch24: pair && pair.priceChange ? Number(pair.priceChange.h24) || 0 : j && j.priceChange24h != null ? Number(j.priceChange24h) || 0 : null,
    vol24: pair && pair.volume ? Number(pair.volume.h24) || 0 : null,
    liq: pair && pair.liquidity ? Number(pair.liquidity.usd) || 0 : null,
    fdv: pair ? Number(pair.fdv || pair.marketCap) || null : null,
    pool: pair ? pair.url : '',
    dex: pair ? pair.dexId : '',
  };
  lastAt = now;
  if (now - histAt >= HIST_MS - 1000) {
    histAt = now;
    ctx.db.run('INSERT OR REPLACE INTO price_hist (ts, usd, ton) VALUES (?, ?, ?)', Math.floor(now / HIST_MS) * HIST_MS, usd, sol || null);
    ctx.db.run('DELETE FROM price_hist WHERE ts < ?', now - HIST_KEEP);
  }
  return last;
}

const swapUrl = (cfg) => 'https://jup.ag/swap/SOL-' + cfg.token.addr.trim();

/* public view: current price, last 24h / 7d / 30d points for the chart, buy and chart links */
function view(ctx, now, range) {
  if (!enabled(ctx.cfg)) return { ok: true, on: false };
  init(ctx);
  const span = range === '7d' ? 7 * 86400000 : range === '30d' ? 30 * 86400000 : 86400000;
  let hist = ctx.db.all('SELECT ts, usd FROM price_hist WHERE ts >= ? ORDER BY ts', now - span).map((r) => [r.ts, r.usd]);
  const step = Math.ceil(hist.length / 120); if (step > 1) hist = hist.filter((_, i) => i % step === 0 || i === hist.length - 1);
  return {
    ok: true, on: true, chain: 'solana', addr: ctx.cfg.token.addr.trim(), at: lastAt,
    m: last, hist, range: range || '24h',
    buy: swapUrl(ctx.cfg),
    chart: (last && last.pool) || 'https://dexscreener.com/solana/' + ctx.cfg.token.addr.trim(),
  };
}

/* the player's token balance in their Solana wallet (JSON-RPC), cached for a minute */
async function balance(ctx, u, now) {
  if (!enabled(ctx.cfg) || !u || !u.sol) return { ok: true, bal: null };
  const hit = balCache.get(u.sol);
  if (hit && now - hit.at < BAL_TTL) return { ok: true, bal: hit.bal };
  try {
    const j = await getJson(ctx.cfg.token.rpc || 'https://api.mainnet-beta.solana.com', {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'getTokenAccountsByOwner', params: [u.sol, { mint: ctx.cfg.token.addr.trim() }, { encoding: 'jsonParsed' }] }),
    });
    if (!j || !j.result) throw new Error('rpc');
    const bal = (j.result.value || []).reduce((t, a) => {
      const ta = a.account && a.account.data && a.account.data.parsed && a.account.data.parsed.info && a.account.data.parsed.info.tokenAmount;
      return t + (ta ? Number(ta.uiAmountString || ta.uiAmount) || 0 : 0);
    }, 0);
    balCache.set(u.sol, { at: now, bal });
    if (balCache.size > 5000) balCache.clear();
    return { ok: true, bal };
  } catch (e) {
    return { ok: true, bal: hit ? hit.bal : null };
  }
}

module.exports = { enabled, poll, view, balance, swapUrl, isSolAddr, POLL_MS };
