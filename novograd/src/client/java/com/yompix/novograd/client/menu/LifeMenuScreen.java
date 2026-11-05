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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Главное меню Novograd вместо обычного меню Майнкрафта: живой фон из кадров Новограда,
 * неоновый логотип, кнопки «Продолжить», «Новая жизнь» (сразу мир-город), миры, сеть, настройки, авторы.
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
            "Совет: учебник из магазина убирает один неверный ответ на уроке.",
            "Совет: вечером Новоград загорается неоном — прокатись по ночному городу!",
            "Совет: в выходные по городу проходят ярмарки, концерты и салюты.",
            "Совет: устал? Поспи или выпей колы — энергия вернётся.",
            "Совет: казино — только для взрослых и только на игровые деньги.",
            "Совет: купи свой дом в телефоне — будешь просыпаться там."};
    private static final long START = System.currentTimeMillis();

    private record Btn(String label, String sub, Runnable action) {}

    private final List<Btn> buttons = new ArrayList<>();
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
        buttons.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (last != null) {
            LevelSummary s = last;
            buttons.add(new Btn("Продолжить", s.getDisplayName(), () -> mc.createIntegratedServerLoader().start(s.getName(), () -> mc.setScreen(this))));
        }
        buttons.add(new Btn("Новая жизнь", "начать сюжет в Новограде", this::newLife));
        buttons.add(new Btn("Мои миры", "", () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new Btn("Играть с друзьями", "", () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new Btn("Настройки", "", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new Btn("Авторы", "", () -> mc.setScreen(new CreditsScreen() {
            @Override
            public void close() {
                mc.setScreen(LifeMenuScreen.this);
            }
        })));
        buttons.add(new Btn("Выход", "", mc::scheduleStop));
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

    /** Фон: кадры города медленно наезжают и сменяются с растворением. */
    public static void drawBackground(DrawContext ctx, int width, int height) {
        float t = (System.currentTimeMillis() - START) / 1000f;
        float per = 8f;
        int i = (int) (t / per) % BACKGROUNDS.length;
        float local = t % per;
        draw(ctx, BACKGROUNDS[i], width, height, local / per, 1f);
        if (local > per - 1.5f) {
            float a = (local - (per - 1.5f)) / 1.5f;
            draw(ctx, BACKGROUNDS[(i + 1) % BACKGROUNDS.length], width, height, 0, a);
        }
    }

    private static void draw(DrawContext ctx, Identifier tex, int width, int height, float k, float alpha) {
        float zoom = 1.06f + k * 0.08f;
        int w = (int) (width * zoom), h = (int) (height * zoom);
        int x = -(w - width) / 2 - (int) (k * width * 0.03f), y = -(h - height) / 2;
        ctx.setShaderColor(1, 1, 1, alpha);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        ctx.drawTexture(tex, x, y, 0, 0, w, h, w, h);
        ctx.setShaderColor(1, 1, 1, 1);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        drawBackground(ctx, width, height);
        // затемнение слева под меню и снизу
        ctx.fillGradient(0, 0, width, height, 0x22000000, 0x99000000);
        for (int x = 0; x < width * 0.55f; x += 4) {
            int a = (int) (200 * (1 - x / (width * 0.55f)));
            ctx.fill(x, 0, x + 4, height, a << 24);
        }
        float t = (System.currentTimeMillis() - START) / 1000f;
        // светлячки
        for (int i = 0; i < 26; i++) {
            float px = (i * 97 + t * (8 + i % 5)) % width, py = height - ((i * 53 + t * (10 + i % 7) * 2) % (height + 20));
            int a = (int) (90 + 80 * Math.sin(t * 2 + i));
            ctx.fill((int) px, (int) py, (int) px + 2, (int) py + 2, a << 24 | (i % 2 == 0 ? 0xFF2FB4 : 0x22E5FF));
        }
        // логотип с неоновым свечением
        int lx = 28, ly = height / 2 - 92;
        float pulse = 0.75f + 0.25f * MathHelper.sin(t * 2.4f);
        for (int g = 3; g >= 1; g--) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(lx + g * 0.6f, ly + g * 1.2f, 0);
            ctx.getMatrices().scale(4f, 4f, 1);
            int a = (int) (60 * pulse / g);
            ctx.drawText(textRenderer, "НОВОГРАД", 0, 0, a << 24 | 0xA855F7, false);
            ctx.getMatrices().pop();
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(lx, ly, 0);
        ctx.getMatrices().scale(4f, 4f, 1);
        ctx.drawText(textRenderer, "НОВОГРАД", 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
        ctx.drawText(textRenderer, "жизнь, которую строишь ты", lx + 2, ly + 38, 0xFFFFC107, true);
        ctx.drawText(textRenderer, "Yompix Games", lx + 2, ly + 50, 0xFF8A5CFF, false);
        // значок версии рядом с названием
        String badge = "v" + VERSION;
        int vbw = textRenderer.getWidth(badge) + 8, bxv = lx + 2 + textRenderer.getWidth("Yompix Games") + 8;
        ctx.fill(bxv, ly + 48, bxv + vbw, ly + 60, 0xCC8A5CFF);
        ctx.drawText(textRenderer, badge, bxv + 4, ly + 50, 0xFFFFFFFF, false);
        // кнопки
        int by = ly + 72;
        for (int i = 0; i < buttons.size(); i++) {
            Btn b = buttons.get(i);
            float appear = MathHelper.clamp((t - 0.2f - i * 0.07f) / 0.35f, 0, 1);
            int bx = lx - (int) ((1 - appear) * 60);
            int bw = 190, bh = b.sub().isEmpty() ? 16 : 22;
            boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
            int slide = hov ? 8 : 0;
            if (hov) {
                ctx.fill(bx, by, bx + bw, by + bh, 0x55FFFFFF & 0x40FFFFFF);
                ctx.fill(bx, by, bx + 3, by + bh, 0xFFFF2FB4);
            }
            int col = (int) (appear * 255) << 24 | (i == 0 ? 0xFFC107 : 0xFFFFFF);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(bx + 10 + slide, by + 3, 0);
            ctx.getMatrices().scale(1.25f, 1.25f, 1);
            ctx.drawText(textRenderer, b.label(), 0, 0, col, true);
            ctx.getMatrices().pop();
            if (!b.sub().isEmpty()) ctx.drawText(textRenderer, b.sub(), bx + 10 + slide, by + 14, (int) (appear * 170) << 24 | 0xC0C8E0, false);
            by += bh + 4;
        }
        // совет дня и версия
        String tip = TIPS[(int) (t / 6) % TIPS.length];
        ctx.drawText(textRenderer, tip, width - textRenderer.getWidth(tip) - 8, height - 24, 0xCCFFFFFF, true);
        String ver = "Новоград " + VERSION + " · Minecraft 1.21.1 · © Yompix Games";
        ctx.drawText(textRenderer, ver, width - textRenderer.getWidth(ver) - 8, height - 12, 0x88FFFFFF, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int lx = 28, by = height / 2 - 92 + 72;
        for (Btn b : buttons) {
            int bh = b.sub().isEmpty() ? 16 : 22;
            if (mx >= lx && mx < lx + 190 && my >= by && my < by + bh) {
                MinecraftClient.getInstance().getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1f));
                b.action().run();
                return true;
            }
            by += bh + 4;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
