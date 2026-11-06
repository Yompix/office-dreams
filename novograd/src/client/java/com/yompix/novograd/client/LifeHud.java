package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Jobs;
import com.yompix.novograd.life.LifeData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * HUD «Тёмных улиц». Справа сверху — звёзды розыска, деньги зелёным с обводкой, часы и оружие с патронами.
 * Слева сверху — работа и задание работы со стрелкой.
 * Левый нижний угол свободен (там радар).
 */
public final class LifeHud {
    private LifeHud() {}

    private static int shownMoney = -1;
    private static long flashUntil;
    private static int lastDiff;

    public static String clock(long timeOfDay) {
        long t = (timeOfDay + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
    }

    /** 12345 → «12 345». */
    public static String money(int v) {
        String s = String.valueOf(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    /** Во время разговора и кат-сцены HUD прячется, как в кино. */
    public static boolean cinematic(MinecraftClient mc) {
        return mc.currentScreen instanceof com.yompix.novograd.client.story.DialogScreen || mc.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen;
    }

    private static float scale() {
        return Math.max(0.5f, Math.min(2f, NovogradConfig.get().hudScale));
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud() || cinematic(mc)) return;
        LifeData d = NovogradClient.data;
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        int w = ctx.getScaledWindowWidth();
        // деньги «докручиваются» к новому значению
        if (shownMoney < 0) shownMoney = d.money;
        if (shownMoney != d.money) {
            int diff = d.money - shownMoney;
            if (now > flashUntil - 300) lastDiff = diff;
            shownMoney += Math.signum(diff) * Math.max(1, Math.abs(diff) / 8);
            flashUntil = now + 1400;
        }
        float s = scale();
        var m = ctx.getMatrices();
        // ---------------- справа сверху
        m.push();
        m.translate(w, 0, 0);
        m.scale(s, s, 1);
        int r = -8;
        stars(ctx, r, 6, now);
        String cash = "$" + money(shownMoney);
        boolean flash = now < flashUntil;
        Ui.big(ctx, tr, cash, r, 20, 1.7f, flash ? (lastDiff < 0 ? 0xFFFF6B6B : 0xFFA6FFB6) : Ui.MONEY, 2);
        if (flash && lastDiff != 0) {
            float k = (flashUntil - now) / 1400f;
            String dd = (lastDiff > 0 ? "+$" : "-$") + money(Math.abs(lastDiff));
            Ui.big(ctx, tr, dd, r, 38 + (1 - k) * -4, 1.1f, Ui.alpha(lastDiff > 0 ? Ui.MONEY : Ui.RED, Math.min(1, k * 2)), 2);
        }
        long tod = mc.world.getTimeOfDay() % 24000;
        String time = clock(tod) + "  ДЕНЬ " + (mc.world.getTimeOfDay() / 24000 + 1);
        int ty = flash && lastDiff != 0 ? 50 : 38;
        Ui.small(ctx, tr, time, r, ty, 0.8f, 0xFFE6E2F0, 2);
        if (d.unread > 0) {
            String msg = "✉ " + d.unread;
            int mw = tr.getWidth(msg) + 8;
            int mx = r - (int) (tr.getWidth(cash) * 1.7f) - mw - 6;
            Ui.slant(ctx, mx, 22, mw, 11, 3, 0xE02FA84F);
            ctx.drawText(tr, msg, mx + 4, 24, 0xFFFFFFFF, false);
        }
        weapon(ctx, tr, mc, r, ty + 11);
        m.pop();
        // ---------------- слева сверху
        m.push();
        m.scale(s, s, 1);
        status(ctx, tr, mc, d, now);
        m.pop();
    }

    /** Звёзды розыска: горят жёлтым, мигают, когда полиция потеряла след; пустые — едва видны. */
    private static void stars(DrawContext ctx, int right, int y, long now) {
        int stars = NovogradClient.wantedStars;
        if (stars <= 0) return;
        boolean blink = !NovogradClient.wantedSeen && (now / 300) % 2 == 0;
        int size = 12, gap = 1;
        for (int i = 0; i < 5; i++) {
            int x = right - (5 - i) * (size + gap);
            boolean on = i < stars;
            if (on) {
                if (blink) Ui.tint(ctx, Ui.STAR, x, y, size, size, 32, 32, 0.55f, 0.45f, 0.2f, 1);
                else Ui.tint(ctx, Ui.STAR, x, y, size, size, 32, 32, 1f, 0.84f, 0.3f, 1);
            } else Ui.tex(ctx, Ui.STAR_EMPTY, x, y, size, size, 32, 32, 0.8f);
        }
    }

    /** Оружие в руке: косая плашка с иконкой, названием и патронами. */
    private static void weapon(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int right, int y) {
        ItemStack st = mc.player.getMainHandStack();
        if (!com.yompix.novograd.gta.Weapons.isGun(st)) return;
        int ammo = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(ModContent.AMMO)) ammo += inv.getStack(i).getCount();
        String a = mc.player.isCreative() ? "∞" : String.valueOf(ammo);
        String name = st.getName().getString().toUpperCase();
        int bw = Math.max(70, tr.getWidth(name) + 34);
        int x = right - bw;
        Ui.slant(ctx, x, y, bw, 22, 6, 0xD00B0812);
        ctx.fill(x + 6, y + 21, right, y + 22, ammo == 0 && !mc.player.isCreative() ? Ui.RED : Ui.PINK);
        ctx.drawItem(st, x + 8, y + 3);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 200);
        Ui.small(ctx, tr, name, right - 4, y + 3, 0.7f, 0xFFB8B2CC, 2);
        Ui.big(ctx, tr, a, right - 4, y + 10, 1f, ammo == 0 && !mc.player.isCreative() ? Ui.RED : 0xFFFFFFFF, 2);
        ctx.getMatrices().pop();
    }

    private static void status(DrawContext ctx, TextRenderer tr, MinecraftClient mc, LifeData d, long now) {
        int x = 6, y = 6, h = 0;
        Jobs.Job jb = Jobs.Job.of(d.job);
        if (jb != Jobs.Job.NONE) {
            String job = jb.title.toUpperCase();
            int jw = (int) (tr.getWidth(job) * 0.8f) + 14;
            Ui.slant(ctx, x - 2, y - 2, jw, 12, 3, 0x9A0B0812);
            ctx.fill(x - 2, y - 2, x, y + 10, Ui.CYAN);
            Ui.small(ctx, tr, job, x + 4, y + 1, 0.8f, 0xFFFFFFFF, 0);
            h = 12;
        }
        // задание работы: адрес и стрелка-направление
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
            int ty = y - 2 + h + (h > 0 ? 2 : 0);
            int tw = tr.getWidth(t) + 12;
            Ui.slant(ctx, x - 2, ty, tw, 13, 4, 0xD0201408);
            ctx.fill(x - 2, ty, x, ty + 13, Ui.GOLD);
            ctx.drawText(tr, t, x + 4, ty + 3, (now / 400) % 2 == 0 ? 0xFFFFC34D : 0xFFFFE6A8, true);
        }
    }
}
