package com.yompix.hushtillsix;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/* Hush Till Six для Android. Игра (папка public/games/nightshift) лежит в assets/game и открывается во WebView по адресу
   https://appassets.androidplatform.net/game/index.html: файлы отдаёт само приложение (shouldInterceptRequest) — без сервера и интернета,
   а у страницы настоящий https-адрес (сохранения в localStorage живут в данных приложения; file:// с XHR новые WebView не пускают).
   Свернули — пауза и тишина (игра открывает меню паузы); «Назад» — как Esc в игре, в главном меню — выход;
   процесс WebView убит системой (мало памяти) — WebView создаётся заново, а приложение не вылетает (сохранения остаются). */
public class MainActivity extends Activity {
  static final String HOST = "appassets.androidplatform.net", START = "https://" + HOST + "/game/index.html";
  static final boolean WEB_DEBUG = false;   // true — только в проверочной сборке (build.js --webview-debug): страницу видят Appium/chrome://inspect
  static final Map<String, String> MIME = new HashMap<>();
  static {
    String[][] t = { { "html", "text/html" }, { "js", "text/javascript" }, { "json", "application/json" }, { "css", "text/css" }, { "txt", "text/plain" },
      { "png", "image/png" }, { "jpg", "image/jpeg" }, { "jpeg", "image/jpeg" }, { "webp", "image/webp" }, { "svg", "image/svg+xml" }, { "ico", "image/x-icon" },
      { "glb", "model/gltf-binary" }, { "mp3", "audio/mpeg" }, { "ogg", "audio/ogg" }, { "wav", "audio/wav" },
      { "woff", "font/woff" }, { "woff2", "font/woff2" }, { "ttf", "font/ttf" }, { "otf", "font/otf" }, { "wasm", "application/wasm" } };
    for (String[] p : t) MIME.put(p[0], p[1]);
  }
  private FrameLayout root;
  private WebView web;
  private Object backCb;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    Window w = getWindow();
    w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (Build.VERSION.SDK_INT >= 28) { WindowManager.LayoutParams lp = w.getAttributes(); lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; w.setAttributes(lp); }
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    // вырез камеры («чёлка»): игра рисуется рядом с ним, а не под ним — кнопки по углам не прячутся
    root.setOnApplyWindowInsetsListener((v, ins) -> {
      int l = 0, t = 0, r = 0, bo = 0;
      if (Build.VERSION.SDK_INT >= 28) { DisplayCutout c = ins.getDisplayCutout(); if (c != null) { l = c.getSafeInsetLeft(); t = c.getSafeInsetTop(); r = c.getSafeInsetRight(); bo = c.getSafeInsetBottom(); } }
      v.setPadding(l, t, r, bo);
      return ins;
    });
    setContentView(root);
    makeWeb();
    if (Build.VERSION.SDK_INT >= 33) backCb = Back33.register(this);
    immersive();
  }

  private void makeWeb() {
    if (WEB_DEBUG) WebView.setWebContentsDebuggingEnabled(true);
    web = new WebView(this);
    web.setBackgroundColor(Color.BLACK);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    web.setVerticalScrollBarEnabled(false);
    web.setHorizontalScrollBarEnabled(false);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setSupportZoom(false);
    s.setBuiltInZoomControls(false);
    s.setDisplayZoomControls(false);
    s.setTextZoom(100);   // крупный шрифт в настройках телефона не ломает интерфейс игры (у неё свои размеры)
    web.addJavascriptInterface(new Bridge(), "nsAppNative");
    web.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) { return serve(req.getUrl()); }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
        Uri u = req.getUrl(); if (HOST.equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }   // внешние ссылки (лицензии) — в браузере
        return true;
      }
      @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) { recreateWeb(); return true; }
    });
    root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    web.loadUrl(START);
  }

  private void recreateWeb() {
    if (web != null) { root.removeView(web); web.destroy(); web = null; }
    makeWeb();
  }

  // файл игры из assets/: путь адреса /game/... → assets/game/...; ?v=… (версии файлов) не важен
  private WebResourceResponse serve(Uri u) {
    if (!HOST.equals(u.getHost())) return null;
    String p = u.getPath(); if (p == null) p = "/";
    if (p.endsWith("/")) p += "index.html";
    p = p.substring(1);
    if (p.contains("..")) return notFound();
    int dot = p.lastIndexOf('.'); String ext = dot < 0 ? "" : p.substring(dot + 1).toLowerCase(Locale.ROOT);
    String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
    try {
      InputStream in = getAssets().open(Uri.decode(p));
      WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") || mime.endsWith("json") ? "utf-8" : null, in);
      Map<String, String> h = new HashMap<>(); h.put("Cache-Control", "no-cache"); r.setResponseHeaders(h);
      return r;
    } catch (IOException e) { return notFound(); }
  }

  private static WebResourceResponse notFound() {
    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
  }

  private void js(String code) { if (web != null) web.evaluateJavascript(code, null); }

  private void immersive() {
    Window w = getWindow();
    if (Build.VERSION.SDK_INT >= 30) {
      w.setDecorFitsSystemWindows(false);
      WindowInsetsController c = w.getInsetsController();
      if (c != null) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
    } else {
      w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

  @Override protected void onPause() {
    super.onPause();
    js("window.__nsApp && window.__nsApp.pause()");   // меню паузы и тишина, пока игра свёрнута (звонок, другое приложение)
    if (web != null) { web.onPause(); web.pauseTimers(); }
  }

  @Override protected void onResume() {
    super.onResume();
    if (web != null) { web.resumeTimers(); web.onResume(); }
    js("window.__nsApp && window.__nsApp.resume()");
    immersive();
  }

  @Override protected void onDestroy() {
    if (Build.VERSION.SDK_INT >= 33 && backCb != null) Back33.unregister(this, backCb);
    if (web != null) { root.removeView(web); web.destroy(); web = null; }
    super.onDestroy();
  }

  // «Назад» до Android 13; с Android 13 — Back33 (OnBackInvokedCallback: на Android 16 onBackPressed уже не вызывается)
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed() { js("window.__nsApp && window.__nsApp.back()"); }

  void back() { js("window.__nsApp && window.__nsApp.back()"); }

  static final class Back33 {
    static Object register(MainActivity a) {
      OnBackInvokedCallback cb = a::back;
      a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
      return cb;
    }
    static void unregister(MainActivity a, Object cb) { a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) cb); }
  }

  // мост для страницы: window.nsAppNative — по нему игра узнаёт, что она в приложении (без доната и спонсоров, кнопка «Выйти из игры»)
  final class Bridge {
    @JavascriptInterface public void quit() { runOnUiThread(MainActivity.this::finish); }
  }
}
