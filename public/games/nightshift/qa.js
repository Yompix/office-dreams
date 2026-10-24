// Режим проверки для владельца: игра с ?qa=1 (запоминается; ?qa=0 — выключить). В демо itch.io не включается (список сцен обходил бы покупку).
// • В меню — «Сцены»: любая сцена, час смены или смерть сразу, без прохождения; «Отправить ошибки (N)».
// • В игре — полоска слева сверху: сцена:кадр:секунда · кадров в секунду; ⏮ кадр заново (второй раз подряд — предыдущий), ⏭ следующий, 🐞 ошибка.
// • 🐞 (или клавиша B): пауза, снимок экрана вместе с субтитрами и кнопками, всё для воспроизведения (сцена:кадр:секунда, графика,
//   ориентация, язык, устройство, кадры в секунду, долгие кадры, ошибки JS, флаги) и комментарий. «Отправить» — все отмеченные ошибки
//   одной картинкой (по 6 на картинку) через «Поделиться» (Telegram, чат с Claude…), а где «Поделиться» нет — файлом + текст в буфер обмена.
// Воспроизвести код из отчёта: node tools/story/beats.js ПАПКА P|L cine|retro ru|en "сцена:кадр:секунда" 'флаги JSON'
(() => {
  const N = window.__ns; if (!N || window.NS_QA) return;
  const $ = id => document.getElementById(id), stage = $('stage');
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const KEY = 'ns1_qaBugs', TAGS = ['Анимация', 'Графика', 'Звук', 'Текст', 'Тормозит', 'Сюжет', 'Другое'];
  let bugs = []; try { bugs = JSON.parse(localStorage.getItem(KEY) || '[]') || []; } catch { bugs = []; }
  const keep = () => { for (;;) { try { localStorage.setItem(KEY, JSON.stringify(bugs)); return; } catch { if (!bugs.length) return; bugs.shift(); } } };   // места мало — уходят самые старые снимки
  const IN = ['play', 'choice', 'mg', 'cams', 'shift', 'explore', 'talk'];
  const MODE = { play: 'сцена', choice: 'выбор', mg: 'мини-игра', cams: 'камеры', shift: 'смена', explore: 'обход', talk: 'разговор', menu: 'меню', end: 'финал', load: 'загрузка' };

  // где мы сейчас — код для воспроизведения
  function code() {
    const m = N.mode, sc = N.scene || '-';
    if (m === 'play') return `${sc}:${N.bi}:${N.bt.toFixed(1)}`;
    if (m === 'shift') { const c = $('clock'); return `${sc} · ${c ? c.textContent : ''} · CAM ${(N.shf && N.shf.cam) || ''}`; }
    return m === 'menu' ? 'меню' : `${sc} · ${MODE[m] || m}`;
  }
  // кадры в секунду и долгие кадры — своим кругом (rAF идёт вместе с кадрами игры); ошибки JS — с местом, где случились
  const fr = [], long = [], errs = []; let lastF = 0, open = false;
  document.addEventListener('visibilitychange', () => { lastF = 0; });
  const fps = () => { const a = fr.slice(-60); return a.length > 5 ? Math.round(1000 * a.length / a.reduce((s, x) => s + x, 0)) : 0; };
  const err = s => { errs.push(`${s} [${code()}]`); if (errs.length > 30) errs.shift(); };
  window.addEventListener('error', e => err(`${String(e.message || 'error').slice(0, 160)} @ ${String(e.filename || '').split('/').pop().split('?')[0]}:${e.lineno || 0}`));
  window.addEventListener('unhandledrejection', e => err(`promise: ${String((e.reason && (e.reason.message || e.reason)) || '').slice(0, 160)}`));
  { const ce = console.error; console.error = function (...a) { err('console: ' + a.map(x => String((x && x.message) || x)).join(' ').slice(0, 160)); return ce.apply(this, a); }; }

  const css = document.createElement('style'); css.textContent = `
#qa { position: absolute; left: 4%; top: 9.5%; z-index: 7; display: flex; align-items: center; gap: calc(1.2 * var(--u)); pointer-events: auto; max-width: 74%;
  background: rgba(0,0,0,.62); border: 1px solid rgba(255,211,107,.5); border-radius: 999px; padding: calc(.8 * var(--u)) calc(.9 * var(--u)) calc(.8 * var(--u)) calc(2.2 * var(--u));
  font: 700 calc(2.6 * var(--u))/1.1 Inter, Arial, sans-serif; color: #ffd36b; }
#qa span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
#qa button { flex: none; width: calc(7.4 * var(--u)); height: calc(7.4 * var(--u)); border-radius: 50%; border: 1px solid rgba(255,255,255,.25); background: rgba(255,255,255,.1); color: #fff; font-size: calc(3.4 * var(--u)); padding: 0; line-height: 1; }
#qa .nav, #qa .nx { display: none; } #qa.in .nav { display: inline-block; } #qa.play .nx { display: inline-block; }
#qa.menu { padding-left: calc(.9 * var(--u)); } #qa.menu span { display: none; }   /* в меню — только 🐞 (не закрывает логотип) */
#qaP { position: absolute; inset: 0; z-index: 40; background: rgba(0,0,0,.84); display: flex; align-items: center; justify-content: center; pointer-events: auto; }
#qaP .box { width: 90%; max-height: 94%; overflow-y: auto; overscroll-behavior: contain; background: #15171b; border: 1px solid rgba(255,255,255,.14); border-radius: calc(2.4 * var(--u)); padding: calc(3 * var(--u)); color: #e8e8ea; font: 600 calc(3.1 * var(--u))/1.35 Inter, Arial, sans-serif; text-align: left; }
body.land #qaP .box { width: 76%; display: grid; grid-template-columns: 46% 1fr; gap: 0 calc(3 * var(--u)); align-items: start; }
#qaP img { display: block; max-width: 100%; max-height: 36vh; margin: 0 auto calc(2 * var(--u)); border-radius: calc(1.2 * var(--u)); border: 1px solid rgba(255,255,255,.15); }
body.land #qaP img { max-height: 70vh; grid-row: span 6; }
#qaP .cd { font: 800 calc(3.4 * var(--u))/1.2 "Courier New", monospace; color: #ffd36b; word-break: break-all; }
#qaP .env { opacity: .7; font-size: calc(2.7 * var(--u)); margin: calc(.6 * var(--u)) 0 calc(2 * var(--u)); }
#qaP .tags { display: flex; flex-wrap: wrap; gap: calc(1.4 * var(--u)); margin-bottom: calc(2 * var(--u)); }
#qaP .tags button { border: 1px solid rgba(255,255,255,.25); background: none; color: #ddd; border-radius: 999px; padding: calc(1.2 * var(--u)) calc(2.6 * var(--u)); font: inherit; font-size: calc(2.9 * var(--u)); }
#qaP .tags button.on { background: #e0303a; border-color: #e0303a; color: #fff; }
#qaP textarea { width: 100%; box-sizing: border-box; min-height: calc(16 * var(--u)); resize: vertical; background: #0c0d10; color: #fff; border: 1px solid rgba(255,255,255,.2); border-radius: calc(1.4 * var(--u)); padding: calc(2 * var(--u)); font: inherit; }
#qaP .row { display: flex; gap: calc(2 * var(--u)); margin-top: calc(2 * var(--u)); }
#qaP .row button { flex: 1; border: 1px solid rgba(255,255,255,.25); background: rgba(255,255,255,.06); color: #fff; border-radius: calc(1.6 * var(--u)); padding: calc(2.6 * var(--u)) calc(1 * var(--u)); font: inherit; font-weight: 800; }
#qaP .row button.go { background: #e0303a; border-color: #e0303a; }
#qaP .msg { font-size: calc(3 * var(--u)); opacity: .8; margin: calc(1 * var(--u)) 0; }
#qaT { position: absolute; left: 50%; top: 18%; transform: translateX(-50%); z-index: 41; background: rgba(0,0,0,.85); color: #ffd36b; border: 1px solid rgba(255,211,107,.5); border-radius: calc(1.6 * var(--u)); padding: calc(1.8 * var(--u)) calc(3 * var(--u)); font: 700 calc(3 * var(--u)) Inter, Arial, sans-serif; pointer-events: none; text-align: center; max-width: 80%; }
.title .mlist button.qa-m { font-size: calc(4.2 * var(--u)); color: #ffd36b; }
.qa-list input { width: 100%; box-sizing: border-box; background: #0c0d10; color: #fff; border: 1px solid rgba(255,255,255,.25); border-radius: calc(1.6 * var(--u)); padding: calc(2.4 * var(--u)); font: 600 calc(3.4 * var(--u)) Inter, Arial, sans-serif; margin-bottom: calc(2 * var(--u)); }
.qa-list #qlL { width: 100%; text-align: left; }
.qa-list h3 { color: #ffd36b; font-size: calc(3.4 * var(--u)); margin: calc(3 * var(--u)) 0 calc(1 * var(--u)); }
.qa-list #qlL button { display: grid; grid-template-columns: calc(12 * var(--u)) 1fr; gap: 0 calc(2 * var(--u)); width: 100%; text-align: left; background: rgba(255,255,255,.04); border: 0; border-bottom: 1px solid rgba(255,255,255,.08); color: #ddd; padding: calc(1.8 * var(--u)) calc(1.4 * var(--u)); font: 600 calc(2.9 * var(--u))/1.3 Inter, Arial, sans-serif; }
.qa-list #qlL button b { color: #ff5a5a; font-family: "Courier New", monospace; grid-row: span 2; }
.qa-list #qlL button i { font-style: normal; color: #fff; font-weight: 800; }
.qa-list #qlL button span { opacity: .6; font-size: calc(2.6 * var(--u)); }`;
  document.head.appendChild(css);

  // полоска в игре
  const bar = document.createElement('div'); bar.id = 'qa';
  bar.innerHTML = '<span id="qaL">QA</span><button class="nav" id="qaPrev" title="Кадр заново (дважды — предыдущий)">⏮</button><button class="nx" id="qaNext" title="Следующий кадр">⏭</button><button id="qaBug" title="Ошибка (B)">🐞</button>';
  stage.appendChild(bar);
  const lab = $('qaL'), stop = e => e.stopPropagation();
  for (const ev of ['pointerdown', 'click']) bar.addEventListener(ev, stop);
  let prevT = 0;
  $('qaPrev').onclick = () => { const m = N.mode, sc = N.scene; if (!sc || !IN.includes(m)) return;
    if (m === 'play') { const quick = performance.now() - prevT < 1500; prevT = performance.now(); N.startScene(sc, Math.max(0, N.bi - (quick ? 1 : 0))); } else N.startScene(sc); };
  $('qaNext').onclick = () => { if (N.mode === 'play') N.skipBeat(); };
  $('qaBug').onclick = () => bug();
  document.addEventListener('keydown', e => { if (open || /^(INPUT|TEXTAREA)$/.test((e.target && e.target.tagName) || '')) return; if (e.key === 'b' || e.key === 'B' || e.key === 'и' || e.key === 'И') bug(); });
  let barT = 0;
  function tick() {
    const t = performance.now(); if (t - barT < 250) return; barT = t;
    const m = N.mode; bar.classList.toggle('in', IN.includes(m)); bar.classList.toggle('play', m === 'play'); bar.classList.toggle('menu', m === 'menu');
    const s = `${code()} · ${fps()} fps`; if (lab.textContent !== s) lab.textContent = s;
  }
  function loop(tp) {
    if (lastF && !document.hidden) { const d = tp - lastF; fr.push(d); if (fr.length > 120) fr.shift(); if (d > 120 && !open && N.mode !== 'load') { long.push([Math.round(d), code()]); if (long.length > 40) long.shift(); } }
    lastF = tp; tick(); requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);

  function toast(s, ms = 2600) { let t = $('qaT'); if (!t) { t = document.createElement('div'); t.id = 'qaT'; stage.appendChild(t); } t.textContent = s; t.style.display = ''; clearTimeout(toast.h); toast.h = setTimeout(() => { t.style.display = 'none'; }, ms); }
  function device() {
    const u = navigator.userAgent; let m;
    const model = (m = /Android [\d.]+; ([^;)]+)/.exec(u)) ? m[1].trim() : /iPhone/.test(u) ? 'iPhone' : /iPad/.test(u) ? 'iPad' : '';
    const os = (m = /Android ([\d.]+)/.exec(u)) ? 'Android ' + m[1] : (m = /OS ([\d_]+) like Mac/.exec(u)) ? 'iOS ' + m[1].replace(/_/g, '.') : /Windows/.test(u) ? 'Windows' : /Mac OS X/.test(u) ? 'Mac' : /Linux/.test(u) ? 'Linux' : '';
    const br = (m = /EdgA?\/(\d+)/.exec(u)) ? 'Edge ' + m[1] : (m = /(?:Chrome|CriOS)\/(\d+)/.exec(u)) ? 'Chrome ' + m[1] : (m = /(?:Firefox|FxiOS)\/(\d+)/.exec(u)) ? 'Firefox ' + m[1] : (m = /Version\/([\d.]+).*Safari/.exec(u)) ? 'Safari ' + m[1] : '';
    return [model, os, br, `${screen.width}×${screen.height}@${(window.devicePixelRatio || 1).toFixed(1)}`].filter(Boolean).join(' · ');
  }
  const ver = () => { const v = n => { const s = [...document.scripts].map(x => x.src).find(x => x.includes(n + '.js?v=')); return s ? n + ' ' + s.split('v=')[1] : ''; }; return ['engine', 'story', 'scenes'].map(v).filter(Boolean).join(', '); };
  function info() {
    const W = window.World, c = N.canvas || [], land = document.body.classList.contains('land'), f = N.flags || {}, fl = {};
    for (const k in f) if (f[k]) fl[k] = f[k];
    const worst = long.reduce((a, b) => (!a || b[0] > a[0] ? b : a), null);
    return { code: code(), scene: N.scene, mode: N.mode, bi: N.mode === 'play' ? N.bi : null, t: N.mode === 'play' ? +N.bt.toFixed(2) : null, land,
      env: [W && W.cine ? 'Кино' : 'Ретро', land ? 'горизонталь' : 'вертикаль', String(window.NS_LANG || 'ru').toUpperCase(), c[0] ? `${c[0]}×${c[1]}` : ''].filter(Boolean).join(' · '),
      perf: `${fps()} fps` + (worst ? ` · долгих кадров ${long.length}, худший ${worst[0]} мс @ ${worst[1]}` : ''),
      snd: `реплик без звука ${N.voiceMiss}, догнано ${N.voiceLate}`, errs: errs.slice(-4), flags: fl, dev: device(), ver: ver(), at: Date.now() };
  }
  // снимок экрана со всем, что поверх картинки (субтитры, кнопки, меню): html2canvas (MIT, lib/), без него — кадр холста и текст субтитров
  let h2cP = null;
  const h2c = () => h2cP || (h2cP = new Promise((ok, no) => { if (window.html2canvas) return ok(); const s = document.createElement('script'); s.src = 'lib/html2canvas.min.js'; s.onload = ok; s.onerror = no; document.head.appendChild(s); }));
  async function capture() {
    const w = stage.clientWidth, h = stage.clientHeight, sc = 960 / Math.max(w, h);
    // сцена стоит по центру через transform: translate(-50%, -50%) — в копии для снимка ставим её в угол без сдвига, иначе картинка уезжает вправо-вниз
    try { await h2c(); const c = await window.html2canvas(stage, { backgroundColor: '#000', scale: sc, logging: false, ignoreElements: el => el.id === 'qa' || el.id === 'qaP' || el.id === 'qaT',
      onclone: doc => { const s2 = doc.getElementById('stage'); if (s2) { s2.style.transform = 'none'; s2.style.left = '0'; s2.style.top = '0'; s2.style.width = w + 'px'; s2.style.height = h + 'px'; } } }); return c.toDataURL('image/jpeg', .8); }
    catch (e) {
      const cv = $('cv'), k = 960 / Math.max(cv.width, cv.height), c = document.createElement('canvas'); c.width = Math.round(cv.width * k); c.height = Math.round(cv.height * k);
      const g = c.getContext('2d'); g.drawImage(cv, 0, 0, c.width, c.height);
      const d = $('dlg'); if (d && !d.classList.contains('hide')) { const s = d.innerText.replace(/\s+/g, ' ').trim(); g.fillStyle = 'rgba(0,0,0,.75)'; g.fillRect(0, c.height * .8, c.width, c.height * .2); g.fillStyle = '#fff'; g.font = '600 20px Arial'; wrap(g, s, c.width - 40).slice(0, 6).forEach((l, i) => g.fillText(l, 20, c.height * .8 + 30 + i * 26)); }
      return c.toDataURL('image/jpeg', .8);
    }
  }
  function wrap(g, s, w) { const out = []; for (const para of String(s).split('\n')) { let line = ''; for (const word of para.split(' ')) { const t = line ? line + ' ' + word : word; if (g.measureText(t).width > w && line) { out.push(line); line = word; } else line = t; } out.push(line); } return out; }
  const wasPaused = () => !$('pz').classList.contains('hide');   // открыто меню паузы — после отчёта оно остаётся
  async function bug() {
    if (open) return; open = true;
    const b = info(), inG = IN.includes(N.mode); if (inG) N.pause(true);
    toast('Снимок…', 1500);
    b.img = await capture();
    panel(b, inG);
  }
  function close(inG) { const P = $('qaP'); if (P) P.remove(); open = false; if (inG && !wasPaused()) N.pause(false); if (N.mode === 'menu') N.back(); }
  function panel(b, inG) {
    const P = document.createElement('div'); P.id = 'qaP';
    P.innerHTML = `<div class="box"><img src="${b.img}" alt=""><div><div class="cd">${esc(b.code)}</div><div class="env">${esc(b.env)} · ${esc(b.perf)}</div>
      <div class="tags">${TAGS.map(t => `<button data-t="${t}">${t}</button>`).join('')}</div>
      <textarea id="qaN" placeholder="Что не так? Можно не писать — хватит снимка"></textarea>
      <div class="row"><button class="go" id="qaS">Сохранить и дальше</button></div>
      <div class="row"><button id="qaA">Сохранить и отправить (${bugs.length + 1})</button><button id="qaX">Отмена</button></div></div></div>`;
    stage.appendChild(P);
    for (const ev of ['pointerdown', 'click', 'keydown', 'keyup']) P.addEventListener(ev, stop);
    P.querySelector('.tags').onclick = e => { const t = e.target.closest('button'); if (t) t.classList.toggle('on'); };
    P.addEventListener('keydown', e => { if (e.key === 'Escape') { e.preventDefault(); $('qaX').click(); } else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); $('qaS').click(); } });   // ПК: Esc — отмена, Ctrl+Enter — сохранить
    setTimeout(() => { const n = $('qaN'); if (n && matchMedia('(pointer: fine)').matches) n.focus(); }, 50);   // на ПК сразу можно печатать
    const take = () => { b.tags = [...P.querySelectorAll('.tags .on')].map(x => x.dataset.t); b.note = $('qaN').value.trim(); return small(b.img).then(im => { b.img = im; bugs.push(b); keep(); }); };
    $('qaS').onclick = () => take().then(() => { close(inG); toast(`Сохранено. Отмечено: ${bugs.length}`); });
    $('qaA').onclick = () => take().then(() => prepare(P, inG));
    $('qaX').onclick = () => close(inG);
  }
  function small(src) { return loadImg(src).then(im => { const k = Math.min(1, (im.width > im.height ? 960 : 540) / im.width), c = document.createElement('canvas'); c.width = Math.round(im.width * k); c.height = Math.round(im.height * k); c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); return c.toDataURL('image/jpeg', .72); }); }   // в память браузера — поменьше
  const loadImg = src => new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = src; });
  // отправка: картинки готовятся заранее, «Поделиться» — сразу по нажатию (Safari разрешает его только прямо из нажатия)
  async function prepare(P, inG) {
    if (!P) { if (open) return; open = true; P = document.createElement('div'); P.id = 'qaP'; stage.appendChild(P); for (const ev of ['pointerdown', 'click', 'keydown', 'keyup']) P.addEventListener(ev, stop); }
    P.innerHTML = `<div class="box"><div class="cd">Отправка: ${bugs.length}</div><div class="msg" id="qaM">Готовлю картинку…</div></div>`;
    const files = [], name = 'hush-check-' + stamp();
    for (let i = 0; i < bugs.length; i += 6) { const bl = await sheet(bugs.slice(i, i + 6), i); files.push(new File([bl], `${name}${bugs.length > 6 ? '-' + (i / 6 + 1) : ''}.jpg`, { type: 'image/jpeg' })); }
    const txt = report(), canShare = !!(navigator.canShare && navigator.canShare({ files })), phone = matchMedia('(pointer: coarse)').matches, share1 = canShare && phone;
    const shareGo = () => navigator.share({ files, text: txt, title: 'Hush Till Six — проверка' }).then(() => toast('Отправлено. Список можно очистить'), e => { if (!e || e.name !== 'AbortError') download(files, txt); });
    P.innerHTML = `<div class="box"><div class="cd">Отправка: ${bugs.length}</div>
      <div class="msg">${share1 ? 'Нажми «Поделиться» и выбери, куда отправить: чат с Claude, Telegram…' : 'Картинка скачается в «Загрузки», текст отчёта — в буфер обмена. Перетащи картинку в чат с Claude (или нажми скрепку и выбери её).'}</div>
      <div class="row"><button class="go" id="qaG">${share1 ? 'Поделиться' : 'Скачать картинку'}</button></div>
      ${canShare ? `<div class="row"><button id="qaG2">${share1 ? 'Скачать картинку' : 'Поделиться'}</button></div>` : ''}
      <div class="row"><button id="qaC">Очистить список</button><button id="qaX">Закрыть</button></div></div>`;
    $('qaG').onclick = () => share1 ? shareGo() : download(files, txt);
    if ($('qaG2')) $('qaG2').onclick = () => share1 ? download(files, txt) : shareGo();
    $('qaC').onclick = () => { bugs = []; keep(); toast('Список очищен'); close(inG); };
    $('qaX').onclick = () => close(inG);
  }
  function download(files, txt) {
    for (const f of files) { const a = document.createElement('a'); a.href = URL.createObjectURL(f); a.download = f.name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 5000); }
    if (navigator.clipboard) navigator.clipboard.writeText(txt).then(() => toast('Картинка скачана, текст отчёта скопирован'), () => toast('Картинка скачана'));
    else toast('Картинка скачана');
  }
  const two = n => String(n).padStart(2, '0');
  const stamp = (d = new Date()) => `${d.getFullYear()}${two(d.getMonth() + 1)}${two(d.getDate())}-${two(d.getHours())}${two(d.getMinutes())}`;
  const when = ts => { const d = new Date(ts); return `${two(d.getDate())}.${two(d.getMonth() + 1)} ${two(d.getHours())}:${two(d.getMinutes())}`; };
  const flagStr = f => { const k = Object.keys(f || {}); return k.length ? JSON.stringify(f) : '{}'; };
  function report() {
    const b0 = bugs[0] || {};
    return [`Hush Till Six — проверка · ${when(Date.now())} · ${b0.ver || ver()}`, `Устройство: ${b0.dev || device()}`,
      ...bugs.map((b, i) => `${i + 1}) ${b.code} · ${b.env}${b.tags && b.tags.length ? ' · [' + b.tags.join(', ') + ']' : ''}${b.note ? ' — ' + b.note : ''} · ${b.perf} · ${b.snd}${b.errs.length ? ' · ошибки: ' + b.errs.join(' | ') : ''} · флаги ${flagStr(b.flags)}`)].join('\n');
  }
  // картинка отчёта: шапка, потом каждая ошибка — снимок и всё, что про неё известно
  async function sheet(list, i0) {
    const W = 1080, pad = 24, col = 470, LH = 34, F = '600 25px Arial, sans-serif', FB = '800 30px "Courier New", monospace';
    const g0 = document.createElement('canvas').getContext('2d');
    const ims = await Promise.all(list.map(b => loadImg(b.img).catch(() => null)));
    const blocks = list.map((b, k) => {
      const im = ims[k], land = !!im && im.width > im.height, iw = im ? (land ? W - 2 * pad : col) : 0, ih = im ? Math.round(iw * im.height / im.width) : 0, tw = land || !im ? W - 2 * pad : W - col - 3 * pad;
      g0.font = F; const rows = [[FB, '#ffd36b', `${i0 + k + 1}. ${b.code}`]]; for (const l of wrap(g0, b.env + ' · ' + when(b.at), tw)) rows.push([F, '#9aa0a8', l]);
      if (b.tags && b.tags.length) rows.push([F, '#ff6b6b', b.tags.join(' · ')]);
      if (b.note) for (const l of wrap(g0, b.note, tw)) rows.push(['700 27px Arial, sans-serif', '#ffffff', l]);
      for (const l of wrap(g0, b.perf, tw)) rows.push([F, '#c8ccd2', l]);
      rows.push([F, '#c8ccd2', b.snd]);
      for (const e of b.errs || []) for (const l of wrap(g0, '⚠ ' + e, tw).slice(0, 3)) rows.push([F, '#ff9a6b', l]);
      for (const l of wrap(g0, 'флаги ' + flagStr(b.flags), tw).slice(0, 4)) rows.push([F, '#7d838b', l]);
      const th = rows.length * LH;
      return { b, im, land, iw, ih, tw, rows, h: (land || !im ? ih + (im ? pad : 0) + th : Math.max(ih, th)) + 2 * pad };
    });
    const head = 150, H = head + blocks.reduce((s, x) => s + x.h, 0);
    const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d');
    g.fillStyle = '#101114'; g.fillRect(0, 0, W, H);
    g.fillStyle = '#e0303a'; g.fillRect(0, 0, W, 8);
    g.fillStyle = '#fff'; g.font = '900 40px Arial, sans-serif'; g.fillText('HUSH TILL SIX — проверка', pad, 64);
    g.fillStyle = '#9aa0a8'; g.font = F; g.fillText(`${when(Date.now())} · ${list.length} из ${bugs.length} · ${(list[0] && list[0].ver) || ver()}`, pad, 102); g.fillText((list[0] && list[0].dev) || device(), pad, 134);
    let y = head;
    for (const x of blocks) {
      g.fillStyle = 'rgba(255,255,255,.08)'; g.fillRect(pad, y, W - 2 * pad, 2); y += pad;
      if (x.im) g.drawImage(x.im, pad, y, x.iw, x.ih);
      let ty = x.land || !x.im ? y + (x.im ? x.ih + pad : 0) : y, tx = x.land || !x.im ? pad : pad * 2 + col;
      for (const [f, col2, s] of x.rows) { g.font = f; g.fillStyle = col2; g.fillText(s, tx, ty + LH - 8); ty += LH; }
      y += x.h - pad;
    }
    return new Promise(r => c.toBlob(r, 'image/jpeg', .86));
  }
  // список сцен (из меню)
  function list() {
    const S = window.STORY, tim = N.tim || {}; let tm = '';
    const rows = Object.entries(S.scenes).map(([id, sc]) => {
      if (sc.time) tm = sc.time;
      const b0 = (sc.beats || []).find(b => (b.say && b.say.length) || b.text) || {}, k = (b0.say || [])[0], th = sc.then || {};
      const line = (k && tim[k] && tim[k].t) || b0.text || (th.shift ? 'час смены ' + th.shift : th.explore ? 'обход' : th.talk ? 'разговор' : '');
      return { id, tm: sc.time || tm, line: String(line).replace(/^—\s*/, '').slice(0, 90), g: id.startsWith('over_') ? 'Смерти' : id.startsWith('sh_') ? 'Часы смены' : 'Сюжет' };
    });
    const ui = $('ui');
    ui.innerHTML = `<div class="screen sub qa-list"><h1>СЦЕНЫ</h1><input id="qlQ" placeholder="поиск: id или слова" autocomplete="off"><div id="qlL"></div><button class="btn ghost" id="qlB">Назад</button></div>`;
    const L = $('qlL'), draw = q => { L.innerHTML = ['Сюжет', 'Часы смены', 'Смерти'].map(g => { const r = rows.filter(x => x.g === g && (!q || (x.id + ' ' + x.line).toLowerCase().includes(q))); return r.length ? `<h3>${g}</h3>` + r.map(x => `<button data-id="${x.id}"><b>${esc(x.tm)}</b><i>${esc(x.id)}</i><span>${esc(x.line)}</span></button>`).join('') : ''; }).join('') || '<p>Ничего не нашлось</p>'; };
    draw('');
    $('qlQ').oninput = e => draw(e.target.value.trim().toLowerCase());
    $('qlQ').onkeydown = e => e.stopPropagation();
    L.onclick = e => { const b = e.target.closest('button[data-id]'); if (b) { e.stopPropagation(); N.begin(b.dataset.id); } };
    $('qlB').onclick = e => { e.stopPropagation(); N.back(); };
  }
  function menuHook() {
    const nav = document.querySelector('#ui .mlist'); if (!nav || nav.querySelector('.qa-m')) return;
    const a = document.createElement('button'); a.className = 'qa-m'; a.textContent = '🐞 Сцены (проверка)'; a.onclick = e => { e.stopPropagation(); list(); }; nav.appendChild(a);
    if (bugs.length) { const s = document.createElement('button'); s.className = 'qa-m'; s.textContent = `Отправить ошибки (${bugs.length})`; s.onclick = e => { e.stopPropagation(); prepare(null, false); }; nav.appendChild(s); }
  }
  { const k = $('keys'); if (k) { const d = document.createElement('div'); d.innerHTML = '<kbd>B</kbd>баг (проверка)'; k.appendChild(d); } }   // на ПК: B (или И) — отметить ошибку
  window.NS_QA = { menu: menuHook, bug, list, get bugs() { return bugs; }, code, info };
  if (N.mode === 'menu') menuHook();
})();
