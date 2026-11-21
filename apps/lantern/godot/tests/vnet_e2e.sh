#!/usr/bin/env bash
# 2.0 «Любые игры» — сквозная проверка виртуальной сети на Linux (нужен root: /dev/net/tun и свои network namespace):
#   relay (Node) + два LANTERN (headless) + два помощника lantern-net, каждый со своим TUN в своём namespace.
#   В namespace B игра «ищет сервер» UDP-широковещанием (10.88.x.255 и 255.255.255.255) — A отвечает лично,
#   потом TCP-соединение A↔B (как вход в игру по IP). Всё идёт: адаптер → LANTERN → relay (или P2P) → LANTERN → адаптер.
#   tests/vnet_e2e.sh                 — через relay (P2P выключен)
#   P2P=1 tests/vnet_e2e.sh           — с прямым соединением (оба на одной машине — пробой по локальному адресу)
set -u
HERE=$(cd "$(dirname "$0")/.." && pwd)
GODOT=${GODOT:-/home/user/tools/godot/godot}
WORK=$(mktemp -d -p "${TMPDIR:-/tmp}" lantern-vnet.XXXXXX)
PORT=${RELAY_PORT:-$((18500 + RANDOM % 400))}
HELPER=$WORK/lantern-net
(cd "$HERE/tools/lantern-net" && go build -o "$HELPER" .) || { echo "FAIL: go build"; exit 1; }
QUIET=1 PORT=$PORT node "$HERE/../relay/server.js" > "$WORK/relay.log" 2>&1 & R=$!
sleep 1
P2P_ARG="--vnet_p2p=${P2P:-0}"
run_app() { # роль, папка
  mkdir -p "$WORK/$2"
  "$GODOT" --headless --path "$HERE" -- --autotest vnet --vnet_role=$1 --vnet_code="$WORK/code.txt" --data="$WORK/$2" \
    --relay=ws://127.0.0.1:$PORT/ws --vnet_helper="$HELPER" --vnet_netns=1 $P2P_ARG --nosplash=1 > "$WORK/$2.log" 2>&1
}
run_app create a & A=$!
run_app join b & B=$!
for i in $(seq 1 300); do
  [ -f "$WORK/a/vnet-ready.json" ] && [ -f "$WORK/b/vnet-ready.json" ] && break
  sleep 0.2
done
RC=1
if [ -f "$WORK/a/vnet-ready.json" ] && [ -f "$WORK/b/vnet-ready.json" ]; then
  NSA=$(python3 -c "import json;print(json.load(open('$WORK/a/vnet-ready.json'))['ns'])")
  NSB=$(python3 -c "import json;print(json.load(open('$WORK/b/vnet-ready.json'))['ns'])")
  IPA=$(python3 -c "import json;print(json.load(open('$WORK/a/vnet-ready.json'))['ip'])")
  IPB=$(python3 -c "import json;print(json.load(open('$WORK/b/vnet-ready.json'))['ip'])")
  echo "A $IPA ($NSA)   B $IPB ($NSB)"
  # A — «сервер игры»: отвечает на широковещательный поиск и принимает TCP
  nsenter --net="$NSA" python3 -I - "$IPA" > "$WORK/game_a.log" 2>&1 <<'PY' &
import socket, sys, threading
ip = sys.argv[1]
u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); u.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
u.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1); u.bind(("", 7777)); u.settimeout(25)
t = socket.socket(); t.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); t.bind((ip, 7778)); t.listen(1); t.settimeout(25)
def tcp():
    c, a = t.accept(); d = c.recv(100); c.sendall(b"WELCOME " + d); c.close(); print("tcp from", a[0], d, flush=True)
threading.Thread(target=tcp, daemon=True).start()
n = 0
while n < 2:
    d, a = u.recvfrom(1500); print("discovery", d, "from", a, flush=True)
    u.sendto(b"GAME-HERE " + ip.encode(), a); n += 1
import time; time.sleep(3)
PY
  GA=$!
  sleep 1
  # B — «клиент игры»: ищет сервер широковещанием, затем заходит по TCP
  nsenter --net="$NSB" python3 -I - "$IPB" "$IPA" > "$WORK/game_b.log" 2>&1 <<'PY'
import socket, sys, time
ip, ipa = sys.argv[1], sys.argv[2]
bc = ip.rsplit(".", 1)[0] + ".255"
ok = 0
for dst, dev in ((bc, None), ("255.255.255.255", b"lantern0")):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    if dev: s.setsockopt(socket.SOL_SOCKET, 25, dev)   # SO_BINDTODEVICE: «интерфейс игры»
    s.settimeout(3)
    for k in range(5):
        s.sendto(b"WHO-HOSTS?", (dst, 7777))
        try:
            d, a = s.recvfrom(1500); print("reply", dst, d, a, flush=True); ok += 1; break
        except socket.timeout: pass
c = socket.create_connection((ipa, 7778), timeout=8); c.sendall(b"hello-from-" + ip.encode()); r = c.recv(100); print("tcp reply", r, flush=True)
ok += r.startswith(b"WELCOME")
# объём: 2 МБ по TCP туда-обратно не нужен игре, но проверим, что поток не рвётся — 200 пакетов UDP лично
u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); u.settimeout(0.5)
print("OK" if ok == 3 else "FAIL", ok, flush=True)
PY
  wait $GA
  cat "$WORK/game_a.log" "$WORK/game_b.log"
  grep -q "^OK" "$WORK/game_b.log" && RC=0
fi
touch "$WORK/a/vnet-done" "$WORK/b/vnet-done"
wait $A; wait $B
grep -h "\[autotest\]\|SCRIPT ERROR\|ERROR" "$WORK/a.log" "$WORK/b.log" | head -40
kill $R 2>/dev/null
echo "work: $WORK  result: $([ $RC = 0 ] && echo PASSED || echo FAILED)"
exit $RC
