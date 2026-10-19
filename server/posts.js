'use strict';
/* Channel posts written ahead: posts/queue.json (in git) holds dated posts about Office Dreams and the server
 * publishes each one to the project channel (CHANNEL) when its time comes. English only (the owner's rule:
 * Telegram translates for the readers). How to write them: posts/README.md.
 *   { "id": "2026-10-19-city", "at": "2026-10-19T16:00:00Z", "text": "<b>…</b>", "photo": "posts/city.jpg", "button": "▶️ Play" }
 *   photo (optional): a file in public/ (e.g. "posts/city.jpg" = public/posts/city.jpg); with a photo the text is a caption (≤ 1024 chars)
 * - each post goes out once (posts_sent): the row is reserved ('sending') before the call, so a crash, a lost reply or
 *   a failed write can never post it twice; a send with an unknown outcome (timeout) is not repeated, the owner is told
 * - one more than LATE_MS overdue is dropped, so a downtime or a late commit never floods the channel; at most one post
 *   per tick and at least GAP_MS between two posts; a refused send is retried after 10, 20, 40… min (TRIES in all)
 * - the owner sees what is coming with /posts in the bot and cancels one with /postskip <id>; a post that fails is
 *   reported to the owner in the bot
 */
const fs = require('fs');
const path = require('path');

