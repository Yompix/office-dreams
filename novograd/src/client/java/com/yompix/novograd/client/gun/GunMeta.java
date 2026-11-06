package com.yompix.novograd.client.gun;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Точки модели для анимаций (пишет tools/gen_gun_geo.py в assets/novograd/guns/meta.json):
 * где рукоять, цевьё, дуло, окно выброса, прицел, куда выпадает магазин. Все в «пикселях предмета» (центр 8, 8, 8).
 */
public final class GunMeta {
    public final String id;
    public float[] grip, support, muzzle, eject, sight, magDir, magHand, boltHand;
    public float slideKick = 1.5f, fpScale = 0.8f;
    public final Map<String, float[]> pivots = new HashMap<>();
    /** Мастерская: глушитель (x, y, z дула, радиус), прицел (x, низ, центр z), рукоятка (x, низ цевья, z); null — не ставится. */
    public float[] silAt, scopeAt, gripAt;
    /** Прицел-коллиматор («точка»), иначе — труба оптики. */
    public boolean scopeDot;

    private GunMeta(String id) {
        this.id = id;
    }

    private static Map<String, GunMeta> all;

    public static GunMeta get(String id) {
        if (all == null) load();
        GunMeta m = all.get(id);
        return m != null ? m : all.getOrDefault("pistol", fallback(id));
    }

    public static void reload() {
        all = null;
    }

    private static void load() {
        all = new HashMap<>();
        try {
            var res = MinecraftClient.getInstance().getResourceManager().getResource(Novograd.id("guns/meta.json"));
            if (res.isEmpty()) return;
            try (var in = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                for (var e : o.entrySet()) {
                    JsonObject g = e.getValue().getAsJsonObject();
                    GunMeta m = new GunMeta(e.getKey());
                    m.grip = v(g, "grip");
                    m.support = v(g, "support");
                    m.muzzle = v(g, "muzzle");
                    m.eject = v(g, "eject");
                    m.sight = v(g, "sight");
                    m.magDir = v(g, "mag_dir");
                    m.magHand = v(g, "mag_hand");
                    m.boltHand = g.has("bolt_hand") ? v(g, "bolt_hand") : null;
                    if (g.has("slide_kick")) m.slideKick = g.get("slide_kick").getAsFloat();
                    if (g.has("fp_scale")) m.fpScale = g.get("fp_scale").getAsFloat();
                    if (g.has("attach")) {
                        JsonObject at = g.getAsJsonObject("attach");
                        if (at.has("sil")) m.silAt = arr(at.getAsJsonArray("sil"));
                        if (at.has("grip")) m.gripAt = arr(at.getAsJsonArray("grip"));
                        if (at.has("scope")) {
                            JsonArray sc = at.getAsJsonArray("scope");
                            m.scopeAt = new float[]{sc.get(0).getAsFloat(), sc.get(1).getAsFloat(), sc.get(2).getAsFloat()};
                            m.scopeDot = sc.size() > 3 && sc.get(3).getAsString().equals("dot");
                        }
                    }
                    if (g.has("pivots"))
                        for (var p : g.getAsJsonObject("pivots").entrySet()) m.pivots.put(p.getKey(), arr(p.getValue().getAsJsonArray()));
                    all.put(e.getKey(), m);
                }
            }
        } catch (Exception ex) {
            Novograd.LOG.warn("guns/meta.json: {}", ex.toString());
        }
    }

    private static GunMeta fallback(String id) {
        GunMeta m = new GunMeta(id);
        m.grip = new float[]{8, 5.6f, 10};
        m.support = new float[]{8, 4.6f, 9.4f};
        m.muzzle = new float[]{8, 9, 1.6f};
        m.eject = new float[]{9, 10, 5};
        m.sight = new float[]{8, 10.8f, 10.4f};
        m.magDir = new float[]{0, -1, 0};
        m.magHand = new float[]{8, 3, 9.6f};
        return m;
    }

    private static float[] v(JsonObject g, String k) {
        return arr(g.getAsJsonArray(k));
    }

    private static float[] arr(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }
}
