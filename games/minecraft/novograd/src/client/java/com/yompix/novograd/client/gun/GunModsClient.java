package com.yompix.novograd.client.gun;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.gta.GunMods;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Обвесы Мастерской на модели ствола (GeckoLib): глушитель на дуле, коллиматор / оптика на планке, рукоятка под цевьём,
 * удлинённый магазин (едет вместе с костью mag при перезарядке), окраска (своя текстура) и свечение неона.
 * Точки крепления — guns/meta.json («attach», пишет tools/gen_gun_geo.py). Всё в «пикселях предмета».
 */
public final class GunModsClient {
    private GunModsClient() {}

    static final Identifier ATTACH = Novograd.id("textures/item/guns/attach.png");
    /** Клетки attach.png: тёмная сталь, полимер, стекло, красная точка. */
    static final int STEEL = 0, POLY = 1, GLASS = 2, DOT = 3;
    static final float SIL_LEN = 5.5f, TUBE_HALF = 3.0f;

    // ---------------------------------------------------------------- точки для прицеливания и вспышки
    /** Точка прицеливания: с прицелом — центр линзы, иначе — родной целик. */
    public static float[] sight(GunMeta m, ItemStack s) {
        if (m.scopeAt == null || !GunMods.has(s, GunMods.SCOPE)) return m.sight;
        float[] a = m.scopeAt;
        return m.scopeDot ? new float[]{a[0], a[1] + 0.75f, a[2] + 0.8f} : new float[]{a[0], a[1] + 1.15f, a[2] + TUBE_HALF};
    }

    /** Дуло: с глушителем — его конец. */
    public static float[] muzzle(GunMeta m, ItemStack s) {
        if (m.silAt == null || !GunMods.has(s, GunMods.SILENCER)) return m.muzzle;
        return new float[]{m.silAt[0], m.silAt[1], m.silAt[2] - SIL_LEN};
    }

    /** Зум в прицеле с обвесом (null — родной). */
    public static Float zoom(GunMeta m, ItemStack s) {
        if (m.scopeAt == null || !GunMods.has(s, GunMods.SCOPE)) return null;
        return m.scopeDot ? 0.7f : 0.62f;
    }

    /** Текстура с окраской Мастерской. */
    public static Identifier texture(String gun, ItemStack s) {
        String k = s == null ? "" : GunMods.skinId(s);
        return Novograd.id("textures/item/guns/" + gun + (k.isEmpty() ? "" : "_" + k) + ".png");
    }

    // ---------------------------------------------------------------- слои для GunGeoRenderer
    /** Обвесы: рисуются в пространстве кости (root — неподвижные, mag — магазин). */
    static final class AttachLayer extends GeoRenderLayer<GunItem> {
        private final GunGeoRenderer r;

        AttachLayer(GunGeoRenderer r) {
            super(r);
            this.r = r;
        }

        @Override
        public void renderForBone(MatrixStack m, GunItem gun, GeoBone bone, RenderLayer type, VertexConsumerProvider buffers, VertexConsumer buffer,
                                  float partialTick, int light, int overlay) {
            ItemStack s = r.getCurrentItemStack();
            if (s == null || bone.isHidden()) return;
            int mods = GunMods.get(s) & GunMods.allowed(gun.gunId());
            if ((mods & 15) == 0) return;
            String n = bone.getName();
            boolean root = n.equals("root"), mag = n.equals("mag") && (mods & GunMods.EXTMAG) != 0;
            if (!root && !mag) return;
            GunMeta meta = GunMeta.get(gun.gunId());
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(ATTACH));
            if (root) {
                if ((mods & GunMods.SILENCER) != 0 && meta.silAt != null) silencer(m, vc, meta.silAt, light);
                if ((mods & GunMods.SCOPE) != 0 && meta.scopeAt != null) scope(m, vc, meta, light);
                if ((mods & GunMods.GRIP) != 0 && meta.gripAt != null) {
                    float[] g = meta.gripAt;
                    box(m, vc, g[0] - 0.45f, g[1] - 2.6f, g[2] - 0.5f, g[0] + 0.45f, g[1], g[2] + 0.5f, POLY, light);
                    box(m, vc, g[0] - 0.55f, g[1] - 0.25f, g[2] - 0.7f, g[0] + 0.55f, g[1] + 0.05f, g[2] + 0.7f, STEEL, light);
                }
            } else extMag(m, vc, meta, light);
            buffers.getBuffer(type); // вернуть буфер ствола (см. GeoRenderLayer.renderForBone)
        }

