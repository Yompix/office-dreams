// Сборка Hush Till Six для Android без Android Studio и Gradle: WebView-приложение, игра целиком внутри (assets/game).
//   node tools/android/build.js [--out ПАПКА]   → ПАПКА/HushTillSix-v<версия>.aab (для Google Play Console) и HushTillSix-v<версия>.apk (поставить на телефон)
//   node tools/android/build.js --webview-debug   → то же, но страницу видят Appium и chrome://inspect (проверка на телефонах BrowserStack; в Play не загружать)
//   node tools/android/build.js --new-key ФАЙЛ.p12   → новый ключ загрузки (upload key) для Google Play; пароль — в ФАЙЛ.p12.pass рядом (chmod 600)
// Ключ подписи берётся из окружения: ANDROID_KEYSTORE (путь к .p12) или ANDROID_KEYSTORE_B64 (сам файл в base64 — секрет облачной среды),
// пароль — ANDROID_KEYSTORE_PASS или файл <ключ>.pass. Ключ и пароль в репозиторий НЕ класть (.gitignore: *.p12, *.jks, *.keystore).
// Инструменты Google (build-tools 36.1: aapt2, d8, apksigner; платформа Android 16 = API 36; bundletool 1.18.3 из Google Maven) скачиваются
// один раз в ~/.cache/hush-android и сверяются по контрольным суммам. Нужны Java 17+ (javac, jarsigner) и python3 (иконки — tools/android/assets.py).
// Версия — из tools/desktop/package.json (как у ПК-версии), номер сборки: 1.2.5 → 10205 (каждая следующая загрузка в Play должна быть больше).
// Google Play с 31.08.2026 принимает только targetSdk 36 (Android 16) и выше; minSdk 26 (Android 8): WebGL 2 есть везде, где есть обновляемый WebView.
const fs = require('fs'), path = require('path'), os = require('os'), crypto = require('crypto'), { execFileSync } = require('child_process');
const ROOT = path.join(__dirname, '../..'), GAME = path.join(ROOT, 'public/games/nightshift'), HERE = __dirname;
const args = process.argv.slice(2), arg = k => (args.includes(k) ? args[args.indexOf(k) + 1] : null);
const PKG = 'com.yompix.hushtillsix', VER = require(path.join(ROOT, 'tools/desktop/package.json')).version;
const CODE = +(process.env.ANDROID_VERSION_CODE || VER.split('.').reduce((a, x, i) => a + (+x) * [10000, 100, 1][i], 0));
const MIN_SDK = 26, TARGET_SDK = 36, WEBDBG = args.includes('--webview-debug');   // --webview-debug — только APK для проверки на BrowserStack, не для Play
const OUT = path.resolve(arg('--out') || path.join(HERE, 'out'));
const CACHE = path.join(os.homedir(), '.cache/hush-android');
const REPO = 'https://dl.google.com/android/repository/', GMAVEN = 'https://dl.google.com/dl/android/maven2/', CENTRAL = 'https://maven-central.storage-download.googleapis.com/maven2/';   // зеркало Maven Central у Google (repo1 часто отвечает 429)
// sha1 — из официального списка пакетов Google (repository2-3.xml); sha256 у jar — посчитаны при первой загрузке (29 окт 2026)
const SDK = [{ zip: 'build-tools_r36.1_linux.zip', sha1: '936a0d6bd5ae3e2118a7567dddbc95ff67ed46e9', dir: 'build-tools' },
  { zip: 'platform-36_r02.zip', sha1: '2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576', dir: 'platform' }];
const JARS = [
  [GMAVEN + 'com/android/tools/build/bundletool/1.18.3/bundletool-1.18.3.jar'], [GMAVEN + 'com/android/tools/build/aapt2-proto/7.3.0-alpha07-8248216/aapt2-proto-7.3.0-alpha07-8248216.jar'],
  [CENTRAL + 'com/google/guava/guava/32.0.1-jre/guava-32.0.1-jre.jar'], [CENTRAL + 'com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar'],
  [CENTRAL + 'com/google/protobuf/protobuf-java/3.22.3/protobuf-java-3.22.3.jar'], [CENTRAL + 'com/google/protobuf/protobuf-java-util/3.22.3/protobuf-java-util-3.22.3.jar'],
  [CENTRAL + 'com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'], [CENTRAL + 'com/google/dagger/dagger/2.28.3/dagger-2.28.3.jar'], [CENTRAL + 'javax/inject/javax.inject/1/javax.inject-1.jar'],
  [CENTRAL + 'com/google/auto/value/auto-value-annotations/1.6.2/auto-value-annotations-1.6.2.jar'], [CENTRAL + 'com/google/errorprone/error_prone_annotations/2.3.1/error_prone_annotations-2.3.1.jar'],
  [CENTRAL + 'org/bitbucket/b_c/jose4j/0.9.5/jose4j-0.9.5.jar'], [CENTRAL + 'org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar']];
