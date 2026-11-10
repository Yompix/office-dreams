package com.godot.game;

// LANTERN by Yompix: служба «мир открыт для друзей».
// Пока игрок в Minecraft, LANTERN в фоне держит мост к relay. Без службы с уведомлением Android может
// заморозить приложение через несколько секунд. Копируется в Gradle-проект при экспорте (addons/lantern_android/plugin.gd).
// Из GDScript: JavaClassWrapper.wrap("com.godot.game.LanternRelayService").start(title, text) / .stop()

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class LanternRelayService extends Service {
	private static final String CHANNEL = "lantern_world";
	private static final int ID = 4471;
	private PowerManager.WakeLock wake;
	private WifiManager.WifiLock wifi;

	private static Context app() {
		try {
			return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
		} catch (Exception e) {
			Log.w("LANTERN", "no application context", e);
			return null;
		}
	}

	public static void start(String title, String text) {
		Context c = app();
		if (c == null) return;
		Intent i = new Intent(c, LanternRelayService.class);
		i.putExtra("title", title);
		i.putExtra("text", text);
		try {
			if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
			else c.startService(i);
		} catch (Exception e) {
			Log.w("LANTERN", "start service failed", e);
		}
	}

	public static void stop() {
		Context c = app();
		if (c == null) return;
		try {
			c.stopService(new Intent(c, LanternRelayService.class));
		} catch (Exception e) {
			Log.w("LANTERN", "stop service failed", e);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String title = intent != null && intent.getStringExtra("title") != null ? intent.getStringExtra("title") : "LANTERN";
		String text = intent != null && intent.getStringExtra("text") != null ? intent.getStringExtra("text") : "";
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder b;
		if (Build.VERSION.SDK_INT >= 26) {
			nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Мир открыт для друзей", NotificationManager.IMPORTANCE_LOW));
			b = new Notification.Builder(this, CHANNEL);
		} else {
			b = new Notification.Builder(this);
		}
		Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
		if (open != null) {
			b.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
		}
		b.setContentTitle(title).setContentText(text).setSmallIcon(getApplicationInfo().icon).setOngoing(true);
		Notification n = b.build();
		try {
			if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
			else startForeground(ID, n);
		} catch (Exception e) {
			Log.w("LANTERN", "startForeground failed", e);
			stopSelf();
			return START_NOT_STICKY;
		}
		try {
			if (wake == null) {
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lantern:world");
				wake.acquire(6 * 60 * 60 * 1000L);
			}
			if (wifi == null) {
				WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
				wifi = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lantern:world");
				wifi.acquire();
			}
		} catch (Exception e) {
			Log.w("LANTERN", "locks failed", e);
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		stopSelf();
	}

	@Override
	public void onDestroy() {
		if (wake != null && wake.isHeld()) wake.release();
		if (wifi != null && wifi.isHeld()) wifi.release();
		wake = null;
		wifi = null;
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
