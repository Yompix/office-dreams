package com.yompix.glukhoman.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.0: Память — забытый игрок в TAB серый («???» на нуле). */
@Mixin(ServerPlayerEntity.class)
public class ListNameMixin {
    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void glukhoman$forgotten(CallbackInfoReturnable<Text> cir) {
        Text t = com.yompix.glukhoman.story.Memory.listName((ServerPlayerEntity) (Object) this);
        if (t != null) cir.setReturnValue(t);
    }
}
