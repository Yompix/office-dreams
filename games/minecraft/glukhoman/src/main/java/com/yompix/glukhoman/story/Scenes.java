package com.yompix.glukhoman.story;

import java.util.Map;

/** Длины кат-сцен (сек) — общие для сервера (ждёт) и клиента (играет камеру и актёров). */
public final class Scenes {
    private Scenes() {}

    public static final Map<String, Float> LEN = new java.util.HashMap<>(Map.of(
            "intro", 13f, "knock", 10f, "village", 15f, "corridor", 8f,
            "anna", 12f, "bunker", 9f, "endA", 14f, "endB", 12f));

    static {
        LEN.put("tt_pole", 12f);
        LEN.put("tr_radio", 6f);
        LEN.put("tr_gore", 6f);
        for (String k : new String[]{"pole", "listener", "choir", "human"}) LEN.put("death_" + k, 3.6f);
        LEN.put("tr_path", 6f);
        LEN.put("tr_listener", 5f);
        LEN.put("tr_doppel", 5f);
        LEN.put("tr_scare", 4f);
        LEN.put("tt_listener", 12f);
        LEN.put("tt_choir", 12f);
    }

    public static int ticks(String id) {
        return Math.round(LEN.getOrDefault(id, 5f) * 20);
    }
}
