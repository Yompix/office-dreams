// lantern-net — помощник LANTERN 2.0 «Любые игры» (by Yompix Games).
// Создаёт виртуальный сетевой адаптер (Windows — Wintun, Linux — /dev/net/tun) с адресом 10.88.x.y/24
// и гонит сырые IPv4-пакеты между адаптером и приложением LANTERN по 127.0.0.1 (UDP).
// Сам в интернет не ходит: связь с другими игроками (relay, P2P) держит приложение.
//
// Протокол с приложением (UDP на 127.0.0.1:<port>, только с этого адреса):
//
//	0x00 "hello <token> <json>"  помощник → приложение: адаптер готов {ok, ifname, err}
//	0x00 "alive"                  в обе стороны раз в секунду; 15 с тишины — помощник выходит и убирает адаптер
//	0x00 "quit"                   приложение → помощник: выйти
//	иначе                         IPv4-пакет как есть
//
// Запуск: lantern-net --port 51820 --token <hex> --ip 10.88.7.2 --mask 24 --mtu 1380
// Windows без UAC каждый раз: один раз «--install» (под администратором) регистрирует задачу планировщика,
// дальше приложение кладёт запрос в %LOCALAPPDATA%\LANTERN\net-request.json и запускает задачу (см. docs/VLAN.md).
package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"net"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

const version = "2.0.0"

type Tun interface {
	Read(b []byte) (int, error)
	Write(b []byte) (int, error)
	Name() string
	Close() error
}

type Config struct {
	Port   int    `json:"port"`
	Token  string `json:"token"`
	IP     string `json:"ip"`
	Mask   int    `json:"mask"`
	MTU    int    `json:"mtu"`
	Metric int    `json:"metric"`
	Netns  bool   `json:"-"`
}

func (c *Config) validate() error {
	ip := net.ParseIP(c.IP).To4()
	if ip == nil || ip[0] != 10 || ip[1] != 88 || ip[2] == 0 || ip[3] == 0 || ip[3] == 255 {
		return errors.New("ip must be 10.88.x.y")
	}
	if c.Port < 1024 || c.Port > 65535 {
		return errors.New("bad port")
	}
	if len(c.Token) < 16 || len(c.Token) > 64 || strings.Trim(c.Token, "0123456789abcdef") != "" {
		return errors.New("bad token")
	}
	if c.Mask != 24 {
		c.Mask = 24
	}
	if c.MTU < 576 || c.MTU > 1500 {
		c.MTU = 1380
	}
	return nil
}

func main() {
	var c Config
	flag.IntVar(&c.Port, "port", 0, "UDP port of the LANTERN app on 127.0.0.1")
	flag.StringVar(&c.Token, "token", "", "one-time token")
	flag.StringVar(&c.IP, "ip", "", "virtual IPv4 address 10.88.x.y")
	flag.IntVar(&c.Mask, "mask", 24, "prefix length")
	flag.IntVar(&c.MTU, "mtu", 1380, "MTU")
	flag.IntVar(&c.Metric, "metric", 1, "interface metric (Windows): low = LAN broadcasts go to LANTERN")
	flag.BoolVar(&c.Netns, "netns", false, "test only (Linux): own network namespace")
	ver := flag.Bool("version", false, "print version")
	install := flag.Bool("install", false, "Windows: register the scheduled task (run as admin once)")
	uninstall := flag.Bool("uninstall", false, "Windows: remove the scheduled task and firewall rule")
	fromTask := flag.Bool("from-task", false, "Windows: read the request file written by LANTERN")
	check := flag.Bool("check", false, "check that the adapter driver can be loaded")
	flag.Parse()
	if *ver {
		fmt.Println("lantern-net", version)
		return
	}
	if *install || *uninstall {
		if err := taskSetup(*install); err != nil {
			fmt.Fprintln(os.Stderr, "error:", err)
			os.Exit(2)
		}
		fmt.Println("ok")
		return
	}
	if *check {
		if err := driverCheck(); err != nil {
			fmt.Println("error:", err)
			os.Exit(3)
		}
		fmt.Println("ok")
		return
	}
	if *fromTask {
		r, err := readRequest()
		if err != nil {
			fmt.Fprintln(os.Stderr, "error:", err)
			os.Exit(2)
		}
		c = r
	}
	if err := c.validate(); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(2)
	}
	os.Exit(run(&c))
}

func run(c *Config) int {
	app, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: c.Port})
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		return 2
	}
	defer app.Close()
	_ = app.SetReadBuffer(1 << 20)
	_ = app.SetWriteBuffer(1 << 20)
	ctl := func(s string) { _, _ = app.Write(append([]byte{0}, s...)) }
	tun, err := openTun(c)
	if err != nil {
		b, _ := json.Marshal(map[string]any{"ok": false, "err": err.Error(), "v": version})
		ctl("hello " + c.Token + " " + string(b))
		fmt.Fprintln(os.Stderr, "error:", err)
		return 3
	}
	defer tun.Close()
	b, _ := json.Marshal(map[string]any{"ok": true, "ifname": tun.Name(), "v": version, "ns": nsPath()})
	ctl("hello " + c.Token + " " + string(b))
	fmt.Println("ready", tun.Name(), nsPath())

	var last atomic.Int64
	last.Store(time.Now().UnixMilli())
	quit := make(chan string, 2)
	// адаптер → приложение
	go func() {
		buf := make([]byte, 2048)
		for {
			n, err := tun.Read(buf)
			if err != nil {
				quit <- "tun: " + err.Error()
				return
			}
			if n >= 20 && buf[0]>>4 == 4 {
				_, _ = app.Write(buf[:n])
			}
		}
	}()
	// приложение → адаптер
	go func() {
		buf := make([]byte, 2048)
		for {
			n, err := app.Read(buf)
			if err != nil {
				if ne, ok := err.(net.Error); ok && ne.Timeout() {
					continue
				}
				// приложение ещё не слушает (ICMP «порт закрыт») — подождём, сторож решит
				time.Sleep(50 * time.Millisecond)
				continue
			}
			last.Store(time.Now().UnixMilli())
			if n == 0 {
				continue
			}
			if buf[0] == 0 {
				if strings.HasPrefix(string(buf[1:n]), "quit") {
					quit <- "quit"
					return
				}
				continue
			}
			if n >= 20 && buf[0]>>4 == 4 && n <= c.MTU+100 {
				_, _ = tun.Write(buf[:n])
			}
		}
	}()
	tick := time.NewTicker(time.Second)
	defer tick.Stop()
	for {
		select {
		case why := <-quit:
			fmt.Println("exit:", why)
			return 0
		case <-tick.C:
			ctl("alive")
			if time.Now().UnixMilli()-last.Load() > 15000 {
				fmt.Println("exit: app is silent")
				return 0
			}
		}
	}
}
