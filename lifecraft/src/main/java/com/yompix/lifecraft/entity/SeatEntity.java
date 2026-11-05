package com.yompix.lifecraft.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/** Невидимое сиденье для парт и скамеек: исчезает, когда человек встал. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public SeatEntity(World world, double x, double y, double z) {
        this(com.yompix.lifecraft.ModContent.SEAT, world);
        setPosition(x, y, z);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && age > 2 && !hasPassengers()) discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public boolean shouldSave() {
        return false;
    }
}
