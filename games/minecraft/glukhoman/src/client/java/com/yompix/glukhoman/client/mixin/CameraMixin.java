package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Cine;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Камера кат-сцены: поза считается каждый кадр (без шагов по тикам), игрок виден как в третьем лице. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean thirdPerson;

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void glukhoman$cine(BlockView area, Entity focused, boolean third, boolean inverse, float tickDelta, CallbackInfo ci) {
        if (!Cine.active) {
            com.yompix.glukhoman.client.HandCam.frame(tickDelta);
            if (com.yompix.glukhoman.client.HandCam.active && !third) {
                Camera self = (Camera) (Object) this;
                net.minecraft.util.math.Vec3d o = com.yompix.glukhoman.client.HandCam.offset(self.getYaw());
                setRotation(self.getYaw() + com.yompix.glukhoman.client.HandCam.dYaw, self.getPitch() + com.yompix.glukhoman.client.HandCam.dPitch);
                setPos(self.getPos().x + o.x, self.getPos().y + o.y, self.getPos().z + o.z);
            }
            return;
        }
        Cine.frame();
        if (!Cine.active) return;
        setRotation(Cine.yaw, Cine.pitch);
        setPos(Cine.cx, Cine.cy, Cine.cz);
        thirdPerson = true;
    }
}
