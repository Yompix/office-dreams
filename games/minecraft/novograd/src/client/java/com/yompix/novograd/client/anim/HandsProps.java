package com.yompix.novograd.client.anim;

import com.yompix.novograd.Novograd;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.9.8: предметы в руках — карта, купюры, монетка, банка, чипсы, хот-дог, мороженое, кофе, пакет, пистолет колонки,
 * ключ, сигарета, зажигалка, бутылка. Маленькие объёмные модели (пиксели — 1/16 блока), цвет — вершинами (одна
 * текстура на всё: без лишних переключений). Начало координат — центр хвата; ось Y — вверх, Z — вперёд (от игрока).
 */
public final class HandsProps {
    private HandsProps() {}

    private static final Identifier SOLID = Novograd.id("textures/entity/car/base.png");

    /** Часть: модель + цвет ARGB. */
    private record Piece(ModelPart part, int color) {}

    private static final Map<String, Piece[]> P = new HashMap<>();

    private static ModelPart box(float x, float y, float z, float w, float h, float d) {
        ModelData md = new ModelData();
        md.getRoot().addChild("p", ModelPartBuilder.create().uv(0, 0).cuboid(x, y, z, w, h, d), ModelTransform.NONE);
        return TexturedModelData.of(md, 64, 64).createModel().getChild("p");
    }

    private static void def(String id, Object... parts) {
        Piece[] ps = new Piece[parts.length / 2];
        for (int i = 0; i < ps.length; i++) ps[i] = new Piece((ModelPart) parts[i * 2], (Integer) parts[i * 2 + 1]);
        P.put(id, ps);
    }

