package com.yompix.novograd.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0: строка над хотбаром (сообщения «в action bar», их в моде больше двухсот) — через общий «тихий» вывод
 * (ui/Quiet.status): повтор той же строки не мигает заново, деньги складываются в один счётчик, не срочные строки — не
 * чаще раза в 8 с, каждая держится 0.06 с на букву (не меньше 2.5 с). Срочные (красные: полиция, шипы, «двигайся»)
 * — сразу.
 */
@Mixin(InGameHud.class)
public abstract class OverlayMessageMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void novograd$quiet(Text message, boolean tinted, CallbackInfo ci) {
        if (!com.yompix.novograd.story.Stage.ON || message == null) return;
        com.yompix.novograd.client.ui.Quiet.status(message);
        ci.cancel();
    }
}
