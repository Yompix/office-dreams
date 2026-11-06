package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Мотор: тон по оборотам (передачи CarEntity.gear/rpm), громче под газом, затихает, когда водитель вышел. */
public class CarEngineSound extends MovingSoundInstance {
    private final CarEntity car;
    private int idle;

    public CarEngineSound(CarEntity car) {
        super(car.isBike() ? com.yompix.novograd.vehicle.Vehicles.BIKE_ENGINE : ModContent.CAR_ENGINE, SoundCategory.NEUTRAL, Random.create());
        this.car = car;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.01f;
        this.x = car.getX();
        this.y = car.getY();
        this.z = car.getZ();
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }

    @Override
    public void tick() {
        if (car.isRemoved()) {
            setDone();
            return;
        }
        x = car.getX();
        y = car.getY();
        z = car.getZ();
        boolean driven = car.getControllingPassenger() != null || car.hasNpcDriver();
        idle = driven ? 0 : idle + 1;
        // тон — от оборотов: растёт на передаче и проседает при переключении; грузовики басовитее, спорткары звонче
        float base = 1f;
        if (!car.isBike() && !car.isScooter()) base = switch (car.body()) {
            case TRUCK -> 0.72f;
            case VAN, PICKUP, SUV -> 0.85f;
            case MUSCLE -> 0.8f;
            case SPORTS -> 1.12f;
            case HATCH -> 1.08f;
            default -> 1f;
        };
        float targetVol = driven ? 0.38f + car.rpm * 0.32f + car.throttle * 0.12f : 0;
        volume = MathHelper.lerp(0.15f, volume, targetVol);
        float targetPitch = (0.55f + car.rpm * 1.05f) * base;
        // переключение вверх — тон падает быстро, разгон — плавно
        pitch = MathHelper.lerp(targetPitch < pitch - 0.15f ? 0.45f : 0.22f, pitch, Math.max(0.5f, targetPitch));
        if (idle > 30) setDone();
    }
}
