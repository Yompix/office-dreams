/* Build: concatenates src/* into
 *   dist/index.html     — the game page served by the server (Telegram Mini App; auto online/demo)
 *   dist/debug.html     — same page with a test hook (window.__ev) for browser tests
 *   dist/artifact.html  — demo-only fragment for the Artifact preview (no <html>/<head>)
 */
const fs = require('fs');
const path = require('path');
const read = (f) => fs.readFileSync(path.join(__dirname, f), 'utf8');
const pkg = JSON.parse(read('package.json'));

const css = read('src/style.css').trim();
const FILES = [
  'src/icons.js', 'src/i18n.js', 'src/lang_en.js', 'src/lang_ru.js', 'src/lang_uk.js',
  'src/engine.js', 'src/sync_client.js', 'src/render.js',
  'src/net.js', 'src/ui_core.js', 'src/music.js', 'src/ui_tabs.js', 'src/ui_fx.js', 'src/ui_tutorial.js', 'src/ui_pvp.js', 'src/ui_main.js',
];
// node-only export blocks are harmless in the browser (module is undefined) but we strip them to keep the bundle lean
const strip = (src) => src.replace(/\nif \(typeof module !== 'undefined'\)[\s\S]*$/, '\n');
const js = FILES.map((f) => '/* ---- ' + f + ' ---- */\n' + strip(read(f))).join('\n');

if (/<\/script/i.test(js) || /<!--/.test(js)) throw new Error('JS contains a sequence that breaks inline <script>');

const FONT_HREF = 'https://fonts.googleapis.com/css2?family=Exo+2:wght@400;600;700;800&family=Press+Start+2P&display=swap';
const SHELL = `<div id="app">
  <header id="hud"></header>
  <main id="main"></main>
  <nav id="tabs" role="tablist"></nav>
</div>
<div id="toasts" aria-live="polite"></div>
<div id="fxl" aria-hidden="true"></div>
<div id="ovl" class="ovl" hidden></div>
<canvas id="confetti" hidden aria-hidden="true"></canvas>
<div id="splash"><div class="sp-logo">OFFICE<span>DREAMS</span></div><div class="sp-bar"><i></i></div><div class="sp-status"></div><div class="sp-actions"></div></div>`;

const script = (debug, mode) =>
  `(function () {\n'use strict';\n${js}\nBUILD.mode = ${JSON.stringify(mode)}; BUILD.version = ${JSON.stringify(pkg.version)};\n${debug ? 'window.__ev = function (c) { return eval(c); };\n' : ''}boot();\n})();`;

// favicon: pixel crown on a dark tile
const { icoSVG } = require('./src/icons.js');
const crown = icoSVG('crown', 32).replace(/currentColor/g, '#ffd23f').replace(/ class="[^"]*"/, '').replace(' aria-hidden="true"', '');
const favSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><rect width="16" height="16" rx="3" fill="#0b1250"/><g transform="translate(2.5 2.5)">${crown.replace(/^<svg[^>]*>/, '').replace(/<\/svg>$/, '')}</g></svg>`;
const favicon = 'data:image/svg+xml,' + encodeURIComponent(favSvg);

const standalone = (debug) => `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<meta name="theme-color" content="#060a26">
<meta name="color-scheme" content="dark">
<meta name="description" content="Office Dreams — build a neon game studio in Telegram.">
<title>Office Dreams</title>
<link rel="icon" href="${favicon}">
<script src="https://telegram.org/js/telegram-web-app.js"></script>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="${FONT_HREF}">
<style>
${css}
</style>
</head>
<body>
${SHELL}
<script>
${script(debug, 'auto')}
</script>
</body>
</html>
`;

const artifact = `<title>Office Dreams</title>
<link rel="stylesheet" href="${FONT_HREF}">
<style>
${css}
</style>
${SHELL}
<script>
${script(false, 'demo')}
</script>
`;

fs.mkdirSync(path.join(__dirname, 'dist'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'dist/index.html'), standalone(false));
fs.writeFileSync(path.join(__dirname, 'dist/debug.html'), standalone(true));
fs.writeFileSync(path.join(__dirname, 'dist/artifact.html'), artifact);

// syntax check of the final bundle
const vm = require('vm');
try {
  new vm.Script(script(true, 'auto'), { filename: 'bundle.js' });
} catch (e) {
  console.error('SYNTAX ERROR in bundle:', e.message);
  console.error(e.stack.split('\n').slice(0, 6).join('\n'));
  process.exit(1);
}
const kb = (f) => (fs.statSync(path.join(__dirname, f)).size / 1024).toFixed(1) + ' KB';
console.log('built v' + pkg.version + ': index.html', kb('dist/index.html'), '| artifact.html', kb('dist/artifact.html'), '| debug.html', kb('dist/debug.html'));
