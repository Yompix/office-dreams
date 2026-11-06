# Self-hosting Office Dreams for $0 (or near it)

The app is plain Node 22, **no npm dependencies**, built by the Dockerfile. The whole
game — every player's points and wallet, **and the airdrop distributor's private key** —
lives in one SQLite file (`data/officedreams.sqlite`). So wherever you host it:

1. the data volume MUST be **persistent** (never wiped on redeploy), and
2. the machine must stay **always-on** (the bot webhook + giveaway/market timers run in-process).

> Free hosts with an EPHEMERAL disk (Render free, Koyeb free) will DELETE the database on
> every redeploy — that loses the airdrop key and all players. Do **not** use them.

## 0. Back up the database FIRST (before moving anything)
Download `data/officedreams.sqlite` from the current host and keep a copy somewhere safe.
If you start a fresh server with `AIRDROP_GENKEY=1` and an empty DB, it generates a **new**
distributor wallet and the 90M OFFX on the old wallet become orphaned. Always carry the DB over.

## 1. Fly.io (cheapest managed; needs a card, ~a couple $/mo, often under the min bill)
`fly.toml` is in the repo. One 256MB always-on machine + a 1GB volume.
```
fly launch --no-deploy --copy-config      # uses fly.toml
fly volumes create od_data --size 1 --region waw
fly secrets set BOT_TOKEN=... ADMIN_KEY=... SESSION_SECRET=... \
  TOKEN_ADDRESS=... SOL_PAY_WALLET=... AIRDROP_WALLET=... AIRDROP_GENKEY=1 \
  CHANNEL=@... PUBLIC_URL=https://<app>.fly.dev    # (your real values; never share them)
fly deploy
# then copy the backed-up DB into the volume:  fly ssh console → put the file at /app/data/
```

## 2. Oracle Cloud Always Free (free FOREVER; needs a card only to verify, no charge)
Create an "Always Free" Ampere VM (Ubuntu). Then:
```
sudo apt update && sudo apt install -y docker.io git
git clone <repo> office-dreams && cd office-dreams
# put your backed-up DB at ./data/officedreams.sqlite
docker build -t od . && docker run -d --restart=always -p 80:8080 \
  -v $PWD/data:/app/data --env-file od.env od
```
`od.env` holds the same variables as above (keep it private; never commit it).

## 3. Any always-on PC / mini-PC you already own ($0, no card)
Same Docker command as Oracle. Point a free tunnel (Cloudflare Tunnel) at it for HTTPS,
or a dynamic-DNS + port-forward, and set `PUBLIC_URL` to that address.

## After moving (any host)
- Point the Telegram webhook at the new URL (the bot does this on boot from `PUBLIC_URL`).
- Open `/admin` and check "bot is admin of channel".
- Verify `/healthz` returns ok and the game loads at the new URL.
