package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Жители на GeckoLib: модель с локтями и коленями, покадровые анимации (ходьба, бег, разговор, танец…),
 * поверх — объёмные причёски, бороды, моргание и губы из {@link HairFeature}, предмет в руке.
 */
public class CitizenGeoRenderer extends GeoEntityRenderer<CitizenEntity> {
    private static final Identifier[] SKINS = new Identifier[CitizenEntity.SKINS.length];
    private static final Identifier WIDE = Novograd.id("geo/citizen.geo.json"), SLIM = Novograd.id("geo/citizen_slim.geo.json");
    private static final Identifier ANIM = Novograd.id("animations/citizen.animation.json");

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = Novograd.id("textures/entity/citizen/" + CitizenEntity.SKINS[i] + ".png");
    }

    public CitizenGeoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model());
        shadowRadius = 0.45f;
        HairFeature hair = new HairFeature(null);
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(MatrixStack m, CitizenEntity e, GeoBone bone, RenderLayer type, VertexConsumerProvider buffers, VertexConsumer buffer,
                                      float partialTick, int light, int overlay) {
                boolean head = bone.getName().equals("head"), waist = bone.getName().equals("waist");
                if (!head && !waist) return;
                // из пространства модели GeckoLib (y вверх, пиксели) в пространство ванильной модели
                m.push();
                m.translate(0, 24 / 16f, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
                if (head) hair.head(m, buffers, light, e);
                else hair.skirt(m, buffers, light, e);
                m.pop();
                buffers.getBuffer(type);
            }
        });
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, CitizenEntity e) {
                return bone.getName().equals("right_hand") ? e.getMainHandStack() : null;
            }

            @Override
            protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, CitizenEntity e) {
                return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(MatrixStack m, GeoBone bone, ItemStack stack, CitizenEntity e, VertexConsumerProvider buffers,
                                              float partialTick, int light, int overlay) {
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                m.translate(0, 0.05, -0.05);
                super.renderStackForBone(m, bone, stack, e, buffers, partialTick, light, overlay);
            }
        });
    }

    @Override
    public void render(CitizenEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        if (e.isInvisible()) return; // ушёл к себе в комнату
        super.render(e, yaw, tickDelta, m, buffers, light);
        if (e.asleep()) {
            // «Z z z» поднимаются над спящим
            var tr = getTextRenderer();
            float time = (e.age + tickDelta) * 0.03f;
            for (int i = 0; i < 3; i++) {
                float k = (time + i / 3f) % 1f;
                m.push();
                m.translate(0.15 + k * 0.35, 0.55 + k * 0.9, 0);
                m.multiply(dispatcher.getRotation());
                float sc = 0.02f + k * 0.025f;
                m.scale(sc, -sc, sc);
                int alpha = (int) (255 * Math.sin(k * Math.PI));
                tr.draw("Z", -tr.getWidth("Z") / 2f, 0, (Math.max(alpha, 8) << 24) | 0xDDEEFF, false, m.peek().getPositionMatrix(), buffers,
                        net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                m.pop();
            }
        }
        String say = e.saying();
        if (!say.isEmpty() && dispatcher.getSquaredDistanceToCamera(e) < 24 * 24) {
            // реплика прохожего: короткая строка в «облачке» над головой
            m.push();
            m.translate(0, e.getHeight() + (e.hasCustomName() && e.isCustomNameVisible() ? 0.85f : 0.55f), 0);
            m.multiply(dispatcher.getRotation());
            m.scale(0.022f, -0.022f, 0.022f);
            var tr = getTextRenderer();
            float x = -tr.getWidth(say) / 2f;
            tr.draw(say, x, 0, 0xFFFFFFFF, false, m.peek().getPositionMatrix(), buffers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0x99202830, light);
            m.pop();
        }
        if (e.hasQuest()) {
            m.push();
            float bob = MathHelper.sin((e.age + tickDelta) * 0.12f) * 0.08f;
            m.translate(0, e.getHeight() + 0.75f + bob, 0);
            m.multiply(dispatcher.getRotation());
            m.scale(0.06f, -0.06f, 0.06f);
            var tr = getTextRenderer();
            tr.draw("!", -tr.getWidth("!") / 2f, 0, 0xFFFFD000, true, m.peek().getPositionMatrix(), buffers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            m.pop();
        }
    }

    /** Сбитый машиной (и любой погибший житель) падает анимацией и лежит — без ванильного заваливания набок. */
    @Override
    protected float getDeathMaxRotation(CitizenEntity e) {
        return e.knockPhase() >= CitizenEntity.KO_DEAD ? 0 : super.getDeathMaxRotation(e);
    }

    /** Красная вспышка — только в момент удара, а не всё время, пока тело лежит. */
    @Override
    public int getPackedOverlay(CitizenEntity e, float u, float partialTick) {
        if (e.knockPhase() >= CitizenEntity.KO_DEAD)
            return net.minecraft.client.render.OverlayTexture.packUv(net.minecraft.client.render.OverlayTexture.getU(u), net.minecraft.client.render.OverlayTexture.getV(e.hurtTime > 0));
        return super.getPackedOverlay(e, u, partialTick);
    }

    static class Model extends GeoModel<CitizenEntity> {
        @Override
        public Identifier getModelResource(CitizenEntity e) {
            return CitizenEntity.isSlim(e.getSkin()) ? SLIM : WIDE;
        }

        @Override
        public Identifier getTextureResource(CitizenEntity e) {
            return SKINS[e.getSkin()];
        }

        @Override
        public Identifier getAnimationResource(CitizenEntity e) {
            return ANIM;
        }

        @Override
        public RenderLayer getRenderType(CitizenEntity e, Identifier texture) {
            return RenderLayer.getEntityTranslucent(texture);
        }

        /** Голова поворачивается туда, куда смотрит житель. */
        @Override
        public void setCustomAnimations(CitizenEntity e, long instanceId, AnimationState<CitizenEntity> state) {
            var head = getAnimationProcessor().getBone("neck");
            var data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && (e.asleep() || e.knockPhase() != CitizenEntity.KO_NONE)) { head.setRotX(0); head.setRotY(0); head.setRotZ(0); return; }
            if (head != null && data != null) {
                // ставим, а не прибавляем: иначе поворот копится каждый кадр и голову «колбасит»
                head.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE);
                head.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
                head.setRotZ(0);
            }
        }
    }
}
