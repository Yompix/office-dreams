/* ===== АНІМАЦІЇ ІНТЕРФЕЙСУ: іскри, каскадна поява, лічильники, зарахування монет ===== */
const FX = (function () {
  const calm = matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- іскри при натисканні ---------- */
  const TAP = ".btn,.tile,.qt,.gt,.pack,.chip,.meth button,.bal";
  document.addEventListener("pointerdown", (e) => {
    if (calm) return;
    const el = e.target.closest(TAP); if (!el || el.disabled) return;
    const c = el.matches(".btn.pk") ? "#d34dff" : el.matches(".btn.cy") ? "#2fd8ff" : el.matches(".btn.gr") ? "#5dff4a" : (getComputedStyle(el).getPropertyValue("--nc").trim() || "#ffc933");
    spark(e.clientX, e.clientY, [c, "#ffffff"], el.matches(".tile,.gt,.qt") ? 12 : 8);
  }, { passive: true });
  function spark(x, y, cols, n) {
    for (let i = 0; i < n; i++) {
      const a = (Math.PI * 2 * i) / n + Math.random() * 0.4, v = 2 + Math.random() * 2.6;
      parts.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 1.4, s: 3 + (i % 2) * 2, c: cols[i % cols.length], life: 16 + Math.random() * 12 });
    }
    if (!fxRun) { fxRun = true; requestAnimationFrame(fxLoop); }
  }

  /* ---------- вхід у вкладку: список з'являється каскадом ---------- */
  let enterT = 0, enterAt = 0;
  const _go = go;
  go = function (tab, opt) {
    const was = S.tab; _go(tab, opt);
    if (was === tab) return;
    const v = $("v-" + tab); if (!v) return;
    enterAt = Date.now(); clearTimeout(enterT); $$(".view.enter").forEach((x) => x.classList.remove("enter"));
    v.classList.add("enter"); enterT = setTimeout(() => v.classList.remove("enter"), 800);
  };

  /* ---------- лічильники у профілі рахують від нуля ---------- */
  const seen = {};
  function countStats() {
    $$(".stats .stat b").forEach((b, i) => {
      if (b.dataset.c) return; b.dataset.c = "1";
      const to = Number(b.textContent.replace(/[^\d−-]/g, "").replace("−", "-")) || 0;
      if (seen[i] === to || !to || Date.now() - enterAt > 1500) { seen[i] = to; return; }
      seen[i] = to; b.classList.add("cnt");
      const t0 = performance.now(), D = 800;
      const step = (t) => { const k = Math.min(1, (t - t0) / D), e = 1 - Math.pow(1 - k, 3); b.textContent = fmt(to * e); if (k < 1 && b.isConnected) requestAnimationFrame(step); };
      requestAnimationFrame(step);
    });
  }
  if (!calm) { const pb = $("pBody"); if (pb) new MutationObserver(countStats).observe(pb, { childList: true }); }

  /* ---------- зарахування монет: велика монета, лічильник, монетки летять у баланс ---------- */
  const LBL = ["Монети зараховано!", "Монеты зачислены!", "Coins credited!"];
  const TAPL = ["торкніться, щоб продовжити", "нажмите, чтобы продолжить", "tap to continue"];
  const ov = document.createElement("div"); ov.id = "depfx"; document.body.appendChild(ov);
  let busy = false, target = 0, phase = 0, timers = [];
  const later = (f, ms) => timers.push(setTimeout(f, ms));
  const quiet = () => !S.match && !S.tblId && !S.jpOpen && !S.room && !$("sheet").classList.contains("on") && !$("modal").classList.contains("on") && !$("gate").classList.contains("on");

  function credit(d, to) {
    busy = true; target = to; phase = 1; timers.forEach(clearTimeout); timers = [];
    ov.innerHTML = `<div class="rays"></div><div class="dc"><i></i></div><div class="lbl2">${LBL[S.li]}</div><div class="amt">+0</div><div class="tap">${TAPL[S.li]}</div>`;
    ov.classList.remove("out"); ov.classList.add("on");
    sfx.open(); hap("success");
    later(() => { sfx.big(); burst(50, innerWidth / 2, innerHeight * 0.4); }, 380);
    const a = ov.querySelector(".amt"), t0 = performance.now() + 350, D = Math.min(1500, 700 + Math.log10(d + 1) * 220);
    let lastTick = 0;
    const step = (t) => {
      if (phase !== 1) return;
      const k = Math.max(0, Math.min(1, (t - t0) / D)), e = 1 - Math.pow(1 - k, 3);
      a.textContent = "+" + fmt(d * e);
      if (k > 0 && k < 1 && t - lastTick > 80) { lastTick = t; sfx.tick(); }
      if (k < 1) requestAnimationFrame(step); else { a.textContent = "+" + fmt(d); later(fly, 900); }
    };
    requestAnimationFrame(step);
  }
  ov.addEventListener("click", () => { if (phase === 1) { timers.forEach(clearTimeout); timers = []; fly(); } });

  function fly() {
    if (phase !== 1) return; phase = 2;
    const dc = ov.querySelector(".dc"), from = dc.getBoundingClientRect();
    const bal = $("bBal"), to = (bal.querySelector(".coin") || bal).getBoundingClientRect();
    const fx0 = from.left + from.width / 2, fy0 = from.top + from.height / 2, tx = to.left + to.width / 2, ty = to.top + to.height / 2;
    ov.classList.add("out");
    const N = 12; let done = 0;
    for (let i = 0; i < N; i++) {
      const c = document.createElement("i"); c.className = "fcn"; document.body.appendChild(c);
      const sx = fx0 + (Math.random() - 0.5) * 80, sy = fy0 + (Math.random() - 0.5) * 80;
      const mx = (sx + tx) / 2 + (Math.random() - 0.5) * 200, my = Math.min(sy, ty) + (Math.random() * 0.4 + 0.1) * (sy - ty);
      const an = c.animate([
        { transform: `translate(${fx0}px,${fy0}px) scale(.4)`, opacity: 0 },
        { transform: `translate(${sx}px,${sy}px) scale(1.3)`, opacity: 1, offset: 0.18 },
        { transform: `translate(${mx}px,${my}px) scale(1.05)`, offset: 0.6 },
        { transform: `translate(${tx}px,${ty}px) scale(.55)`, opacity: 1 },
      ], { duration: 800 + Math.random() * 240, delay: i * 55, easing: "cubic-bezier(.45,0,.7,1)", fill: "both" });
      an.onfinish = () => {
        c.remove(); done++;
        bal.classList.remove("hit"); void bal.offsetWidth; bal.classList.add("hit"); sfx.chip();
        if (done === 1) _setBal(target, true);
        if (done === N) finish(tx, ty);
      };
    }
  }
  function finish(x, y) {
    burst(24, x, y, ["#ffc933", "#fff3a0", "#ffffff"]); sfx.win(); hap("success");
    ov.classList.remove("on", "out"); ov.innerHTML = ""; phase = 0; busy = false;
    if (S.me && S.me.balance !== balShown) _setBal(S.me.balance, true);
  }

  /* баланс: якщо монет побільшало поза грою — показуємо анімацію зарахування */
  const _setBal = setBal;
  setBal = function (n, animate) {
    n = Math.round(n);
    if (busy) { target = n; if (S.me) S.me.balance = n; return; }
    const d = n - balShown;
    if (animate && d >= 1 && S.me && quiet() && !calm && !document.hidden) { S.me.balance = n; credit(d, n); return; }
    _setBal(n, animate);
  };
  const _onMsg = onMsg;
  onMsg = function (m) { if (m && m.t === "bal") { setBal(m.balance, true); return; } _onMsg(m); };

  return { credit, spark, test: (d = 1500) => credit(d, (S.me ? S.me.balance : balShown) + d) };
})();
