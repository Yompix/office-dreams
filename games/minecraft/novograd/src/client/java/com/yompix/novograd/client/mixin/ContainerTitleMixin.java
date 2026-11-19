package com.yompix.novograd.client.mixin;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Сундуки и бочки в стиле города (тёмная панель, tools/gen_containers.py): подписи — светлые, а не тёмно-серые. */
@Mixin(HandledScreen.class)
public class ContainerTitleMixin {
    @ModifyConstant(method = "drawForeground", constant = @Constant(intValue = 4210752))
    private int novograd$lightTitle(int color) {
        return (Object) this instanceof GenericContainerScreen ? 0xFFE6E6E6 : color;
    }
}
