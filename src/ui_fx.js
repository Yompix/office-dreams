/* ============================================================
   OFFICE DREAMS — interactivity & juice:
   tappable office scene, catchable ideas, talking employees,
   coins flying to the HUD, tap combos, screen shake
   ============================================================ */

/* ---------- scene <-> screen coordinates ---------- */
function sceneMap() {
  if (artOfficeOn()) return artSceneMap();
  const cv = UI.sceneCv;
  if (!cv || !cv.isConnected) return null;
  const r = cv.getBoundingClientRect();
  if (!r.width) return null;
  const f = camFit(cv.width, cv.height, true); // canvas pixels
  const kx = r.width / cv.width, ky = r.height / cv.height;
  return {
    r,
    // scene pixel → position inside the .scene box (CSS px)
    toCss: (x, y) => [f[0] * kx + ((x - CAM.sx) / CAM.sw) * f[2] * kx, f[1] * ky + ((y - CAM.sy) / CAM.sh) * f[3] * ky],
    // client point → scene pixel
    toScene: (cx, cy) => [CAM.sx + (((cx - r.left) / kx - f[0]) / f[2]) * CAM.sw, CAM.sy + (((cy - r.top) / ky - f[1]) / f[3]) * CAM.sh],
    scale: (f[2] * kx) / CAM.sw,
  };
}
const HEAD_DY = 20; // sprite head is ~20 scene px above the seat point
function spotOf(kind) {
  if (artOfficeOn()) return artSpot(kind);
  if (kind === 'you') { const a = P(5.1, 5.1, 9); return [a[0], a[1] - HEAD_DY]; }
  if (kind === 'laptop') return P(6.3, 5.4, 16);
  if (kind === 'cat') { const a = P(6.5, 6.1, 13); return [a[0] - 3, a[1] - 3]; }
  const sp = STAND[kind];
  if (sp) { const a = P(sp.gx, sp.gy, 0); return [a[0], a[1] - (sp.pose === 'pad' ? 17 : 26)]; }
  const st = STATION[kind];
  if (!st) return null;
  const a = P(st.chair[0], st.chair[1], 9);
  return [a[0], a[1] - HEAD_DY];
}

/* ---------- tapping the office ---------- */
function onSceneTap(e) {
  const m = sceneMap();
  if (!m) return;
  const [x, y] = m.toScene(e.clientX, e.clientY);
  const near = (k, rad) => { const p = spotOf(k); return p && Math.hypot(p[0] - x, p[1] - y) <= rad; };
  if (S.idea && near(S.idea.role, 16)) return grabIdea();
  if (S.bug) { const b = bugSpot(); if (b && Math.hypot(b[0] - x, b[1] - y) <= 14) return squash(); }
  if (artOfficeOn()) return artOfficeTap(e, x * ART_K, y * ART_K);
  if (near('cat', 9)) return petCat(e);
  for (const r of ROLES) {
    if (near(r, 15)) return S.staff[r].lvl > 0 ? talk(r) : (UI.sel = r, go('studio', 'team'));
  }
  if (near('you', 16) || near('laptop', 12)) return onWorkDown(e);
}
function petCat(e) {
  UI.catPetT = performance.now() / 1000;
  sfx('meow');
  haptic('light');
  for (let i = 0; i < 4; i++) setTimeout(() => floatText(e.clientX + (Math.random() * 30 - 15), e.clientY - 10, '❤', 'heart'), i * 90);
}
const TALK = { ok: 4, mid: 3, low: 3 };
function talk(role) {
  const st = S.staff[role];
  const mood = moodClass(st.mood);
  const line = L('talk.' + mood + (1 + Math.floor(Math.random() * TALK[mood])));
  sayBubble(role, `<b>${esc(L('role.' + role + '.name'))}</b> · ${Math.round(st.mood)}%<br>${esc(line)}`);
  sfx('blip');
  haptic('light');
}
function sayBubble(kind, html) {
  ART_LIFE.talk[kind] = performance.now() / 1000; // their mouth moves in the new look
  const p = spotOf(kind);
  if (p) sayAt(p[0], p[1], html);
}
// a speech bubble above a scene point (kept inside the scene box)
function sayAt(x, y, html) {
  const box = $('.scene .scene-fx');
  const m = sceneMap();
  if (!box || !m) return;
  let [cx, cy] = m.toCss(x, y - 6);
  cx = Math.max(90, Math.min(m.r.width - 90, cx));
  box.querySelectorAll('.say').forEach((n) => n.remove());
  const el = document.createElement('div');
  el.className = 'say';
  el.innerHTML = html;
  el.style.left = cx + 'px';
  el.style.top = cy + 'px';
  box.appendChild(el);
  // no room above (a thing near the top of the picture): show it below the point instead
  if (cy - el.offsetHeight < 6) { el.classList.add('dn'); el.style.top = cy + 26 + 'px'; }
  setTimeout(() => el.classList.add('out'), 2300);
  setTimeout(() => el.remove(), 2700);
}

