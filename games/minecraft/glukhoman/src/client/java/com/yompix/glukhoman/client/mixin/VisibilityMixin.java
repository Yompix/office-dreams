package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Viewfinder;
import com.yompix.glukhoman.entity.Seen;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Порожняк виден только в видоискателе, Обходчик — только глазами. Решает каждый клиент сам: кооператив видит разное. */
@Mixin(EntityRenderDispatcher.class)
public class VisibilityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void glukhoman$seen(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Seen s)) return;
        if (com.yompix.glukhoman.client.Cine.active && com.yompix.glukhoman.client.Cine.replay) {   // на плёнке видно то, чего не было в кадре
            cir.setReturnValue(true);
            return;
        }
        boolean cam = Viewfinder.view();
        if (s.seen() == Seen.CAMERA_ONLY && !cam) cir.setReturnValue(false);
        else if (s.seen() == Seen.EYE_ONLY && cam) cir.setReturnValue(false);
        else if (s.seen() == Seen.CAMERA_ONLY) cir.setReturnValue(true);   // состав длинный: не отсекать по рамке
    }
}
