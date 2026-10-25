// Hush Till Six для ПК: окно Electron вместо браузера. Файлы игры лежат в app.asar/game и отдаются по своему протоколу app://game/
// (у страницы постоянный адрес — сохранения и настройки живут в профиле игрока: %APPDATA%/Hush Till Six, в Linux ~/.config/Hush Till Six).
// Окно: в первый раз во весь экран; дальше — как оставил (размер, место, весь экран — settings.json). F, F11 или Alt+Enter — окно/весь экран.
// Окно в фоне — без звука (если в настройках игры «Звук в фоне» выключен). Достижения — Steam, если игру запустил Steam или рядом с .exe лежит steam_appid.txt;
// без Steam игра идёт как обычно. Сохранения копируются в cloud/save.json (для Steam Cloud, см. tools/desktop/steam/STEAM.md).
// NS_WINDOWED=1 — запуск в окне, NS_DEVTOOLS=1 — инструменты разработчика, NS_USERDATA=папка — свой профиль, NS_QUERY='cs=0.5' — параметры страницы (автопроверка).
const { app, BrowserWindow, Menu, ipcMain, net, protocol, screen, session, shell } = require('electron');
const fs = require('fs'), path = require('path'), { pathToFileURL } = require('url');
const GAME = path.join(__dirname, 'game');

protocol.registerSchemesAsPrivileged([{ scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }]);
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');   // звук сразу, без «нажми, чтобы включить звук»
app.commandLine.appendSwitch('force_high_performance_gpu');                     // ноутбук с двумя видеокартами — играет на дискретной
app.commandLine.appendSwitch('ignore-gpu-blocklist');                           // 3D и на старых драйверах
app.commandLine.appendSwitch('enable-unsafe-swiftshader');                      // нет рабочей видеокарты — 3D программно (игра сама перейдёт на «Ретро»); в окне только наша игра, чужих страниц нет
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.setName('Hush Till Six');
if (process.env.NS_USERDATA) app.setPath('userData', path.resolve(process.env.NS_USERDATA));
if (!app.requestSingleInstanceLock()) app.quit();
const UD = app.getPath('userData'), CFG = path.join(UD, 'settings.json'), ACHF = path.join(UD, 'achievements.json'), SAVE = path.join(UD, 'cloud', 'save.json');
const readJson = (f, d) => { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return d; } };
function writeAtomic(f, s) { try { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f + '.tmp', s); fs.renameSync(f + '.tmp', f); } catch (e) { console.log('запись', f, e.message); } }
const cfg = Object.assign({ full: true, bg: false, win: null, max: false }, readJson(CFG, {}));   // full — весь экран; bg — звук, когда окно в фоне
const saveCfg = () => writeAtomic(CFG, JSON.stringify(cfg));

/* ---------- Steam (steamworks.js, MIT): только в сборке для Steam (build.js --steam) и только если её запустил Steam ---------- */
const STEAM = (() => {
  let id = process.env.SteamAppId || '';
  try { if (!id) id = fs.readFileSync(path.join(path.dirname(process.execPath), 'steam_appid.txt'), 'utf8').trim(); } catch {}
  if (!/^[1-9]\d*$/.test(id)) return null;
  const note = s => { console.log(s); writeAtomic(path.join(UD, 'steam.log'), new Date().toISOString() + ' ' + s + '\n'); };   // steam.log — что вышло со Steam (для поддержки и автопроверки)
  try { const sw = require('steamworks.js'), c = sw.init(+id); try { sw.electronEnableSteamOverlay(); } catch {} note('Steam: ок, приложение ' + id); return c; }
  catch (e) { note('Steam: без Steam (' + String(e && e.message || e).slice(0, 160) + ')'); return null; }
})();
// достижения: копия у себя (achievements.json) — «все концовки/смерти» считаются по ней; полученные без Steam дотягиваются при запуске со Steam
const ACH = ['END_INSIDE', 'END_SISTER', 'END_DAWN', 'END_NEWENTRY', ...Array.from({ length: 14 }, (_, i) => 'DEATH_' + String(i + 1).padStart(2, '0')), 'ALL_ENDINGS', 'ALL_DEATHS', 'NIGHT_DONE'];
const got = new Set(readJson(ACHF, []).filter(a => ACH.includes(a)));
function steamAch(a) { if (!STEAM) return; try { if (!STEAM.achievement.isActivated(a)) STEAM.achievement.activate(a); } catch (e) { console.log('Steam: достижение', a, e.message); } }
function ach(id) {
  if (!ACH.includes(id)) return;
  const n = got.size; got.add(id);
  const all = p => ACH.filter(a => a.startsWith(p)).every(a => got.has(a));
  if (all('END_')) got.add('ALL_ENDINGS'); if (all('DEATH_')) got.add('ALL_DEATHS');
  if (got.size !== n) writeAtomic(ACHF, JSON.stringify([...got]));
  for (const a of got) steamAch(a);
}
if (STEAM) for (const a of got) steamAch(a);

