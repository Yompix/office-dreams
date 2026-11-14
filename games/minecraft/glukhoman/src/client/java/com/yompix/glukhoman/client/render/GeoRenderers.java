package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.AnnaEntity;
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
                if (m.equals("@anna")) return id("textures/entity/anna.png");
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

    /** Анна: моргает, на испуге — другое лицо, в кат-сцене «говорит» (рот), голова следит за камерой. */
    static class AnnaModel extends GeoModel<AnnaEntity> {
        @Override
        public Identifier getModelResource(AnnaEntity e) {
            return id("geo/human_slim.geo.json");
        }

        @Override
        public Identifier getTextureResource(AnnaEntity e) {
            long t = e.getWorld().getTime() + e.getId() * 7L;
            if (e.act().equals("upset")) return id("textures/entity/anna_fear.png");
            if (e.talking && (t / 3) % 2 == 0) return id("textures/entity/anna_talk.png");
            if (t % 80 < 3) return id("textures/entity/anna_blink.png");
            return id("textures/entity/anna.png");
        }

        @Override
        public Identifier getAnimationResource(AnnaEntity e) {
            return id("animations/human.animation.json");
        }

        @Override
        public void setCustomAnimations(AnnaEntity e, long instanceId, AnimationState<AnnaEntity> state) {
            if (!com.yompix.glukhoman.client.Cine.active || !e.talking) return;
            GeoBone head = getAnimationProcessor().getBone("head");
            if (head == null) return;
            var c = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            double dx = c.x - e.getX(), dz = c.z - e.getZ();
            float want = (float) (Math.atan2(dx, dz)) - (float) Math.toRadians(-e.bodyYaw);
            head.setRotY(head.getRotY() + Math.max(-0.9f, Math.min(0.9f, (float) Math.atan2(Math.sin(want), Math.cos(want)))) * 0.6f);
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
        EntityRendererRegistry.register(GContent.ANNA, ctx -> new GeoEntityRenderer<>(ctx, new AnnaModel()));
        EntityRendererRegistry.register(GContent.PEBBLE_ENTITY, net.minecraft.client.render.entity.FlyingItemEntityRenderer::new);
    }
}
