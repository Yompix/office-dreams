/* ===== АНІМАЦІЇ ІНТЕРФЕЙСУ: живий фон, відблиски, іскри, лічильники, зарахування монет ===== */
const FX = (function () {
  const calm = matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- живий фон за всім інтерфейсом ---------- */
  (function bg() {
    const b = document.createElement("div"); b.id = "bgfx";
    const stars = (n, sz) => { let s = []; for (let i = 0; i < n; i++) { const c = ["#ffffff", "#ffd6f2", "#c8f6ff", "#fff3a0"][i % 4]; s.push(`${Math.round(Math.random() * 520)}px ${Math.round(Math.random() * 1000) - 40}px 0 ${c}`); } return s.join(","); };
    b.innerHTML = `<i class="st" style="box-shadow:${stars(46)}"></i><i class="st s2" style="box-shadow:${stars(18)}"></i><div class="glow"></div><div class="grid"></div>`;
    $("app").prepend(b);
  })();

  /* ---------- монетки, що злітають над вивіскою в лобі ---------- */
  (function heroCoins() {
    const h = $("hero"); if (!h || calm) return;
    [[14, 7.5, 0, 10], [32, 9, -3.2, -8], [58, 8, -5.5, 12], [79, 10, -1.6, -10], [90, 8.5, -6.8, 6]].forEach(([x, d, dl, dx]) => {
      const f = document.createElement("i"); f.className = "fly";
      f.style.cssText = `left:${x}%;--d:${d}s;--dl:${dl}s;--x:${dx}px`; h.appendChild(f);
    });
  })();

  /* ---------- відблиск на кнопках і плитках (додається й до нових елементів) ---------- */
  const SHINE = ".tile,.qt,.gt,.bal,.meth button.on,.pack,.btn.pk,.btn.gr,.btn.cy,.btn.w:not(.dk),.spb";
  let decT = 0;
  function decorate() {
    decT = 0;
    $$(SHINE).forEach((el, i) => {
      if (el.querySelector(":scope>.shn")) return;
      const s = document.createElement("s"); s.className = "shn"; s.setAttribute("aria-hidden", "true");
      s.style.setProperty("--sd", (-(i * 0.73) % 5).toFixed(2) + "s"); el.appendChild(s);
    });
    countStats();
  }
  if (!calm) {
    new MutationObserver(() => { if (!decT) decT = requestAnimationFrame(decorate); }).observe(document.body, { childList: true, subtree: true });
    decorate();
  }

  /* ---------- іскри при натисканні ---------- */
  const TAP = ".btn,.tile,.qt,.gt,.pack,.chip,.meth button,.bal,.nb";
  document.addEventListener("pointerdown", (e) => {
    if (calm) return;
    const el = e.target.closest(TAP); if (!el || el.disabled) return;
    let c = getComputedStyle(el).getPropertyValue("--bc").trim();
    if (!c || c === "var(--ink)" || /^#1a0636|^#3a2590|^#2a1a70/i.test(c)) c = el.matches(".btn.pk,.nb") ? "#ff2fb0" : el.matches(".btn.cy") ? "#27e6ff" : el.matches(".btn.gr") ? "#3dff7a" : "#ffc933";
    spark(e.clientX, e.clientY, [c, "#ffffff"], el.matches(".tile,.gt") ? 14 : 9);
  }, { passive: true });
  function spark(x, y, cols, n) {
    for (let i = 0; i < n; i++) {
      const a = (Math.PI * 2 * i) / n + Math.random() * 0.4, v = 2.2 + Math.random() * 2.8;
      parts.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 1.5, s: 3 + (i % 2) * 2, c: cols[i % cols.length], life: 18 + Math.random() * 14 });
    }
    if (!fxRun) { fxRun = true; requestAnimationFrame(fxLoop); }
  }

  /* ---------- позначка «вхід у вкладку» — список з'являється каскадом ---------- */
  let enterT = 0, enterAt = 0;
  const _go = go;
  go = function (tab, opt) {
    const was = S.tab; _go(tab, opt);
    if (was === tab) return;
    const v = $("v-" + tab); if (!v) return;
    enterAt = Date.now(); clearTimeout(enterT); $$(".view.enter").forEach((x) => x.classList.remove("enter"));
    v.classList.add("enter"); enterT = setTimeout(() => v.classList.remove("enter"), 900);
  };

  /* ---------- лічильники у профілі рахують від нуля ---------- */
  const seen = {};
  function countStats() {
    $$(".stats .stat b").forEach((b, i) => {
      if (b.dataset.c) return; b.dataset.c = "1";
      const to = Number(b.textContent.replace(/[^\d−-]/g, "").replace("−", "-")) || 0;
      if (seen[i] === to || !to || Date.now() - enterAt > 1500) { seen[i] = to; return; }
      seen[i] = to; b.classList.add("cnt");
      const t0 = performance.now(), D = 900;
      const step = (t) => { const k = Math.min(1, (t - t0) / D), e = 1 - Math.pow(1 - k, 3); b.textContent = fmt(to * e); if (k < 1 && b.isConnected) requestAnimationFrame(step); };
      requestAnimationFrame(step);
    });
  }

  /* ---------- «онлайн» підстрибує, коли число змінюється ---------- */
  (function () {
    const o = $("onl"); if (!o) return; let last = o.textContent;
    new MutationObserver(() => { if (o.textContent !== last) { last = o.textContent; o.classList.remove("bump"); void o.offsetWidth; o.classList.add("bump"); } }).observe(o, { childList: true, characterData: true, subtree: true });
  })();

  /* ---------- зарахування монет: велика монета, промені, лічильник і монетки летять у баланс ---------- */
  const LBL = ["Монети зараховано!", "Монеты зачислены!", "Coins credited!"];
  const TAPL = ["торкніться, щоб продовжити", "нажмите, чтобы продолжить", "tap to continue"];
  const ov = document.createElement("div"); ov.id = "depfx"; document.body.appendChild(ov);
  let busy = false, target = 0, phase = 0, timers = [];
  const later = (f, ms) => timers.push(setTimeout(f, ms));
  const quiet = () => !S.match && !S.tblId && !S.jpOpen && !S.room && !$("sheet").classList.contains("on") && !$("modal").classList.contains("on") && !$("gate").classList.contains("on");

  function credit(d, to) {
    busy = true; target = to; phase = 1; timers.forEach(clearTimeout); timers = [];
    ov.innerHTML = `<div class="rays"></div><div class="ring"></div><div class="ring r2"></div><div class="dc"><i></i></div><div class="lbl2">${LBL[S.li]}</div><div class="amt"><span>+0</span></div><div class="tap">${TAPL[S.li]}</div>`;
    ov.classList.remove("out"); ov.classList.add("on");
    sfx.open(); hap("success");
    later(() => { sfx.big(); burst(60, innerWidth / 2, innerHeight * 0.4); }, 380);
    const a = ov.querySelector(".amt"), sp = a.firstChild, t0 = performance.now() + 350, D = Math.min(1600, 700 + Math.log10(d + 1) * 250);
    let lastTick = 0;
    const step = (t) => {
      if (phase !== 1) return;
      const k = Math.max(0, Math.min(1, (t - t0) / D)), e = 1 - Math.pow(1 - k, 3);
      sp.textContent = "+" + fmt(d * e);
      if (k > 0 && t - lastTick > 70 && k < 1) { lastTick = t; sfx.tick(); a.classList.remove("tick"); void a.offsetWidth; a.classList.add("tick"); }
      if (k < 1) requestAnimationFrame(step); else { sp.textContent = "+" + fmt(d); later(() => fly(d), 900); }
    };
    requestAnimationFrame(step);
  }
  ov.addEventListener("click", () => { if (phase === 1) { const sp = ov.querySelector(".amt span"); phase = 2; timers.forEach(clearTimeout); timers = []; fly(); } });

  function fly() {
    phase = 2;
    const dc = ov.querySelector(".dc"), from = dc ? dc.getBoundingClientRect() : { left: innerWidth / 2, top: innerHeight / 2, width: 0, height: 0 };
    const bal = $("bBal"), coinEl = bal.querySelector(".coin") || bal, to = coinEl.getBoundingClientRect();
    const fx0 = from.left + from.width / 2, fy0 = from.top + from.height / 2, tx = to.left + to.width / 2, ty = to.top + to.height / 2;
    ov.classList.add("out");
    const N = 14; let done = 0;
    for (let i = 0; i < N; i++) {
      const c = document.createElement("i"); c.className = "fcn"; document.body.appendChild(c);
      const sx = fx0 + (Math.random() - 0.5) * 90, sy = fy0 + (Math.random() - 0.5) * 90;
      const mx = (sx + tx) / 2 + (Math.random() - 0.5) * 220, my = Math.min(sy, ty) + (Math.random() * 0.4 + 0.1) * (sy - ty);
      const an = c.animate([
        { transform: `translate(${fx0}px,${fy0}px) scale(.4)`, opacity: 0 },
        { transform: `translate(${sx}px,${sy}px) scale(1.4)`, opacity: 1, offset: 0.18 },
        { transform: `translate(${mx}px,${my}px) scale(1.1)`, offset: 0.6 },
        { transform: `translate(${tx}px,${ty}px) scale(.55)`, opacity: 1 },
      ], { duration: 820 + Math.random() * 260, delay: i * 55, easing: "cubic-bezier(.45,0,.7,1)", fill: "both" });
      an.onfinish = () => {
        c.remove(); done++;
        bal.classList.remove("hit"); void bal.offsetWidth; bal.classList.add("hit"); sfx.chip(); if (done % 3 === 0) hap("light");
        if (done === 1) _setBal(target, true);
        if (done === N) finish(tx, ty);
      };
    }
  }
  function finish(x, y) {
    burst(28, x, y, ["#ffc933", "#fff3a0", "#ffffff"]); sfx.win(); hap("success");
    ov.classList.remove("on", "out"); ov.innerHTML = ""; phase = 0; busy = false;
    if (S.me && S.me.balance !== balShown) _setBal(S.me.balance, true);
    const w = document.querySelector("#v-wallet.on .wtop"); if (w) { w.classList.remove("hit"); void w.offsetWidth; w.classList.add("hit"); }
  }

  /* перехоплюємо оновлення балансу: якщо монет побільшало поза грою — показуємо свято */
  const _setBal = setBal;
  setBal = function (n, animate) {
    n = Math.round(n);
    if (busy) { target = n; if (S.me) S.me.balance = n; return; }
    const d = n - balShown;
    if (animate && d >= 1 && S.me && quiet() && !calm && !document.hidden) { S.me.balance = n; credit(d, n); return; }
    _setBal(n, animate);
  };
  /* старе «+N» у гаманці більше не потрібне — його замінює анімація */
  const _onMsg = onMsg;
  onMsg = function (m) { if (m && m.t === "bal") { setBal(m.balance, true); return; } _onMsg(m); };

  return { credit, spark, test: (d = 1500) => credit(d, (S.me ? S.me.balance : balShown) + d) };
})();
