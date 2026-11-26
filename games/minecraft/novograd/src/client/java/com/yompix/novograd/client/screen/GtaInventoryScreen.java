package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.mixin.SlotAccessor;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Arsenal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Инвентарь (клавиша E) в стиле города: тёмная панель, вкладки по категориям, герой в полный рост с одеждой,
 * деньги и розыск, быстрые слоты 1–9 как в GTA, свои подсказки. Работает поверх того же PlayerScreenHandler, что и
 * обычный инвентарь: перетаскивание, Shift+клик, броня, вторая рука и синхронизация с сервером — прежние,
 * сохранения не меняются. Ванильный крафт 2×2 — на вкладке «Крафт».
 *
 * <p>Ячейки переставляются по своей сетке на время, пока окно открыто, и возвращаются на место при закрытии.
 */
public class GtaInventoryScreen extends HandledScreen<PlayerScreenHandler> {
    /** Вкладки: 0 Всё, 1 Оружие, 2 Еда и аптечки, 3 Одежда, 4 Предметы, 5 Крафт. */
    private static final String[] TABS = {"ВСЁ", "ОРУЖИЕ", "ЕДА/АПТЕЧКИ", "ОДЕЖДА", "ПРЕДМЕТЫ", "КРАФТ"};
    private static int tab;
    private static final int W = 368, H = 222, S = 20;
    private final int[][] orig;
    private final PlayerEntity player;
    private float[] tabHover = new float[TABS.length];
    private float open;

    public GtaInventoryScreen(PlayerEntity player) {
        super(player.playerScreenHandler, player.getInventory(), Text.literal("Инвентарь"));
        this.player = player;
        this.backgroundWidth = W;
        this.backgroundHeight = H;
        List<Slot> sl = handler.slots;
        orig = new int[sl.size()][2];
        for (int i = 0; i < sl.size(); i++) {
            orig[i][0] = sl.get(i).x;
            orig[i][1] = sl.get(i).y;
        }
        layout();
    }

    /** Категория предмета (1–4) для вкладок и подписи в подсказке. */
    public static int category(ItemStack s) {
        if (s.isEmpty()) return 0;
        if (Arsenal.isWeapon(s) || s.isOf(ModContent.AMMO) || s.isOf(ModContent.BAT)) return 1;
        if (s.get(DataComponentTypes.FOOD) != null || s.isOf(Arsenal.MEDKIT) || s.getItem() instanceof PotionItem) return 2;
        if (s.getItem() instanceof ArmorItem || s.getItem() instanceof Equipment || s.isOf(Arsenal.ARMOR) || s.isOf(ModContent.SUIT)) return 3;
        return 4;
    }

    private static final String[] CAT_NAMES = {"", "ОРУЖИЕ", "ЕДА И АПТЕЧКИ", "ОДЕЖДА", "ПРЕДМЕТ"};
    private static final int[] CAT_COLORS = {Ui.DIM, Ui.RED, Ui.MONEY, Ui.CYAN, Ui.GOLD};

    private static void put(Slot s, int x, int y) {
        ((SlotAccessor) s).novograd$setX(x);
        ((SlotAccessor) s).novograd$setY(y);
    }

    /** Своя сетка ячеек (координаты — от левого верхнего угла окна). */
    private void layout() {
        List<Slot> sl = handler.slots;
        boolean craft = tab == 5;
        // 0 — результат крафта, 1–4 — сетка 2×2: только на вкладке «Крафт» (иначе — за краем окна)
        int cx = 296, cy = 128;
        put(sl.get(0), craft ? cx + 46 : -10000, craft ? cy + 10 : -10000);
        for (int i = 0; i < 4; i++) put(sl.get(1 + i), craft ? cx + (i % 2) * S : -10000, craft ? cy + (i / 2) * S : -10000);
        // 5–8 — броня (голова → ноги) слева от героя, 45 — вторая рука под бронёй
        for (int i = 0; i < 4; i++) put(sl.get(5 + i), 10, 62 + i * S);
        put(sl.get(45), 10, 62 + 4 * S + 6);
        // 9–35 — рюкзак 9×3, 36–44 — быстрые слоты
        int gx = 110, gy = 62;
        for (int i = 0; i < 27; i++) put(sl.get(9 + i), gx + (i % 9) * S, gy + (i / 9) * S);
        for (int i = 0; i < 9; i++) put(sl.get(36 + i), gx + i * S, 160);
    }

    private void restore() {
        List<Slot> sl = handler.slots;
        for (int i = 0; i < sl.size() && i < orig.length; i++) put(sl.get(i), orig[i][0], orig[i][1]);
    }

