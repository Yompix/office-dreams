package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.AnnaEntity;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PoleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class Renderers {
    private Renderers() {}

    public static final EntityModelLayer POLE = new EntityModelLayer(Glukhoman.id("pole"), "main");
    public static final EntityModelLayer LISTENER = new EntityModelLayer(Glukhoman.id("listener"), "main");
    public static final EntityModelLayer CHOIR = new EntityModelLayer(Glukhoman.id("choir"), "main");

    static Identifier tex(String n) {
        return Glukhoman.id("textures/entity/" + n + ".png");
    }

    public static void init() {
        EntityModelLayerRegistry.registerModelLayer(POLE, PoleModel::data);
        EntityModelLayerRegistry.registerModelLayer(LISTENER, ListenerModel::data);
        EntityModelLayerRegistry.registerModelLayer(CHOIR, ChoirModel::data);
        EntityRendererRegistry.register(GContent.POLE, ctx -> new MobEntityRenderer<PoleEntity, PoleModel>(ctx, new PoleModel(ctx.getPart(POLE)), 0.4f) {
            @Override
            public Identifier getTexture(PoleEntity e) {
                return tex("pole");
            }
        });
        EntityRendererRegistry.register(GContent.LISTENER, ctx -> new MobEntityRenderer<ListenerEntity, ListenerModel>(ctx, new ListenerModel(ctx.getPart(LISTENER)), 0.6f) {
            @Override
            public Identifier getTexture(ListenerEntity e) {
                return tex("listener");
            }
        });
        EntityRendererRegistry.register(GContent.CHOIR, ctx -> new MobEntityRenderer<ChoirEntity, ChoirModel>(ctx, new ChoirModel(ctx.getPart(CHOIR)), 1.2f) {
            @Override
            public Identifier getTexture(ChoirEntity e) {
                return tex("choir");
            }

            @Override
            protected void scale(ChoirEntity e, MatrixStack m, float f) {
                m.scale(1.1f, 1.1f, 1.1f);
            }
        });
        EntityRendererRegistry.register(GContent.ANNA, ctx -> new BipedEntityRenderer<AnnaEntity, PlayerEntityModel<AnnaEntity>>(ctx,
                new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true), 0.5f) {
            @Override
            public Identifier getTexture(AnnaEntity e) {
                return tex("anna");
            }
        });
        EntityRendererRegistry.register(GContent.PEBBLE_ENTITY, FlyingItemEntityRenderer::new);
    }
}
