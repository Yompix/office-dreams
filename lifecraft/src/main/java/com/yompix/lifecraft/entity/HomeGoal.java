package com.yompix.lifecraft.entity;

import com.yompix.lifecraft.world.TownState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Жизнь дома. Вечером житель идёт к своему дому, открывает дверь, заходит и закрывает её за собой.
 * Дома сидит в телефоне, потом ложится спать: кто первым пришёл — в кровать, второй — на диван,
 * остальные «ушли по комнатам» (их не видно). Утром встаёт, потягивается, завтракает на кухне,
 * выходит из дома и закрывает дверь — дальше обычный распорядок дня.
 */
public class HomeGoal extends Goal {
    /** Дома горожан (дом игрока «home» занят семьёй из сюжета). */
    static final String[] HOUSES = {"gleb", "lisa", "house4", "tema", "house6"};
    /** Кто где спит: «дом:bed» / «дом:sofa» → житель. */
    private static final Map<String, UUID> CLAIMS = new HashMap<>();

    private enum Phase { TO_DOOR, ENTER, EVENING, TO_BED, SLEEP, STRETCH, BREAKFAST, EXIT, LEAVE, OUT }

    private final CitizenEntity c;
    private Phase phase;
    private int timer, stuck;
    @Nullable private BlockPos door;
    private Vec3d outside, inside;
    @Nullable private String spot;   // "bed" / "sofa" / null — спрятался в комнате
    private Vec3d lie, stand;
    private float lieYaw;

