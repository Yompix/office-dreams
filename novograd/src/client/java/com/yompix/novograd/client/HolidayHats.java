package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Праздничные костюмы прохожих (рисуются поверх головы из HairFeature): на Новый год — шапка Деда Мороза,
 * на Хэллоуин — голова-тыква или шляпа ведьмы. Кому что — по номеру жителя, так что костюм не «мигает».
 */
public final class HolidayHats {
    private HolidayHats() {}

    private static final Identifier SOLID = Novograd.id("textures/entity/car/base.png");
    private static final Map<String, ModelPart> PARTS = new HashMap<>();

    private static ModelPart part(String name) {
        ModelPart p = PARTS.get(name);
        if (p != null) return p;
        ModelPartBuilder b = ModelPartBuilder.create().uv(0, 0);
        // координаты головы: лицо смотрит в −z, макушка — y = −8
        switch (name) {
            case "santa_band" -> b.cuboid(-4.8f, -9.3f, -4.8f, 9.6f, 2.2f, 9.6f);
            case "santa_cap" -> b.cuboid(-4.3f, -11.6f, -4.3f, 8.6f, 2.4f, 8.6f).cuboid(-3.3f, -13.4f, -2.8f, 6.6f, 1.9f, 6.6f)
                    .cuboid(-2.2f, -14.8f, -0.8f, 4.4f, 1.5f, 4.4f).cuboid(-1.3f, -15.4f, 1.6f, 2.6f, 1.6f, 3.2f);
            case "santa_pom" -> b.cuboid(-1.4f, -15.6f, 4.2f, 2.8f, 2.8f, 2.8f);
            case "pumpkin" -> b.cuboid(-5.2f, -10.2f, -5.2f, 10.4f, 10.4f, 10.4f);
            case "pumpkin_stem" -> b.cuboid(-0.8f, -12f, -0.8f, 1.6f, 2f, 1.6f);
            case "pumpkin_face" -> b.cuboid(-3.6f, -7.6f, -5.35f, 2.2f, 1.8f, 0.2f).cuboid(1.4f, -7.6f, -5.35f, 2.2f, 1.8f, 0.2f)
                    .cuboid(-3.2f, -4.2f, -5.35f, 6.4f, 1.2f, 0.2f).cuboid(-1.6f, -5f, -5.35f, 1.2f, 0.8f, 0.2f).cuboid(0.6f, -3f, -5.35f, 1.2f, 0.8f, 0.2f);
            case "witch_brim" -> b.cuboid(-6.8f, -8.7f, -6.8f, 13.6f, 0.7f, 13.6f);
            case "witch_cone" -> b.cuboid(-3.8f, -12.2f, -3.8f, 7.6f, 3.6f, 7.6f).cuboid(-2.6f, -14.8f, -2.2f, 5.2f, 2.6f, 5.2f)
                    .cuboid(-1.4f, -17f, -0.6f, 2.8f, 2.2f, 2.8f).cuboid(-0.6f, -18.4f, 1.2f, 1.2f, 1.6f, 1.6f);
            case "witch_band" -> b.cuboid(-3.95f, -9.6f, -3.95f, 7.9f, 1.1f, 7.9f);
            default -> {}
        }
        ModelData data = new ModelData();
        data.getRoot().addChild("p", b, ModelTransform.NONE);
        p = TexturedModelData.of(data, 64, 64).createModel().getChild("p");
        PARTS.put(name, p);
        return p;
    }

    /** Голова уже в пространстве головы ванильной модели (как у HairFeature.head). */
    public static void render(MatrixStack m, VertexConsumerProvider buffers, int light, int id) {
        String h = OutdoorClient.holiday;
        if (!OutdoorClient.costumes()) return;
        int roll = Math.floorMod(id * 7919 + 13, 100);
        if (h.equals("NEW_YEAR")) {
            if (roll >= 45) return;
            draw("santa_cap", m, buffers, light, 0xFFD01818);
            draw("santa_band", m, buffers, light, 0xFFF4F4F4);
            draw("santa_pom", m, buffers, light, 0xFFFFFFFF);
        } else if (h.equals("HALLOWEEN")) {
            if (roll < 22) {
                draw("pumpkin", m, buffers, light, 0xFFE0761C);
                draw("pumpkin_stem", m, buffers, light, 0xFF4A6A20);
                draw("pumpkin_face", m, buffers, LightmapTextureManager.MAX_LIGHT_COORDINATE, 0xFFFFD040); // светится изнутри
            } else if (roll < 42) {
                draw("witch_brim", m, buffers, light, 0xFF1C1424);
                draw("witch_cone", m, buffers, light, 0xFF241A30);
                draw("witch_band", m, buffers, light, 0xFF8A3CC8);
            }
        }
    }

    private static void draw(String name, MatrixStack m, VertexConsumerProvider buffers, int light, int color) {
        part(name).render(m, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID)), light, OverlayTexture.DEFAULT_UV, color);
    }
}
