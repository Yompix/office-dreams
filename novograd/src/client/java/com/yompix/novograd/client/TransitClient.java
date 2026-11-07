package com.yompix.novograd.client;

import com.yompix.novograd.client.render.TrainRenderer;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.entity.TrainEntity;
import com.yompix.novograd.world.CityNet;
import com.yompix.novograd.world.Metro;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Клиент кафе, супермаркетов и метро: меню (заказ, покупки, билеты, «куда едем»), подсказки в вагоне,
 * посадка (ПКМ у открытых дверей), угон (Shift+ПКМ у поезда), пропуск поездки (Пробел), затемнение.
 */
public final class TransitClient {
    private TransitClient() {}

    private static NbtCompound hud;
    private static long hudAt, fadeAt = -1;
    private static String fadeLabel = "";
    private static int useCooldown;

    public static void init() {
        EntityRendererRegistry.register(Metro.TRAIN, TrainRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CityNet.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.data()));
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(TransitClient::tick);
    }

    public static void send(String type, NbtCompound n) {
        n.putString("type", type);
        ClientPlayNetworking.send(new CityNet.C2S(n));
    }

    private static void receive(MinecraftClient c, NbtCompound d) {
        switch (d.getString("type")) {
            case "menu" -> c.setScreen(new MenuScreen(d));
            case "metro_hud" -> {
                hud = d;
                hudAt = System.currentTimeMillis();
            }
            case "fade" -> {
                fadeAt = System.currentTimeMillis();
                fadeLabel = d.getString("label");
                if (c.currentScreen instanceof MenuScreen) c.setScreen(null);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ управление
    @org.jetbrains.annotations.Nullable
    private static TrainEntity nearTrain(MinecraftClient mc, double r) {
        TrainEntity best = null;
        double bd = r * r;
        for (TrainEntity e : mc.world.getEntitiesByClass(TrainEntity.class, mc.player.getBoundingBox().expand(r + 18), x -> true)) {
            for (int k = 0; k < 4; k++) {
                double[] p = Metro.carPose(e.trainId(), e.progress(), k);
                double yaw = Math.toRadians(p[2]);
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                double dx = mc.player.getX() - p[0], dz = mc.player.getZ() - p[1];
                double along = MathHelper.clamp(dx * fx + dz * fz, -3.5, 3.5);
                double ex = dx - fx * along, ez = dz - fz * along;
                double d = ex * ex + ez * ez + Math.pow(mc.player.getY() - (Metro.D + 1), 2) * 0.3;
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (useCooldown > 0) useCooldown--;
        boolean riding = mc.player.getVehicle() instanceof TrainEntity;
        if (riding && mc.currentScreen == null) {
            TrainEntity e = (TrainEntity) mc.player.getVehicle();
            boolean driver = e.driverId() == mc.player.getId();
            if (!driver) while (mc.options.jumpKey.wasPressed()) send("skip", new NbtCompound());
            return;
        }
        if (mc.currentScreen != null || mc.player.getY() < Metro.D || mc.player.getY() > Metro.D + 4 || useCooldown > 0) return;
        if (!mc.options.useKey.isPressed()) return;
        TrainEntity e = nearTrain(mc, mc.player.isSneaking() ? 6 : 5.5);
        if (e == null) return;
        useCooldown = 15;
        if (mc.player.isSneaking()) send("hijack", new NbtCompound());
        else if (e.doorsOpen()) send("board", new NbtCompound());
    }

    // ------------------------------------------------------------------ подсказки
    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (mc.player != null && !mc.options.hudHidden) {
            if (mc.player.getVehicle() instanceof TrainEntity && hud != null && System.currentTimeMillis() - hudAt < 2000) {
                boolean drv = hud.getBoolean("driver");
                int y = h - 78;
                ctx.fill(w / 2 - 120, y - 4, w / 2 + 120, y + 30, 0xB0101418);
                ctx.fill(w / 2 - 120, y - 4, w / 2 - 116, y + 30, 0xFF1E5AA8);
                String top = hud.getInt("stop") >= 0 ? "Ⓜ Станция «" + hud.getString("here") + "»" + (hud.getBoolean("doors") ? " · двери открыты" : "")
                        : "Ⓜ Следующая: «" + hud.getString("next") + "»";
                Ui.small(ctx, mc.textRenderer, top, w / 2f, y, 1f, 0xFFFFFFFF, 1);
                String sub = drv ? hud.getInt("kmh") + " км/ч · W — газ · S — тормоз/назад · Shift — бросить"
                        : hud.getInt("kmh") + " км/ч · Пробел — пропустить поездку · Shift — выйти";
                Ui.small(ctx, mc.textRenderer, sub, w / 2f, y + 14, 0.8f, drv ? 0xFFFF6E6E : 0xFFFFC428, 1);
            } else if (mc.world != null && mc.currentScreen == null && mc.player.getY() >= Metro.D && mc.player.getY() <= Metro.D + 4) {
                TrainEntity e = nearTrain(mc, 5.5);
                if (e != null && e.doorsOpen())
                    Ui.small(ctx, mc.textRenderer, "ПКМ — войти в вагон (нужен билет) · Shift+ПКМ — угнать поезд", w / 2f, h - 62, 0.9f, 0xFFFFC428, 1);
            }
        }
        if (fadeAt < 0) return;
        float t = (System.currentTimeMillis() - fadeAt) / 2200f;
        if (t >= 1) {
            fadeAt = -1;
            return;
        }
        float a = MathHelper.clamp(t < 0.15f ? t / 0.15f : t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f, 0, 1);
        ctx.fill(0, 0, w, h, Ui.alpha(0xFF05060A, a));
        if (a > 0.4f) {
            float k = (a - 0.4f) / 0.6f;
            Ui.small(ctx, mc.textRenderer, "МЕТРО", w / 2f, h / 2f - 22, 1f, Ui.alpha(0xFF6FA8FF, k), 1);
            Ui.big(ctx, mc.textRenderer, fadeLabel.toUpperCase(), w / 2f, h / 2f - 8, 2f, Ui.alpha(0xFFFFFFFF, k), 1);
        }
    }

    // ------------------------------------------------------------------ меню
    /** Меню из пунктов «название · описание · цена»: мышь или стрелки + Enter. */
    public static final class MenuScreen extends Screen {
        private final NbtCompound d;
        private final NbtList items;
        private final int accent;
        private int sel;
        private final long opened = System.currentTimeMillis();

        MenuScreen(NbtCompound d) {
            super(Text.literal(d.getString("title")));
            this.d = d;
            this.items = d.getList("items", NbtElement.COMPOUND_TYPE);
            this.accent = d.getInt("color") == 0 ? Ui.GOLD : d.getInt("color");
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private int[] box() {
            int pw = Math.min(360, width - 20), rh = 22, ph = Math.min(height - 16, 50 + items.size() * rh + 18);
            return new int[]{(width - pw) / 2, (height - ph) / 2, pw, ph, rh};
        }

        private int rowY(int i) {
            int[] b = box();
            return b[1] + 40 + i * b[4];
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x88000000);
            int[] b = box();
            float k = Ui.easeOut(Math.min(1, (System.currentTimeMillis() - opened) / 180f));
            int oy = (int) ((1 - k) * 12);
            Ui.panel(ctx, b[0], b[1] + oy, b[2], b[3], accent);
            Ui.big(ctx, textRenderer, d.getString("title"), b[0] + 12, b[1] + 8 + oy, 1.3f, 0xFFFFFFFF, 0);
            Ui.small(ctx, textRenderer, d.getString("sub"), b[0] + 12, b[1] + 24 + oy, 0.75f, 0xFFB8C0CC, 0);
            for (int i = 0; i < items.size(); i++) {
                NbtCompound it = items.getCompound(i);
                int y = rowY(i) + oy;
                if (y + b[4] > b[1] + b[3] - 4) break;
                boolean hover = mx >= b[0] + 8 && mx < b[0] + b[2] - 8 && my >= y && my < y + b[4] - 2;
                if (hover) sel = i;
                boolean ok = it.getBoolean("ok"), on = i == sel;
                ctx.fill(b[0] + 8, y, b[0] + b[2] - 8, y + b[4] - 2, on ? (ok ? Ui.alpha(accent, 0.85f) : 0xFF3A3F4A) : 0xFF1C2028);
                int tc = on && ok ? 0xFF101010 : ok ? 0xFFFFFFFF : 0xFF7A828E;
                ctx.drawText(textRenderer, it.getString("label"), b[0] + 14, y + 3, tc, false);
                String desc = it.getString("desc");
                if (!desc.isEmpty()) {
                    String cut = textRenderer.trimToWidth(desc, b[2] - 110);
                    Ui.small(ctx, textRenderer, cut, b[0] + 14, y + 12.5f, 0.7f, on && ok ? 0xFF303030 : 0xFF9AA3B0, 0);
                }
                int price = it.getInt("price");
                if (price > 0) {
                    String p = "$" + price;
                    ctx.drawText(textRenderer, p, b[0] + b[2] - 14 - textRenderer.getWidth(p), y + 6, on && ok ? 0xFF101010 : 0xFF62E57E, false);
                }
            }
            Ui.small(ctx, textRenderer, "Клик или стрелки + Enter · Esc — закрыть", width / 2f, b[1] + b[3] - 11 + oy, 0.7f, 0xFF8A93A0, 1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int[] b = box();
            for (int i = 0; i < items.size(); i++) {
                int y = rowY(i);
                if (mx >= b[0] + 8 && mx < b[0] + b[2] - 8 && my >= y && my < y + b[4] - 2) {
                    pick(i);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            switch (key) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> sel = Math.max(0, sel - 1);
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> sel = Math.min(items.size() - 1, sel + 1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> pick(sel);
                default -> {
                    return super.keyPressed(key, scan, mods);
                }
            }
            return true;
        }

        private void pick(int i) {
            if (i < 0 || i >= items.size() || !items.getCompound(i).getBoolean("ok")) return;
            NbtCompound n = new NbtCompound();
            n.putString("ctx", d.getString("ctx"));
            n.putInt("i", i);
            send("pick", n);
            close();
        }
    }

    /** Для автотеста: открыто ли меню и какое. */
    public static String openMenu() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.currentScreen instanceof MenuScreen m ? m.d.getString("ctx") : "";
    }
}
