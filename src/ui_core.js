/* ============================================================
   OFFICE DREAMS — UI core: helpers, images, sound, toasts,
   modals, particles, confetti
   ============================================================ */
const $ = (sel, root) => (root || document).querySelector(sel);
const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const I = icoSVG;
const money = (n) => '$' + fmt(n);
const fmtSmall = (n) => (n < 10 ? (Math.round(n * 10) / 10).toString() : fmt(n));
const fmtHud = (n) => (n >= 1e5 && n < 1e6 ? Math.floor(n / 1e3) + 'K' : fmt(n));
function fmtPts(n) {
  n = Number(n) || 0;
  if (n >= 1e6) return (n / 1e6).toFixed(2).replace(/\.?0+$/, '') + 'M';
  if (n >= 1e4) return fmt(Math.floor(n));
  return (Math.round(n * 10) / 10).toLocaleString(LANG === 'en' ? 'en-US' : LANG, { maximumFractionDigits: 1 });
}
function fmtDur(sec) {
  sec = Math.max(0, Math.round(sec));
  if (sec < 60) return sec + L('u.s');
  const m = Math.floor(sec / 60), s = sec % 60;
  if (m < 60) return m + L('u.m') + (s ? ' ' + s + L('u.s') : '');
  const h = Math.floor(m / 60);
  return h + L('u.h') + ' ' + (m % 60) + L('u.m');
}

const UI = {
  tab: 'office', sub: { studio: 'team', drop: 'home' }, sel: 'prog',
  modal: null, queue: [], down: false, audio: null,
  lastRev: -1, lastSig: '', tapFlash: 0, booted: false,
  sceneK: 0, sceneAcc: 0, towerAcc: 0, liveAcc: 0,
  hub: { tasks: null, friends: null, board: null, shop: null, loadedAt: {} },
  ecoShown: 0, nameSeed: 1, renderedTab: '',
};

/* ---------- pixel images (canvas → PNG data URL, cached) ---------- */
const imgCache = {};
function pxURL(key, w, h, draw) {
  if (imgCache[key] !== undefined) return imgCache[key];
  let url = '';
  try {
    const B = new Buf(w, h);
    draw(B);
    const cv = document.createElement('canvas');
    cv.width = w;
    cv.height = h;
    cv.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(B.d.buffer), w, h), 0, 0);
    url = cv.toDataURL('image/png');
  } catch (e) { url = ''; }
  imgCache[key] = url;
  return url;
}
// AI-drawn portraits served by the game server (the procedural pixel portrait stays as a fallback offline / for other kinds);
// the mood rides in the URL fragment so CSS can dim a sad face
const AI_PORTRAITS = new Set(['you', 'prog', 'des', 'art', 'mkt', 'qa', 'snd', 'prod', 'cm', 'boss', 'inv', 'npc']);
const portrait = (kind, mood) => artFace(kind, mood) || ((AI_PORTRAITS.has(kind) && typeof NET !== 'undefined' && NET.mode === 'online')
  ? '/portrait-' + kind + '.jpg#m' + (mood >= 60 ? 2 : mood >= 35 ? 1 : 0)
  : portraitPx(kind, mood));
