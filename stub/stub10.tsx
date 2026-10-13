// Pixel Casino loader v10: сервер (app.tsx) та інтерфейс (web.html) беруться з GitHub (Yompix/office-dreams, гілка pixel-casino),
// перевіряються за sha256 (APP_SHA / WEB_SHA) і кешуються в базі; якщо щось не так — запускається попередня версія з APP_Z1..8
import * as TON from "@ton/ton@15";
import * as TC from "@ton/crypto@3";
import { Chess } from "chess.js@1";
import { SQL } from "bun";
const G: any = globalThis; G.__TON = TON; G.__TC = TC; G.__Chess = Chess;
const env = process.env;
const sh = (s: string) => new Bun.CryptoHasher("sha256").update(s).digest("hex");
const REF = env.APP_REF || "pixel-casino";
const SRC = [`https://raw.githubusercontent.com/Yompix/office-dreams/${REF}/`, `https://cdn.jsdelivr.net/gh/Yompix/office-dreams@${REF}/`];
const db = new SQL(env.DATABASE_URL!);
async function load(name: string, want?: string): Promise<string | null> {
  if (!want) { console.error("no sha for", name); return null; }
  try { const r = await db`select body from parts where name = ${"cache:" + name}`; if (r.length && sh(r[0].body) === want) { console.log("cache", name, r[0].body.length); return r[0].body; } } catch (e: any) { console.error("cache read", e.message); }
  for (const base of SRC) for (let k = 0; k < 3; k++) {
    try {
      const res = await fetch(base + name, { signal: AbortSignal.timeout(20000) }); const t = await res.text();
      if (sh(t) === want) {
        try { await db`insert into parts(name, body) values(${"cache:" + name}, ${t}) on conflict (name) do update set body = excluded.body`; } catch (e: any) { console.error("cache write", e.message); }
        console.log("fetched", name, t.length, base.slice(8, 30)); return t;
      }
      console.error("sha mismatch", name, res.status, t.length, base.slice(8, 30)); break;
    } catch (e: any) { console.error("fetch", name, e.message); await Bun.sleep(1500); }
  }
  return null;
}
let code = await load("app.tsx", env.APP_SHA);
const web = code ? await load("web.html", env.WEB_SHA) : null;
if (code && web) { G.__WEB = web; console.log("Loader v10: new app", REF.slice(0, 10)); }
else {
  console.error("Loader v10: fallback to APP_Z");
  const b64 = [1, 2, 3, 4, 5, 6, 7, 8].map((i) => env["APP_Z" + i] || "").join("");
  code = new TextDecoder().decode(Bun.gunzipSync(Buffer.from(b64, "base64")));
}
let file = import.meta.dir + "/app.tsx";
try { await Bun.write(file, code!); } catch { file = "/tmp/pixel-app.tsx"; await Bun.write(file, code!); }
await import(file);
