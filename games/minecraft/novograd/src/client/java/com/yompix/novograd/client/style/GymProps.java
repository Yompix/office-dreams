package com.yompix.novograd.client.style;

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
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.9.6: снаряды спортзала у игрока, пока идёт подход (жест gym_*): гантели в кистях, штанга в руках на жиме,
 * турник над головой, груша перед собой, беговая дорожка и коврик под ногами. Гантели и штанга привязаны к рукам —
 * их правда держат; остальное стоит в пространстве модели.
 */
public final class GymProps {
    private GymProps() {}

    private static final Identifier SOLID = Novograd.id("textures/entity/car/base.png");
    private static final Map<String, ModelPart> P = new HashMap<>();

    private static ModelPartBuilder b() {
        return ModelPartBuilder.create().uv(0, 0);
    }

    private static void part(String n, ModelPartBuilder pb) {
        ModelData d = new ModelData();
        d.getRoot().addChild("p", pb, ModelTransform.NONE);
        P.put(n, TexturedModelData.of(d, 64, 64).createModel().getChild("p"));
    }

    static {
        // кисть — на 10 пикс. ниже плеча; правая рука в своих координатах: x −3…1, у тонкой −2…1
        part("dumbbell_bar", b().cuboid(-3.8f, 9.6f, -0.5f, 5.6f, 1f, 1f));
        part("dumbbell_w", b().cuboid(-5.2f, 8.6f, -1.5f, 1.6f, 3f, 3f).cuboid(1.6f, 8.6f, -1.5f, 1.6f, 3f, 3f));
        part("barbell_bar", b().cuboid(-1.5f, 9.8f, -0.4f, 16f, 0.8f, 0.8f));
        part("barbell_w", b().cuboid(-5.5f, 7.5f, -2.5f, 2f, 5.5f, 5.5f).cuboid(12.5f, 7.5f, -2.5f, 2f, 5.5f, 5.5f));
        // в координатах модели: шея y = 0, ноги до y = 24, лицо в −z
        part("pullbar", b().cuboid(-9f, -16f, -0.6f, 18f, 1.2f, 1.2f).cuboid(-9.6f, -16f, -1f, 1.2f, 40f, 2f).cuboid(8.4f, -16f, -1f, 1.2f, 40f, 2f));
        part("bag", b().cuboid(-3f, -6f, -13f, 6f, 15f, 6f).cuboid(-0.4f, -12f, -10.4f, 0.8f, 6f, 0.8f));
        part("treadmill", b().cuboid(-6f, 24f, -12f, 12f, 1.5f, 26f).cuboid(-6.5f, 4f, -13.5f, 1f, 21f, 1f).cuboid(5.5f, 4f, -13.5f, 1f, 21f, 1f)
                .cuboid(-6.5f, 3f, -14f, 13f, 2f, 3f));
        part("mat", b().cuboid(-6f, 23.8f, -10f, 12f, 0.3f, 22f));
        part("bench", b().cuboid(-3.5f, -2f, 2.4f, 7f, 3f, 30f));
    }

    private static void draw(MatrixStack m, VertexConsumer vc, int light, String n, int color) {
        P.get(n).render(m, vc, light, OverlayTexture.DEFAULT_UV, color);
    }

    /** Снаряды для жеста emote (пространство модели игрока). */
    public static void render(MatrixStack m, VertexConsumerProvider buffers, int light, String emote, PlayerEntityModel<?> model) {
        if (!emote.startsWith("gym_")) return;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
        int iron = 0xFF2A2A2E, steel = 0xFFC8C8D0;
        switch (emote) {
            case "gym_curl" -> {
                for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
                    m.push();
                    arm.rotate(m);
                    if (arm == model.leftArm) m.translate(2 / 16f, 0, 0);
                    draw(m, vc, light, "dumbbell_bar", steel);
                    draw(m, vc, light, "dumbbell_w", iron);
                    m.pop();
                }
            }
            case "gym_bench" -> {
                m.push();
                model.rightArm.rotate(m);
                draw(m, vc, light, "barbell_bar", steel);
                draw(m, vc, light, "barbell_w", 0xFFB02030);
                m.pop();
                draw(m, vc, light, "bench", 0xFF303034);
            }
            case "gym_pullup" -> draw(m, vc, light, "pullbar", steel);
            case "gym_bag" -> draw(m, vc, light, "bag", 0xFF7A1F2B);
            case "gym_run" -> draw(m, vc, light, "treadmill", 0xFF26262A);
            case "gym_yoga", "gym_squat" -> draw(m, vc, light, "mat", 0xFF3FB8B0);
            default -> {}
        }
    }
}
