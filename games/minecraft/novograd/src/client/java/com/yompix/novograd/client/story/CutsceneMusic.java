package com.yompix.novograd.client.story;

import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Музыка кат-сцены (реплика режиссёра «music»): трек радио как тихая партитура под сценой.
 * Плавно входит, плавно стихает по «music stop» и сама гаснет, когда кат-сцена закрылась.
 */
public final class CutsceneMusic {
    private CutsceneMusic() {}

    @Nullable private static Track current;

    /** Сервер: «cut_music» {id, vol}. */
    public static void handle(NbtCompound n) {
        String id = n.getString("id");
        if (current != null) current.fadeOut();
        current = null;
        if (id.isEmpty() || id.equals("stop")) return;
        Identifier sid = id.contains(":") ? Identifier.tryParse(id) : Novograd.id(id);
        if (sid == null) return;
        current = new Track(sid, n.contains("vol") ? n.getFloat("vol") : 0.35f);
        MinecraftClient.getInstance().getSoundManager().play(current);
    }

    private static final class Track extends AbstractSoundInstance implements TickableSoundInstance {
        private final float target;
        private float level;
        private boolean out, done, seen;
        private int age;

        Track(Identifier id, float vol) {
            super(id, SoundCategory.MASTER, Random.create());
            target = vol;
            volume = 0.001f;
            repeat = false;
            attenuationType = SoundInstance.AttenuationType.NONE;
            relative = true;
        }

        void fadeOut() {
            out = true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            boolean open = MinecraftClient.getInstance().currentScreen instanceof CutsceneScreen;
            if (open) seen = true;
            // сцена закрылась (или так и не открылась за 5 с) — гасим
            if (seen && !open || !seen && age > 100) out = true;
            level = out ? level - target / 30f : Math.min(target, level + target / 40f);
            if (out && level <= 0) {
                done = true;
                level = 0;
                if (current == this) current = null;
            }
            volume = Math.max(0.001f, level);
        }
    }
}
