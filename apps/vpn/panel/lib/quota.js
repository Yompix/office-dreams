'use strict';
/* Traffic limits by plan. The window is the calendar month (UTC), and it starts again on a new purchase,
   so a user who ran out can simply buy again. Limits are in GB per month; 0 = no limit. */

const GB = 1024 ** 3;
const dayOf = (ms) => new Date(ms).toISOString().slice(0, 10);
const monthDay = (now) => dayOf(Date.UTC(new Date(now).getUTCFullYear(), new Date(now).getUTCMonth(), 1));

// first day counted for this user
const windowStart = (u, now) => { const m = monthDay(now); const p = u.paid_at ? dayOf(u.paid_at) : ''; return p > m ? p : m; };

const quotaGb = (cfg, u) => {
  const q = cfg.quotas[u.plan || 'm1'];
  return q === undefined ? cfg.quotas.m1 : q;
};

function usage(store, cfg, u, now = Date.now()) {
  const from = windowStart(u, now);
  const used = store.userTrafficSince(u.id, from);
  const q = quotaGb(cfg, u);
  return { used, quota: q * GB, usedGb: Math.round((used / GB) * 10) / 10, quotaGb: q, over: q > 0 && used >= q * GB, share: q > 0 ? used / (q * GB) : 0, from };
}

// users who may connect right now: paid / trial and inside their traffic limit
function allowedUsers(store, cfg, now = Date.now()) {
  return store.activeUsers(now).filter((u) => !usage(store, cfg, u, now).over);
}

module.exports = { GB, usage, allowedUsers, quotaGb, windowStart, monthDay };
