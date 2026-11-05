package com.yompix.lifecraft.client;

import com.yompix.lifecraft.life.Jobs;
import com.yompix.lifecraft.life.LifeData;
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

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        LifeData d = LifeCraftClient.data;
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
        // задание: адрес и стрелка-направление
        if (d.taskPos != null && !d.taskKind.isEmpty()) {
            BlockPos p = d.taskPos;
            double dx = p.getX() + 0.5 - mc.player.getX(), dz = p.getZ() + 0.5 - mc.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            double ang = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYaw();
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String arrow = arrows[(int) Math.floorMod(Math.round(ang / 45.0), 8)];
            String t = (d.taskKind.equals("taxi") ? "Такси" : "Посылка") + ": " + arrow + " " + dist + " м";
            int ty = y + h + 3;
            ctx.fill(x, ty, x + tr.getWidth(t) + 10, ty + 13, 0x88302010);
            ctx.drawTextWithShadow(tr, t, x + 5, ty + 3, (now / 400) % 2 == 0 ? 0xFFFFC34D : 0xFFFFE6A8);
        }
    }
}
