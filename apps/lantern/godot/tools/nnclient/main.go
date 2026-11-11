// Тестовый клиент/сервер Bedrock для автотестов LANTERN (gophertunnel): discover|join (NetherNet LAN), rak|http (вход на сервер), srv (тестовый сервер).
// Сборка: go build -o nnclient .   Используют tests/ownworld_nn.sh, tests/bedrock_join.sh, tests/versions.sh (NNCLIENT=...).
package main

import (
	"context"
	"fmt"
	"log/slog"
	"math/rand/v2"
	"net"
	"os"
	"strconv"
	"time"

	"github.com/df-mc/go-nethernet"
	"github.com/df-mc/go-nethernet/discovery"
	"github.com/sandertv/gophertunnel/minecraft"
	"github.com/sandertv/gophertunnel/minecraft/protocol"
	"github.com/sandertv/gophertunnel/minecraft/protocol/login"
)

// nnclient <discover|join> <host:port> [name]
func main() {
	mode, target := os.Args[1], os.Args[2]
	name := "NNTester"
	if len(os.Args) > 3 {
		name = os.Args[3]
	}
	addr, err := net.ResolveUDPAddr("udp", target)
	if err != nil {
		panic(err)
	}
	lvl := slog.LevelInfo
	if os.Getenv("NNDEBUG") != "" {
		lvl = slog.LevelDebug
	}
	log := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: lvl}))
	if mode == "srv" {
		// тестовый «сервер Bedrock»: RakNet на UDP <port> и вход по HTTP (NetherNet endpoint) на TCP <port>
		cfg := minecraft.ListenConfig{StatusProvider: minecraft.NewStatusProvider("LANTERN test", "world"), AuthenticationDisabled: true, ErrorLog: log}
		serve := func(l *minecraft.Listener, kind string) {
			for {
				c, err := l.Accept()
				if err != nil {
					return
				}
				go func(conn *minecraft.Conn) {
					defer conn.Close()
					if err := conn.StartGame(minecraft.GameData{WorldName: "LANTERN test"}); err != nil {
						fmt.Println("START_ERROR", kind, err)
						return
					}
					fmt.Println("PLAYER_SPAWNED", kind, conn.IdentityData().DisplayName)
					for {
						if _, err := conn.ReadPacket(); err != nil {
							return
						}
					}
				}(c.(*minecraft.Conn))
			}
		}
		lr, err := cfg.Listen("raknet", target)
		if err != nil {
			panic(err)
		}
		minecraft.RegisterNetwork("nnhttp", func(l *slog.Logger) minecraft.Network {
			return minecraft.NetherNet{Log: l, ListenConfig: nethernet.ListenConfig{AllowAnonymous: true}}
		})
		ln, err := cfg.Listen("nnhttp", target)
		if err != nil {
			panic(err)
		}
		fmt.Println("LISTENING", target)
		go serve(lr, "raknet")
		serve(ln, "http")
		return
	}
	if mode == "rak" || mode == "http" {
		fmt.Println("client protocol", protocol.CurrentProtocol, protocol.CurrentVersion)
		ctx, cancel := context.WithTimeout(context.Background(), 40*time.Second)
		defer cancel()
		d := minecraft.Dialer{IdentityData: login.IdentityData{DisplayName: name}, ErrorLog: log}
		var conn *minecraft.Conn
		var err error
		if mode == "http" {
			conn, err = d.DialContextNetwork(ctx, minecraft.NetherNet{Log: log, Dialer: nethernet.Dialer{Log: log}}, "http://"+target)
		} else {
			conn, err = d.DialContext(ctx, "raknet", target)
		}
		if err != nil {
			fmt.Println("RESULT dial_error", err)
			os.Exit(3)
		}
		fmt.Println("CONNECTED world:", conn.GameData().WorldName)
		if err := conn.DoSpawnContext(ctx); err != nil {
			fmt.Println("RESULT spawn_error", err)
			os.Exit(4)
		}
		fmt.Println("RESULT spawned")
		time.Sleep(2 * time.Second)
		conn.Close()
		return
	}
	l, err := discovery.ListenConfig{NetworkID: rand.Uint64() >> 2, BroadcastAddress: addr, Log: log}.Listen("127.0.0.1:0")
	if err != nil {
		panic(err)
	}
	deadline := time.Now().Add(20 * time.Second)
	var id uint64
	var sd discovery.ServerData
	for time.Now().Before(deadline) {
		for k, v := range l.Responses() {
			if err := sd.UnmarshalBinary(v); err != nil {
				fmt.Println("bad server data:", err, len(v))
				continue
			}
			id = k
		}
		if id != 0 {
			break
		}
		time.Sleep(500 * time.Millisecond)
	}
	if id == 0 {
		fmt.Println("RESULT no_world")
		os.Exit(2)
	}
	fmt.Printf("FOUND id=%d server=%q level=%q version=%q protocol=%d players=%d/%d type=%d\n", id, sd.ServerName, sd.LevelName, sd.Version, sd.Protocol, sd.PlayerCount, sd.MaxPlayerCount, sd.ConnectionType)
	if mode == "discover" {
		fmt.Println("RESULT discovered")
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 40*time.Second)
	defer cancel()
	d := minecraft.Dialer{IdentityData: login.IdentityData{DisplayName: name}, ErrorLog: log}
	conn, err := d.DialContextNetwork(ctx, minecraft.NetherNet{Signaling: l, Log: log, Dialer: nethernet.Dialer{AllowIdentitylessServer: true, Log: log}}, strconv.FormatUint(id, 10))
	if err != nil {
		fmt.Println("RESULT dial_error", err)
		os.Exit(3)
	}
	fmt.Println("CONNECTED world:", conn.GameData().WorldName)
	if err := conn.DoSpawnContext(ctx); err != nil {
		fmt.Println("RESULT spawn_error", err)
		os.Exit(4)
	}
	fmt.Println("RESULT spawned")
	conn.Close()
}
