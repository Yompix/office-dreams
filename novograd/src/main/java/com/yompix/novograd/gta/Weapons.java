package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/** Выстрел «лучом»: вспышка у ствола, дымный след, искры от стены, кровь от попадания. Общий для игрока, полиции и банд. */
public final class Weapons {
    private Weapons() {}

    public static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55f, 0.02f, 0.02f), 1.4f);
    private static final BlockStateParticleEffect BLOOD_DROPS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());

    /**
     * @return в кого попали (или null)
     */
    public static Entity shoot(ServerWorld w, LivingEntity shooter, Vec3d from, Vec3d dir, float damage, double spread, double range, boolean smg) {
        var r = w.getRandom();
        dir = dir.normalize().add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread).normalize();
        Vec3d to = from.add(dir.multiply(range));
        BlockHitResult block = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
        Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
        Box box = shooter.getBoundingBox().stretch(end.subtract(from)).expand(1);
        EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(shooter, from, end, box,
                e -> e != shooter && !e.isSpectator() && e.isAlive() && (e instanceof LivingEntity || e instanceof CarEntity) && !shooter.isConnectedThroughVehicle(e),
                from.squaredDistanceTo(end));
        if (hit != null) end = hit.getPos();
        // звук и вспышка
        w.playSound(null, shooter.getBlockPos(), smg ? ModContent.SMG_SHOT : ModContent.GUNSHOT, SoundCategory.PLAYERS, smg ? 0.9f : 1.2f, 0.95f + r.nextFloat() * 0.1f);
        // вспышка у ствола: правее и ниже глаз, где оружие в руке
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d muzzle = from.add(dir.multiply(1.3)).add(right.multiply(0.32)).add(0, -0.28, 0);
        w.spawnParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.01, 0.01, 0.01, 0.005);
        w.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 1, 0.02, 0.02, 0.02, 0.01);
        // след пули
        double len = end.distanceTo(from);
        for (double t = 2.5; t < len; t += 1.6) {
            Vec3d p = from.add(dir.multiply(t));
            w.spawnParticles(ParticleTypes.WHITE_SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Entity victim = null;
        if (hit != null) {
            victim = hit.getEntity();
            DamageSource src = shooter instanceof PlayerEntity pl ? w.getDamageSources().playerAttack(pl) : w.getDamageSources().mobAttack(shooter);
            if (victim instanceof CarEntity car) {
                car.hitByBullet(damage, shooter);
                w.spawnParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
            } else {
                victim.damage(src, damage);
            }
        } else if (block.getType() != HitResult.Type.MISS) {
            var st = w.getBlockState(block.getBlockPos());
            w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), end.x, end.y, end.z, 6, 0.05, 0.05, 0.05, 0.1);
            w.spawnParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.15);
        }
        // горожане в панике, полиция слышит выстрел
        Crime.gunshot(w, shooter, from);
        return victim;
    }

    /** Кровь при ранении: облачко и капли, падающие на землю. */
    public static void blood(ServerWorld w, LivingEntity e, float amount, boolean death) {
        if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) return;
        double y = e.getY() + e.getHeight() * 0.6;
        int n = Math.min(30, 6 + (int) (amount * 2)) * (death ? 2 : 1);
        w.spawnParticles(BLOOD, e.getX(), y, e.getZ(), n, 0.2, 0.25, 0.2, 0.02);
        w.spawnParticles(BLOOD_DROPS, e.getX(), y, e.getZ(), n / 2, 0.15, 0.2, 0.15, 0.15);
    }

    /** Есть ли патроны (в творческом — бесконечно). Тратит один. */
    public static boolean useAmmo(PlayerEntity p) {
        if (p.isCreative()) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var s = inv.getStack(i);
            if (s.isOf(ModContent.AMMO)) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }

    public static boolean isGun(net.minecraft.item.ItemStack s) {
        return s.isOf(ModContent.PISTOL) || s.isOf(ModContent.SMG);
    }

    /** Кто стреляет «по-нашему»: игроки, полицейские и бандиты. */
    public static boolean armed(LivingEntity e) {
        return e instanceof ServerPlayerEntity || e instanceof CitizenEntity c && (c.getRole() == CitizenEntity.Role.POLICE || c.getRole() == CitizenEntity.Role.GANG);
    }
}