// что из папки игры не нужно в приложении: картинки для соцсетей и страницы, режим проверки владельца и его снимки экрана, манифест веб-приложения
const SKIP = /^(og\.(jpg|webp)|cover\.webp|manifest\.json|privacy\.html|qa\.js|lib\/html2canvas\.min\.js|apple-touch-icon\.png|icon-maskable-.*\.png|favicon.*)$/;

const run = (cmd, a, o = {}) => { try { return execFileSync(cmd, a, Object.assign({ stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 1 << 28 }, o)).toString(); }
  catch (e) { const t = String(e.stderr || '') + String(e.stdout || ''); throw new Error(path.basename(cmd) + ' ' + a.slice(0, 4).join(' ') + ' → ' + t.replace(/Picked up JAVA_TOOL_OPTIONS[^\n]*\n/, '').slice(-2500)); } };
const hash = (f, alg) => crypto.createHash(alg).update(fs.readFileSync(f)).digest('hex');
function fetchTo(url, dst) { if (fs.existsSync(dst)) return; fs.mkdirSync(path.dirname(dst), { recursive: true }); run('curl', ['-sfL', '--retry', '4', '--retry-delay', '5', '--max-time', '600', '-o', dst + '.part', url]); fs.renameSync(dst + '.part', dst); }
function tools() {
  for (const s of SDK) {
    const z = path.join(CACHE, s.zip), d = path.join(CACHE, s.dir);
    fetchTo(REPO + s.zip, z); if (hash(z, 'sha1') !== s.sha1) throw new Error('контрольная сумма не совпала: ' + s.zip);
    if (!fs.existsSync(d)) { const tmp = d + '.tmp'; fs.rmSync(tmp, { recursive: true, force: true }); fs.mkdirSync(tmp, { recursive: true }); run('unzip', ['-q', z, '-d', tmp]);
      const [inner] = fs.readdirSync(tmp); fs.renameSync(path.join(tmp, inner), d); fs.rmSync(tmp, { recursive: true, force: true }); }
  }
  const jars = JARS.map(([u]) => { const f = path.join(CACHE, 'jars', path.basename(u)); fetchTo(u, f); return f; });
  const lock = path.join(CACHE, 'jars.sha256'), sums = jars.map(f => path.basename(f) + ' ' + hash(f, 'sha256')).join('\n');
  if (!fs.existsSync(lock)) fs.writeFileSync(lock, sums); else if (fs.readFileSync(lock, 'utf8') !== sums) throw new Error('jar изменились с прошлой сборки: ' + lock);
  const bt = path.join(CACHE, 'build-tools');
  return { aapt2: path.join(bt, 'aapt2'), d8: path.join(bt, 'lib/d8.jar'), apksigner: path.join(bt, 'lib/apksigner.jar'), jar: path.join(CACHE, 'platform/android.jar'), lambda: path.join(bt, 'core-lambda-stubs.jar'), cp: jars.join(':') };
}

function newKey(file) {   // ключ загрузки: RSA 4096, 30 лет; пароль — случайный, в файл рядом
  if (fs.existsSync(file)) throw new Error('уже есть: ' + file);
  const pw = crypto.randomBytes(24).toString('base64url');
  run('keytool', ['-genkeypair', '-keystore', file, '-storetype', 'PKCS12', '-alias', 'upload', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '11000',
    '-dname', 'CN=Hush Till Six, O=Yompix Games', '-storepass:env', 'KSP', '-keypass:env', 'KSP'], { env: Object.assign({}, process.env, { KSP: pw }) });
  fs.writeFileSync(file + '.pass', pw + '\n', { mode: 0o600 }); fs.chmodSync(file, 0o600);
  console.log('ключ загрузки:', file, '(пароль —', file + '.pass)');
}

