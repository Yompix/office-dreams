package com.yompix.lifecraft.client.menu;

import com.yompix.lifecraft.client.LifeCraftClient;
import com.yompix.lifecraft.client.screen.PhoneScreen;
import com.yompix.lifecraft.client.story.ClientStory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Пауза в стиле LifeCraft: что сейчас по сюжету, деньги и кнопки. */
public class LifePauseScreen extends Screen {
    private record Btn(String label, Runnable action) {}

    private final long opened = System.currentTimeMillis();
    private Btn[] buttons;

    public LifePauseScreen() {
        super(Text.literal("Пауза"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        buttons = new Btn[]{
                new Btn("Продолжить", () -> mc.setScreen(null)),
                new Btn("Телефон", () -> mc.setScreen(new PhoneScreen(null))),
                new Btn("Сюжет", () -> mc.setScreen(new PhoneScreen("story"))),
                new Btn("Награды", () -> mc.setScreen(new PhoneScreen("trophy"))),
                new Btn("Настройки", () -> mc.setScreen(new OptionsScreen(this, mc.options))),
                friends(mc),
                new Btn("Сохранить и выйти", this::quit)};
    }

    /** «Открыть для друзей» (стандартное «Открыть для сети»): с модом e4mc — играть вдвоём через интернет. */
    private Btn friends(MinecraftClient mc) {
        var server = mc.getServer();
        if (server == null) return new Btn("Ты в гостях у друга", () -> {});
        if (server.isRemote()) return new Btn("Мир открыт для друзей ✓", () -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("Мир уже открыт. Адрес для друга — в чате (с модом e4mc) или порт " + server.getServerPort()), false);
            mc.setScreen(null);
        });
        return new Btn("Открыть для друзей", () -> mc.setScreen(new net.minecraft.client.gui.screen.OpenToLanScreen(this)));
    }

    private void quit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean sp = mc.isInSingleplayer();
        if (mc.world != null) mc.world.disconnect();
        if (sp) mc.disconnect(new MessageScreen(Text.literal("Сохраняем твою жизнь…")));
        else mc.disconnect();
        mc.setScreen(new LifeMenuScreen());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float a = MathHelper.clamp((System.currentTimeMillis() - opened) / 250f, 0, 1);
        ctx.fillGradient(0, 0, width, height, (int) (a * 120) << 24, (int) (a * 200) << 24);
        for (int x = 0; x < width * 0.45f; x += 4) ctx.fill(x, 0, x + 4, height, (int) (a * 170 * (1 - x / (width * 0.45f))) << 24);
        int lx = 28 - (int) ((1 - a) * 40), ly = height / 2 - 80;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(lx, ly, 0);
        ctx.getMatrices().scale(2.6f, 2.6f, 1);
        ctx.drawText(textRenderer, "ПАУЗА", 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
        var o = ClientStory.objective;
        String sub = o.contains("mission") ? o.getString("chapter") + " · " + o.getString("mission") : "Свободная жизнь в Новограде";
        ctx.drawText(textRenderer, sub, lx, ly + 26, 0xFFFFC107, true);
        ctx.drawText(textRenderer, "$" + LifeCraftClient.data.money + " · " + LifeCraftClient.data.eduName(), lx, ly + 38, 0xFFB0FFC0, false);
        int by = ly + 56;
        for (Btn b : buttons) {
            boolean hov = mouseX >= lx && mouseX < lx + 170 && mouseY >= by && mouseY < by + 16;
            if (hov) {
                ctx.fill(lx, by, lx + 170, by + 16, 0x40FFFFFF);
                ctx.fill(lx, by, lx + 3, by + 16, 0xFFFF2FB4);
            }
            ctx.getMatrices().push();
            ctx.getMatrices().translate(lx + 10 + (hov ? 6 : 0), by + 3, 0);
            ctx.getMatrices().scale(1.2f, 1.2f, 1);
            ctx.drawText(textRenderer, b.label(), 0, 0, (int) (a * 255) << 24 | 0xFFFFFF, true);
            ctx.getMatrices().pop();
            by += 20;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = 28, by = height / 2 - 80 + 56;
        for (Btn b : buttons) {
            if (button == 0 && mx >= lx && mx < lx + 170 && my >= by && my < by + 16) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
                b.action().run();
                return true;
            }
            by += 20;
        }
        return super.mouseClicked(mx, my, button);
    }
}
