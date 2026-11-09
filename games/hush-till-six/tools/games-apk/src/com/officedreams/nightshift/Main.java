package com.officedreams.nightshift;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/* Обёртка: игра лежит в assets и открывается во весь экран без интернета. */
public class Main extends Activity {
  private WebView web;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    web = new WebView(this);
    web.setBackgroundColor(0xff000000);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(true);
    s.setAllowFileAccessFromFileURLs(true);
    // ссылки наружу (поддержать игру в Telegram) открываются в браузере или Telegram, игра остаётся
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
        if (url.startsWith("file:")) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
        return true;
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/nightshift/index.html");
  }

  @Override public void onWindowFocusChanged(boolean f) {
    super.onWindowFocusChanged(f);
    if (f) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override protected void onPause() { super.onPause(); web.onPause(); web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null); }
  @Override protected void onResume() { super.onResume(); web.onResume(); }
  @Override public void onBackPressed() { web.evaluateJavascript("window.__ns && window.__ns.back && window.__ns.back()", null); }
}
