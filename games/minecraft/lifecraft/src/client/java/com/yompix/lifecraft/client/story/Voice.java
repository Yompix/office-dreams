package com.yompix.lifecraft.client.story;

import com.yompix.lifecraft.LifeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Проигрывает озвученную реплику (assets/lifecraft/sounds/voice/<id>.ogg). Одновременно звучит одна реплика. */
public final class Voice {
    private Voice() {}

    @Nullable private static SoundInstance current;

    public static void play(String id) {
        stop();
        if (id == null || id.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        current = new PositionedSoundInstance(LifeCraft.id("voice." + id), SoundCategory.VOICE, 1f, 1f, Random.create(), false, 0,
                SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(current);
    }

    public static boolean playing() {
        return current != null && MinecraftClient.getInstance().getSoundManager().isPlaying(current);
    }

    public static void stop() {
        if (current != null) MinecraftClient.getInstance().getSoundManager().stop(current);
        current = null;
    }
}