/* ---------- ideas: catch the light bulb ---------- */
/* ---------- bugs: squash them before they eat progress ---------- */
const BUG_TTL = 10;
function bugSpot() {
  if (artOfficeOn()) return artBugSpot();
  if (!S.bug) return null;
  const k = 1 - Math.max(0, S.bug.ttl) / BUG_TTL; // 0 → 1 across the floor
  const lane = S.bug.lane || 0;
  const dir = lane === 1 ? -1 : 1;
  const x0 = CAM.sx + 14, x1 = CAM.sx + CAM.sw - 14;
  const x = dir > 0 ? x0 + (x1 - x0) * k : x1 - (x1 - x0) * k;
  const y = [106, 121, 136][lane] + Math.sin(k * 40) * 3;
  return [x, y, dir];
}
function updateBug(box, m) {
  let b = box.querySelector('.bug');
  if (!S.bug || UI.tab !== 'office') { if (b && !b.classList.contains('splat')) b.remove(); return; }
  const p = bugSpot();
  const [cx, cy] = m.toCss(p[0], p[1]);
  if (!b || b.classList.contains('splat')) {
    b = document.createElement('button');
    b.className = 'bug';
    b.setAttribute('aria-label', L('bug.squash'));
    b.innerHTML = '<span>🐞</span>';
    b.addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); squash(); });
    box.appendChild(b);
  }
  b.style.left = cx + 'px';
  b.style.top = cy + 'px';
  b.style.setProperty('--dir', p[2]);
  b.classList.toggle('late', S.bug.ttl < 3.5);
}
function squash() {
  const b = $('.scene .bug');
  const r = b ? b.getBoundingClientRect() : null;
  const res = netAct('bug');
  if (!res.ok) return;
  haptic('heavy');
  sfx('hit');
  shake(false);
  if (b) { b.classList.add('splat'); b.innerHTML = '<span>💥</span>'; setTimeout(() => b.remove(), 450); }
  if (r) {
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    burst(x, y, 12, ['#7dff6a', '#3fd0ff', '#ffd23f']);
    floatText(x, y - 16, L('bug.got', { n: money(res.cash) }), 'cash');
    flyTo(x, y, '.pill.cash', I('coin', 14), 5);
  }
  render();
}

function updateSceneFx() {
  const box = $('.scene .scene-fx');
  if (!box) return;
  const mm = sceneMap();
  if (mm) updateBug(box, mm);
  let b = box.querySelector('.idea');
  if (!S.idea || UI.tab !== 'office') { if (b) b.remove(); return; }
  const m = sceneMap();
  if (!m) return;
  const p = spotOf(S.idea.role);
  if (!p) return;
  const [cx, cy] = m.toCss(p[0], p[1] - 14);
  if (!b) {
    b = document.createElement('button');
    b.className = 'idea';
    b.setAttribute('aria-label', L('idea.catch'));
    b.innerHTML = '<span class="i-bulb">💡</span><svg viewBox="0 0 36 36"><circle cx="18" cy="18" r="16"/></svg>';
    b.addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); grabIdea(); });
    box.appendChild(b);
  }
  b.style.left = cx + 'px';
  b.style.top = cy + 'px';
  b.style.setProperty('--left', Math.max(0, Math.min(1, S.idea.ttl / 12)).toFixed(3));
}
function grabIdea() {
  const role = S.idea && S.idea.role;
  const b = $('.scene .idea');
  const r = b ? b.getBoundingClientRect() : null;
  const res = netAct('idea');
  if (!res.ok) return;
  haptic('medium');
  sfx('idea');
  if (b) { b.classList.add('pop'); setTimeout(() => b.remove(), 350); }
  if (r) {
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    burst(x, y, 14, ['#ffd23f', '#fff6c2', '#3fd0ff', '#ff3fd0']);
    floatText(x, y - 18, '+' + fmt(res.bonus) + ' ' + L('ui.wp'), 'pts');
    flyTo(x, y, '.pmini .bar', '✦', 6, 'spark-fly');
    flyTo(x, y, '.pill.cash', I('coin', 14), 4);
  }
  if (role) setTimeout(() => sayBubble(role, esc(L(`idea.thanks${1 + Math.floor(Math.random() * 3)}`))), 200);
  render();
}

