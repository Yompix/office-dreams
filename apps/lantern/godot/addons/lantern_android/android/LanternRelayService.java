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
//   maker() / overlayState() / openVendorSettings() — 1.7: производитель (Xiaomi и др.), что вышло с кнопкой (для журнала),
//                                          экран настроек «другие разрешения / автозапуск / батарея» этого производителя
// update/takeActions/event вызываются и из потока моста (основной цикл Godot в фоне стоит) — всё рисуем через Handler.

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
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
	// 1.7 «ночной лагерь»: тёмное стекло цвета индиго, янтарный акцент (как в приложении — scripts/ui/ui.gd)
	private static final int GREEN = 0xFF5FD38A, RED = 0xFFFF6B6B, YELLOW = 0xFFFFC857, BG = 0xF2101326, BG2 = 0xF21A1D38, TEXT = 0xFFF4F2EE, MUTED = 0xFFB3B4C8,
		ACCENT = 0xFFFFB648, ACCENT2 = 0xFFFF8A3D, ON_ACCENT = 0xFF1A1206, BTN = 0x26FFFFFF, DANGER = 0x40FF6B6B, LINE = 0x33FFFFFF;
	private static volatile String bubbleState = "не показывалась";

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
	private View panelRoot;
	private WindowManager.LayoutParams panelLp;
	private boolean panelOpen = false;
	private boolean confirmClose = false;
	private String morePeer = "";   // у кого раскрыто «⋯» (кнопка «Забанить»)
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

	// 1.6: вход к другу — только служба с уведомлением (без кнопки поверх игры: она для хозяина мира)
	static volatile boolean joinOnly = false;

	public static void startJoin(String title, String text) {
		joinOnly = true;
		launch(title, text);
	}

	public static void start(String title, String text) {
		joinOnly = false;
		launch(title, text);
	}

	private static void launch(String title, String text) {
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

	public static String maker() {
		return (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE).trim();
	}

	// Для журнала «Сообщить о проблеме»: добавлена ли кнопка поверх игры и почему нет.
	public static String overlayState() {
		Context c = app();
		String bat = "?";
		try {
			if (c != null && Build.VERSION.SDK_INT >= 23) {
				PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
				bat = pm.isIgnoringBatteryOptimizations(c.getPackageName()) ? "без ограничений" : "с ограничениями";
			}
		} catch (Exception ignored) {}
		return "кнопка: " + bubbleState + "; служба: " + (self != null ? "работает" : "нет") + (joinOnly ? " (вход к другу)" : "") + "; батарея: " + bat;
	}

	private static boolean tryStart(Context c, Intent i) {
		try {
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			if (i.resolveActivity(c.getPackageManager()) == null) return false;
			c.startActivity(i);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// Экран настроек производителя. Разрешений не запрашиваем (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS в Google Play
	// ограничен) — только открываем нужный экран; не нашёлся — «О приложении».
	public static String openVendorSettings() {
		Context c = app();
		if (c == null) return "нет контекста";
		String pkg = c.getPackageName();
		String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
		if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
			Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
			i.putExtra("extra_pkgname", pkg);
			if (tryStart(c, i)) return "MIUI: другие разрешения";
			i = new Intent("miui.intent.action.APP_PERM_EDITOR");
			i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
			i.putExtra("extra_pkgname", pkg);
			if (tryStart(c, i)) return "MIUI: редактор разрешений";
		}
		Intent d = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
		if (tryStart(c, d)) return "о приложении";
		if (Build.VERSION.SDK_INT >= 23 && tryStart(c, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return "список оптимизации батареи";
		return "не открылось";
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
		if (joinOnly || bubble != null) return;
		if (!canDrawOverlays()) { bubbleState = "нет разрешения «поверх других приложений»"; return; }
		try {
			wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			bubble = new TextView(this);
			bubble.setGravity(Gravity.CENTER);
			bubble.setTextColor(ACCENT);
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
			bubbleState = "добавлена (TYPE " + overlayType() + ")";
		} catch (Exception e) {
			Log.w("LANTERN", "bubble failed", e);
			bubbleState = "ошибка: " + e.getClass().getSimpleName() + " " + e.getMessage();
			bubble = null;
		}
	}

	private void hideOverlay() {
		try {
			if (panel != null && panelOpen) wm.removeView(panelRoot);
			if (bubble != null) wm.removeView(bubble);
		} catch (Exception ignored) {}
		panel = null;
		bubble = null;
		panelOpen = false;
		bubbleState = "убрана (комната закрыта)";
	}

	private void togglePanel() {
		if (wm == null) return;
		if (panelOpen) {
			try { wm.removeView(panelRoot); } catch (Exception ignored) {}
			panelOpen = false;
			confirmClose = false;
			return;
		}
		// боковая панель справа на всю высоту (как в списках игр), содержимое прокручивается
		panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(16), dp(16), dp(16), dp(16));
		GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BG2, BG});
		bg.setCornerRadii(new float[] {dp(28), dp(28), 0, 0, 0, 0, dp(28), dp(28)});
		bg.setStroke(dp(1), LINE);
		ScrollView frame = new ScrollView(this);
		frame.setBackground(bg);
		frame.setElevation(dp(12));
		frame.addView(panel);
		panelRoot = frame;
		panelLp = new WindowManager.LayoutParams(dp(320), WindowManager.LayoutParams.MATCH_PARENT, overlayType(),
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
			PixelFormat.TRANSLUCENT);
		panelLp.gravity = Gravity.TOP | Gravity.END;
		panelLp.windowAnimations = android.R.style.Animation_Translucent;
		frame.setOnTouchListener((v, e) -> {
			if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { togglePanel(); return true; }
			return false;
		});
		try {
			wm.addView(frame, panelLp);
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
		b.setTextColor(color == ACCENT ? ON_ACCENT : TEXT);
		b.setMinHeight(0);
		b.setMinimumHeight(0);
		b.setPadding(dp(14), dp(9), dp(14), dp(9));
		GradientDrawable d = color == ACCENT ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {ACCENT, ACCENT2}) : new GradientDrawable();
		if (color != ACCENT) { d.setColor(color); d.setStroke(dp(1), color == DANGER ? 0x55FF6B6B : LINE); }
		d.setCornerRadius(dp(14));
		b.setStateListAnimator(null);
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
			d.setColor(ok ? 0xF2101326 : 0xF2401A1A);
			d.setStroke(dp(3), ok ? ACCENT : RED);
			bubble.setBackground(d);
			bubble.setText(ok ? (n > 0 ? String.valueOf(n) : "✓") : "!");
			bubble.setTextColor(ok ? ACCENT : 0xFFFFFFFF);
		}
		if (!panelOpen || panel == null) return;
		String key = s.toString() + confirmClose;
		if (key.equals(lastKey)) return;
		lastKey = key;
		panel.removeAllViews();
		// --- шапка: номер комнаты и связь
		String num = s.optString("num", "");
		TextView nt = text(num.isEmpty() ? s.optString("code", "······") : "№ " + num, 24, TEXT, true);
		nt.setLetterSpacing(0.06f);
		panel.addView(nt);
		String q = s.optString("quality", "");
		int qc = "great".equals(q) ? GREEN : "good".equals(q) ? YELLOW : "poor".equals(q) ? RED : MUTED;
		String qt = "great".equals(q) ? "● связь отличная" : "good".equals(q) ? "● связь хорошая" : "poor".equals(q) ? "● связь так себе" : "● проверяем связь…";
		panel.addView(text(ok ? qt : "● друзья не видят мир", 13, ok ? qc : RED, true));
		if (!ok) panel.addView(text(s.optString("why", "Вернитесь в мир или откройте LANTERN"), 13, MUTED, false));
		panel.addView(text(s.optString("world", "") + (num.isEmpty() ? "" : " · код " + s.optString("code", "")), 13, MUTED, false));
		// --- игроки
		panel.addView(text("Игроки" + (n > 0 ? " · " + n : ""), 15, TEXT, true), wrap(18));
		if (n == 0) panel.addView(text("Пока никто не зашёл", 13, MUTED, false));
		for (int i = 0; peers != null && i < peers.length(); i++) {
			JSONObject p = peers.optJSONObject(i);
			if (p == null) continue;
			final String pid = p.optString("peer");
			LinearLayout r = new LinearLayout(this);
			r.setGravity(Gravity.CENTER_VERTICAL);
			TextView name = text(p.optString("name", "Гость"), 14, TEXT, false);
			name.setSingleLine(true);
			r.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			r.addView(btn("Выгнать", BTN, v -> pushPeer("kick", pid)), wrap(6));
			if (pid.equals(morePeer)) r.addView(btn("Забанить", DANGER, v -> { morePeer = ""; pushPeer("ban", pid); }), wrap(6));
			else r.addView(btn("⋯", BTN, v -> { morePeer = pid; lastKey = ""; refresh(); }), wrap(6));
			panel.addView(r);
		}
		// --- пригласить
		panel.addView(text("Пригласить друзей", 15, TEXT, true), wrap(18));
		final String share = s.optString("share", "");
		LinearLayout row = new LinearLayout(this);
		if (!share.isEmpty()) {
			row.addView(btn("Поделиться ссылкой", ACCENT, v -> {
				try { startActivity(shareIntent(share)); } catch (Exception e) { Log.w("LANTERN", "share failed", e); }
				togglePanel();
			}), wrap(6));
		}
		final String copyText = num.isEmpty() ? s.optString("code", "") : num;
		row.addView(btn(num.isEmpty() ? "Скопировать код" : "Скопировать номер", BTN, v -> {
			try {
				ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				cm.setPrimaryClip(ClipData.newPlainText("LANTERN", copyText));
				Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
			} catch (Exception ignored) {}
		}), wrap(6));
		panel.addView(row);
		JSONArray fr = s.optJSONArray("friends");
		for (int i = 0; fr != null && i < fr.length(); i++) {
			JSONObject f = fr.optJSONObject(i);
			if (f == null) continue;
			final int fid = f.optInt("id");
			LinearLayout r = new LinearLayout(this);
			r.setGravity(Gravity.CENTER_VERTICAL);
			TextView name = text(f.optString("nick", ""), 14, TEXT, false);
			name.setSingleLine(true);
			r.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
			r.addView(btn("Пригласить", BTN, v -> {
				try { act(new JSONObject().put("a", "invite").put("id", fid)); } catch (Exception ignored) {}
				Toast.makeText(this, "Приглашение отправлено", Toast.LENGTH_SHORT).show();
			}), wrap(6));
			panel.addView(r);
		}
		// --- кто видит
		panel.addView(text("Кто видит игру", 13, MUTED, true), wrap(18));
		final boolean pub = s.optBoolean("public", false);
		LinearLayout who = new LinearLayout(this);
		who.addView(btn("Все в LANTERN", pub ? ACCENT : BTN, v -> pushAction("public", true)), wrap(6));
		who.addView(btn("Только по коду", pub ? BTN : ACCENT, v -> pushAction("public", false)), wrap(6));
		panel.addView(who);
		// --- защита мира: мир из самого Minecraft снаружи не настроить (трафик зашифрован) — одна строка без «мёртвых» кнопок
		panel.addView(text("Защита мира", 13, MUTED, true), wrap(18));
		panel.addView(text("Без PvP, без взрывов, огонь, вещи после смерти — эти правила включаются в настройках самого мира в Minecraft.", 13, MUTED, false), wrap(4));
		LinearLayout bottom = new LinearLayout(this);
		bottom.addView(btn("Открыть LANTERN", BTN, v -> {
			PendingIntent pi = openIntent(this);
			try { if (pi != null) pi.send(); } catch (Exception ignored) {}
			togglePanel();
		}), wrap(20));
		bottom.addView(btn("Свернуть", BTN, v -> togglePanel()), wrap(20));
		panel.addView(bottom);
		panel.addView(btn(confirmClose ? "Точно закрыть?" : "Закрыть комнату", DANGER, v -> {
			if (!confirmClose) { confirmClose = true; refresh(); return; }
			hideOverlay();
			closeRoom();
		}), wrap(8));
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
