package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Подработки за рулём, как в GTA SA (клавиша J, продолжение такси и патруля из Activities):
 *  • СКОРАЯ — белый фургон у больницы: подбирай раненых и вези в больницу на время;
 *  • ПОЖАРНЫЕ — красный грузовик: туши горящие машины водой из брандспойта (ПКМ);
 *  • ПИЦЦА — любой скутер или мотоцикл: развози пиццу из «Мама Мия» по адресам.
 * Уровни растут, за каждый — чистые деньги на карту. Пройдёшь 12-й уровень — навсегда умение:
 * скорая → здоровье само восстанавливается, пожарные → огонь не страшен, пицца → +4 сердца.
 */
public final class VehicleJobs {
    private VehicleJobs() {}

    /** Цвета служебных машин (по ним машина узнаётся и после перезахода). */
    public static final int AMB_COLOR = 0xF5F7FA, FIRE_COLOR = 0xC4161C;
    public static final int MAX_LEVEL = 12;

    public enum Kind {
        AMBULANCE("ambulance", "СКОРАЯ", "medic"), FIRE("fire", "ПОЖАРНЫЕ", "firefighter"), PIZZA("pizza", "ДОСТАВКА ПИЦЦЫ", "pizzaman");
        final String id, title, perk;

        Kind(String id, String title, String perk) {
            this.id = id;
            this.title = title;
            this.perk = perk;
        }
    }

    private static final class Fire {
        Vec3d at;
        @Nullable UUID car;
        float heat = 100;
    }

    private static final class Job {
        Kind kind;
        int level = 1, timer, done, outOfCar, stage;
        final List<UUID> people = new ArrayList<>();
        final List<Fire> fires = new ArrayList<>();
        @Nullable Vec3d dest;
        int hose;
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    public static boolean busy(ServerPlayerEntity p) {
        return JOBS.containsKey(p.getUuid());
    }

    @Nullable
    public static Kind kindOf(CarEntity car) {
        if (car.isScooter() || car.isBike()) return Kind.PIZZA;
        if (car.getCommandTags().contains("nv_ambulance") || car.getVariant() == 4 && car.color() == AMB_COLOR || car.getVariant() == CarEntity.AMBULANCE) return Kind.AMBULANCE;
        if (car.getCommandTags().contains("nv_firetruck") || car.getVariant() == 11 && car.color() == FIRE_COLOR || car.getVariant() == CarEntity.FIRETRUCK) return Kind.FIRE;
        return null;
    }

    /** Из Activities.toggle: true — клавиша J обработана здесь. */
    public static boolean toggle(ServerPlayerEntity p) {
        Job j = JOBS.get(p.getUuid());
        if (j != null) {
            stop(p, "Подработка окончена.");
            return true;
        }
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) return false;
        Kind k = kindOf(car);
        if (k == null) return false;
        if (FunUtil.busy(p)) {
            FunUtil.hint(p, "Сначала закончи текущее дело", Formatting.YELLOW);
            return true;
        }
        j = new Job();
        j.kind = k;
        JOBS.put(p.getUuid(), j);
        if (k != Kind.PIZZA) car.toggleLight(CarEntity.HAZARD); // «мигалка»
        LifeData d = Life.get(p);
        int best = FunUtil.getInt(d, "vjob:" + k.id);
        FunUtil.title(p, k.title, switch (k) {
            case AMBULANCE -> "Вози раненых в больницу · J — закончить";
            case FIRE -> "Туши пожары: ПКМ — брандспойт · J — закончить";
            case PIZZA -> "Развози пиццу по адресам · J — закончить";
        } + (best > 0 ? " · рекорд: уровень " + best : ""));
        hud(p, j);
        next(p, j);
        return true;
    }

    private static void stop(ServerPlayerEntity p, String why) {
        Job j = JOBS.remove(p.getUuid());
        if (j == null) return;
        ServerWorld w = p.getServerWorld();
        for (UUID u : j.people) if (w.getEntity(u) instanceof CitizenEntity c) {
            c.stopRiding();
            c.lockActivity(null);
            c.removeLater(100);
        }
        for (Fire f : j.fires) if (f.car != null && w.getEntity(f.car) instanceof CarEntity c) c.setAmbient(true);
        if (p.getVehicle() instanceof CarEntity car && car.light(CarEntity.HAZARD) && j.kind != Kind.PIZZA) car.toggleLight(CarEntity.HAZARD);
        FunUtil.clearHud(p);
        LifeNet.send(p, "vjob", new NbtCompound());
        if (!why.isEmpty()) p.sendMessage(Text.literal(why + " Уровень: " + j.level).formatted(Formatting.GOLD), true);
    }

