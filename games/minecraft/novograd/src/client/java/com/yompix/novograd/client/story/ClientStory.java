package com.yompix.novograd.client.story;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Что клиент знает о сюжете: текущая цель, заставка главы, обратный отсчёт, «миссия выполнена». */
public final class ClientStory {
    private ClientStory() {}

    public static NbtCompound objective = new NbtCompound();
    /** Адреса города (для карты и такси): id → {name, x, z}; плюс ox/oz — центр города. */
    public static NbtCompound places = new NbtCompound();
    /** Своя метка на карте (ставится на карте M). */
    @Nullable public static Vec3d waypoint;
    /** Все игроки на сервере (для карты и «Друзей»), приходит раз в 2 секунды. */
    public static NbtCompound players = new NbtCompound();
    /** Кто сейчас говорит (id существа → до какого времени шевелить губами). */
    public static final java.util.Map<Integer, Long> talking = new java.util.HashMap<>();

    public static void talk(int entity, long ms) {
        talking.put(entity, System.currentTimeMillis() + ms);
    }

    public static boolean isTalking(int entity) {
        Long t = talking.get(entity);
        return t != null && t > System.currentTimeMillis();
    }
    public static String title = "", subtitle = "";
    public static long titleAt = -1;
    public static String countdown = "";
    public static long countdownAt = -1;
    public static long sirenAt = -1;
    public static String achName = "", achDesc = "";
    public static long achAt = -1;
    public static String missionDone = "";
    public static long missionDoneAt = -1;

    @Nullable
    public static Vec3d target() {
        return objective.contains("x") ? new Vec3d(objective.getDouble("x"), objective.getDouble("y"), objective.getDouble("z")) : null;
    }

    public static void title(String t, String s) {
        title = t;
        subtitle = s;
        titleAt = System.currentTimeMillis();
    }

    public static void countdown(String t) {
        countdown = t;
        countdownAt = System.currentTimeMillis();
    }

    public static void achievement(String name, String desc) {
        achName = name;
        achDesc = desc;
        achAt = System.currentTimeMillis();
    }

    public static void missionDone(String t) {
        missionDone = t;
        missionDoneAt = System.currentTimeMillis();
    }
}
