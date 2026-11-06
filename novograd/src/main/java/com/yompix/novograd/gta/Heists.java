package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.ItemEntity;
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
 * Свободная игра без миссий, как в GTA:
 *  • ограбление банка — зайди с оружием к хранилищу, продержись, пока вскрывают сейф, и уйди от полиции;
 *  • налёт на магазин — целься в продавца, он отдаст кассу;
 *  • инкассатор — иногда по городу едет бронефургон, разбей его и забери деньги;
 *  • бизнесы — купи клуб, бар, отель: каждое утро приносят доход;
 *  • бойни — подбери череп банды и успей уложить десяток бандитов за минуту.
 */
public final class Heists {
    private Heists() {}

    // ------------------------------------------------------------------ банки
    /** Название, место, награда от, до. */
    private static final Object[][] BANKS = {
            {"Банк «Новоград-Кредит»", "bank_door", 12000, 20000},
            {"Башня Сити-Банка", "bank_tower", 18000, 30000},
            {"Хранилище «Золотой короны»", "riv_casino", 30000, 50000},
    };

    private static final class Heist {
        int bank, stage, timer, aim;
        final List<UUID> spawned = new ArrayList<>();
    }

    private static final Map<UUID, Heist> HEISTS = new HashMap<>();
    private static final Map<UUID, Integer> NEAR = new HashMap<>();

