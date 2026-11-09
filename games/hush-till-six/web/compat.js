/* Hush Till Six — совместимость: ТВ-приставки (Android TV, в т.ч. 32-битные) и старые браузеры. Грузится ПЕРВЫМ, написан на старом JS (ES5),
   чтобы его прочитал любой браузер. Новым браузерам на ПК и телефоне почти ничего не делает.
   1) ТВ: window.nsTV, класс .tv на <html>; первый запуск — графика «Ретро» (слабая приставка тянет её ровно; «Кино» можно включить в настройках).
      Пульт (стрелки, ОК, «Назад», ▶︎‖) — в pad.js. ?tv=1 / ?tv=0 — включить/выключить вручную (запоминается).
   2) Старый браузер не может прочитать скрипт игры (SyntaxError: `??`, `catch {}` — WebView/Chrome < 80 на приставках) — сразу после
      него подгружается копия из legacy/ (ES2017, собирает tools/tv/legacy.js). window.nsLegacy — сколько скриптов подменено.
   3) Мелкие полифилы (flatMap, flat, Object.fromEntries, globalThis …).
   4) CSS, которого нет в старых браузерах: единица интерфейса --u (cqw/cqh) и пропорции кадра (aspect-ratio) — считаются здесь в px;
      inset — переписывается в top/right/bottom/left прямо в <style>. */
