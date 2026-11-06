package com.yompix.novograd.client.menu;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.story.CreditsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.PathUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

import com.yompix.novograd.client.ui.MenuList;
import com.yompix.novograd.client.ui.Ui;
import java.util.Comparator;
import java.util.List;

/**
 * Главное меню Novograd вместо обычного меню Майнкрафта: живой фон из кадров Новограда,
 * неоновый логотип, кнопки «Продолжить», «Новая игра» (сразу мир-город), миры, сеть, настройки, авторы.
 */
/** Версия мода из fabric.mod.json — показывается на главном экране. */
public class LifeMenuScreen extends Screen {
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("novograd")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");

    public static final Identifier[] BACKGROUNDS = {Novograd.id("textures/gui/menu/bg1.png"), Novograd.id("textures/gui/menu/bg2.png"),
            Novograd.id("textures/gui/menu/bg3.png"), Novograd.id("textures/gui/menu/bg4.png")};
    private static final String[] TIPS = {
            "Совет: клавиша G открывает телефон, а в нём — карта и дневник сюжета.",
            "Совет: золотой столб света показывает, куда идти по сюжету.",
            "Совет: вечером Новоград загорается неоном — прокатись по ночному городу!",
            "Совет: звёзды розыска гаснут, если оторваться от полиции и спрятаться.",
            "Совет: в машине клавиша R включает радио, N — нитро.",
            "Совет: побочные задания — в телефоне, приложение «Задания».",
            "Совет: казино — только на игровые деньги.",
            "Совет: купи свой дом в телефоне — будешь просыпаться там."};
    private static final long START = System.currentTimeMillis();

    private final MenuList menu = new MenuList();
    @Nullable private LevelSummary last;
    private int mouseX, mouseY;

    public LifeMenuScreen() {
        super(Text.literal("Новоград"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            mc.getLevelStorage().loadSummaries(mc.getLevelStorage().getLevelList()).thenAccept(list -> mc.execute(() -> {
                last = list.stream().filter(s -> !s.isLocked() && !s.requiresConversion()).max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null);
                rebuild();
            }));
        } catch (Exception e) {
            Novograd.LOG.warn("Не удалось прочитать список миров", e);
        }
        rebuild();
    }

