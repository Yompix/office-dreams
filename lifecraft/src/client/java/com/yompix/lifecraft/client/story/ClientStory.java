package com.yompix.lifecraft.client.story;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Что клиент знает о сюжете: текущая цель, заставка главы, обратный отсчёт, «миссия выполнена». */
public final class ClientStory {
    private ClientStory() {}

    public static NbtCompound objective = new NbtCompound();
    public static String title = "", subtitle = "";
    public static long titleAt = -1;
    public static String countdown = "";
    public static long countdownAt = -1;
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

    public static void missionDone(String t) {
        missionDone = t;
        missionDoneAt = System.currentTimeMillis();
    }
}