        private static void silencer(MatrixStack m, VertexConsumer vc, float[] a, int light) {
            float rr = a[3] + 0.18f, z0 = a[2] - SIL_LEN, z1 = a[2] + 0.3f;
            octo(m, vc, a[0], a[1], z0, z1, rr, STEEL, light);
            octo(m, vc, a[0], a[1], z0 - 0.05f, z0 + 0.3f, rr * 0.55f, POLY, light);
        }

        private static void scope(MatrixStack m, VertexConsumer vc, GunMeta meta, int light) {
            float[] a = meta.scopeAt;
            float x = a[0], y = a[1], z = a[2];
            if (meta.scopeDot) {
                box(m, vc, x - 0.55f, y, z - 0.8f, x + 0.55f, y + 0.25f, z + 0.8f, STEEL, light);
                box(m, vc, x - 0.55f, y + 0.25f, z - 0.75f, x - 0.4f, y + 1.3f, z - 0.45f, STEEL, light);
                box(m, vc, x + 0.4f, y + 0.25f, z - 0.75f, x + 0.55f, y + 1.3f, z - 0.45f, STEEL, light);
                box(m, vc, x - 0.55f, y + 1.15f, z - 0.75f, x + 0.55f, y + 1.3f, z - 0.45f, STEEL, light);
                box(m, vc, x - 0.4f, y + 0.25f, z - 0.62f, x + 0.4f, y + 1.15f, z - 0.58f, GLASS, light);
                box(m, vc, x - 0.06f, y + 0.69f, z - 0.66f, x + 0.06f, y + 0.81f, z - 0.62f, DOT, 0xF000F0);
            } else {
                float cy = y + 1.15f;
                box(m, vc, x - 0.4f, y, z - 1.6f, x + 0.4f, cy - 0.3f, z - 0.8f, STEEL, light);
                box(m, vc, x - 0.4f, y, z + 0.8f, x + 0.4f, cy - 0.3f, z + 1.6f, STEEL, light);
                octo(m, vc, x, cy, z - TUBE_HALF, z + TUBE_HALF, 0.62f, STEEL, light);
                octo(m, vc, x, cy, z - TUBE_HALF - 1.0f, z - TUBE_HALF, 0.82f, STEEL, light);
                octo(m, vc, x, cy, z - TUBE_HALF - 1.02f, z - TUBE_HALF - 0.98f, 0.7f, GLASS, light);
                octo(m, vc, x, cy, z + TUBE_HALF - 0.02f, z + TUBE_HALF + 0.02f, 0.5f, GLASS, light);
                box(m, vc, x + 0.55f, cy - 0.35f, z - 0.4f, x + 0.95f, cy + 0.35f, z + 0.4f, STEEL, light);
            }
        }

