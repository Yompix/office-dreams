package com.yompix.novograd.client.story;

import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Проигрывает озвученную реплику (assets/novograd/sounds/voice/<id>.ogg). Одновременно звучит одна реплика. */
public final class Voice {
    private Voice() {}

    @Nullable private static SoundInstance current;

    public static void play(String id) {
        stop();
        if (id == null || id.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        CineRec.event("VOICE", id);
        current = new PositionedSoundInstance(Novograd.id("voice." + id), SoundCategory.VOICE, 1f, 1f, Random.create(), false, 0,
                SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(current);
        currentId = id;
        startedNs = System.nanoTime();
    }

    /** 1.0 (Lipsync): какая реплика звучит и сколько секунд. */
    @Nullable public static String currentId;
    private static long startedNs;

    public static double time() {
        return currentId == null ? -1 : (System.nanoTime() - startedNs) / 1e9;
    }

    private static final java.util.Map<String, Double> LEN = new java.util.HashMap<>();

    /**
     * Длина реплики в секундах — по самому файлу .ogg (последняя гранула / частота). Нужна, чтобы субтитры шли в такт
     * голосу, даже когда звук выключен или не запустился (раньше реплика тогда держалась 1.2 с и сцена «съезжала»).
     */
    public static double length(String id) {
        if (id == null || id.isEmpty()) return 0;
        return LEN.computeIfAbsent(id, k -> {
            try {
                var res = MinecraftClient.getInstance().getResourceManager().getResource(Novograd.id("sounds/voice/" + k + ".ogg"));
                if (res.isEmpty()) return 0.0;
                byte[] b;
                try (var in = res.get().getInputStream()) {
                    b = in.readAllBytes();
                }
                int rate = 0;
                for (int i = 0; i + 16 < b.length && i < 4096; i++)
                    if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r') {
                        rate = (b[i + 12] & 255) | (b[i + 13] & 255) << 8 | (b[i + 14] & 255) << 16 | (b[i + 15] & 255) << 24;
                        break;
                    }
                for (int i = b.length - 27; i >= 0; i--)
                    if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S') {
                        long g = 0;
                        for (int j = 7; j >= 0; j--) g = g << 8 | (b[i + 6 + j] & 255);
                        return rate > 0 ? g / (double) rate : 0.0;
                    }
            } catch (Exception ignored) {
            }
            return 0.0;
        });
    }

    public static boolean playing() {
        return current != null && MinecraftClient.getInstance().getSoundManager().isPlaying(current);
    }

    public static void stop() {
        if (current != null) MinecraftClient.getInstance().getSoundManager().stop(current);
        current = null;
        currentId = null;
    }
}
