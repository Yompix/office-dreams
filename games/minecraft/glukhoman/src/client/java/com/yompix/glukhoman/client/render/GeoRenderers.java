package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.entity.DoppelEntity;
import com.yompix.glukhoman.entity.HorrorMob;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Анимированные модели (GeckoLib): существа со светящимися глазами, люди на скелете с захватом движения. */
public final class GeoRenderers {
    private GeoRenderers() {}

    static Identifier id(String p) {
        return Glukhoman.id(p);
    }

    static class MobModel<T extends HorrorMob> extends GeoModel<T> {
        @Override
        public Identifier getModelResource(T e) {
            if (e instanceof DoppelEntity d) return id("geo/" + (slim(d) ? "human_slim" : "human") + ".geo.json");
            return id("geo/" + e.geoName() + ".geo.json");
        }

        @Override
        public Identifier getTextureResource(T e) {
            if (e instanceof DoppelEntity d) {
                String m = d.mimic();
                if (m.equals("@anna")) {   // Двойник Анны: моргает медленно и не вовремя
                    long t = d.getWorld().getTime() + d.getId() * 13L;
                    return id(t % 97 < 16 ? "textures/entity/anna_double_blink.png" : "textures/entity/anna_double.png");
                }
                SkinTextures s = skin(m);
                if (s != null) return s.texture();
                return id("textures/entity/doppel.png");
            }
            return id("textures/entity/" + e.geoName() + ".png");
        }

        @Override
        public Identifier getAnimationResource(T e) {
            return id("animations/" + (e instanceof DoppelEntity ? "human" : e.geoName()) + ".animation.json");
        }

        /** Обходчик: тело поворачивает по ходу, а голова неотрывно на игроке — слишком далеко, до 110°. */
        @Override
        public void setCustomAnimations(T e, long instanceId, AnimationState<T> state) {
            if (e instanceof com.yompix.glukhoman.entity.WatcherEntity && !com.yompix.glukhoman.client.Cine.active)
                headLook(getAnimationProcessor().getBone("head"), e, state.getPartialTick(), 110);
        }

        static boolean slim(DoppelEntity d) {
            if (d.mimic().equals("@anna")) return true;
            SkinTextures s = skin(d.mimic());
            return s != null && s.model() == SkinTextures.Model.SLIM;
        }

        static SkinTextures skin(String name) {
            var h = MinecraftClient.getInstance().getNetworkHandler();
            if (h == null || name.isEmpty()) return null;
            PlayerListEntry e = h.getPlayerListEntry(name);
            return e == null ? null : e.getSkinTextures();
        }
    }

    /** Жители: скин по имени, моргают, «говорят» в кат-сцене; Вика — ниже ростом. */
    static class ResidentModel extends GeoModel<ResidentEntity> {
        @Override
        public Identifier getModelResource(ResidentEntity e) {
            String w = e.who();
            return id("geo/" + (w.equals("vika") || w.equals("zina") || w.equals("polya") || w.equals("nyura") || w.equals("lyuba") || w.equals("mitya") ? "human_slim" : "human") + ".geo.json");
        }

        @Override
        public Identifier getTextureResource(ResidentEntity e) {
            long t = e.getWorld().getTime() + e.getId() * 7L;
            String base = "textures/entity/res_" + e.who();
            if (e.talking && (t / 3) % 2 == 0) return id(base + "_talk.png");
            if (!e.sleepwalking() && t % 80 < 3) return id(base + "_blink.png");
            if (e.sleepwalking() && t % 300 < 120) return id(base + "_blink.png");   // идёт с закрытыми глазами
            return id(base + ".png");
        }

        @Override
        public Identifier getAnimationResource(ResidentEntity e) {
            return id("animations/human.animation.json");
        }

        /** Голова смотрит туда, куда смотрит житель (на игрока, по сторонам), поверх анимации тела. */
        @Override
        public void setCustomAnimations(ResidentEntity e, long instanceId, AnimationState<ResidentEntity> state) {
            if (e.sleepwalking() || com.yompix.glukhoman.client.Cine.active) return;
            headLook(getAnimationProcessor().getBone("head"), e, state.getPartialTick(), 60);
        }
    }

