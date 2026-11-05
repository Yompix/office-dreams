package com.yompix.novograd.client;

import com.yompix.novograd.life.Jobs;
import com.yompix.novograd.life.LifeData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;

/** Плашка в левом верхнем углу: часы, деньги, учёба, работа и текущее задание со стрелкой. */
public final class LifeHud {
    private LifeHud() {}

    private static int shownMoney = -1;
    private static long flashUntil;

    public static String clock(long timeOfDay) {
        long t = (timeOfDay + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
    }

    private static void bar(DrawContext ctx, TextRenderer tr, int x, int y, String label, int value, int color) {
        ctx.fill(x, y, x + 84, y + 7, 0x88101020);
        ctx.fill(x + 1, y + 1, x + 1 + (int) (82 * value / 100f), y + 6, color);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 3, y + 1, 0);
        ctx.getMatrices().scale(0.6f, 0.6f, 1);
        ctx.drawText(tr, label + " " + value + "%", 0, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().pop();
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        LifeData d = NovogradClient.data;
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        // деньги «докручиваются» к новому значению
        if (shownMoney < 0) shownMoney = d.money;
        if (shownMoney != d.money) {
            int diff = d.money - shownMoney;
            shownMoney += Math.signum(diff) * Math.max(1, Math.abs(diff) / 8);
            flashUntil = now + 600;
        }
        long tod = mc.world.getTimeOfDay() % 24000;
        String job = Jobs.Job.of(d.job).title;
        String[] lines = {
                "⏰ " + clock(tod) + "   День " + (mc.world.getTimeOfDay() / 24000 + 1),
                "$" + shownMoney,
                "✎ " + d.eduName() + "  " + d.lessons + "/" + LifeData.lessonsNeeded(d.edu),
                "⚒ " + job};
        int w = 0;
        for (String l : lines) w = Math.max(w, tr.getWidth(l));
        int x = 4, y = 4, h = lines.length * 10 + 6;
        ctx.fill(x, y, x + w + 10, y + h, 0x88101020);
        ctx.fill(x, y, x + 2, y + h, 0xFF4AA3FF);
        boolean flash = now < flashUntil;
        int[] colors = {0xFFE0E6F0, flash ? 0xFF7CFF8A : 0xFF5BE37D, 0xFFFFD36E, 0xFF9FC8FF};
        for (int i = 0; i < lines.length; i++) ctx.drawTextWithShadow(tr, lines[i], x + 6, y + 4 + i * 10, colors[i]);
        if (d.unread > 0) {
            String m = "✉ " + d.unread;
            int mx = x + w + 14;
            ctx.fill(mx, y, mx + tr.getWidth(m) + 6, y + 12, 0xCC2FA84F);
            ctx.drawTextWithShadow(tr, m, mx + 3, y + 2, 0xFFFFFFFF);
        }
        // энергия и настроение
        int ny = y + h + 2;
        bar(ctx, tr, x, ny, "Энергия", d.energy, d.energy < 20 ? 0xFFE53935 : 0xFF42A5F5);
        bar(ctx, tr, x, ny + 9, "Настроение", d.mood, d.mood < 30 ? 0xFFFF7043 : 0xFFFFCA28);
        ctx.drawTextWithShadow(tr, "⚖ " + Math.round(com.yompix.novograd.life.Fitness.shown(d)) + " кг · форма " + d.fitness, x, ny + 18, d.weight > 85 ? 0xFFFFA726 : 0xFFB0BEC5);
        h += 20;
        // задание: адрес и стрелка-направление
        if (d.taskPos != null && !d.taskKind.isEmpty()) {
            BlockPos p = d.taskPos;
            double dx = p.getX() + 0.5 - mc.player.getX(), dz = p.getZ() + 0.5 - mc.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            double ang = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYaw();
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String arrow = arrows[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
            String kind = d.taskKind.equals("taxi") ? "Везёшь клиента" : d.taskKind.equals("taxi_wait") ? "Такси подъедет сюда"
                    : d.taskKind.equals("taxi_ride") ? "Едем" : d.taskKind.startsWith("pickup:") ? "Забери клиента"
                    : d.taskKind.startsWith("quest") ? "Задание" : "Посылка";
            String t = kind + ": " + arrow + " " + dist + " м";
            int ty = y + h + 3;
            ctx.fill(x, ty, x + tr.getWidth(t) + 10, ty + 13, 0x88302010);
            ctx.drawTextWithShadow(tr, t, x + 5, ty + 3, (now / 400) % 2 == 0 ? 0xFFFFC34D : 0xFFFFE6A8);
        }
    }
}
