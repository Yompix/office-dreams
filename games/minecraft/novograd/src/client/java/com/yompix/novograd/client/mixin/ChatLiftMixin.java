package com.yompix.novograd.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.9.6: чат не прячется за радаром — как в GTA, радар внизу слева, чат над ним (поднимаем на высоту радара;
 * сверху обрезаем, чтобы не залезал под подсказки). Без радара — как в Minecraft.
 */
@Mixin(InGameHud.class)
public class ChatLiftMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void novograd$liftChat(DrawContext ctx, RenderTickCounter t, CallbackInfo ci) {
        int lift = com.yompix.novograd.client.Minimap.chatLift(ctx);
        ctx.getMatrices().push();
        if (lift > 0) {
            ctx.enableScissor(0, 30, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight() - lift - 34);
            ctx.getMatrices().translate(0, -lift, 0);
        }
    }

    @Inject(method = "renderChat", at = @At("TAIL"))
    private void novograd$liftChatEnd(DrawContext ctx, RenderTickCounter t, CallbackInfo ci) {
        if (com.yompix.novograd.client.Minimap.chatLift(ctx) > 0) ctx.disableScissor();
        ctx.getMatrices().pop();
    }
}