    @Override
    protected void init() {
        super.init();
        // заголовок «Инвентарь» рисуется над окном (y − 14): на экране 240 px в высоту окно опускаем, чтобы он не обрезался
        y = Math.max(y, Math.min(14, height - H));
        layout();
        open = 0;
    }

    @Override
    public void removed() {
        restore();
        super.removed();
    }

    private boolean matches(Slot s) {
        if (tab == 0 || tab == 5) return true;
        int idx = handler.slots.indexOf(s);
        if (idx < 9 || idx == 45) return true; // броня и вторая рука — всегда видны
        return !s.getStack().isEmpty() && category(s.getStack()) == tab;
    }

    // ------------------------------------------------------------------ вкладки
    private int tabX(int i) {
        return x + 8 + i * 59;
    }

    private int tabY() {
        return y + 32;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i), ty = tabY();
            if (mx >= tx && mx < tx + 56 && my >= ty && my < ty + 14) {
                if (tab != i) {
                    tab = i;
                    layout();
                    if (client != null && client.player != null)
                        client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Tab / Shift+Tab — листать вкладки
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            tab = Math.floorMod(tab + ((mods & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), TABS.length);
            layout();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ отрисовка
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // затемнение мира с лёгкой виньеткой вместо ванильного размытия
        ctx.fillGradient(0, 0, width, height, 0xB0000000, 0xD0050508);
        drawBackground(ctx, delta, mouseX, mouseY); // HandledScreen рисует панель именно отсюда
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        open = Math.min(1, open + delta * 0.15f);
        var tr = textRenderer;
        // основная панель
        Ui.panel(ctx, x, y, W, H, Ui.PINK);
        Ui.header(ctx, tr, "Инвентарь", x + 6, y - 14, Ui.PINK);
        // вкладки
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabX(i), ty = tabY();
            boolean hov = mouseX >= tx && mouseX < tx + 56 && mouseY >= ty && mouseY < ty + 14;
            tabHover[i] = Ui.approach(tabHover[i], hov ? 1 : 0, 0.3f);
            boolean on = tab == i;
            int bg = on ? Ui.PINK : Ui.alpha(0xFF2A2A2E, 0.85f + 0.15f * tabHover[i]);
            Ui.slant(ctx, tx, ty, 54, 14, 4, bg);
            if (!on && tabHover[i] > 0.02f) ctx.fill(tx + 4, ty + 13, tx + 4 + (int) (50 * tabHover[i]), ty + 14, Ui.PINK);
            Ui.small(ctx, tr, TABS[i], tx + 29, ty + 4, 0.75f, on ? Ui.WHITE : Ui.alpha(Ui.DIM, 0.8f + 0.2f * tabHover[i]), 1);
        }
        // левая колонка: герой во весь рост в своей одежде
        ctx.fill(x + 6, y + 54, x + 102, y + 198, 0x60000000);
        ctx.fill(x + 6, y + 54, x + 102, y + 55, 0x30FFFFFF);
        Ui.small(ctx, tr, "ПЕРСОНАЖ", x + 32, y + 57, 0.7f, Ui.DIM, 0);
        InventoryScreen.drawEntity(ctx, x + 32, y + 66, x + 100, y + 190, 32, 0.0625f, mouseX, mouseY, player);
        // центр: рюкзак и быстрые слоты
        Ui.small(ctx, tr, "РЮКЗАК", x + 110, y + 53, 0.7f, Ui.DIM, 0);
        Ui.small(ctx, tr, "БЫСТРЫЕ СЛОТЫ", x + 110, y + 146, 0.7f, Ui.DIM, 0);
        int sel = player.getInventory().selectedSlot;
        for (int i = 0; i < 9; i++) {
            int sx = x + 110 + i * S, sy = y + 160;
            boolean cur = i == sel;
            // номер клавиши над ячейкой, как в колесе оружия
            Ui.small(ctx, tr, String.valueOf(i + 1), sx + 8, sy + 19, 0.6f, cur ? Ui.GOLD : Ui.alpha(Ui.DIM, 0.7f), 1);
            if (cur) {
                ctx.fill(sx - 2, sy - 2, sx + 18, sy + 18, Ui.GOLD);
            }
        }
        // ячейки: тёмные квадраты под предметами
        for (Slot s : handler.slots) {
            if (s.x < -1000) continue;
            int sx = x + s.x, sy = y + s.y;
            ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF101013);
            ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF1E1E22);
            if (!s.hasStack() && s.getBackgroundSprite() != null) {
                var bg = s.getBackgroundSprite();
                ctx.drawSprite(sx, sy, 0, 16, 16, client.getSpriteAtlas(bg.getFirst()).apply(bg.getSecond()));
            }
            int cat = category(s.getStack());
            if (cat > 0 && tab == 0) ctx.fill(sx, sy + 15, sx + 16, sy + 16, Ui.alpha(CAT_COLORS[cat], 0.8f)); // цвет категории — полоской
        }
        // правая колонка: деньги, розыск, крафт
        int rx = x + 296;
        ctx.fill(rx - 6, y + 54, x + W - 6, y + 198, 0x60000000);
        var d = NovogradClient.data;
        Ui.small(ctx, tr, "КАРТА · НАЛИЧНЫЕ", rx, y + 58, 0.7f, Ui.DIM, 0);
        Ui.big(ctx, tr, "$" + String.format(java.util.Locale.ROOT, "%,d", d.money).replace(',', ' ') + " · $" + String.format(java.util.Locale.ROOT, "%,d", d.cash).replace(',', ' '),
                rx, y + 67, 1.0f, Ui.MONEY, 0);
        Ui.small(ctx, tr, "ГРЯЗНЫЕ", rx, y + 82, 0.7f, Ui.DIM, 0);
        Ui.big(ctx, tr, "$" + String.format(java.util.Locale.ROOT, "%,d", d.dirty).replace(',', ' '), rx, y + 91, 1.1f, d.dirty > 0 ? Ui.GOLD : Ui.DIM, 0);
        Ui.small(ctx, tr, "РОЗЫСК", rx, y + 106, 0.7f, Ui.DIM, 0);
        int stars = NovogradClient.wantedStars;
        for (int i = 0; i < 5; i++)
            if (i < stars) Ui.tint(ctx, Ui.STAR, rx + i * 12, y + 115, 10, 10, 32, 32, 1f, 0.84f, 0.3f, 1);
            else Ui.tex(ctx, Ui.STAR_EMPTY, rx + i * 12, y + 115, 10, 10, 32, 32, 0.6f);
        if (tab == 5) {
            Ui.small(ctx, tr, "КРАФТ 2×2", rx, y + 132 - 10, 0.7f, Ui.CYAN, 0);
            int ax = rx + 2 * S - 2;
            Ui.small(ctx, tr, "→", ax + 4, y + 128 + 14, 1f, Ui.DIM, 0);
        } else {
            Ui.small(ctx, tr, "Tab — вкладки", rx, y + 180, 0.6f, Ui.alpha(Ui.DIM, 0.7f), 0);
            Ui.small(ctx, tr, "Shift+клик — в рюкзак", rx, y + 188, 0.6f, Ui.alpha(Ui.DIM, 0.7f), 0);
        }
        // броня и вторая рука: подписи
        Ui.small(ctx, tr, "РУКА 2", x + 8, y + 62 + 4 * S + 24, 0.55f, Ui.alpha(Ui.DIM, 0.7f), 0);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // заголовки рисуем в drawBackground; здесь — затемнение ячеек, которые не подходят под вкладку
        if (tab == 0 || tab == 5) return;
        for (Slot s : handler.slots) {
            if (s.x < -1000 || matches(s)) continue;
            ctx.fill(s.x, s.y, s.x + 16, s.y + 16, 0xC00A0A0C);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    /** Подсказка в стиле города: тёмная плашка, имя крупно, категория цветом, остальное — приглушённо. */
    @Override
    protected void drawMouseoverTooltip(DrawContext ctx, int mx, int my) {
        if (!handler.getCursorStack().isEmpty() || focusedSlot == null || !focusedSlot.hasStack()) return;
        if (!matches(focusedSlot)) return;
        ItemStack st = focusedSlot.getStack();
        List<Text> lines = getTooltipFromItem(st);
        var tr = textRenderer;
        int cat = category(st);
        int w = 0;
        for (Text t : lines) w = Math.max(w, tr.getWidth(t));
        w = Math.max(w, tr.getWidth(CAT_NAMES[cat]) + 4) + 14;
        int h = 18 + (lines.size() - 1) * 10 + 12;
        int tx = mx + 12, ty = my - 8;
        if (tx + w > width - 4) tx = mx - 12 - w;
        ty = MathHelper.clamp(ty, 4, height - h - 4);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        ctx.fill(tx + 2, ty + 2, tx + w + 2, ty + h + 2, 0x60000000);
        ctx.fill(tx, ty, tx + w, ty + h, 0xF00C0C0E);
        ctx.fill(tx, ty, tx + 2, ty + h, CAT_COLORS[cat]);
        Ui.outlined(ctx, tr, lines.get(0).getString(), tx + 7, ty + 5, Ui.WHITE);
        for (int i = 1; i < lines.size(); i++) ctx.drawText(tr, lines.get(i), tx + 7, ty + 9 + i * 10, Ui.DIM, false);
        Ui.small(ctx, tr, CAT_NAMES[cat], tx + 7, ty + h - 9, 0.7f, CAT_COLORS[cat], 0);
        ctx.getMatrices().pop();
    }
}
