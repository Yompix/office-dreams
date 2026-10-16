# Art tools (dev only)

- `common.js` — Playwright helpers: serves the built `dist/debug.html` at `http://od.test/`, `rich(p)` fills a demo studio, `ev(p, code)` runs code in the page.
- `skinshot.js` — `node tools/art/skinshot.js <outdir> [candidate.css] [lang]` screenshots every main screen in Art 2 mode (`CLASSIC=1` for the classic look). Run `node build.js` first.
- `cutout.py` — `python3 tools/art/cutout.py sheet.png out_ name1,name2,...` chroma-keys a sprite sheet drawn on #00FF00 and saves each sprite as a PNG (convert to webp for `public/art/`).
