package com.yompix.novograd.client.style;

import com.google.gson.JsonObject;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Appearance;
import com.yompix.novograd.life.LookGen;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0.9.6: внешность жителей из генератора на клиенте: зерно (с сервера) → внешность, описание объёмных частей и скин.
 * Разворачивается один раз на зерно (кэш), скин собирается {@link SkinComposer} (не больше нескольких за кадр).
 */
public final class CitizenLooks {
    private CitizenLooks() {}

    public record Entry(Appearance look, JsonObject head, String key, float width, float height) {}

    private static final Map<Long, Entry> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Entry> e) {
            return size() > 768;
        }
    };

    public static Entry entry(long seed) {
        Entry e = CACHE.get(seed);
        if (e == null) {
            Appearance a = LookGen.citizen(seed);
            e = new Entry(a, LookGen.renderJson(a), a.key(), StyleClient.buildWidth(a), StyleClient.buildHeight(a));
            CACHE.put(seed, e);
        }
        return e;
    }

    /** Текстура жителя (собирается при первой отрисовке); null — пока обычный скин. */
    @Nullable
    public static Identifier texture(CitizenEntity c) {
        long s = c.getLookSeed();
        if (s == 0) return null;
        Entry e = entry(s);
        return SkinComposer.texture(e.key(), e.look());
    }

    /** Внешность жителя, если его скин уже собран (иначе причёска и скин были бы от разных людей). */
    @Nullable
    public static Entry get(CitizenEntity c) {
        long s = c.getLookSeed();
        if (s == 0) return null;
        Entry e = entry(s);
        return SkinComposer.peek(e.key()) != null ? e : null;
    }
}