    public HomeGoal(CitizenEntity c) {
        this.c = c;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    // ---------------------------------------------------------------- время
    /** Часы от полудня: 0 — полдень, 12 — полночь, 18 — шесть утра. */
    private static double fromNoon(ServerWorld w) {
        double h = ((w.getTimeOfDay() % 24000) / 1000.0 + 6) % 24;
        return (h - 12 + 24) % 24;
    }

    private double homeAt() {
        return (c.isKid() ? 6.5 : 7) + c.personal() * (c.isKid() ? 1 : 1.5);   // 18:30–20:30
    }

    private double bedAt() {
        return c.isKid() ? 9 + c.personal() * 0.5 : 10.5 + c.personal() * 1.5; // 21:00–00:00
    }

    private double wakeAt() {
        return c.isKid() ? 18.3 + c.personal() * 0.5 : 17 + c.personal() * 2;  // 05:00–07:00
    }

    private boolean night(ServerWorld w) {
        double m = fromNoon(w);
        return m >= homeAt() && m < wakeAt();
    }

    private boolean bedtime(ServerWorld w) {
        double m = fromNoon(w);
        return m >= bedAt() && m < wakeAt();
    }

    // ---------------------------------------------------------------- цикл
    @Override
    public boolean canStart() {
        if (!(c.getWorld() instanceof ServerWorld w) || !c.livesInTown()) return false;
        if (c.hidden || c.getActivity() == CitizenEntity.Activity.SLEEP) return locate(w);
        if (c.getRandom().nextInt(10) != 0 || !night(w)) return false;
        return locate(w);
    }

    /** Находит дверь своего дома и точки снаружи/внутри. */
    private boolean locate(ServerWorld w) {
        double[] p = TownState.get(w).place(c.home() + "_door");
        if (p == null) return false;
        double rad = Math.toRadians(p[3]);
        double ix = -Math.sin(rad), iz = Math.cos(rad);
        outside = new Vec3d(p[0], p[1], p[2]);
        door = null;
        BlockPos guess = BlockPos.ofFloored(p[0] + ix * 2, p[1], p[2] + iz * 2);
        for (BlockPos b : BlockPos.iterate(guess.add(-1, -1, -1), guess.add(1, 1, 1)))
            if (w.getBlockState(b).getBlock() instanceof DoorBlock && w.getBlockState(b).get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
                door = b.toImmutable();
                break;
            }
        if (door == null) return false;
        inside = Vec3d.ofBottomCenter(door).add(ix * 1.6, 0, iz * 1.6);
        return true;
    }

    @Override
    public void start() {
        c.homeActive = true;
        timer = 0;
        stuck = 0;
        if (c.getActivity() == CitizenEntity.Activity.SLEEP) {
            spot = claimed();
            if (spot == null || !findLie((ServerWorld) c.getWorld(), spot)) wakeUp();
            else phase = Phase.SLEEP;
        } else if (c.hidden) {
            phase = Phase.EVENING;
        } else {
            phase = Phase.TO_DOOR;
            c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean shouldContinue() {
        return phase != Phase.OUT && c.livesInTown();
    }

    @Override
    public void stop() {
        c.homeActive = false;
        if (c.getActivity() == CitizenEntity.Activity.SLEEP) c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
        c.setNoGravity(false);
        if (c.hidden) show();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    private Phase logged;

    @Override
    public void tick() {
        ServerWorld w = (ServerWorld) c.getWorld();
        timer++;
        if (phase != logged && Boolean.getBoolean("lifecraft.debughome")) {
            com.yompix.lifecraft.LifeCraft.LOG.info("[homegoal] {} {} -> {} spot {} at {}", c.getName().getString(), logged, phase, spot, c.getBlockPos());
            logged = phase;
        }
        switch (phase) {
            case TO_DOOR -> {
                if (!night(w)) { phase = Phase.OUT; return; }
                if (walk(outside, 0.7, 2.0)) {
                    setDoor(w, true);
                    phase = Phase.ENTER;
                    timer = 0;
                }
            }
            case ENTER -> {
                if (walk(inside, 0.6, 0.8) || timer > 120) {
                    if (timer > 120) c.refreshPositionAndAngles(inside.x, inside.y, inside.z, c.getYaw(), 0);
                    setDoor(w, false);
                    spot = claim(w);
                    phase = Phase.EVENING;
                    timer = 0;
                    if (spot == null) hide();
                    else c.setActivity(CitizenEntity.Activity.THINK, ItemStack.EMPTY);
                }
            }
            case EVENING -> {
                if (!night(w)) { morning(w); return; }
                if (c.hidden) {
                    c.setPosition(inside.x, inside.y, inside.z);
                    return;
                }
                // вечер дома: посидеть в телефоне у телевизора, пока не пора спать
                if (timer == 1) {
                    double[] liv = TownState.get(w).place(c.home() + "_living");
                    if (liv != null) c.getNavigation().startMovingTo(liv[0] + (spot.equals("bed") ? -1 : 1), liv[1], liv[2], 0.5);
                    c.setActivity(CitizenEntity.Activity.PHONE, new ItemStack(com.yompix.lifecraft.ModContent.PHONE));
                }
                if (bedtime(w) && findLie(w, spot)) {
                    phase = Phase.TO_BED;
                    timer = 0;
                    c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
                }
            }
            case TO_BED -> {
                if (walk(stand, 0.5, 1.0) || timer > 200) {
                    phase = Phase.SLEEP;
                    c.setActivity(CitizenEntity.Activity.SLEEP, ItemStack.EMPTY);
                }
            }
            case SLEEP -> {
                c.getNavigation().stop();
                c.setNoGravity(true);
                c.setVelocity(Vec3d.ZERO);
                c.refreshPositionAndAngles(lie.x, lie.y, lie.z, lieYaw, 0);
                c.setBodyYaw(lieYaw);
                c.setHeadYaw(lieYaw);
                if (!bedtime(w)) wakeUp();
            }
            case STRETCH -> {
                c.getNavigation().stop();
                if (timer > 50) {
                    phase = Phase.BREAKFAST;
                    timer = 0;
                    double[] k = TownState.get(w).place(c.home() + "_kitchen");
                    if (k != null) c.getNavigation().startMovingTo(k[0], k[1], k[2], 0.5);
                    c.setActivity(CitizenEntity.Activity.IDLE, new ItemStack(c.getRandom().nextBoolean() ? com.yompix.lifecraft.ModContent.BURGER : com.yompix.lifecraft.ModContent.COLA));
                }
            }
            case BREAKFAST -> {
                if (timer > 220) {
                    c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
                    phase = Phase.EXIT;
                    timer = 0;
                }
            }
            case EXIT -> {
                if (timer == 1 || c.getNavigation().isIdle()) c.getNavigation().startMovingTo(inside.x, inside.y, inside.z, 0.6);
                if (c.squaredDistanceTo(inside) < 1.2 || timer > 160) {
                    if (timer > 160) c.refreshPositionAndAngles(inside.x, inside.y, inside.z, c.getYaw(), 0);
                    setDoor(w, true);
                    phase = Phase.LEAVE;
                    timer = 0;
                }
            }
            case LEAVE -> {
                // вышел на крыльцо — прикрыл дверь; дальше обычный распорядок дня
                if (walk(outside, 0.7, 1.5) || timer > 140) {
                    setDoor(w, false);
                    release();
                    phase = Phase.OUT;
                }
            }
            case OUT -> { }
        }
    }

    // ---------------------------------------------------------------- утро
    private void morning(ServerWorld w) {
        if (c.hidden) {
            show();
            c.refreshPositionAndAngles(inside.x, inside.y, inside.z, c.getYaw(), 0);
            c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
            phase = Phase.EXIT;
        } else {
            phase = Phase.BREAKFAST;
            double[] k = TownState.get(w).place(c.home() + "_kitchen");
            if (k != null) c.getNavigation().startMovingTo(k[0], k[1], k[2], 0.5);
            c.setActivity(CitizenEntity.Activity.IDLE, new ItemStack(com.yompix.lifecraft.ModContent.COLA));
        }
        timer = 0;
    }

    private void wakeUp() {
        c.setNoGravity(false);
        // встаёт рядом с кроватью и потягивается
        if (stand != null) c.refreshPositionAndAngles(stand.x, stand.y, stand.z, lieYaw, 0);
        c.setActivity(CitizenEntity.Activity.CHEER, ItemStack.EMPTY);
        phase = Phase.STRETCH;
        timer = 0;
    }

    // ---------------------------------------------------------------- мелочи
    /** Идёт к точке; true — дошёл. Если застрял — пробует снова, потом подходит «вплотную». */
    private boolean walk(Vec3d to, double speed, double near) {
        if (c.squaredDistanceTo(to.x, c.getY(), to.z) <= near * near) {
            c.getNavigation().stop();
            return true;
        }
        if (c.getNavigation().isIdle()) {
            if (!c.getNavigation().startMovingTo(to.x, to.y, to.z, speed) && ++stuck > 6) {
                c.refreshPositionAndAngles(to.x, to.y, to.z, c.getYaw(), 0);
                stuck = 0;
            }
        }
        return false;
    }

    private void setDoor(ServerWorld w, boolean open) {
        if (door == null) return;
        BlockState s = w.getBlockState(door);
        if (s.getBlock() instanceof DoorBlock db && s.get(DoorBlock.OPEN) != open) db.setOpen(c, w, s, door, open);
    }

    private String key(String spot) {
        return c.home() + ":" + spot;
    }

    @Nullable
    private String claimed() {
        for (String s : new String[]{"bed", "sofa"}) if (c.getUuid().equals(CLAIMS.get(key(s)))) return s;
        if (c.getActivity() == CitizenEntity.Activity.SLEEP) {
            // после перезагрузки мира — занимаем место снова
            for (String s : new String[]{"bed", "sofa"})
                if (CLAIMS.get(key(s)) == null) { CLAIMS.put(key(s), c.getUuid()); return s; }
        }
        return null;
    }

    /** Кровать или диван, если свободны (место освобождается, если хозяин пропал). */
    @Nullable
    private String claim(ServerWorld w) {
        for (String s : new String[]{"bed", "sofa"}) {
            UUID who = CLAIMS.get(key(s));
            if (who == null || who.equals(c.getUuid()) || !(w.getEntity(who) instanceof CitizenEntity o) || !o.homeActive) {
                CLAIMS.put(key(s), c.getUuid());
                return s;
            }
        }
        return null;
    }

    private void release() {
        CLAIMS.values().removeIf(u -> u.equals(c.getUuid()));
    }

    private void hide() {
        c.hidden = true;
        c.setInvisible(true);
        c.getNavigation().stop();
        c.setActivity(CitizenEntity.Activity.IDLE, ItemStack.EMPTY);
    }

    private void show() {
        c.hidden = false;
        c.setInvisible(false);
    }

    /**
     * Где лечь: на кровать (ноги у изножья, голова на подушке) или вдоль дивана.
     * Модель «спит» на спине головой назад, поэтому житель смотрит от подушки.
     */
    private boolean findLie(ServerWorld w, String spot) {
        if (spot == null) return false;
        double[] p = TownState.get(w).place(c.home() + (spot.equals("bed") ? "_bed" : "_living"));
        if (p == null) return false;
        BlockPos at = BlockPos.ofFloored(p[0], p[1], p[2]);
        stand = new Vec3d(p[0], p[1], p[2]);
        int r = spot.equals("bed") ? 2 : 4;
        for (BlockPos b : BlockPos.iterate(at.add(-r, -1, -r), at.add(r, 1, r))) {
            BlockState s = w.getBlockState(b);
            if (spot.equals("bed") && s.getBlock() instanceof BedBlock && s.get(BedBlock.PART) == BedPart.FOOT) {
                Direction d = s.get(BedBlock.FACING);
                lay(Vec3d.ofBottomCenter(b), d, 0.5625);
                return true;
            }
            if (spot.equals("sofa") && s.getBlock() instanceof StairsBlock) {
                for (Direction d : Direction.Type.HORIZONTAL) {
                    BlockState n = w.getBlockState(b.offset(d));
                    if (n.getBlock() instanceof StairsBlock && d.getAxis() != s.get(StairsBlock.FACING).getAxis()) {
                        Direction back = s.get(StairsBlock.FACING);
                        lay(Vec3d.ofBottomCenter(b).add(-back.getOffsetX() * 0.15, 0, -back.getOffsetZ() * 0.15), d, 0.5);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void lay(Vec3d footCenter, Direction toHead, double height) {
        lie = footCenter.add(-toHead.getOffsetX() * 0.45, height, -toHead.getOffsetZ() * 0.45);
        // смотрит в сторону изножья: yaw для направления (fx, fz) = atan2(-fx, fz)
        lieYaw = (float) Math.toDegrees(Math.atan2(toHead.getOffsetX(), -toHead.getOffsetZ()));
    }
}
