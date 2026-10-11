/* ============================================================
   OFFICE DREAMS — i18n
   L('key', {param}) → text in the current language.
   Params that are strings starting with '$' are keys themselves
   (the engine stores '$role.prog.name' so the log is language-free).
   ============================================================ */
const LANGS = {};
const LANG_LIST = ['en', 'ru', 'uk'];
let LANG = 'en';

function setLang(code) {
  LANG = LANG_LIST.includes(code) ? code : 'en';
  if (typeof document !== 'undefined' && document.documentElement) document.documentElement.lang = LANG;
  return LANG;
}
function detectLang(tgCode) {
  const c = String(tgCode || (typeof navigator !== 'undefined' && navigator.language) || 'en').toLowerCase();
  if (c.startsWith('uk')) return 'uk';
  if (c.startsWith('ru') || c.startsWith('be') || c.startsWith('kk')) return 'ru';
  return 'en';
}
function hasKey(k) {
  return (LANGS[LANG] && LANGS[LANG][k] !== undefined) || (LANGS.en && LANGS.en[k] !== undefined);
}
function L(k, p) {
  let t = LANGS[LANG] && LANGS[LANG][k];
  if (t === undefined) t = LANGS.en && LANGS.en[k];
  if (t === undefined) return k;
  if (!p) return t;
  return t.replace(/\{(\w+)\}/g, (m, name) => {
    let v = p[name];
    if (v === undefined || v === null) return '';
    if (typeof v === 'string' && /^\$[a-z]/.test(v)) v = L(v.slice(1));
    return String(v);
  });
}

if (typeof module !== 'undefined') module.exports = { LANGS, LANG_LIST, setLang, detectLang, L, hasKey, getLang: () => LANG };
