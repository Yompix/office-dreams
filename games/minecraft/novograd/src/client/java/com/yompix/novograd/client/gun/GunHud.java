package com.yompix.novograd.client.gun;

import com.yompix.novograd.client.GunFx;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.MathHelper;

/**
 * Прицел шутера: четыре чёрточки расходятся при ходьбе и стрельбе, в прицеливании (ПКМ) пропадают.
 * Хитмаркер — крестик и «тк» при попадании, красный при убийстве.
 */
public final class GunHud {
    private GunHud() {}

    private static long hitAt;
    private static boolean kill;
    private static float gap = 6;

    public static void hit(boolean k) {
        hitAt = System.nanoTime();
        kill = k;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(k ? Arsenal.HIT_KILL : Arsenal.HIT_MARKER, 1f, k ? 0.9f : 0.75f));
    }

    /** Ванильный «плюсик» не рисуем, когда в руках ствол и вид от первого лица. */
    public static boolean replacesCrosshair() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.player != null && c.options.getPerspective().isFirstPerson() && c.player.getMainHandStack().getItem() instanceof GunItem;
    }

    public static void render(DrawContext ctx, float delta) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden || !replacesCrosshair() || c.currentScreen != null && !(c.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;
        float target = GunFx.crosshairGap();
        gap += (target - gap) * 0.35f;
        float alpha = 1 - GunFx.adsAmount() * 1.6f;
        if (GunFx.reloadProgress(c.player.getMainHandStack().getItem()) >= 0) alpha *= 0.45f;
        if (alpha > 0.02f) {
            int a = (int) (alpha * 230) << 24;
            int g = Math.round(gap), len = 4;
            // тень + белая чёрточка
            bar(ctx, cx - g - len, cy, len, 1, a);
            bar(ctx, cx + g + 1, cy, len, 1, a);
            bar(ctx, cx, cy - g - len, 1, len, a);
            bar(ctx, cx, cy + g + 1, 1, len, a);
            ctx.fill(cx, cy, cx + 1, cy + 1, a | 0xFFFFFF);
        }
        // в прицеле — маленькая точка (как у коллиматора): куда летит пуля, ровно над мушкой
        float ads = GunFx.adsAmount();
        if (ads > 0.6f && !(c.player.getMainHandStack().getItem() == com.yompix.novograd.gta.Arsenal.SNIPER)) {
            int a = (int) (Math.min(1, (ads - 0.6f) * 2.5f) * 220) << 24;
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, a);
            ctx.fill(cx, cy, cx + 1, cy + 1, a | 0xFF3B3B);
        }
        float age = (System.nanoTime() - hitAt) / 1e9f;
        float life = GunFx.testFlashNs > 0 ? 1.2f : 0.3f;
        if (age < life) {
            float k = 1 - age / life;
            int col = ((int) (k * 255) << 24) | (kill ? 0xFF3A3A : 0xFFFFFF);
            int off = 4 + Math.round((1 - k) * 2), n = kill ? 5 : 4;
            for (int i = 0; i < n; i++) {
                int d = off + i;
                ctx.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, col);
                ctx.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, col);
                ctx.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, col);
                ctx.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, col);
            }
        }
    }

    private static void bar(DrawContext ctx, int x, int y, int w, int h, int a) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, ((a >>> 25) << 24));
        ctx.fill(x, y, x + w, y + h, a | 0xFFFFFF);
    }

    static float clamp(float v, float a, float b) {
        return MathHelper.clamp(v, a, b);
    }
}
