//go:build windows

package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

// Wintun (https://www.wintun.net, подписанный драйвер WireGuard LLC, можно распространять вместе с программой):
// wintun.dll лежит рядом с lantern-net.exe. Вызовы — напрямую через syscall, без сторонних библиотек.
var (
	wintun            = syscall.NewLazyDLL(dllPath())
	pCreateAdapter    = wintun.NewProc("WintunCreateAdapter")
	pCloseAdapter     = wintun.NewProc("WintunCloseAdapter")
	pStartSession     = wintun.NewProc("WintunStartSession")
	pEndSession       = wintun.NewProc("WintunEndSession")
	pGetReadWaitEvent = wintun.NewProc("WintunGetReadWaitEvent")
	pReceivePacket    = wintun.NewProc("WintunReceivePacket")
	pReleaseReceive   = wintun.NewProc("WintunReleaseReceivePacket")
	pAllocateSend     = wintun.NewProc("WintunAllocateSendPacket")
	pSendPacket       = wintun.NewProc("WintunSendPacket")
	pGetVersion       = wintun.NewProc("WintunGetRunningDriverVersion")
	kernel32          = syscall.NewLazyDLL("kernel32.dll")
	pWaitForSingle    = kernel32.NewProc("WaitForSingleObject")
)

const adapterName = "LANTERN"
const taskName = "LANTERN Net"
const fwRule = "LANTERN virtual LAN"

// постоянный GUID: Windows не плодит «Сеть 2, 3, 4…» при каждом подключении
var adapterGUID = [16]byte{0x4c, 0x41, 0x4e, 0x54, 0x45, 0x52, 0x4e, 0x2d, 0x59, 0x6f, 0x6d, 0x70, 0x69, 0x78, 0x2e, 0x31}

func dllPath() string {
	exe, err := os.Executable()
	if err != nil {
		return "wintun.dll"
	}
	return filepath.Join(filepath.Dir(exe), "wintun.dll")
}

func nsPath() string { return "" }

type winTun struct {
	adapter, session uintptr
	event            uintptr
	name             string
}

func (t *winTun) Name() string { return t.name }

func (t *winTun) Read(b []byte) (int, error) {
	for {
		var size uint32
		p, _, e := pReceivePacket.Call(t.session, uintptr(unsafe.Pointer(&size)))
		if p != 0 {
			n := copy(b, unsafe.Slice((*byte)(unsafe.Pointer(p)), size))
			pReleaseReceive.Call(t.session, p)
			return n, nil
		}
		if en, ok := e.(syscall.Errno); ok && en == 259 { // ERROR_NO_MORE_ITEMS
			pWaitForSingle.Call(t.event, 250)
			continue
		}
		if t.session == 0 {
			return 0, errors.New("closed")
		}
		return 0, fmt.Errorf("receive: %v", e)
	}
}

func (t *winTun) Write(b []byte) (int, error) {
	p, _, e := pAllocateSend.Call(t.session, uintptr(len(b)))
	if p == 0 {
		return 0, fmt.Errorf("send: %v", e) // кольцо переполнено — пакет теряется, как в настоящей сети
	}
	copy(unsafe.Slice((*byte)(unsafe.Pointer(p)), len(b)), b)
	pSendPacket.Call(t.session, p)
	return len(b), nil
}

func (t *winTun) Close() error {
	if t.session != 0 {
		pEndSession.Call(t.session)
		t.session = 0
	}
	if t.adapter != 0 {
		pCloseAdapter.Call(t.adapter) // адаптер, созданный этим процессом, удаляется
		t.adapter = 0
	}
	return nil
}

func netsh(args ...string) error {
	cmd := exec.Command("netsh", args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("netsh %s: %v %s", strings.Join(args[:2], " "), err, strings.TrimSpace(string(out)))
	}
	return nil
}

func driverCheck() error {
	if err := wintun.Load(); err != nil {
		return fmt.Errorf("wintun.dll: %v", err)
	}
	return nil
}