    private void rebuild() {
        int sel = menu.selected;
        menu.clear();
        menu.rowH = 15;
        menu.subH = 25;
        menu.rowGap = 2;
        menu.scale = 1.4f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (last != null) {
            LevelSummary s = last;
            menu.add("Продолжить", s.getDisplayName(), () -> mc.createIntegratedServerLoader().start(s.getName(), () -> mc.setScreen(this)));
        }
        menu.add("Новая игра", last == null ? "начать сюжет в Новограде" : "", this::newLife);
        menu.add("Мои миры", "", () -> mc.setScreen(new SelectWorldScreen(this)));
        menu.add("Играть с друзьями", "", () -> mc.setScreen(new MultiplayerScreen(this)));
        menu.add("Настройки игры", "", () -> mc.setScreen(new SettingsScreen(this)));
        menu.add("Настройки Minecraft", "", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        menu.add("Авторы", "", () -> mc.setScreen(new CreditsScreen() {
            @Override
            public void close() {
                mc.setScreen(LifeMenuScreen.this);
            }
        }));
        menu.add("Выход", "", mc::scheduleStop);
        menu.selected = Math.min(sel, menu.items.size() - 1);
    }

    /** Новый мир «Новоград: Город» одним нажатием — сразу начинается сюжет. */
    private void newLife() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String name;
        try {
            name = PathUtil.getNextUniqueName(mc.getLevelStorage().getSavesDirectory(), "Новоград", "");
        } catch (Exception e) {
            name = "Новоград " + System.currentTimeMillis() % 10000;
        }
        DataConfiguration data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), FeatureFlags.DEFAULT_ENABLED_FEATURES);
        LevelInfo info = new LevelInfo(name, GameMode.SURVIVAL, false, Difficulty.EASY, true, new GameRules(), data);
        RegistryKey<WorldPreset> city = RegistryKey.of(RegistryKeys.WORLD_PRESET, Novograd.id("city"));
        mc.createIntegratedServerLoader().createAndStart(name, info, GeneratorOptions.createRandom(),
                drm -> drm.get(RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(), this);
    }

    /** Фон: ночной город на закате медленно плывёт, справа — комикс-панели из кадров Новограда. */
    public static void drawBackground(DrawContext ctx, int width, int height) {
        float t = (System.currentTimeMillis() - START) / 1000f;
        float pan = 0.5f + 0.5f * MathHelper.sin(t * 0.05f);
        Ui.cover(ctx, Ui.SKYLINE, 0, 0, width, height, Ui.SKYLINE_SIZE[0], Ui.SKYLINE_SIZE[1], 1.12f + 0.03f * MathHelper.sin(t * 0.08f), pan, 1f);
    }

    /** Наклонённая комикс-панель с рамкой; плавает и чуть качается. */
    private static void comicPanel(DrawContext ctx, int i, float cx, float cy, float w, float rot, float appear) {
        if (appear <= 0) return;
        float h = w * 9 / 16f;
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx + (1 - appear) * 160, cy, 0);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        ctx.fill((int) (-w / 2) + 5, (int) (-h / 2) + 6, (int) (w / 2) + 5, (int) (h / 2) + 6, (int) (appear * 150) << 24);
        ctx.fill((int) (-w / 2) - 3, (int) (-h / 2) - 3, (int) (w / 2) + 3, (int) (h / 2) + 3, Ui.alpha(0xFFF4EEE0, appear));
        Ui.tex(ctx, Ui.panel(i), -w / 2, -h / 2, w, h, Ui.PANEL_SIZE[0], Ui.PANEL_SIZE[1], appear);
        m.pop();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        float t = (System.currentTimeMillis() - START) / 1000f;
        drawBackground(ctx, width, height);
        // комикс-панели справа: «арт загрузочного экрана»
        float pw = Math.min(width * 0.36f, 230);
        int shift = (int) (t / 7) % 4;
        float[][] slots = {{0.70f, 0.30f, -5f}, {0.84f, 0.52f, 4f}, {0.66f, 0.73f, -2f}};
        for (int k = 0; k < slots.length; k++) {
            float bob = MathHelper.sin(t * 0.7f + k * 1.9f) * 3;
            float ap = Ui.easeOut((t - 0.35f - k * 0.18f) / 0.5f);
            comicPanel(ctx, k + shift, width * slots[k][0], height * slots[k][1] + bob, pw * (k == 1 ? 0.92f : 1f), slots[k][2] + MathHelper.sin(t * 0.4f + k) * 0.6f, ap);
        }
        // затемнение под меню
        for (int x = 0; x < width * 0.6f; x += 4) {
            int a = (int) (215 * Math.pow(1 - x / (width * 0.6f), 1.3));
            ctx.fill(x, 0, x + 4, height, a << 24 | 0x07040E);
        }
        Ui.vignette(ctx, width, height, 0.8f);
        // логотип
        int lx = 26;
        float lw = Math.min(width * 0.42f, 230);
        float la = Ui.easeOut(t / 0.6f);
        int ly = Math.max(10, height / 2 - 112);
        int lh = Ui.logo(ctx, lx - 4 + (1 - la) * -30, ly, lw, la);
        float sa = Ui.easeOut((t - 0.3f) / 0.5f);
        int sh = Ui.subtitle(ctx, lx + 4 + (1 - sa) * 30, ly + lh - 2, lw * 0.55f, sa);
        // пункты меню
        menu.render(ctx, textRenderer, lx + 10, ly + lh + sh + 12, 200, mouseX, mouseY);
        // студия и версия сверху справа
        String studio = "YOMPIX GAMES";
        int sw = studio.length() * 8;
        Ui.spaced(ctx, textRenderer, studio, width - sw - 10, 8, 0xCCFFFFFF, 2);
        String badge = "v" + VERSION;
        int bw = textRenderer.getWidth(badge) + 8;
        Ui.slant(ctx, width - bw - 14, 20, bw + 4, 11, 3, Ui.PINK);
        ctx.drawText(textRenderer, badge, width - bw - 10, 22, 0xFFFFFFFF, false);
        // нижняя полоса: совет дня
        ctx.fill(0, height - 20, width, height, 0xE0050309);
        ctx.fill(0, height - 21, width, height - 20, Ui.alpha(Ui.PINK, 0.8f));
        Ui.slant(ctx, 6, height - 17, 50, 14, 4, Ui.CYAN);
        ctx.drawText(textRenderer, "СОВЕТ", 15, height - 14, 0xFF07050C, false);
        String tip = TIPS[(int) (t / 6) % TIPS.length].replace("Совет: ", "");
        float tipA = Math.min(1, Math.min((t % 6) / 0.4f, (6 - t % 6) / 0.4f));
        ctx.drawText(textRenderer, tip, 64, height - 14, Ui.alpha(0xFFE8E4F2, tipA), false);
        String ver = "Minecraft 1.21.1 · © Yompix Games";
        if (textRenderer.getWidth(tip) + 64 + textRenderer.getWidth(ver) + 20 < width)
            ctx.drawText(textRenderer, ver, width - textRenderer.getWidth(ver) - 8, height - 14, 0x80FFFFFF, false);
        Ui.grain(ctx, width, height, 0.25f);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && menu.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (menu.key(key)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
