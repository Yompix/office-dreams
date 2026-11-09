package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.WildAnimalEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Олень и кабан (модели и анимации пишет tools/gen_outdoor.py). */
public class WildGeoRenderer extends GeoEntityRenderer<WildAnimalEntity> {
    public WildGeoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model());
        shadowRadius = 0.5f;
    }

    static class Model extends GeoModel<WildAnimalEntity> {
        private static final Identifier DEER_GEO = Novograd.id("geo/deer.geo.json"), BOAR_GEO = Novograd.id("geo/boar.geo.json"),
                DEER_TEX = Novograd.id("textures/entity/wild/deer.png"), BOAR_TEX = Novograd.id("textures/entity/wild/boar.png"),
                ANIM = Novograd.id("animations/wild.animation.json");

        @Override
        public Identifier getModelResource(WildAnimalEntity e) {
            return e.kind() == WildAnimalEntity.Kind.BOAR ? BOAR_GEO : DEER_GEO;
        }

        @Override
        public Identifier getTextureResource(WildAnimalEntity e) {
            return e.kind() == WildAnimalEntity.Kind.BOAR ? BOAR_TEX : DEER_TEX;
        }

        @Override
        public Identifier getAnimationResource(WildAnimalEntity e) {
            return ANIM;
        }
    }
}