func openTun(c *Config) (Tun, error) {
	if err := driverCheck(); err != nil {
		return nil, err
	}
	name, _ := syscall.UTF16PtrFromString(adapterName)
	ttype, _ := syscall.UTF16PtrFromString("LANTERN")
	a, _, e := pCreateAdapter.Call(uintptr(unsafe.Pointer(name)), uintptr(unsafe.Pointer(ttype)), uintptr(unsafe.Pointer(&adapterGUID)))
	if a == 0 {
		return nil, fmt.Errorf("create adapter: %v (administrator rights needed)", e)
	}
	s, _, e := pStartSession.Call(a, 0x400000) // кольцо 4 МиБ
	if s == 0 {
		pCloseAdapter.Call(a)
		return nil, fmt.Errorf("adapter session: %v", e)
	}
	ev, _, _ := pGetReadWaitEvent.Call(s)
	t := &winTun{adapter: a, session: s, event: ev, name: adapterName}
	mask := net.IP(net.CIDRMask(c.Mask, 32)).String()
	// адрес, MTU и низкая метрика (широковещательные 255.255.255.255 уходят в LANTERN — так делают Hamachi и Radmin)
	var err error
	for i := 0; i < 10; i++ { // адаптер появляется в системе не мгновенно
		if err = netsh("interface", "ipv4", "set", "address", "name="+adapterName, "source=static", "address="+c.IP, "mask="+mask, "gateway=none"); err == nil {
			break
		}
		time.Sleep(300 * time.Millisecond)
	}
	if err != nil {
		t.Close()
		return nil, err
	}
	_ = netsh("interface", "ipv4", "set", "subinterface", adapterName, fmt.Sprintf("mtu=%d", c.MTU), "store=active")
	if c.Metric > 0 {
		_ = netsh("interface", "ipv4", "set", "interface", adapterName, fmt.Sprintf("metric=%d", c.Metric))
	}
	// брандмауэр: новый адаптер — «неопознанная сеть» (общедоступная), входящие от игроков сети разрешаем только с 10.88.0.0/16
	_ = netsh("advfirewall", "firewall", "delete", "rule", "name="+fwRule)
	_ = netsh("advfirewall", "firewall", "add", "rule", "name="+fwRule, "dir=in", "action=allow", "protocol=any", "remoteip=10.88.0.0/16", "localip=10.88.0.0/16", "profile=any")
	return t, nil
}

func reqPath() string {
	d := os.Getenv("LOCALAPPDATA")
	if d == "" {
		d = os.TempDir()
	}
	return filepath.Join(d, "LANTERN", "net-request.json")
}

// Задача планировщика с наивысшими правами: UAC спрашивается один раз (при --install).
func taskSetup(install bool) error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	run := func(args ...string) error {
		cmd := exec.Command("schtasks", args...)
		cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
		out, err := cmd.CombinedOutput()
		if err != nil {
			return fmt.Errorf("schtasks: %v %s", err, strings.TrimSpace(string(out)))
		}
		return nil
	}
	if !install {
		_ = netsh("advfirewall", "firewall", "delete", "rule", "name="+fwRule)
		return run("/delete", "/tn", taskName, "/f")
	}
	user := os.Getenv("USERDOMAIN") + `\` + os.Getenv("USERNAME")
	return run("/create", "/tn", taskName, "/tr", `"`+exe+`" --from-task`, "/sc", "once", "/st", "00:00", "/sd", "01/01/2000",
		"/rl", "highest", "/ru", user, "/it", "/f")
}

func readRequest() (Config, error) {
	var c Config
	p := reqPath()
	b, err := os.ReadFile(p)
	if err != nil {
		return c, err
	}
	_ = os.Remove(p) // одноразовый
	if err := json.Unmarshal(b, &c); err != nil {
		return c, err
	}
	return c, nil
}
