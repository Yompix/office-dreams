package com.yompix.glukhoman.client.menu;

import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/** Звуки заставки и меню: разовые и петли (фон меню). При записи видео автотест собирает звук по тому же расписанию. */
public final class MenuSound {
    private MenuSound() {}

    static final Map<String, Loop> LOOPS = new HashMap<>();
    /** Журнал для сборки звука записанного ролика: время → звук. */
    public static final java.util.List<String> LOG = new java.util.ArrayList<>();

    static SoundEvent ev(String id) {
        return Registries.SOUND_EVENT.get(Glukhoman.id(id));
    }

    public static void play(String id, float vol, float pitch) {
        LOG.add(String.format(java.util.Locale.ROOT, "%.3f,%s,%.2f,%.2f", MenuClock.now(), id, vol, pitch));
        SoundEvent e = ev(id);
        if (e != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, vol));
    }

    static class Loop extends MovingSoundInstance {
        float target;

        Loop(SoundEvent e) {
            super(e, SoundCategory.MASTER, SoundInstance.createRandom());
            repeat = true;
            volume = 0.01f;
            relative = true;
            attenuationType = AttenuationType.NONE;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.05f;
            if (target <= 0.001f && volume < 0.01f) setDone();
        }
    }

    public static void loop(String id, float vol) {
        Loop l = LOOPS.get(id);
        if (l == null || l.isDone()) {
            SoundEvent e = ev(id);
            if (e == null) return;
            l = new Loop(e);
            LOOPS.put(id, l);
            MinecraftClient.getInstance().getSoundManager().play(l);
            LOG.add(String.format(java.util.Locale.ROOT, "%.3f,loop:%s,%.2f,1", MenuClock.now(), id, vol));
        }
        l.target = vol;
    }

    public static void stopLoops() {
        for (Loop l : LOOPS.values()) l.target = 0;
    }
}
