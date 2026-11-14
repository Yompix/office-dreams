package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.horror.Noise;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Брошенный камешек: при падении шумит (громко — 18 блоков), Слухачи идут туда. */
public class PebbleEntity extends ThrownItemEntity {
    public PebbleEntity(EntityType<? extends PebbleEntity> type, World world) {
        super(type, world);
    }

    public PebbleEntity(World world, LivingEntity owner) {
        super(GContent.PEBBLE_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return GContent.PEBBLE;
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, getX(), getY(), getZ(), GContent.sound("pebble"), SoundCategory.PLAYERS, 1.2f, 0.9f + random.nextFloat() * 0.2f);
            Noise.emit(sw, getPos(), 18, null);
            discard();
        }
    }
}
