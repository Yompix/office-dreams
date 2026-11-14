package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.HorrorMob;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Полноэкранный скример: модель существа крупным планом со своей анимацией броска, удар звука, вспышка. */
public final class Jumpscare {
    private Jumpscare() {}

    static LivingEntity e;
    static long start;
    static float headY = 3.4f;

    public static boolean active() {
        return e != null && (System.nanoTime() - start) / 1e9 < 1.3;
    }

    public static void start(String kind) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        if (GConfig.get().fewerScares) {
            GlukhomanClient.shake = Math.max(GlukhomanClient.shake, 0.8f);
            play("stinger_soft", 0.8f);
            return;
        }
        EntityType<? extends LivingEntity> t = switch (kind) {
            case "listener" -> GContent.LISTENER;
            case "choir" -> GContent.CHOIR;
            case "doppel" -> GContent.DOPPEL;
            default -> GContent.POLE;
        };
        headY = switch (kind) { case "listener" -> 1.0f; case "choir" -> 2.6f; case "doppel" -> 1.6f; default -> 3.45f; };
        e = t.create(c.world);
        if (e instanceof HorrorMob m) m.act("jumpscare", 0);
        start = System.nanoTime();
        GlukhomanClient.shake = Math.max(GlukhomanClient.shake, GConfig.get().noFlashing ? 1f : 2.6f);
        GlukhomanClient.hushT = 0;
        play("jumpscare", 1.2f);
    }

    static void play(String n, float vol) {
        SoundEvent s = Registries.SOUND_EVENT.get(Glukhoman.id(n));
        if (s != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(s, 1f, vol));
    }

    public static void render(DrawContext ctx, int w, int h) {
        if (!active()) {
            e = null;
            return;
        }
        float t = (System.nanoTime() - start) / 1e9f;
        if (e instanceof HorrorMob m) m.animClock = t * 20;
        float k = Math.min(1, t / 0.25f);
        int dark = (int) (Math.min(1, t * 6) * 235);
        ctx.fill(0, 0, w, h, dark << 24);
        float size = h * (0.55f + k * 0.9f);
        float jx = (float) Math.sin(t * 61) * 6 * (1 - k * 0.5f), jy = (float) Math.cos(t * 47) * 5;
        e.bodyYaw = e.prevBodyYaw = 180;
        e.headYaw = e.prevHeadYaw = 180;
        e.setYaw(180);
        e.setPitch(0);
        InventoryScreen.drawEntity(ctx, w / 2f + jx, h / 2f + jy, size, new Vector3f(0, headY, 0), new Quaternionf().rotateZ((float) Math.PI), null, e);
        if (!GConfig.get().noFlashing && t < 0.12f) ctx.fill(0, 0, w, h, ((int) ((0.12f - t) / 0.12f * 200) << 24) | 0xF0E8E8);
        if (t > 1.0f) ctx.fill(0, 0, w, h, ((int) Math.min(255, (t - 1.0f) / 0.3f * 255)) << 24);
    }
}