    static {
        // координаты в пикселях; модель рисуется со scale 1/16 и переворотом осей (см. draw): y вверх, z вперёд
        def("card", box(-1.6f, -0.05f, -1.0f, 3.2f, 0.12f, 2.0f), 0xFFFFC107, box(-1.2f, 0.07f, -0.2f, 0.7f, 0.03f, 0.5f), 0xFFB08800,
                box(-1.6f, 0.07f, 0.55f, 3.2f, 0.03f, 0.25f), 0xFF5D4000);
        def("cash", box(-1.8f, -0.25f, -0.8f, 3.6f, 0.5f, 1.6f), 0xFF5FB86B, box(-1.85f, -0.05f, -0.2f, 3.7f, 0.12f, 0.4f), 0xFFE8E2C8,
                box(-0.4f, 0.26f, -0.4f, 0.8f, 0.02f, 0.8f), 0xFF2E7D32);
        def("coin", box(-0.4f, -0.08f, -0.4f, 0.8f, 0.16f, 0.8f), 0xFFE0C060);
        def("can", box(-0.8f, -1.3f, -0.8f, 1.6f, 2.6f, 1.6f), 0xFFC62828, box(-0.82f, -0.2f, -0.82f, 1.64f, 0.5f, 1.64f), 0xFFF5F5F5,
                box(-0.7f, 1.3f, -0.7f, 1.4f, 0.15f, 1.4f), 0xFFBDBDBD);
        def("chips", box(-1.2f, -1.6f, -0.45f, 2.4f, 3.2f, 0.9f), 0xFFFFB300, box(-1.25f, 1.4f, -0.3f, 2.5f, 0.35f, 0.6f), 0xFFD32F2F,
                box(-0.6f, -0.4f, -0.5f, 1.2f, 1.0f, 0.05f), 0xFFFFF3B0);
        def("hotdog", box(-0.6f, -0.35f, -2.2f, 1.2f, 0.7f, 4.4f), 0xFFE0A060, box(-0.35f, 0.2f, -2.6f, 0.7f, 0.5f, 5.2f), 0xFFB5452B,
                box(-0.2f, 0.68f, -1.8f, 0.4f, 0.06f, 3.6f), 0xFFF2C21B);
        def("icecream", box(-0.5f, -1.6f, -0.5f, 1.0f, 2.2f, 1.0f), 0xFFD7A15E, box(-0.8f, 0.5f, -0.8f, 1.6f, 1.2f, 1.6f), 0xFFF8F4E8,
                box(-0.4f, 1.6f, -0.4f, 0.8f, 0.4f, 0.8f), 0xFFF06292);
        def("coffee", box(-0.75f, -1.2f, -0.75f, 1.5f, 2.3f, 1.5f), 0xFFF5F5F5, box(-0.8f, -0.3f, -0.8f, 1.6f, 0.8f, 1.6f), 0xFF8D6E63,
                box(-0.8f, 1.1f, -0.8f, 1.6f, 0.3f, 1.6f), 0xFF3E2723);
        def("bag", box(-2.2f, -5.2f, -1.2f, 4.4f, 4.6f, 2.4f), 0xFFE8D9B0, box(-1.6f, -0.6f, -0.1f, 0.3f, 0.9f, 0.2f), 0xFFBCA77A,
                box(1.3f, -0.6f, -0.1f, 0.3f, 0.9f, 0.2f), 0xFFBCA77A, box(-1.6f, 0.2f, -0.1f, 3.2f, 0.25f, 0.2f), 0xFFBCA77A);
        def("goods", box(-2.4f, -1.2f, -1.6f, 4.8f, 2.4f, 3.2f), 0xFFE8D9B0, box(-1.4f, 1.2f, -0.6f, 1.2f, 1.8f, 1.2f), 0xFF1E88E5,
                box(0.4f, 1.2f, -1.0f, 1.4f, 1.0f, 1.4f), 0xFFFFB300);
        def("nozzle", box(-0.5f, -1.8f, -0.6f, 1.0f, 2.6f, 1.2f), 0xFF212121, box(-0.35f, 0.4f, -0.5f, 0.7f, 0.7f, 3.4f), 0xFF424242,
                box(-0.2f, 0.55f, 2.9f, 0.4f, 0.4f, 1.6f), 0xFFB0BEC5, box(-0.45f, -1.2f, 0.6f, 0.9f, 0.25f, 0.9f), 0xFFFFC107,
                box(-0.3f, -2.6f, -0.3f, 0.6f, 0.9f, 0.6f), 0xFF111111);
        def("wrench", box(-0.25f, -2.6f, -0.25f, 0.5f, 4.4f, 0.5f), 0xFF9E9E9E, box(-0.6f, 1.6f, -0.6f, 1.2f, 0.9f, 1.2f), 0xFF757575,
                box(-0.3f, -2.6f, -0.3f, 0.6f, 1.5f, 0.6f), 0xFFC62828);
        def("cigarette", box(-0.12f, -0.12f, -0.2f, 0.24f, 0.24f, 1.5f), 0xFFF5F5F5, box(-0.13f, -0.13f, -0.35f, 0.26f, 0.26f, 0.45f), 0xFFD69A5A,
                box(-0.11f, -0.11f, 1.3f, 0.22f, 0.22f, 0.12f), 0xFFFF7043);
        def("lighter", box(-0.4f, -0.9f, -0.25f, 0.8f, 1.6f, 0.5f), 0xFF1565C0, box(-0.35f, 0.7f, -0.2f, 0.7f, 0.3f, 0.4f), 0xFFB0BEC5);
        def("beer", box(-0.6f, -1.8f, -0.6f, 1.2f, 2.6f, 1.2f), 0xFF5D4037, box(-0.3f, 0.8f, -0.3f, 0.6f, 1.4f, 0.6f), 0xFF5D4037,
                box(-0.62f, -1.0f, -0.62f, 1.24f, 1.0f, 1.24f), 0xFFF4E4BC, box(-0.32f, 2.2f, -0.32f, 0.64f, 0.2f, 0.64f), 0xFFFFC107);
        def("soda", box(-0.6f, -1.8f, -0.6f, 1.2f, 2.6f, 1.2f), 0xFF2E7D32, box(-0.3f, 0.8f, -0.3f, 0.6f, 1.4f, 0.6f), 0xFF388E3C,
                box(-0.62f, -1.0f, -0.62f, 1.24f, 1.0f, 1.24f), 0xFFE8F5E9, box(-0.32f, 2.2f, -0.32f, 0.64f, 0.2f, 0.64f), 0xFFD32F2F);
        def("phone", box(-0.75f, -1.4f, -0.12f, 1.5f, 2.8f, 0.24f), 0xFF1C1C1E, box(-0.65f, -1.25f, -0.14f, 1.3f, 2.5f, 0.03f), 0xFF2D5D8A);
    }

    /** Есть ли такая модель. */
    public static boolean has(String id) {
        return P.containsKey(id);
    }

    /**
     * Нарисовать предмет. Матрица — в «пространстве тела» (x вправо, y вверх, z вперёд, 1 = блок), центр — хват;
     * tilt — наклон вперёд (к лицу при глотке), градусы; roll — вбок.
     */
    public static void draw(MatrixStack m, VertexConsumerProvider buffers, int light, String id, float tiltDeg, float rollDeg, float scale) {
        Piece[] ps = P.get(id);
        if (ps == null) return;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
        m.push();
        if (tiltDeg != 0) m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(tiltDeg));
        if (rollDeg != 0) m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));
        m.scale(scale, scale, scale);
        for (Piece p : ps) p.part().render(m, vc, light, OverlayTexture.DEFAULT_UV, p.color());
        m.pop();
    }
}
