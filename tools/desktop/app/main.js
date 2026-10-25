// Hush Till Six для ПК: окно Electron вместо браузера. Файлы игры лежат в app.asar/game и отдаются по своему протоколу app://game/
// (у страницы постоянный адрес — сохранения и настройки живут в профиле игрока: %APPDATA%/Hush Till Six).
// Окно сразу во весь экран; F, F11 или Alt+Enter — окно/полный экран. NS_WINDOWED=1 — запуск в окне, NS_DEVTOOLS=1 — инструменты разработчика.
const { app, BrowserWindow, Menu, ipcMain, net, protocol, session, shell } = require('electron');
const path = require('path'), { pathToFileURL } = require('url');
const GAME = path.join(__dirname, 'game');

protocol.registerSchemesAsPrivileged([{ scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }]);
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');   // звук сразу, без «нажми, чтобы включить звук»
app.commandLine.appendSwitch('force_high_performance_gpu');                     // ноутбук с двумя видеокартами — играет на дискретной
app.commandLine.appendSwitch('ignore-gpu-blocklist');                           // 3D и на старых драйверах
app.commandLine.appendSwitch('enable-unsafe-swiftshader');                      // нет рабочей видеокарты — 3D программно (игра сама перейдёт на «Ретро»); в окне только наша игра, чужих страниц нет
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.setName('Hush Till Six');
if (!app.requestSingleInstanceLock()) app.quit();

let win = null;
function create() {
  win = new BrowserWindow({
    width: 1600, height: 900, minWidth: 960, minHeight: 540, useContentSize: true, show: false,
    fullscreen: !process.env.NS_WINDOWED, backgroundColor: '#050607', title: 'Hush Till Six', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, nodeIntegration: false,
      backgroundThrottling: false, spellcheck: false, devTools: !app.isPackaged || !!process.env.NS_DEVTOOLS },
  });
  win.once('ready-to-show', () => win.show());
  const outside = u => { if (/^https:\/\//.test(u)) shell.openExternal(u); };   // ссылки (страница игры, Steam) — в обычном браузере игрока
  win.webContents.setWindowOpenHandler(({ url }) => { outside(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('app://game/')) { e.preventDefault(); outside(url); } });
  win.webContents.on('before-input-event', (e, i) => {
    if (i.type === 'keyDown' && (i.key === 'F11' || (i.key === 'Enter' && i.alt))) { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
  });
  win.on('page-title-updated', e => e.preventDefault());
  win.loadURL('app://game/index.html');
}

ipcMain.on('ns-quit', () => app.quit());
ipcMain.on('ns-full', (e, on) => { if (win) win.setFullScreen(typeof on === 'boolean' ? on : !win.isFullScreen()); });

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