    public static void onDownfall(ServerPlayerEntity p) {
        if (JOBS.containsKey(p.getUuid())) stop(p, "Подработка сорвана.");
    }

    public static void onLeave(ServerPlayerEntity p) {
        stop(p, "");
    }

    private static void hud(ServerPlayerEntity p, Job j) {
        NbtCompound n = new NbtCompound();
        n.putString("kind", j.kind.id);
        n.putInt("level", j.level);
        String what = switch (j.kind) {
            case AMBULANCE -> "раненых " + j.done + "/" + j.level;
            case FIRE -> "пожаров " + j.fires.stream().filter(f -> f.heat > 0).count() + " горит";
            case PIZZA -> "пицц " + j.done + "/" + j.level;
        };
        n.putString("text", j.kind.title + " · уровень " + j.level + " · " + what);
        LifeNet.send(p, "vjob", n);
    }

    // ------------------------------------------------------------------ новый заказ
    private static void next(ServerPlayerEntity p, Job j) {
        ServerWorld w = p.getServerWorld();
        switch (j.kind) {
            case AMBULANCE -> {
                int k = Math.min(3, j.level - j.done);
                double sum = 0;
                for (int i = 0; i < k; i++) {
                    Vec3d at = FunUtil.curbAround(p, 60 + i * 30, 160 + j.level * 10);
                    if (at == null) at = FunUtil.curb(w, p.getX() + 40, p.getZ());
                    CitizenEntity c = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
                    c.setStationary(true);
                    c.lockActivity(CitizenEntity.Activity.SLEEP);
                    c.setCustomName(Text.literal("✚ Раненый"));
                    c.setCustomNameVisible(true);
                    c.setGlowing(true);
                    j.people.add(c.getUuid());
                    sum += Math.sqrt(at.squaredDistanceTo(p.getPos()));
                }
                Vec3d hosp = hospital(w);
                j.timer += (int) (20 * (25 + sum / 6 + (hosp == null ? 60 : Math.sqrt(hosp.squaredDistanceTo(p.getPos())) / 6)));
                j.stage = 0;
                FunUtil.hint(p, "Уровень " + j.level + ": раненых " + k + " — голубая метка на радаре", Formatting.AQUA);
            }
            case FIRE -> {
                Vec3d at = FunUtil.curbAround(p, 90, 220);
                if (at == null) at = FunUtil.curb(w, p.getX() + 80, p.getZ());
                Vec3d base = FunUtil.road(w, at.x, at.z, w.getRandom().nextBoolean());
                int n = Math.min(6, 1 + (j.level + 1) / 2);
                var r = w.getRandom();
                int[] vars = {0, 1, 3, 4, 7, 8, 9};
                for (int i = 0; i < n; i++) {
                    Vec3d v = base.add(r.nextDouble() * 14 - 7, 0, r.nextDouble() * 14 - 7);
                    v = new Vec3d(v.x, Story.topY(w, (int) Math.floor(v.x), (int) Math.floor(v.z)), v.z);
                    Fire f = new Fire();
                    f.at = v;
                    CarEntity car = FunUtil.car(w, v, r.nextFloat() * 360, vars[r.nextInt(vars.length)]);
                    car.hitByBullet(36, null);
                    car.addCommandTag("nv_burning");
                    f.car = car.getUuid();
                    j.fires.add(f);
                }
                j.dest = base;
                j.timer = 20 * (int) (35 + Math.sqrt(base.squaredDistanceTo(p.getPos())) / 5 + n * 14);
                FunUtil.hint(p, "Уровень " + j.level + ": горят машины (" + n + ") — голубая метка", Formatting.RED);
            }
            case PIZZA -> {
                Vec3d shop = Story.place(w, "pizza_door");
                if (j.stage == 0 && shop != null && shop.squaredDistanceTo(p.getPos()) > 40 * 40) {
                    j.dest = shop;
                    j.timer = 20 * (int) (40 + Math.sqrt(shop.squaredDistanceTo(p.getPos())) / 6);
                    FunUtil.hint(p, "Заскочи в пиццерию «Мама Мия» за пиццами", Formatting.GOLD);
                    j.stage = -1; // едем за пиццей
                    return;
                }
                j.stage = 1;
                address(p, j);
            }
        }
        hud(p, j);
    }

