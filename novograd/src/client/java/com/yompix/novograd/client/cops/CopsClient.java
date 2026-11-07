package com.yompix.novograd.client.cops;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.CopsNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Клиент «полиции»: зона поиска на радаре (мигающий круг, как в GTA V), удары PIT и «зажим» по своей машине,
 * маски на игроках (K — надеть/снять), табло «Полицейских и воров», таймер колонии (телефон изъят — G открывает меню камеры).
 */
public final class CopsClient {
    private CopsClient() {}

    public static final Map<UUID, String> MASKS = new HashMap<>();
    static boolean searchOn;
    static double sx, sz, sr;
    static boolean jailOn;
    static long jailLeft, jailAt;
    static int jailStage;
    static NbtCompound cnr;
    static NbtList pings;
    static long pingAt, cnrAt;
    private static KeyBinding maskKey;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CopsNet.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.type(), payload.data()));
        maskKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.novograd.mask", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.novograd"));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (maskKey.wasPressed()) if (c.player != null && c.currentScreen == null) send("mask_toggle", new NbtCompound());
        });
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            searchOn = false;
            jailOn = false;
            cnr = null;
            pings = null;
            MASKS.clear();
        });
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new MaskFeature(pr));
        });
        CopsAutotest.init();
    }

    public static void send(String type, NbtCompound n) {
        if (MinecraftClient.getInstance().getNetworkHandler() != null) ClientPlayNetworking.send(new CopsNet.C2S(type, n));
    }

    private static void receive(MinecraftClient c, String type, NbtCompound n) {
        switch (type) {
            case "search" -> {
                searchOn = n.getBoolean("on");
                if (searchOn) {
                    sx = n.getDouble("x");
                    sz = n.getDouble("z");
                    sr = n.getDouble("r");
                }
            }
            case "pit", "box" -> {
                if (c.player != null && c.player.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == c.player) {
                    car.speed *= n.getFloat("mul");
                    if (type.equals("pit")) car.setYaw(car.getYaw() + n.getFloat("kick"));
                }
            }
            case "mask" -> MASKS.put(n.getUuid("id"), n.getString("mask"));
            case "jail" -> {
                jailOn = n.getBoolean("on");
                jailLeft = n.getLong("left");
                jailStage = n.getInt("stage");
                jailAt = System.currentTimeMillis();
                if (!jailOn && c.currentScreen instanceof CopsMenuScreen s && s.kind().equals("jail")) s.close();
            }
            case "menu" -> {
                if (c.currentScreen instanceof CopsMenuScreen s && s.kind().equals(n.getString("kind"))) s.update(n);
                else if (c.currentScreen == null || c.currentScreen instanceof CopsMenuScreen
                        || c.currentScreen instanceof com.yompix.novograd.client.screen.LifeScreen) c.setScreen(new CopsMenuScreen(n));
            }
            case "cnr" -> {
                cnr = n.getBoolean("on") ? n : null;
                cnrAt = System.currentTimeMillis();
            }
            case "cnr_ping" -> {
                pings = n.getList("list", NbtElement.COMPOUND_TYPE);
                pingAt = System.currentTimeMillis();
            }
            default -> {}
        }
    }

    /** Телефон изъят в колонии: G открывает меню камеры. */
    public static boolean blockPhone(MinecraftClient c) {
        if (!jailOn) return false;
        send("jail_menu", new NbtCompound());
        return true;
    }

    public static boolean jailed() {
        return jailOn;
    }

    // ------------------------------------------------------------------ радар (рисуем в координатах мира, см. Minimap)
    public static void radar(DrawContext ctx, double px, double pz, float zoom) {
        long ms = System.currentTimeMillis();
        if (searchOn && sr > 0) {
            // круг поиска: полупрозрачная заливка красно-синим миганием и кольцо
            boolean red = (ms / 500) % 2 == 0;
            int fill = red ? 0x38FF3030 : 0x383070FF, ring = red ? 0xC0FF4040 : 0xC04F8CFF;
            double step = Math.max(1.5, 2.5 / zoom);
            for (double dz = -sr; dz < sr; dz += step) {
                double half = Math.sqrt(Math.max(0, sr * sr - (dz + step / 2) * (dz + step / 2)));
                rect(ctx, sx - half, sz + dz, sx + half, sz + dz + step, fill);
            }
            double w = 1.3 / zoom;
            for (int i = 0; i < 72; i++) {
                double a = i / 72.0 * Math.PI * 2;
                double x = sx + Math.cos(a) * sr, z = sz + Math.sin(a) * sr;
                rect(ctx, x - w, z - w, x + w, z + w, ring);
            }
        }
        if (pings != null && ms - pingAt < 6000 && (ms / 250) % 2 == 0) {
            double s = 3.2 / zoom;
            for (int i = 0; i < pings.size(); i++) {
                NbtCompound q = pings.getCompound(i);
                double x = q.getDouble("x"), z = q.getDouble("z");
                rect(ctx, x - s, z - s, x + s, z + s, 0xFF000000);
                rect(ctx, x - s * 0.7, z - s * 0.7, x + s * 0.7, z + s * 0.7, 0xFFFF3030);
            }
        }
    }

    private static void rect(DrawContext ctx, double x1, double y1, double x2, double y2, int col) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x1, y1, 0);
        m.scale((float) (x2 - x1), (float) (y2 - y1), 1);
        ctx.fill(0, 0, 1, 1, col);
        m.pop();
    }

    // ------------------------------------------------------------------ интерфейс
    private static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth();
        long ms = System.currentTimeMillis();
        if (jailOn) {
            long left = Math.max(0, jailLeft - (ms - jailAt) / 1000);
            String s = jailStage >= 3 ? "🚨 ПОБЕГ — беги от охраны и собак!" : jailStage > 0 ? "🔓 Побег: " + (jailStage == 1 ? "ковыряй решётку вентиляции (Shift)" : "к лестнице у стены — тихо!")
                    : "🔒 Колония №7 · осталось " + left / 60 + ":" + String.format("%02d", left % 60) + " · G — меню камеры";
            int w = tr.getWidth(s) + 12;
            ctx.fill(W / 2 - w / 2, 4, W / 2 + w / 2, 18, 0xC0000000);
            ctx.fill(W / 2 - w / 2, 17, W / 2 + w / 2, 18, jailStage >= 3 ? 0xFFFF3030 : 0xFFFF7043);
            ctx.drawText(tr, s, W / 2 - tr.getWidth(s) / 2, 7, 0xFFFFFFFF, true);
        }
        if (searchOn && !jailOn) {
            String s = "Полиция прочёсывает район — уйди из круга на радаре";
            ctx.drawText(tr, s, 12, ctx.getScaledWindowHeight() - 140, (ms / 500) % 2 == 0 ? 0xFFFF6060 : 0xFF6FA8FF, true);
        }
        if (cnr != null) {
            NbtCompound n = cnr;
            boolean cop = n.getString("role").equals("cop");
            long left = Math.max(0, n.getInt("left") - (ms - cnrAt) / 1000);
            NbtList sc = n.getList("score", NbtElement.COMPOUND_TYPE);
            int w = 150, h = 46 + sc.size() * 10;
            int x = W - w - 6, y = 30;
            ctx.fill(x, y, x + w, y + h, 0xC0000000);
            ctx.fill(x, y, x + w, y + 2, cop ? 0xFF4FA3FF : 0xFFFF4040);
            ctx.drawText(tr, "ПОЛИЦЕЙСКИЕ И ВОРЫ", x + 5, y + 5, 0xFFFFC428, true);
            String r = (n.getBoolean("break") ? "Перерыв · " : "Раунд " + n.getInt("round") + "/" + n.getInt("rounds") + " · ") + left / 60 + ":" + String.format("%02d", left % 60);
            ctx.drawText(tr, r, x + 5, y + 15, 0xFFFFFFFF, true);
            ctx.drawText(tr, (cop ? "Ты — полиция" : "Ты — вор") + " · воры " + n.getInt("robLeft") + "/" + n.getInt("robAll"), x + 5, y + 25, cop ? 0xFF8FC6FF : 0xFFFF8A8A, true);
            if (!cop && !n.getString("exit").isEmpty()) ctx.drawText(tr, "Точка: " + n.getString("exit"), x + 5, y + 35, 0xFFFFE860, true);
            for (int i = 0; i < sc.size(); i++) {
                NbtCompound q = sc.getCompound(i);
                String role = q.getString("r");
                int col = role.equals("cop") ? 0xFF8FC6FF : role.equals("caught") ? 0xFF888888 : role.equals("escaped") ? 0xFF7CFF8A : 0xFFFF8A8A;
                String tag = role.equals("cop") ? "🚓 " : role.equals("caught") ? "⛓ " : role.equals("escaped") ? "💰 " : "🏃 ";
                ctx.drawText(tr, tag + q.getString("n"), x + 5, y + 46 + i * 10, col, true);
                String s = String.valueOf(q.getInt("s"));
                ctx.drawText(tr, s, x + w - 6 - tr.getWidth(s), y + 46 + i * 10, 0xFFFFFFFF, true);
            }
        }
    }
}