function keystore(tmp) {
  let ks = process.env.ANDROID_KEYSTORE || '';
  if (!ks && process.env.ANDROID_KEYSTORE_B64) { ks = path.join(tmp, 'upload.p12'); fs.writeFileSync(ks, Buffer.from(process.env.ANDROID_KEYSTORE_B64, 'base64'), { mode: 0o600 }); }
  if (!ks) ks = path.join(os.homedir(), '.android-upload/hush-upload.p12');
  if (!fs.existsSync(ks)) throw new Error('нет ключа подписи: ANDROID_KEYSTORE / ANDROID_KEYSTORE_B64 или node tools/android/build.js --new-key ' + ks);
  const pw = (process.env.ANDROID_KEYSTORE_PASS || (fs.existsSync(ks + '.pass') ? fs.readFileSync(ks + '.pass', 'utf8') : '')).trim();
  if (!pw) throw new Error('нет пароля ключа: ANDROID_KEYSTORE_PASS или ' + ks + '.pass');
  const pwFile = path.join(tmp, 'ks.pass'); fs.writeFileSync(pwFile, pw, { mode: 0o600 });   // bundletool читает пароль из файла — не в командной строке
  return { ks, pw, pwFile };
}

function copyGame(dst) {
  let n = 0;
  (function walk(rel) {
    for (const e of fs.readdirSync(path.join(GAME, rel), { withFileTypes: true })) {
      const r = rel ? rel + '/' + e.name : e.name;
      if (e.name.startsWith('.') || e.name.startsWith('_') || SKIP.test(r)) continue;
      if (e.isDirectory()) { walk(r); continue; }
      fs.mkdirSync(path.join(dst, rel), { recursive: true }); fs.copyFileSync(path.join(GAME, r), path.join(dst, r)); n++;
    }
  })('');
  // ссылки страницы на манифест веб-приложения и значки — в приложении не нужны
  const idx = path.join(dst, 'index.html');
  fs.writeFileSync(idx, fs.readFileSync(idx, 'utf8').replace(/<link rel="manifest"[^>]*>\n?/, '').replace(/<link rel="apple-touch-icon"[^>]*>\n?/, '').replace(/<link rel="icon"[^>]*>\n?/g, ''));
  return n;
}

function zipDir(dir, zip) { fs.rmSync(zip, { force: true }); run('zip', ['-q', '-r', '-X', zip, '.'], { cwd: dir }); }

