package com.yompix.novograd.client.story;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Разговор как в кино: сверху и снизу выезжают чёрные полосы, камера плавно поворачивается к говорящему,
 * слева — живой портрет персонажа, текст печатается по буквам под озвучку, в конце — выбор ответа.
 */
public class DialogScreen extends LifeScreen {
    private record Line(String who, String name, int color, String text, String voice, int entity) {}

    private final List<Line> lines = new ArrayList<>();
    private final List<String> choiceTexts = new ArrayList<>();
    private final List<List<Line>> replies = new ArrayList<>();
    private int index, choice = -1;
    private long lineAt = System.currentTimeMillis();
    private boolean choosing, finished;

    private boolean viewOnly;

    /** Разговор вне сюжета (побочные задания): по окончании ничего не отправляем на сервер. */
    public DialogScreen(NbtCompound n, boolean viewOnly) {
        this(n);
        this.viewOnly = viewOnly;
    }

    public DialogScreen(NbtCompound n) {
        super("Разговор");
        lines.addAll(read(n.getList("lines", NbtElement.COMPOUND_TYPE)));
        for (NbtElement e : n.getList("choices", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            choiceTexts.add(c.getString("text"));
            replies.add(read(c.getList("reply", NbtElement.COMPOUND_TYPE)));
        }
        startLine();
    }

    private static List<Line> read(NbtList l) {
        List<Line> out = new ArrayList<>();
        for (NbtElement e : l) {
            NbtCompound c = (NbtCompound) e;
            out.add(new Line(c.getString("who"), c.getString("name"), c.getInt("color"), c.getString("text"), c.getString("voice"),
                    c.contains("entity") ? c.getInt("entity") : -1));
        }
        return out;
    }

    private void startLine() {
        lineAt = System.currentTimeMillis();
        if (index < lines.size()) {
            Voice.play(lines.get(index).voice());
            if (lines.get(index).entity() >= 0) ClientStory.talk(lines.get(index).entity(), 60L * lines.get(index).text().length() + 400);
        }
    }

    private int shownChars(Line l) {
        return (int) MathHelper.clamp((System.currentTimeMillis() - lineAt) / 32, 0, l.text().length());
    }

    private void next() {
        if (finished) return;
        if (choosing) return;
        if (index < lines.size()) {
            Line l = lines.get(index);
            if (shownChars(l) < l.text().length()) {
                lineAt = 0; // показать текст целиком
                return;
            }
            index++;
            if (index < lines.size()) {
                startLine();
                return;
            }
        }
        if (!choiceTexts.isEmpty() && choice < 0) {
            choosing = true;
            Voice.stop();
            return;
        }
        finish();
    }

    private void pick(int i) {
        choice = i;
        choosing = false;
        lines.clear();
        lines.addAll(replies.get(i));
        index = 0;
        if (lines.isEmpty()) finish();
        else startLine();
    }

    private void finish() {
        finished = true;
        Voice.stop();
        if (!viewOnly) {
            NbtCompound n = new NbtCompound();
            n.putInt("choice", choice);
            NovogradClient.send("dialog_done", n);
        }
        super.close();
    }

    @Override
    public void close() {
        // разговор нельзя просто закрыть — только дослушать
    }

    /** Друг уже ответил за компанию — сюжет пошёл дальше, это окно больше не нужно. */
    public void forceClose() {
        finished = true;
        Voice.stop();
        super.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void background(DrawContext ctx) {
    }

    private final float[] choiceAnim = new float[9];
    private int choiceSel = -1;

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int bar = (int) (height * 0.13f * a);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        ctx.fillGradient(0, height - bar - 70, width, height - bar, 0x00000000, (int) (a * 150) << 24);
        Line l = index < lines.size() ? lines.get(index) : null;
        lookAt(l, delta);
        Entity ent = l != null && l.entity() >= 0 && mc().world != null ? mc().world.getEntityById(l.entity()) : null;
        int maxW = Math.min(width - 60, 360);
        if (choosing) {
            // выбор ответа: чистый список, наведённый пункт — розовая косая плашка
            int n = choiceTexts.size();
            int rowH = 16, lw = 0;
            for (String c : choiceTexts) lw = Math.max(lw, textRenderer.getWidth(c));
            lw = Math.min(maxW, lw + 40);
            int x = width / 2 - lw / 2, y0 = height - bar - 12 - n * (rowH + 2);
            Ui.small(ctx, textRenderer, "ЧТО ОТВЕТИТЬ?", x, y0 - 12, 0.8f, Ui.GOLD, 0);
            for (int i = 0; i < n; i++) {
                int y = y0 + i * (rowH + 2);
                boolean hov = hover(x - 4, y, lw + 8, rowH);
                if (hov) choiceSel = i;
                choiceAnim[i] = Ui.approach(choiceAnim[i], i == choiceSel ? 1 : 0, 0.35f);
                ctx.fill(x - 4, y, x + lw + 4, y + rowH, 0xA0000000);
                if (choiceAnim[i] > 0.02f) Ui.slant(ctx, x - 4, y, (int) ((lw + 8) * Ui.easeOut(choiceAnim[i])), rowH, 4, 0xF0D42A3A);
                Ui.slant(ctx, x - 4, y, 14, rowH, 4, i == choiceSel ? 0xFF060606 : Ui.CYAN);
                ctx.drawText(textRenderer, String.valueOf(i + 1), x - 1, y + 4, i == choiceSel ? Ui.CYAN : 0xFF060606, false);
                String c = choiceTexts.get(i);
                if (textRenderer.getWidth(c) > lw - 22) c = textRenderer.trimToWidth(c, lw - 26) + "…";
                ctx.drawText(textRenderer, c, x + 16 + (int) (choiceAnim[i] * 3), y + 4, 0xFFFFFFFF, true);
                final int ci = i;
                area(x - 4, y, lw + 8, rowH, () -> pick(ci));
            }
            hintLine(ctx, "1–" + n + " или клик — ответить");
            return;
        }
        if (l == null) return;
        // портрет говорящего слева, в косой рамке цвета героя
        int col = l.color() | 0xFF000000;
        if (ent instanceof LivingEntity le) {
            int pw = 54, ph = 64, px = 14 - (int) ((1 - a) * 60), py = height - bar - ph - 6;
            Ui.slant(ctx, px - 3, py - 3, pw + 6, ph + 6, 6, col);
            Ui.slant(ctx, px, py, pw, ph, 6, 0xF00A0A0A);
            float sc = le.getScale();
            ctx.enableScissor(px, py, px + pw + 6, py + ph);
            InventoryScreen.drawEntity(ctx, px + 2, py + 2, px + pw + 2, py + ph + 20, (int) (32 / Math.max(0.6f, sc)), 0.0625f,
                    px + pw / 2f + (float) Math.sin(seconds() * 0.8) * 30, py + 10, le);
            ctx.disableScissor();
        }
        // субтитры: имя цветом героя, ниже — текст по центру с обводкой
        boolean call = ent == null && !l.who().equals("narrator");
        String name = ((call ? "☎ " : "") + l.name()).toUpperCase();
        boolean subs = com.yompix.novograd.client.NovogradConfig.get().subtitles || l.voice() == null || l.voice().isEmpty();
        String shown = subs ? l.text().substring(0, shownChars(l)) : "";
        var wrapped = textRenderer.wrapLines(Text.literal(subs ? l.text() : ""), maxW);
        int n = Math.min(4, wrapped.size());
        int ty = height - bar - 10 - n * 11;
        boolean narrator = l.who().equals("narrator");
        if (!narrator) {
            int nw = textRenderer.getWidth(name);
            Ui.big(ctx, textRenderer, name, width / 2f, ty - 13, 1f, col, 1);
            ctx.fill(width / 2 - nw / 2, ty - 3, width / 2 + nw / 2, ty - 2, Ui.alpha(col, 0.7f));
        }
        // печатаем по буквам: строки берём из полного текста, чтобы они не прыгали
        int left = shown.length();
        var full = textRenderer.getTextHandler().wrapLines(subs ? l.text() : "", maxW, net.minecraft.text.Style.EMPTY);
        for (int i = 0; i < n && i < full.size(); i++) {
            String line = full.get(i).getString();
            String part = line.substring(0, Math.max(0, Math.min(line.length(), left)));
            left -= line.length();
            while (left > 0 && left < l.text().length() && Character.isWhitespace(l.text().charAt(shown.length() - left))) left--;
            int lw = textRenderer.getWidth(line);
            Ui.outlined(ctx, textRenderer, part, width / 2 - lw / 2, ty + i * 11, narrator ? 0xFFFFE9A8 : 0xFFF6F4EC);
        }
        if (!subs) Ui.big(ctx, textRenderer, "…", width / 2f, ty, 1f, 0xFFFFFFFF, 1);
        if (shownChars(l) >= l.text().length() && (System.currentTimeMillis() / 400) % 2 == 0)
            ctx.drawText(textRenderer, "▼", width / 2 - 3, height - bar + 3, Ui.PINK, false);
        hintLine(ctx, "ЛКМ / Пробел — дальше");
    }

    private void hintLine(DrawContext ctx, String hint) {
        ctx.drawText(textRenderer, hint, width - textRenderer.getWidth(hint) - 8, height - 11, 0x88FFFFFF, false);
    }

    /** Камера плавно поворачивается к тому, кто говорит. */
    private void lookAt(Line l, float delta) {
        if (l == null || l.entity() < 0 || mc().player == null || mc().world == null) return;
        Entity e = mc().world.getEntityById(l.entity());
        if (e == null) return;
        Vec3d eye = mc().player.getEyePos(), t = e.getEyePos();
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90;
        float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        var p = mc().player;
        p.setYaw(p.getYaw() + MathHelper.wrapDegrees(yaw - p.getYaw()) * 0.08f);
        p.setPitch(p.getPitch() + (pitch - p.getPitch()) * 0.08f);
        p.setHeadYaw(p.getYaw());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        next();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (choosing && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + choiceTexts.size()) {
            click();
            pick(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (choosing && (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP)) {
            choiceSel = Math.floorMod(choiceSel + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), choiceTexts.size());
            return true;
        }
        if (choosing && choiceSel >= 0 && choiceSel < choiceTexts.size() && key == GLFW.GLFW_KEY_ENTER) {
            click();
            pick(choiceSel);
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_E) {
            next();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void click() {
        if (mc().player != null) mc().player.playSound(ModContent.PHONE_CLICK, 0.4f, 1.3f);
    }
}
