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
     * Выстрел NPC и старых мест: одна пуля, звук пистолета или автомата.
     * @return в кого попали (или null)
     */
    public static Entity shoot(ServerWorld w, LivingEntity shooter, Vec3d from, Vec3d dir, float damage, double spread, double range, boolean smg) {
        return fire(w, shooter, from, dir, damage, spread, range, 1, smg ? ModContent.SMG_SHOT : ModContent.GUNSHOT, smg ? 0.9f : 1.2f);
    }

    /**
     * Выстрел любым стволом: pellets дробинок (у дробовика урон падает с расстоянием), один звук и одна вспышка.
     * Урон по одной цели суммируется и наносится разом. Пули игрока не упираются в «неуязвимость после удара».
     * @return первый, в кого попали (или null)
     */
    public static Entity fire(ServerWorld w, LivingEntity shooter, Vec3d from, Vec3d dir0, float damage, double spread, double range, int pellets,
                              net.minecraft.sound.SoundEvent sound, float volume) {
        int kind = shooter.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem g ? g.kindIndex()
                : pellets > 1 ? 2 : sound == ModContent.SMG_SHOT ? 1 : 0;
        return fire(w, shooter, from, dir0, damage, spread, range, pellets, sound, volume, kind);
    }

    /**
     * То же с видом ствола (kind — GunItem.kindIndex): клиенты получают пакет Arsenal.ShotFx и рисуют вспышку, трассеры,
     * гильзы, искры; стрелок-игрок — ещё и хитмаркер.
     */
    public static Entity fire(ServerWorld w, LivingEntity shooter, Vec3d from, Vec3d dir0, float damage, double spread, double range, int pellets,
                              net.minecraft.sound.SoundEvent sound, float volume, int kind) {
        var r = w.getRandom();
        Vec3d aim = dir0.normalize();
        w.playSound(null, shooter.getBlockPos(), sound, SoundCategory.PLAYERS, volume, 0.95f + r.nextFloat() * 0.1f);
        float[] ends = new float[pellets * 3];
        byte[] what = new byte[pellets];
        java.util.Map<Entity, Float> hits = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pellets; i++) {
            Vec3d dir = aim.add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread).normalize();
            Vec3d to = from.add(dir.multiply(range));
            BlockHitResult block = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
            Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
            Box box = shooter.getBoundingBox().stretch(end.subtract(from)).expand(1);
            EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(shooter, from, end, box,
                    e -> e != shooter && !e.isSpectator() && e.isAlive() && (e instanceof LivingEntity || e instanceof CarEntity) && !shooter.isConnectedThroughVehicle(e),
                    from.squaredDistanceTo(end));
            if (hit != null) end = hit.getPos();
            double len = end.distanceTo(from);
            ends[i * 3] = (float) end.x;
            ends[i * 3 + 1] = (float) end.y;
            ends[i * 3 + 2] = (float) end.z;
            what[i] = (byte) (hit != null ? 2 : block.getType() != HitResult.Type.MISS ? 1 : 0);
            if (hit != null) {
                float dmg = pellets > 1 ? damage * (float) Math.max(0.35, 1 - len / range * 0.65) : damage;
                hits.merge(hit.getEntity(), dmg, Float::sum);
                if (hit.getEntity() instanceof CarEntity) w.spawnParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
            } else if (block.getType() != HitResult.Type.MISS) {
                var st = w.getBlockState(block.getBlockPos());
                w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), end.x, end.y, end.z, pellets > 1 ? 3 : 6, 0.05, 0.05, 0.05, 0.1);
                what[i] = (byte) (1 | (block.getSide().getId() << 2)); // грань — для искр и следа от пули
            }
        }
        Entity first = null;
        DamageSource src = shooter instanceof PlayerEntity pl ? w.getDamageSources().playerAttack(pl) : w.getDamageSources().mobAttack(shooter);
        for (var e : hits.entrySet()) {
            Entity victim = e.getKey();
            if (first == null) first = victim;
            if (victim instanceof CarEntity car) {
                car.hitByBullet(e.getValue(), shooter);
            } else {
                if (shooter instanceof PlayerEntity && victim instanceof LivingEntity le) le.timeUntilRegen = 0;
                victim.damage(src, e.getValue());
            }
        }
        // хитмаркер: 1 — попал, 2 — убил
        byte hitFlag = 0;
        for (Entity v : hits.keySet()) {
            if (v instanceof LivingEntity le && !(le instanceof net.minecraft.entity.decoration.ArmorStandEntity)) hitFlag = (byte) Math.max(hitFlag, le.isDead() ? 2 : 1);
            else if (v instanceof CarEntity) hitFlag = (byte) Math.max(hitFlag, 1);
        }
        Arsenal.sendShotFx(w, shooter, from, kind, ends, what, hitFlag);
        // горожане в панике, полиция слышит выстрел
        Crime.gunshot(w, shooter, from);
        return first;
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

    /** Любой ствол (пистолет, автомат, дробовик, «АК», снайперка). Гранаты и нож — см. Arsenal.isWeapon. */
    public static boolean isGun(net.minecraft.item.ItemStack s) {
        return s.getItem() instanceof com.yompix.novograd.item.GunItem;
    }

    /** Кто стреляет «по-нашему»: игроки, полицейские и бандиты. */
    public static boolean armed(LivingEntity e) {
        return e instanceof ServerPlayerEntity || e instanceof CitizenEntity c && (c.getRole() == CitizenEntity.Role.POLICE || c.getRole() == CitizenEntity.Role.GANG);
    }
}
