# Token launch page

`public/launch.html` (served at `/launch`) lets the project owner create and set up the OFFICE token on Solana
by signing every step in Phantom — the server never sees keys.

`public/launch-lib.js` is a browser bundle of `@solana/web3.js@1.95.3` and `@solana/spl-token@0.4.8`.
Rebuild it (from any empty folder):

```bash
npm i @solana/web3.js@1.95.3 @solana/spl-token@0.4.8 buffer@6.0.3 esbuild@0.23.1
cp <repo>/scripts/launch/*.js .
npx esbuild entry.js --bundle --minify --format=iife --global-name=SOLLIB --platform=browser \
  --define:global=globalThis --alias:crypto=./crypto-shim.js --inject:./buffer-shim.js --outfile=<repo>/public/launch-lib.js
```
