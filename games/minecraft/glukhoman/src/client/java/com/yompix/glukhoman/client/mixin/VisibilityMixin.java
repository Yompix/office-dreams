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
        if (entity instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity tdc && com.yompix.glukhoman.client.Cine.active
                && !com.yompix.glukhoman.client.Cine.showsWorldText(tdc)) {   // 1.0: в кат-сценах — без бирок и надписей мира (кроме тех, что ставит сама сцена)
            cir.setReturnValue(false);
            return;
        }
        if (entity instanceof net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity td && td.getText().getString().startsWith("‡")) {
            // 0.6: надписи на шпалах «вырезаны» — видны только в видоискателе в ИК (и на плёнке)
            boolean ok = (Viewfinder.view() && Viewfinder.ir) || (com.yompix.glukhoman.client.Cine.active && com.yompix.glukhoman.client.Cine.replay);
            if (!ok) cir.setReturnValue(false);
            return;
        }
        if (entity instanceof com.yompix.glukhoman.entity.ResidentEntity rx && rx.extra()) {   // 0.7: Лишний — на плёнке его нет
            if (Viewfinder.view()) cir.setReturnValue(false);
            return;
        }
        if (entity instanceof com.yompix.glukhoman.entity.TrainEntity && com.yompix.glukhoman.client.GlukhomanClient.trainVisible) {   // глава V: видно глазами
            cir.setReturnValue(true);
            return;
        }
        if (entity instanceof com.yompix.glukhoman.entity.ResidentEntity r && r.cameraOnly) {
            cir.setReturnValue(Viewfinder.view());
            return;
        }
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
