package com.yompix.novograd.client.gun;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Set;

/**
 * Ствол в 3D (GeckoLib): своя модель на каждое оружие, кости двигает GunAnim —
 * затвор при выстреле, магазин при перезарядке, помпа, затвор снайперки, спуск.
 * Анимируем только оружие в руках у себя (от первого и от третьего лица); у NPC и других — в покое.
 */
public class GunGeoRenderer extends GeoItemRenderer<GunItem> {
    private static final Set<String> MOVING = Set.of("slide", "mag", "trigger", "hammer", "bolt", "handle", "pump", "shell");
    private boolean animate;

    public GunGeoRenderer(GunItem item) {
        super(new Model(item.gunId()));
    }

    /** Фабрика для GunItem.renderProvider. */
    public static Object provider(GunItem item) {
        return new GeoRenderProvider() {
            private GunGeoRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new GunGeoRenderer(item);
                return renderer;
            }
        };
    }

    @Override
    public void preRender(MatrixStack m, GunItem animatable, BakedGeoModel model, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        this.itemRenderTranslations = new Matrix4f(m.peek().getPositionMatrix());
        scaleModelForRender(this.scaleWidth, this.scaleHeight, m, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        // ровно в центр предмета (у GeckoLib 0.51 — сдвигает модель на 0.16 пикселя вверх, прицел бы «гулял»)
        if (!isReRender) m.translate(0.5f, 0.5f, 0.5f);
        var p = MinecraftClient.getInstance().player;
        animate = p != null && getCurrentItemStack() != null && getCurrentItemStack() == p.getMainHandStack()
                && GunAnim.POSE.gun != null && GunAnim.POSE.gun.equals(animatable.gunId());
    }

    @Override
    public void renderRecursively(MatrixStack m, GunItem animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String n = bone.getName();
        if (MOVING.contains(n)) {
            float[] t = animate ? GunAnim.POSE.bone(n) : null;
            if (t != null) {
                // пиксели предмета → GeckoLib (у него x зеркальный), градусы → радианы
                bone.setPosX(-t[0]);
                bone.setPosY(t[1]);
                bone.setPosZ(t[2]);
                bone.setRotX(t[3] * (float) Math.PI / 180f);
                bone.setRotY(t[4] * (float) Math.PI / 180f);
                bone.setRotZ(t[5] * (float) Math.PI / 180f);
                bone.setHidden(t[6] > 0.5f);
            } else {
                bone.setPosX(0);
                bone.setPosY(0);
                bone.setPosZ(0);
                bone.setRotX(0);
                bone.setRotY(0);
                bone.setRotZ(0);
                bone.setHidden(n.equals("shell"));
            }
        }
        super.renderRecursively(m, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    static final class Model extends GeoModel<GunItem> {
        private final Identifier geo, tex, anim;

        Model(String id) {
            geo = Novograd.id("geo/guns/" + id + ".geo.json");
            tex = Novograd.id("textures/item/guns/" + id + ".png");
            anim = Novograd.id("animations/guns/" + id + ".animation.json");
        }

        @Override
        public Identifier getModelResource(GunItem animatable) {
            return geo;
        }

        @Override
        public Identifier getTextureResource(GunItem animatable) {
            return tex;
        }

        @Override
        public Identifier getAnimationResource(GunItem animatable) {
            return anim;
        }

        @Override
        public RenderLayer getRenderType(GunItem animatable, Identifier texture) {
            return RenderLayer.getEntityCutoutNoCull(texture);
        }
    }
}
