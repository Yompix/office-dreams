package com.yompix.glukhoman.story;

import java.util.HashMap;
import java.util.Map;

/** Длины кат-сцен (сек) — общие для сервера (ждёт) и клиента (играет камеру и актёров). */
public final class Scenes {
    private Scenes() {}

    public static final Map<String, Float> LEN = new HashMap<>();

    static {
        LEN.put("pr_train", 12f);
        LEN.put("pr_board", 14f);
        LEN.put("rp_night", 15f);
        for (String k : new String[]{"pole", "watcher", "listener", "choir"}) LEN.put("tt_" + k, 12f);
        // трейлер 0.4 (только автотест-съёмка)
        LEN.put("t3_rails", 6.5f);
        LEN.put("t3_cut", 5f);
        LEN.put("t3_bell", 6f);
        LEN.put("t3_train", 8f);
        LEN.put("t3_updown", 5.5f);
        LEN.put("t3_house", 5f);
        LEN.put("t3_board", 5f);
        LEN.put("t3_rewind", 6.2f);
        LEN.put("t3_rollcall", 5f);
        LEN.put("t3_draisine", 4.5f);
        LEN.put("t3_tapes", 4.5f);
        LEN.put("t3_stop", 3.2f);
        LEN.put("t3_vanish", 4.5f);
        LEN.put("t3_look", 4.5f);
        LEN.put("t3_bogie", 4.0f);
        LEN.put("pano", 600f);
        LEN.put("in_ir", 3.0f);
    }

    public static int ticks(String id) {
        return Math.round(LEN.getOrDefault(id, 5f) * 20);
    }
}
