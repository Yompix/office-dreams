package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Киностудия: свои кинофильтры (пост-эффекты). */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("loadPostProcessor")
    void novograd$loadPost(Identifier id);

    @Invoker("disablePostProcessor")
    void novograd$disablePost();
}