/* ---------- окно ---------- */
let win = null, saveT = 0;
const hush = () => { if (win && !win.isDestroyed()) win.webContents.setAudioMuted(!cfg.bg && !win.isFocused()); };   // окно в фоне и «Звук в фоне» выключен — без звука
function place() {   // прошлое место и размер окна — если тот монитор ещё на месте; иначе по центру
  const b = cfg.win, d = b ? screen.getDisplayMatching(b).workArea : null;
  if (!b || !(b.width >= 640 && b.height >= 360)) return { width: 1600, height: 900, useContentSize: true };
  const w = Math.min(b.width, d.width), h = Math.min(b.height, d.height);
  const seen = Math.min(b.x + w, d.x + d.width) - Math.max(b.x, d.x) > 160 && Math.min(b.y + h, d.y + d.height) - Math.max(b.y, d.y) > 120;
  return seen ? { x: b.x, y: b.y, width: w, height: h } : { width: w, height: h };
}
function remember() { if (!win || win.isDestroyed()) return; cfg.win = win.getNormalBounds(); cfg.max = win.isMaximized(); saveCfg(); }
function create() {
  const full = !process.env.NS_WINDOWED && cfg.full !== false;
  win = new BrowserWindow(Object.assign(place(), {
    minWidth: 960, minHeight: 540, show: false, fullscreen: full, backgroundColor: '#050607', title: 'Hush Till Six', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, nodeIntegration: false,
      backgroundThrottling: false, spellcheck: false, devTools: !app.isPackaged || !!process.env.NS_DEVTOOLS,
      additionalArguments: [`--ns-full=${full ? 1 : 0}`, `--ns-bg=${cfg.bg ? 1 : 0}`, `--ns-steam=${STEAM ? 1 : 0}`] },   // начальное состояние для preload (игра показывает его в настройках)
  }));
  if (cfg.max && !full && !process.env.NS_WINDOWED) win.maximize();
  win.once('ready-to-show', () => win.show());
  const wc = win.webContents, outside = u => { if (/^https:\/\//.test(u)) shell.openExternal(u); };   // ссылки (страница игры, Steam) — в обычном браузере игрока
  wc.setWindowOpenHandler(({ url }) => { outside(url); return { action: 'deny' }; });
  wc.on('will-navigate', (e, url) => { if (!url.startsWith('app://game/')) { e.preventDefault(); outside(url); } });   // и брошенный в окно файл никуда не уводит
  wc.on('before-input-event', (e, i) => {
    if (i.type !== 'keyDown') return;
    if (i.key === 'F11' || (i.key === 'Enter' && i.alt)) { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
    else if ((i.control || i.meta) && /^(\+|-|=|0|_)$/.test(i.key)) e.preventDefault();   // без увеличения страницы: интерфейс игры сам подстраивается под окно
  });
  wc.on('did-finish-load', () => { wc.setZoomFactor(1); wc.setVisualZoomLevelLimits(1, 1).catch(() => {}); });   // и без «щипка» на тачпаде
  win.on('page-title-updated', e => e.preventDefault());
  const fullState = on => { cfg.full = on; saveCfg(); if (!wc.isDestroyed()) wc.send('ns-full-state', on); };
  win.on('enter-full-screen', () => fullState(true)); win.on('leave-full-screen', () => fullState(false));
  const later = () => { clearTimeout(saveT); saveT = setTimeout(remember, 600); };
  win.on('resize', later); win.on('move', later); win.on('maximize', later); win.on('unmaximize', later); win.on('close', remember);
  win.on('blur', hush); win.on('focus', hush);   // в фоне — тихо (игра сама встаёт на паузу, pad.js)
  win.loadURL('app://game/index.html' + (process.env.NS_QUERY ? '?' + process.env.NS_QUERY : ''));
}

ipcMain.on('ns-quit', () => app.quit());
ipcMain.on('ns-full', (e, on) => { if (win) win.setFullScreen(typeof on === 'boolean' ? on : !win.isFullScreen()); });
ipcMain.on('ns-bg', (e, on) => { cfg.bg = !!on; saveCfg(); hush(); });
ipcMain.on('ns-ach', (e, id) => ach(String(id)));
// сохранения игры (localStorage ns1_*) — копия в файле: Steam Cloud переносит её на другой ПК; preload берёт её, если она новее (cloud/save.json)
ipcMain.on('ns-cloud-get', e => { try { e.returnValue = fs.readFileSync(SAVE, 'utf8'); } catch { e.returnValue = ''; } });
ipcMain.on('ns-cloud-put', (e, s) => { if (typeof s === 'string' && s.length < 8e6) writeAtomic(SAVE, s); e.returnValue = 1; });

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  protocol.handle('app', req => {
    const { host, pathname } = new URL(req.url), f = path.join(GAME, decodeURIComponent(pathname)), rel = path.relative(GAME, f);
    if (host !== 'game' || !rel || rel.startsWith('..') || path.isAbsolute(rel)) return new Response('not found', { status: 404 });
    return net.fetch(pathToFileURL(f).toString());
  });
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(perm === 'fullscreen'));   // игре не нужны ни микрофон, ни камера, ни уведомления
  create();
});
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.on('window-all-closed', () => app.quit());