(function () {
  'use strict';
  var W = window, D = document, UA = navigator.userAgent || '', Q = location.search;
  var mm = function (q) { try { return !!(W.matchMedia && W.matchMedia(q).matches); } catch (e) { return false; } };
  var ls = function (k, v) { try { if (arguments.length > 1) localStorage.setItem(k, v); else return localStorage.getItem(k); } catch (e) { return null; } };

  /* ---------- 1. ТВ ---------- */
  var force = /[?&]tv=([01])\b/.exec(Q);
  if (force) ls('ns1_tv', force[1]);
  var saved = ls('ns1_tv');
  var touch = (navigator.maxTouchPoints || 0) > 0 || 'ontouchstart' in W;
  var tvUA = /Android ?TV|Google ?TV|\bAFT[A-Z0-9]|BRAVIA|SMART-?TV|SmartTV|HbbTV|Tizen|Web0S|webOS|NetCast|MiTV|MIBOX|\bTV\b/i.test(UA);
  // Android без сенсора и без мыши — приставка с пультом (у телефона и планшета указатель «coarse», у ПК — «fine»)
  var tvGuess = /Android/i.test(UA) && !touch && !mm('(any-pointer: coarse)') && !mm('(any-pointer: fine)');
  var TV = saved === '1' ? true : saved === '0' ? false : (tvUA || tvGuess);
  W.nsTV = TV;
  if (TV) {
    D.documentElement.className += ' tv';
    if (ls('ns1_gfx') == null) { ls('ns1_gfx', '"retro"'); ls('ns1_tvGfx', '1'); }   // сам игрок ещё не выбирал — «Ретро»
  }

  /* ---------- 2. подмена скриптов для старых браузеров ---------- */
  var me = D.currentScript || (function () { var s = D.getElementsByTagName('script'); return s[s.length - 1]; })();
  var BASE = String(me && me.src || '').replace(/[^\/]*$/, '');   // …/games/nightshift/ или https://yompix.github.io/
  var done = {};
  W.nsLegacy = 0;
  function legacy(rel, q) {
    if (done[rel]) return; done[rel] = 1; W.nsLegacy++;
    var src = BASE + 'legacy/' + rel + (q ? '?' + q : '');   // тот же ?v=, что у оригинала: новая версия игры — не старая копия из кэша
    if (D.readyState === 'loading') D.write('<script src="' + src + '"><\/script>');   // сразу после упавшего тега: порядок скриптов сохраняется
    else { var s = D.createElement('script'); s.src = src; (D.head || D.documentElement).appendChild(s); }   // подгружено позже (qa.js)
  }
  W.addEventListener('error', function (e) {
    var f = String(e.filename || ''), msg = String(e.message || ''), err = e.error;
    if (!BASE || f.indexOf(BASE) !== 0) return;
    var tail = f.slice(BASE.length).split('#')[0], rel = tail.split('?')[0], q = tail.split('?')[1] || '';
    if (!rel || /^legacy\//.test(rel) || !/\.js$/.test(rel)) return;
    if (!((err && err.name === 'SyntaxError') || /SyntaxError|Unexpected token|Unexpected identifier|missing \)|expected expression/i.test(msg))) return;
    if (e.preventDefault) e.preventDefault();
    legacy(rel, q);
  }, true);

  /* ---------- 3. полифилы ---------- */
  var def = function (o, k, v) { if (!o[k]) try { Object.defineProperty(o, k, { value: v, configurable: true, writable: true }); } catch (e) { o[k] = v; } };
  def(Array.prototype, 'flat', function (d) {
    d = d === undefined ? 1 : Math.floor(d); var out = [];
    (function rec(a, n) { for (var i = 0; i < a.length; i++) { if (!(i in a)) continue; if (Array.isArray(a[i]) && n > 0) rec(a[i], n - 1); else out.push(a[i]); } })(this, d);
    return out;
  });
  def(Array.prototype, 'flatMap', function (fn, t) { return Array.prototype.map.call(this, fn, t).flat(1); });
  def(Object, 'fromEntries', function (it) { var o = {}; Array.from(it).forEach(function (p) { o[p[0]] = p[1]; }); return o; });
  def(String.prototype, 'replaceAll', function (a, b) { return a instanceof RegExp ? this.replace(a, b) : this.split(a).join(typeof b === 'function' ? b(a) : b); });
  if (typeof globalThis === 'undefined') W.globalThis = W;
  if (W.Promise && !Promise.allSettled) Promise.allSettled = function (ps) { return Promise.all(Array.from(ps).map(function (p) { return Promise.resolve(p).then(function (v) { return { status: 'fulfilled', value: v }; }, function (r) { return { status: 'rejected', reason: r }; }); })); };
  if (!W.queueMicrotask) W.queueMicrotask = function (fn) { Promise.resolve().then(fn); };
  if (W.Element) {
    def(Element.prototype, 'after', function () { var p = this.parentNode, n = this.nextSibling; if (!p) return; for (var i = 0; i < arguments.length; i++) { var x = arguments[i]; p.insertBefore(typeof x === 'string' ? D.createTextNode(x) : x, n); } });
    def(Element.prototype, 'toggleAttribute', function (n, f) { var on = f === undefined ? !this.hasAttribute(n) : !!f; if (on) this.setAttribute(n, ''); else this.removeAttribute(n); return on; });
  }

  /* ---------- 4. CSS старых браузеров ---------- */
  var sup = function (p, v) { try { return !!(W.CSS && CSS.supports && CSS.supports(p, v)); } catch (e) { return false; } };
  if (!sup('inset', '0')) {   // inset: a b c d → top/right/bottom/left, на том же месте в стилях (порядок правил не меняется)
    var ex = function (m, a, v, imp) { var p = v.trim().split(/\s+/), I = imp ? ' !important' : '', t = p[0], r = p[1] || t, b = p[2] || t, l = p[3] || r;
      return a + 'top:' + t + I + ';right:' + r + I + ';bottom:' + b + I + ';left:' + l + I; };
    var st = D.getElementsByTagName('style');
    for (var i = 0; i < st.length; i++) { var tx = st[i].textContent; if (/inset\s*:/.test(tx)) st[i].textContent = tx.replace(/([;{\s])inset\s*:\s*([^;}!]+?)\s*(!important)?(?=\s*[;}])/g, ex); }
  }
  var noCQ = !sup('width', '1cqw'), noAR = !sup('aspect-ratio', '1 / 1');
  if (noCQ || noAR) {
    var fit = function () {
      var s = D.getElementById('stage'); if (!s || !D.body) return;
      var land = /\bland\b/.test(D.body.className), ar = land ? 16 / 9 : 9 / 16, vw = W.innerWidth, vh = W.innerHeight, w, h;
      if (vw / vh > ar) { h = vh; w = vh * ar; } else { w = vw; h = vw / ar; }
      s.style.width = w + 'px'; s.style.height = h + 'px';
      if (noCQ) s.style.setProperty('--u', (land ? h * .0068 : w * .01) + 'px');
      if (noAR) { var c = D.querySelectorAll('.camgrid .cam'); for (var j = 0; j < c.length; j++) c[j].style.height = c[j].offsetWidth * (land ? 4 / 3 : 12 / 9) + 'px'; }
    };
    W.addEventListener('resize', fit);
    if (W.MutationObserver) D.addEventListener('DOMContentLoaded', function () { new MutationObserver(fit).observe(D.body, { attributes: true, attributeFilter: ['class'] }); fit(); });
    setInterval(fit, 400);   // камеры смены появляются и пропадают — пропорции досчитать
    fit();
  }
})();
