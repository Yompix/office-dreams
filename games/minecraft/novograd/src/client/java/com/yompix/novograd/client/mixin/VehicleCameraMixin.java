package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Камера от третьего лица дальше в технике: в машине чуть дальше, в вертолёте/самолёте/катере — заметно. */
@Mixin(Camera.class)
public abstract class VehicleCameraMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), index = 0)
    private float novograd$vehicleDistance(float dist) {
        var e = ((Camera) (Object) this).getFocusedEntity();
        if (e == null || e.getVehicle() == null) return dist;
        var v = e.getVehicle();
        if (v instanceof com.yompix.novograd.vehicle.HeliEntity || v instanceof com.yompix.novograd.vehicle.PlaneEntity) return dist * 3.0f;
        if (v instanceof com.yompix.novograd.vehicle.SpeedboatEntity) return dist * 2.0f;
        if (v instanceof com.yompix.novograd.entity.CarEntity car) return dist * (car.isBike() || car.isScooter() ? 1.2f : 1.55f);
        return dist;
    }
}
