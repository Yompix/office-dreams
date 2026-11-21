package com.godot.game;

// LANTERN 2.0 by Yompix Games: «Любые игры» на Android — виртуальная локальная сеть через VpnService.
// Служба создаёт интерфейс с адресом 10.88.x.y/24 и маршрутами ТОЛЬКО своей сети (+ широковещательный 255.255.255.255
// и групповые 224.0.0.0/4, если Android их примет), поэтому остальной интернет телефона идёт мимо LANTERN.
// Пакеты передаются приложению LANTERN по UDP на 127.0.0.1 (тот же протокол, что у lantern-net на ПК, docs/VLAN.md):
//   0x00 "hello <token> <json>" · 0x00 "alive" (раз в секунду) · 0x00 "quit" · иначе — IPv4-пакет.
// Само приложение (VNet) держит связь с relay / P2P. Себя из VPN исключаем (addDisallowedApplication) — без петель.
//
// Мост с GDScript (JavaClassWrapper.wrap("com.godot.game.LanternVpnService"), статические методы):
//   connectVpn(port, token, ip, prefix, mtu) -> "starting" | "need_permission" | "error:…"
//   stop()

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class LanternVpnService extends VpnService {
	private static final String TAG = "LANTERN-VPN";
	private static volatile int port0, prefix0 = 24, mtu0 = 1380;
	private static volatile String token0 = "", ip0 = "";
	private static volatile LanternVpnService self;
	private static volatile Thread waiter;

	private ParcelFileDescriptor tun;
	private DatagramSocket sock;
	private volatile boolean running;

	private static Context app() {
		try {
			return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
		} catch (Exception e) {
			return null;
		}
	}

	public static String connectVpn(int port, String token, String ip, int prefix, int mtu) {
		final Context c = app();
		if (c == null) return "error:no_context";
		if (!ip.startsWith("10.88.") || port < 1024 || port > 65535) return "error:bad_args";
		port0 = port; token0 = token; ip0 = ip; prefix0 = prefix; mtu0 = mtu;
		Intent prep;
		try {
			prep = VpnService.prepare(c);
		} catch (Exception e) {
			return "error:" + e.getMessage();
		}
		if (prep == null) {
			launch(c);
			return "starting";
		}
		// системное окно «Разрешить VPN?»: ждём ответа (повторно спрашиваем prepare) до 2 минут
		prep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			c.startActivity(prep);
		} catch (Exception e) {
			return "error:" + e.getMessage();
		}
		if (waiter == null || !waiter.isAlive()) {
			waiter = new Thread(() -> {
				long until = System.currentTimeMillis() + 120_000;
				while (System.currentTimeMillis() < until) {
					try { Thread.sleep(500); } catch (InterruptedException ie) { return; }
					try {
						if (VpnService.prepare(c) == null) { launch(c); return; }
					} catch (Exception e) { return; }
				}
			}, "lantern-vpn-wait");
			waiter.start();
		}
		return "need_permission";
	}

	private static void launch(Context c) {
		try {
			c.startService(new Intent(c, LanternVpnService.class));
		} catch (Exception e) {
			Log.w(TAG, "start failed", e);
		}
	}

	public static void stop() {
		LanternVpnService s = self;
		if (s != null) s.shutdown();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		shutdown();
		self = this;
		running = true;
		new Thread(this::run, "lantern-vpn").start();
		return START_NOT_STICKY;
	}

	@Override
	public void onRevoke() {   // пользователь выключил VPN в настройках или включил другой VPN
		shutdown();
		super.onRevoke();
	}

	@Override
	public void onDestroy() {
		shutdown();
		super.onDestroy();
	}

	private synchronized void shutdown() {
		running = false;
		try { if (sock != null) sock.close(); } catch (Exception ignored) {}
		try { if (tun != null) tun.close(); } catch (Exception ignored) {}
		sock = null;
		tun = null;
		if (self == this) self = null;
	}

	private void ctl(DatagramSocket s, InetAddress lo, String text) {
		try {
			byte[] t = text.getBytes("UTF-8");
			byte[] b = new byte[t.length + 1];
			System.arraycopy(t, 0, b, 1, t.length);
			s.send(new DatagramPacket(b, b.length, lo, port0));
		} catch (Exception ignored) {}
	}

	private void run() {
		InetAddress lo;
		DatagramSocket s;
		try {
			lo = InetAddress.getByName("127.0.0.1");
			s = new DatagramSocket(0, lo);
			s.setSoTimeout(1000);
			s.setReceiveBufferSize(1 << 20);
			sock = s;
		} catch (Exception e) {
			Log.w(TAG, "socket", e);
			return;
		}
		String err = "";
		try {
			String[] p = ip0.split("\\.");
			String net = p[0] + "." + p[1] + "." + p[2] + ".0";
			Builder b = new Builder();
			b.setSession("LANTERN").setMtu(mtu0).addAddress(ip0, prefix0).addRoute(net, prefix0);
			try { b.addRoute("255.255.255.255", 32); } catch (Exception e) { Log.i(TAG, "no broadcast route: " + e); }
			try { b.addRoute("224.0.0.0", 4); } catch (Exception e) { Log.i(TAG, "no multicast route: " + e); }
			try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}
			if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
			tun = b.establish();
			if (tun == null) err = "VPN не разрешён";
		} catch (Exception e) {
			err = String.valueOf(e.getMessage());
		}
		if (tun == null) {
			ctl(s, lo, "hello " + token0 + " {\"ok\":false,\"err\":\"" + err.replace("\"", "'") + "\"}");
			shutdown();
			stopSelf();
			return;
		}
		ctl(s, lo, "hello " + token0 + " {\"ok\":true,\"ifname\":\"tun (VPN)\",\"v\":\"2.0.0\"}");
		final FileInputStream in = new FileInputStream(tun.getFileDescriptor());
		final FileOutputStream out = new FileOutputStream(tun.getFileDescriptor());
		final DatagramSocket fs = s;
		final InetAddress flo = lo;
		// интерфейс → приложение
		Thread rd = new Thread(() -> {
			byte[] buf = new byte[2048];
			while (running) {
				try {
					int n = in.read(buf);
					if (n >= 20 && (buf[0] >> 4 & 15) == 4) fs.send(new DatagramPacket(buf, n, flo, port0));
				} catch (Exception e) {
					if (running) Log.w(TAG, "tun read", e);
					break;
				}
			}
		}, "lantern-vpn-rd");
		rd.start();
		// приложение → интерфейс; сторож «жив»
		byte[] buf = new byte[2048];
		long last = System.currentTimeMillis(), ka = 0;
		while (running) {
			long now = System.currentTimeMillis();
			if (now - ka > 1000) { ka = now; ctl(s, lo, "alive"); }
			if (now - last > 15_000) break;   // LANTERN закрыли — VPN убираем
			try {
				DatagramPacket pk = new DatagramPacket(buf, buf.length);
				s.receive(pk);
				if (pk.getPort() != port0 || !pk.getAddress().isLoopbackAddress()) continue;
				last = System.currentTimeMillis();
				int n = pk.getLength();
				if (n == 0) continue;
				if (buf[0] == 0) {
					if (n >= 5 && new String(buf, 1, n - 1, "UTF-8").startsWith("quit")) break;
					continue;
				}
				if (n >= 20 && (buf[0] >> 4 & 15) == 4) out.write(buf, 0, n);
			} catch (SocketTimeoutException te) {
				// тишина — проверим сторожа
			} catch (Exception e) {
				if (running) Log.w(TAG, "loop", e);
				break;
			}
		}
		shutdown();
		stopSelf();
	}
}