function build() {
  const T = tools(); fs.mkdirSync(OUT, { recursive: true });
  const B = fs.mkdtempSync(path.join(os.tmpdir(), 'hush-android-')); const K = keystore(B);
  try {
    // 1) ресурсы: значки, тема, название → proto-формат (для AAB)
    if (!fs.existsSync(path.join(HERE, 'res/mipmap-xxxhdpi/ic_launcher_fg.png'))) run('python3', [path.join(HERE, 'assets.py')]);
    run(T.aapt2, ['compile', '--dir', path.join(HERE, 'res'), '-o', path.join(B, 'res.zip')]);
    run(T.aapt2, ['link', '--proto-format', '-o', path.join(B, 'linked.zip'), '-I', T.jar, '--manifest', path.join(HERE, 'AndroidManifest.xml'),
      '--min-sdk-version', String(MIN_SDK), '--target-sdk-version', String(TARGET_SDK), '--version-code', String(CODE), '--version-name', VER, '--auto-add-overlay', path.join(B, 'res.zip')]);
    // 2) код: Java 8 → dex
    const cls = path.join(B, 'classes'); fs.mkdirSync(cls);
    let srcDir = path.join(HERE, 'src');
    if (WEBDBG) { srcDir = path.join(B, 'src'); fs.cpSync(path.join(HERE, 'src'), srcDir, { recursive: true });   // проверочная сборка: отладка WebView включена
      const m = path.join(srcDir, 'com/yompix/hushtillsix/MainActivity.java'); fs.writeFileSync(m, fs.readFileSync(m, 'utf8').replace('WEB_DEBUG = false', 'WEB_DEBUG = true')); }
    const src = []; (function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const f = path.join(d, e.name); if (e.isDirectory()) walk(f); else if (f.endsWith('.java')) src.push(f); } })(srcDir);
    run('javac', ['-nowarn', '-Xlint:-options', '-source', '8', '-target', '8', '-bootclasspath', T.jar + ':' + T.lambda, '-classpath', T.jar, '-d', cls, ...src]);
    const classFiles = []; (function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const f = path.join(d, e.name); if (e.isDirectory()) walk(f); else if (f.endsWith('.class')) classFiles.push(f); } })(cls);
    const dex = path.join(B, 'dex'); fs.mkdirSync(dex);
    run('java', ['-cp', T.d8, 'com.android.tools.r8.D8', '--release', '--min-api', String(MIN_SDK), '--lib', T.jar, '--output', dex, ...classFiles]);
    // 3) модуль base: manifest/, res/, resources.pb, dex/, assets/game/
    const mod = path.join(B, 'base'); fs.mkdirSync(path.join(mod, 'manifest'), { recursive: true }); fs.mkdirSync(path.join(mod, 'dex'));
    run('unzip', ['-q', path.join(B, 'linked.zip'), '-d', path.join(B, 'linked')]);
    fs.renameSync(path.join(B, 'linked/AndroidManifest.xml'), path.join(mod, 'manifest/AndroidManifest.xml'));
    fs.renameSync(path.join(B, 'linked/resources.pb'), path.join(mod, 'resources.pb'));
    fs.renameSync(path.join(B, 'linked/res'), path.join(mod, 'res'));
    fs.copyFileSync(path.join(dex, 'classes.dex'), path.join(mod, 'dex/classes.dex'));
    const files = copyGame(path.join(mod, 'assets/game'));
    zipDir(mod, path.join(B, 'base.zip'));
    // 4) AAB: уже сжатое (mp3, glb, картинки) кладём без сжатия — быстрее открывается
    const cfg = path.join(B, 'BundleConfig.json');
    fs.writeFileSync(cfg, JSON.stringify({ compression: { uncompressedGlob: ['assets/**.mp3', 'assets/**.glb', 'assets/**.webp', 'assets/**.png', 'assets/**.jpg'] } }));
    const name = 'HushTillSix-v' + VER + (WEBDBG ? '-webdebug' : ''), aab = path.join(OUT, name + '.aab'), apk = path.join(OUT, name + '.apk');
    for (const f of [aab, apk]) fs.rmSync(f, { force: true });
    const BT = ['-cp', T.cp, 'com.android.tools.build.bundletool.BundleToolMain'];
    run('java', [...BT, 'build-bundle', '--modules=' + path.join(B, 'base.zip'), '--output=' + aab, '--config=' + cfg]);
    run('java', [...BT, 'validate', '--bundle=' + aab]);
    // 5) подпись AAB ключом загрузки (Play App Signing: Google переподписывает своим ключом)
    run('jarsigner', ['-sigalg', 'SHA256withRSA', '-digestalg', 'SHA-256', '-keystore', K.ks, '-storetype', 'PKCS12', '-storepass:env', 'KSP', aab, 'upload'], { env: Object.assign({}, process.env, { KSP: K.pw }) });
    // 6) APK для телефона — из того же AAB (universal: все экраны и языки в одном файле), подписан тем же ключом
    const apks = path.join(B, 'u.apks');
    run('java', [...BT, 'build-apks', '--bundle=' + aab, '--output=' + apks, '--mode=universal', '--aapt2=' + T.aapt2,
      '--ks=' + K.ks, '--ks-key-alias=upload', '--ks-pass=file:' + K.pwFile, '--key-pass=file:' + K.pwFile]);
    run('unzip', ['-q', '-o', apks, 'universal.apk', '-d', B]); fs.renameSync(path.join(B, 'universal.apk'), apk);
    // 7) проверки: подпись, состав, разрешения, SDK
    const ver = run('java', ['-jar', T.apksigner, 'verify', '--verbose', '--print-certs', apk]);
    const js = run('jarsigner', ['-verify', aab]);
    const badge = run(T.aapt2, ['dump', 'badging', apk]);
    const info = { package: (badge.match(/package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/) || []).slice(1), sdk: (badge.match(/sdkVersion:'(\d+)'/) || [])[1], target: (badge.match(/targetSdkVersion:'(\d+)'/) || [])[1],
      permissions: [...badge.matchAll(/uses-permission: name='([^']+)'/g)].map(m => m[1]), apkV2: /Verified using v2 scheme \(APK Signature Scheme v2\): true/.test(ver), aabSigned: /jar verified/.test(js),
      gameFiles: files, aabMB: +(fs.statSync(aab).size / 1048576).toFixed(1), apkMB: +(fs.statSync(apk).size / 1048576).toFixed(1),
      cert: (ver.match(/Signer #1 certificate SHA-256 digest: ([0-9a-f]+)/) || [])[1] };
    console.log(JSON.stringify(info, null, 1));
    console.log('AAB:', aab); console.log('APK:', apk);
  } finally { fs.rmSync(B, { recursive: true, force: true }); }
}

if (args.includes('--new-key')) newKey(path.resolve(arg('--new-key')));
else build();