/* ---------- things flying into the HUD ---------- */
function flyTo(x, y, targetSel, html, n, cls) {
  const t = $(targetSel);
  const layer = $('#fxl');
  if (!t || !layer) return;
  const tr = t.getBoundingClientRect();
  const tx = tr.left + tr.width / 2, ty = tr.top + tr.height / 2;
  for (let i = 0; i < (n || 6); i++) {
    const el = document.createElement('div');
    el.className = 'fly ' + (cls || '');
    el.innerHTML = html;
    el.style.left = x + 'px';
    el.style.top = y + 'px';
    layer.appendChild(el);
    const sx = (Math.random() - 0.5) * 70, sy = -30 - Math.random() * 50;
    const dur = 650 + Math.random() * 300;
    if (el.animate) {
      el.animate([
        { transform: 'translate(-50%,-50%) scale(0.6)', opacity: 0 },
        { transform: `translate(calc(-50% + ${sx}px), calc(-50% + ${sy}px)) scale(1.15)`, opacity: 1, offset: 0.3 },
        { transform: `translate(calc(-50% + ${tx - x}px), calc(-50% + ${ty - y}px)) scale(0.5)`, opacity: 0.9 },
      ], { duration: dur, delay: i * 45, easing: 'cubic-bezier(.5,0,.6,1)', fill: 'both' });
    }
    setTimeout(() => el.remove(), dur + i * 45 + 50);
  }
  setTimeout(() => { t.classList.remove('flash'); void t.offsetWidth; t.classList.add('flash'); }, 700);
}

/* ---------- tap combo (pure juice) ---------- */
const COMBO = { n: 0, last: 0, el: null };
function comboTap(x, y) {
  const now = performance.now();
  COMBO.n = now - COMBO.last < 450 ? COMBO.n + 1 : 1;
  COMBO.last = now;
  const wrap = $('.workp');
  if (!wrap) return;
  if (!COMBO.el || !COMBO.el.isConnected) {
    COMBO.el = document.createElement('div');
    COMBO.el.className = 'combo px';
    wrap.appendChild(COMBO.el);
  }
  if (COMBO.n < 5) { COMBO.el.classList.remove('on'); return; }
  COMBO.el.textContent = 'COMBO ×' + COMBO.n;
  COMBO.el.classList.remove('bump'); void COMBO.el.offsetWidth; COMBO.el.classList.add('on', 'bump');
  COMBO.el.dataset.lvl = COMBO.n >= 50 ? 3 : COMBO.n >= 25 ? 2 : COMBO.n >= 10 ? 1 : 0;
  if (COMBO.n === 10 || COMBO.n === 25 || COMBO.n === 50 || (COMBO.n > 50 && COMBO.n % 25 === 0)) {
    burst(x, y, 18, ['#ff3fd0', '#ffd23f', '#3fd0ff', '#34e37a']);
    sfx('combo');
    haptic('heavy');
  }
  clearTimeout(COMBO.t);
  COMBO.t = setTimeout(() => { if (COMBO.el) COMBO.el.classList.remove('on'); COMBO.n = 0; }, 900);
}

/* ---------- screen shake ---------- */
function shake(strong) {
  const a = $('#app');
  if (!a) return;
  a.classList.remove('shake', 'shake2'); void a.offsetWidth;
  a.classList.add(strong ? 'shake2' : 'shake');
  setTimeout(() => a.classList.remove('shake', 'shake2'), 600);
}

/* ============================================================
   v2 visual layer: living background, tap ripples, staggered entrances
   ============================================================ */
const VFX = { reduce: false };
function vfxInit() {
  try { VFX.reduce = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) {}
}
// a soft ripple wherever a button is pressed
function vfxRipple(e) {
  const el = e.target && e.target.closest && e.target.closest('.btn, .sbtn');
  if (!el || VFX.reduce) return;
  const r = el.getBoundingClientRect();
  const s = document.createElement('span');
  s.className = 'rip';
  const d = Math.max(r.width, r.height) * 2.2;
  s.style.width = s.style.height = d + 'px';
  s.style.left = (e.clientX - r.left - d / 2) + 'px';
  s.style.top = (e.clientY - r.top - d / 2) + 'px';
  el.classList.add('has-rip');
  el.appendChild(s);
  setTimeout(() => s.remove(), 650);
}
// entrance cascade: each block of a freshly opened page rises in after the previous one
function vfxStagger() {
  const page = document.querySelector('#main > .page.enter');
  if (!page) return;
  Array.prototype.forEach.call(page.children, (el, i) => el.style.setProperty('--i', Math.min(i, 10)));
}
