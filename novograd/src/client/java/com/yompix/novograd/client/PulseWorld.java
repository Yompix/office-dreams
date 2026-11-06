package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.TvBlock;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Всё, что «Пульс города» рисует в мире: граффити на стенах (тег банды → твой тег), фото-точки, подписи трамплинов,
 * видеостены «Новоград-24» и телевизоры на канале «Новости 24», прожекторы (NightFx).
 */
public final class PulseWorld {
    private PulseWorld() {}

    private static final Identifier[] RIVAL = {Novograd.id("textures/misc/graffiti/rival_0.png"), Novograd.id("textures/misc/graffiti/rival_1.png"),
            Novograd.id("textures/misc/graffiti/rival_2.png")};
    private static final Identifier[] MINE = new Identifier[6];

    static {
        for (int i = 0; i < MINE.length; i++) MINE[i] = Novograd.id("textures/misc/graffiti/tag_" + i + ".png");
    }

    private static final List<BlockPos> TVS = new ArrayList<>();

    static void tick(MinecraftClient c) {
        // телевизоры на канале «Новости 24» рядом (раз в секунду)
        if (c.player.age % 20 != 3) return;
        TVS.clear();
        BlockPos at = c.player.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -14; dx <= 14; dx++)
            for (int dy = -6; dy <= 6; dy++)
                for (int dz = -14; dz <= 14; dz++) {
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState s = c.world.getBlockState(m);
                    if (s.isOf(ModContent.TV) && s.get(TvBlock.CHANNEL) == 1) TVS.add(m.toImmutable());
                }
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        Vec3d cam = ctx.camera().getPos();
        try {
            collectibles(mc, m, imm, cam, ctx);
            screens(mc, m, imm, cam);
            tvBlocks(mc, m, imm, cam);
            imm.draw();
            NightFx.render(mc, m, imm, cam, ctx.tickCounter().getTickDelta(false));
            imm.draw();
        } catch (RuntimeException e) {
            Novograd.LOG.warn("PulseWorld", e);
        }
    }

    // ------------------------------------------------------------------ коллекции
    private static void collectibles(MinecraftClient mc, MatrixStack m, VertexConsumerProvider.Immediate imm, Vec3d cam, WorldRenderContext ctx) {
        NbtList near = PulseClient.near;
        TextRenderer tr = mc.textRenderer;
        for (int i = 0; i < near.size(); i++) {
            NbtCompound e = near.getCompound(i);
            String k = e.getString("k");
            boolean done = e.getBoolean("done");
            switch (k) {
                case "tag" -> {
                    BlockPos b = BlockPos.fromLong(e.getLong("b"));
                    Direction f = Direction.byId(e.getInt("f"));
                    if (b.getSquaredDistance(cam) > 64 * 64) continue;
                    int idx = e.getInt("i");
                    Identifier tex = done ? MINE[idx % MINE.length] : RIVAL[idx % RIVAL.length];
                    Vec3d c = Vec3d.ofCenter(b).add(f.getOffsetX() * 0.51, 0.5, f.getOffsetZ() * 0.51);
                    int light = WorldRenderer.getLightmapCoordinates(mc.world, b.offset(f).up());
                    m.push();
                    m.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
                    PulseClient.rotY(m, -f.asRotation());
                    decal(m, imm.getBuffer(RenderLayer.getEntityTranslucent(tex)), 1.0f, light, done ? 255 : 235);
                    m.pop();
                }
                case "photo" -> {
                    Vec3d p = new Vec3d(e.getDouble("x"), e.getDouble("y"), e.getDouble("z"));
                    if (p.squaredDistanceTo(cam) > 90 * 90) continue;
                    if (!done) beam(m, imm, cam, p, 0x4FE3FF, 14);
                    label(mc, m, imm, cam, p.add(0, 2.2, 0), done ? "✔ " + e.getString("name") : "◉ ФОТО: " + e.getString("name"),
                            done ? 0xFF9E9E9E : 0xFF4FE3FF, done ? 0.6f : 1f);
                }
                case "jump" -> {
                    int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
                    int[] d = dirs[e.getInt("dir")];
                    Vec3d p = new Vec3d(e.getInt("x") + 0.5 + d[0] * 5.5, e.getInt("y") + 3.2, e.getInt("z") + 0.5 + d[1] * 5.5);
                    if (p.squaredDistanceTo(cam) > 110 * 110) continue;
                    if (!done) beam(m, imm, cam, p, 0xFFA726, 10);
                    label(mc, m, imm, cam, p.add(0, 2.4, 0), done ? "✔ прыжок пройден" : "▲ УНИКАЛЬНЫЙ ПРЫЖОК — разгонись!", done ? 0xFF9E9E9E : 0xFFFFC107, done ? 0.6f : 1.2f);
                }
                default -> {}
            }
        }
    }

    private static void decal(MatrixStack m, VertexConsumer vc, float r, int light, int alpha) {
        Matrix4f mat = m.peek().getPositionMatrix();
        var entry = m.peek();
        vc.vertex(mat, -r, -r, 0).color(255, 255, 255, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        vc.vertex(mat, r, -r, 0).color(255, 255, 255, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        vc.vertex(mat, r, r, 0).color(255, 255, 255, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
        vc.vertex(mat, -r, r, 0).color(255, 255, 255, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 0, 1);
    }

    /** Светящийся столб над точкой (как метка задания, но своего цвета). */
    static void beam(MatrixStack m, VertexConsumerProvider imm, Vec3d cam, Vec3d at, int rgb, float top) {
        VertexConsumer vc = imm.getBuffer(RenderLayer.getLightning());
        m.push();
        m.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        Matrix4f mat = m.peek().getPositionMatrix();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int a = (int) (90 * pulse);
        float w = 0.3f;
        float[][] q = {{-w, 0, w, 0}, {0, -w, 0, w}};
        for (float[] s : q) {
            vc.vertex(mat, s[0], 0, s[1]).color(r, g, b, a);
            vc.vertex(mat, s[2], 0, s[3]).color(r, g, b, a);
            vc.vertex(mat, s[2], top, s[3]).color(r, g, b, 0);
            vc.vertex(mat, s[0], top, s[1]).color(r, g, b, 0);
        }
        m.pop();
    }

    /** Надпись-табличка, всегда лицом к камере. */
    static void label(MinecraftClient mc, MatrixStack m, VertexConsumerProvider imm, Vec3d cam, Vec3d at, String s, int color, float scale) {
        TextRenderer tr = mc.textRenderer;
        double dist = Math.sqrt(at.squaredDistanceTo(cam));
        float k = 0.025f * scale * (float) Math.max(1, dist / 14);
        m.push();
        m.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        m.multiply(mc.gameRenderer.getCamera().getRotation());
        m.scale(k, -k, k);
        Matrix4f mat = m.peek().getPositionMatrix();
        float x = -tr.getWidth(s) / 2f;
        tr.draw(s, x, 0, color, false, mat, imm, TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        m.pop();
    }

    // ------------------------------------------------------------------ видеостены «Новоград-24»
    private static void screens(MinecraftClient mc, MatrixStack m, VertexConsumerProvider.Immediate imm, Vec3d cam) {
        NbtList l = PulseClient.tvScreens;
        for (int i = 0; i < l.size(); i++) {
            NbtCompound s = l.getCompound(i);
            BlockPos b = BlockPos.fromLong(s.getLong("b"));
            Direction f = Direction.byId(s.getInt("f"));
            Vec3d c = Vec3d.ofCenter(b).add(f.getOffsetX() * 0.55, 0, f.getOffsetZ() * 0.55);
            if (c.squaredDistanceTo(cam) > 140 * 140) continue;
            // экран видно только спереди
            if ((cam.x - c.x) * f.getOffsetX() + (cam.z - c.z) * f.getOffsetZ() < 0) continue;
            m.push();
            m.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            PulseClient.rotY(m, -f.asRotation());
            float u = 1 / 16f; // экран 9×5 блоков = 144×80 единиц
            m.scale(u, -u, u);
            drawScreen(mc, m, imm, 144, 80, true);
            m.pop();
        }
    }

    /** Телевизоры в квартирах на канале «Новости 24»: та же лента, маленькая. */
    private static void tvBlocks(MinecraftClient mc, MatrixStack m, VertexConsumerProvider.Immediate imm, Vec3d cam) {
        for (BlockPos b : TVS) {
            BlockState st = mc.world.getBlockState(b);
            if (!st.isOf(ModContent.TV) || st.get(TvBlock.CHANNEL) != 1) continue;
            Direction f = st.get(Properties.HORIZONTAL_FACING);
            Vec3d c = Vec3d.ofCenter(b).add(f.getOffsetX() * 0.078, 0.03125, f.getOffsetZ() * 0.078);
            if ((cam.x - c.x) * f.getOffsetX() + (cam.z - c.z) * f.getOffsetZ() < 0) continue;
            m.push();
            m.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            PulseClient.rotY(m, -f.asRotation());
            float u = 0.875f / 112f;
            m.scale(u, -u, u);
            drawScreen(mc, m, imm, 112, 72, false);
            m.pop();
        }
    }

    private static void quad(Matrix4f mat, VertexConsumerProvider imm, float x1, float y1, float x2, float y2, float z, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        int L = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        VertexConsumer vc = imm.getBuffer(RenderLayer.getTextBackground()); // берём заново: текст между квадами закрывает буфер
        vc.vertex(mat, x1, y1, z).color(r, g, b, a).light(L);
        vc.vertex(mat, x1, y2, z).color(r, g, b, a).light(L);
        vc.vertex(mat, x2, y2, z).color(r, g, b, a).light(L);
        vc.vertex(mat, x2, y1, z).color(r, g, b, a).light(L);
    }

    /** Картинка канала в единицах экрана (0,0 — центр; +x вправо, +y вниз). */
    private static void drawScreen(MinecraftClient mc, MatrixStack m, VertexConsumerProvider.Immediate imm, float W, float H, boolean big) {
        TextRenderer tr = mc.textRenderer;
        NbtCompound feed = PulseClient.tvFeed;
        NbtList heads = feed.getList("head", NbtElement.COMPOUND_TYPE);
        float x0 = -W / 2, y0 = -H / 2;
        long ms = System.currentTimeMillis();
        Matrix4f mat = m.peek().getPositionMatrix();
        quad(mat, imm, x0, y0, x0 + W, y0 + H, -0.6f, 0xFF071022);
        quad(mat, imm, x0, y0, x0 + W, y0 + 12, -0.4f, 0xFFC62828);                // шапка
        quad(mat, imm, x0, y0 + H - 14, x0 + W, y0 + H, -0.4f, 0xFF0D1B33);         // биржа внизу
        int L = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        var POL = TextRenderer.TextLayerType.POLYGON_OFFSET;
        boolean blink = (ms / 600) % 2 == 0;
        String time = feed.getString("time");
        tr.draw(big ? "НОВОГРАД-24" : "НОВОСТИ 24", x0 + 3, y0 + 2, 0xFFFFFFFF, false, mat, imm, POL, 0, L);
        String live = (blink ? "● " : "  ") + (big ? "ЭФИР " : "") + time;
        tr.draw(live, x0 + W - 3 - tr.getWidth(live), y0 + 2, 0xFFFFE0E0, false, mat, imm, POL, 0, L);
        // главная новость: рубрика + бегущий заголовок крупно
        String[] h = {"Новоград-24: круглосуточные новости острова", "ГОРОД"};
        if (!heads.isEmpty()) {
            int idx = (int) ((ms / 14000) % heads.size());
            NbtCompound c = heads.getCompound(idx);
            h = new String[]{c.getString("h"), c.getString("c")};
        }
        int catCol = h[1].equals("БИРЖА") ? 0xFF2E7D32 : h[1].equals("ГОРОД") ? 0xFF1565C0 : 0xFFD32F2F;
        String cat = h[1].equals("ЭКСТРЕННО") ? "ЭКСТРЕННО" : h[1].equals("БИРЖА") ? "БИРЖА" : "НОВОСТИ";
        float cy = y0 + 15;
        quad(mat, imm, x0 + 3, cy, x0 + 7 + tr.getWidth(cat), cy + 10, -0.2f, catCol | 0xFF000000);
        tr.draw(cat, x0 + 5, cy + 1, 0xFFFFFFFF, false, mat, imm, POL, 0, L);
        float scale = big ? 2.6f : 2.0f;
        float lineY = cy + 14;
        m.push();
        m.translate(x0 + 3, lineY, 0);
        m.scale(scale, scale, 1);
        marquee(tr, m.peek().getPositionMatrix(), imm, h[0], (W - 6) / scale, (ms % 14000) / 1000f * 18f, 0xFFFFFFFF);
        m.pop();
        // вторая строка — предыдущая новость, мелко
        if (heads.size() > 1 && big) {
            NbtCompound c2 = heads.getCompound((int) ((ms / 14000 + 1) % heads.size()));
            String s2 = "Далее: " + c2.getString("h");
            marquee(tr, mat, imm, s2, W - 6, 0, 0xFF90A4AE, x0 + 3, lineY + 9 * scale + 3);
        }
        // низ: биржа бежит, справа погода
        String weather = feed.getString("weather");
        float ww = tr.getWidth(weather) + 6;
        quad(mat, imm, x0 + W - ww, y0 + H - 14, x0 + W, y0 + H, -0.2f, 0xFF16325C);
        tr.draw(weather, x0 + W - ww + 3, y0 + H - 11, 0xFFFFE082, false, mat, imm, POL, 0, L);
        String stocks = feed.getString("stocks");
        if (stocks.isEmpty()) stocks = "БИРЖА НОВОГРАДА";
        marquee(tr, mat, imm, stocks + "      ", W - ww - 6, (ms / 1000f) * 22f, 0xFFB9F6CA, x0 + 3, y0 + H - 11);
    }

    private static void marquee(TextRenderer tr, Matrix4f mat, VertexConsumerProvider imm, String s, float width, float offset, int color) {
        marquee(tr, mat, imm, s, width, offset, color, 0, 0);
    }

    /** Бегущая строка в окне шириной width: рисуем только целиком видимые буквы. */
    private static void marquee(TextRenderer tr, Matrix4f mat, VertexConsumerProvider imm, String s, float width, float offset, int color, float x, float y) {
        int L = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float full = tr.getWidth(s);
        if (full <= width) {
            tr.draw(s, x, y, color, false, mat, imm, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, L);
            return;
        }
        String loop = s + "   •   ";
        float period = tr.getWidth(loop);
        float off = offset % period;
        String text = loop + loop;
        float pos = 0;
        StringBuilder vis = new StringBuilder();
        float startX = -1;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float cw = tr.getWidth(ch);
            if (pos >= off && pos + cw <= off + width) {
                if (startX < 0) startX = pos - off;
                vis.append(ch);
            } else if (pos > off + width) break;
            pos += cw;
        }
        if (startX >= 0) tr.draw(vis.toString(), x + startX, y, color, false, mat, imm, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, L);
    }
}