        private static void extMag(MatrixStack m, VertexConsumer vc, GunMeta meta, int light) {
            float[] h = meta.magHand, d = meta.magDir;
            if (Math.abs(d[0]) > 0.5f || Math.abs(d[2]) > 0.9f) return; // барабан, труба гранатомёта
            m.push();
            m.translate((h[0] - 8) / 16f, (h[1] - 8) / 16f, (h[2] - 8) / 16f);
            m.multiply(RotationAxis.POSITIVE_X.rotation((float) Math.atan2(-d[2], -d[1])));
            m.translate(-(h[0] - 8) / 16f, -(h[1] - 8) / 16f, -(h[2] - 8) / 16f);
            box(m, vc, h[0] - 0.68f, h[1] - 2.6f, h[2] - 0.95f, h[0] + 0.68f, h[1] + 0.4f, h[2] + 0.95f, STEEL, light);
            box(m, vc, h[0] - 0.75f, h[1] - 2.9f, h[2] - 1.05f, h[0] + 0.75f, h[1] - 2.6f, h[2] + 1.05f, POLY, light);
            m.pop();
        }
    }

    /** Неон: поверх ствола — светящиеся кромки (маска <gun>_neon_e.png, полная яркость). */
    static final class NeonLayer extends GeoRenderLayer<GunItem> {
        private final GunGeoRenderer r;

        NeonLayer(GunGeoRenderer r) {
            super(r);
            this.r = r;
        }

        @Override
        public void render(MatrixStack m, GunItem gun, BakedGeoModel model, RenderLayer type, VertexConsumerProvider buffers, VertexConsumer buffer,
                           float partialTick, int light, int overlay) {
            ItemStack s = r.getCurrentItemStack();
            if (s == null || buffer == null || !GunMods.skinId(s).equals("neon")) return;
            RenderLayer glow = RenderLayer.getEyes(Novograd.id("textures/item/guns/" + gun.gunId() + "_neon_e.png"));
            getRenderer().reRender(model, m, buffers, gun, glow, buffers.getBuffer(glow), partialTick, 0xF000F0, overlay, 0xFFFFFFFF);
            buffers.getBuffer(type);
        }
    }

    // ---------------------------------------------------------------- кубики
    /** Восьмигранник вдоль z (как octo в gen_gun_geo.py): квадрат + квадрат под 45°. */
    static void octo(MatrixStack m, VertexConsumer vc, float cx, float cy, float z0, float z1, float r, int cell, int light) {
        box(m, vc, cx - r, cy - r, z0, cx + r, cy + r, z1, cell, light);
        m.push();
        m.translate((cx - 8) / 16f, (cy - 8) / 16f, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
        m.translate(-(cx - 8) / 16f, -(cy - 8) / 16f, 0);
        box(m, vc, cx - r, cy - r, z0, cx + r, cy + r, z1, cell, light);
        m.pop();
    }

    /** Кубик в пикселях предмета (центр 8, 8, 8 → начало координат кости). */
    static void box(MatrixStack m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int cell, int light) {
        float a = (x0 - 8) / 16f, b = (y0 - 8) / 16f, c = (z0 - 8) / 16f, d = (x1 - 8) / 16f, e = (y1 - 8) / 16f, f = (z1 - 8) / 16f;
        float u0 = cell * 0.25f + 0.02f, u1 = cell * 0.25f + 0.23f, v0 = 0.1f, v1 = 0.9f;
        MatrixStack.Entry en = m.peek();
        Matrix4f mat = en.getPositionMatrix();
        // north (-z), south (+z), west (-x), east (+x), down, up
        quad(vc, en, mat, new float[][]{{d, b, c}, {a, b, c}, {a, e, c}, {d, e, c}}, 0, 0, -1, u0, v0, u1, v1, light);
        quad(vc, en, mat, new float[][]{{a, b, f}, {d, b, f}, {d, e, f}, {a, e, f}}, 0, 0, 1, u0, v0, u1, v1, light);
        quad(vc, en, mat, new float[][]{{a, b, c}, {a, b, f}, {a, e, f}, {a, e, c}}, -1, 0, 0, u0, v0, u1, v1, light);
        quad(vc, en, mat, new float[][]{{d, b, f}, {d, b, c}, {d, e, c}, {d, e, f}}, 1, 0, 0, u0, v0, u1, v1, light);
        quad(vc, en, mat, new float[][]{{a, b, c}, {d, b, c}, {d, b, f}, {a, b, f}}, 0, -1, 0, u0, v0, u1, v1, light);
        quad(vc, en, mat, new float[][]{{a, e, f}, {d, e, f}, {d, e, c}, {a, e, c}}, 0, 1, 0, u0, v0, u1, v1, light);
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry en, Matrix4f mat, float[][] p, float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1, int light) {
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++)
            vc.vertex(mat, p[i][0], p[i][1], p[i][2]).color(255, 255, 255, 255).texture(uv[i][0], uv[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(en, nx, ny, nz);
    }
}