    static void headLook(GeoBone head, LivingEntity e, float pt, float limit) {
        if (head == null) return;
        float body = net.minecraft.util.math.MathHelper.lerpAngleDegrees(pt, e.prevBodyYaw, e.bodyYaw);
        float hy = net.minecraft.util.math.MathHelper.lerpAngleDegrees(pt, e.prevHeadYaw, e.headYaw);
        float d = net.minecraft.util.math.MathHelper.clamp(net.minecraft.util.math.MathHelper.wrapDegrees(hy - body), -limit, limit);
        float pitch = net.minecraft.util.math.MathHelper.lerp(pt, e.prevPitch, e.getPitch());
        head.setRotY(head.getRotY() + d * net.minecraft.util.math.MathHelper.RADIANS_PER_DEGREE);
        head.setRotX(head.getRotX() + pitch * net.minecraft.util.math.MathHelper.RADIANS_PER_DEGREE * 0.7f);
    }

    static class TrainModel extends GeoModel<TrainEntity> {
        @Override
        public Identifier getModelResource(TrainEntity e) {
            return id("geo/train.geo.json");
        }

        @Override
        public Identifier getTextureResource(TrainEntity e) {
            return id("textures/entity/train.png");
        }

        @Override
        public Identifier getAnimationResource(TrainEntity e) {
            return id("animations/train.animation.json");
        }
    }

    static <T extends HorrorMob> GeoEntityRenderer<T> mob(net.minecraft.client.render.entity.EntityRendererFactory.Context ctx, float shadow, boolean glow) {
        GeoEntityRenderer<T> r = new GeoEntityRenderer<>(ctx, new MobModel<>());
        if (glow) r.addRenderLayer(new AutoGlowingGeoLayer<>(r));   // светящиеся глаза (текстура *_glowmask)
        return r;
    }

    public static void init() {
        EntityRendererRegistry.register(GContent.POLE, ctx -> mob(ctx, 0.4f, true));
        EntityRendererRegistry.register(GContent.LISTENER, ctx -> mob(ctx, 0.6f, false));
        EntityRendererRegistry.register(GContent.CHOIR, ctx -> mob(ctx, 1.2f, true));
        EntityRendererRegistry.register(GContent.DOPPEL, ctx -> mob(ctx, 0.5f, false));
        EntityRendererRegistry.register(GContent.RESIDENT, ctx -> {
            GeoEntityRenderer<ResidentEntity> r = new GeoEntityRenderer<>(ctx, new ResidentModel()) {
                @Override
                public void scaleModelForRender(float widthScale, float heightScale, net.minecraft.client.util.math.MatrixStack poseStack, ResidentEntity animatable,
                                                software.bernie.geckolib.cache.object.BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
                    float k = animatable.who().equals("vika") ? 0.86f : animatable.who().equals("mitya") ? 0.74f : 1f;
                    super.scaleModelForRender(widthScale * k, heightScale * k, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
                }
            };
            return r;
        });
        EntityRendererRegistry.register(GContent.TRAIN, ctx -> {
            // GeckoLib берёт поворот только у «живых» сущностей; поезд — не живой, поэтому поворот по пути задаём сами,
            // иначе состав всегда стоит вдоль оси Z — поперёк путей (баг 0.4.0)
            GeoEntityRenderer<TrainEntity> r = new GeoEntityRenderer<>(ctx, new TrainModel()) {
                @Override
                protected void applyRotations(TrainEntity e, net.minecraft.client.util.math.MatrixStack m, float age, float rotationYaw, float pt, float nativeScale) {
                    super.applyRotations(e, m, age, net.minecraft.util.math.MathHelper.lerpAngleDegrees(pt, e.prevYaw, e.getYaw()), pt, nativeScale);
                }
            };
            r.addRenderLayer(new AutoGlowingGeoLayer<>(r));
            return r;
        });
        EntityRendererRegistry.register(GContent.WATCHER, ctx -> mob(ctx, 0.4f, true));
        EntityRendererRegistry.register(GContent.PEBBLE_ENTITY, net.minecraft.client.render.entity.FlyingItemEntityRenderer::new);
    }
}
