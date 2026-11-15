// Renders scene.html frame by frame (1080x1920, 30 fps) into FRAMES_DIR/f00001.jpg …
//   node render.js FRAMES_DIR [fps]
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
  for (let i = 0; i < n; i++) {
    await p.evaluate((t) => window.frame(t), i / fps);
    await p.screenshot({ path: path.join(out, `f${String(i + 1).padStart(5, '0')}.jpg`), type: 'jpeg', quality: 92 });
    if (i % 60 === 0) process.stdout.write(`${i}/${n} `);
  }
  console.log('done', n);
  await b.close();
})();
