package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.Island;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Радар слева внизу, как в криминальных боевиках: карта острова поворачивается вместе с игроком,
 * жёлтая метка — цель задания, фиолетовая линия — маршрут по улицам, синие/красные точки — полиция и банды.
 * В машине радар отдаляется, чтобы видеть дорогу дальше. Под радаром — здоровье и броня.
 */
public final class Minimap {
    private Minimap() {}

    private static final Identifier TEX = Novograd.id("dynamic/radar");
    private static final int SIZE = 512; // блоков (1 пиксель = 1 блок)
    private static NativeImageBackedTexture tex;
    private static int ox = Integer.MIN_VALUE, oz;
    private static float zoom = 1f;
    /** Цель подработки (такси, патруль): голубая метка; пусто — нет. */
    public static String actLabel = "";
    public static double actX, actZ;
    public static int actTimer = -1;

    private static void bake(int cx, int cz) {
        int x0 = cx - SIZE / 2, z0 = cz - SIZE / 2;
        NativeImage img = tex != null ? tex.getImage() : new NativeImage(SIZE, SIZE, false);
        for (int pz = 0; pz < SIZE; pz++)
            for (int px = 0; px < SIZE; px++) {
                int x = x0 + px, z = z0 + pz;
                int argb = Island.mapColor(x, z);
                // радар — тёмный и контрастный: дороги светлые, остальное приглушено
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                boolean road = r == g && g == b && r < 0x70 || argb == 0xFF505560 || argb == 0xFF3E4148;
                if (road) { r = 0x9A; g = 0x9E; b = 0xA6; }
                else { r = r * 45 / 100; g = g * 50 / 100; b = b * 55 / 100; }
                img.setColor(px, pz, 0xFF000000 | b << 16 | g << 8 | r);
            }
        if (tex == null) {
            tex = new NativeImageBackedTexture(img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEX, tex);
        } else tex.upload();
        ox = x0;
        oz = z0;
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || !NovogradConfig.get().minimap || LifeHud.cinematic(mc)) return;
        var pl = mc.player;
        float delta = mc.getRenderTickCounter().getTickDelta(true);
        double px = MathHelper.lerp(delta, pl.prevX, pl.getX()), pz = MathHelper.lerp(delta, pl.prevZ, pl.getZ());
        if (tex == null || Math.abs(px - (ox + SIZE / 2)) > 140 || Math.abs(pz - (oz + SIZE / 2)) > 140) bake((int) px, (int) pz);

        int W = 132, H = 96, x = 12, y = ctx.getScaledWindowHeight() - H - 22;
        int cx = x + W / 2, cy = y + H / 2 + 14; // игрок ниже центра — впереди видно больше
        boolean inCar = pl.getVehicle() instanceof CarEntity;
        float want = inCar ? 0.55f + (float) Math.max(0, 0.35 - pl.getVehicle().getVelocity().horizontalLength()) : 1.1f;
        zoom += (want - zoom) * 0.05f;
        float yaw = MathHelper.lerpAngleDegrees(delta, pl.prevYaw, pl.getYaw());
        if (pl.getVehicle() != null) yaw = pl.getVehicle().getYaw(delta);

