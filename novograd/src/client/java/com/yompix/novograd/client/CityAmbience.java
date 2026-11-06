package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.world.Island;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/** Фон города: гул улиц днём, сверчки и далёкая сирена ночью, волны на пляже — с плавным переходом по району и времени. */
public final class CityAmbience {
    private CityAmbience() {}

    private static final class Loop extends AbstractSoundInstance implements TickableSoundInstance {
        float target, cur;
        boolean done;

        Loop(String id) {
            super(Novograd.id(id), SoundCategory.AMBIENT, Random.create());
            repeat = true;
            relative = true;
            attenuationType = SoundInstance.AttenuationType.NONE;
            volume = 0.0001f;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public void tick() {
            cur += (target - cur) * 0.03f;
            volume = Math.max(0.0001f, cur);
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }

    private static Loop day, night, beach;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !(mc.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD)) {
            stop(mc);
            return;
        }
        if (day == null) {
            day = new Loop("ambience.city_day");
            night = new Loop("ambience.city_night");
            beach = new Loop("ambience.beach");
            mc.getSoundManager().play(day);
            mc.getSoundManager().play(night);
            mc.getSoundManager().play(beach);
        }
        Island.Zone z = Island.zone(mc.player.getBlockX(), mc.player.getBlockZ());
        boolean city = Island.urban(z) || z == Island.Zone.CORE;
        boolean sea = z == Island.Zone.BEACH || z == Island.Zone.PORT || z == Island.Zone.SEA;
        long t = mc.world.getTimeOfDay() % 24000;
        float nightK = t > 13000 && t < 23000 ? 1 : t > 12000 && t <= 13000 ? (t - 12000) / 1000f : t >= 23000 ? (24000 - t) / 1000f : 0;
        boolean indoors = !mc.world.isSkyVisible(mc.player.getBlockPos().up());
        float base = indoors ? 0.25f : 0.55f;
        if (CinemaCam.active()) base *= 0.7f;
        day.target = city ? base * (1 - nightK) : 0;
        night.target = city ? base * nightK : 0;
        beach.target = sea ? base * 0.9f : 0;
    }

    private static void stop(MinecraftClient mc) {
        for (Loop l : new Loop[]{day, night, beach}) if (l != null) {
            l.done = true;
            mc.getSoundManager().stop(l);
        }
        day = night = beach = null;
    }
}
