package com.yompix.glukhoman.horror;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.story.Story;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;

/**
 * Рассудок 0–100. Падает в темноте, рядом с существами и от событий; растёт у света, в безопасных комнатах
 * и рядом с друзьями (в кооперативе вместе не так страшно). На нуле — паника: темнота в глазах и крик, который слышат Слухачи.
 */
public final class Sanity {
    private Sanity() {}

    public static float get(ServerPlayerEntity p) {
        return StoryState.get(p.getServerWorld()).sanity.getOrDefault(p.getUuid(), 100f);
    }

    public static void add(ServerPlayerEntity p, float d) {
        if (p.isCreative() || p.isSpectator()) return;
        StoryState st = StoryState.get(p.getServerWorld());
        float v = Math.max(0, Math.min(100, get(p) + d));
        st.sanity.put(p.getUuid(), v);
        st.markDirty();
        if (Math.abs(d) >= 5) send(p, v);
    }

    public static void set(ServerPlayerEntity p, float v) {
        StoryState.get(p.getServerWorld()).sanity.put(p.getUuid(), v);
        send(p, v);
    }

    static void send(ServerPlayerEntity p, float v) {
        NbtCompound m = Net.msg("sanity");
        m.putFloat("v", v);
        Net.send(p, m);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        StoryState st = StoryState.get(server.getOverworld());
        if (st.chapter == 0 || !st.ending.isEmpty()) return;
        float mult = st.difficulty == 0 ? 0.6f : st.difficulty == 2 ? 1.4f : 1f;
        var players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity p : players) {
            if (p.isSpectator() || p.isCreative()) continue;
            float d = 0;
            int light = p.getServerWorld().getLightLevel(p.getBlockPos().up());
            boolean friend = players.stream().anyMatch(o -> o != p && !o.isSpectator() && o.squaredDistanceTo(p) < 100);
            boolean safe = Story.inSafe(p);
            if (safe) d += 2.0f;
            else if (Light.isOn(p)) d += light >= 9 ? 0.2f : 0f;
            else if (light <= 4) d -= (v0(p) > 45 ? 0.15f : 0f) * mult * (friend ? 0.5f : 1f);   // темнота сама по себе не съедает Память ниже середины
            else if (light >= 10) d += 0.35f;
            if (friend) d += 0.25f;
            Box near = p.getBoundingBox().expand(20);
            for (LivingEntity e : p.getServerWorld().getEntitiesByClass(LivingEntity.class, near,
                    e -> e instanceof PoleEntity || e instanceof ListenerEntity || e instanceof ChoirEntity || e instanceof com.yompix.glukhoman.entity.WatcherEntity)) {
                if (!p.canSee(e)) continue;
                if (e instanceof com.yompix.glukhoman.entity.WatcherEntity) {
                    d -= 0.6f * mult;
                    continue;
                }
                d -= (e instanceof ChoirEntity ? 2.0f : e instanceof PoleEntity pe && !pe.isApparition() ? 1.5f : 0.8f) * mult;
            }
            float v = Math.max(0, Math.min(100, get(p) + d));
            st.sanity.put(p.getUuid(), v);
            if (v <= 0) panic(p);
            else send(p, v);
            if (v < 35 && server.getTicks() % 80 == 0)
                p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("breath"), SoundCategory.PLAYERS, 0.4f, 1f);
        }
        st.markDirty();
    }

    static float v0(ServerPlayerEntity p) {
        return get(p);
    }

    static void panic(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, false));
        p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("breath"), SoundCategory.PLAYERS, 1f, 1.3f);
        Noise.emit(p.getServerWorld(), p.getPos(), 20, p);
        NbtCompound fx = Net.msg("fx");
        fx.putString("k", "panic");
        Net.send(p, fx);
        set(p, 25);
    }
}
