package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.9.5 «Наблюдение» (скрытая настройка): над горожанами — имя, характер, чем занят и шкалы нужд (данные — gta/Observe).
 * Включить/выключить: команда /наблюдение (или /observe) или -Dnovograd.observe=true.
 */
public final class ObserveOverlay {
    private ObserveOverlay() {}

    public static boolean on = Boolean.getBoolean("novograd.observe");
    private static List<NbtCompound> list = new ArrayList<>();
    private static final String[] NEED = {"Еда", "Сил", "Общ", "Отд", "$"};

    public static void toggle(boolean v) {
        on = v;
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", v);
        NovogradClient.send("observe", n);
        if (!v) list = new ArrayList<>();
    }

    /** Мир загрузился — напомнить серверу, если включено свойством. */
    public static void join() {
        if (on) toggle(true);
    }

    public static void receive(NbtCompound n) {
        List<NbtCompound> l = new ArrayList<>();
        NbtList nl = n.getList("list", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < nl.size(); i++) l.add(nl.getCompound(i));
        list = l;
    }

    private static Text bars(String csv) {
        MutableText t = Text.empty();
        String[] v = csv.split(",");
        for (int i = 0; i < v.length && i < NEED.length; i++) {
            float f;
            try {
                f = Float.parseFloat(v[i]);
            } catch (NumberFormatException e) {
                continue;
            }
            int k = Math.round(Math.max(0, Math.min(1, f)) * 5);
            int col = f > 0.6f ? 0x7CFF7C : f > 0.3f ? 0xFFD54F : 0xFF6E6E;
            if (i > 0) t.append(Text.literal(" "));
            t.append(Text.literal(NEED[i]).withColor(0xB0BEC5)).append(Text.literal("▮".repeat(k) + "▯".repeat(5 - k)).withColor(col));
        }
        return t;
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!on || list.isEmpty() || mc.world == null || ctx.consumers() == null || mc.options.hudHidden) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        TextRenderer tr = mc.textRenderer;
        float delta = ctx.tickCounter().getTickDelta(false);
        for (NbtCompound n : list) {
            Entity e = mc.world.getEntityById(n.getInt("id"));
            if (e == null || e.squaredDistanceTo(cam) > 30 * 30) continue;
            Vec3d at = e.getLerpedPos(delta).add(0, e.getHeight() + 0.55, 0);
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal(n.getString("name")).withColor(0xFFFFFF));
            if (!n.getString("l1").isEmpty()) lines.add(Text.literal(n.getString("l1")).withColor(0x80DEEA));
            if (!n.getString("l2").isEmpty()) lines.add(Text.literal(n.getString("l2")).withColor(0xFFE082));
            if (!n.getString("needs").isEmpty()) lines.add(bars(n.getString("needs")));
            m.push();
            m.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            m.multiply(ctx.camera().getRotation());
            m.scale(0.022f, -0.022f, 0.022f);
            Matrix4f mat = m.peek().getPositionMatrix();
            int y = -lines.size() * 10;
            for (Text t : lines) {
                float x = -tr.getWidth(t) / 2f;
                tr.draw(t, x, y, 0xFFFFFFFF, false, mat, ctx.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
                y += 10;
            }
            m.pop();
        }
    }
}
