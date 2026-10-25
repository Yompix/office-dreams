// Сборка Hush Till Six для ПК — программа без браузера (Electron): node tools/desktop/build.js [win] [linux] [--steam] [--test] [--no-setup] [--out ПАПКА]
//   win     → ПАПКА/Hush Till Six/Hush Till Six.exe, архив HushTillSix-Windows-v<версия>.zip и установщик «Hush Till Six Setup <версия>.exe» (по умолчанию)
//   linux   → ПАПКА/hush-till-six-linux/hush-till-six и архив HushTillSix-Linux-v<версия>.zip (Steam Deck идёт и на Windows-сборке через Proton)
//   --steam → сборка для Steam (со steamworks.js: достижения): ПАПКА/steam-windows или steam-linux — это содержимое депо (steam/STEAM.md), без архивов
//   --test  → Linux-сборка с открытым портом отладки (fuse) — для автопроверки (tools/desktop/check.js), не для игроков
//   --no-setup → без установщика (он собирается только на Linux: makensis из electron-builder-binaries)
// Перед сборкой: (cd tools/desktop && npm ci). Electron (версия в package.json) и NSIS скачиваются один раз в ~/.cache/hush-desktop и сверяются по SHA-256.
// Игра — копия public/games/nightshift (полная версия, без демо) в resources/app.asar/game; иконка и подписи в .exe — resedit; лишнее Electron выключено fuses.
// В корне сборки — лицензии: Electron (LICENSE), Chromium (LICENSES.chromium.html), игры (CREDITS.txt, licenses/).
const fs = require('fs'), path = require('path'), os = require('os'), crypto = require('crypto'), { execFileSync } = require('child_process');
const asar = require('@electron/asar'), ResEdit = require('resedit'), { flipFuses, FuseVersion, FuseV1Options } = require('@electron/fuses');
const ROOT = path.join(__dirname, '../..'), GAME = path.join(ROOT, 'public/games/nightshift'), PKG = require('./package.json');
const args = process.argv.slice(2), TEST = args.includes('--test'), STEAM = args.includes('--steam'), SETUP = !args.includes('--no-setup') && !TEST && !STEAM;
const OUT = path.resolve(args.includes('--out') ? args[args.indexOf('--out') + 1] : path.join(__dirname, 'out'));
const targets = args.filter(a => a === 'win' || a === 'linux'); if (!targets.length) targets.push(TEST ? 'linux' : 'win');
const E = PKG.electron, VER = PKG.version, CACHE = path.join(os.homedir(), '.cache/hush-desktop');
const NAME = 'Hush Till Six', LOCALES = /^(en-US|en-GB|ru|uk|es|es-419|pt-BR|pt-PT|de)\.pak$/;   // язык меню Chromium (почти не видно) — остальные 50 локалей не нужны
// что из папки игры не нужно в ПК-версии: картинки для соцсетей и страницы, режим проверки, манифест веб-приложения
const SKIP = /^(og\.(jpg|webp)|cover\.webp|manifest\.json|qa\.js|apple-touch-icon\.png|icon-maskable-.*\.png)$/;
// NSIS 3.0.4.1 из electron-builder-binaries — тот же архив, что берёт electron-builder (его SHA-512 совпадает с прописанным там)
const NSIS = { ver: '3.0.4.1', sha: '9877df902530f96357d13a7a31ae2b9df67f48b11ffc9a1700a7c961574ec5fa' };
const SW = path.join(__dirname, 'node_modules/steamworks.js');
fs.mkdirSync(OUT, { recursive: true });

