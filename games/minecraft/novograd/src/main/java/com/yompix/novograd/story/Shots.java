package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.novograd.Novograd;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 1.0 «Кино»: постановка кат-сцен из офлайн-планировщика (tools/shot_planner.py → novograd_shots.json).
 *
 * <p>Файл переопределяет планы камеры у выбранных сцен («глава:миссия:шаг») и может добавить реплики таймлайна
 * (жесты, взгляды, рукопожатия). Сюжет, реплики и механика шага не меняются — только как это снято.
 *
 * <p>План с ключами: {"keys": [[t, x, y, z, yaw, pitch, fov, roll], …], "sec": …, "subject": "актёр", "aim": [x, y, z],
 * "hand": амплитуда «ручной камеры» в градусах}. Между ключами — кривая Безье (Катмулл — Ром) по позиции, углам и FOV.
 * subject/aim: где планировщик ждал героя; если в игре он стоит иначе — кадр доворачивается на разницу (композиция
 * сохраняется). -Dnovograd.shots=false — старые планы (для сравнения «до/после»).
 */
public final class Shots {
    private Shots() {}

    public static final boolean ON = !"false".equals(System.getProperty("novograd.shots"));

    static void apply(JsonObject root) {
        if (!ON) return;
        JsonObject over;
        try (var in = Shots.class.getResourceAsStream("/novograd_shots.json")) {
            if (in == null) return;
            over = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Novograd.LOG.warn("novograd_shots.json не прочитан: {}", e.toString());
            return;
        }
        JsonArray chs = root.getAsJsonArray("chapters");
        int n = 0;
        for (Map.Entry<String, JsonElement> en : over.getAsJsonObject("scenes").entrySet()) {
            String[] k = en.getKey().split(":");
            try {
                JsonObject step = chs.get(Integer.parseInt(k[0])).getAsJsonObject().getAsJsonArray("missions").get(Integer.parseInt(k[1]))
                        .getAsJsonObject().getAsJsonArray("steps").get(Integer.parseInt(k[2])).getAsJsonObject();
                if (!"cutscene".equals(Story.str(step, "type", ""))) continue;
                JsonObject o = en.getValue().getAsJsonObject();
                if (o.has("shots")) step.add("shots", o.getAsJsonArray("shots"));
                if (o.has("cues")) {
                    JsonArray acts = step.has("actors") ? step.getAsJsonArray("actors") : new JsonArray();
                    for (JsonElement c : o.getAsJsonArray("cues")) acts.add(c);
                    step.add("actors", acts);
                }
                n++;
            } catch (RuntimeException e) {
                Novograd.LOG.warn("novograd_shots.json: сцена {} пропущена: {}", en.getKey(), e.toString());
            }
        }
        Novograd.LOG.info("Постановка 1.0: переснято кат-сцен {}", n);
    }

    /** План с ключами → пакет клиенту. */
    static void toNbt(JsonObject sh, NbtCompound sn) {
        NbtList keys = new NbtList();
        for (JsonElement e : sh.getAsJsonArray("keys")) {
            JsonArray k = e.getAsJsonArray();
            for (int i = 0; i < 8; i++) keys.add(NbtDouble.of(i < k.size() ? k.get(i).getAsDouble() : i == 6 ? 70 : 0));
        }
        sn.put("keys", keys);
        if (sh.has("subject")) sn.putString("subject", sh.get("subject").getAsString());
        if (sh.has("aim")) {
            NbtList a = new NbtList();
            for (JsonElement e : sh.getAsJsonArray("aim")) a.add(NbtDouble.of(e.getAsDouble()));
            sn.put("aim", a);
        }
        if (sh.has("hand")) sn.putFloat("hand", sh.get("hand").getAsFloat());
        if (sh.has("name")) sn.putString("name", sh.get("name").getAsString());
    }
}