    private static void address(ServerPlayerEntity p, Job j) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = FunUtil.curbAround(p, 50, 150 + j.level * 8);
        if (at == null) at = FunUtil.curb(w, p.getX() - 60, p.getZ());
        CitizenEntity c = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
        c.setStationary(true);
        c.lockActivity(CitizenEntity.Activity.WAVE);
        c.setCustomName(Text.literal("Заказ"));
        c.setCustomNameVisible(true);
        j.people.clear();
        j.people.add(c.getUuid());
        j.dest = at;
        j.timer += (int) (20 * (18 + Math.sqrt(at.squaredDistanceTo(p.getPos())) / 6.5));
    }

    @Nullable
    private static Vec3d hospital(ServerWorld w) {
        Vec3d h = Story.place(w, "hospital");
        return h != null ? h : Story.place(w, "police_station");
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            Job j = JOBS.get(p.getUuid());
            if (j != null && t % 5 == 1) {
                try {
                    tickJob(p, j);
                } catch (RuntimeException ex) {
                    com.yompix.novograd.Novograd.LOG.warn("vehicle job", ex);
                    stop(p, "Подработка прервана.");
                }
            }
            if (j != null && j.kind == Kind.FIRE && j.hose > 0) hoseTick(p, j);
            if (t % 40 == 9) depots(p);
            if (t % 80 == 21) perks(p, Life.get(p));
        }
    }

    private static void tickJob(ServerPlayerEntity p, Job j) {
        ServerWorld w = p.getServerWorld();
        CarEntity car = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p ? c : null;
        boolean right = car != null && kindOf(car) == j.kind;
        j.outOfCar = right ? 0 : j.outOfCar + 5;
        if (j.outOfCar > 20 * 12) {
            stop(p, "Ты бросил машину — подработка окончена.");
            return;
        }
        if (car != null && car.wrecked()) {
            stop(p, "Машина разбита — подработка окончена.");
            return;
        }
        j.timer -= 5;
        if (j.timer % 20 == 0) FunUtil.timer(p, Math.max(0, j.timer / 20));
        if (j.timer <= 0) {
            if (j.kind == Kind.FIRE) for (Fire f : j.fires) if (f.heat > 0 && f.car != null && w.getEntity(f.car) instanceof CarEntity c) c.hitByBullet(999, null);
            stop(p, j.kind == Kind.FIRE ? "Пожар не потушен — машины взорвались!" : "Время вышло!");
            return;
        }
        switch (j.kind) {
            case AMBULANCE -> tickAmbulance(p, j, car);
            case FIRE -> tickFire(p, j);
            case PIZZA -> tickPizza(p, j, car);
        }
    }

    private static void tickAmbulance(ServerPlayerEntity p, Job j, @Nullable CarEntity car) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity nearest = null;
        double best = 1e18;
        int riding = 0;
        for (UUID u : j.people) {
            if (!(w.getEntity(u) instanceof CitizenEntity c) || !c.isAlive()) continue;
            if (c.hasVehicle()) {
                riding++;
                continue;
            }
            double d = c.squaredDistanceTo(p);
            if (d < best) {
                best = d;
                nearest = c;
            }
            if (car != null && kindOf(car) == Kind.AMBULANCE && car.squaredDistanceTo(c) < 7 * 7 && car.slow() && car.getPassengerList().size() < 4) {
                c.lockActivity(null);
                c.setStationary(false);
                c.setGlowing(false);
                c.startRiding(car, true);
                j.timer += 20 * 6;
                w.playSound(null, car.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.5f, 1.6f);
                FunUtil.hint(p, "✚ Раненый в машине · +6 с", Formatting.AQUA);
            }
        }
        Vec3d hosp = hospital(w);
        if (nearest != null) FunUtil.target(p, nearest.getPos(), "Раненый");
        else if (riding > 0 && hosp != null) {
            FunUtil.target(p, hosp, "Больница");
            if (car != null && car.getPos().squaredDistanceTo(hosp.x, car.getY(), hosp.z) < 16 * 16 && car.slow()) {
                for (UUID u : j.people) if (w.getEntity(u) instanceof CitizenEntity c && c.hasVehicle()) {
                    c.stopRiding();
                    c.removeLater(60);
                    j.done++;
                }
                j.people.clear();
                w.spawnParticles(ParticleTypes.HEART, car.getX(), car.getY() + 2, car.getZ(), 8, 1, 0.5, 1, 0);
                if (j.done >= j.level) levelDone(p, j, 70 * j.level + j.timer / 40);
                else next(p, j);
                hud(p, j);
            }
        } else if (riding == 0 && nearest == null) next(p, j); // раненых не осталось (убежали / пропали)
    }

    private static void tickFire(ServerPlayerEntity p, Job j) {
        ServerWorld w = p.getServerWorld();
        int burning = 0;
        Fire near = null;
        double best = 1e18;
        for (Fire f : j.fires) {
            if (f.heat <= 0) continue;
            burning++;
            CarEntity c = f.car == null ? null : w.getEntity(f.car) instanceof CarEntity cc ? cc : null;
            if (c != null) f.at = c.getPos();
            f.heat = Math.min(100, f.heat + 0.4f); // огонь разгорается снова
            int n = 2 + (int) (f.heat / 20);
            w.spawnParticles(ParticleTypes.FLAME, f.at.x, f.at.y + 1.2, f.at.z, n, 0.9, 0.5, 0.9, 0.03);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, f.at.x, f.at.y + 2.2, f.at.z, 1 + n / 3, 0.6, 0.4, 0.6, 0.04);
            if (w.getRandom().nextInt(4) == 0) w.spawnParticles(ParticleTypes.LAVA, f.at.x, f.at.y + 1, f.at.z, 1, 0.5, 0.3, 0.5, 0);
            for (ServerPlayerEntity o : w.getPlayers()) // дым видно издалека
                if (o.squaredDistanceTo(f.at) < 300 * 300 && w.getTime() % 10 == 1)
                    w.spawnParticles(o, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, f.at.x, f.at.y + 2, f.at.z, 1, 0.3, 0.2, 0.3, 0.01);
            if (w.getTime() % 40 == 1) w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(f.at), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.5f, 1f);
            double d = f.at.squaredDistanceTo(p.getPos());
            if (d < best) {
                best = d;
                near = f;
            }
        }
        if (near != null) FunUtil.target(p, near.at, "Пожар (" + burning + ")");
        if (burning == 0) {
            Vec3d at = j.dest != null ? j.dest : p.getPos();
            Island.Zone zn = Island.zone((int) at.x, (int) at.z);
            if (zn == Island.Zone.RIVIERA) Economy.shock(p.getServer(), "RIVH", 0.03, "пожарные спасли квартал отелей");
            else if (zn == Island.Zone.PORT || zn == Island.Zone.INDUSTRIAL && w.getRandom().nextInt(3) == 0)
                Economy.shock(p.getServer(), "PORT", 0.03, "пожар у складов потушен вовремя");
            for (Fire f : j.fires) if (f.car != null && w.getEntity(f.car) instanceof CarEntity c) c.setAmbient(true);
            j.fires.clear();
            levelDone(p, j, 90 * j.level + j.timer / 40);
        }
    }

    /** Брандспойт: клиент держит ПКМ в пожарной машине — вода летит туда, куда смотрит игрок. */
    public static void hose(ServerPlayerEntity p, boolean on) {
        Job j = JOBS.get(p.getUuid());
        if (j == null || j.kind != Kind.FIRE || !(p.getVehicle() instanceof CarEntity car) || kindOf(car) != Kind.FIRE) return;
        j.hose = on ? 8 : 0; // клиент повторяет пакет, пока кнопка зажата
    }

    private static void hoseTick(ServerPlayerEntity p, Job j) {
        j.hose--;
        if (!(p.getVehicle() instanceof CarEntity car)) return;
        ServerWorld w = p.getServerWorld();
        Vec3d from = car.getPos().add(0, 2.6, 0);
        Vec3d dir = p.getRotationVec(1);
        if (w.getTime() % 2 == 0)
            for (int i = 2; i < 22; i += 2) {
                Vec3d pt = from.add(dir.multiply(i)).add(0, -0.004 * i * i, 0);
                w.spawnParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 3, 0.15, 0.15, 0.15, 0.1);
                if (i % 6 == 0) w.spawnParticles(ParticleTypes.FALLING_WATER, pt.x, pt.y, pt.z, 2, 0.2, 0.2, 0.2, 0);
            }
        if (w.getTime() % 8 == 0) w.playSound(null, car.getBlockPos(), SoundEvents.WEATHER_RAIN, SoundCategory.NEUTRAL, 0.6f, 1.6f);
        com.yompix.novograd.world.Fires.douse(w, from, dir, p); // уличные пожары (урны, киоски, машины после взрывов)
        for (Fire f : j.fires) {
            if (f.heat <= 0) continue;
            Vec3d to = f.at.add(0, 1, 0).subtract(from);
            double along = to.dotProduct(dir);
            if (along < 0 || along > 24) continue;
            double off = to.subtract(dir.multiply(along)).length();
            if (off > 3.2) continue;
            f.heat -= 3.2f;
            w.spawnParticles(ParticleTypes.CLOUD, f.at.x, f.at.y + 1.5, f.at.z, 2, 0.6, 0.3, 0.6, 0.02);
            if (f.heat <= 0) {
                w.spawnParticles(ParticleTypes.POOF, f.at.x, f.at.y + 1, f.at.z, 30, 1, 0.6, 1, 0.05);
                w.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(f.at), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.2f, 1f);
                if (f.car != null && w.getEntity(f.car) instanceof CarEntity c) c.paint(0x2A2522);
                FunUtil.hint(p, "Потушено!", Formatting.AQUA);
                hud(p, j);
            }
        }
        // струя сбивает с ног прохожих и бандитов
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, car.getBoundingBox().expand(20), c -> c.isAlive() && !c.hasVehicle())) {
            Vec3d to = c.getPos().add(0, 1, 0).subtract(from);
            double along = to.dotProduct(dir);
            if (along < 1 || along > 20 || to.subtract(dir.multiply(along)).length() > 1.6) continue;
            c.addVelocity(dir.x * 0.25, 0.12, dir.z * 0.25);
            c.velocityModified = true;
        }
    }

    private static void tickPizza(ServerPlayerEntity p, Job j, @Nullable CarEntity car) {
        ServerWorld w = p.getServerWorld();
        if (j.stage == -1) {
            FunUtil.target(p, j.dest, "Пиццерия");
            if (j.dest != null && p.getPos().squaredDistanceTo(j.dest.x, p.getY(), j.dest.z) < 12 * 12) {
                j.stage = 1;
                j.timer = 0;
                w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 1f, 1f);
                FunUtil.hint(p, "Пиццы в коробе: " + (j.level - j.done) + " — вперёд по адресам!", Formatting.GOLD);
                address(p, j);
                hud(p, j);
            }
            return;
        }
        CitizenEntity c = j.people.isEmpty() ? null : w.getEntity(j.people.get(0)) instanceof CitizenEntity cc && cc.isAlive() ? cc : null;
        if (c == null) {
            address(p, j);
            return;
        }
        FunUtil.target(p, c.getPos(), "Заказ");
        if (car != null && car.squaredDistanceTo(c) < 6 * 6 && car.slow()) {
            c.lockActivity(CitizenEntity.Activity.CHEER);
            c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PIZZA));
            c.removeLater(160);
            c.setCustomName(Text.literal("Спасибо!"));
            j.people.clear();
            j.done++;
            int tip = Math.max(0, j.timer / 60);
            Life.earn(p, 15 + tip, "пицца доставлена" + (tip > 0 ? " (чаевые $" + tip + ")" : ""));
            if (j.done >= j.level) {
                levelDone(p, j, 45 * j.level);
                return;
            }
            address(p, j);
            hud(p, j);
        }
    }

    private static void levelDone(ServerPlayerEntity p, Job j, int pay) {
        LifeData d = Life.get(p);
        Life.earn(p, pay, j.kind.title.toLowerCase(Locale.ROOT) + ": уровень " + j.level);
        FunUtil.title(p, "УРОВЕНЬ " + j.level + " ПРОЙДЕН", j.kind.title + " · +$" + pay);
        int best = FunUtil.getInt(d, "vjob:" + j.kind.id);
        if (j.level > best) FunUtil.setInt(d, "vjob:" + j.kind.id, j.level);
        if (j.level == MAX_LEVEL && d.flags.add("perk:" + j.kind.perk)) {
            String perk = switch (j.kind) {
                case AMBULANCE -> "Здоровье теперь само восстанавливается";
                case FIRE -> "Огонь тебе больше не страшен";
                case PIZZA -> "+4 сердца к здоровью";
            };
            Achievements.give(p, j.kind.id + "12");
            FunUtil.title(p, "УМЕНИЕ ОТКРЫТО!", perk);
            Life.message(p, "Новоград-гид", j.kind.title + ": 12 уровней пройдено! " + perk + ".");
            perks(p, d);
        }
        j.level++;
        j.done = 0;
        j.stage = 0;
        j.timer = Math.max(j.timer, 0) / 2;
        Life.sync(p);
        next(p, j);
    }

    // ------------------------------------------------------------------ умения за 12 уровней
    private static final net.minecraft.util.Identifier PIZZA_HP = com.yompix.novograd.Novograd.id("pizza_perk");

    private static void perks(ServerPlayerEntity p, LifeData d) {
        if (d.flags.contains("perk:medic") && p.getHealth() < p.getMaxHealth() && p.age - p.getLastAttackedTime() > 100 && !p.isDead())
            p.heal(1f);
        if (d.flags.contains("perk:firefighter") && !p.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) || d.flags.contains("perk:firefighter") && p.getStatusEffect(StatusEffects.FIRE_RESISTANCE).getDuration() < 200)
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60, 0, true, false, true));
        var hp = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null && d.flags.contains("perk:pizzaman") && !hp.hasModifier(PIZZA_HP))
            hp.addPersistentModifier(new EntityAttributeModifier(PIZZA_HP, 8, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    // ------------------------------------------------------------------ служебные машины у больницы и пиццерии
    private static final String[][] DEPOTS = {{"hospital+9,4", "ambulance"}, {"hospital+-9,4", "fire"}, {"police_station+10,6", "fire"}, {"pizza_door+5,5", "pizza"}};

    private static void depots(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        for (String[] dp : DEPOTS) {
            Vec3d at = Story.place(w, dp[0]);
            if (at == null || p.squaredDistanceTo(at) > 90 * 90 || p.squaredDistanceTo(at) < 24 * 24) continue;
            var box = new net.minecraft.util.math.Box(at, at).expand(14, 6, 14);
            Kind want = dp[1].equals("ambulance") ? Kind.AMBULANCE : dp[1].equals("fire") ? Kind.FIRE : Kind.PIZZA;
            if (!w.getEntitiesByClass(CarEntity.class, box, c -> kindOf(c) == want).isEmpty()) continue;
            int y = Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z));
            if (!w.isAir(net.minecraft.util.math.BlockPos.ofFloored(at.x, y + 0.5, at.z))) continue;
            CarEntity car;
            if (want == Kind.PIZZA) {
                car = new CarEntity(ModContent.SCOOTER, w);
                car.refreshPositionAndAngles(at.x, y, at.z, Story.placeYaw(w, dp[0]), 0);
                car.paint(0xD32F2F);
                w.spawnEntity(car);
            } else {
                car = FunUtil.car(w, new Vec3d(at.x, y, at.z), Story.placeYaw(w, dp[0]), want == Kind.AMBULANCE ? CarEntity.AMBULANCE : CarEntity.FIRETRUCK); // 0.4: свои кузова скорой и пожарной
                car.paint(want == Kind.AMBULANCE ? AMB_COLOR : FIRE_COLOR);
                car.addCommandTag(want == Kind.AMBULANCE ? "nv_ambulance" : "nv_firetruck");
            }
        }
    }

    /** Текущее состояние для автотеста и подсказок. */
    @Nullable
    public static Kind active(ServerPlayerEntity p) {
        Job j = JOBS.get(p.getUuid());
        return j == null ? null : j.kind;
    }

    @SuppressWarnings("unused")
    private static ItemStack icon(Kind k) {
        return new ItemStack(k == Kind.PIZZA ? ModContent.PIZZA : k == Kind.FIRE ? Items.WATER_BUCKET : Items.RED_DYE);
    }
}