    private static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000;
    }

    private static void tickBanks(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        Heist h = HEISTS.get(p.getUuid());
        if (h == null) {
            if (!Weapons.isGun(p.getMainHandStack()) || Story.active(p) && !Story.idle(d)) return;
            for (int i = 0; i < BANKS.length; i++) {
                Vec3d at = Story.place(w, (String) BANKS[i][1]);
                if (at == null || p.squaredDistanceTo(at) > 7 * 7) continue;
                String cd = "heist_cd:" + i;
                long last = d.flags.stream().filter(f -> f.startsWith(cd + "=")).mapToLong(f -> Long.parseLong(f.substring(cd.length() + 1))).max().orElse(-99);
                if (day(w) - last < 2) {
                    if (w.getTime() % 100 == 0) p.sendMessage(Text.literal("🏦 Хранилище пустое после прошлого налёта — приходи через пару дней").formatted(Formatting.GRAY), true);
                    return;
                }
                int n = NEAR.merge(p.getUuid(), 1, Integer::sum);
                if (n == 1) p.sendMessage(Text.literal("🏦 Стой с оружием у хранилища — начнётся ограбление…").formatted(Formatting.GOLD), true);
                if (n >= 6) { // ~3 секунды с оружием у двери
                    NEAR.remove(p.getUuid());
                    h = new Heist();
                    h.bank = i;
                    h.timer = 20 * 45;
                    HEISTS.put(p.getUuid(), h);
                    String flag = cd + "=";
                    d.flags.removeIf(f -> f.startsWith(flag));
                    d.flags.add(flag + day(w));
                    Activities.title(p, "ОГРАБЛЕНИЕ БАНКА", BANKS[i][0] + " · держи хранилище 45 с");
                    Wanted.add(p, 0, "ограбление банка", 3);
                    w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2f, 0.6f); // сигнализация
                }
                return;
            }
            NEAR.remove(p.getUuid());
            return;
        }
        Vec3d at = Story.place(w, (String) BANKS[h.bank][1]);
        if (at == null) {
            HEISTS.remove(p.getUuid());
            return;
        }
        if (h.stage == 0) {
            // вскрываем сейф: игрок держится рядом, охрана и полиция лезут волнами
            h.timer -= 10;
            LifeNet.send(p, "act_timer", sec(h.timer / 20));
            target(p, at, "Хранилище");
            if (w.getTime() % 40 == 0) w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 20, 0.6, 0.6, 0.6, 0.2);
            if (h.timer % (20 * 9) == 0) guards(p, at, 2 + h.bank, h);
            if (p.squaredDistanceTo(at) > 22 * 22) {
                fail(p, "Ты ушёл от хранилища — ограбление сорвано");
                return;
            }
            if (h.timer <= 0) {
                h.stage = 1;
                Activities.title(p, "СЕЙФ ВСКРЫТ", "Деньги у тебя! Оторвись от полиции");
                Wanted.add(p, 0, "ограбление банка", 4);
                target(p, null, "");
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.7f);
            }
        } else {
            // уходим от погони: розыск погас — деньги наши
            LifeNet.send(p, "act_timer", sec(-1));
            if (Wanted.stars(p) == 0) {
                int min = (int) BANKS[h.bank][2], max = (int) BANKS[h.bank][3];
                int pay = min + p.getRandom().nextInt(max - min + 1);
                Economy.dirty(p, pay, "ограбление банка");
                Economy.shock(p.getServer(), h.bank == 2 ? "KRON" : "CITY", h.bank == 2 ? -0.35 : -0.25, "ограблено хранилище: " + BANKS[h.bank][0]);
                if (h.bank == 2) Economy.shock(p.getServer(), "RIVH", -0.06, "туристы пугаются ограблений на Ривьере");
                Activities.title(p, "ОГРАБЛЕНИЕ УДАЛОСЬ", BANKS[h.bank][0] + " · +$" + pay);
                com.yompix.novograd.life.Achievements.give(p, "heist");
                Life.sync(p);
                end(p);
            }
        }
    }

    private static void guards(ServerPlayerEntity p, Vec3d at, int n, Heist h) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        for (int i = 0; i < n; i++) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, i == 0 ? "Охранник банка" : "Полицейский");
            double a = r.nextDouble() * Math.PI * 2;
            Vec3d v = at.add(Math.cos(a) * 14, 0, Math.sin(a) * 14);
            c.refreshPositionAndAngles(v.x, Story.topY(w, (int) v.x, (int) v.z), v.z, 0, 0);
            c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            c.removeWhenIdle();
            w.spawnEntity(c);
            c.attack(p);
            h.spawned.add(c.getUuid());
        }
    }

    private static void fail(ServerPlayerEntity p, String why) {
        Activities.title(p, "ОГРАБЛЕНИЕ ПРОВАЛЕНО", why);
        end(p);
    }

    private static void end(ServerPlayerEntity p) {
        HEISTS.remove(p.getUuid());
        target(p, null, "");
        LifeNet.send(p, "act_timer", sec(-1));
    }

    /** Погиб или задержан — сорвалось всё, что было в процессе. */
    public static void onDownfall(ServerPlayerEntity p, boolean busted) {
        Economy.onDownfall(p, busted);
        if (HEISTS.containsKey(p.getUuid())) fail(p, "Деньги потеряны");
        if (RAMPAGE.containsKey(p.getUuid())) {
            RAMPAGE.remove(p.getUuid());
            Activities.title(p, "БОЙНЯ ПРОВАЛЕНА", "");
        }
    }

    // ------------------------------------------------------------------ налёт на магазин
    private static final Map<UUID, Long> ROBBED = new HashMap<>();
    private static final Map<UUID, Integer> AIMING = new HashMap<>();

    private static void tickShops(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!Weapons.isGun(p.getMainHandStack())) {
            AIMING.remove(p.getUuid());
            return;
        }
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1);
        CitizenEntity clerk = null;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(7), c -> c.getRole() == CitizenEntity.Role.SHOPKEEPER || c.getRole() == CitizenEntity.Role.BANKER)) {
            Vec3d to = c.getEyePos().subtract(eye);
            if (to.normalize().dotProduct(look) > 0.96 && p.canSee(c)) clerk = c;
        }
        if (clerk == null) {
            AIMING.remove(p.getUuid());
            return;
        }
        long now = w.getTime();
        if (now - ROBBED.getOrDefault(clerk.getUuid(), -999999L) < 24000) {
            if (now % 60 == 0) p.sendMessage(Text.literal("«Касса пустая, правда! Вы же сегодня уже забрали всё!»").formatted(Formatting.GRAY), true);
            return;
        }
        int t = AIMING.merge(p.getUuid(), 5, Integer::sum);
        if (t == 5) {
            clerk.lockActivity(CitizenEntity.Activity.UPSET);
            clerk.face(p);
            p.sendMessage(Text.literal("«Не стреляйте! Сейчас, сейчас, всё отдам!» — держи прицел").formatted(Formatting.YELLOW), true);
        }
        if (t >= 70) { // 3.5 секунды под прицелом
            AIMING.remove(p.getUuid());
            ROBBED.put(clerk.getUuid(), now);
            int pay = 150 + p.getRandom().nextInt(450);
            ItemEntity bag = new ItemEntity(w, clerk.getX(), clerk.getY() + 1, clerk.getZ(), new ItemStack(Items.PAPER));
            bag.setPickupDelayInfinite();
            bag.setVelocity(p.getPos().subtract(clerk.getPos()).normalize().multiply(0.25).add(0, 0.25, 0));
            w.spawnEntity(bag);
            bag.setDespawnImmediately();
            Economy.dirty(p, pay, "налёт на магазин");
            Activities.title(p, "КАССА ВЗЯТА", "+$" + pay + " · полиция уже едет");
            com.yompix.novograd.life.Achievements.give(p, "holdup");
            Wanted.add(p, 0, "налёт на магазин", 2);
            clerk.lockActivity(null);
            Life.sync(p);
        }
    }

    // ------------------------------------------------------------------ инкассатор
    private static final Map<UUID, UUID> TRUCK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_TRUCK = new HashMap<>();

    private static void tickTruck(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        UUID id = TRUCK.get(p.getUuid());
        if (id == null) {
            long next = NEXT_TRUCK.computeIfAbsent(p.getUuid(), u -> now + 20 * 60 * 4);
            if (now < next || !Island.urban(Island.zone((int) p.getX(), (int) p.getZ())) || Story.active(p) && !Story.idle(Life.get(p))) return;
            NEXT_TRUCK.put(p.getUuid(), now + 20 * 60 * (6 + p.getRandom().nextInt(6)));
            // фургон на ближайшей улице в 60–90 блоках
            for (int tries = 0; tries < 12; tries++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                int x = snap(p.getX() + Math.cos(a) * 75), z = (int) (p.getZ() + Math.sin(a) * 75);
                if (!Island.urban(Island.zone(x, z))) continue;
                w.getChunk(x >> 4, z >> 4);
                CarEntity van = new CarEntity(ModContent.CAR, w);
                van.refreshPositionAndAngles(x + 0.5, Story.topY(w, x, z), z + 0.5, 0, 0);
                van.setVariant(4);
                van.setTune(false, 1, 2);
                van.paint(0x3A4A3A);
                w.spawnEntity(van);
                CitizenEntity drv = new CitizenEntity(ModContent.CITIZEN, w);
                drv.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.CITIZEN, false, "Инкассатор");
                drv.refreshPositionAndAngles(van.getX(), van.getY(), van.getZ(), 0, 0);
                w.spawnEntity(drv);
                drv.startRiding(van, true);
                List<Vec3d> route = new ArrayList<>();
                int cx = x, cz = snap(z);
                for (int k = 0; k < 10; k++) {
                    if (k % 2 == 0) cz += (p.getRandom().nextBoolean() ? 1 : -1) * 48 * (1 + p.getRandom().nextInt(3));
                    else cx += (p.getRandom().nextBoolean() ? 1 : -1) * 48 * (1 + p.getRandom().nextInt(3));
                    route.add(new Vec3d(cx + 0.5, Story.topY(w, cx, cz), cz + 0.5));
                }
                van.setRoute(route, 0.55f, null);
                TRUCK.put(p.getUuid(), van.getUuid());
                Life.message(p, "Сканер полиции", "Бронефургон инкассаторов выехал рядом с тобой. Разбей его — и выручка твоя.");
                return;
            }
            return;
        }
        var e = w.getEntity(id);
        if (!(e instanceof CarEntity van) || van.getPos().squaredDistanceTo(p.getPos()) > 300 * 300) {
            TRUCK.remove(p.getUuid());
            target(p, null, "");
            if (e != null) {
                for (var ps : e.getPassengerList()) ps.discard();
                e.discard();
            }
            return;
        }
        if (now % 20 == 0) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, van.getChunkPos(), 2, van.getBlockPos());
        target(p, van.getPos(), "Инкассатор");
        if (van.wrecked()) {
            TRUCK.remove(p.getUuid());
            target(p, null, "");
            int pay = 3000 + p.getRandom().nextInt(4000);
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, van.getX(), van.getY() + 1, van.getZ(), 30, 1, 1, 1, 0.1);
            Economy.dirty(p, pay, "инкассатор");
            Economy.shock(p.getServer(), "CITY", -0.06, "разбит бронефургон банка");
            Activities.title(p, "ИНКАССАТОР ВЗЯТ", "+$" + pay);
            com.yompix.novograd.life.Achievements.give(p, "armored");
            Wanted.add(p, 0, "нападение на инкассаторов", 3);
            Life.sync(p);
        }
    }

    // ------------------------------------------------------------------ бизнесы
    /** Название, место, цена, доход в день. */
    private static final Object[][] BUSINESS = {
            {"Бар «Ржавый якорь»", "bar_door", 25000, 900}, {"Клуб «Неон»", "club_neon", 60000, 2400}, {"Стоянка «Центр»", "parking", 15000, 500},
            {"Отель «Лазурь»", "riv_hotel", 140000, 5500}, {"Пристань Ривьеры", "riv_marina", 45000, 1600}, {"Ферма в Сосновке", "village_farm", 20000, 700},
    };
    private static final Map<UUID, Integer> AT_BIZ = new HashMap<>();

    private static void tickBusiness(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        // доход раз в игровой день
        long today = day(w);
        long last = d.flags.stream().filter(f -> f.startsWith("biz_day=")).mapToLong(f -> Long.parseLong(f.substring(8))).max().orElse(today);
        if (today > last) {
            int sum = 0, washed = 0;
            for (int i = 0; i < BUSINESS.length; i++) {
                if (!d.flags.contains("biz:" + i)) continue;
                int income = income(d, i);
                int safe = Math.min(safe(d, i) + income * (int) Math.min(3, today - last), income * 3); // сейф вмещает 3 дня
                setSafe(d, i, safe);
                sum += income;
                washed += Economy.launderDaily(p, income * 2); // бизнес отмывает грязные наличные
            }
            if (sum > 0) Life.message(p, "Бухгалтер", "Бизнесы заработали $" + sum + " — деньги в сейфах, заезжай забрать."
                    + (washed > 0 ? " Отмыто через бизнесы: $" + washed + " на карту." : ""));
            if (washed > 0) Life.sync(p);
        }
        d.flags.removeIf(f -> f.startsWith("biz_day="));
        d.flags.add("biz_day=" + today);
        int inside = -1;
        for (int i = 0; i < BUSINESS.length; i++) {
            Vec3d at = Story.place(w, (String) BUSINESS[i][1]);
            if (at == null || p.squaredDistanceTo(at) > 48 * 48) continue;
            boolean own = d.flags.contains("biz:" + i);
            String tag = "biz_" + i;
            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) at.x, (int) at.z);
            List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(at.x - 3, y - 4, at.z - 3, at.x + 3, y + 4, at.z + 3), e -> e.getCommandTags().contains(tag));
            ItemEntity it;
            if (have.isEmpty()) {
                it = new ItemEntity(w, at.x + 2, y + 0.3, at.z, new ItemStack(Items.GOLD_INGOT));
                it.setVelocity(0, 0, 0);
                it.setPickupDelayInfinite();
                it.setNeverDespawn();
                it.addCommandTag(tag);
                it.setCustomNameVisible(true);
                w.spawnEntity(it);
            } else it = have.get(0);
            it.setCustomName(Text.literal(own ? "💼 " + BUSINESS[i][0] + " — сейф $" + safe(d, i) + " · ур." + (level(d, i) + 1) + " · +$" + income(d, i) + "/день"
                    : "💼 " + BUSINESS[i][0] + " — продаётся $" + BUSINESS[i][2] + " (доход $" + BUSINESS[i][3] + "/день)").formatted(own ? Formatting.GREEN : Formatting.GOLD));
            if (p.squaredDistanceTo(it) < 2.2 * 2.2) inside = i;
        }
        Integer was = AT_BIZ.put(p.getUuid(), inside);
        if (inside < 0 || was != null && was == inside) return;
        if (d.flags.contains("biz:" + inside)) { // свой бизнес: забрать выручку из сейфа
            int cash = safe(d, inside);
            if (cash > 0) {
                setSafe(d, inside, 0);
                Life.earn(p, cash, "выручка: " + BUSINESS[inside][0]);
            } else p.sendMessage(Text.literal("💼 Сейф пуст — выручка копится каждый день (до трёх дней)").formatted(Formatting.GRAY), true);
            return;
        }
        int price = (int) BUSINESS[inside][2];
        if (d.money >= price) {
            d.money -= price;
            d.flags.add("biz:" + inside);
            com.yompix.novograd.life.Achievements.give(p, "tycoon");
            Activities.title(p, "БИЗНЕС КУПЛЕН", BUSINESS[inside][0] + " · +$" + BUSINESS[inside][3] + " каждый день");
            w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.1f);
            Life.sync(p);
        } else p.sendMessage(Text.literal("💼 " + BUSINESS[inside][0] + ": нужно $" + price + ", у тебя $" + d.money).formatted(Formatting.YELLOW), true);
    }

    private static int level(LifeData d, int i) {
        return (int) d.flags.stream().filter(f -> f.startsWith("bizlvl:" + i + "=")).mapToLong(f -> Long.parseLong(f.substring(f.indexOf('=') + 1))).max().orElse(0);
    }

    private static int income(LifeData d, int i) {
        return (int) BUSINESS[i][3] * (2 + level(d, i)) / 2; // каждый уровень +50%
    }

    private static int safe(LifeData d, int i) {
        return (int) d.flags.stream().filter(f -> f.startsWith("safe:" + i + "=")).mapToLong(f -> Long.parseLong(f.substring(f.indexOf('=') + 1))).max().orElse(0);
    }

    private static void setSafe(LifeData d, int i, int v) {
        d.flags.removeIf(f -> f.startsWith("safe:" + i + "="));
        d.flags.add("safe:" + i + "=" + v);
    }

    /** Улучшить бизнес (из телефона): +50% дохода и отмывки, до 3 уровня. */
    public static void upgrade(ServerPlayerEntity p, int i) {
        LifeData d = Life.get(p);
        if (i < 0 || i >= BUSINESS.length || !d.flags.contains("biz:" + i)) return;
        int lv = level(d, i);
        if (lv >= 2) return;
        int cost = (int) BUSINESS[i][2] * (lv + 1) / 2;
        if (!Life.pay(p, cost, "улучшение: " + BUSINESS[i][0])) return;
        d.flags.removeIf(f -> f.startsWith("bizlvl:" + i + "="));
        d.flags.add("bizlvl:" + i + "=" + (lv + 1));
        Activities.title(p, "БИЗНЕС УЛУЧШЕН", BUSINESS[i][0] + " · уровень " + (lv + 2) + " · +$" + income(d, i) + "/день");
        Life.sync(p);
        Economy.sendState(p);
    }

    public static net.minecraft.nbt.NbtList businessState(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
        for (int i = 0; i < BUSINESS.length; i++) {
            NbtCompound c = new NbtCompound();
            boolean own = d.flags.contains("biz:" + i);
            c.putString("name", (String) BUSINESS[i][0]);
            c.putBoolean("own", own);
            c.putInt("price", (int) BUSINESS[i][2]);
            c.putInt("income", own ? income(d, i) : (int) BUSINESS[i][3]);
            c.putInt("safe", safe(d, i));
            c.putInt("level", level(d, i));
            c.putInt("upgrade", level(d, i) >= 2 ? 0 : (int) BUSINESS[i][2] * (level(d, i) + 1) / 2);
            c.putInt("wash", own ? income(d, i) * 2 : 0);
            l.add(c);
        }
        return l;
    }

    /** Рэй на пристани Ривьеры: отмывка всего сразу (70%). */
    private static final Map<UUID, Boolean> AT_RAY = new HashMap<>();

    private static void tickRay(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, "riv_marina+6,4");
        if (at == null || p.squaredDistanceTo(at) > 48 * 48) return;
        int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) at.x, (int) at.z);
        List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(at.x - 3, y - 4, at.z - 3, at.x + 3, y + 4, at.z + 3), e -> e.getCommandTags().contains("ray_wash"));
        ItemEntity it;
        if (have.isEmpty()) {
            it = new ItemEntity(w, at.x, y + 0.3, at.z, new ItemStack(Items.EMERALD));
            it.setVelocity(0, 0, 0);
            it.setPickupDelayInfinite();
            it.setNeverDespawn();
            it.addCommandTag("ray_wash");
            it.setCustomNameVisible(true);
            it.setCustomName(Text.literal("💵 Рэй: отмою всё сразу, возьму 30%").formatted(Formatting.GREEN));
            w.spawnEntity(it);
        } else it = have.get(0);
        boolean inside = p.squaredDistanceTo(it) < 2.2 * 2.2;
        Boolean was = AT_RAY.put(p.getUuid(), inside);
        if (inside && (was == null || !was)) Economy.launderRay(p);
    }

    // ------------------------------------------------------------------ бойни (черепа банд)
    private static final String[] SKULLS = {"gang_yard+20,10", "industry_street+-30,40", "chop_shop+10,-20", "port_warehouse+20,0", "riv_villa+-20,10", "desert_mesa+20,-10"};

    private static final class Rampage {
        int left = 20 * 60, kills;
        Vec3d at;
        final List<UUID> gang = new ArrayList<>();
    }

    private static final Map<UUID, Rampage> RAMPAGE = new HashMap<>();

    private static void tickRampage(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        Rampage rp = RAMPAGE.get(p.getUuid());
        if (rp == null) {
            if (Story.active(p) && !Story.idle(d)) return;
            for (int i = 0; i < SKULLS.length; i++) {
                if (d.flags.contains("rampage:" + i)) continue;
                Vec3d at = Story.place(w, SKULLS[i]);
                if (at == null || p.squaredDistanceTo(at) > 40 * 40) continue;
                String tag = "skull_" + i;
                List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(at.x - 3, at.y - 4, at.z - 3, at.x + 3, at.y + 4, at.z + 3), e -> e.getCommandTags().contains(tag));
                ItemEntity it;
                if (have.isEmpty()) {
                    it = new ItemEntity(w, at.x, at.y + 0.3, at.z, new ItemStack(Items.SKELETON_SKULL));
                    it.setVelocity(0, 0, 0);
                    it.setPickupDelayInfinite();
                    it.setNeverDespawn();
                    it.setGlowing(true);
                    it.addCommandTag(tag);
                    it.setCustomName(Text.literal("☠ Бойня").formatted(Formatting.RED));
                    it.setCustomNameVisible(true);
                    w.spawnEntity(it);
                } else it = have.get(0);
                if (p.squaredDistanceTo(it) < 2 * 2) {
                    it.discard();
                    rp = new Rampage();
                    rp.at = at;
                    RAMPAGE.put(p.getUuid(), rp);
                    d.flags.add("rampage:" + i);
                    Activities.title(p, "БОЙНЯ!", "Уложи 10 бандитов за 60 секунд");
                    for (int k = 0; k < 6; k++) gangster(p, rp);
                    return;
                }
            }
            return;
        }
        rp.left -= 10;
        LifeNet.send(p, "act_timer", sec(rp.left / 20));
        int alive = 0;
        for (UUID u : rp.gang) if (w.getEntity(u) instanceof CitizenEntity c && c.isAlive()) alive++;
        rp.kills = rp.gang.size() - alive;
        if (alive < 3 && rp.gang.size() < 16) for (int k = 0; k < 3; k++) gangster(p, rp);
        if (rp.kills >= 10) {
            RAMPAGE.remove(p.getUuid());
            Economy.dirty(p, 2500, "бойня");
            Activities.title(p, "БОЙНЯ ПРОЙДЕНА", "+$2500");
            com.yompix.novograd.life.Achievements.give(p, "rampage");
            LifeNet.send(p, "act_timer", sec(-1));
            Life.sync(p);
        } else if (rp.left <= 0) {
            RAMPAGE.remove(p.getUuid());
            Activities.title(p, "БОЙНЯ ПРОВАЛЕНА", "Время вышло: " + rp.kills + "/10");
            LifeNet.send(p, "act_timer", sec(-1));
        }
    }

    private static void gangster(ServerPlayerEntity p, Rampage rp) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        Vec3d v = rp.at.add(Math.cos(a) * (10 + r.nextInt(10)), 0, Math.sin(a) * (10 + r.nextInt(10)));
        CitizenEntity g = new CitizenEntity(ModContent.CITIZEN, w);
        g.setup(CitizenEntity.RANDOM_FROM + 39 - r.nextInt(4) * 2, CitizenEntity.Role.GANG, false, "Бандит");
        g.refreshPositionAndAngles(v.x, Story.topY(w, (int) v.x, (int) v.z), v.z, 0, 0);
        if (r.nextInt(3) == 0) g.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
        g.removeWhenIdle();
        w.spawnEntity(g);
        g.attack(p);
        rp.gang.add(g.getUuid());
    }

    // ------------------------------------------------------------------ общее
    private static int snap(double v) {
        return (int) (Math.round((v - 24) / 48.0) * 48 + 24);
    }

    private static NbtCompound sec(int s) {
        NbtCompound n = new NbtCompound();
        n.putInt("sec", s);
        return n;
    }

    private static void target(ServerPlayerEntity p, @Nullable Vec3d at, String label) {
        NbtCompound n = new NbtCompound();
        if (at != null) {
            n.putDouble("x", at.x);
            n.putDouble("z", at.z);
            n.putString("label", label);
        }
        LifeNet.send(p, "act_target", n);
    }

    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 10 != 3) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            LifeData d = Life.get(p);
            if (!d.flags.contains("tips_free") && p.age > 20 * 60 * 3) {
                d.flags.add("tips_free");
                Life.message(p, "Новоград-гид", "Без миссий тоже есть чем заняться: грабь банки (подойди с оружием к хранилищу), бери кассу в магазинах (целься в продавца), лови бронефургоны, покупай бизнесы (золотые метки) и ищи черепа банд — бойни. А ещё 50 тайников, трюки, такси и патруль (J в машине).");
            }
            tickBanks(p, d);
            tickShops(p);
            tickTruck(p);
            tickRampage(p, d);
            if (t % 40 == 3) tickBusiness(p, d);
            if (t % 20 == 13) tickRay(p, d);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        HEISTS.remove(p.getUuid());
        RAMPAGE.remove(p.getUuid());
        var e = TRUCK.remove(p.getUuid());
        if (e != null && p.getServerWorld().getEntity(e) instanceof CarEntity van) {
            for (var ps : van.getPassengerList()) ps.discard();
            van.discard();
        }
    }
}