// Art 2: the face of the drawn character (the same person as in the illustrated office), cut from the standing sprite;
// '' until the sprite has loaded (the page is redrawn then) or when art mode is off
const ART_FACES = new Set(['you', 'prog', 'des', 'art', 'mkt', 'qa', 'snd', 'prod', 'cm', 'boss', 'inv', 'npc', 'streamer']);
// people from the events are drawn characters too (npc_<name>.webp); the streamer is Art 2 only
const ART_FACE_IMG = { boss: 'npc_boss', inv: 'npc_investor', npc: 'npc_journalist', streamer: 'npc_streamer' };
const ART_EV_FACE = { blogger: 'streamer', fan: 'streamer' };
const evFace = (id) => (artOn() && ART_EV_FACE[id]) || EV_FACE[id] || 'npc';
function artFace(kind, mood, st) {
  if (!ART_FACES.has(kind) || !artOn()) return '';
  const m = mood >= 60 ? 2 : mood >= 35 ? 1 : 0, key = 'af:' + kind + ':' + m + (st ? ':' + st : '');
  if (!imgCache[key]) {
    const src = ART_FACE_IMG[kind] || 'stand_' + kind, im = artImg(src);
    if (!im) {
      const el = ART.imgs[src];
      if (el && !el.odFace) { el.odFace = 1; el.addEventListener('load', () => { clearTimeout(UI.faceT); UI.faceT = setTimeout(() => render(true), 80); }); }
      return '';
    }
    try {
      const S2 = 96, w = im.naturalWidth, s = w * 1.32, cv = document.createElement('canvas');
      cv.width = cv.height = S2;
      const g = cv.getContext('2d'), bg = g.createRadialGradient(S2 / 2, S2 * 0.38, 4, S2 / 2, S2 * 0.45, S2 * 0.7);
      bg.addColorStop(0, '#2c2a8a'); bg.addColorStop(1, '#0c1040');
      g.fillStyle = bg; g.fillRect(0, 0, S2, S2);
      const k = S2 / s, top = Math.round(6 * k), fim = artFaceV(im, st || 0);
      g.drawImage(fim, 0, 0, w, (S2 - top) / k, (S2 - w * k) / 2, top, w * k, S2 - top);
      if (m < 2) { // a tired or sad face looks greyer and darker (same as the mood filter on the other portraits)
        const d = g.getImageData(0, 0, S2, S2), a = d.data, sat = m ? 0.8 : 0.45, br = m ? 0.92 : 0.8;
        for (let i = 0; i < a.length; i += 4) { const l = a[i] * 0.3 + a[i + 1] * 0.59 + a[i + 2] * 0.11; for (let c = 0; c < 3; c++) a[i + c] = (l + (a[i + c] - l) * sat) * br; }
        g.putImageData(d, 0, 0);
      }
      imgCache[key] = cv.toDataURL('image/png');
      if (!st) { ART_FACE_URL.set(imgCache[key], [kind, mood]); if (!ART_FACE_URL.timer) ART_FACE_URL.timer = setInterval(artFacesTick, 110); }
    } catch (e) { return ''; }
  }
  return imgCache[key];
}
// the faces on screen are alive too: they blink, and in a window that just opened (an event, a talk) the person speaks
const ART_FACE_URL = new Map();
function artFacesTick() {
  if (document.hidden) return;
  const now = performance.now() / 1000;
  for (const el of document.querySelectorAll('img')) {
    let d = el._af;
    if (!d) {
      const k = ART_FACE_URL.get(el.getAttribute('src'));
      if (!k) continue;
      d = el._af = { kind: k[0], mood: k[1], t0: now, base: el.getAttribute('src'), st: 0, talk: !!el.closest('#ovl, .say, .modal') };
    }
    const st = artFaceState(d.kind + (d.talk ? 'o' : ''), now, d.talk && now - d.t0 < 2.4);
    if (st === d.st) continue;
    const url = st ? artFace(d.kind, d.mood, st) : d.base;
    if (url) { el.setAttribute('src', url); d.st = st; }
  }
}
const portraitPx = (kind, mood) => pxURL('p2:' + kind + ':' + (mood >= 60 ? 2 : mood >= 35 ? 1 : 0), 48, 48, (B) => drawPortrait(B, kind, mood));
const seedOf = (name) => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) % 97;
  return h;
};
const coverURL = (genre, name) => pxURL('c:' + genre + ':' + seedOf(name), 32, 32, (B) => drawCover(B, genre, seedOf(name)));
function heroURL() {
  return pxURL('hero', 256, 176, (B) => {
    const sc = createScene();
    const fr = sc.render({
      f: 0.93, working: true,
      staff: { prog: { lvl: 4, mood: 90 }, art: { lvl: 3, mood: 90 }, des: { lvl: 3, mood: 90 }, mkt: { lvl: 2, mood: 90 } },
      items: { coffee: 1, chairs: 1, monitor2: 1, lamp: 1, lounge: 1, rack: 1 },
    }, 1.2);
    B.d.set(fr.d);
  });
}
const imgTag = (src, cls, alt) => `<img class="${cls || ''}" src="${src}" alt="${esc(alt || '')}" width="48" height="48" draggable="false">`;
const EV_FACE = { deadline: 'boss', investor: 'inv', bug: 'prog', poach: 'npc', hackathon: 'npc', blogger: 'npc', party: 'des', phishing: 'npc', award: 'boss', server: 'prog', coffee: 'art', fan: 'npc' };

