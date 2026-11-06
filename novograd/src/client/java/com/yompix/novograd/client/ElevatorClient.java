package com.yompix.novograd.client;

import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.world.Elevators;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

import java.util.ArrayList;
import java.util.List;

/** Лифт на клиенте: меню этажей (кнопки, мышь или стрелки + Enter) и плавное затемнение поездки. */
public final class ElevatorClient {
    private ElevatorClient() {}

    private static long fadeStart = -1;
    private static int fadeMs = 1000;
    private static String fadeLabel = "";

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Elevators.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.data()));
        HudRenderCallback.EVENT.register((ctx, t) -> renderFade(ctx));
    }

    private static void receive(MinecraftClient c, NbtCompound d) {
        switch (d.getString("type")) {
            case "menu" -> {
                NbtList l = d.getList("names", NbtElement.STRING_TYPE);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < l.size(); i++) names.add(l.getString(i));
                c.setScreen(new FloorScreen(d.getInt("x"), d.getInt("z"), d.getInt("cur"), names));
            }
            case "fade" -> {
                fadeStart = System.currentTimeMillis();
                fadeMs = d.getInt("ticks") * 50 + 600;
                fadeLabel = d.getString("label");
                if (c.currentScreen instanceof FloorScreen) c.setScreen(null);
            }
            default -> {}
        }
    }

    /** Чёрная шторка: быстро темнеет, держится, пока сервер переносит, и светлеет уже на новом этаже. */
    private static void renderFade(DrawContext ctx) {
        if (fadeStart < 0) return;
        float t = (System.currentTimeMillis() - fadeStart) / (float) fadeMs;
        if (t >= 1) {
            fadeStart = -1;
            return;
        }
        float a = t < 0.25f ? t / 0.25f : t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f;
        a = MathHelper.clamp(a, 0, 1);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        ctx.fill(0, 0, w, h, Ui.alpha(0xFF05060A, a));
        if (a > 0.3f) {
            var tr = MinecraftClient.getInstance().textRenderer;
            float k = (a - 0.3f) / 0.7f;
            Ui.small(ctx, tr, "ЛИФТ", w / 2f, h / 2f - 22, 1f, Ui.alpha(0xFFFFC428, k), 1);
            Ui.big(ctx, tr, fadeLabel.toUpperCase(), w / 2f, h / 2f - 8, 2f, Ui.alpha(0xFFFFFFFF, k), 1);
        }
    }

    /** Меню этажей: сетка кнопок, текущий этаж подсвечен. */
    public static final class FloorScreen extends Screen {
        private final int x, z, cur;
        private final List<String> names;
        private int sel;
        private final long opened = System.currentTimeMillis();

        FloorScreen(int x, int z, int cur, List<String> names) {
            super(Text.literal("Лифт"));
            this.x = x;
            this.z = z;
            this.cur = cur;
            this.names = names;
            this.sel = cur == names.size() - 1 ? 0 : names.size() - 1;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        private int cols() {
            return names.size() <= 8 ? 2 : names.size() <= 18 ? 3 : 4;
        }

        private int[] box() {
            int cols = cols(), rows = (names.size() + cols - 1) / cols;
            int bw = 92, bh = 18, gap = 4;
            int pw = cols * bw + (cols - 1) * gap + 24, ph = rows * (bh + gap) + 58;
            ph = Math.min(ph, height - 20);
            return new int[]{(width - pw) / 2, (height - ph) / 2, pw, ph, bw, bh, gap, cols};
        }

        /** Кнопки идут снизу вверх, как на настоящей панели: лобби внизу, крыша наверху. */
        private int[] cell(int i) {
            int[] b = box();
            int cols = b[7], rows = (names.size() + cols - 1) / cols;
            int r = rows - 1 - i / cols, cc = i % cols;
            return new int[]{b[0] + 12 + cc * (b[4] + b[6]), b[1] + 34 + r * (b[5] + b[6]), b[4], b[5]};
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0x88000000);
            int[] b = box();
            float k = Ui.easeOut(Math.min(1, (System.currentTimeMillis() - opened) / 180f));
            int oy = (int) ((1 - k) * 14);
            Ui.panel(ctx, b[0], b[1] + oy, b[2], b[3], Ui.GOLD);
            Ui.big(ctx, textRenderer, "ЛИФТ", b[0] + 12, b[1] + 9 + oy, 1.6f, 0xFFFFFFFF, 0);
            Ui.small(ctx, textRenderer, "Сейчас: " + names.get(cur), b[0] + b[2] - 12, b[1] + 13 + oy, 1f, 0xFFB8C0CC, 2);
            for (int i = 0; i < names.size(); i++) {
                int[] c = cell(i);
                int cy = c[1] + oy;
                if (cy + c[3] > b[1] + b[3] - 16) continue;
                boolean hover = mx >= c[0] && mx < c[0] + c[2] && my >= cy && my < cy + c[3];
                if (hover) sel = i;
                boolean here = i == cur, on = i == sel;
                int bg = here ? 0xFF3A3F4A : on ? 0xFFFFC428 : 0xFF1C2028;
                ctx.fill(c[0], cy, c[0] + c[2], cy + c[3], bg);
                ctx.fill(c[0], cy + c[3] - 1, c[0] + c[2], cy + c[3], 0x40FFFFFF);
                String label = names.get(i) + (here ? "  •" : "");
                int col = on && !here ? 0xFF101010 : here ? 0xFF9AA3B0 : 0xFFFFFFFF;
                ctx.drawText(textRenderer, label, c[0] + c[2] / 2 - textRenderer.getWidth(label) / 2, cy + 5, col, false);
            }
            Ui.small(ctx, textRenderer, "Клик или стрелки + Enter · Esc — закрыть · в кабине: прыжок — вверх, Shift — вниз",
                    width / 2f, b[1] + b[3] - 12 + oy, 0.75f, 0xFF8A93A0, 1);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            for (int i = 0; i < names.size(); i++) {
                int[] c = cell(i);
                if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                    go(i);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            int cols = cols();
            switch (key) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> sel = Math.min(names.size() - 1, sel + cols);
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> sel = Math.max(0, sel - cols);
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> sel = Math.min(names.size() - 1, sel + 1);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> sel = Math.max(0, sel - 1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> go(sel);
                default -> {
                    return super.keyPressed(key, scan, mods);
                }
            }
            return true;
        }

        private void go(int i) {
            if (i == cur) {
                close();
                return;
            }
            NbtCompound d = new NbtCompound();
            d.putInt("x", x);
            d.putInt("z", z);
            d.putInt("floor", i);
            ClientPlayNetworking.send(new Elevators.C2S(d));
            close();
        }
    }
}
