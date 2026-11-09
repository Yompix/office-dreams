package com.yompix.novograd.client;

import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.Skydive;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleToIntFunction;

/**
 * Клиент «всякого интересного»: физика и купол парашюта, брандспойт пожарной машины, полоска подработки над радаром,
 * цветные районы банд на карте, значки новых мест, экраны автомата и дартса.
 */
public final class FunClient {
    private FunClient() {}

    private static final Set<Integer> CHUTES = new HashSet<>();
    private static boolean jumpWas, useWas;
    private static int hoseTick;
    /** Подработка (скорая/пожарные/пицца): тип и строка для полоски над радаром. */
    public static String vjobKind = "", vjobText = "";
    private static NbtList turf = new NbtList();
    private static NbtList funPlaces = new NbtList();
    private static long turfAsked;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FunClient::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(FunClient::renderChutes);
        FunAutotest.init();
    }

    /** Пакеты сервера; true — наш. */
    public static boolean receive(MinecraftClient client, String type, NbtCompound n) {
        switch (type) {
            case "chute" -> {
                if (n.getBoolean("on")) CHUTES.add(n.getInt("id"));
                else CHUTES.remove(n.getInt("id"));
            }
            case "vjob" -> {
                vjobKind = n.getString("kind");
                vjobText = n.getString("text");
            }
            case "turf" -> turf = n.getList("list", NbtElement.COMPOUND_TYPE);
            case "fun_places" -> {
                funPlaces = n.getList("list", NbtElement.COMPOUND_TYPE);
                injectPlaces();
            }
            case "open_arcade" -> {
                var s = n.getString("game").equals("darts") ? new com.yompix.novograd.client.screen.DartsScreen(n) : new com.yompix.novograd.client.screen.ArcadeScreen(n);
                NovogradClient.show(client, s);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Новые места (клуб, скорая, пожарная часть…) — в общий список мест: их рисуют радар и карта. */
    private static void injectPlaces() {
        NbtCompound pl = ClientStory.places;
        for (NbtElement e : funPlaces) {
            NbtCompound c = (NbtCompound) e;
            String id = c.getString("id");
            if (pl.contains(id)) continue;
            NbtCompound v = new NbtCompound();
            v.putInt("x", c.getInt("x"));
            v.putInt("z", c.getInt("z"));
            v.putString("name", c.getString("name"));
            pl.put(id, v);
        }
    }

    public static boolean hasChute(MinecraftClient mc) {
        return mc.player != null && Skydive.PARACHUTE != null && mc.player.getInventory().count(Skydive.PARACHUTE) > 0;
    }

    public static boolean chuteOpen(MinecraftClient mc) {
        return mc.player != null && CHUTES.contains(mc.player.getId());
    }

    private static boolean freefall(MinecraftClient mc) {
        var p = mc.player;
        return p != null && !p.isOnGround() && !p.hasVehicle() && !p.isTouchingWater() && !p.getAbilities().flying && p.getVelocity().y < -0.45 && p.fallDistance > 5;
    }

    private static void tick(MinecraftClient mc) {
        var p = mc.player;
        if (p == null || mc.world == null) {
            CHUTES.clear();
            return;
        }
        if (mc.world.getTime() % 20 == 0 && !funPlaces.isEmpty()) injectPlaces();
        boolean jump = mc.options.jumpKey.isPressed();
        if (chuteOpen(mc)) {
            if (p.isOnGround() || p.isTouchingWater() || p.hasVehicle()) CHUTES.remove(p.getId());
            else steerChute(mc);
        } else if (freefall(mc) && hasChute(mc)) {
            if (jump && !jumpWas && mc.currentScreen == null) NovogradClient.send("chute_open");
            // свободное падение: W — пикировать вперёд
            if (mc.options.forwardKey.isPressed()) {
                Vec3d look = Vec3d.fromPolar(0, p.getYaw());
                Vec3d v = p.getVelocity();
                double h = Math.hypot(v.x, v.z);
                if (h < 0.7) p.setVelocity(v.x + look.x * 0.04, v.y, v.z + look.z * 0.04);
            }
        }
        jumpWas = jump;
        // брандспойт: ПКМ в пожарной машине во время подработки
        boolean use = mc.options.useKey.isPressed() && mc.currentScreen == null && vjobKind.equals("fire") && p.getVehicle() instanceof CarEntity;
        if (use && (--hoseTick <= 0 || !useWas)) {
            NbtCompound n = new NbtCompound();
            n.putBoolean("on", true);
            NovogradClient.send("hose", n);
            hoseTick = 4;
        } else if (!use && useWas) {
            NbtCompound n = new NbtCompound();
            n.putBoolean("on", false);
            NovogradClient.send("hose", n);
        }
        useWas = use;
    }

    /** Купол: медленный спуск, W/S — скорость и угол, A/D — поворот (как в GTA SA). */
    private static void steerChute(MinecraftClient mc) {
        var p = mc.player;
        boolean fwd = mc.options.forwardKey.isPressed(), back = mc.options.backKey.isPressed();
        if (mc.options.leftKey.isPressed()) p.setYaw(p.getYaw() - 3.2f);
        if (mc.options.rightKey.isPressed()) p.setYaw(p.getYaw() + 3.2f);
        p.setBodyYaw(p.getYaw());
        double spd = fwd ? 0.46 : back ? 0.1 : 0.27, vyT = fwd ? -0.27 : back ? -0.085 : -0.15;
        Vec3d dir = Vec3d.fromPolar(0, p.getYaw());
        Vec3d v = p.getVelocity();
        double nx = MathHelper.lerp(0.12, v.x, dir.x * spd), nz = MathHelper.lerp(0.12, v.z, dir.z * spd);
        double ny = v.y < vyT ? MathHelper.lerp(0.3, v.y, vyT) : vyT;
        p.setVelocity(nx, ny, nz);
        p.fallDistance = 0;
        p.setSprinting(false);
    }

    // ------------------------------------------------------------------ купол в мире
    private static void renderChutes(WorldRenderContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || CHUTES.isEmpty() || ctx.consumers() == null) return;
        Vec3d cam = ctx.camera().getPos();
        float delta = ctx.tickCounter().getTickDelta(false);
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getDebugQuads());
        var ms = ctx.matrixStack();
        for (int id : CHUTES) {
            Entity e = mc.world.getEntityById(id);
            if (!(e instanceof PlayerEntity pl)) continue;
            double x = MathHelper.lerp(delta, pl.prevX, pl.getX()) - cam.x, y = MathHelper.lerp(delta, pl.prevY, pl.getY()) - cam.y, z = MathHelper.lerp(delta, pl.prevZ, pl.getZ()) - cam.z;
            float yaw = MathHelper.lerpAngleDegrees(delta, pl.prevBodyYaw, pl.bodyYaw);
            ms.push();
            ms.translate(x, y, z);
            ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            canopy(vc, ms.peek().getPositionMatrix(), pl.age + delta);
            ms.pop();
        }
    }

    /** Ram-air купол: дуга из 8 секций (оранжевый/белый), стропы к плечам. Локально: +z — вперёд. */
    private static void canopy(VertexConsumer vc, Matrix4f m, float t) {
        int n = 8;
        float R = 3.0f, top = 4.6f, depth = 1.15f, sway = MathHelper.sin(t * 0.08f) * 0.06f;
        for (int i = 0; i < n; i++) {
            float a0 = -1.0f + 2f * i / n + sway, a1 = -1.0f + 2f * (i + 1) / n + sway;
            float x0 = R * MathHelper.sin(a0), y0 = top + R * (MathHelper.cos(a0) - 1), x1 = R * MathHelper.sin(a1), y1 = top + R * (MathHelper.cos(a1) - 1);
            int c = i % 2 == 0 ? 0xFFFF7A1A : 0xFFF4F4F4;
            int c2 = i % 2 == 0 ? 0xFFC85A10 : 0xFFC9C9C9; // нижняя сторона темнее
            quad(vc, m, x0, y0 + 0.12f, -depth, x1, y1 + 0.12f, -depth, x1, y1 + 0.12f, depth, x0, y0 + 0.12f, depth, c);
            quad(vc, m, x0, y0, depth, x1, y1, depth, x1, y1, -depth, x0, y0, -depth, c2);
            // передний и задний край (толщина крыла)
            quad(vc, m, x0, y0, depth, x0, y0 + 0.12f, depth, x1, y1 + 0.12f, depth, x1, y1, depth, c2);
            quad(vc, m, x1, y1, -depth, x1, y1 + 0.12f, -depth, x0, y0 + 0.12f, -depth, x0, y0, -depth, c2);
        }
        // стропы: от краёв и середины купола к плечам
        float[] xs = {-1, -0.5f, 0.5f, 1};
        for (float k : xs) {
            float a = k + sway, cx = R * MathHelper.sin(a), cy = top + R * (MathHelper.cos(a) - 1);
            float sx = k < 0 ? -0.3f : 0.3f;
            line(vc, m, cx, cy, depth * 0.7f, sx, 1.45f, 0.05f);
            line(vc, m, cx, cy, -depth * 0.7f, sx, 1.45f, -0.05f);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c) {
        int a = c >>> 24, r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        vc.vertex(m, x0, y0, z0).color(r, g, b, a);
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a);
        vc.vertex(m, x3, y3, z3).color(r, g, b, a);
        // обратная сторона (без отсечения граней)
        vc.vertex(m, x3, y3, z3).color(r, g, b, a);
        vc.vertex(m, x2, y2, z2).color(r, g, b, a);
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
        vc.vertex(m, x0, y0, z0).color(r, g, b, a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1) {
        float w = 0.025f;
        quad(vc, m, x0 - w, y0, z0, x0 + w, y0, z0, x1 + w, y1, z1, x1 - w, y1, z1, 0xFF2A2A2A);
        quad(vc, m, x0, y0, z0 - w, x0, y0, z0 + w, x1, y1, z1 + w, x1, y1, z1 - w, 0xFF2A2A2A);
    }

    // ------------------------------------------------------------------ HUD
    private static void hud(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || CinemaCam.active()) return;
        TextRenderer tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        // полоска подработки над радаром
        if (!vjobText.isEmpty()) {
            float K = NovogradConfig.hud();
            int y = (int) (H - (96 + 22 + 16) * K);
            int col = vjobKind.equals("fire") ? 0xFFFF5A3C : vjobKind.equals("ambulance") ? 0xFF4FC3F7 : 0xFFFFB74D;
            int w = tr.getWidth(vjobText) + 12;
            ctx.fill(9, y - 2, 9 + w, y + 11, 0xC0000000);
            ctx.fill(9, y - 2, 11, y + 11, col);
            ctx.drawText(tr, vjobText, 15, y, 0xFFFFFFFF, true);
            if (vjobKind.equals("fire") && mc.player.getVehicle() instanceof CarEntity)
                ctx.drawText(tr, "ПКМ — брандспойт", 15, y - 12, 0xFFBFE9FF, true);
        }
        // парашют: подсказка и высотомер
        boolean open = chuteOpen(mc);
        if (open || freefall(mc) && hasChute(mc) || freefall(mc) && mc.player.fallDistance > 12) {
            int ground = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, mc.player.getBlockX(), mc.player.getBlockZ());
            int alt = Math.max(0, (int) (mc.player.getY() - ground));
            String s = open ? "Высота " + alt + " м   W/S — скорость · A/D — поворот"
                    : hasChute(mc) ? "ПРОБЕЛ — РАСКРЫТЬ ПАРАШЮТ   " + alt + " м" : "НЕТ ПАРАШЮТА!   " + alt + " м";
            int col = open ? Ui.CYAN : hasChute(mc) ? ((System.currentTimeMillis() / 300) % 2 == 0 ? Ui.GOLD : Ui.WHITE) : Ui.RED;
            Ui.big(ctx, tr, s, W / 2f, H - 78, 1.25f, col, 1);
            if (!open && alt < 60 && hasChute(mc)) Ui.big(ctx, tr, "РАСКРЫВАЙ!", W / 2f, H - 98, 1.6f, Ui.RED, 1);
        }
    }

    // ------------------------------------------------------------------ карта: районы банд
    private static final int[] GANG_COL = {0xE53935, 0x1E88E5, 0x43A047};
    private static final String[] GANG = {"Чёрные Ящеры", "Синие", "Твоя банда"};

    /** Вызывается из MapScreen после подложки острова: цветные районы, подписи и легенда. */
    public static void drawMap(DrawContext ctx, TextRenderer tr, DoubleToIntFunction sx, DoubleToIntFunction sy, float zoom, int mapX, int mapY, int mapH) {
        long now = System.currentTimeMillis();
        if (now - turfAsked > 10000) {
            turfAsked = now;
            NovogradClient.send("turf_please");
        }
        if (turf.isEmpty()) return;
        boolean blink = (now / 400) % 2 == 0;
        for (NbtElement e : turf) {
            NbtCompound c = (NbtCompound) e;
            int[] b = c.getIntArray("box");
            if (b.length < 4) continue;
            int o = MathHelper.clamp(c.getInt("owner"), 0, 2);
            boolean war = c.getBoolean("war");
            int x0 = sx.applyAsInt(b[0]), y0 = sy.applyAsInt(b[1]), x1 = sx.applyAsInt(b[2]), y1 = sy.applyAsInt(b[3]);
            int col = GANG_COL[o];
            ctx.fill(x0, y0, x1, y1, (war && blink ? 0x70 : 0x3A) << 24 | col);
            int edge = 0xD0000000 | col;
            ctx.fill(x0, y0, x1, y0 + 1, edge);
            ctx.fill(x0, y1 - 1, x1, y1, edge);
            ctx.fill(x0, y0, x0 + 1, y1, edge);
            ctx.fill(x1 - 1, y0, x1, y1, edge);
            if (zoom > 0.3f && x1 - x0 > 40) {
                String name = (war ? "⚔ " : "") + c.getString("name");
                int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                ctx.drawText(tr, name, cx - tr.getWidth(name) / 2, cy - 4, 0xFF000000 | (o == 2 ? 0xB9F6CA : o == 0 ? 0xFFCDD2 : 0xBBDEFB), true);
            }
        }
        // легенда
        int lx = mapX + 6, ly = mapY + mapH - 46;
        ctx.fill(lx - 3, ly - 3, lx + 112, ly + 41, 0xC0000000);
        int[] count = new int[3];
        for (NbtElement e : turf) count[MathHelper.clamp(((NbtCompound) e).getInt("owner"), 0, 2)]++;
        for (int i = 0; i < 3; i++) {
            ctx.fill(lx, ly + i * 13, lx + 9, ly + i * 13 + 9, 0xFF000000 | GANG_COL[i]);
            ctx.drawText(tr, GANG[i] + ": " + count[i], lx + 13, ly + i * 13 + 1, 0xFFFFFFFF, false);
        }
    }
}
