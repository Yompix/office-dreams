package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Работа программиста: тёмная IDE, нужно перепечатать строки кода. Буквы подсвечиваются: зелёные — верно, красные — опечатка. */
public class TypingScreen extends LifeScreen {
    private final List<String> lines = new ArrayList<>();
    private final List<String> typed = new ArrayList<>();
    private TextFieldWidget field;
    private boolean sent;

    public TypingScreen(NbtCompound n) {
        super("Код");
        NbtList l = n.getList("lines", NbtElement.STRING_TYPE);
        for (int i = 0; i < l.size(); i++) lines.add(l.getString(i));
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(textRenderer, 0, 0, 200, 16, Text.literal("код"));
        field.setMaxLength(64);
        addDrawableChild(field);
        setInitialFocus(field);
    }

    private void submit() {
        if (typed.size() >= lines.size()) return;
        typed.add(field.getText());
        field.setText("");
        if (typed.size() >= lines.size() && !sent) {
            sent = true;
            NbtCompound n = new NbtCompound();
            NbtList l = new NbtList();
            for (String s : typed) l.add(NbtString.of(s));
            n.put("typed", l);
            NovogradClient.send("work_answer", n);
        }
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(300, width - 20), h = Math.min(200, height - 20);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFF1E2229, 0xFF15181E);
        ctx.fill(x, y, x + w, y + 14, 0xFF2B303A);
        for (int i = 0; i < 3; i++) ctx.fill(x + 6 + i * 8, y + 5, x + 10 + i * 8, y + 9, new int[]{0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840}[i]);
        ctx.drawText(textRenderer, "Main.java — Novograd IDE", x + 34, y + 3, 0xFFAAB2C0, false);
        for (int i = 0; i < lines.size(); i++) {
            int ly = y + 22 + i * 14;
            ctx.drawText(textRenderer, String.valueOf(i + 1), x + 6, ly, 0xFF5C6370, false);
            String target = lines.get(i);
            if (i < typed.size()) {
                boolean ok = typed.get(i).trim().equals(target);
                ctx.drawText(textRenderer, target, x + 20, ly, ok ? 0xFF98C379 : 0xFFE06C75, false);
                ctx.drawText(textRenderer, ok ? "✔" : "✖", x + w - 14, ly, ok ? 0xFF98C379 : 0xFFE06C75, false);
            } else if (i == typed.size()) {
                // посимвольная подсветка текущей строки
                String in = field.getText();
                int cx = x + 20;
                for (int c = 0; c < target.length(); c++) {
                    String ch = String.valueOf(target.charAt(c));
                    int col = c < in.length() ? (in.charAt(c) == target.charAt(c) ? 0xFF98C379 : 0xFFE06C75) : 0xFFE5C07B;
                    ctx.drawText(textRenderer, ch, cx, ly, col, false);
                    cx += textRenderer.getWidth(ch);
                }
                if ((System.currentTimeMillis() / 400) % 2 == 0) ctx.fill(x + 16, ly, x + 17, ly + 9, 0xFF61AFEF);
            } else {
                ctx.drawText(textRenderer, lines.get(i), x + 20, ly, 0xFF4B5263, false);
            }
        }
        field.setX(x + 10);
        field.setY(y + h - 42);
        field.setWidth(w - 20);
        field.visible = typed.size() < lines.size();
        if (typed.size() >= lines.size()) {
            center(ctx, "Коммит отправлен! Оплата придёт на карту.", x + w / 2, y + h - 40, 0xFF98C379);
            button(ctx, x + w / 2 - 40, y + h - 24, 80, 16, "Готово", 0xFF3A7BD5, true, this::close);
        } else {
            ctx.drawText(textRenderer, "Печатай строку и жми Enter", x + 10, y + h - 20, 0xFF5C6370, false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return typed.size() >= lines.size();
    }
}
