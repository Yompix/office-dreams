package com.yompix.novograd.client.cops;

import com.yompix.novograd.Novograd;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Маска на лице игрока (gta/Disguise): балаклава, хоккейная, клоун, свинья, череп, платок — объёмные кубики поверх головы. */
public class MaskFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier SOLID = Novograd.id("textures/entity/car/base.png");

    private record Part(ModelPart part, int color) {}

    private static final Map<String, List<Part>> MASKS = new HashMap<>();

    public MaskFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
        if (!MASKS.isEmpty()) return;
        // координаты — пиксели головы: лицо смотрит в −z, макушка y = −8, глаза на y ≈ −4..−3
        int black = 0xFF17171A, dark = 0xFF0A0A0A;
        mask("balaclava",
                p(black, b().cuboid(-4.35f, -8.45f, -4.4f, 8.7f, 3.6f, 0.45f).cuboid(-4.35f, -3.15f, -4.4f, 8.7f, 3.55f, 0.45f)
                        .cuboid(-4.35f, -4.85f, -4.4f, 1.0f, 1.7f, 0.45f).cuboid(3.35f, -4.85f, -4.4f, 1.0f, 1.7f, 0.45f).cuboid(-0.55f, -4.85f, -4.4f, 1.1f, 1.7f, 0.45f)
                        .cuboid(-4.35f, -8.45f, -3.95f, 8.7f, 8.85f, 8.35f)));
        int white = 0xFFEDEDE6;
        mask("hockey",
                p(white, plate(-3.9f, 3.9f)),
                p(0xFF2A2A2A, b().cuboid(-4.4f, -6.2f, -4.0f, 8.8f, 0.6f, 8.45f)),
                p(0xFFC62828, b().cuboid(-2.8f, -7.6f, -4.95f, 0.5f, 1.6f, 0.1f).cuboid(-2.3f, -7.2f, -4.95f, 0.5f, 0.5f, 0.1f).cuboid(2.3f, -7.6f, -4.95f, 0.5f, 1.6f, 0.1f).cuboid(1.8f, -7.2f, -4.95f, 0.5f, 0.5f, 0.1f)),
                p(dark, holes()));
        mask("clown",
                p(0xFFF7F4EE, plate(-3.9f, 3.9f)),
                p(0xFFE53935, b().cuboid(-0.8f, -3.5f, -5.5f, 1.6f, 1.5f, 1.1f).cuboid(-2.4f, -1.6f, -4.96f, 4.8f, 0.8f, 0.1f).cuboid(-2.8f, -2.1f, -4.96f, 0.6f, 0.6f, 0.1f).cuboid(2.2f, -2.1f, -4.96f, 0.6f, 0.6f, 0.1f)),
                p(0xFF1E88E5, b().cuboid(-2.9f, -5.6f, -4.96f, 1.6f, 0.6f, 0.1f).cuboid(1.3f, -5.6f, -4.96f, 1.6f, 0.6f, 0.1f)),
                p(0xFFFF7043, b().cuboid(-5.4f, -8.4f, -2.4f, 1.6f, 3.6f, 5.2f).cuboid(3.8f, -8.4f, -2.4f, 1.6f, 3.6f, 5.2f)));
        int pink = 0xFFF2A6B4;
        mask("pig",
                p(pink, plate(-3.9f, 3.9f)),
                p(0xFFE07A90, b().cuboid(-1.5f, -3.5f, -5.7f, 3.0f, 1.9f, 1.3f).cuboid(-4.0f, -9.6f, -2.6f, 1.8f, 1.8f, 0.7f).cuboid(2.2f, -9.6f, -2.6f, 1.8f, 1.8f, 0.7f)),
                p(0xFF6A2A36, b().cuboid(-0.9f, -3.0f, -5.75f, 0.6f, 0.7f, 0.1f).cuboid(0.3f, -3.0f, -5.75f, 0.6f, 0.7f, 0.1f)));
        int bone = 0xFFE8E4D8;
        mask("skull",
                p(bone, b().cuboid(-3.9f, -8.3f, -4.5f, 7.8f, 8.6f, 0.5f).cuboid(-4.4f, -8.45f, -4.0f, 8.8f, 3.2f, 8.45f)),
                p(dark, b().cuboid(-3.2f, -5.2f, -4.56f, 2.6f, 2.2f, 0.1f).cuboid(0.6f, -5.2f, -4.56f, 2.6f, 2.2f, 0.1f).cuboid(-0.5f, -2.9f, -4.56f, 1.0f, 1.0f, 0.1f)
                        .cuboid(-2.6f, -1.2f, -4.56f, 5.2f, 0.35f, 0.1f).cuboid(-1.8f, -1.6f, -4.56f, 0.25f, 1.2f, 0.1f).cuboid(-0.6f, -1.6f, -4.56f, 0.25f, 1.2f, 0.1f)
                        .cuboid(0.6f, -1.6f, -4.56f, 0.25f, 1.2f, 0.1f).cuboid(1.8f, -1.6f, -4.56f, 0.25f, 1.2f, 0.1f)));
        mask("scarf",
                p(0xFFB71C1C, b().cuboid(-4.45f, -3.5f, -4.5f, 8.9f, 3.9f, 8.95f).cuboid(-1.6f, 0.4f, -4.6f, 3.2f, 1.3f, 0.5f)),
                p(0xFFF5F5F5, b().cuboid(-3.0f, -2.2f, -4.56f, 0.6f, 0.6f, 0.08f).cuboid(-0.3f, -1.4f, -4.56f, 0.6f, 0.6f, 0.08f).cuboid(2.4f, -2.2f, -4.56f, 0.6f, 0.6f, 0.08f)));
    }

    /** Пластина на лицо с прорезями для глаз. */
    private static ModelPartBuilder plate(float x0, float x1) {
        float w = x1 - x0;
        return b().cuboid(x0, -8.3f, -4.5f, w, 3.5f, 0.5f).cuboid(x0, -3.1f, -4.5f, w, 3.4f, 0.5f)
                .cuboid(x0, -4.8f, -4.5f, 0.9f, 1.7f, 0.5f).cuboid(x1 - 0.9f, -4.8f, -4.5f, 0.9f, 1.7f, 0.5f).cuboid(-0.6f, -4.8f, -4.5f, 1.2f, 1.7f, 0.5f);
    }

    private static ModelPartBuilder holes() {
        ModelPartBuilder h = b();
        float[][] at = {{-2.6f, -2.4f}, {-1.4f, -1.6f}, {0.9f, -1.6f}, {2.1f, -2.4f}, {-0.3f, -0.8f}, {-2.9f, -6.9f}, {2.4f, -6.9f}, {-0.3f, -7.4f}};
        for (float[] a : at) h.cuboid(a[0], a[1], -5.02f, 0.5f, 0.5f, 0.08f);
        return h;
    }

    private static ModelPartBuilder b() {
        return ModelPartBuilder.create().uv(0, 0);
    }

    private static Part p(int color, ModelPartBuilder builder) {
        ModelData data = new ModelData();
        data.getRoot().addChild("p", builder, ModelTransform.NONE);
        return new Part(TexturedModelData.of(data, 64, 64).createModel().getChild("p"), color);
    }

    private static void mask(String id, Part... parts) {
        MASKS.put(id, new ArrayList<>(List.of(parts)));
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity p, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        String id = CopsClient.MASKS.get(p.getUuid());
        if (id == null || id.isEmpty() || p.isInvisible()) return;
        List<Part> parts = MASKS.get(id);
        if (parts == null) return;
        m.push();
        getContextModel().head.rotate(m);
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
        for (Part q : parts) q.part().render(m, vc, light, OverlayTexture.DEFAULT_UV, q.color());
        m.pop();
    }
}
