package com.godot.game;

// LANTERN by Yompix: служба «мир открыт для друзей» + плавающая кнопка поверх Minecraft.
// Пока игрок в Minecraft, LANTERN в фоне держит мост к relay. Без службы с уведомлением Android может
// заморозить приложение через несколько секунд. Копируется в Gradle-проект при экспорте (addons/lantern_android/plugin.gd).
//
// Мост с GDScript (JavaClassWrapper.wrap("com.godot.game.LanternRelayService"), только статические методы):
//   start(title, text) / stop()          — служба и уведомление
//   update(json)                         — состояние комнаты: {code, ok, why, world, peers:[{peer,name}], public, share}
//   takeActions() -> json-массив         — что игрок нажал в окошке/уведомлении: {a:"kick"|"ban", peer} {a:"public", on} {a:"close"}
//   event(text, alert)                   — «Алекс зашёл в ваш мир» / «Мир не виден друзьям»
//   canDrawOverlays() / requestOverlay() — разрешение «Поверх других приложений»
// update/takeActions/event вызываются и из потока моста (основной цикл Godot в фоне стоит) — всё рисуем через Handler.

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class LanternRelayService extends Service {
	private static final String CHANNEL = "lantern_world";
	private static final String CHANNEL_EV = "lantern_friends";
	private static final int ID = 4471;
	private static final int ID_EV = 4472;
	private static final String A_CLOSE = "lantern.CLOSE";
	private static final int GREEN = 0xFF3FB950, RED = 0xFFE5534B, BG = 0xF0151A23, TEXT = 0xFFEEF1F7, MUTED = 0xFF9AA4B5;

	private static final Handler ui = new Handler(Looper.getMainLooper());
	private static final Object lock = new Object();
	private static JSONObject state = new JSONObject();
	private static JSONArray actions = new JSONArray();
	private static LanternRelayService self;
	private static String title0 = "LANTERN", text0 = "";

	private PowerManager.WakeLock wake;
	private WifiManager.WifiLock wifi;
	private WindowManager wm;
	private TextView bubble;
	private WindowManager.LayoutParams bubbleLp;
	private LinearLayout panel;
	private WindowManager.LayoutParams panelLp;
	private boolean panelOpen = false;
	private boolean confirmClose = false;
	private String lastKey = "";
	private String lastNote = "";

	private static Context app() {
		try {
			return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
		} catch (Exception e) {
			Log.w("LANTERN", "no application context", e);
			return null;
		}
	}

	// ------------------------------------------------------------------ мост для GDScript

	public static void start(String title, String text) {
		Context c = app();
		if (c == null) return;
		title0 = title;
		text0 = text;
		Intent i = new Intent(c, LanternRelayService.class);
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
		synchronized (lock) { state = new JSONObject(); }
		try {
			c.stopService(new Intent(c, LanternRelayService.class));
		} catch (Exception e) {
			Log.w("LANTERN", "stop service failed", e);
		}
	}

	public static void update(String json) {
		try {
			JSONObject o = new JSONObject(json);
			synchronized (lock) { state = o; }
		} catch (Exception e) {
			return;
		}
		ui.post(() -> { if (self != null) self.refresh(); });
	}

	public static String takeActions() {
		synchronized (lock) {
			String s = actions.toString();
			actions = new JSONArray();
			return s;
		}
	}

	public static void event(String text, boolean alert) {
		ui.post(() -> {
			Context c = self != null ? self : app();
			if (c == null) return;
			try {
				NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
				Notification.Builder b;
				if (Build.VERSION.SDK_INT >= 26) {
					nm.createNotificationChannel(new NotificationChannel(CHANNEL_EV, "Друзья заходят и выходят", NotificationManager.IMPORTANCE_DEFAULT));
					b = new Notification.Builder(c, CHANNEL_EV);
				} else {
					b = new Notification.Builder(c);
				}
				b.setContentTitle(alert ? "LANTERN: внимание" : "LANTERN").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
					.setSmallIcon(c.getApplicationInfo().icon).setAutoCancel(true);
				if (Build.VERSION.SDK_INT >= 26 && !alert) b.setTimeoutAfter(15000);
				PendingIntent open = openIntent(c);
				if (open != null) b.setContentIntent(open);
				nm.notify(ID_EV, b.build());
			} catch (Exception e) {
				Log.w("LANTERN", "event notify failed", e);
			}
			if (self != null && self.bubble != null) {
				try { Toast.makeText(c, text, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
			}
		});
	}

	public static boolean canDrawOverlays() {
		Context c = app();
		return c != null && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c));
	}

	public static void requestOverlay() {
		Context c = app();
		if (c == null || Build.VERSION.SDK_INT < 23) return;
		try {
			Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			c.startActivity(i);
		} catch (Exception e) {
			Log.w("LANTERN", "overlay settings failed", e);
		}
	}

	private static void act(JSONObject a) {
		synchronized (lock) { actions.put(a); }
	}

	private static JSONObject st() {
		synchronized (lock) { return state; }
	}

	private static PendingIntent openIntent(Context c) {
		Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
		if (open == null) return null;
		return PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private static Intent shareIntent(String text) {
		Intent send = new Intent(Intent.ACTION_SEND);
		send.setType("text/plain");
		send.putExtra(Intent.EXTRA_TEXT, text);
		Intent ch = Intent.createChooser(send, "Пригласить друга");
		ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return ch;
	}

	// ------------------------------------------------------------------ служба

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent != null ? intent.getAction() : null;
		if (A_CLOSE.equals(action)) {
			closeRoom();
			return START_NOT_STICKY;
		}
		self = this;
		Notification n = buildNotification();
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
				WifiManager wfm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
				wifi = wfm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF, "lantern:world");
				wifi.acquire();
			}
		} catch (Exception e) {
			Log.w("LANTERN", "locks failed", e);
		}
		showBubble();
		refresh();
		return START_NOT_STICKY;
	}

	private void closeRoom() {
		try {
			act(new JSONObject().put("a", "close"));
		} catch (Exception ignored) {}
		stopSelf();
	}

	private Notification buildNotification() {
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		Notification.Builder b;
		if (Build.VERSION.SDK_INT >= 26) {
			nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Мир открыт для друзей", NotificationManager.IMPORTANCE_LOW));
			b = new Notification.Builder(this, CHANNEL);
		} else {
			b = new Notification.Builder(this);
		}
		PendingIntent open = openIntent(this);
		if (open != null) b.setContentIntent(open);
		JSONObject s = st();
		String title = title0, text = text0;
		if (s.has("code")) {
			boolean ok = s.optBoolean("ok", false);
			int n = s.optJSONArray("peers") != null ? s.optJSONArray("peers").length() : 0;
			title = ok ? "Мир открыт · друзей в игре: " + n + " · код " + s.optString("code") : "Мир не виден друзьям";
			text = ok ? "Друзья могут зайти прямо сейчас" : s.optString("why", "Вернитесь в мир или откройте LANTERN");
			String share = s.optString("share", "");
			if (!share.isEmpty()) {
				b.addAction(new Notification.Action.Builder(null, "Поделиться",
					PendingIntent.getActivity(this, 1, shareIntent(share), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
			}
			Intent close = new Intent(this, LanternRelayService.class).setAction(A_CLOSE);
			b.addAction(new Notification.Action.Builder(null, "Закрыть",
				PendingIntent.getService(this, 2, close, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
		}
		b.setContentTitle(title).setContentText(text).setSmallIcon(getApplicationInfo().icon).setOngoing(true).setOnlyAlertOnce(true);
		return b.build();
	}

	// ------------------------------------------------------------------ плавающая кнопка и панель

	private int dp(float v) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
	}

	private int overlayType() {
		return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
	}

	private void showBubble() {
		if (bubble != null || !canDrawOverlays()) return;
		try {
			wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			bubble = new TextView(this);
			bubble.setGravity(Gravity.CENTER);
			bubble.setTextColor(Color.WHITE);
			bubble.setTypeface(Typeface.DEFAULT_BOLD);
			bubble.setTextSize(15);
			bubble.setElevation(dp(6));
			bubbleLp = new WindowManager.LayoutParams(dp(54), dp(54), overlayType(),
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
			bubbleLp.gravity = Gravity.TOP | Gravity.START;
			bubbleLp.x = dp(8);
			bubbleLp.y = dp(140);
			bubble.setOnTouchListener(new View.OnTouchListener() {
				float sx, sy; int ox, oy; boolean moved;
				@Override
				public boolean onTouch(View v, MotionEvent e) {
					switch (e.getAction()) {
						case MotionEvent.ACTION_DOWN:
							sx = e.getRawX(); sy = e.getRawY(); ox = bubbleLp.x; oy = bubbleLp.y; moved = false;
							return true;
						case MotionEvent.ACTION_MOVE:
							int dx = (int) (e.getRawX() - sx), dy = (int) (e.getRawY() - sy);
							if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true;
							if (moved) {
								bubbleLp.x = ox + dx; bubbleLp.y = Math.max(0, oy + dy);
								try { wm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) {}
							}
							return true;
						case MotionEvent.ACTION_UP:
							if (!moved) togglePanel();
							return true;
					}
					return false;
				}
			});
			wm.addView(bubble, bubbleLp);
		} catch (Exception e) {
			Log.w("LANTERN", "bubble failed", e);
			bubble = null;
		}
	}

	private void hideOverlay() {
		try {
			if (panel != null && panelOpen) wm.removeView(panel);
			if (bubble != null) wm.removeView(bubble);
		} catch (Exception ignored) {}
		panel = null;
		bubble = null;
		panelOpen = false;
	}

	private void togglePanel() {
		if (wm == null) return;
		if (panelOpen) {
			try { wm.removeView(panel); } catch (Exception ignored) {}
			panelOpen = false;
			confirmClose = false;
			return;
		}
		panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(16), dp(14), dp(16), dp(14));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(BG);
		bg.setCornerRadius(dp(18));
		panel.setBackground(bg);
		panel.setElevation(dp(10));
		panelLp = new WindowManager.LayoutParams(dp(300), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
			PixelFormat.TRANSLUCENT);
		panelLp.gravity = Gravity.TOP | Gravity.START;
		panelLp.x = Math.max(dp(4), bubbleLp.x);
		panelLp.y = bubbleLp.y + dp(62);
		panel.setOnTouchListener((v, e) -> {
			if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { togglePanel(); return true; }
			return false;
		});
		try {
			wm.addView(panel, panelLp);
			panelOpen = true;
			lastKey = "";
			refresh();
		} catch (Exception e) {
			Log.w("LANTERN", "panel failed", e);
		}
	}

	private TextView text(String s, float size, int color, boolean bold) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(size);
		t.setTextColor(color);
		if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}

	private Button btn(String s, int color, View.OnClickListener on) {
		Button b = new Button(this);
		b.setText(s);
		b.setAllCaps(false);
		b.setTextSize(13);
		b.setTextColor(Color.WHITE);
		b.setMinHeight(0);
		b.setMinimumHeight(0);
		b.setPadding(dp(10), dp(6), dp(10), dp(6));
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(dp(10));
		b.setBackground(d);
		b.setOnClickListener(on);
		return b;
	}

	private LinearLayout.LayoutParams wrap(int top) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		p.topMargin = dp(top);
		p.rightMargin = dp(6);
		return p;
	}

	void refresh() {
		JSONObject s = st();
		boolean ok = s.optBoolean("ok", false);
		JSONArray peers = s.optJSONArray("peers");
		int n = peers != null ? peers.length() : 0;
		String note = ok + "|" + n + "|" + s.optString("code") + "|" + s.optString("why") + "|" + s.optString("share");
		if (!note.equals(lastNote)) {
			lastNote = note;
			try {
				NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				nm.notify(ID, buildNotification());
			} catch (Exception ignored) {}
		}
		if (bubble == null) showBubble();
		if (bubble != null) {
			GradientDrawable d = new GradientDrawable();
			d.setShape(GradientDrawable.OVAL);
			d.setColor(ok ? GREEN : RED);
			d.setStroke(dp(3), 0xCCFFFFFF);
			bubble.setBackground(d);
			bubble.setText(ok ? (n > 0 ? String.valueOf(n) : "✓") : "!");
		}
		if (!panelOpen || panel == null) return;
		String key = s.toString() + confirmClose;
		if (key.equals(lastKey)) return;
		lastKey = key;
		panel.removeAllViews();
		panel.addView(text(ok ? "Мир открыт для друзей ✓" : "Нет связи — друзья не видят мир", 16, ok ? GREEN : RED, true));
		if (!ok) panel.addView(text(s.optString("why", "Вернитесь в мир или откройте LANTERN"), 13, MUTED, false));
		panel.addView(text(s.optString("world", ""), 13, MUTED, false));
		TextView code = text(s.optString("code", "······"), 30, TEXT, true);
		code.setLetterSpacing(0.12f);
		panel.addView(code);
		final String share = s.optString("share", "");
		LinearLayout row = new LinearLayout(this);
		if (!share.isEmpty()) {
			row.addView(btn("Поделиться", 0xFF2F81F7, v -> {
				try { startActivity(shareIntent(share)); } catch (Exception e) { Log.w("LANTERN", "share failed", e); }
				togglePanel();
			}), wrap(4));
		}
		row.addView(btn("Открыть LANTERN", 0xFF30363D, v -> {
			PendingIntent pi = openIntent(this);
			try { if (pi != null) pi.send(); } catch (Exception ignored) {}
			togglePanel();
		}), wrap(4));
		panel.addView(row);
		// кто может зайти
		panel.addView(text("Кто может зайти", 13, MUTED, true), wrap(12));
		final boolean pub = s.optBoolean("public", false);
		LinearLayout who = new LinearLayout(this);
		who.addView(btn((pub ? "" : "● ") + "Только друзья", pub ? 0xFF30363D : 0xFF2F81F7, v -> pushAction("public", false)), wrap(4));
		who.addView(btn((pub ? "● " : "") + "Все в LANTERN", pub ? 0xFF2F81F7 : 0xFF30363D, v -> pushAction("public", true)), wrap(4));
		panel.addView(who);
		// игроки
		panel.addView(text(n > 0 ? "В мире: " + n : "Пока никто не зашёл", 13, MUTED, true), wrap(12));
		LinearLayout list = new LinearLayout(this);
		list.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; peers != null && i < peers.length(); i++) {
			JSONObject p = peers.optJSONObject(i);
			if (p == null) continue;
			final String pid = p.optString("peer");
			LinearLayout r = new LinearLayout(this);
			r.setGravity(Gravity.CENTER_VERTICAL);
			TextView name = text(p.optString("name", "Гость"), 14, TEXT, false);
			name.setSingleLine(true);
			r.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			r.addView(btn("Выгнать", 0xFF6E5A1E, v -> pushPeer("kick", pid)), wrap(4));
			r.addView(btn("Забанить", 0xFF8B2C2C, v -> pushPeer("ban", pid)), wrap(4));
			list.addView(r);
		}
		if (n > 4) {
			ScrollView sv = new ScrollView(this);
			sv.addView(list);
			panel.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));
		} else {
			panel.addView(list);
		}
		LinearLayout bottom = new LinearLayout(this);
		bottom.addView(btn(confirmClose ? "Точно закрыть?" : "Закрыть комнату", 0xFFB62324, v -> {
			if (!confirmClose) { confirmClose = true; refresh(); return; }
			hideOverlay();
			closeRoom();
		}), wrap(14));
		bottom.addView(btn("Свернуть", 0xFF30363D, v -> togglePanel()), wrap(14));
		panel.addView(bottom);
	}

	private void pushPeer(String a, String peer) {
		try { act(new JSONObject().put("a", a).put("peer", peer)); } catch (Exception ignored) {}
	}

	private void pushAction(String a, boolean on) {
		try { act(new JSONObject().put("a", a).put("on", on)); } catch (Exception ignored) {}
		// сразу показываем выбор, не дожидаясь LANTERN
		try {
			synchronized (lock) { state = new JSONObject(state.toString()).put("public", on); }
		} catch (Exception ignored) {}
		refresh();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		stopSelf();
	}

	@Override
	public void onDestroy() {
		hideOverlay();
		if (self == this) self = null;
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
