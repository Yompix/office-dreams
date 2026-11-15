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
        LEN.put("proto_npc", 21f);    // прототипы новой анимации/режиссёра (только просмотр и запись, в сюжете нет)
        LEN.put("proto_cut", 40f);
        LEN.put("c1_handover", 34f);
        LEN.put("c2_gena", 24f);       // 0.6: часть вторая
        LEN.put("c2_gena_ok", 24f);
        LEN.put("c2_pass", 16f);
        LEN.put("c3_naryad", 22f);
        LEN.put("c3_savva", 16f);
        LEN.put("demo_ch2", 34f);
        LEN.put("demo_ch3", 34f);   // 0.5.1: «Сдача смены» в сюжете (Захар в дежурке)
        LEN.put("demo_watcher", 30f);   // ролики 0.5.1 (только запись)
        LEN.put("demo_residents", 26f);
        LEN.put("demo_moments", 30f);
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