/* ---------- sound (tiny WebAudio chiptune) ---------- */
function ensureAudio() {
  try {
    if (!UI.audio) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC) UI.audio = new AC();
    }
    if (UI.audio && UI.audio.state === 'suspended') UI.audio.resume();
  } catch (e) {}
}
function beep(f, d, type, vol, when) {
  const a = UI.audio;
  if (!a || !S || !S.settings.sound) return;
  try {
    const o = a.createOscillator(), g = a.createGain();
    o.type = type || 'square';
    o.frequency.value = f;
    const t0 = a.currentTime + (when || 0);
    g.gain.setValueAtTime(vol || 0.035, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + d);
    o.connect(g);
    g.connect(a.destination);
    o.start(t0);
    o.stop(t0 + d + 0.02);
  } catch (e) {}
}
const SFX = {
  tap: () => beep(620 + Math.random() * 80, 0.045, 'square', 0.022),
  coin: () => { beep(988, 0.06); beep(1319, 0.12, 'square', 0.04, 0.06); },
  up: () => { beep(523, 0.07); beep(659, 0.07, 'square', 0.04, 0.07); beep(784, 0.12, 'square', 0.04, 0.14); },
  release: () => { beep(392, 0.1); beep(523, 0.14, 'square', 0.04, 0.1); },
  hit: () => { [523, 659, 784, 1046].forEach((f, i) => beep(f, 0.12, 'square', 0.04, i * 0.09)); },
  floor: () => { [392, 523, 659, 784, 1046].forEach((f, i) => beep(f, 0.14, 'square', 0.045, i * 0.08)); },
  bad: () => beep(180, 0.2, 'sawtooth', 0.04),
  pts: () => { beep(1568, 0.05, 'triangle', 0.05); beep(2093, 0.09, 'triangle', 0.05, 0.05); },
  meow: () => { beep(880, 0.08, 'triangle', 0.05); beep(660, 0.16, 'triangle', 0.04, 0.07); },
  blip: () => beep(1046, 0.05, 'triangle', 0.04),
  idea: () => { [784, 988, 1175, 1568].forEach((f, i) => beep(f, 0.08, 'triangle', 0.05, i * 0.05)); },
  combo: () => { [659, 880, 1319].forEach((f, i) => beep(f, 0.07, 'square', 0.035, i * 0.05)); },
  pop: () => beep(1318, 0.06, 'sine', 0.05),
};
function sfx(n) { try { if (SFX[n]) SFX[n](); } catch (e) {} }
function haptic(kind) {
  try {
    if (TG && TG.HapticFeedback) {
      if (kind === 'ok' || kind === 'err') TG.HapticFeedback.notificationOccurred(kind === 'ok' ? 'success' : 'error');
      else TG.HapticFeedback.impactOccurred(kind || 'light');
    } else if (navigator.vibrate) navigator.vibrate(kind === 'heavy' ? 20 : 8);
  } catch (e) {}
}

/* ---------- toasts ---------- */
function toast(text, kind, icon) {
  const box = $('#toasts');
  if (!box || !text) return;
  if (box.lastChild && box.lastChild.dataset.t === text) return;
  while (box.childElementCount >= 2) box.firstChild.remove();
  const el = document.createElement('div');
  el.className = 'toast ' + (kind || '');
  el.dataset.t = text;
  el.innerHTML = (icon ? I(icon, 16) : '') + '<span>' + esc(text) + '</span>';
  box.appendChild(el);
  setTimeout(() => el.remove(), 2700);
}
function errToast(err) {
  const k = 'err.' + (err || 'unavailable');
  toast(hasKey(k) ? L(k) : L('err.unavailable'), 'bad');
  sfx('bad');
  haptic('err');
}

