package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.gta.Cheats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Окошко чит-кода (клавиша ` / Ё): вводишь код — Enter. Игра не реагирует на буквы, пока окно открыто. */
public class CheatScreen extends LifeScreen {
    private TextFieldWidget field;
    private String status = "";

    public CheatScreen() {
        super("Чит-код");
    }

    @Override
    protected void init() {
        super.init();
        field = new TextFieldWidget(textRenderer, width / 2 - 90, height / 2 - 6, 180, 18, Text.literal("код"));
        field.setMaxLength(24);
        field.setFocused(true);
        addDrawableChild(field);
        setInitialFocus(field);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = 260, h = 92, x = width / 2 - w / 2, y = height / 2 - 44;
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        title(ctx, "ЧИТ-КОД", x + 10, y + 8, 0xFFFFD54F);
        center(ctx, "Например HESOYAM — и Enter. Esc — закрыть", width / 2, y + 26, 0xFFB0BEC5);
        if (!status.isEmpty()) center(ctx, status, width / 2, y + 70, 0xFFFF5252);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String code = field.getText().trim().toUpperCase();
            if (Cheats.LIST.containsKey(code) || code.equals("TISHINA") || code.equals("HAOS") || code.equals("ARSENAL")
                    || code.equals("NOCH") || code.equals("DEN") || code.equals("LIVEN") || code.equals("SOLNCE") || code.equals("ZDOROV")) {
                NbtCompound n = new NbtCompound();
                n.putString("code", code);
                NovogradClient.send("cheat_code", n);
                close();
            } else status = "Такого кода нет";
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return field.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return field.charTyped(c, mods);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
