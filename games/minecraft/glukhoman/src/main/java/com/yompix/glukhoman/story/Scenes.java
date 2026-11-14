package com.yompix.glukhoman.story;

import java.util.Map;

/** Длины кат-сцен (сек) — общие для сервера (ждёт) и клиента (играет камеру и актёров). */
public final class Scenes {
    private Scenes() {}

    public static final Map<String, Float> LEN = Map.of(
            "intro", 13f, "knock", 10f, "village", 15f, "corridor", 8f,
            "anna", 12f, "bunker", 9f, "endA", 14f, "endB", 12f);

    public static int ticks(String id) {
        return Math.round(LEN.getOrDefault(id, 5f) * 20);
    }
}