        // рамка
        ctx.fill(x - 3, y - 3, x + W + 3, y + H + 3, 0xC0000000);
        ctx.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF111418);
        ctx.enableScissor(x, y, x + W, y + H);
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-180 - yaw));
        m.scale(zoom, zoom, 1);
        m.translate(-px, -pz, 0); // дальше рисуем в координатах мира (x, z)
        ctx.drawTexture(TEX, ox, oz, 0, 0, SIZE, SIZE, SIZE, SIZE);

        // маршрут по улицам до цели (улицы — на линиях ≡ 24 по модулю 48)
        NbtCompound o = ClientStory.objective;
        boolean has = o.contains("x");
        double tx = o.getDouble("x"), tz = o.getDouble("z");
        if (has) {
            int sx = snap(px), sz = snap(pz), ex = snap(tx), ez = snap(tz);
            int col = 0xFFB04CFF;
            boolean onX = Math.abs(pz - sz) < Math.abs(px - sx); // стоим на улице вдоль X
            if (onX) {
                seg(ctx, px, sz, ex, sz, col);
                seg(ctx, ex, sz, ex, ez, col);
            } else {
                seg(ctx, sx, pz, sx, ez, col);
                seg(ctx, sx, ez, ex, ez, col);
            }
            seg(ctx, ex, ez, tx, ez, col);
            seg(ctx, tx, ez, tx, tz, col);
        }
        // полиция и банды рядом
        double r = 90 / zoom;
        for (Entity e : mc.world.getEntities()) {
            if (Math.abs(e.getX() - px) > r || Math.abs(e.getZ() - pz) > r || e == pl) continue;
            int c = 0;
            if (e instanceof CitizenEntity ce) {
                if (ce.getRole() == CitizenEntity.Role.POLICE) c = 0xFF4FA3FF;
                else if (ce.getRole() == CitizenEntity.Role.GANG) c = 0xFFFF4040;
                else if (!ce.getStoryId().isEmpty()) c = 0xFF7CFF8A;
            } else if (e instanceof CarEntity car && car.isPolice()) c = (pl.age / 5) % 2 == 0 ? 0xFF4FA3FF : 0xFFFF4040;
            else if (e instanceof net.minecraft.entity.player.PlayerEntity) c = 0xFFFF9EF5; // друзья онлайн — розовые
            if (c == 0) continue;
            float s = 2.2f / zoom;
            fillF(ctx, e.getX() - s, e.getZ() - s, e.getX() + s, e.getZ() + s, 0xFF000000);
            fillF(ctx, e.getX() - s * 0.7, e.getZ() - s * 0.7, e.getX() + s * 0.7, e.getZ() + s * 0.7, c);
        }
        m.pop();
        ctx.disableScissor();

        // цель: на радаре или прижата к краю
        if (has) {
            double dx = tx - px, dz = tz - pz;
            double a = Math.toRadians(-180 - yaw);
            double sxp = (dx * Math.cos(a) - dz * Math.sin(a)) * zoom, syp = (dx * Math.sin(a) + dz * Math.cos(a)) * zoom;
            double kx = sxp == 0 ? 1e9 : (W / 2.0 - 5) / Math.abs(sxp);
            double ky = syp == 0 ? 1e9 : (syp > 0 ? (y + H - 5 - cy) : (cy - y - 5)) / Math.abs(syp);
            double k = Math.min(1, Math.min(kx, ky));
            int bx = (int) (cx + sxp * k), by = (int) (cy + syp * k);
            boolean pulse = (System.currentTimeMillis() / 400) % 2 == 0;
            if (o.getString("type").equals("free")) {
                // метка новой миссии — жёлтая «М», как контакт в GTA
                ctx.fill(bx - 5, by - 5, bx + 5, by + 5, 0xFF000000);
                ctx.fill(bx - 4, by - 4, bx + 4, by + 4, 0xFFFFD000);
                ctx.drawText(mc.textRenderer, "M", bx - 2, by - 3, 0xFF000000, false);
            } else {
                ctx.fill(bx - 4, by - 4, bx + 4, by + 4, 0xFF000000);
                ctx.fill(bx - 3, by - 3, bx + 3, by + 3, pulse ? 0xFFFFD000 : 0xFFFFE860);
            }
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            if (k < 1 || dist > 25) {
                String ds = dist >= 1000 ? String.format("%.1f км", dist / 1000f) : dist + " м";
                int tw = mc.textRenderer.getWidth(ds);
                int lx = MathHelper.clamp(bx - tw / 2, x + 1, x + W - tw - 1), ly = by > cy ? by - 13 : by + 6;
                ctx.drawText(mc.textRenderer, ds, lx, ly, 0xFFFFE860, true);
            }
        }
        if (!actLabel.isEmpty()) blip(ctx, mc, px, pz, actX, actZ, yaw, cx, cy, x, y, W, H, 0xFF3FD8FF, actLabel);
        if (actTimer >= 0) {
            String ts = String.format("%d:%02d", actTimer / 60, actTimer % 60);
            ctx.drawText(mc.textRenderer, ts, x + W - mc.textRenderer.getWidth(ts) - 3, y + 3, actTimer < 15 ? 0xFFFF5050 : 0xFFFFFFFF, true);
        }
        // игрок — белая стрелка
        ctx.fill(cx - 1, cy - 4, cx + 1, cy + 3, 0xFF000000);
        for (int i = 0; i < 4; i++) ctx.fill(cx - i, cy - 3 + i, cx + i + 1, cy - 2 + i, 0xFFFFFFFF);
        // север
        double an = Math.toRadians(-180 - yaw);
        int nx = (int) (cx + Math.sin(an) * -1 * 0 + (0 * Math.cos(an) - (-1) * Math.sin(an)) * 60), ny = (int) (cy + (0 * Math.sin(an) + (-1) * Math.cos(an)) * 60);
        nx = MathHelper.clamp(nx, x + 4, x + W - 8);
        ny = MathHelper.clamp(ny, y + 4, y + H - 10);
        ctx.fill(nx - 4, ny - 2, nx + 5, ny + 9, 0xE0000000);
        ctx.drawText(mc.textRenderer, "N", nx - 2, ny, 0xFFFFFFFF, false);

        // здоровье и броня
        int by0 = y + H + 4;
        float hp = MathHelper.clamp(pl.getHealth() / pl.getMaxHealth(), 0, 1), armor = MathHelper.clamp(pl.getArmor() / 20f, 0, 1);
        int half = W / 2 - 1;
        ctx.fill(x - 2, by0 - 1, x + W + 2, by0 + 5, 0xC0000000);
        ctx.fill(x, by0, x + half, by0 + 4, 0xFF1E3A1E);
        ctx.fill(x, by0, x + (int) (half * hp), by0 + 4, hp < 0.3f && (pl.age / 6) % 2 == 0 ? 0xFFE53935 : 0xFF5BC236);
        ctx.fill(x + half + 2, by0, x + W, by0 + 4, 0xFF1A2A3A);
        ctx.fill(x + half + 2, by0, x + half + 2 + (int) ((W - half - 2) * armor), by0 + 4, 0xFF3FA9F5);
    }

    private static void blip(DrawContext ctx, MinecraftClient mc, double px, double pz, double tx, double tz, float yaw, int cx, int cy, int x, int y, int W, int H, int col, String label) {
        double dx = tx - px, dz = tz - pz;
        double a = Math.toRadians(-180 - yaw);
        double sxp = (dx * Math.cos(a) - dz * Math.sin(a)) * zoom, syp = (dx * Math.sin(a) + dz * Math.cos(a)) * zoom;
        double kx = sxp == 0 ? 1e9 : (W / 2.0 - 5) / Math.abs(sxp);
        double ky = syp == 0 ? 1e9 : (syp > 0 ? (y + H - 5 - cy) : (cy - y - 5)) / Math.abs(syp);
        double k = Math.min(1, Math.min(kx, ky));
        int bx = (int) (cx + sxp * k), by = (int) (cy + syp * k);
        ctx.fill(bx - 4, by - 4, bx + 4, by + 4, 0xFF000000);
        ctx.fill(bx - 3, by - 3, bx + 3, by + 3, col);
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        String ds = label + " · " + (dist >= 1000 ? String.format("%.1f км", dist / 1000f) : dist + " м");
        int tw = mc.textRenderer.getWidth(ds);
        int lx = MathHelper.clamp(bx - tw / 2, x + 1, x + W - tw - 1), ly = by > cy ? by - 13 : by + 6;
        ctx.drawText(mc.textRenderer, ds, lx, ly, col, true);
    }

    private static int snap(double v) {
        return (int) (Math.round((v - 24) / 48.0) * 48 + 24);
    }

    private static void seg(DrawContext ctx, double ax, double az, double bx, double bz, int col) {
        double w = 1.6 / zoom;
        fillF(ctx, Math.min(ax, bx) - w, Math.min(az, bz) - w, Math.max(ax, bx) + w, Math.max(az, bz) + w, col);
    }

    /** Прямоугольник с дробными координатами (ctx.fill округляет до целых, а мы в масштабе мира). */
    private static void fillF(DrawContext ctx, double x1, double y1, double x2, double y2, int col) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x1, y1, 0);
        m.scale((float) (x2 - x1), (float) (y2 - y1), 1);
        ctx.fill(0, 0, 1, 1, col);
        m.pop();
    }
}
