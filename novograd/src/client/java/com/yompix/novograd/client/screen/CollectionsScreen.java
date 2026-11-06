package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.PulseClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.lwjgl.glfw.GLFW;

/**
 * Телефон → «Коллекции»: граффити, тайники, фото-точки, трюки — счётчики, награды за наборы,
 * покупка карт (у ларька), список фото-точек; вкладка «Хаос» — включить/выключить «Режим Хаос».
 */
public class CollectionsScreen extends LifeScreen {
    private int tab, scroll;

    private static final String[] REWARDS = {
            "Награда: $20 000 и оружие в убежище каждый день (пистолет, ПП, дробовик, бронежилет)",
            "Награда: $10 000 и оружие в убежище каждый день (автомат, снайперка, гранаты, аптечки)",
            "Награда: $15 000 и бесплатный механик по вызову",
            "Награда: $20 000 и +4 сердца здоровья навсегда",
    };
    private static final String[] HOW = {
            "Теги банды на стенах по всему городу. Подойди, смотри на стену и держи Shift.",
            "Светящиеся посылки по острову — просто подбери.",
            "Голубые столбы «◉ ФОТО» у достопримечательностей: встань и сними камерой телефона.",
            "Трамплины за городом с полосатыми столбиками: разгонись и прыгай прямо.",
    };
    private static final int[] COLORS = {0xFFFF4FB8, 0xFF6BFF6B, 0xFF4FE3FF, 0xFFFFA726};

