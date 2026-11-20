package com.yompix.novograd.client.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 1.0: рот в такт озвучке. Огибающая громкости каждой реплики (tools/gen_lipsync.py → assets/novograd/lipsync.json,
 * 20 значений в секунду, 0…9) и время звучания текущей реплики (Voice).
 */
public final class Lipsync {
    private Lipsync() {}

    private static JsonObject data;

    private static JsonObject data() {
        if (data == null) {
            data = new JsonObject();
            try {
                var r = MinecraftClient.getInstance().getResourceManager().getResource(Novograd.id("lipsync.json"));
                if (r.isPresent()) try (var in = new InputStreamReader(r.get().getInputStream(), StandardCharsets.UTF_8)) {
                    data = JsonParser.parseReader(in).getAsJsonObject();
                }
            } catch (Exception e) {
                Novograd.LOG.warn("lipsync.json не прочитан: {}", e.toString());
            }
        }
        return data;
    }

    /** Открытие рта 0…9 у реплики id через t секунд от начала (−1 — нет данных). */
    public static int level(String id, double t) {
        if (id == null || id.isEmpty() || t < 0) return -1;
        JsonObject d = data();
        if (!d.has(id)) return -1;
        String s = d.get(id).getAsString();
        double f = t * 20;
        int i = (int) f;
        if (i >= s.length()) return 0;
        int a = s.charAt(i) - '0', b = i + 1 < s.length() ? s.charAt(i + 1) - '0' : 0;
        return (int) Math.round(a + (b - a) * (f - i));
    }

    /** Ударный слог сейчас (пик огибающей): для кивка головой говорящего. */
    public static double stress(String id, double t) {
        int now = level(id, t), before = level(id, t - 0.15);
        return now >= 7 && now - before >= 3 ? 1 : 0;
    }
}