const sha = f => crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
function curl(url, dest) { execFileSync('curl', ['-fsSL', '--retry', '4', '-o', dest, url], { stdio: 'inherit' }); }
function fetchChecked(url, file, want) {
  if (!fs.existsSync(file) || sha(file) !== want) { console.log('скачиваю', path.basename(file)); curl(url, file); }
  if (sha(file) !== want) throw new Error('контрольная сумма не сошлась: ' + file);
  return file;
}
function electronZip(plat) {
  fs.mkdirSync(CACHE, { recursive: true });
  const name = `electron-v${E}-${plat}-x64.zip`, sums = path.join(CACHE, `SHASUMS256-${E}.txt`), base = `https://github.com/electron/electron/releases/download/v${E}/`;
  if (!fs.existsSync(sums)) curl(base + 'SHASUMS256.txt', sums);
  const want = (fs.readFileSync(sums, 'utf8').split('\n').find(l => l.endsWith('*' + name) || l.endsWith(' ' + name)) || '').split(' ')[0];
  if (!want) throw new Error('нет контрольной суммы для ' + name);
  return fetchChecked(base + name, path.join(CACHE, name), want);
}
function copyGame(dst) {   // игра как есть, кроме лишнего; index.html без ссылки на манифест
  fs.mkdirSync(dst, { recursive: true });
  for (const e of fs.readdirSync(GAME, { withFileTypes: true })) {
    if (SKIP.test(e.name)) continue;
    const s = path.join(GAME, e.name), d = path.join(dst, e.name);
    if (e.isDirectory()) fs.cpSync(s, d, { recursive: true }); else fs.copyFileSync(s, d);
  }
  const idx = path.join(dst, 'index.html');
  fs.writeFileSync(idx, fs.readFileSync(idx, 'utf8').replace(/<link rel="manifest"[^>]*>\n?/, '').replace(/<link rel="apple-touch-icon"[^>]*>\n?/, ''));
}
async function appAsar(dst, win) {
  const st = fs.mkdtempSync(path.join(OUT, '.app-'));
  for (const f of ['main.js', 'preload.js']) fs.copyFileSync(path.join(__dirname, 'app', f), path.join(st, f));
  fs.copyFileSync(path.join(GAME, 'icon-512.png'), path.join(st, 'icon.png'));
  fs.writeFileSync(path.join(st, 'package.json'), JSON.stringify({ name: 'hush-till-six', productName: NAME, version: VER, main: 'main.js', description: NAME }, null, 1));
  copyGame(path.join(st, 'game'));
  if (STEAM) {   // steamworks.js (MIT) + библиотека Steam только своей платформы; .node/.dll/.so — рядом с app.asar (app.asar.unpacked): их грузит система
    const sub = win ? 'win64' : 'linux64', d = path.join(st, 'node_modules/steamworks.js');
    fs.mkdirSync(path.join(d, 'dist', sub), { recursive: true });
    for (const f of ['index.js', 'package.json', 'LICENSE']) fs.copyFileSync(path.join(SW, f), path.join(d, f));
    for (const f of fs.readdirSync(path.join(SW, 'dist', sub))) if (!/\.lib$/.test(f)) fs.copyFileSync(path.join(SW, 'dist', sub, f), path.join(d, 'dist', sub, f));
  }
  await asar.createPackageWithOptions(st, dst, { unpack: '*.{node,dll,so}' });
  fs.rmSync(st, { recursive: true, force: true });
}
function icoPath() {   // .ico из icon-512.png: 16…256 px
  const ico = path.join(CACHE, 'hts.ico');
  execFileSync('python3', ['-c', `from PIL import Image; Image.open(${JSON.stringify(path.join(GAME, 'icon-512.png'))}).convert('RGBA').save(${JSON.stringify(ico)}, sizes=[(16,16),(20,20),(24,24),(32,32),(40,40),(48,48),(64,64),(128,128),(256,256)])`]);
  return ico;
}
function exeBrand(exe) {   // иконка и подписи «Свойства → Подробно» в .exe
  const pe = ResEdit.NtExecutable.from(fs.readFileSync(exe), { ignoreCert: true }), res = ResEdit.NtExecutableResource.from(pe);
  const icons = ResEdit.Data.IconFile.from(fs.readFileSync(icoPath())).icons.map(i => i.data);
  for (const g of ResEdit.Resource.IconGroupEntry.fromEntries(res.entries)) ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, g.id, g.lang, icons);
  const vi = ResEdit.Resource.VersionInfo.fromEntries(res.entries)[0], [a, b, c] = VER.split('.').map(Number);
  const lang = vi.getAllLanguagesForStringValues()[0] || { lang: 1033, codepage: 1200 };
  vi.setStringValues(lang, { ProductName: NAME, FileDescription: NAME, CompanyName: 'Office Dreams', LegalCopyright: '© 2026 Office Dreams',
    InternalName: NAME, OriginalFilename: NAME + '.exe', ProductVersion: VER, FileVersion: VER });
  vi.removeStringValue(lang, 'SquirrelAwareVersion');   // метка установщика Squirrel — у нас его нет
  vi.setFileVersion(a, b, c, 0, lang.lang); vi.setProductVersion(a, b, c, 0, lang.lang);
  vi.outputToResourceEntries(res.entries); res.outputResource(pe);
  fs.writeFileSync(exe, Buffer.from(pe.generate()));
}
function makensis() {   // NSIS: скачать один раз, распаковать 7-Zip из npm (7zip-bin)
  const dir = path.join(CACHE, 'nsis-' + NSIS.ver), bin = path.join(dir, 'linux/makensis');
  if (!fs.existsSync(bin)) {
    const z = fetchChecked(`https://github.com/electron-userland/electron-builder-binaries/releases/download/nsis-${NSIS.ver}/nsis-${NSIS.ver}.7z`, path.join(CACHE, `nsis-${NSIS.ver}.7z`), NSIS.sha);
    const za = require('7zip-bin').path7za; fs.chmodSync(za, 0o755);
    execFileSync(za, ['x', '-y', '-o' + dir, z], { stdio: 'ignore' }); fs.chmodSync(bin, 0o755);
  }
  return { bin, dir };
}
function setup(src) {   // установщик Windows (installer.nsi): в профиль игрока без прав администратора, ярлыки, удаление
  if (process.platform !== 'linux') { console.log('установщик: собирается только на Linux — пропускаю'); return null; }
  const { bin, dir } = makensis(), art = fs.mkdtempSync(path.join(OUT, '.nsis-')), exe = path.join(OUT, `${NAME} Setup ${VER}.exe`);
  execFileSync('python3', [path.join(__dirname, 'installer_art.py'), path.join(GAME, 'fonts'), art]);   // картинка слева на приветствии и в шапке — из шрифта игры
  fs.rmSync(exe, { force: true });
  execFileSync(bin, ['-V2', '-INPUTCHARSET', 'UTF8', `-DSRC=${src}`, `-DOUT=${exe}`, `-DVER=${VER}`, `-DICON=${icoPath()}`, `-DSIDE=${art}/side.bmp`, `-DHEAD=${art}/head.bmp`, path.join(__dirname, 'installer.nsi')],
    { env: Object.assign({}, process.env, { NSISDIR: dir }), stdio: 'inherit' });
  fs.rmSync(art, { recursive: true, force: true });
  return exe;
}
const README = win => [`${NAME} ${VER}`, '',
  win ? `Запуск: ${NAME}.exe. Сохранения: %APPDATA%\\${NAME}.` : `Запуск: ./hush-till-six. Сохранения: ~/.config/${NAME}.`,
  'F11 или Alt+Enter — окно / весь экран. Пробел — дальше, 1–4 — выбор, Esc — пауза.',
  'Геймпад: A — дальше и нажать, B — назад, Start — пауза, крестовина — выбор кнопки, LB/RB — камеры, X — записать, Y — пульт.', '',
  win ? `Run ${NAME}.exe. Saves: %APPDATA%\\${NAME}.` : `Run ./hush-till-six. Saves: ~/.config/${NAME}.`,
  'F11 or Alt+Enter toggles fullscreen. Space — next, 1–4 — choose, Esc — pause.',
  'Gamepad: A — next / press, B — back, Start — pause, D-pad — move between buttons, LB/RB — cameras, X — log, Y — panel.', '',
  'Credits and licenses: CREDITS.txt, licenses/, LICENSE (Electron), LICENSES.chromium.html.', ''].join(win ? '\r\n' : '\n');
