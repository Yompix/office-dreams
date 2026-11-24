package com.yompix.yurk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/**
 * The system VPN: every app's traffic goes into a tun device, Xray reads it (fd) and sends it through our node.
 * YURK itself is excluded from the VPN, so Xray's own connections go out directly (no loop).
 */
public class YurkVpnService extends VpnService {
    static final String ACTION_START = "com.yompix.yurk.START";
    static final String ACTION_STOP = "com.yompix.yurk.STOP";
    static volatile String state = "off";

    private static final String CH = "vpn";
    private ParcelFileDescriptor tun;
    private CoreController core;
    private Thread worker;

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if (ACTION_STOP.equals(a)) { stopVpn("off"); return START_NOT_STICKY; }
        if (ACTION_START.equals(a)) {
            final int node = i.getIntExtra("node", 0);
            final String mode = i.getStringExtra("mode");
            foreground(getString(R.string.n_connecting));
            stopCore();
            setState("connecting");
            worker = new Thread(() -> connect(node, mode == null ? "ws" : mode));
            worker.start();
        }
        return START_NOT_STICKY;
    }

    private void connect(int node, String mode) {
        SharedPreferences p = getSharedPreferences("yurk", MODE_PRIVATE);
        String token = p.getString("token", "");
        try {
            if (token.isEmpty()) throw new Exception("login");
            // fresh config from the panel; if the panel is unreachable, the last good one
            Http.Result r = Http.request("GET", BuildConfig.PANEL + "/app-config/" + token + "?node=" + node + "&mode=" + mode, null, token);
            String cfg; String name;
            if (r.code == 200) {
                JSONObject j = new JSONObject(r.body);
                cfg = j.getJSONObject("config").toString();
                name = j.getJSONObject("node").optString("name", "YURK");
                p.edit().putString("config", cfg).putString("configName", name).apply();
            } else if (r.code == 0 && p.contains("config")) {
                cfg = p.getString("config", "");
                name = p.getString("configName", "YURK");
            } else {
                String err = "server";
                try { err = new JSONObject(r.body).optString("error", err); } catch (Exception ignored) { }
                throw new Exception(err); // expired | quota | network | nodes
            }

            Builder b = new Builder()
                    .setSession("YURK")
                    .setMtu(1500)
                    .addAddress("10.18.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fd18::1", 126)
                    .addRoute("::", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8");
            b.addDisallowedApplication(getPackageName());
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
            tun = b.establish();
            if (tun == null) throw new Exception("permission");

            Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "");
            core = Libv2ray.newCoreController(new CoreCallbackHandler() {
                @Override public long startup() { return 0; }
                @Override public long shutdown() { return 0; }
                @Override public long onEmitStatus(long l, String s) { return 0; }
            });
            core.startLoop(cfg, tun.getFd());
            foreground(getString(R.string.n_on) + " · " + name);
            setState("on");
        } catch (Throwable e) {
            String m = e.getMessage() == null ? "error" : e.getMessage();
            stopVpn("error:" + (m.length() > 80 ? m.substring(0, 80) : m));
        }
    }

    private void stopCore() {
        try { if (core != null) core.stopLoop(); } catch (Exception ignored) { }
        core = null;
        try { if (tun != null) tun.close(); } catch (Exception ignored) { }
        tun = null;
    }

    private void stopVpn(String st) {
        stopCore();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        setState(st);
        stopSelf();
    }

    @Override public void onRevoke() { stopVpn("off"); } // another VPN took over / the user turned it off in settings
    @Override public void onDestroy() { stopCore(); if (!state.startsWith("error")) state = "off"; super.onDestroy(); }

    private void setState(String s) {
        state = s;
        MainActivity.pushState(s);
    }

    private void foreground(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(new NotificationChannel(CH, getString(R.string.n_channel), NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, YurkVpnService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("YURK VPN")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.n_stop), stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }
}
