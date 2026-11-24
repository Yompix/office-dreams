package com.yompix.yurk;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

import org.json.JSONObject;

/** One screen: the YURK interface (the same page as the Telegram Mini App, bundled in assets) + a bridge to the VPN. */
public class MainActivity extends Activity {
    static volatile MainActivity live;
    private WebView web;
    private SharedPreferences prefs;
    private int pendingNode;
    private String pendingMode = "ws";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        live = this;
        prefs = getSharedPreferences("yurk", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.parseColor("#05060b"));
        getWindow().setNavigationBarColor(Color.parseColor("#05060b"));
        handleLogin(getIntent());

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#05060b"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                openExternal(r.getUrl().toString());
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "YurkNative");
        web.loadUrl("file:///android_asset/app.html");
        setContentView(web);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (handleLogin(i) && web != null) web.reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pushState(YurkVpnService.state); // the user may have turned it off from the notification
    }

    @Override
    protected void onDestroy() {
        if (live == this) live = null;
        super.onDestroy();
    }

    /** yurk://login/<token> — sign in; yurk://connect/<token>?node=N — sign in and turn the VPN on (the «Подключить» button in Telegram) */
    private boolean handleLogin(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || !"yurk".equals(d.getScheme())) return false;
        String host = d.getHost();
        if (!"login".equals(host) && !"connect".equals(host)) return false;
        String t = d.getLastPathSegment();
        if (t == null || !t.matches("[A-Za-z0-9_-]{16,64}")) return false;
        if (!t.equals(prefs.getString("token", ""))) prefs.edit().putString("token", t).remove("config").apply();
        if ("connect".equals(host)) {
            int node = 0;
            try { node = Integer.parseInt(d.getQueryParameter("node")); } catch (Exception ignored) { }
            final int n = node;
            getWindow().getDecorView().post(() -> new Bridge().connect(n, "ws")); // asks for VPN permission only the first time
        }
        return true;
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    private void startVpn() {
        Intent s = new Intent(this, YurkVpnService.class)
                .setAction(YurkVpnService.ACTION_START)
                .putExtra("node", pendingNode)
                .putExtra("mode", pendingMode);
        startForegroundService(s);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1) {
            if (res == RESULT_OK) startVpn();
            else pushState("off");
        }
    }

    /** VPN state to the page: off | connecting | on | error:<text> */
    static void pushState(String st) {
        MainActivity a = live;
        if (a == null || a.web == null) return;
        final String js = "window.onYurkState && window.onYurkState(" + JSONObject.quote(st) + ")";
        a.runOnUiThread(() -> a.web.evaluateJavascript(js, null));
    }

    /** Called from the page as window.YurkNative.* */
    class Bridge {
        @JavascriptInterface public String token() { return prefs.getString("token", ""); }
        @JavascriptInterface public String state() { return YurkVpnService.state; }
        @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface public void saveMe(String json) {
            try {
                JSONObject j = new JSONObject(json);
                String t = j.optString("token", "");
                if (t.matches("[A-Za-z0-9_-]{16,64}")) prefs.edit().putString("token", t).apply(); // after a key reset
            } catch (Exception ignored) { }
        }

        /** POST /api/<action> with the app token; the answer comes back as window.__yurkCb(id, code, json) */
        @JavascriptInterface public void api(final int id, final String action, final String body) {
            new Thread(() -> {
                Http.Result r = Http.request("POST", BuildConfig.PANEL + "/api/" + action.replaceAll("[^a-z]", ""), body, token());
                final String js = "window.__yurkCb(" + id + "," + r.code + "," + JSONObject.quote(r.body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        @JavascriptInterface public void connect(final int node, final String mode) {
            runOnUiThread(() -> {
                pendingNode = node;
                pendingMode = "reality".equals(mode) ? "reality" : "ws";
                Intent ask = VpnService.prepare(MainActivity.this);
                if (ask != null) startActivityForResult(ask, 1); // Android asks the user once
                else startVpn();
            });
        }

        @JavascriptInterface public void disconnect() {
            startService(new Intent(MainActivity.this, YurkVpnService.class).setAction(YurkVpnService.ACTION_STOP));
        }

        @JavascriptInterface public void open(String url) {
            if (url != null && (url.startsWith("https://") || url.startsWith("tg://"))) openExternal(url);
        }
    }
}