const LATE_MS = 12 * 3600e3, GAP_MS = 2 * 3600e3, RETRY_MS = 10 * 60e3, TRIES = 6;
const inited = new WeakSet();
function init(ctx) {
  if (inited.has(ctx.db)) return;
  ctx.db.raw.exec('CREATE TABLE IF NOT EXISTS posts_sent (id TEXT PRIMARY KEY, at INTEGER, status TEXT, msg_id INTEGER, tries INTEGER DEFAULT 0, err TEXT)');
  inited.add(ctx.db);
}
const file = (ctx) => ctx.cfg.postsFile || path.join(ctx.cfg.root, 'posts', 'queue.json');
// the queue, checked: bad entries are left out (and said once in the log), sorted by time
function load(ctx) {
  let raw;
  try { raw = JSON.parse(fs.readFileSync(file(ctx), 'utf8')); } catch (e) {
    if (e.code !== 'ENOENT' && ctx.cache.postsErr !== e.message) { ctx.cache.postsErr = e.message; ctx.log && ctx.log('posts: queue.json unreadable', e.message); }
    return [];
  }
  return check(Array.isArray(raw) ? raw : raw && raw.posts, ctx);
}
// Telegram's HTML: only its tags, balanced; a bare < > & breaks the whole post ("can't parse entities")
const TAGS = new Set(['b', 'strong', 'i', 'em', 'u', 'ins', 's', 'strike', 'del', 'a', 'code', 'pre', 'blockquote', 'tg-spoiler', 'span']);
function badHtml(t) {
  const open = [];
  for (const m of t.matchAll(/<(\/?)([a-z][a-z-]*)((?:\s+[a-z-]+="[^"<>]*")*)\s*>|[<>]|&(?!(?:lt|gt|amp|quot|#\d+);)/gi)) {
    if (!m[2]) return 'bare ' + m[0];
    const tag = m[2].toLowerCase();
    if (!TAGS.has(tag)) return '<' + tag + '> is not allowed';
    if (!m[1]) open.push(tag); else if (open.pop() !== tag) return '</' + tag + '> does not match';
  }
  return open.length ? '<' + open.pop() + '> is not closed' : '';
}
// slices by characters, not UTF-16 units: half an emoji makes Telegram refuse the whole message
const cut = (t, n, tail = '') => { const a = Array.from(t); return a.length > n ? a.slice(0, n - tail.length).join('') + tail : t; };
function check(list, ctx) {
  const out = [], seen = new Set();
  for (const p of list || []) {
    const at = p && Date.parse(p.at), text = p && typeof p.text === 'string' ? p.text.trim() : '';
    const why = !p || !/^[a-z0-9][a-z0-9-]{2,63}$/.test(p.id || '') ? 'id' : seen.has(p.id) ? 'duplicate id' : !Number.isFinite(at) ? 'at' : !text ? 'text'
      : text.length > (p.photo ? 1024 : 4096) ? 'too long' : badHtml(text) ? 'html: ' + badHtml(text)
      : p.photo && !/^(posts\/)?[\w-][\w.-]*\.(jpg|png|webp)$/.test(p.photo) ? 'photo (a file in public/ or public/posts/)' : '';
    if (why) { if (ctx && ctx.log && ctx.cache.postsBad !== (p && p.id) + why) { ctx.cache.postsBad = (p && p.id) + why; ctx.log('posts: skipped a bad entry', p && p.id, why); } continue; }
    seen.add(p.id);
    out.push({ id: p.id, at, text, photo: p.photo || '', button: p.button === false ? '' : cut(String(p.button || '▶️ Play Office Dreams'), 40) });
  }
  return out.sort((a, b) => a.at - b.at);
}
const esc = (s) => String(s).replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
const sentRow = (ctx, id) => ctx.db.get('SELECT * FROM posts_sent WHERE id=?', id);
function appLink(ctx) {
  const c = ctx.cfg;
  return c.botUsername ? `https://t.me/${c.botUsername}${c.appShort ? '/' + c.appShort : ''}` : '';
}
async function publish(ctx, p) {
  const link = appLink(ctx);
  // a channel cannot open a mini app button (web_app works only in private chats): a plain t.me link does
  const markup = p.button && link ? { inline_keyboard: [[{ text: p.button, url: link }]] } : undefined;
  if (p.photo && /^https:/.test(ctx.cfg.publicUrl)) {
    return ctx.tg.call('sendPhoto', { chat_id: ctx.cfg.channel, photo: ctx.cfg.publicUrl + '/' + p.photo, caption: p.text, parse_mode: 'HTML', reply_markup: markup });
  }
  return ctx.tg.call('sendMessage', { chat_id: ctx.cfg.channel, text: p.text, parse_mode: 'HTML', link_preview_options: { is_disabled: true }, reply_markup: markup });
}
// can the bot publish in the channel? It must be an admin there with "Post messages"
async function canPost(ctx) {
  if (!ctx.cfg.channel) return false;
  const me = await ctx.tg.call('getMe', {}).catch(() => null);
  const r = me && me.ok ? await ctx.tg.call('getChatMember', { chat_id: ctx.cfg.channel, user_id: me.result.id }).catch(() => null) : null;
  const m = r && r.ok && r.result;
  return !!m && (m.status === 'creator' || (m.status === 'administrator' && m.can_post_messages !== false));
}
let busy = false;
async function tick(ctx, now = Date.now(), list = load(ctx)) {
  if (!ctx.cfg.channel || busy) return null;
  busy = true;
  try {
    init(ctx);
    if (!ctx.cache.postsProbe) { // once per start: say in the log whether posts can go out at all
      ctx.cache.postsProbe = 1;
      const ok = await canPost(ctx);
      ctx.log && ctx.log('posts: the bot', ok ? 'can post in' : 'CANNOT post in (make it an admin of the channel with "Post messages")', ctx.cfg.channel, '·', list.filter((p) => p.at > now).length, 'posts queued');
    }
    for (const p of list) {
      if (p.at > now) break;
      const row = sentRow(ctx, p.id);
      if (row && row.status !== 'retry') continue;
      if (now - p.at > LATE_MS) { // missed its moment: drop it quietly instead of posting stale news
        ctx.db.run("INSERT INTO posts_sent(id,at,status) VALUES(?,?,'late') ON CONFLICT(id) DO UPDATE SET status='late', at=excluded.at", p.id, now);
        ctx.log && ctx.log('posts: dropped (too late)', p.id);
        continue;
      }
      if (row && now - row.at < RETRY_MS * 2 ** (row.tries - 1)) continue; // a refused one waits 10, 20, 40… min
      const last = ctx.db.get("SELECT MAX(at) AS t FROM posts_sent WHERE status='sent'");
      if (last && last.t && now - last.t < GAP_MS) return null;
      const tries = (row ? row.tries : 0) + 1;
      ctx.db.run("INSERT INTO posts_sent(id,at,status,tries) VALUES(?,?,'sending',?) ON CONFLICT(id) DO UPDATE SET status='sending', at=excluded.at, tries=excluded.tries", p.id, now, tries);
      let r;
      try { r = await publish(ctx, p); } catch (e) { r = { ok: false, description: String(e && e.message) }; }
      if (r && r.ok) {
        ctx.db.run("UPDATE posts_sent SET status='sent', msg_id=?, err=NULL WHERE id=?", (r.result && r.result.message_id) || null, p.id);
        ctx.log && ctx.log('posts: published', p.id);
        return p.id;
      }
      // a timeout may still have reached the channel: never repeated (no double post), the owner checks
      const unknown = !!(r && r.network && /abort/i.test(r.description || ''));
      const st = unknown ? 'unknown' : tries >= TRIES ? 'failed' : 'retry', err = String((r && r.description) || 'no response').slice(0, 300);
      ctx.db.run("UPDATE posts_sent SET status=?, err=? WHERE id=? AND status='sending'", st, err, p.id); // a /postskip meanwhile wins
      ctx.log && ctx.log('posts: telegram refused', p.id, st, err);
      if (st !== 'retry' || tries === 1) await tellOwner(ctx, st === 'unknown' ? `❓ Пост <code>${esc(p.id)}</code>: Telegram не ответил вовремя. Проверь канал, вышел ли он; повторять не буду, чтобы не было дубля.`
        : st === 'failed' ? `❌ Пост <code>${esc(p.id)}</code> так и не ушёл в канал: ${esc(err)}` : `⚠️ Пост <code>${esc(p.id)}</code> не ушёл в канал: ${esc(err)}\nПопробую ещё через 10 минут.`);
      return null;
    }
    return null;
  } finally { busy = false; }
}
/* ---- the owner, in the bot ---- */
function tellOwner(ctx, text) {
  return Promise.all([...new Set([...(ctx.cfg.adminTgIds || []), ...(ctx.cfg.devIds || [])])].map((id) => ctx.tg.call('sendMessage', { chat_id: id, text, parse_mode: 'HTML' }).catch(() => null)));
}
// the owner lives in Kyiv: times in Kyiv time (DST included), UTC if the runtime has no time zone data
const when = (t) => { try { return new Date(t).toLocaleString('ru-RU', { timeZone: 'Europe/Kyiv', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) + ' Киев'; } catch (e) { return new Date(t).toISOString().slice(5, 16).replace('T', ' ') + ' UTC'; } };
const plain = (s, n) => cut(s.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim(), n, '…');
function ownerReport(ctx, now = Date.now(), list = load(ctx)) {
  init(ctx);
  const next = list.filter((p) => { const r = sentRow(ctx, p.id); return !r || r.status === 'retry'; }).filter((p) => now - p.at <= LATE_MS).slice(0, 6);
  const done = ctx.db.all("SELECT id, at, status, err FROM posts_sent ORDER BY at DESC LIMIT 4");
  const lines = ['📢 <b>Посты в канал</b> ' + esc(ctx.cfg.channel || '(CHANNEL не задан)'), ''];
  lines.push(next.length ? '<b>Скоро:</b>' : 'В очереди пусто.');
  for (const p of next) lines.push(`• ${when(p.at)} <code>${esc(p.id)}</code>${p.photo ? ' 🖼' : ''}\n  ${esc(plain(p.text, 110))}`);
  if (done.length) { lines.push('', '<b>Последние:</b>'); for (const d of done) lines.push(`• ${when(d.at)} <code>${esc(d.id)}</code> — ${{ sent: '✅', late: '⏭ опоздал', skipped: '🚫 отменён', failed: '⚠️ ошибка', retry: '🔁 повтор', sending: '⏳ отправляется', unknown: '❓ не знаю, вышел ли — проверь канал' }[d.status] || esc(d.status)}${d.err ? ' ' + esc(plain(d.err, 80)) : ''}`); }
  lines.push('', 'Отменить пост: /postskip id');
  return lines.join('\n');
}
// 'ok' | 'sent' (too late: it is out or going out right now) | 'unknown' (no such post in the queue)
function skip(ctx, id, now = Date.now(), list = load(ctx)) {
  init(ctx);
  if (!list.some((p) => p.id === id)) return 'unknown';
  const row = sentRow(ctx, id);
  if (row && (row.status === 'sent' || row.status === 'sending' || row.status === 'unknown')) return 'sent';
  ctx.db.run("INSERT INTO posts_sent(id,at,status) VALUES(?,?,'skipped') ON CONFLICT(id) DO UPDATE SET status='skipped', at=excluded.at", id, now);
  return 'ok';
}

module.exports = { tick, load, check, badHtml, ownerReport, skip, canPost, LATE_MS, GAP_MS };