async function build(plat) {
  const win = plat === 'win', dir = path.join(OUT, STEAM ? 'steam-' + (win ? 'windows' : 'linux') : win ? NAME : 'hush-till-six-linux');
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  execFileSync('unzip', ['-q', electronZip(win ? 'win32' : 'linux'), '-d', dir]);
  for (const f of fs.readdirSync(path.join(dir, 'locales'))) if (!LOCALES.test(f)) fs.rmSync(path.join(dir, 'locales', f));
  fs.rmSync(path.join(dir, 'resources/default_app.asar'), { force: true });
  await appAsar(path.join(dir, 'resources/app.asar'), win);
  const exe = path.join(dir, win ? NAME + '.exe' : 'hush-till-six');
  fs.renameSync(path.join(dir, win ? 'electron.exe' : 'electron'), exe);
  if (win) exeBrand(exe); else fs.chmodSync(exe, 0o755);
  await flipFuses(exe, { version: FuseVersion.V1, [FuseV1Options.RunAsNode]: false, [FuseV1Options.EnableNodeOptionsEnvironmentVariable]: false,
    [FuseV1Options.EnableNodeCliInspectArguments]: TEST, [FuseV1Options.OnlyLoadAppFromAsar]: true });
  fs.copyFileSync(path.join(GAME, 'CREDITS.txt'), path.join(dir, 'CREDITS.txt'));
  fs.cpSync(path.join(GAME, 'licenses'), path.join(dir, 'licenses'), { recursive: true });
  if (STEAM) fs.copyFileSync(path.join(SW, 'LICENSE'), path.join(dir, 'licenses/MIT-steamworks.js.txt'));
  fs.writeFileSync(path.join(dir, 'README.txt'), README(win));
  const mb = f => (fs.statSync(f).size / 1048576).toFixed(0) + ' МБ';
  let msg = `${plat}${STEAM ? ' (Steam)' : ''}: ${exe} (${mb(exe)})`;
  if (!TEST && !STEAM) {
    const zip = path.join(OUT, `HushTillSix-${win ? 'Windows' : 'Linux'}-v${VER}.zip`);
    fs.rmSync(zip, { force: true }); execFileSync('zip', ['-qr9y', zip, path.basename(dir)], { cwd: OUT }); msg += `, архив ${zip} (${mb(zip)})`;
    if (win && SETUP) { const s = setup(dir); if (s) msg += `, установщик ${s} (${mb(s)})`; }
  }
  console.log(msg);
}
(async () => { for (const t of targets) await build(t); })().catch(e => { console.error(e); process.exit(1); });
