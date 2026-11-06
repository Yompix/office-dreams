package com.yompix.novograd.entity;

import com.yompix.novograd.gta.Bodyguards;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.gta.Weapons;
import com.yompix.novograd.item.GunItem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Телохранитель (агентство «Щит», см. Bodyguards): житель Новограда с оружием, который ходит за нанимателем,
 * садится к нему в машину пассажиром, стреляет по тем, кто напал на хозяина (бандиты, полиция, звери), и может погибнуть.
 * Вне нанимателя не существует: в мир не сохраняется, при входе игрока появляется заново (Bodyguards следит).
 */
public class BodyguardEntity extends CitizenEntity {
    @Nullable private UUID owner;
    private int tier, slot, shootCooldown, burstLeft, lastCopWarn;
    private boolean stay;
    @Nullable private LivingEntity target;
    @Nullable private Vec3d stayAt;

    public BodyguardEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createGuardAttributes() {
        return CitizenEntity.createAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_ARMOR, 4);
    }

    /** Нанят: хозяин, класс (0 охранник, 1 боец, 2 ветеран спецназа), номер в списке. */
    public void hire(UUID owner, int tier, int slot) {
        this.owner = owner;
        this.tier = tier;
        this.slot = slot;
        var hp = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Bodyguards.TIER_HP[tier]);
        setHealth(getMaxHealth());
        lockActivity(Activity.IDLE);
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Bodyguards.tierGun(tier)));
        setCustomNameVisible(false); // имя — при наведении, без толпы табличек над охраной
    }

    @Nullable public UUID owner() {
        return owner;
    }

    public int slot() {
        return slot;
    }

    public boolean isOwner(net.minecraft.entity.Entity e) {
        return owner != null && e != null && owner.equals(e.getUuid());
    }

    public void setStay(boolean s) {
        stay = s;
        stayAt = s ? getPos() : null;
    }

    public boolean isStaying() {
        return stay;
    }

    @Nullable ServerPlayerEntity ownerPlayer() {
        if (owner == null || !(getWorld() instanceof ServerWorld w)) return null;
        return w.getServer().getPlayerManager().getPlayer(owner);
    }

    /** Взять цель (напали на хозяина или на самого охранника). Своих и хозяина не трогаем. */
    public void engage(@Nullable LivingEntity t) {
        if (t == null || !t.isAlive() || isOwner(t) || t == this) return;
        if (t instanceof BodyguardEntity b && owner != null && owner.equals(b.owner)) return;
        if (target == null || !target.isAlive() || distanceTo(t) < distanceTo(target) - 4) target = t;
    }

    @Nullable public LivingEntity target() {
        return target;
    }

    // ---------------------------------------------------------------- поведение
    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new GuardGoal(this));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    public void attack(LivingEntity t) {
        engage(t);
    }

    @Override
    public void panic(Vec3d from) {
        // охрана не разбегается
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // по своим не попадает: хозяин и другие охранники того же хозяина урона не наносят
        var a = source.getAttacker();
        if (isOwner(a) || a instanceof BodyguardEntity b && owner != null && owner.equals(b.owner)) return false;
        boolean hurt = super.damage(source, amount);
        if (hurt && !getWorld().isClient && a instanceof LivingEntity le && isAlive()) engage(le);
        return hurt;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!getWorld().isClient) Bodyguards.onDeath(this, source);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            if (isOwner(sp)) Bodyguards.toggleStay(sp);
            else sp.sendMessage(net.minecraft.text.Text.literal(getName().getString() + ": «Я на работе. Проходи.»"), true);
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** В мир не сохраняется — Bodyguards вызовет охрану заново, когда наниматель снова в игре. */
    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        // без хозяина (уволен, вышел из игры) — уходит
        if (owner == null || age % 20 == 0 && !Bodyguards.isActive(this)) discard();
    }

    /** Ход охраны: держаться рядом с хозяином, садиться к нему в машину, отстреливаться. */
    static class GuardGoal extends Goal {
        private final BodyguardEntity g;

        GuardGoal(BodyguardEntity g) {
            this.g = g;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return g.owner != null && g.isAlive();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerPlayerEntity o = g.ownerPlayer();
            if (o == null || o.getServerWorld() != g.getWorld()) return;
            if (g.age % 10 == 0) scan(o);
            LivingEntity t = g.target;
            if (t != null && (!t.isAlive() || t.isRemoved() || g.squaredDistanceTo(t) > 48 * 48
                    || t instanceof CitizenEntity c && c.getRole() == Role.POLICE && Wanted.stars(o) == 0 && o.getAttacker() != c)) {
                g.target = t = null;
            }
            double dOwner = g.distanceTo(o);
            // машина: хозяин в машине — садимся, вышел — выходим
            if (o.getVehicle() instanceof CarEntity car && !car.wrecked()) {
                if (g.getVehicle() != car) {
                    if (g.hasVehicle()) g.stopRiding();
                    if (dOwner < 5 || dOwner > 40) {
                        if (car.getPassengerList().size() < 4 && g.startRiding(car, true)) return;
                    } else g.getNavigation().startMovingTo(car.getX(), car.getY(), car.getZ(), 1.4);
                }
            } else if (g.hasVehicle()) {
                g.stopRiding();
            }
            if (g.hasVehicle()) {
                if (t != null) shoot(o, t); // стреляет из окна
                return;
            }
            if (t != null) {
                if (g.getActivity() != Activity.AIM) {
                    g.lockActivity(Activity.AIM);
                    g.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Bodyguards.tierGun(g.tier)));
                }
                g.getLookControl().lookAt(t, 40, 40);
                double d = g.distanceTo(t);
                boolean see = g.canSee(t);
                // держимся между хозяином и угрозой, но не дальше 18 блоков от хозяина
                if (dOwner > 18) g.getNavigation().startMovingTo(o, 1.35);
                else if (d > 16 || !see) g.getNavigation().startMovingTo(t, 1.25);
                else if (d < 5) {
                    Vec3d back = g.getPos().add(g.getPos().subtract(t.getPos()).normalize().multiply(3));
                    g.getNavigation().startMovingTo(back.x, back.y, back.z, 1.1);
                } else g.getNavigation().stop();
                if (see) shoot(o, t);
                return;
            }
            if (g.getActivity() != Activity.IDLE) {
                g.lockActivity(Activity.IDLE);
                g.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Bodyguards.tierGun(g.tier)));
            }
            if (g.stay && g.stayAt != null) {
                if (g.squaredDistanceTo(g.stayAt) > 4) g.getNavigation().startMovingTo(g.stayAt.x, g.stayAt.y, g.stayAt.z, 1.0);
                if (g.age % 20 == 0) g.getLookControl().lookAt(o, 30, 30);
                return;
            }
            // идти за хозяином: в паре шагов, бегом, если отстал; совсем отстал — догоняет «за кадром»
            if (dOwner > 48) Bodyguards.catchUp(g, o);
            else if (dOwner > 3.5 + g.slot) g.getNavigation().startMovingTo(o, dOwner > 9 ? 1.4 : 1.05);
            else {
                g.getNavigation().stop();
                if (g.age % 10 == 0) g.getLookControl().lookAt(o, 30, 30);
            }
        }

        /** Кто угрожает хозяину: напал на него, гонится за ним (бандиты, полиция при розыске), мобы. */
        private void scan(ServerPlayerEntity o) {
            LivingEntity a = o.getAttacker();
            if (a != null && a.isAlive() && o.age - o.getLastAttackedTime() < 100) g.engage(a);
            var w = o.getServerWorld();
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, o.getBoundingBox().expand(24),
                    c -> c.isAlive() && c.chasing(o) && (c.getRole() == Role.GANG || c.getRole() == Role.POLICE)))
                g.engage(c);
            for (HostileEntity m : w.getEntitiesByClass(HostileEntity.class, o.getBoundingBox().expand(16), m -> m.isAlive() && m.getTarget() == o))
                g.engage(m);
            // хозяин сам стреляет по бандитам — помогаем
            LivingEntity hit = o.getAttacking();
            if (hit instanceof CitizenEntity c && c.isAlive() && c.getRole() == Role.GANG && o.age - o.getLastAttackTime() < 100) g.engage(c);
        }

        private void shoot(ServerPlayerEntity o, LivingEntity t) {
            if (--g.shootCooldown > 0 || !g.canSee(t)) return;
            double d = g.distanceTo(t);
            if (d > 34) return;
            var w = (ServerWorld) g.getWorld();
            Vec3d eye = g.getEyePos();
            float dmg = Bodyguards.TIER_DMG[g.tier];
            boolean auto = g.tier > 0;
            Weapons.shoot(w, g, eye, t.getEyePos().add(0, -0.25, 0).subtract(eye), dmg, 0.05 + d * 0.003 - g.tier * 0.01, 40, auto);
            if (auto && --g.burstLeft > 0) g.shootCooldown = 3;
            else {
                g.burstLeft = auto ? 3 + g.getRandom().nextInt(3) : 1;
                g.shootCooldown = (auto ? 18 : 14) + g.getRandom().nextInt(10);
            }
            // огонь по полиции — розыск хозяину
            if (t instanceof CitizenEntity c && c.getRole() == Role.POLICE && g.age - g.lastCopWarn > 100) {
                g.lastCopWarn = g.age;
                Wanted.add(o, 0, "твоя охрана стреляет по полиции", Math.max(2, Wanted.stars(o)));
            }
        }
    }
}
