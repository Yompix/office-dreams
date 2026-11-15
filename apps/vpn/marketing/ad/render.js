// Renders scene.html frame by frame (1080x1920, 30 fps) into FRAMES_DIR/f00001.jpg …
//   node render.js FRAMES_DIR [fps] [fromFrame toFrame]
// Needs Playwright (preinstalled in the cloud image; locally: npm i playwright).
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

(async () => {
  const out = process.argv[2];
  const fps = Number(process.argv[3] || 30);
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 540, height: 960 }, deviceScaleFactor: 2 });
  await p.goto('file://' + path.join(__dirname, 'scene.html') + '?render');
  const dur = await p.evaluate(() => window.DUR);
  const n = Math.round(dur * fps);
  const voice = process.env.VOICE_SYNC === '1'; // timing fitted to the owner's recording (scene.html VOICE)
  const from = Number(process.argv[4] || 0), to = Number(process.argv[5] || n); // optional frame range
  for (let i = from; i < Math.min(n, to); i++) {
    await p.evaluate(([t, v]) => (v ? window.frameVoice(t) : window.frame(t)), [i / fps, voice]);
    await p.screenshot({ path: path.join(out, `f${String(i + 1).padStart(5, '0')}.jpg`), type: 'jpeg', quality: 92 });
    if (i % 60 === 0) process.stdout.write(`${i}/${n} `);
  }
  console.log('done', n);
  await b.close();
})();
