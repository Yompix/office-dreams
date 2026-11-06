package com.yompix.novograd.client.render.vehicle;

import com.yompix.novograd.Novograd;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Модель из кубиков без отдельного слоя: координаты в пикселях (16 = блок), y вверх от низа, перед смотрит в +z.
 * Детали красятся цветом при отрисовке (текстура — светлая основа машин).
 */
public final class Cubes {
    public static final Identifier BASE = Novograd.id("textures/entity/car/base.png");
    public static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private final ModelData data = new ModelData();

    public Cubes box(String name, float x1, float y1, float z1, float x2, float y2, float z2) {
        data.getRoot().addChild(name, b(x1, y1, z1, x2, y2, z2), ModelTransform.NONE);
        return this;
    }

    /** Несколько кубиков одной детали: {x1,y1,z1,x2,y2,z2} подряд. */
    public Cubes boxes(String name, float... c) {
        ModelPartBuilder pb = ModelPartBuilder.create().uv(0, 0);
        for (int i = 0; i + 5 < c.length; i += 6) pb.cuboid(c[i], c[i + 1], c[i + 2], c[i + 3] - c[i], c[i + 4] - c[i + 1], c[i + 5] - c[i + 2]);
        data.getRoot().addChild(name, pb, ModelTransform.NONE);
        return this;
    }

    /** Вращающаяся деталь (колесо, винт): кубики относительно точки поворота px,py,pz. */
    public Cubes pivot(String name, float px, float py, float pz, float... c) {
        ModelPartBuilder pb = ModelPartBuilder.create().uv(0, 0);
        for (int i = 0; i + 5 < c.length; i += 6) pb.cuboid(c[i], c[i + 1], c[i + 2], c[i + 3] - c[i], c[i + 4] - c[i + 1], c[i + 5] - c[i + 2]);
        data.getRoot().addChild(name, pb, ModelTransform.pivot(px, py, pz));
        return this;
    }

    public ModelPart build() {
        return TexturedModelData.of(data, 256, 256).createModel();
    }

    public static ModelPartBuilder b(float x1, float y1, float z1, float x2, float y2, float z2) {
        return ModelPartBuilder.create().uv(0, 0).cuboid(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
    }

    /** Рисовалка на кадр. */
    public static final class Paint {
        private final ModelPart root;
        private final MatrixStack m;
        private final VertexConsumer solid, glass;
        private final int light;

        public Paint(ModelPart root, MatrixStack m, VertexConsumerProvider buffers, int light) {
            this.root = root;
            this.m = m;
            this.solid = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(BASE));
            this.glass = buffers.getBuffer(RenderLayer.getEntityTranslucent(BASE));
            this.light = light;
        }

        public ModelPart get(String name) {
            return root.getChild(name);
        }

        public void p(String name, int argb) {
            root.getChild(name).render(m, solid, light, OverlayTexture.DEFAULT_UV, argb);
        }

        /** Светится сама (фары, огни). */
        public void glow(String name, int argb) {
            root.getChild(name).render(m, solid, FULL, OverlayTexture.DEFAULT_UV, argb);
        }

        public void glass(String name, int argb) {
            root.getChild(name).render(m, glass, light, OverlayTexture.DEFAULT_UV, argb);
        }
    }

    /** Цвет темнее/светлее: k < 1 — темнее. */
    public static int shade(int argb, float k) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
        r = Math.min(255, (int) (r * k));
        g = Math.min(255, (int) (g * k));
        bl = Math.min(255, (int) (bl * k));
        return a << 24 | r << 16 | g << 8 | bl;
    }
}
