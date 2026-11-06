package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.Island;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Окно «В ЭФИРЕ» в углу экрана, пока над тобой кружит вертолёт «Новоград-24»: вид с вертолёта —
 * карта района с высоты (ночью — зелёная ночная камера), ты в прицеле оператора, патрульные вокруг,
 * логотип, таймкод и бегущая строка о погоне. Рисуется из готовых данных — без второго прохода рендера.
 */
public final class LiveHud {
    private LiveHud() {}

    private static final Identifier TEX = Novograd.id("dynamic/live_cam");
    private static final int SIZE = 128;
    private static NativeImageBackedTexture tex;
    private static int ox = Integer.MIN_VALUE, oz;
    private static boolean texNight;
    private static float show;
    private static long startedAt;

    static void tick(MinecraftClient c) {
        boolean on = PulseClient.liveOn() && !c.options.hudHidden;
        if (on && show == 0) startedAt = System.currentTimeMillis();
        show = MathHelper.clamp(show + (on ? 0.1f : -0.1f), 0, 1);
        if (show <= 0) return;
        int px = c.player.getBlockX(), pz = c.player.getBlockZ();
        boolean night = PulseClient.night(c);
        if (tex == null || Math.abs(px - (ox + SIZE / 2)) > 24 || Math.abs(pz - (oz + SIZE / 2)) > 24 || night != texNight) bake(px, pz, night);
    }

