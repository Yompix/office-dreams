package com.yompix.novograd.client;

import com.yompix.novograd.client.screen.BodyguardScreen;
import com.yompix.novograd.gta.Armory;
import com.yompix.novograd.gta.Bodyguards;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.nbt.NbtCompound;

/** Клиент новых стволов: ракета гранатомёта, телохранители (модель жителя), ответы агентства «Щит». */
public final class ArmoryClient {
    private ArmoryClient() {}

    /** Последнее состояние агентства (для окна «Охрана»). */
    public static NbtCompound guards = new NbtCompound();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        EntityRendererRegistry.register(Armory.ROCKET_ENTITY, ctx -> new FlyingItemEntityRenderer<>(ctx, 1.4f, true));
        EntityRendererRegistry.register(Armory.BODYGUARD, (EntityRendererFactory) com.yompix.novograd.client.render.CitizenGeoRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(Bodyguards.Info.ID, (info, ctx) -> ctx.client().execute(() -> {
            guards = info.data();
            if (ctx.client().currentScreen instanceof BodyguardScreen s) s.refresh();
        }));
    }
}
