package com.yompix.lifecraft.client.story;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.client.LifeCraftClient;
import com.yompix.lifecraft.client.screen.LifeScreen;
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
        if (index < lines.size()) Voice.play(lines.get(index).voice());
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
        NbtCompound n = new NbtCompound();
        n.putInt("choice", choice);
        LifeCraftClient.send("dialog_done", n);
        super.close();
    }

    @Override
    public void close() {
        // разговор нельзя просто закрыть — только дослушать
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void background(DrawContext ctx) {
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int bar = (int) (height * 0.11f * a);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        Line l = index < lines.size() ? lines.get(index) : null;
        lookAt(l, delta);
        int pw = Math.min(width - 20, 420), ph = 64;
        int px = width / 2 - pw / 2, py = height - ph - 10 - (int) ((1 - a) * 40);
        // портрет говорящего
        Entity ent = l != null && l.entity() >= 0 && mc().world != null ? mc().world.getEntityById(l.entity()) : null;
        int portrait = 0;
        if (ent instanceof LivingEntity le && !choosing) {
            portrait = 70;
            ctx.fill(px, py - 30, px + 66, py + ph, 0xCC101018);
            ctx.drawBorder(px, py - 30, 66, ph + 30, l.color() | 0xFF000000);
            float sc = le.getScale();
            InventoryScreen.drawEntity(ctx, px + 2, py - 28, px + 64, py + ph - 2, (int) (34 / Math.max(0.6f, sc)), 0.0625f,
                    px + 33 + (float) Math.sin(seconds() * 0.8) * 30, py + 6, le);
        }
        int tx = px + portrait;
        ctx.fill(tx, py, px + pw, py + ph, 0xE0101018);
        ctx.fill(tx, py, px + pw, py + 1, l != null ? l.color() | 0xFF000000 : 0xFFFFFFFF);
        if (choosing) {
            ctx.drawText(textRenderer, "Что ответить?", tx + 8, py + 6, 0xFFAAAAAA, false);
            for (int i = 0; i < choiceTexts.size(); i++) {
                final int ci = i;
                button(ctx, tx + 8, py + 18 + i * 20, pw - portrait - 16, 17, (i + 1) + ". " + choiceTexts.get(i), 0xFF2C3E66, true, () -> pick(ci));
            }
            return;
        }
        if (l == null) return;
        boolean call = ent == null && !l.who().equals("narrator");
        String name = (call ? "☎ " : "") + l.name();
        ctx.drawText(textRenderer, name, tx + 8, py + 6, l.color() | 0xFF000000, true);
        String shown = l.text().substring(0, shownChars(l));
        var wrapped = textRenderer.wrapLines(Text.literal(shown), pw - portrait - 18);
        for (int i = 0; i < wrapped.size() && i < 4; i++) ctx.drawText(textRenderer, wrapped.get(i), tx + 8, py + 19 + i * 10, 0xFFF0F0F0, false);
        if (shownChars(l) >= l.text().length() && (System.currentTimeMillis() / 400) % 2 == 0)
            ctx.drawText(textRenderer, "▼", px + pw - 12, py + ph - 12, 0xFFFFFFFF, false);
        String hint = "ЛКМ / Пробел — дальше";
        ctx.drawText(textRenderer, hint, width - textRenderer.getWidth(hint) - 6, height - 10, 0x88FFFFFF, false);
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