    private static void bake(int cx, int cz, boolean night) {
        int x0 = cx - SIZE / 2, z0 = cz - SIZE / 2;
        NativeImage img = tex != null ? tex.getImage() : new NativeImage(SIZE, SIZE, false);
        for (int pz = 0; pz < SIZE; pz++)
            for (int px = 0; px < SIZE; px++) {
                int argb = Island.mapColor(x0 + px, z0 + pz);
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                if (night) { // ночная камера: зелёный монохром
                    int l = (r * 3 + g * 5 + b * 2) / 10;
                    r = l / 5;
                    g = Math.min(255, l * 3 / 4 + 12);
                    b = l / 6;
                } else {
                    r = Math.min(255, r * 9 / 10 + 10);
                    g = Math.min(255, g * 9 / 10 + 10);
                    b = Math.min(255, b * 9 / 10 + 14);
                }
                img.setColor(px, pz, 0xFF000000 | b << 16 | g << 8 | r);
            }
        if (tex == null) {
            tex = new NativeImageBackedTexture(img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEX, tex);
        } else tex.upload();
        ox = x0;
        oz = z0;
        texNight = night;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (show <= 0 || mc.player == null || mc.world == null || tex == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth();
        int bw = 168, bh = 104;
        int x = W - bw - 10 + (int) ((1 - show) * (bw + 20)), y = 98; // под деньгами и звёздами
        float delta = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d pp = mc.player.getLerpedPos(delta);
        Entity heli = mc.world.getEntityById(PulseClient.live.getInt("id"));
        float bearing = heli != null ? PulseClient.yawTo(heli.getLerpedPos(delta), pp) : mc.player.getYaw();
        boolean night = PulseClient.night(mc);
        long ms = System.currentTimeMillis();

        ctx.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, 0xFF000000);
        ctx.enableScissor(x, y, x + bw, y + bh);
        var m = ctx.getMatrices();
        m.push();
        int cx = x + bw / 2, cy = y + bh / 2 + 6;
        float zoom = 2.6f + 0.15f * (float) Math.sin(ms / 1700.0); // оператор чуть «дышит» зумом
        float shakeX = (float) Math.sin(ms / 230.0) * 0.8f, shakeY = (float) Math.cos(ms / 310.0) * 0.6f;
        m.translate(cx + shakeX, cy + shakeY, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-180 - bearing));
        m.scale(zoom, zoom, 1);
        m.translate(-pp.x, -pp.z, 0);
        ctx.drawTexture(TEX, ox, oz, 0, 0, SIZE, SIZE, SIZE, SIZE);
        // патрульные и машины полиции
        for (Entity e : mc.world.getEntities()) {
            if (Math.abs(e.getX() - pp.x) > 40 || Math.abs(e.getZ() - pp.z) > 40) continue;
            int col = 0;
            if (e instanceof CitizenEntity ce && ce.getRole() == CitizenEntity.Role.POLICE) col = 0xFF4FA3FF;
            else if (e instanceof CarEntity car && car.isPolice()) col = (ms / 250) % 2 == 0 ? 0xFF4FA3FF : 0xFFFF4040;
            else if (e instanceof CarEntity) col = 0xFF8A8F99;
            if (col == 0) continue;
            float s = e instanceof CarEntity ? 1.1f : 0.55f;
            m.push();
            m.translate(e.getX(), e.getZ(), 0);
            m.scale(s, s, 1);
            ctx.fill(-1, -1, 1, 1, col);
            m.pop();
        }
        m.pop();
        // ты в кадре
        try {
            if (mc.player.getVehicle() instanceof CarEntity) {
                ctx.fill(cx - 5, cy - 8, cx + 5, cy + 8, 0xFF20242C);
                ctx.fill(cx - 4, cy - 7, cx + 4, cy + 7, 0xFFE0E0E0);
                ctx.fill(cx - 3, cy - 4, cx + 3, cy - 1, 0xFF4A6A8A);
            } else {
                net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(ctx, cx - 14, cy - 26, cx + 14, cy + 6, 13, 0.0625f,
                        cx + 20f, cy - 60f, mc.player);
            }
        } catch (RuntimeException ignored) {}
        // прицел оператора и красный круг вокруг цели
        boolean pulse = (ms / 400) % 2 == 0;
        int rc = pulse ? 0xFFFF3030 : 0xC0FF3030;
        ring(ctx, cx, cy - 4, 15, rc);
        // рамка камеры
        int k = 10, cc = 0xFFFFFFFF;
        ctx.fill(x + 4, y + 4, x + 4 + k, y + 5, cc);
        ctx.fill(x + 4, y + 4, x + 5, y + 4 + k, cc);
        ctx.fill(x + bw - 4 - k, y + 4, x + bw - 4, y + 5, cc);
        ctx.fill(x + bw - 5, y + 4, x + bw - 4, y + 4 + k, cc);
        ctx.fill(x + 4, y + bh - 5, x + 4 + k, y + bh - 4, cc);
        ctx.fill(x + 4, y + bh - 4 - k, x + 5, y + bh - 4, cc);
        ctx.fill(x + bw - 4 - k, y + bh - 5, x + bw - 4, y + bh - 4, cc);
        ctx.fill(x + bw - 5, y + bh - 4 - k, x + bw - 4, y + bh - 4, cc);
        // строки развёртки и виньетка
        for (int yy = y; yy < y + bh; yy += 3) ctx.fill(x, yy, x + bw, yy + 1, night ? 0x2200FF00 : 0x18000000);
        ctx.fill(x, y, x + bw, y + 6, 0x50000000);
        ctx.fill(x, y + bh - 6, x + bw, y + bh, 0x50000000);
        ctx.disableScissor();
        // подписи
        if ((ms / 500) % 2 == 0) ctx.fill(x + 8, y + 9, x + 13, y + 14, 0xFFFF2020);
        ctx.drawText(tr, "В ЭФИРЕ", x + 16, y + 8, 0xFFFFFFFF, true);
        String tc = String.format("%02d:%02d:%02d", (ms - startedAt) / 60000 % 60, (ms - startedAt) / 1000 % 60, (ms - startedAt) / 40 % 25);
        ctx.drawText(tr, tc, x + bw - 8 - tr.getWidth(tc), y + 8, 0xFFFFFFFF, true);
        if (night) ctx.drawText(tr, "НОЧНАЯ КАМЕРА", x + 8, y + bh - 30, 0xFF7CFF7C, true);
        // логотип канала
        ctx.fill(x + 6, y + bh - 18, x + 6 + tr.getWidth("НОВОГРАД-24") + 8, y + bh - 6, 0xFFC62828);
        ctx.drawText(tr, "НОВОГРАД-24", x + 10, y + bh - 16, 0xFFFFFFFF, false);
        // бегущая строка под окном
        int sy = y + bh + 3;
        ctx.fill(x - 2, sy, x + bw + 2, sy + 12, 0xE0101418);
        ctx.fill(x - 2, sy, x + 44, sy + 12, 0xFFC62828);
        ctx.drawText(tr, "ПОГОНЯ", x + 3, sy + 2, 0xFFFFFFFF, false);
        String stars = "★".repeat(Math.max(0, PulseClient.live.getInt("stars")));
        String line = "Полиция преследует подозреваемого: " + PulseClient.live.getString("zone") + " · угроза " + stars + " · смотрите «Новоград-24»   ";
        int lx = x + 48, lw = bw - 50;
        ctx.enableScissor(lx, sy, lx + lw, sy + 12);
        int textW = tr.getWidth(line);
        int off = (int) ((ms / 25) % (textW + 20));
        ctx.drawText(tr, line, lx + lw - off, sy + 2, 0xFFFFE082, false);
        ctx.drawText(tr, line, lx + lw - off + textW + 20, sy + 2, 0xFFFFE082, false);
        ctx.disableScissor();
    }

    private static void ring(DrawContext ctx, int cx, int cy, int r, int col) {
        for (int i = 0; i < 48; i++) {
            if (i % 4 == 3) continue;
            double a = i * Math.PI * 2 / 48;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            ctx.fill(px, py, px + 1, py + 1, col);
        }
    }
}