    public CollectionsScreen() {
        super("Коллекции");
        PulseClient.send("col_state");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        NbtCompound st = PulseClient.colState;
        int w = Math.min(460, width - 30), h = Math.min(300, height - 24);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF0121020, 0xF0080610);
        title(ctx, "КОЛЛЕКЦИИ", x + 10, y + 8, 0xFFFF4FB8);
        String[] tabs = {"Наборы", "Фото-точки", "Хаос"};
        for (int i = 0; i < tabs.length; i++) {
            final int k = i;
            button(ctx, x + w - 10 - (3 - i) * 74, y + 6, 70, 14, tabs[i], i == tab ? 0xFFFF4FB8 : 0xFF5A4F7A, true, () -> { tab = k; scroll = 0; });
        }
        int cy = y + 30;
        switch (tab) {
            case 0 -> sets(ctx, st, x, cy, w);
            case 1 -> photos(ctx, st, x, cy, w, y + h - 18);
            default -> chaos(ctx, x, cy, w);
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    private void sets(DrawContext ctx, NbtCompound st, int x, int y, int w) {
        NbtList cats = st.getList("cats", NbtElement.COMPOUND_TYPE);
        boolean stall = st.getBoolean("stall");
        if (cats.isEmpty()) {
            wrap(ctx, "Загрузка…", x + 14, y + 10, w - 28, 0xFFB0BEC5);
            return;
        }
        int row = 56;
        for (int i = 0; i < cats.size(); i++) {
            NbtCompound c = cats.getCompound(i);
            int ry = y + i * row;
            int col = COLORS[i % COLORS.length];
            int n = c.getInt("n"), tot = Math.max(1, c.getInt("total"));
            ctx.fill(x + 10, ry, x + 13, ry + row - 6, col);
            ctx.drawText(textRenderer, c.getString("name"), x + 18, ry, 0xFFFFFFFF, true);
            String cnt = n + " / " + tot + (c.getBoolean("set") ? "  ✔ НАБОР СОБРАН" : "");
            ctx.drawText(textRenderer, cnt, x + 110, ry, c.getBoolean("set") ? 0xFF7CFF7C : 0xFFFFE082, false);
            int bx = x + 18, bw = w - 140;
            ctx.fill(bx, ry + 11, bx + bw, ry + 15, 0xFF2A2440);
            ctx.fill(bx, ry + 11, bx + (int) (bw * n / (float) tot), ry + 15, col);
            fit(ctx, HOW[i % HOW.length], bx, ry + 19, bw, 0xFFB0BEC5);
            fit(ctx, REWARDS[i % REWARDS.length], bx, ry + 30, bw, 0xFF90A4AE);
            String radar = c.getBoolean("radar") ? "радар: виден" : "радар: нет";
            ctx.drawText(textRenderer, radar, x + w - 112, ry, c.getBoolean("radar") ? 0xFF7CFF7C : 0xFF9E9E9E, false);
            if (c.getBoolean("set")) {
                // набор собран — карта не нужна
            } else if (!c.getBoolean("map")) {
                final String id = c.getString("id");
                button(ctx, x + w - 112, ry + 12, 100, 14, "Карта $" + c.getInt("price"), 0xFF2E7D32, stall, () -> {
                    NbtCompound n2 = new NbtCompound();
                    n2.putString("cat", id);
                    PulseClient.send("col_buy", n2);
                });
                if (!stall) ctx.drawText(textRenderer, "у ларька «Карты»", x + w - 112, ry + 29, 0xFF757575, false);
            } else ctx.drawText(textRenderer, "карта куплена", x + w - 112, ry + 15, 0xFF7CFF7C, false);
            if (c.getString("id").equals("photo") && c.getBoolean("set"))
                button(ctx, x + w - 112, ry + 32, 100, 14, "Механик (бесплатно)", 0xFF1565C0, true, () -> PulseClient.send("col_repair"));
        }
        String tip = stall ? "Ты у ларька «Карты»: карта покажет оставшиеся точки на радаре."
                : "Половина набора — и радар сам покажет оставшиеся. Карты: деловая площадь, ворота порта, стрип Ривьеры.";
        wrap(ctx, tip, x + 14, y + cats.size() * row, w - 28, 0xFFFFC107);
    }

    /** Строка, ужатая по ширине. */
    private void fit(DrawContext ctx, String s, int x, int y, int w, int color) {
        int tw = textRenderer.getWidth(s);
        float k = tw > w ? Math.max(0.55f, w / (float) tw) : 1f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y + (1 - k) * 4, 0);
        ctx.getMatrices().scale(k, k, 1);
        ctx.drawText(textRenderer, s, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    private void photos(DrawContext ctx, NbtCompound st, int x, int y, int w, int bottom) {
        NbtList l = st.getList("photos", NbtElement.COMPOUND_TYPE);
        int colW = (w - 28) / 2, perCol = Math.max(1, (bottom - y) / 12);
        for (int i = scroll; i < l.size(); i++) {
            int k = i - scroll;
            int cx = x + 14 + (k / perCol) * colW, cy = y + (k % perCol) * 12;
            if (k / perCol >= 2) break;
            NbtCompound e = l.getCompound(i);
            boolean d = e.getBoolean("done");
            ctx.drawText(textRenderer, (d ? "✔ " : "◉ ") + (i + 1) + ". " + e.getString("name"), cx, cy, d ? 0xFF7CFF7C : 0xFFB0BEC5, false);
        }
    }

    private void chaos(DrawContext ctx, int x, int y, int w) {
        boolean on = PulseClient.chaos;
        ctx.drawText(textRenderer, "РЕЖИМ ХАОС", x + 14, y, 0xFFFF5252, true);
        int used = wrap(ctx, "Уличные события втрое чаще, банды устраивают засады, патрули узнают тебя по репортажам «Новоград-24», "
                + "а новости кричат о беспорядках. После финала сюжета включается сам.", x + 14, y + 14, w - 28, 0xFFE0E0E0);
        String state = on ? "Сейчас: ВКЛЮЧЁН" : "Сейчас: выключен";
        ctx.drawText(textRenderer, state, x + 14, y + 22 + used, on ? 0xFFFF5252 : 0xFF9E9E9E, false);
        if (!PulseClient.chaosUnlocked)
            ctx.drawText(textRenderer, "Сюжет ещё не пройден — включай на свой страх и риск.", x + 14, y + 34 + used, 0xFFFFC107, false);
        button(ctx, x + 14, y + 50 + used, 150, 18, on ? "Выключить хаос" : "Включить хаос", on ? 0xFF546E7A : 0xFFD32F2F, true, () -> {
            NbtCompound n = new NbtCompound();
            n.putBoolean("on", !PulseClient.chaos);
            PulseClient.send("chaos", n);
        });
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scroll = Math.max(0, Math.min(20, scroll + (vy < 0 ? 2 : -2)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
