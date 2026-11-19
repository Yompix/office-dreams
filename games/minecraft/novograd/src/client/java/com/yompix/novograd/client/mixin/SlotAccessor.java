package com.yompix.novograd.client.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Инвентарь «как в GTA» (GtaInventoryScreen) ставит ячейки по своей сетке, пока открыт, и возвращает их на место. */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void novograd$setX(int x);

    @Mutable
    @Accessor("y")
    void novograd$setY(int y);
}
