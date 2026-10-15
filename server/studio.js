'use strict';
/* Studio names: every player may name their studio; a name belongs to one player only.
 * "Ola", "OLA" and "O-la" are the same name (compared by letters and digits, case-insensitive),
 * and look-alike Latin/Cyrillic letters are folded so "Оla" (Cyrillic O) cannot copy "Ola".
 */
const FOLD = { а: 'a', в: 'b', е: 'e', ё: 'e', к: 'k', м: 'm', н: 'h', о: 'o', р: 'p', с: 'c', т: 't', у: 'y', х: 'x', і: 'i', ї: 'i', 0: 'o', 1: 'i', 3: 'e' };
const RESERVED = ['officedream', 'officedreams', 'admin', 'administrator', 'support', 'moderator', 'telegram', 'bot', 'system'];
const BAD = ['хуй', 'хуе', 'хуё', 'пизд', 'ебат', 'ебан', 'ебал', 'еблан', 'блять', 'бляд', 'сука', 'муда', 'пидор', 'пидар', 'залуп', 'fuck', 'shit', 'cunt', 'nigg', 'faggot', 'whore', 'porn', 'hitler', 'nazi'];

// control characters, zero-width and direction marks (people use them to fake a "different" name)
const INVISIBLE = new RegExp('[\\u0000-\\u001f\\u007f\\u00ad\\u200b-\\u200f\\u2028-\\u202e\\u2060-\\u206f\\ufeff]', 'g');
function clean(raw) {
  return String(raw || '').normalize('NFC').replace(INVISIBLE, '').replace(/\s+/g, ' ').trim();
}
function keyOf(name) {
  const low = clean(name).toLowerCase();
  let k = '';
  for (const ch of low) {
    if (!/[\p{L}\p{N}]/u.test(ch)) continue;
    k += FOLD[ch] || ch;
  }
  return k;
}
function check(raw) {
  const name = clean(raw);
  if (name.length < 3 || name.length > 20) return { ok: false, err: 'len' };
  if (!/^[\p{L}\p{N} .'&!?_\-]+$/u.test(name)) return { ok: false, err: 'chars' };
  const key = keyOf(name);
  if (key.length < 3) return { ok: false, err: 'len' };
  if (RESERVED.some((r) => key === r || key.startsWith('officedream'))) return { ok: false, err: 'taken' };
  const plain = name.toLowerCase().replace(/[^\p{L}]/gu, '');
  if (BAD.some((w) => plain.includes(w) || key.includes(w))) return { ok: false, err: 'bad' };
  return { ok: true, name, key };
}
function setStudio(ctx, uid, raw) {
  const c = check(raw);
  if (!c.ok) return c;
  const other = ctx.db.get('SELECT id FROM users WHERE studio_key=? AND id<>?', c.key, uid);
  if (other) return { ok: false, err: 'taken' };
  try {
    ctx.db.run('UPDATE users SET studio=?, studio_key=? WHERE id=?', c.name, c.key, uid);
  } catch (e) {
    return { ok: false, err: 'taken' }; // the unique index caught a race
  }
  ctx.cache.lb = null;
  return { ok: true, studio: c.name };
}
function isFree(ctx, uid, raw) {
  const c = check(raw);
  if (!c.ok) return c;
  const other = ctx.db.get('SELECT id FROM users WHERE studio_key=? AND id<>?', c.key, uid);
  return other ? { ok: false, err: 'taken' } : { ok: true, name: c.name };
}
// the name other players see: the studio when it has one, otherwise the Telegram name —
// but a Telegram name that pretends to be staff ("Admin", "Адмін", "Support"…) is shown as "Player #1234"
const STAFF = ['admin', 'адмін', 'админ', 'adмин', 'moder', 'модер', 'support', 'поддерж', 'підтрим', 'officedream', 'officedreams', 'administrator', 'owner', 'creator', 'создатель', 'творець'];
function fakeStaff(name) { const k = keyOf(name); return STAFF.some((w) => k.includes(keyOf(w))); }
const shown = (u) => {
  if (!u) return 'Player';
  if (u.studio) return u.studio;
  const id = u.id || u.uid || 0;
  if (!u.name || fakeStaff(u.name)) return 'Player #' + String(id).slice(-4);
  return u.name;
};

module.exports = { clean, keyOf, check, setStudio, isFree, shown, fakeStaff };
