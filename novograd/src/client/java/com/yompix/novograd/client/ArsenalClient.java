package com.yompix.novograd.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.screen.WeaponWheelScreen;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.item.GunItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент арсенала: колесо оружия (держи TAB), перезарядка (R пешком — в машине R листает радио),
 * прицел снайперки (присесть), счётчик патронов у панели, полёт гранат.
 */
public final class ArsenalClient {
    private ArsenalClient() {}

    private static KeyBinding radioKey;
    private static boolean reloadWas, wheelWas;
    private static float zoom = 1f, prevZoom = 1f;

    public static void init() {
        EntityRendererRegistry.register(Arsenal.GRENADE_ENTITY, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(Arsenal.MOLOTOV_ENTITY, FlyingItemEntityRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(ArsenalClient::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx, t.getTickDelta(false)));
    }

    public static void send(String action, int value) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) ClientPlayNetworking.send(new Arsenal.C2S(action, value));
    }

    /** Код клавиши, на которой висит привязка (TAB / R по умолчанию). */
    private static int code(KeyBinding k, int fallback) {
        if (k == null) return fallback;
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(k);
        return key.getCategory() == InputUtil.Type.KEYSYM ? key.getCode() : fallback;
    }

    public static boolean held(int code) {
        return code > 0 && InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), code);
    }

    public static int wheelKey() {
        return code(MinecraftClient.getInstance().options.playerListKey, GLFW.GLFW_KEY_TAB);
    }

    private static void tick(MinecraftClient c) {
        prevZoom = zoom;
        if (c.player == null) {
            zoom = 1f;
            return;
        }
        if (radioKey == null)
            for (KeyBinding k : c.options.allKeys) if (k.getTranslationKey().equals("key.novograd.radio")) radioKey = k;
        // R: в машине — радио (это делает NovogradClient), пешком — перезарядка
        boolean r = c.currentScreen == null && held(code(radioKey, GLFW.GLFW_KEY_R));
        if (r && !reloadWas && !(c.player.getVehicle() instanceof CarEntity) && c.player.getMainHandStack().getItem() instanceof GunItem)
            send("reload", 0);
        reloadWas = r;
        // TAB: колесо оружия
        boolean tab = held(wheelKey());
        if (tab && !wheelWas && c.currentScreen == null && WeaponWheelScreen.hasWeapons(c.player)) c.setScreen(new WeaponWheelScreen());
        wheelWas = tab;
        // прицел снайперки: присесть с винтовкой в руках
        boolean scope = c.currentScreen == null && c.options.getPerspective() == Perspective.FIRST_PERSON && c.player.isSneaking()
                && c.player.getMainHandStack().getItem() instanceof GunItem g && g.spec().scope();
        zoom += ((scope ? 0.18f : 1f) - zoom) * 0.5f;
        if (Math.abs(zoom - 1f) < 0.01f) zoom = 1f;
    }

    /** Множитель поля зрения (вызывается из ArsenalFovMixin). */
    public static double fovScale(float delta) {
        return prevZoom + (zoom - prevZoom) * delta;
    }

    private static void hud(DrawContext ctx, float delta) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float z = (float) fovScale(delta);
        if (z < 0.6f) scope(ctx, w, h);
        ItemStack s = c.player.getMainHandStack();
        if (s.getItem() instanceof GunItem g) {
            int mag = g.mag(s), max = g.spec().magazine();
            int reserve = c.player.isCreative() ? -1 : c.player.getInventory().count(ModContent.AMMO);
            boolean reloading = GunFx.reloadProgress(g) >= 0; // по настоящей длинной перезарядке, а не по любому кулдауну выстрела
            String big = String.valueOf(mag), small = " / " + max + (reserve >= 0 ? "  ·  " + reserve : "  ·  ∞");
            // счётчик патронов — в плашке оружия справа вверху (LifeHud); здесь только подсказки
            int x = w / 2 + 96, y = h - 4;
            if (reloading) ctx.drawText(c.textRenderer, "ПЕРЕЗАРЯДКА", x, y - 16, 0xFFFFC94A, true);
            else if (mag == 0 && reserve == 0) ctx.drawText(c.textRenderer, "НЕТ ПАТРОНОВ", x, y - 16, 0xFFFF3EA5, true);
        }
    }

    /** Чёрная рамка оптического прицела, перекрестие и красная точка. */
    private static void scope(DrawContext ctx, int w, int h) {
        ctx.draw();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.44f;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        ring(m, cx, cy, r, (float) Math.hypot(w, h), 0, (float) (Math.PI * 2), 0xFF050505, 64);
        ring(m, cx, cy, r - 3, r, 0, (float) (Math.PI * 2), 0xFF1A1A1A, 64);
        ctx.fill((int) (cx - r), (int) cy, (int) (cx - 6), (int) cy + 1, 0xE0000000);
        ctx.fill((int) (cx + 6), (int) cy, (int) (cx + r), (int) cy + 1, 0xE0000000);
        ctx.fill((int) cx, (int) (cy - r), (int) cx + 1, (int) (cy - 6), 0xE0000000);
        ctx.fill((int) cx, (int) (cy + 6), (int) cx + 1, (int) (cy + r), 0xE0000000);
        for (int i = 1; i <= 4; i++) {
            int d = (int) (r * 0.12f * i);
            ctx.fill((int) cx - 3, (int) cy + d, (int) cx + 4, (int) cy + d + 1, 0xC0000000);
        }
        ctx.fill((int) cx - 1, (int) cy - 1, (int) cx + 2, (int) cy + 2, 0xFFFF2040);
    }

    /** Сектор кольца (от a0 до a1, радианы, 0 — вверх, по часовой) одним цветом. */
    public static void ring(Matrix4f m, float cx, float cy, float r1, float r2, float a0, float a1, int color, int steps) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            float t0 = a0 + (a1 - a0) * i / steps, t1 = a0 + (a1 - a0) * (i + 1) / steps;
            float s0 = (float) Math.sin(t0), c0 = (float) -Math.cos(t0), s1 = (float) Math.sin(t1), c1 = (float) -Math.cos(t1);
            float ix0 = cx + s0 * r1, iy0 = cy + c0 * r1, ox0 = cx + s0 * r2, oy0 = cy + c0 * r2;
            float ix1 = cx + s1 * r1, iy1 = cy + c1 * r1, ox1 = cx + s1 * r2, oy1 = cy + c1 * r2;
            b.vertex(m, ix0, iy0, 0).color(color);
            b.vertex(m, ox1, oy1, 0).color(color);
            b.vertex(m, ox0, oy0, 0).color(color);
            b.vertex(m, ix0, iy0, 0).color(color);
            b.vertex(m, ix1, iy1, 0).color(color);
            b.vertex(m, ox1, oy1, 0).color(color);
        }
        RenderSystem.disableCull();
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
