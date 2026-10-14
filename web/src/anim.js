/* ===== АНІМАЦІЇ: піксельні руки для КНП, частинки й струс для ігор ===== */

/* руки малюються з простих фігур на сітці 26×34 (зап'ясток знизу), далі автоматично — контур, світло й тінь */
const HAND = (() => {
  const W = 26, H = 34;
  const shapes = {
    r(rect, line, m) {
      rect(4, 11, 21, 25); [[4, 11], [21, 11], [4, 25], [21, 25]].forEach(([x, y]) => (m[y][x] = 0));
      for (const x of [9, 13, 17]) rect(x, 11, x, 15, 3);
      rect(5, 16, 20, 16, 3);
      rect(3, 18, 13, 22); m[18][3] = 0; m[22][3] = 0; rect(4, 18, 12, 18, 3); rect(13, 19, 13, 22, 3);
    },
    p(rect, line, m) {
      rect(5, 13, 20, 26);
      [[5, 9], [9, 4], [13, 3], [17, 6]].forEach(([x, top]) => { rect(x, top, x + 2, 14); m[top][x] = 0; m[top][x + 2] = 0; rect(x, 12, x + 2, 12, 3); });
      line(6, 23, 1, 15, 4); rect(6, 20, 6, 24, 3);
    },
    s(rect, line, m) {
      rect(5, 15, 20, 26); [[5, 15], [20, 15]].forEach(([x, y]) => (m[y][x] = 0));
      line(10, 17, 6, 3, 4); line(15, 17, 19, 3, 4);
      rect(13, 15, 13, 19, 3); rect(14, 19, 20, 19, 3);
      rect(4, 20, 12, 23); m[20][4] = 0; m[23][4] = 0; rect(5, 20, 11, 20, 3); rect(12, 21, 12, 23, 3);
    },
  };
  function mask(k) {
    const m = Array.from({ length: H }, () => Array(W).fill(0));
    const rect = (x0, y0, x1, y1, v = 1) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) if (y >= 0 && y < H && x >= 0 && x < W) m[y][x] = v; };
    const line = (x0, y0, x1, y1, t, v = 1) => { const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2; for (let i = 0; i <= n; i++) { const x = Math.round(x0 + ((x1 - x0) * i) / n - t / 2), y = Math.round(y0 + ((y1 - y0) * i) / n - t / 2); rect(x, y, x + t - 1, y + t - 1, v); } };
    rect(8, 24, 17, 28); // зап'ясток
    shapes[k](rect, line, m);
    rect(6, 28, 19, 33, 2); // манжета
    return m;
  }
  const PAL = { me: ["#27e6ff", "#0a8aa8", "#bff8ff"], op: ["#ff2fb0", "#a0106a", "#ffb0e4"], gold: ["#ffc933", "#c88a00", "#fff3a0"] };
  const cache = {};
  function url(k, side) {
    const id = k + side; if (cache[id]) return cache[id];
    const m = mask(k), [cu, cuS, cuH] = PAL[side] || PAL.me;
    const at = (x, y) => (y >= 0 && y < H && x >= 0 && x < W ? m[y][x] : 0);
    cache[id] = pxCanvas(W + 2, H + 2, (g) => {
      for (let y = -1; y <= H; y++) for (let x = -1; x <= W; x++) {
        const v = at(x, y); let c = null;
        if (!v) { if (at(x + 1, y) || at(x - 1, y) || at(x, y + 1) || at(x, y - 1)) c = "#1a0630"; }
        else if (v === 3) c = "#b8664a";
        else if (v === 2) c = !at(x + 1, y) || !at(x, y + 1) ? cuS : !at(x - 1, y) || !at(x, y - 1) || y === 29 ? cuH : y === 31 ? cuS : cu;
        else c = !at(x + 1, y) || !at(x, y + 1) || at(x, y + 1) === 3 ? "#e09062" : !at(x - 1, y) || !at(x, y - 1) || at(x, y - 1) === 3 ? "#fff0de" : "#ffcf9e";
        if (c) { g.fillStyle = c; g.fillRect(x + 1, y + 1, 1, 1); }
      }
    }, 4).toDataURL();
    return cache[id];
  }
  return { url };
})();

/* частинки з центру елемента; струс елемента; короткий спалах */
function burstAt(el, n, cols) { if (!el) return; const r = el.getBoundingClientRect(); burst(n, r.left + r.width / 2, r.top + r.height / 2, cols); }
function shakeEl(el, cls) { if (!el) return; cls = cls || "shk"; el.classList.remove(cls); void el.offsetWidth; el.classList.add(cls); setTimeout(() => el.classList.remove(cls), 600); }
function flashAt(el, col) {
  if (!el) return; const r = el.getBoundingClientRect(), f = document.createElement("i");
  f.className = "flash"; f.style.cssText = `left:${r.left + r.width / 2}px;top:${r.top + r.height / 2}px;--fc:${col || "#ffffff"}`;
  document.body.appendChild(f); setTimeout(() => f.remove(), 600);
}
