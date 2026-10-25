// Сборка Hush Till Six для ПК — программа без браузера (Electron): node tools/desktop/build.js [win] [linux] [--test] [--out ПАПКА]
//   win   → ПАПКА/Hush Till Six/Hush Till Six.exe и ПАПКА/HushTillSix-Windows-v<версия>.zip (по умолчанию)
//   linux → ПАПКА/hush-till-six-linux/hush-till-six и zip (Steam Deck идёт и на Windows-сборке через Proton)
//   --test → Linux-сборка с открытым портом отладки (fuse) — для автопроверки (tools/desktop/check.js), не для игроков
// Перед сборкой: (cd tools/desktop && npm ci). Electron (версия в package.json) скачивается один раз в ~/.cache/hush-desktop и сверяется по SHA-256.
// Игра — копия public/games/nightshift (полная версия, без демо) в resources/app.asar/game; иконка и подписи в .exe — resedit; лишнее Electron выключено fuses.
const fs = require('fs'), path = require('path'), os = require('os'), crypto = require('crypto'), { execFileSync } = require('child_process');
const asar = require('@electron/asar'), ResEdit = require('resedit'), { flipFuses, FuseVersion, FuseV1Options } = require('@electron/fuses');
const ROOT = path.join(__dirname, '../..'), GAME = path.join(ROOT, 'public/games/nightshift'), PKG = require('./package.json');
const args = process.argv.slice(2), TEST = args.includes('--test');
const OUT = path.resolve(args.includes('--out') ? args[args.indexOf('--out') + 1] : path.join(__dirname, 'out'));
const targets = args.filter(a => a === 'win' || a === 'linux'); if (!targets.length) targets.push(TEST ? 'linux' : 'win');
const E = PKG.electron, VER = PKG.version, CACHE = path.join(os.homedir(), '.cache/hush-desktop');
const NAME = 'Hush Till Six', LOCALES = /^(en-US|en-GB|ru|uk|es|es-419|pt-BR|pt-PT|de)\.pak$/;   // язык меню Chromium (почти не видно) — остальные 50 локалей не нужны
// что из папки игры не нужно в ПК-версии: картинки для соцсетей и страницы, режим проверки, манифест веб-приложения
const SKIP = /^(og\.(jpg|webp)|cover\.webp|manifest\.json|qa\.js|apple-touch-icon\.png|icon-maskable-.*\.png)$/;

function curl(url, dest) { execFileSync('curl', ['-fsSL', '--retry', '4', '-o', dest, url], { stdio: 'inherit' }); }
function electronZip(plat) {
  fs.mkdirSync(CACHE, { recursive: true });
  const name = `electron-v${E}-${plat}-x64.zip`, zip = path.join(CACHE, name), sums = path.join(CACHE, `SHASUMS256-${E}.txt`), base = `https://github.com/electron/electron/releases/download/v${E}/`;
  if (!fs.existsSync(sums)) curl(base + 'SHASUMS256.txt', sums);
  const want = (fs.readFileSync(sums, 'utf8').split('\n').find(l => l.endsWith('*' + name) || l.endsWith(' ' + name)) || '').split(' ')[0];
  if (!want) throw new Error('нет контрольной суммы для ' + name);
  const sha = f => crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');
  if (!fs.existsSync(zip) || sha(zip) !== want) { console.log('скачиваю', name); curl(base + name, zip); }
  if (sha(zip) !== want) throw new Error('контрольная сумма не сошлась: ' + name);
  return zip;
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
async function appAsar(dst) {
  const st = fs.mkdtempSync(path.join(OUT, '.app-'));
  for (const f of ['main.js', 'preload.js']) fs.copyFileSync(path.join(__dirname, 'app', f), path.join(st, f));
  fs.copyFileSync(path.join(GAME, 'icon-512.png'), path.join(st, 'icon.png'));
  fs.writeFileSync(path.join(st, 'package.json'), JSON.stringify({ name: 'hush-till-six', productName: NAME, version: VER, main: 'main.js', description: NAME }, null, 1));
  copyGame(path.join(st, 'game'));
  await asar.createPackage(st, dst);
  fs.rmSync(st, { recursive: true, force: true });
}
function icoFile() {   // .ico из icon-512.png: 16…256 px
  const ico = path.join(CACHE, 'hts.ico');
  execFileSync('python3', ['-c', `from PIL import Image; Image.open(${JSON.stringify(path.join(GAME, 'icon-512.png'))}).convert('RGBA').save(${JSON.stringify(ico)}, sizes=[(16,16),(20,20),(24,24),(32,32),(40,40),(48,48),(64,64),(128,128),(256,256)])`]);
  return fs.readFileSync(ico);
}
function exeBrand(exe) {   // иконка и подписи «Свойства → Подробно» в .exe
  const pe = ResEdit.NtExecutable.from(fs.readFileSync(exe), { ignoreCert: true }), res = ResEdit.NtExecutableResource.from(pe);
  const icons = ResEdit.Data.IconFile.from(icoFile()).icons.map(i => i.data);
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
async function build(plat) {
  const win = plat === 'win', dir = path.join(OUT, win ? NAME : 'hush-till-six-linux');
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  execFileSync('unzip', ['-q', electronZip(win ? 'win32' : 'linux'), '-d', dir]);
  for (const f of fs.readdirSync(path.join(dir, 'locales'))) if (!LOCALES.test(f)) fs.rmSync(path.join(dir, 'locales', f));
  fs.rmSync(path.join(dir, 'resources/default_app.asar'), { force: true });
  await appAsar(path.join(dir, 'resources/app.asar'));
  const exe = path.join(dir, win ? NAME + '.exe' : 'hush-till-six');
  fs.renameSync(path.join(dir, win ? 'electron.exe' : 'electron'), exe);
  if (win) exeBrand(exe); else fs.chmodSync(exe, 0o755);
  await flipFuses(exe, { version: FuseVersion.V1, [FuseV1Options.RunAsNode]: false, [FuseV1Options.EnableNodeOptionsEnvironmentVariable]: false,
    [FuseV1Options.EnableNodeCliInspectArguments]: TEST, [FuseV1Options.OnlyLoadAppFromAsar]: true });
  fs.writeFileSync(path.join(dir, 'README.txt'), win
    ? `${NAME} ${VER}\r\nЗапуск: ${NAME}.exe. F11 / Alt+Enter — окно или весь экран. Сохранения: %APPDATA%\\${NAME}.\r\nRun ${NAME}.exe. F11 / Alt+Enter toggles fullscreen. Saves: %APPDATA%\\${NAME}.\r\n`
    : `${NAME} ${VER}\nЗапуск: ./hush-till-six. F11 — окно или весь экран. Сохранения: ~/.config/${NAME}.\n`);
  const zip = path.join(OUT, `HushTillSix-${win ? 'Windows' : 'Linux'}-v${VER}${TEST ? '-test' : ''}.zip`);
  if (!TEST) { fs.rmSync(zip, { force: true }); execFileSync('zip', ['-qr9y', zip, path.basename(dir)], { cwd: OUT }); }
  const mb = f => (fs.statSync(f).size / 1048576).toFixed(0) + ' МБ';
  console.log(`${plat}: ${exe} (${mb(exe)})${TEST ? '' : `, архив ${zip} (${mb(zip)})`}`);
}
(async () => { for (const t of targets) await build(t); })().catch(e => { console.error(e); process.exit(1); });
