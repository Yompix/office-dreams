package com.yompix.novograd.client;

import com.yompix.novograd.client.render.vehicle.BikeRenderer;
import com.yompix.novograd.client.render.vehicle.HeliRenderer;
import com.yompix.novograd.client.render.vehicle.PlaneRenderer;
import com.yompix.novograd.client.render.vehicle.SpeedboatRenderer;
import com.yompix.novograd.vehicle.HeliEntity;
import com.yompix.novograd.vehicle.MotorVehicle;
import com.yompix.novograd.vehicle.PlaneEntity;
import com.yompix.novograd.vehicle.SpeedboatEntity;
import com.yompix.novograd.vehicle.Vehicles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;

/** Клиент транспорта: модели, звук моторов/винтов, ввод Пробел/Shift для полёта, приборы вертолёта и самолёта. */
public final class VehiclesClient {
    private VehiclesClient() {}

    private static final Map<Integer, Sound> SOUNDS = new HashMap<>();

    public static void init() {
        EntityRendererRegistry.register(Vehicles.BIKE, BikeRenderer::new);
        EntityRendererRegistry.register(Vehicles.SPEEDBOAT, SpeedboatRenderer::new);
        EntityRendererRegistry.register(Vehicles.HELI, HeliRenderer::new);
        EntityRendererRegistry.register(Vehicles.PLANE, PlaneRenderer::new);
        ClientTickEvents.START_CLIENT_TICK.register(c -> {
            boolean flying = c.player != null && c.player.getVehicle() instanceof MotorVehicle && c.currentScreen == null;
            Vehicles.inUp = flying && c.options.jumpKey.isPressed();
            Vehicles.inDown = flying && c.options.sneakKey.isPressed();
            SOUNDS.values().removeIf(Sound::isDone);
        });
        Vehicles.clientTick = VehiclesClient::engine;
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx));
    }

    private static void engine(MotorVehicle v) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !v.engineRunning() || SOUNDS.containsKey(v.getId())) return;
        if (v.squaredDistanceTo(c.player) > (v instanceof SpeedboatEntity ? 40 * 40 : 90 * 90)) return;
        Sound s = new Sound(v);
        SOUNDS.put(v.getId(), s);
        c.getSoundManager().play(s);
    }

    /** Приборы: высота над землёй, скорость, обороты/газ и подсказка по управлению. */
    private static void hud(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden || c.currentScreen != null) return;
        if (!(c.player.getVehicle() instanceof MotorVehicle v) || v.getControllingPassenger() != c.player) return;
        if (!(v instanceof HeliEntity) && !(v instanceof PlaneEntity)) return;
        var tr = c.textRenderer;
        int ground = c.world.getTopY(Heightmap.Type.MOTION_BLOCKING, v.getBlockX(), v.getBlockZ());
        int alt = Math.max(0, (int) Math.floor(v.getY() - ground));
        int kmh = Math.round(Math.abs(v.clientSpeed) * 20 * 3.6f);
        if (v instanceof PlaneEntity p) kmh = Math.round((float) v.getVelocity().length() * 20 * 3.6f);
        int x = ctx.getScaledWindowWidth() - 132, y = ctx.getScaledWindowHeight() / 2 - 34;
        ctx.fill(x - 6, y - 6, x + 126, y + 58, 0x88000000);
        ctx.drawText(tr, (v instanceof HeliEntity ? "ВЕРТОЛЁТ" : "САМОЛЁТ"), x, y, 0xFFFFC107, true);
        ctx.drawText(tr, "Высота: " + alt + " м", x, y + 12, 0xFFFFFFFF, true);
        ctx.drawText(tr, "Скорость: " + kmh + " км/ч", x, y + 22, 0xFFFFFFFF, true);
        if (v instanceof HeliEntity h) {
            int rpm = Math.round(h.rotor * 100);
            ctx.drawText(tr, "Винт: " + rpm + "%" + (rpm < 85 ? " — раскрутка" : ""), x, y + 32, rpm < 85 ? 0xFFFF8A65 : 0xFF9CFF9C, true);
            ctx.drawText(tr, "Пробел ↑  Shift ↓", x, y + 44, 0xFFB0B0B0, true);
        } else if (v instanceof PlaneEntity p) {
            ctx.drawText(tr, "Газ: " + Math.round(p.throttle * 100) + "%", x, y + 32, 0xFF9CFF9C, true);
            boolean stall = !v.isOnGround() && Math.abs(v.clientSpeed) < 0.75f;
            ctx.drawText(tr, stall ? "СВАЛИВАНИЕ! Газ и нос вниз" : "Пробел — нос вверх", x, y + 44, stall ? 0xFFFF5252 : 0xFFB0B0B0, true);
        }
        int hp = v.health();
        ctx.fill(x, y + 55, x + 120, y + 57, 0xFF333333);
        ctx.fill(x, y + 55, x + (int) (120 * MathHelper.clamp(hp / 100f, 0, 1)), y + 57, hp > 50 ? 0xFF4CAF50 : hp > 25 ? 0xFFFFC107 : 0xFFF44336);
    }

    /** Мотор/винт: тон и громкость от транспорта, затихает, когда заглушили. */
    private static final class Sound extends MovingSoundInstance {
        private final MotorVehicle v;
        private int idle;

        Sound(MotorVehicle v) {
            super(v.engineSound(), SoundCategory.NEUTRAL, Random.create());
            this.v = v;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.01f;
            this.pitch = 0.6f;
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (v.isRemoved()) {
                setDone();
                return;
            }
            x = v.getX();
            y = v.getY() + 1;
            z = v.getZ();
            boolean on = v.engineRunning() || (v instanceof HeliEntity h && h.rotor > 0.05f);
            idle = on ? 0 : idle + 1;
            volume = MathHelper.lerp(0.12f, volume, on ? v.soundVolume() : 0);
            pitch = MathHelper.lerp(0.15f, pitch, Math.max(0.5f, v.soundPitch()));
            if (idle > 40) setDone();
        }
    }
}
