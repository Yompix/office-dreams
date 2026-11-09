// мостик между игрой и окном: игра видит window.nsDesktop (engine.js: DESK) — выйти, весь экран, звук в фоне, достижения Steam
// Сохранения: всё ns1_* из localStorage раз в 15 с и при выходе копируется в файл (main.js: cloud/save.json — его переносит Steam Cloud).
// При запуске: файл новее того, что эта копия игры сама записала последней (пришёл с другого ПК или профиль браузера пуст), — сохранения берутся из него.
const { contextBridge, ipcRenderer } = require('electron');
const arg = k => { const a = process.argv.find(s => s.startsWith(`--ns-${k}=`)); return a ? a.slice(k.length + 6) : ''; };
let full = arg('full') === '1', bg = arg('bg') === '1';
ipcRenderer.on('ns-full-state', (e, on) => { full = !!on; });

const MARK = 'ns1__cloud';   // когда эта копия игры в последний раз писала файл сохранений
const snap = () => { const o = {}; for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k && k.startsWith('ns1_') && k !== MARK) o[k] = localStorage.getItem(k); } return o; };
try {
  const f = JSON.parse(ipcRenderer.sendSync('ns-cloud-get') || 'null');
  if (f && f.ls && typeof f.ls === 'object' && +f.t > (+localStorage.getItem(MARK) || 0)) {
    for (const k of Object.keys(snap())) localStorage.removeItem(k);
    for (const k in f.ls) if (k.startsWith('ns1_') && typeof f.ls[k] === 'string') localStorage.setItem(k, f.ls[k]);
    localStorage.setItem(MARK, String(+f.t));
  }
} catch {}
let last = null;
function push(sync) {
  try { const o = snap(), s = JSON.stringify(o); if (s === last) return; last = s;
    const t = Date.now(), msg = JSON.stringify({ t, ls: o }); localStorage.setItem(MARK, String(t));
    sync ? ipcRenderer.sendSync('ns-cloud-put', msg) : ipcRenderer.send('ns-cloud-put', msg); } catch {}
}
setInterval(() => push(false), 15000);
window.addEventListener('pagehide', () => push(true)); window.addEventListener('beforeunload', () => push(true));
window.addEventListener('DOMContentLoaded', () => push(false));

contextBridge.exposeInMainWorld('nsDesktop', {
  quit: () => { push(true); ipcRenderer.send('ns-quit'); },
  full: on => { full = typeof on === 'boolean' ? on : !full; ipcRenderer.send('ns-full', full); },
  isFull: () => full,
  bgSound: () => bg,
  setBgSound: on => { bg = !!on; ipcRenderer.send('ns-bg', bg); },
  ach: id => ipcRenderer.send('ns-ach', String(id)),
  steam: arg('steam') === '1',
  platform: process.platform,
});
