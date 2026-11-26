package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.9.8, только автотест-витрины: камера в заданной точке (вид «сбоку» на героя), герой при этом рисуется (вид от 3-го лица). */
@Mixin(Camera.class)
public abstract class ShowCameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void novograd$showCam(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        float[] c = com.yompix.novograd.client.HandShow.CAM;
        if (c == null || !thirdPerson) return;
        setPos(new Vec3d(c[0], c[1], c[2]));
        setRotation(c[3], c[4]);
    }
}
