package com.yompix.glukhoman.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.glukhoman.client.GConfig;
import net.minecraft.client.MinecraftClient;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Язык субтитров (этап 4): реплика приходит с сервера по-русски и с id; клиент берёт перевод из
 * {@code assets/glukhoman/story/<язык>.json}. Язык — по настройке («как в игре» / ru / uk / en), запасной — русский.
 */
public final class StoryLang {
    private StoryLang() {}

    static final Map<String, JsonObject> CACHE = new HashMap<>();
    public static final String[] LANGS = {"auto", "ru_ru", "uk_ua", "en_us"};
    public static final String[] NAMES = {"как в игре", "русский", "українська", "English"};

    public static String lang() {
        String l = GConfig.get().subLang;
        if (l == null || l.equals("auto")) {
            var mc = MinecraftClient.getInstance();
            l = mc != null && mc.getLanguageManager() != null ? mc.getLanguageManager().getLanguage() : "ru_ru";
        }
        return l;
    }

    static JsonObject table() {
        String l = lang();
        if (l.startsWith("ru")) return null;
        return CACHE.computeIfAbsent(l, k -> {
            try (var in = StoryLang.class.getResourceAsStream("/assets/glukhoman/story/" + k + ".json")) {
                if (in == null) return new JsonObject();
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                return new JsonObject();
            }
        });
    }

    /** Текст реплики по id; нет перевода — как пришло. */
    public static String text(String id, String fallback) {
        JsonObject t = table();
        if (t == null || id == null || id.isEmpty() || !t.has("lines")) return fallback;
        var v = t.getAsJsonObject("lines").get(id);
        return v == null ? fallback : v.getAsString();
    }

    /** Имя говорящего по ключу (zakhar, vika…). */
    public static String who(String key, String fallback) {
        JsonObject t = table();
        if (t == null || key == null || key.isEmpty() || !t.has("who")) return fallback;
        var v = t.getAsJsonObject("who").get(key);
        return v == null ? fallback : v.getAsString();
    }
}
