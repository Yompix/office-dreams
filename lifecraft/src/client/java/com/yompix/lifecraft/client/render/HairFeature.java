package com.yompix.lifecraft.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.entity.CitizenEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Объёмные причёски, бороды, усы, кепки, шапки, каски и очки поверх головы жителя.
 * Какая причёска у какого скина — в citizen_looks.json (его пишет tools/gen_assets.py).
 */
public class HairFeature extends FeatureRenderer<CitizenEntity, PlayerEntityModel<CitizenEntity>> {
    private static final Identifier HAIR = LifeCraft.id("textures/entity/citizen/hair.png");
    private static final Identifier SOLID = LifeCraft.id("textures/entity/car/base.png");
    private static JsonArray looks;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public HairFeature(FeatureRendererContext<CitizenEntity, PlayerEntityModel<CitizenEntity>> ctx) {
        super(ctx);
        // координаты в пикселях головы: лицо смотрит в −z, макушка — y = −8
        part("top", b().cuboid(-4.5f, -9.1f, -4.5f, 9, 1.6f, 9));
        part("fringe", b().cuboid(-4.5f, -8.7f, -4.75f, 9, 1.3f, 1));
        part("back_short", b().cuboid(-4.5f, -8f, 3.55f, 9, 4.5f, 1.2f).cuboid(-4.65f, -8f, -2f, 0.9f, 2.6f, 6).cuboid(3.75f, -8f, -2f, 0.9f, 2.6f, 6));
        part("buzz", b().cuboid(-4.3f, -8.45f, -4.3f, 8.6f, 0.75f, 8.6f));
        part("quiff", b().cuboid(-3.5f, -10f, -4.9f, 7, 1.8f, 3.6f).cuboid(-3f, -9.6f, -1.5f, 6, 1.2f, 3));
        part("long", b().cuboid(-4.75f, -8f, -4f, 1, 9.5f, 8.2f).cuboid(3.75f, -8f, -4f, 1, 9.5f, 8.2f).cuboid(-4.6f, -8f, 3.55f, 9.2f, 11, 1.3f));
        part("bob", b().cuboid(-4.75f, -8f, -3.6f, 1, 6.6f, 7.8f).cuboid(3.75f, -8f, -3.6f, 1, 6.6f, 7.8f).cuboid(-4.6f, -8f, 3.55f, 9.2f, 6.8f, 1.3f));
        part("ponytail", b().cuboid(-4.5f, -8f, 3.55f, 9, 3.5f, 1.2f).cuboid(-1.1f, -6.6f, 4.5f, 2.2f, 2.2f, 1.4f).cuboid(-1f, -5.4f, 5.5f, 2, 7.5f, 2).cuboid(-0.7f, 2f, 5.8f, 1.4f, 2, 1.4f));
        ModelPartBuilder curly = b();
        for (int x = -4; x <= 2; x += 2)
            for (int z = -4; z <= 2; z += 2) curly.cuboid(x - 0.3f, -9.7f + ((x + z) & 2) * 0.25f, z - 0.3f, 2.6f, 2.2f, 2.6f);
        for (int x = -4; x <= 2; x += 2) curly.cuboid(x - 0.3f, -8f, 3.4f, 2.6f, 4, 1.6f);
        curly.cuboid(-4.9f, -8.2f, -3f, 1.3f, 3, 6).cuboid(3.6f, -8.2f, -3f, 1.3f, 3, 6);
        part("curly", curly);
        part("bald_ring", b().cuboid(-4.4f, -5.2f, -1f, 0.7f, 2, 5).cuboid(3.7f, -5.2f, -1f, 0.7f, 2, 5).cuboid(-4.4f, -5.2f, 3.6f, 8.8f, 2, 0.8f));
        part("beard", b().cuboid(-4.3f, -3.3f, -4.75f, 8.6f, 3.5f, 1.2f).cuboid(-4.35f, -3.6f, -4f, 0.8f, 3.4f, 4.5f).cuboid(3.55f, -3.6f, -4f, 0.8f, 3.4f, 4.5f)
                .cuboid(-3f, -0.3f, -5f, 6, 1.8f, 1.6f));
        part("mustache", b().cuboid(-2.7f, -3.5f, -4.85f, 5.4f, 1, 0.9f).cuboid(-3.2f, -3f, -4.8f, 1, 1.2f, 0.8f).cuboid(2.2f, -3f, -4.8f, 1, 1.2f, 0.8f));
        part("cap", b().cuboid(-4.6f, -9.3f, -4.6f, 9.2f, 2.6f, 9.2f).cuboid(-4.6f, -7.2f, -7.6f, 9.2f, 0.6f, 3.2f).cuboid(-0.6f, -9.8f, -0.6f, 1.2f, 0.5f, 1.2f));
        part("helmet", b().cuboid(-4.9f, -9.8f, -4.9f, 9.8f, 3.2f, 9.8f).cuboid(-5.5f, -7f, -5.5f, 11, 0.7f, 11).cuboid(-0.8f, -10.4f, -4.9f, 1.6f, 0.8f, 9.8f));
        part("beanie", b().cuboid(-4.7f, -9.7f, -4.7f, 9.4f, 3.4f, 9.4f).cuboid(-4.95f, -6.7f, -4.95f, 9.9f, 1.3f, 9.9f).cuboid(-1.1f, -11f, -1.1f, 2.2f, 1.5f, 2.2f));
        part("glasses_frame", b().cuboid(-4.4f, -4.7f, -4.65f, 8.8f, 0.6f, 0.4f).cuboid(-4.55f, -4.7f, -4.4f, 0.4f, 0.6f, 4.2f).cuboid(4.15f, -4.7f, -4.4f, 0.4f, 0.6f, 4.2f)
                .cuboid(-3.9f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(-0.85f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(0.45f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(3.5f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f));
        part("glasses_lens", b().cuboid(-3.6f, -4.2f, -4.6f, 2.8f, 1.6f, 0.3f).cuboid(0.8f, -4.2f, -4.6f, 2.8f, 1.6f, 0.3f));
    }

    private static ModelPartBuilder b() {
        return ModelPartBuilder.create().uv(0, 0);
    }

    private void part(String name, ModelPartBuilder builder) {
        ModelData data = new ModelData();
        data.getRoot().addChild("p", builder, ModelTransform.NONE);
        parts.put(name, TexturedModelData.of(data, 64, 64).createModel().getChild("p"));
    }

    static JsonArray looks() {
        if (looks == null) {
            try (var in = HairFeature.class.getResourceAsStream("/assets/lifecraft/citizen_looks.json")) {
                looks = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            } catch (Exception e) {
                looks = new JsonArray();
            }
        }
        return looks;
    }

    private static int color(JsonObject o, String key, float shade) {
        int c = Integer.parseInt(o.get(key).getAsString(), 16);
        int r = Math.min(255, (int) (((c >> 16) & 255) * shade + 18)), g = Math.min(255, (int) (((c >> 8) & 255) * shade + 18)), b = Math.min(255, (int) ((c & 255) * shade + 18));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider buffers, int light, CitizenEntity e, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        JsonArray all = looks();
        int skin = e.getSkin();
        if (skin >= all.size() || e.isInvisible()) return;
        JsonObject look = all.get(skin).getAsJsonObject();
        m.push();
        getContextModel().head.rotate(m);
        VertexConsumer hair = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(HAIR));
        int ov = OverlayTexture.DEFAULT_UV;
        int hc = color(look, "hair", 1.0f);
        boolean hat = look.has("cap") || look.has("beanie") || look.has("helmet");
        String style = look.get("style").getAsString();
        switch (style) {
            case "buzz" -> render("buzz", m, hair, light, hc);
            case "bald" -> render("bald_ring", m, hair, light, hc);
            case "curly" -> {
                if (!hat) render("curly", m, hair, light, hc);
                else render("back_short", m, hair, light, hc);
            }
            default -> {
                if (!hat) {
                    render("top", m, hair, light, hc);
                    render("fringe", m, hair, light, hc);
                }
                switch (style) {
                    case "long" -> render("long", m, hair, light, hc);
                    case "bob" -> render("bob", m, hair, light, hc);
                    case "ponytail" -> render("ponytail", m, hair, light, hc);
                    case "slick" -> {
                        render("back_short", m, hair, light, hc);
                        if (!hat) render("quiff", m, hair, light, hc);
                    }
                    default -> render("back_short", m, hair, light, hc);
                }
            }
        }
        if (look.has("beard")) render("beard", m, hair, light, color(look, "beard", 0.95f));
        if (look.has("mustache")) render("mustache", m, hair, light, color(look, "mustache", 0.95f));
        VertexConsumer solid = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
        if (look.has("helmet")) render("helmet", m, solid, light, color(look, "helmet", 1f));
        else if (look.has("cap")) render("cap", m, solid, light, color(look, "cap", 1f));
        else if (look.has("beanie")) render("beanie", m, solid, light, color(look, "beanie", 1f));
        if (look.has("glasses")) {
            String g = look.get("glasses").getAsString();
            int frame = g.equals("gold") ? 0xFFD4AF37 : 0xFF1A1A1A;
            render("glasses_frame", m, solid, light, frame);
            VertexConsumer lens = buffers.getBuffer(RenderLayer.getEntityTranslucent(SOLID));
            parts.get("glasses_lens").render(m, lens, light, ov, g.equals("shades") ? 0xF0101010 : 0x70A8D8FF);
        }
        m.pop();
    }

    private void render(String name, MatrixStack m, VertexConsumer vc, int light, int color) {
        parts.get(name).render(m, vc, light, OverlayTexture.DEFAULT_UV, color);
    }
}
