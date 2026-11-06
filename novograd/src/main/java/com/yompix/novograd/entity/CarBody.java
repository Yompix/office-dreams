package com.yompix.novograd.entity;

/**
 * Тип кузова по варианту машины (номер варианта = ordinal): форму рисует CarRenderer, характер езды — CarEntity.drive.
 * 5 — такси и 6 — полиция остаются на своих местах: на них завязаны сюжет, погоня и работа таксиста.
 */
public enum CarBody {
    //        макс.   разгон  руль   сцепл.  тормоз  передач
    SEDAN    (1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 5, 1400, 0.82f),
    HATCH    (0.92f, 1.05f, 1.12f, 1.05f, 1.00f, 5),
    SPORTS   (1.22f, 1.40f, 1.12f, 1.10f, 1.25f, 6, 1300, 0.74f),
    LUXURY   (1.10f, 1.15f, 0.95f, 1.05f, 1.10f, 6),
    VAN      (0.82f, 0.75f, 0.85f, 0.95f, 0.85f, 5, 2600, 0.85f),
    TAXI     (1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 5),
    POLICE   (1.14f, 1.25f, 1.05f, 1.05f, 1.15f, 5),
    SUV      (0.98f, 1.00f, 0.92f, 1.00f, 1.00f, 6, 2100, 0.97f),
    PICKUP   (0.95f, 1.00f, 0.90f, 0.85f, 0.95f, 5, 2000, 0.95f),
    MUSCLE   (1.16f, 1.45f, 0.95f, 0.70f, 0.95f, 4),
    LIMO     (0.95f, 0.85f, 0.75f, 1.00f, 0.90f, 5, 2800, 0.70f),
    TRUCK    (0.76f, 0.60f, 0.75f, 0.95f, 0.75f, 6, 7500, 0.85f),
    // 0.4: новые машины (варианты 12–19, см. CarEntity.VARIANTS)       масса  бездорожье
    SUPERCAR (1.42f, 1.65f, 1.15f, 1.20f, 1.40f, 7, 1350, 0.70f),
    CLASSIC  (1.08f, 1.12f, 0.88f, 0.80f, 0.85f, 4, 1550, 0.80f),
    LOWRIDER (0.96f, 0.95f, 0.85f, 0.85f, 0.90f, 4, 1900, 0.75f),
    BUGGY    (1.04f, 1.35f, 1.25f, 0.95f, 1.05f, 5, 800, 1.08f),
    COMPACT  (0.86f, 1.10f, 1.28f, 1.10f, 1.05f, 5, 950, 0.82f),
    SWAT     (0.90f, 0.82f, 0.80f, 1.00f, 0.90f, 5, 6200, 0.95f),
    AMBULANCE(0.94f, 0.90f, 0.88f, 0.98f, 0.95f, 5, 3200, 0.85f),
    FIRETRUCK(0.80f, 0.66f, 0.72f, 0.95f, 0.80f, 6, 9000, 0.85f);

    /** Множители: максимальная скорость, разгон, поворот руля, сцепление шин (меньше — легче занос), тормоза. */
    public final float top, accel, turn, grip, brake;
    public final int gears;
    /** Масса, кг (для витрины и тяжести ударов) и скорость вне асфальта (песок, трава, снег): 1 — как по дороге. */
    public final int mass;
    public final float offroad;

    CarBody(float top, float accel, float turn, float grip, float brake, int gears) {
        this(top, accel, turn, grip, brake, gears, 1400, 0.82f);
    }

    CarBody(float top, float accel, float turn, float grip, float brake, int gears, int mass, float offroad) {
        this.top = top;
        this.accel = accel;
        this.turn = turn;
        this.grip = grip;
        this.brake = brake;
        this.gears = gears;
        this.mass = mass;
        this.offroad = offroad;
    }

    /** Служебные машины: не жгут бензин игрока и не продаются у дилера. */
    public boolean service() {
        return this == POLICE || this == TAXI || this == SWAT || this == AMBULANCE || this == FIRETRUCK;
    }

    /** Мигалка на крыше (полиция, спецназ, скорая, пожарные). */
    public boolean beacon() {
        return this == POLICE || this == SWAT || this == AMBULANCE || this == FIRETRUCK;
    }

    /** Длинная грузовая база (6 колёс, огни внизу будки). */
    public boolean truck() {
        return this == TRUCK || this == FIRETRUCK;
    }

    public static CarBody of(int variant) {
        CarBody[] v = values();
        return v[Math.max(0, Math.min(variant, v.length - 1))];
    }
}
