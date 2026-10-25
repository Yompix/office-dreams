/* «Молчи до шести» (Hush Till Six) — геймпад (Steam Deck, Xbox, PlayStation) и мелочи ПК. Подключается после engine.js.
   В движок не лезет: жмёт те же кнопки и клавиши, что игрок (из __ns берёт только режим и паузу).
   A — нажать кнопку под рамкой, а если кнопок нет — дальше (как пробел); B — назад (Esc); Start — пауза; крестовина и левый стик — рамка по кнопкам
   верхнего слоя (пауза, меню, выбор, обход, мини-игра, смена); LB/RB — камеры в смене (13-ю — только нарочно, рамкой); X — «Записать», Y — «Пульт» (вне смены — «реплики сами»),
   View — «Журнал» в смене, иначе записка «Правила»; правый стик — оглядеться (как мышью). Удержание («НЕ ДЫШИ», «ДЕРЖИ ФРЭНКА», «ДЕРЖИ») — держать A.
   Мини-игры: бег — влево/вправо, шнур — A или вниз, перемотка записи — влево/вправо, стекло и фонарь — стиком водишь «палец».
   Клавиатура — как пульт: стрелки/WASD — рамка, Enter/пробел — A, Esc/Backspace — B (и для Steam Input, если он шлёт клавиши вместо кнопок).
   Пульт с нестандартной раскладкой (Linux, Steam Deck) переводится в стандартную — std(). Проверка: tools/desktop/pad_test.js.
   Мышь сдвинулась — рамка прячется. Курсор сам прячется в кат-сценах. ПК-версия: окно ушло в фон — пауза (если «Звук в фоне» выключен). */
