package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** «Задания»: побочные миссии по категориям (вкладки), награда, статус; кнопки «Начать» и «Отменить текущее». */
public class MissionsScreen extends LifeScreen {
    private final NbtCompound data;
    private final Map<String, List<NbtCompound>> cats = new LinkedHashMap<>();
    private String tab;
    private int scroll;

    public MissionsScreen(NbtCompound data) {
        super("Задания");
        this.data = data;
        for (NbtElement e : data.getList("list", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            cats.computeIfAbsent(c.getString("cat"), k -> new ArrayList<>()).add(c);
        }
        tab = cats.isEmpty() ? "" : cats.keySet().iterator().next();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(420, width - 20), h = Math.min(260, height - 20);
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - openAnim()) * 30);
        panel(ctx, x, y, w, h, 0xFF2A1416, 0xFF140A0B);
        ctx.fillGradient(x, y, x + w, y + 22, 0xFFD32F2F, 0xFF8E1B1B);
        ctx.drawText(textRenderer, "ЗАДАНИЯ · выполнено " + data.getInt("done") + " из " + data.getInt("total"), x + 8, y + 7, 0xFFFFFFFF, true);
        String active = data.getString("active");
        if (!active.isEmpty())
            button(ctx, x + w - 128, y + 3, 120, 16, "Отменить текущее", 0xFF5D4037, true, () -> {
                NovogradClient.send("side_cancel");
                close();
            });
        // вкладки категорий
        int tx = x + 6, ty = y + 26;
        for (String c : cats.keySet()) {
            int tw = textRenderer.getWidth(c) + 10;
            if (tx + tw > x + w - 6) {
                tx = x + 6;
                ty += 16;
            }
            boolean sel = c.equals(tab);
            ctx.fill(tx, ty, tx + tw, ty + 14, sel ? 0xFFD32F2F : 0xFF3A2224);
            ctx.drawText(textRenderer, c, tx + 5, ty + 3, 0xFFFFFFFF, false);
            String cc = c;
            area(tx, ty, tw, 14, () -> {
                tab = cc;
                scroll = 0;
            });
            tx += tw + 4;
        }
        // список
        int ly = ty + 20, rowH = 22, rows = (y + h - 8 - ly) / rowH;
        List<NbtCompound> list = cats.getOrDefault(tab, List.of());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            NbtCompound m = list.get(i + scroll);
            int ry = ly + i * rowH;
            boolean done = m.getBoolean("done"), open = m.getBoolean("open"), cur = m.getString("id").equals(active);
            ctx.fill(x + 6, ry, x + w - 6, ry + rowH - 2, cur ? 0xFF4A3010 : 0xFF241416);
            ctx.drawText(textRenderer, (done ? "✔ " : open ? "• " : "🔒 ") + m.getString("title"), x + 12, ry + 3, done ? 0xFF8BC34A : open ? 0xFFFFFFFF : 0xFF777777, false);
            ctx.drawText(textRenderer, m.getString("giver") + " · $" + m.getInt("reward"), x + 12, ry + 12, 0xFFB0A0A0, false);
            if (open && !done && active.isEmpty())
                button(ctx, x + w - 76, ry + 2, 66, 16, "Начать", 0xFFD32F2F, true, () -> {
                    NbtCompound n = new NbtCompound();
                    n.putString("id", m.getString("id"));
                    NovogradClient.send("side_start", n);
                    close();
                });
            else if (cur) ctx.drawText(textRenderer, "идёт", x + w - 40, ry + 7, 0xFFFFB300, false);
            else if (!open) ctx.drawText(textRenderer, "позже по сюжету", x + w - 92, ry + 7, 0xFF777777, false);
        }
        if (list.size() > rows) ctx.drawText(textRenderer, "колесо мыши — листать", x + w - 130, y + h - 10, 0xFF886666, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (int) Math.signum(v);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
