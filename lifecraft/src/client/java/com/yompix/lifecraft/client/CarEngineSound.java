package com.yompix.lifecraft.client;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** Мотор: тон растёт со скоростью, затихает, когда водитель вышел. */
public class CarEngineSound extends MovingSoundInstance {
    private final CarEntity car;
    private int idle;

    public CarEngineSound(CarEntity car) {
        super(ModContent.CAR_ENGINE, SoundCategory.NEUTRAL, Random.create());
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
        float speed = Math.abs(car.clientSpeed);
        boolean driven = car.getControllingPassenger() != null;
        idle = driven ? 0 : idle + 1;
        float targetVol = driven ? 0.45f + speed * 0.5f : 0;
        volume = MathHelper.lerp(0.15f, volume, targetVol);
        pitch = MathHelper.lerp(0.2f, pitch, 0.7f + speed * 1.6f);
        if (idle > 30) setDone();
    }
}
