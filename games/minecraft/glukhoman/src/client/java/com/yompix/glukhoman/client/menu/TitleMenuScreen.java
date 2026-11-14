package com.yompix.glukhoman.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Главное меню «Глухомани» вместо ванильного: ночная станция (панорама из нашей карты), камера медленно ползёт,
 * поверх — видоискатель VHS (REC, 14.10.1996, шум, редкие сбои), туман, далёкая мигающая лампа. Логотип — наклейка кассеты.
 * Пункты — строки текста, красный ► при наведении и тихий щелчок реле. Фон: гул, ветер, далёкий стук колёс.
 * Изредка (раз в 1–2 минуты) — силуэт на краю кадра на полсекунды или время на экране прыгает на 03:14.
 */
public class TitleMenuScreen extends Screen {
    /** Круговая панорама станции ночью (4 грани по 90°, снятые на нашей карте), 3072×553. */
    static final Identifier STRIP = Glukhoman.id("textures/gui/menu_strip.png");
    static final int SW = 3072, SH = 553;
    static final Identifier LOGO = Glukhoman.id("textures/gui/logo_label.png");
    static final Identifier SIL = Glukhoman.id("textures/gui/silhouette.png");
    final Random rnd = Random.create();
    record Item(String label, Runnable act) {}
    List<Item> items;
    int hover = -1, lastHover = -1;
    double nextScare = 70 + Math.random() * 50, scareUntil = -1, timeJumpUntil = -1, nextClatter = 12;
    int scareKind;
    /** Автотест: принудительно навести на пункт / показать сбой. */
    public static int forceHover = -1;
    public static boolean forceScare;
    static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(Glukhoman.MOD_ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

    public TitleMenuScreen() {
        super(Text.literal("Глухомань"));
    }

    @Override
    protected void init() {
        MinecraftClient c = MinecraftClient.getInstance();
        items = List.of(
                new Item("Новая смена", () -> {
                    if (WorldStart.hasSave(c)) c.setScreen(new com.yompix.glukhoman.client.ui.ConfirmScreen(this, false, "Новая смена?",
                            "Прошлая смена останется в «Мирах».", "Начать", "Назад", WorldStart::newShift));
                    else WorldStart.newShift();
                }),
                new Item("Продолжить", WorldStart::continueLatest),
                new Item("Главы", () -> c.setScreen(new com.yompix.glukhoman.client.ui.ChapterScreen(this))),
                new Item("Вместе", () -> c.setScreen(new com.yompix.glukhoman.client.ui.CoopScreen(this))),
                new Item("Настройки", () -> c.setScreen(new com.yompix.glukhoman.client.screen.SettingsScreen(this))),
                new Item("Миры", () -> c.setScreen(new SelectWorldScreen(this))),
                new Item("Титры", () -> c.setScreen(new CreditsMenuScreen(this))),
                new Item("Выход", () -> c.setScreen(new com.yompix.glukhoman.client.ui.ConfirmScreen(this, false, "Выйти из игры?", "", "Выйти", "Назад", c::scheduleStop))));
        MenuSound.loop("amb.forest", 0.5f);
        MenuSound.loop("amb.dread", 0.3f);
    }

    int itemY(int i) {
        return height / 2 + 4 + i * 16;
    }

    int itemX() {
        return Math.max(30, width / 2 - 170);
    }

    int hit(double mx, double my) {
        for (int i = 0; i < items.size(); i++) {
            int y = itemY(i);
            if (my >= y - 3 && my < y + 12 && mx >= itemX() - 14 && mx < itemX() + 160) return i;
        }
        return -1;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = MenuClock.now();
        int w = width, h = height;
        // --- ночная станция: панорама, камера медленно ползёт (вбок и чуть вверх-вниз) и дышит
        float yaw = (float) (t * 1.1 + Math.sin(t * 0.045) * 6);
        float pitch = (float) (Math.sin(t * 0.11) * 1.5 + Math.sin(t * 0.7) * 0.15);
        int dh = (int) (h * 1.12), dw = (int) ((long) SW * dh / SH);
        double px = (yaw / 360.0) * dw;
        int x0 = (int) -(px % dw), y0 = (int) (-(dh - h) / 2 + pitch * h / 60f);
        ctx.drawTexture(STRIP, x0, y0, 0, 0, dw, dh, dw, dh);
        ctx.drawTexture(STRIP, x0 + dw, y0, 0, 0, dw, dh, dw, dh);
        // туман и темнота
        ctx.fillGradient(0, 0, w, h / 2, 0x50000000, 0x10101418);
        ctx.fillGradient(0, h / 2, w, h, 0x10101418, 0xB0101418);
        ctx.fill(0, 0, w, h, 0x40000000);
        // далёкая лампа: тёплая точка, мигает
        double lampU = 0.69;   // доля ширины панорамы, где лампа перрона
        int lx = (int) (x0 + lampU * dw), ly = (int) (y0 + dh * 0.33);
        if (lx < -20) lx += dw;
        boolean lampOn = ((int) (t * 1.7) % 7) != 0 && rnd.nextFloat() > 0.02f;
        if (lampOn && lx > -20 && lx < w + 20) {
            for (int r = 9; r >= 1; r -= 2) ctx.fill(lx - r, ly - r, lx + r, ly + r, IntroScreen.alpha(0xF0C070, 0.06f));
            ctx.fill(lx - 1, ly - 1, lx + 1, ly + 1, 0xFFFFE0A0);
        }
        // --- редкий сбой: силуэт на краю кадра на полсекунды или время прыгает на 03:14
        if (forceScare || t > nextScare) {
            nextScare = t + 60 + rnd.nextDouble() * 60;
            scareKind = forceScare ? 0 : rnd.nextInt(2);
            forceScare = false;
            if (scareKind == 0) {
                scareUntil = t + 0.5;
                MenuSound.play("breath_close", 0.35f, 0.8f);
            } else timeJumpUntil = t + 2.2;
        }
        if (t < scareUntil) {
            int sh = (int) (h * 0.62), sw = sh / 3;
            RenderSystem.enableBlend();
            ctx.setShaderColor(1, 1, 1, 0.92f);
            ctx.drawTexture(SIL, w - sw + sw / 5, h - sh - h / 10, 0, 0, sw, sh, sw, sh);
            ctx.setShaderColor(1, 1, 1, 1);
        }
        if (t > nextClatter) {
            nextClatter = t + 25 + rnd.nextDouble() * 30;
            MenuSound.play("joints", 0.35f, 0.9f);
        }
        // --- видоискатель: шум, полосы, рамка, REC, дата
        noise(ctx, w, h, t, t < scareUntil ? 1.4f : 0.5f);
        int m = 12, L = 16, col = 0xB0E8E8E8;
        ctx.fill(m, m, m + L, m + 1, col); ctx.fill(m, m, m + 1, m + L, col);
        ctx.fill(w - m - L, m, w - m, m + 1, col); ctx.fill(w - m - 1, m, w - m, m + L, col);
        ctx.fill(m, h - m - 1, m + L, h - m, col); ctx.fill(m, h - m - L, m + 1, h - m, col);
        ctx.fill(w - m - L, h - m - 1, w - m, h - m, col); ctx.fill(w - m - 1, h - m - L, w - m, h - m, col);
        if ((int) (t * 2) % 2 == 0) ctx.drawTextWithShadow(textRenderer, "●", w - m - 46, m + 6, 0xFFE02424);
        ctx.drawTextWithShadow(textRenderer, "REC", w - m - 34, m + 6, 0xE0E8E8E8);
        int secs = (int) (51 * 60 + t) % 3600;
        String clock = t < timeJumpUntil ? "03:14:00" : String.format("02:%02d:%02d", secs / 60 % 60, secs % 60);
        ctx.drawTextWithShadow(textRenderer, "14.10.1996  " + clock, w - m - 118, h - m - 14, t < timeJumpUntil ? 0xFFE04040 : 0xC0D8D8D0);
        ctx.drawTextWithShadow(textRenderer, "SP", m + 8, h - m - 14, 0x90D8D8D0);
        // --- логотип-наклейка
        int lw = Math.min(w - 60, 360), lh = lw / 4, lx0 = itemX() - 10, ly0 = height / 2 - lh - 18;
        RenderSystem.enableBlend();
        float jit = rnd.nextFloat() < 0.01f ? 3 : 0;
        ctx.drawTexture(LOGO, (int) (lx0 + jit), ly0, 0, 0, lw, lh, lw, lh);
        // --- пункты меню: строки, красный ► при наведении, щелчок реле
        hover = forceHover >= 0 ? forceHover : hit(mx, my);
        if (hover != lastHover && hover >= 0) MenuSound.play("relay", 0.35f, 1.2f);
        lastHover = hover;
        for (int i = 0; i < items.size(); i++) {
            int y = itemY(i);
            boolean hv = i == hover;
            if (hv) ctx.drawTextWithShadow(textRenderer, "►", itemX() - 12, y, 0xFFE02828);
            String s = hv && (int) (t * 2.5) % 2 == 0 ? items.get(i).label() + "_" : items.get(i).label();
            ctx.drawTextWithShadow(textRenderer, s.toUpperCase(), itemX() + (hv ? 2 : 0), y, hv ? 0xFFF0EAD8 : 0xB0B8B0A0);
        }
        ctx.drawTextWithShadow(textRenderer, "Не связано с Mojang", m + 26, h - m - 14, 0x70A0A0A0);
        ctx.drawTextWithShadow(textRenderer, "Yompix Games · " + VERSION, w - m - textRenderer.getWidth("Yompix Games · " + VERSION) - 128, h - m - 14, 0x60A0A0A0);
    }

    /** Плёночный шум поверх меню: мягкие полосы, крупинки, редкий срыв. */
    void noise(DrawContext ctx, int w, int h, double t, float k) {
        int band = (int) (h * (0.55 + 0.4 * ((t * 0.05) % 1)));
        for (int i = 0; i < 26 * k; i++) {
            int yy = band + rnd.nextInt(14) - 7, xx = rnd.nextInt(w);
            ctx.fill(xx, yy, Math.min(w, xx + 8 + rnd.nextInt(70)), yy + 1, IntroScreen.alpha(0xD0D0E0, 0.12f + rnd.nextFloat() * 0.2f));
        }
        for (int i = 0; i < 90 * k; i++) {
            int x = rnd.nextInt(w), y = rnd.nextInt(h);
            ctx.fill(x, y, x + 1, y + 1, IntroScreen.alpha(0xFFFFFF, 0.08f + rnd.nextFloat() * 0.1f));
        }
        if (rnd.nextFloat() < 0.004f * k) {
            int yy = rnd.nextInt(h);
            ctx.fill(0, yy, w, yy + 3 + rnd.nextInt(6), IntroScreen.alpha(0xB0B0B0, 0.25f));
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        int i = hit(x, y);
        if (i >= 0) {
            activate(i);
            return true;
        }
        return super.mouseClicked(x, y, b);
    }

    void activate(int i) {
        MenuSound.play("tape_click", 0.7f, 1.1f);
        MenuSound.stopLoops();
        items.get(i).act().run();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN) hover = lastHover = forceHover = Math.min(items.size() - 1, Math.max(0, lastHover + 1));
        else if (key == GLFW.GLFW_KEY_UP) hover = lastHover = forceHover = Math.max(0, lastHover - 1);
        else if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && lastHover >= 0) activate(lastHover);
        else return super.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (forceHover >= 0 && !com.yompix.glukhoman.client.Recorder.on) forceHover = -1;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        MenuSound.stopLoops();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }
}