(() => {
'use strict';
const $ = id => document.getElementById(id), B = document.body, NS = () => window.__ns || {}, DESK = window.nsDesktop || null;
const IN_GAME = ['play', 'choice', 'mg', 'cams', 'shift', 'explore', 'talk'];
const FOC = 'button, a[href], input[type=range], .camgrid .cam, .cord .plug';
const HOLD = '#hBtn, .bigbtn';   // кнопки, которые держат (слушают pointerdown/pointerup)
const vis = el => !!el && el.isConnected && !el.closest('.hide') && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
const tnow = () => performance.now();
const key = k => document.dispatchEvent(new KeyboardEvent('keydown', { key: k, code: k, bubbles: true, cancelable: true }));
const ptr = (el, type, x, y, down) => el.dispatchEvent(new PointerEvent(type, { bubbles: true, cancelable: true, composed: true, pointerId: 31, pointerType: 'touch', isPrimary: true, clientX: x, clientY: y, button: 0, buttons: down ? 1 : 0 }));
const mid = el => { const r = el.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; };

/* ---------- верхний слой: где сейчас кнопки ---------- */
function layer() {
  const pz = $('pz'), note = $('note'), ui = $('ui'), pop = $('shPop');
  if (vis(pz) && pz.children.length) return pz;
  if (vis(note)) return note;
  if (ui && ui.children.length && vis(ui.firstElementChild)) return ui;
  if (vis(pop)) return pop;
  for (const id of ['choice', 'mg', 'act']) { const el = $(id); if (vis(el) && el.children.length) return el; }
  return null;
}
const items = root => root ? [...root.querySelectorAll(FOC)].filter(el => !el.disabled && vis(el)) : [];
function defItem(list) {   // куда встаёт рамка в новом слое
  const pick = s => list.find(el => el.matches(s));
  return pick('#pzGo') || pick('#oRetry') || pick('#hBtn') || pick('.bigbtn') || pick('.plug') || pick('.sh-cams button.on') || list.find(el => !el.matches('.sx, #tutSkip')) || list[0] || null;
}
function reveal(el) {   // кнопка за краем прокрутки (настройки, журнал ночи) — докрутить; сам кадр (#stage) не двигаем
  for (let p = el.parentElement; p && p.id !== 'stage'; p = p.parentElement) {
    const oy = getComputedStyle(p).overflowY; if ((oy !== 'auto' && oy !== 'scroll') || p.scrollHeight <= p.clientHeight) continue;
    const a = p.getBoundingClientRect(), r = el.getBoundingClientRect(), m = 12;
    if (r.top < a.top + m) p.scrollTop -= a.top + m - r.top; else if (r.bottom > a.bottom - m) p.scrollTop += r.bottom - a.bottom + m;
  }
}
let cur = null, curRoot = null, armT = 0;
function setCur(el) {
  if (cur && cur !== el) cur.classList.remove('pfocus');
  cur = el; if (!el) return;
  el.classList.add('pfocus'); reveal(el);
  const a = document.activeElement; if (a && a !== el && a !== B && !a.matches('input, textarea')) a.blur();   // фокус клавиатуры (движок ставит его на «Попробовать снова») не светится второй рамкой
}
function sync() {   // слой сменился или кнопка исчезла — рамку на кнопку по умолчанию (A не жмёт её сразу: только что появилась)
  const root = layer(), list = items(root);
  if (root !== curRoot) { curRoot = root; touched = false; armT = tnow() + (root && /^(choice|act|shPop)$/.test(root.id) ? 450 : 320); setCur(defItem(list)); return list; }   // выбор и окна смены — чуть дольше: решение, а не повтор
  if (cur && !list.includes(cur)) { armT = Math.max(armT, tnow() + 200); setCur(defItem(list)); }
  else if (!cur && list.length) setCur(defItem(list));
  return list;
}
// рамка — к ближайшей кнопке в сторону нажатия
function move(list, dx, dy) {
  if (!cur) return setCur(list[0] || null);
  const [ax, ay] = mid(cur); let best = null, bs = Infinity;
  for (const el of list) { if (el === cur) continue; const [x, y] = mid(el), mx = (x - ax) * dx + (y - ay) * dy, side = dx ? Math.abs(y - ay) : Math.abs(x - ax);
    if (mx <= 2) continue; const s = mx + side * 2.2; if (s < bs) { bs = s; best = el; } }
  if (best) setCur(best);
}
function slide(inp, d) {   // ползунок громкости: влево/вправо — значение, проба звука — когда отпустил
  const st = +inp.step || 1, v = Math.max(+inp.min || 0, Math.min(+inp.max || 100, +inp.value + d * st));
  if (v === +inp.value) return; inp.value = v; inp.dispatchEvent(new Event('input', { bubbles: true }));
  clearTimeout(inp._padT); inp._padT = setTimeout(() => inp.dispatchEvent(new Event('change', { bubbles: true })), 350);
}

/* ---------- состояние геймпада (стандартная раскладка: Xbox, PlayStation, Steam Deck через Steam Input) ---------- */
const BTN = { A: 0, B: 1, X: 2, Y: 3, LB: 4, RB: 5, VIEW: 8, START: 9, UP: 12, DOWN: 13, LEFT: 14, RIGHT: 15 };
let prev = [], on = false;
const rep = { k: '', t: 0, n: 0 };   // повтор направления, пока держишь
// Нестандартная раскладка (mapping ''): так браузер отдаёт пульт, которого не знает, — в Linux (Steam Deck, виртуальный пульт Steam)
// кнопки и оси идут в порядке драйвера xpad: A B X Y LB RB Back Start Guide LS RS, оси LX LY LT RX RY RT, крестовина — оси 6/7;
// в Windows у DirectInput-пультов крестовина — ось 9 (−1…1 по кругу, больше 1 — не нажата). Переводим в стандартную, иначе
// A/B/Start и крестовина не работали, а курок LT (ось 2, в покое −1) уводил взгляд в сторону.
const hat = {};
function std(p) {
  const raw = p.buttons.map(x => !!x && (x.pressed || x.value > .5)), a = p.axes;
  if (p.mapping === 'standard') return { b: raw, ax: a.slice(0, 4) };
  const b = raw.slice(0, 6); b[8] = raw[6]; b[9] = raw[7]; b[10] = raw[9]; b[11] = raw[10];
  let hx = 0, hy = 0;
  if (a.length > 9 && Math.abs(a[9]) > 1.05) hat[p.index] = 1;   // ось 9 — крестовина, только если видели её «не нажата» (>1); иначе покой 0 читался бы как «вниз»
  if (hat[p.index] && Math.abs(a[9]) <= 1.05) { const i = Math.round((a[9] + 1) * 3.5) % 8; hx = [0, 1, 1, 1, 0, -1, -1, -1][i]; hy = [-1, -1, 0, 1, 1, 1, 0, -1][i]; }
  else if (a.length >= 8) { hx = Math.abs(a[6]) > .5 ? Math.sign(a[6]) : 0; hy = Math.abs(a[7]) > .5 ? Math.sign(a[7]) : 0; }
  b[12] = hy < 0; b[13] = hy > 0; b[14] = hx < 0; b[15] = hx > 0;
  return { b, ax: a.length >= 5 ? [a[0], a[1], a[3], a[4]] : a.slice(0, 4) };
}
// клавиатура как пульт: стрелки/WASD — крестовина, Enter/пробел — A, Esc/Backspace — B. Нужна и без пульта, и на Steam Deck,
// когда Steam Input шлёт игре клавиши вместо кнопок (шаблоны «Клавиатура», «Веб-браузер»): раньше меню на клавиши не отвечало
const KB = { ArrowUp: 12, ArrowDown: 13, ArrowLeft: 14, ArrowRight: 15, KeyW: 12, KeyS: 13, KeyA: 14, KeyD: 15, Enter: 0, NumpadEnter: 0, Space: 0, Escape: 1, Backspace: 1 };
const kb = [], kbL = []; let kbUsed = false, touched = false;   // kbL — нажатие держится до кадра: короткое касание клавиши (Steam Input) не теряется
addEventListener('keydown', e => {
  const i = KB[e.code] ?? KB[e.key]; if (!e.isTrusted || i == null || e.ctrlKey || e.altKey || e.metaKey) return;
  if (e.code === 'Space' || e.key === ' ') { if (!kbUsed && !layer()) return; }   // пробел в кат-сцене без кнопок — движку как было (листает реплики)
  e.preventDefault(); e.stopImmediatePropagation(); kbUsed = true;
  if (!e.repeat) kb[i] = kbL[i] = true;
}, true);
addEventListener('keyup', e => { const i = KB[e.code] ?? KB[e.key]; if (e.isTrusted && i != null) { kb[i] = false; if (kbUsed) { e.preventDefault(); e.stopImmediatePropagation(); } } }, true);
addEventListener('blur', () => kb.fill(false));
function read() {
  const pads = navigator.getGamepads ? [...navigator.getGamepads()].filter(p => p && p.connected !== false) : [];
  if (!pads.length && !kbUsed) return null;
  const b = kb.map((x, i) => x || kbL[i]), ax = [0, 0, 0, 0], kbA = !!b[0]; kbL.length = 0;
  for (const p of pads) { const s = std(p); s.b.forEach((x, i) => { b[i] = b[i] || !!x; }); s.ax.forEach((v, i) => { if (Math.abs(v || 0) > Math.abs(ax[i])) ax[i] = v || 0; }); }
  return { b, ax, kbA: kbA && !pads.some(p => p.buttons[0] && p.buttons[0].pressed) };
}
const dz = (x, y, d = .22) => { const m = Math.hypot(x, y); return m < d ? [0, 0] : [x * (m - d) / (1 - d) / m, y * (m - d) / (1 - d) / m]; };
function padOn() { if (!on) { on = true; B.classList.add('pad'); syncT = 0; } }
function padOff() { if (on) { on = false; B.classList.remove('pad'); } }
addEventListener('mousemove', e => { if (!e.isTrusted || Math.abs(e.movementX) + Math.abs(e.movementY) < 3) return; padOff(); curShow(); }, true);
addEventListener('pointerdown', e => { if (e.isTrusted && e.pointerType !== 'touch') { padOff(); curShow(); } }, true);
addEventListener('gamepaddisconnected', () => { const n = NS(); if (IN_GAME.includes(n.mode) && !n.userPause && $('bMenu')) $('bMenu').click(); });   // геймпад отключился посреди игры — пауза

/* ---------- мини-игры, где кнопкой не обойтись ---------- */
let pull = null, tape = null, vp = null, vpH = null, vDown = false;
function pullCord(plug) {   // шнур: вилку вниз одним движением
  if (pull) return; const box = plug.closest('.cord'), [x, y] = mid(plug), lim = (box ? box.clientHeight : 300) * .52, t0 = tnow();
  ptr(plug, 'pointerdown', x, y, 1);
  pull = () => { const k = Math.min(1, (tnow() - t0) / 260); ptr(plug, 'pointermove', x, y + lim * k, 1); if (k >= 1 || !plug.isConnected) { ptr(plug, 'pointerup', x, y + lim * k, 0); pull = null; } };
}
function tapeStep(box, dir, dt) {   // перемотка: держишь влево/вправо — ползунок едет
  const r = box.getBoundingClientRect(), y = r.top + r.height / 2;
  if (!dir) { if (tape) { ptr(box, 'pointerup', r.left + tape.k * r.width, y, 0); tape = null; } return; }
  if (!tape || tape.box !== box) { const kn = box.querySelector('.tknob'), k0 = kn ? parseFloat(kn.style.left) / 100 : 1; tape = { box, k: isNaN(k0) ? 1 : k0 }; ptr(box, 'pointerdown', r.left + tape.k * r.width, y, 1); }
  tape.k = Math.max(0, Math.min(1, tape.k + dir * dt * .5)); ptr(box, 'pointermove', r.left + tape.k * r.width, y, 1);
}
const pointerMode = mg => vis(mg) && typeof mg.onpointerdown === 'function' && !items(mg).length;   // стекло, фонарь: «палец» водит стик
function vpStep(mg, vx, vy, hold, dt) {
  const st = $('stage').getBoundingClientRect();
  if (mg.onpointerdown !== vpH) { vpH = mg.onpointerdown; vDown = false; vp = { x: st.left + st.width / 2, y: st.top + st.height / 2 }; }
  const moving = Math.hypot(vx, vy) > 0, sp = st.height * 1.1;
  if (moving) { vp.x = Math.max(st.left + 2, Math.min(st.right - 2, vp.x + vx * sp * dt)); vp.y = Math.max(st.top + 2, Math.min(st.bottom - 2, vp.y + vy * sp * dt)); }
  if ((moving || hold) && !vDown) { vDown = true; ptr(mg, 'pointerdown', vp.x, vp.y, 1); }
  else if (vDown && moving) ptr(mg, 'pointermove', vp.x, vp.y, 1);
  else if (vDown && !moving && !hold) { vDown = false; ptr(mg, 'pointerup', vp.x, vp.y, 0); }
  curDot(vp.x - st.left, vp.y - st.top);
}
let dot = null;
function curDot(x, y) {
  if (!dot) { if (x == null) return; dot = document.createElement('div'); dot.id = 'padCur'; $('stage').appendChild(dot); }
  if (x == null) { dot.classList.remove('on'); return; }
  dot.style.left = x + 'px'; dot.style.top = y + 'px'; dot.classList.add('on');
}
// правый стик — оглядеться: движок ведёт взгляд за мышью (pointermove на окне), даём ему «мышь» по отклонению стика
let lookOn = false;
function look(x, y) {
  const m = Math.hypot(x, y); if (m < .01 && !lookOn) return; lookOn = m >= .01;
  const r = $('stage').getBoundingClientRect();
  dispatchEvent(new PointerEvent('pointermove', { pointerType: 'mouse', clientX: r.left + r.width * (.5 + x * .5), clientY: r.top + r.height * (.5 + y * .5) }));
}

/* ---------- кнопки ---------- */
let holdEl = null, list = [], syncT = 0;
function pressA(fromKb) {
  list = sync(); const root = curRoot;
  if (tnow() < armT && root) return;   // только что появились кнопки — A от прошлого нажатия их не выбирает
  if (fromKb && root && /^(choice|act)$/.test(root.id) && !touched) { touched = true; return; }   // клавиатура: выбор по Enter/пробелу — только вторым нажатием (листал реплики пробелом — не выбрал случайно)
  if (cur && list.includes(cur)) {
    if (cur.matches(HOLD)) return;   // удержание начинает главный цикл: A зажата, кнопка под рамкой
    if (cur.matches('.plug')) return pullCord(cur);
    if (cur.matches('input')) return;
    return cur.click();
  }
  if (root) { const s = [root, root.firstElementChild].find(el => el && typeof el.onclick === 'function'); if (s) return s.click(); }   // заставка, финал, записка
  key('Enter');   // дальше: реплика, кадр, «нажми, чтобы начать»
}
function releaseA() { if (holdEl) { const [x, y] = mid(holdEl); ptr(holdEl, 'pointerup', x, y, 0); holdEl = null; } }
function pressB() {
  const note = $('note'); if (vis(note)) return note.click();
  const pop = $('shPop'); if (vis(pop)) { if ($('shX')) $('shX').click(); return; }   // окно смены — закрыть (звонок и «держи» без крестика не закрываются)
  key('Escape');
}
function pressStart() {
  const pz = $('pz');
  if (vis(pz) && pz.children.length) return $('pzGo') ? $('pzGo').click() : key('Escape');
  if (IN_GAME.includes(NS().mode)) { if ($('bMenu')) $('bMenu').click(); return; }
  if (document.querySelector('#ui .title.gate')) return key('Enter');   // титул: «нажми, чтобы начать»
  key('Escape');
}
function camCycle(d) {   // LB/RB: соседняя камера смены, тринадцатую пропускаем
  const bs = [...document.querySelectorAll('#mg .sh-cams button')].filter(b => vis(b) && !b.classList.contains('c13')); if (bs.length < 2 || vis($('shPop'))) return;
  const i = Math.max(0, bs.findIndex(b => b.classList.contains('on'))), nb = bs[(i + d + bs.length) % bs.length], was = cur && cur.closest('.sh-cams');
  nb.click(); if (was) setCur(nb);
}
const inShift = () => NS().mode === 'shift' && vis($('mg')) && !!document.querySelector('#mg .sh');
function shiftBtn(id) { if (inShift() && !vis($('shPop')) && $(id)) $(id).click(); }

/* ---------- главный цикл ---------- */
let lt = tnow(), frames = 0;
function frame() {
  const t = tnow(), dt = Math.min(.1, (t - lt) / 1000); lt = t; frames++;
  if (pull) pull();
  const s = read();
  if (s) {
    const b = s.b, hit = i => b[i] && !prev[i], up = i => !b[i] && prev[i];
    const [lx, ly] = dz(s.ax[0] || 0, s.ax[1] || 0), [rx, ry] = dz(s.ax[2] || 0, s.ax[3] || 0);
    if (b.some(Boolean) || lx || ly || rx || ry) padOn();
    if (on && t > syncT) { list = sync(); syncT = t + 100; }
    const mg = $('mg'), mgOn = vis(mg), pm = on && mgOn && pointerMode(mg);
    if (pm) { const dx = (b[BTN.RIGHT] ? 1 : 0) - (b[BTN.LEFT] ? 1 : 0), dy = (b[BTN.DOWN] ? 1 : 0) - (b[BTN.UP] ? 1 : 0); vpStep(mg, lx + rx + dx, ly + ry + dy, b[BTN.A], dt); }
    else { if (dot) curDot(); vDown = false; if (on) look(rx, ry); }
    // направление: крестовина или левый стик (с повтором, пока держишь)
    const dir = b[BTN.UP] || ly < -.5 ? 'U' : b[BTN.DOWN] || ly > .5 ? 'D' : b[BTN.LEFT] || lx < -.5 ? 'L' : b[BTN.RIGHT] || lx > .5 ? 'R' : '';
    const tp = mgOn ? mg.querySelector('.tape') : null, tpOn = !!tp && vis(tp);
    if (tpOn) tapeStep(tp, dir === 'L' ? -1 : dir === 'R' ? 1 : 0, dt); else tape = null;
    if (!dir) rep.k = '';
    else if (!pm && !tpOn) {
      let fire = false;
      if (dir !== rep.k) { rep.k = dir; rep.t = t + 380; rep.n = 0; fire = true; } else if (t > rep.t) { rep.t = t + 120; rep.n++; fire = true; }
      if (fire) {
        list = sync(); syncT = t + 100; touched = true;
        const ar = mgOn ? mg.querySelector('.arrows') : null, plug = mgOn ? mg.querySelector('.cord .plug') : null;
        if (ar && vis(ar) && (dir === 'L' || dir === 'R')) { if (!rep.n) { const bt = ar.querySelector(`button[data-d="${dir}"]`); if (bt) bt.click(); } }   // бег: сторона сразу, без повтора
        else if (plug && vis(plug) && dir === 'D') { if (!rep.n) pullCord(plug); }
        else if (cur && cur.matches('input[type=range]') && (dir === 'L' || dir === 'R')) slide(cur, dir === 'L' ? -1 : 1);
        else move(list, dir === 'L' ? -1 : dir === 'R' ? 1 : 0, dir === 'U' ? -1 : dir === 'D' ? 1 : 0);
      }
    }
    if (hit(BTN.A) && !pm) pressA(s.kbA); if (up(BTN.A)) releaseA();
    // «НЕ ДЫШИ», «ДЕРЖИ»: A зажата (хоть и раньше, чем кнопка появилась) — держим, как пальцем; отпустил — отпустили
    if (holdEl && !holdEl.isConnected) holdEl = null;
    if (b[BTN.A] && !pm && !holdEl && cur && cur.matches(HOLD) && vis(cur) && t >= armT) { const [x, y] = mid(cur); holdEl = cur; ptr(cur, 'pointerdown', x, y, 1); }
    if (hit(BTN.B)) pressB();
    if (hit(BTN.START)) pressStart();
    if (hit(BTN.LB)) camCycle(-1); if (hit(BTN.RB)) camCycle(1);
    if (hit(BTN.X)) shiftBtn('shLog'); if (hit(BTN.Y)) { if (inShift()) shiftBtn('shPanel'); else if (IN_GAME.includes(NS().mode) && vis($('bAuto'))) $('bAuto').click(); }
    if (hit(BTN.VIEW)) { if (inShift()) shiftBtn('shBook'); else if (IN_GAME.includes(NS().mode) && vis($('bRules'))) $('bRules').click(); }
    prev = b.slice();
  } else if (prev.length) { releaseA(); prev = []; }
  if (t > curNext) { curNext = t + 300; curTick(t); }
  requestAnimationFrame(frame);
}

/* ---------- курсор: прячется в кат-сценах, если мышь не двигали; картинки не тянутся, текст не выделяется ---------- */
let curT = tnow(), curNext = 0;
function curShow() { curT = tnow(); B.classList.remove('nocur'); }
function curTick(t) {
  const n = NS(), film = n.mode === 'play' && !n.userPause && !layer();
  if (film && t - curT > 2600) B.classList.add('nocur'); else if (!film) B.classList.remove('nocur');
}
addEventListener('dragstart', e => e.preventDefault());
addEventListener('selectstart', e => { if (!(e.target.closest && e.target.closest('input, textarea, [contenteditable]'))) e.preventDefault(); });

/* ---------- ПК-версия: окно ушло в фон — пауза (а звук там глушит main.js), если в настройках «Звук в фоне» выключен ---------- */
if (DESK) addEventListener('wheel', e => { if (e.ctrlKey) e.preventDefault(); }, { passive: false });   // Ctrl + колесо не увеличивает страницу
if (DESK) addEventListener('blur', () => { const n = NS(); if (DESK.bgSound && DESK.bgSound()) return; if (IN_GAME.includes(n.mode) && !n.userPause && $('bMenu')) $('bMenu').click(); });
window.__pad = { get on() { return on; }, get cur() { return cur; }, get root() { return curRoot; }, get frames() { return frames; }, layer, sync };   // для автопроверки (tools/desktop/check.js)
requestAnimationFrame(frame);
})();
