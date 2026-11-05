package com.yompix.lifecraft.client.render;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.entity.CitizenEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Жители рисуются моделью игрока со своими скинами: учитель, продавец, школьники, рабочий, офисный работник, полицейский, бабушка. */
public class CitizenRenderer extends BipedEntityRenderer<CitizenEntity, PlayerEntityModel<CitizenEntity>> {
    private static final Identifier[] SKINS = new Identifier[CitizenEntity.SKINS.length];

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = LifeCraft.id("textures/entity/citizen/" + CitizenEntity.SKINS[i] + ".png");
    }

    public CitizenRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(CitizenEntity e) {
        return SKINS[e.getSkin()];
    }
}