/* ---------- modals ---------- */
function openModal(html, opt) {
  opt = opt || {};
  const ovl = $('#ovl');
  // same window refreshed (a toggle, a language switch): swap the content in place — no flash, no re-animation
  if (UI.modal && opt.id && UI.modal.id === opt.id && !ovl.hidden) {
    const m0 = $('.modal', ovl);
    if (m0) {
      const keep = m0.scrollTop;
      m0.className = 'modal ' + (opt.mcls || '');
      m0.innerHTML = (opt.sticky ? '' : '<button class="m-x" data-act="close" aria-label="' + esc(L('ui.close')) + '">' + I('close', 14) + '</button>') + html;
      m0.scrollTop = keep;
      UI.modal.sticky = !!opt.sticky;
      return;
    }
  }
  UI.modal = { sticky: !!opt.sticky, id: opt.id || '' };
  ovl.className = 'ovl ' + (opt.cls || '');
  ovl.innerHTML = '<div class="modal ' + (opt.mcls || '') + '" role="dialog" aria-modal="true">' +
    (opt.sticky ? '' : '<button class="m-x" data-act="close" aria-label="' + esc(L('ui.close')) + '">' + I('close', 14) + '</button>') + html + '</div>';
  ovl.hidden = false;
  const m = $('.modal', ovl);
  if (m) m.scrollTop = 0;
  if (TG && TG.BackButton && !opt.sticky) { try { TG.BackButton.show(); } catch (e) {} }
}
function closeModal(force) {
  if (UI.modal && UI.modal.sticky && !force) return;
  const ovl = $('#ovl');
  UI.modal = null;
  ovl.hidden = true;
  ovl.innerHTML = '';
  syncBackButton();
  const next = UI.queue.shift();
  if (next) setTimeout(() => (UI.pvp ? UI.queue.unshift(next) : next()), 220);
}
function modalOrQueue(fn) {
  if (UI.modal || UI.pvp) UI.queue.push(fn); // nothing pops up over a running battle
  else fn();
}
function syncBackButton() {
  if (!TG || !TG.BackButton) return;
  try {
    if (UI.modal || UI.pvp || UI.tab !== 'office') TG.BackButton.show();
    else TG.BackButton.hide();
  } catch (e) {}
}

/* ---------- particles (DOM, GPU-friendly transforms) ---------- */
function floatText(x, y, html, cls) {
  const layer = $('#fxl');
  if (!layer) return;
  if (layer.childElementCount > 26) layer.firstChild.remove();
  const el = document.createElement('div');
  el.className = 'flt ' + (cls || '');
  el.innerHTML = html;
  el.style.left = x + 'px';
  el.style.top = y + 'px';
  el.style.setProperty('--dx', (Math.random() * 40 - 20).toFixed(0) + 'px');
  layer.appendChild(el);
  setTimeout(() => el.remove(), 1000);
}
function burst(x, y, n, colors) {
  const layer = $('#fxl');
  if (!layer) return;
  for (let i = 0; i < n; i++) {
    if (layer.childElementCount > 60) break;
    const el = document.createElement('i');
    el.className = 'spark';
    const a = Math.random() * Math.PI * 2, r = 30 + Math.random() * 50;
    el.style.left = x + 'px';
    el.style.top = y + 'px';
    el.style.background = colors[i % colors.length];
    el.style.setProperty('--tx', (Math.cos(a) * r).toFixed(0) + 'px');
    el.style.setProperty('--ty', (Math.sin(a) * r - 20).toFixed(0) + 'px');
    layer.appendChild(el);
    setTimeout(() => el.remove(), 700);
  }
}

/* ---------- confetti (canvas, runs only while visible) ---------- */
const CONF = { parts: [], raf: 0, cv: null };
function confetti(n) {
  const cv = $('#confetti');
  if (!cv) return;
  CONF.cv = cv;
  const w = (cv.width = window.innerWidth), h = (cv.height = window.innerHeight);
  const cols = ['#ff3fd0', '#3fd0ff', '#ffd23f', '#34e37a', '#ffffff', '#8a5cff'];
  for (let i = 0; i < (n || 90); i++) {
    CONF.parts.push({
      x: w / 2 + (Math.random() - 0.5) * w * 0.3, y: h * 0.35,
      vx: (Math.random() - 0.5) * 9, vy: -6 - Math.random() * 8,
      s: 3 + Math.floor(Math.random() * 4), c: cols[i % cols.length], r: Math.random() * 6, life: 120 + Math.random() * 60,
    });
  }
  cv.hidden = false;
  if (!CONF.raf) CONF.raf = requestAnimationFrame(confStep);
}
function confStep() {
  const cv = CONF.cv, c = cv.getContext('2d');
  c.clearRect(0, 0, cv.width, cv.height);
  CONF.parts = CONF.parts.filter((p) => p.life > 0 && p.y < cv.height + 20);
  for (const p of CONF.parts) {
    p.vy += 0.28; p.vx *= 0.99; p.x += p.vx; p.y += p.vy; p.r += 0.2; p.life--;
    c.fillStyle = p.c;
    const sw = Math.abs(Math.cos(p.r)) * p.s + 1;
    c.fillRect(Math.round(p.x), Math.round(p.y), Math.round(sw), p.s);
  }
  if (CONF.parts.length) CONF.raf = requestAnimationFrame(confStep);
  else { CONF.raf = 0; cv.hidden = true; }
}

