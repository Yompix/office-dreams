package com.yompix.novograd.entity;

/**
 * Тип кузова по варианту машины (номер варианта = ordinal): форму рисует CarRenderer, характер езды — CarEntity.drive.
 * 5 — такси и 6 — полиция остаются на своих местах: на них завязаны сюжет, погоня и работа таксиста.
 */
public enum CarBody {
    //        макс.   разгон  руль   сцепл.  тормоз  передач
    SEDAN    (1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 5),
    HATCH    (0.92f, 1.05f, 1.12f, 1.05f, 1.00f, 5),
    SPORTS   (1.22f, 1.40f, 1.12f, 1.10f, 1.25f, 6),
    LUXURY   (1.10f, 1.15f, 0.95f, 1.05f, 1.10f, 6),
    VAN      (0.82f, 0.75f, 0.85f, 0.95f, 0.85f, 5),
    TAXI     (1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 5),
    POLICE   (1.14f, 1.25f, 1.05f, 1.05f, 1.15f, 5),
    SUV      (0.98f, 1.00f, 0.92f, 1.00f, 1.00f, 6),
    PICKUP   (0.95f, 1.00f, 0.90f, 0.85f, 0.95f, 5),
    MUSCLE   (1.16f, 1.45f, 0.95f, 0.70f, 0.95f, 4),
    LIMO     (0.95f, 0.85f, 0.75f, 1.00f, 0.90f, 5),
    TRUCK    (0.76f, 0.60f, 0.75f, 0.95f, 0.75f, 6);

    /** Множители: максимальная скорость, разгон, поворот руля, сцепление шин (меньше — легче занос), тормоза. */
    public final float top, accel, turn, grip, brake;
    public final int gears;

    CarBody(float top, float accel, float turn, float grip, float brake, int gears) {
        this.top = top;
        this.accel = accel;
        this.turn = turn;
        this.grip = grip;
        this.brake = brake;
        this.gears = gears;
    }

    public static CarBody of(int variant) {
        CarBody[] v = values();
        return v[Math.max(0, Math.min(variant, v.length - 1))];
    }
}
