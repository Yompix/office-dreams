package com.yompix.novograd.client.story;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.lwjgl.glfw.GLFW;

/**
 * Телефон → «Контакты»: герои, с которыми ты знаком, уровень дружбы и её бонус. Можно позвонить самому
 * и попросить об услуге (Питерсон — замять дело, Брик — прислать бойцов). Данные — story/Calls.sendContacts.
 */
public class ContactsScreen extends LifeScreen {
    public static NbtCompound data = new NbtCompound();
    private int scroll;

    public ContactsScreen() {
        super("Контакты");
        data = new NbtCompound();
        NovogradClient.send("call_contacts");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(440, width - 30), h = Math.min(320, height - 30);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        title(ctx, "КОНТАКТЫ", x + 10, y + 8, 0xFF5AE6A0);
        String sub = "дружба растёт, когда берёшь трубку и помогаешь";
        ctx.drawText(textRenderer, sub, x + w - 10 - textRenderer.getWidth(sub), y + 11, 0xFF90A4AE, false);
        NbtList list = data.getList("list", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) {
            center(ctx, data.contains("list") ? "Пока никто не оставил тебе номер." : "Загрузка…", x + w / 2, y + h / 2, 0xFFB0BEC5);
        }
        int rowH = 40, top = y + 28, visible = (h - 46) / rowH;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        for (int i = scroll; i < list.size() && i < scroll + visible; i++) {
            NbtCompound c = list.getCompound(i);
            int ry = top + (i - scroll) * rowH;
            int col = 0xFF000000 | c.getInt("color");
            ctx.fill(x + 8, ry, x + w - 8, ry + rowH - 4, 0x601E1E1D);
            ctx.fill(x + 8, ry, x + 10, ry + rowH - 4, col);
            // аватар
            ctx.fill(x + 14, ry + 4, x + 42, ry + 32, col);
            String name = c.getString("name");
            CallClient.letter(ctx, textRenderer, name.isEmpty() ? "?" : name.substring(0, 1), x + 28, ry + 10, 0xFFFFFFFF);
            ctx.drawText(textRenderer, name, x + 48, ry + 4, 0xFFFFFFFF, true);
            int fr = c.getInt("fr");
            String t = c.getString("title") + " · " + fr + "/10" + (c.getInt("help") > 0 ? " · помог " + c.getInt("help") + "×" : "");
            ctx.drawText(textRenderer, t, x + 48 + textRenderer.getWidth(name) + 6, ry + 4, 0xFF9F9E99, false);
            // сердечки дружбы
            for (int k = 0; k < 10; k++) {
                int hx = x + 48 + k * 8, hy = ry + 15;
                ctx.fill(hx, hy, hx + 6, hy + 5, k < fr ? 0xFFD0404E : 0xFF383736);
            }
            if (c.contains("perk")) {
                boolean on = c.getBoolean("perkOn");
                String perk = (on ? "★ " : "с 3 ♥: ") + c.getString("perk");
                ctx.drawText(textRenderer, textRenderer.trimToWidth(perk, w - 170), x + 48, ry + 24, on ? Ui.GOLD : 0xFF707080, false);
            }
            String who = c.getString("who");
            int bx = x + w - 108;
            button(ctx, bx, ry + 4, 96, 13, "☎ Позвонить", 0xFF2FA84F, true, () -> {
                NbtCompound n = new NbtCompound();
                n.putString("who", who);
                NovogradClient.send("call_dial", n);
                close();
            });
            if (c.contains("perkBtn")) {
                button(ctx, bx, ry + 19, 96, 13, c.getString("perkBtn"), 0xFF8A5CFF, c.getBoolean("perkReady"), () -> {
                    NbtCompound n = new NbtCompound();
                    n.putString("who", who);
                    NovogradClient.send("call_perk", n);
                });
            }
        }
        int more = list.size() - scroll - visible;
        center(ctx, (more > 0 ? "↓ ещё " + more + " · " : "") + "колесо — листать · Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        scroll += vy < 0 ? 1 : -1;
        if (scroll < 0) scroll = 0;
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