/* ---------- count-up numbers ---------- */
function countUp(el, to, dur, fmtF) {
  if (!el) return;
  const from = parseFloat(el.dataset.v || '0') || 0;
  el.dataset.v = String(to);
  if (Math.abs(to - from) < 0.05) { el.textContent = fmtF(to); return; }
  const t0 = performance.now();
  const step = (t) => {
    const k = Math.min(1, (t - t0) / (dur || 700));
    const e = 1 - Math.pow(1 - k, 3);
    el.textContent = fmtF(from + (to - from) * e);
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

/* ---------- clipboard / sharing ---------- */
function copyText(text, cb) {
  const done = (ok) => cb && cb(ok);
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => done(true), () => done(fallbackCopy(text)));
      return;
    }
  } catch (e) {}
  done(fallbackCopy(text));
}
function fallbackCopy(text) {
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return ok;
  } catch (e) { return false; }
}
function openLink(url) {
  if (!url) return;
  try {
    if (TG && /^https:\/\/t\.me\//.test(url) && TG.openTelegramLink) return TG.openTelegramLink(url);
    if (TG && TG.openLink) return TG.openLink(url);
  } catch (e) {}
  window.open(url, '_blank', 'noopener');
}

/* ---------- in-place DOM patch: re-renders change only what changed ----------
   Rebuilding the page with innerHTML recreated the office canvas (a black flash), every image
   (a texture blink) and restarted CSS animations several times a second. morph() keeps the
   existing nodes and patches attributes/text, so canvases, images and animations survive. */
const MORPH_KEEP = ['hit', 'pulse', 'flash', 'has-rip', 'moving', 'tired', 'lit'];
// elements added at runtime by effects (press ripples, the tap combo counter): kept where they are
const morphDyn = (n) => n && n.nodeType === 1 && (n.classList.contains('rip') || n.classList.contains('combo'));
function morphAttrs(a, b) {
  const canvas = a.tagName === 'CANVAS', live = a.hasAttribute('data-w');
  for (let i = a.attributes.length - 1; i >= 0; i--) {
    const n = a.attributes[i].name;
    if (b.hasAttribute(n) || n === 'style' || (canvas && (n === 'width' || n === 'height'))) continue;
    a.removeAttribute(n);
  }
  for (let i = 0; i < b.attributes.length; i++) {
    const n = b.attributes[i].name, v = b.attributes[i].value;
    if (canvas && (n === 'width' || n === 'height')) continue; // resizing a canvas wipes it
    if (n === 'style' && live) continue; // bar widths are driven by live()
    if (n === 'class') {
      const keep = MORPH_KEEP.filter((c) => a.classList.contains(c) && !b.classList.contains(c));
      const nv = keep.length ? v + ' ' + keep.join(' ') : v;
      if (a.getAttribute('class') !== nv) a.setAttribute('class', nv);
      continue;
    }
    if (a.getAttribute(n) !== v) { a.setAttribute(n, v); if (n === 'data-t' || n === 'data-w') a._v = undefined; }
  }
  if (a.tagName === 'INPUT' && a !== document.activeElement && a.value !== b.value) a.value = b.value;
}
function morphKids(a, b) {
  const an = a.childNodes, bn = Array.prototype.slice.call(b.childNodes);
  let i = 0;
  for (const nb of bn) {
    let na = an[i];
    while (morphDyn(na)) na = an[++i]; // a running press ripple / combo counter
    if (!na) { a.appendChild(nb); i++; continue; }
    if (na.nodeType !== nb.nodeType || (na.nodeType === 1 && (na.tagName !== nb.tagName || na.id !== nb.id))) { a.replaceChild(nb, na); i++; continue; }
    if (na.nodeType === 3 || na.nodeType === 8) { if (na.nodeValue !== nb.nodeValue) na.nodeValue = nb.nodeValue; i++; continue; }
    morphAttrs(na, nb);
    if (na.tagName === 'CANVAS' || na.hasAttribute('data-keep') || (na.hasAttribute('data-t') && na._v !== undefined)) { i++; continue; } // live() / effects own this content
    if (na.tagName === 'SVG' || na.tagName === 'svg') { if (na.innerHTML !== nb.innerHTML) na.innerHTML = nb.innerHTML; i++; continue; }
    morphKids(na, nb);
    i++;
  }
  while (an.length > i) {
    const x = an[an.length - 1];
    if (morphDyn(x)) { if (an.length - 1 === i) break; a.insertBefore(x, an[i]); continue; }
    a.removeChild(x);
  }
}
function morph(el, html) {
  const t = document.createElement('template');
  t.innerHTML = html;
  morphKids(el, t.content);
}
