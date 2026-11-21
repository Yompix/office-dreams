//go:build linux

package main

import (
	"errors"
	"fmt"
	"net"
	"os"
	"runtime"
	"syscall"
	"unsafe"
)

type linuxTun struct {
	f    *os.File
	name string
}

func (t *linuxTun) Read(b []byte) (int, error)  { return t.f.Read(b) }
func (t *linuxTun) Write(b []byte) (int, error) { return t.f.Write(b) }
func (t *linuxTun) Name() string                { return t.name }
func (t *linuxTun) Close() error                { return t.f.Close() }

var nsFile = ""

func nsPath() string { return nsFile }

type ifreq struct {
	name [16]byte
	data [24]byte
}

func ioctl(fd uintptr, req uintptr, arg unsafe.Pointer) error {
	if _, _, e := syscall.Syscall(syscall.SYS_IOCTL, fd, req, uintptr(arg)); e != 0 {
		return e
	}
	return nil
}

func sockaddr(ip net.IP) [24]byte {
	var d [24]byte
	d[0] = syscall.AF_INET // sa_family (little-endian u16)
	copy(d[4:8], ip.To4())
	return d
}

func ifUp(s int, name string) error {
	var r ifreq
	copy(r.name[:], name)
	if err := ioctl(uintptr(s), syscall.SIOCGIFFLAGS, unsafe.Pointer(&r)); err != nil {
		return err
	}
	flags := *(*uint16)(unsafe.Pointer(&r.data[0]))
	*(*uint16)(unsafe.Pointer(&r.data[0])) = flags | syscall.IFF_UP | syscall.IFF_RUNNING
	return ioctl(uintptr(s), syscall.SIOCSIFFLAGS, unsafe.Pointer(&r))
}

func openTun(c *Config) (Tun, error) {
	if c.Netns {
		// только для тестов: свой сетевой namespace на этом потоке (сокет к приложению уже открыт в общем)
		runtime.LockOSThread() // поток не отпускаем: дальше все ioctl — в новом namespace
		if err := syscall.Unshare(syscall.CLONE_NEWNET); err != nil {
			return nil, fmt.Errorf("unshare: %w", err)
		}
		nsFile = fmt.Sprintf("/proc/%d/task/%d/ns/net", os.Getpid(), syscall.Gettid())
	}
	f, err := os.OpenFile("/dev/net/tun", os.O_RDWR, 0)
	if err != nil {
		return nil, err
	}
	var r ifreq
	copy(r.name[:], "lantern%d")
	*(*uint16)(unsafe.Pointer(&r.data[0])) = syscall.IFF_TUN | syscall.IFF_NO_PI
	if err := ioctl(f.Fd(), syscall.TUNSETIFF, unsafe.Pointer(&r)); err != nil {
		f.Close()
		return nil, fmt.Errorf("TUNSETIFF: %w (administrator rights needed)", err)
	}
	name := string(r.name[:clen(r.name[:])])
	s, err := syscall.Socket(syscall.AF_INET, syscall.SOCK_DGRAM, 0)
	if err != nil {
		f.Close()
		return nil, err
	}
	defer syscall.Close(s)
	if c.Netns {
		_ = ifUp(s, "lo")
	}
	ip := net.ParseIP(c.IP)
	mask := net.CIDRMask(c.Mask, 32)
	set := func(req uintptr, d [24]byte) error {
		var q ifreq
		copy(q.name[:], name)
		q.data = d
		return ioctl(uintptr(s), req, unsafe.Pointer(&q))
	}
	if err := set(syscall.SIOCSIFADDR, sockaddr(ip)); err != nil {
		f.Close()
		return nil, fmt.Errorf("SIOCSIFADDR: %w", err)
	}
	if err := set(syscall.SIOCSIFNETMASK, sockaddr(net.IP(mask))); err != nil {
		f.Close()
		return nil, err
	}
	bc := ip.To4().Mask(mask)
	bc[3] = 255
	_ = set(syscall.SIOCSIFBRDADDR, sockaddr(bc))
	var m [24]byte
	*(*int32)(unsafe.Pointer(&m[0])) = int32(c.MTU)
	_ = set(syscall.SIOCSIFMTU, m)
	if err := ifUp(s, name); err != nil {
		f.Close()
		return nil, err
	}
	return &linuxTun{f: f, name: name}, nil
}

func clen(b []byte) int {
	for i, x := range b {
		if x == 0 {
			return i
		}
	}
	return len(b)
}

func driverCheck() error {
	f, err := os.OpenFile("/dev/net/tun", os.O_RDWR, 0)
	if err != nil {
		return err
	}
	return f.Close()
}

func taskSetup(bool) error         { return errors.New("only on Windows") }
func readRequest() (Config, error) { return Config{}, errors.New("only on Windows") }
