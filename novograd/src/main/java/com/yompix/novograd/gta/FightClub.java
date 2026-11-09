package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Подпольные бои «Подвал» в промзоне (вход — с тротуара у улицы промзоны, нужен акт II).
 * Внизу ринг, толпа и букмекер: бои NPC 1×1 каждые ~минуту — ставки грязными наличными по коэффициентам;
 * или выйди на ринг сам: только кулаки, 3 раунда по 40 секунд, нокаут или решение судей; приз — наличными.
 * Урон на ринге считает этот класс (Pulse → ALLOW_DAMAGE): без розыска, без смерти.
 */
public final class FightClub {
    private FightClub() {}

    // вход — тротуар у улицы промзоны (x = 408), подвал — под участком к востоку
    public static final int EX = 413, EZ = 300;
    static final int OX = 416, OZ = 290, SX = 21, SZ = 17;
    static final int FLOOR = -63; // пол подвала (воздух -62..-59, потолок -58 — под землёй острова)
    private static final String TAG = "pulse_fc";
    private static final String[] TIERS = {"Новичок", "Профи", "Чемпион «Подвала»"};
    private static final int[] PRIZE = {500, 1500, 5000};
    private static final float[] OPP_POWER = {0.8f, 1.05f, 1.35f};
    private static final int[] OPP_HP = {40, 65, 100};
    private static final String[] NICKS = {"Молот", "Бульдог", "Тихий", "Кувалда", "Шрам", "Медведь", "Змей", "Татарин", "Кирпич",
            "Бешеный", "Самурай", "Гвоздь", "Монах", "Цыган", "Шторм", "Боцман", "Клык", "Север"};

    static Vec3d center() {
        return new Vec3d(OX + SX / 2 + 0.5, FLOOR + 1, OZ + SZ / 2 + 0.5);
    }

    static Vec3d inside() {
        return new Vec3d(OX + 5.5, FLOOR + 1, OZ + 4.5);
    }

    static Vec3d bookie() {
        return new Vec3d(OX + 2.5, FLOOR + 1, OZ + SZ - 2.5);
    }

    public static boolean inside(ServerPlayerEntity p) {
        return p.getX() >= OX && p.getX() <= OX + SX && p.getZ() >= OZ && p.getZ() <= OZ + SZ && p.getY() < FLOOR + 6;
    }

    // ================================================================== бой
    private enum Phase { IDLE, BETS, FIGHT, RESULT }

    private static final class Bout {
        Phase phase = Phase.IDLE;
        int timer, round = 1;
        UUID a, b;               // b == null и player != null — бой игрока
        String nameA = "", nameB = "";
        float powA, powB, hpA, hpB, maxA, maxB;
        double odds1, odds2;
        UUID player;
        int tier;
        final Map<UUID, int[]> bets = new HashMap<>(); // игрок → {сторона 1|2, сумма}
        int winner;
    }

    private static Bout bout = new Bout();
    private static boolean built;
    private static final Map<UUID, Long> GATE = new HashMap<>();
    private static final Random R = new Random();

    public static void tick(MinecraftServer s) {
        long t = s.getTicks();
        ServerWorld w = s.getOverworld();
        if (t % 10 == 4) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.getServerWorld() != w || p.isSpectator()) continue;
            tickDoors(p, w);
        }
        if (t % 5 != 0) return;
        boolean anyone = s.getPlayerManager().getPlayerList().stream().anyMatch(FightClub::inside);
        if (!anyone && bout.phase != Phase.IDLE && bout.player == null) {
            reset(w);
            return;
        }
        if (anyone && t % 200 == 0) crowd(w);
        tickBout(w, s);
        if (t % 10 == 0) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) if (inside(p)) sendHud(p);
    }

    /** Вход с улицы, выход и букмекер — висящие метки. */
    private static void tickDoors(ServerPlayerEntity p, ServerWorld w) {
        long now = w.getTime();
        if (Math.abs(p.getX() - EX) < 48 && Math.abs(p.getZ() - EZ) < 48) {
            Vec3d door = new Vec3d(EX + 0.5, Story.topY(w, EX, EZ), EZ + 0.5);
            var m = FunUtil.marker(w, door, "fc_door", new ItemStack(Items.IRON_DOOR),
                    Text.literal("«Подвал» — подпольные бои и ставки").formatted(Formatting.RED), true);
            if (p.squaredDistanceTo(m) < 1.8 * 1.8 && !p.hasVehicle() && now > GATE.getOrDefault(p.getUuid(), 0L)) {
                GATE.put(p.getUuid(), now + 60);
                LifeData d = Life.get(p);
                if (Economy.act(d) < 1) {
                    p.sendMessage(Text.literal("Охранник: «Тебя здесь не знают. Приходи, когда о тебе заговорит улица» (с II акта)").formatted(Formatting.GRAY), true);
                    return;
                }
                if (Wanted.stars(p) > 0) {
                    p.sendMessage(Text.literal("Охранник: «С хвостом из полиции — не пущу. Сбрось розыск»").formatted(Formatting.YELLOW), true);
                    return;
                }
                build(w);
                Vec3d in = inside();
                p.teleport(w, in.x, in.y, in.z, -45, 0);
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.8f);
                Activities.title(p, "«ПОДВАЛ»", "Ставки — у букмекера, бой — на ринге. Выход — железная дверь");
                crowd(w);
                sendState(p);
            }
        }
        if (!inside(p)) return;
        Vec3d exit = new Vec3d(OX + 1.5, FLOOR + 1, OZ + 1.5);
        var ex = FunUtil.marker(w, exit, "fc_exit", new ItemStack(Items.IRON_DOOR), Text.literal("⬆ Выход на улицу").formatted(Formatting.GRAY), false);
        var bk = FunUtil.marker(w, bookie(), "fc_bookie", new ItemStack(Items.GOLD_INGOT), Text.literal("Букмекер: ставки и запись на бой").formatted(Formatting.GOLD), true);
        if (p.squaredDistanceTo(ex) < 1.5 * 1.5 && now > GATE.getOrDefault(p.getUuid(), 0L) && !p.getUuid().equals(bout.player)) {
            GATE.put(p.getUuid(), now + 60);
            Vec3d out = new Vec3d(EX + 0.5, Story.topY(w, EX, EZ), EZ + 2.5);
            p.teleport(w, out.x, out.y, out.z, 0, 0);
            Pulse.send(p, "fight_hud", new NbtCompound());
        }
        if (p.squaredDistanceTo(bk) < 2 * 2 && now > GATE.getOrDefault(p.getUuid(), 0L)) {
            GATE.put(p.getUuid(), now + 80);
            openScreen(p);
        }
    }

    // ------------------------------------------------------------------ постройка подвала
    private static void build(ServerWorld w) {
        if (built && w.getBlockState(new BlockPos(OX + 1, FLOOR, OZ + 1)).isOf(Blocks.POLISHED_DEEPSLATE)) return;
        BlockState wall = Blocks.STONE_BRICKS.getDefaultState(), floor = Blocks.POLISHED_DEEPSLATE.getDefaultState(),
                ring = Blocks.WHITE_CONCRETE.getDefaultState(), air = Blocks.AIR.getDefaultState(), lamp = Blocks.GLOWSTONE.getDefaultState(),
                post = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState(), ceil = Blocks.DEEPSLATE_TILES.getDefaultState();
        if (!w.getBlockState(new BlockPos(OX + 1, FLOOR, OZ + 1)).isOf(Blocks.POLISHED_DEEPSLATE)) {
            for (int x = OX - 1; x <= OX + SX; x++)
                for (int z = OZ - 1; z <= OZ + SZ; z++)
                    for (int y = FLOOR; y <= FLOOR + 5; y++) {
                        boolean edge = x == OX - 1 || x == OX + SX || z == OZ - 1 || z == OZ + SZ;
                        BlockPos b = new BlockPos(x, y, z);
                        if (y == FLOOR) w.setBlockState(b, floor, 2);
                        else if (y == FLOOR + 5) w.setBlockState(b, (x - OX) % 4 == 2 && (z - OZ) % 4 == 2 && !edge ? lamp : ceil, 2);
                        else w.setBlockState(b, edge ? wall : air, 2);
                    }
            Vec3d c = center();
            int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
            for (int x = cx - 4; x <= cx + 4; x++)
                for (int z = cz - 4; z <= cz + 4; z++) {
                    boolean edge = Math.abs(x - cx) == 4 || Math.abs(z - cz) == 4;
                    boolean corner = Math.abs(x - cx) == 4 && Math.abs(z - cz) == 4;
                    w.setBlockState(new BlockPos(x, FLOOR, z), ring, 2);
                    if (corner) {
                        w.setBlockState(new BlockPos(x, FLOOR + 1, z), post, 2);
                        w.setBlockState(new BlockPos(x, FLOOR + 2, z), post, 2);
                    } else if (edge && Math.abs(x - cx) != 0 && Math.abs(z - cz) != 0)
                        w.setBlockState(new BlockPos(x, FLOOR + 1, z), Blocks.RED_CARPET.getDefaultState(), 2);
                }
            // скамейки для зрителей и бар у стены
            for (int x = OX + 1; x < OX + SX - 1; x += 2) {
                w.setBlockState(new BlockPos(x, FLOOR + 1, OZ + SZ - 1), Blocks.SPRUCE_STAIRS.getDefaultState(), 2);
            }
            for (int z = OZ + 4; z < OZ + SZ - 4; z++) w.setBlockState(new BlockPos(OX + SX - 1, FLOOR + 1, z), Blocks.BARREL.getDefaultState(), 2);
            w.setBlockState(new BlockPos(OX + 1, FLOOR + 1, OZ), Blocks.IRON_DOOR.getDefaultState(), 2);
            w.setBlockState(new BlockPos(OX + 1, FLOOR + 2, OZ), Blocks.IRON_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), 2);
            for (Entity e : w.getEntitiesByClass(Entity.class, new Box(OX, FLOOR, OZ, OX + SX, FLOOR + 6, OZ + SZ), e -> e.getCommandTags().contains(TAG))) e.discard();
        }
        built = true;
    }

    /** Зрители у ринга — кричат и хлопают. */
    private static void crowd(ServerWorld w) {
        Box box = new Box(OX, FLOOR, OZ, OX + SX, FLOOR + 6, OZ + SZ);
        List<CitizenEntity> have = w.getEntitiesByClass(CitizenEntity.class, box, c -> c.getCommandTags().contains(TAG + "_crowd"));
        if (have.size() >= 8) return;
        Vec3d c = center();
        for (int i = have.size(); i < 8; i++) {
            double a = i * Math.PI * 2 / 8 + 0.3;
            double x = c.x + Math.cos(a) * 6.3, z = c.z + Math.sin(a) * 6.3;
            CitizenEntity n = new CitizenEntity(ModContent.CITIZEN, w);
            n.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, true, Names.random(w.getRandom()));
            n.setCustomNameVisible(false);
            n.refreshPositionAndAngles(x, FLOOR + 1, z, (float) Math.toDegrees(Math.atan2(-(c.x - x), c.z - z)), 0);
            n.addCommandTag(TAG);
            n.addCommandTag(TAG + "_crowd");
            w.spawnEntity(n);
            n.lockActivity(i % 3 == 0 ? CitizenEntity.Activity.ARGUE : CitizenEntity.Activity.CHEER);
        }
    }

    // ------------------------------------------------------------------ ход боя
    private static void tickBout(ServerWorld w, MinecraftServer s) {
        Bout b = bout;
        b.timer -= 5;
        switch (b.phase) {
            case IDLE -> {
                if (s.getPlayerManager().getPlayerList().stream().anyMatch(FightClub::inside)) newNpcBout(w);
            }
            case BETS -> {
                if (b.timer % 100 == 0 && b.timer > 0) say(s, "Ставки принимаются: " + b.nameA + " (×" + fmt(b.odds1) + ") против " + b.nameB + " (×" + fmt(b.odds2) + ") · " + b.timer / 20 + " с");
                if (b.timer <= 0) {
                    b.phase = Phase.FIGHT;
                    b.timer = 20 * 40;
                    b.round = 1;
                    say(s, "БОЙ! " + b.nameA + " против " + b.nameB);
                    w.playSound(null, BlockPos.ofFloored(center()), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.5f, 1.2f);
                }
            }
            case FIGHT -> fight(w, s, b);
            case RESULT -> {
                if (b.timer <= 0) reset(w);
            }
        }
    }

    private static void fight(ServerWorld w, MinecraftServer s, Bout b) {
        CitizenEntity ca = w.getEntity(b.a) instanceof CitizenEntity c ? c : null;
        LivingEntity cb = b.player != null ? s.getPlayerManager().getPlayer(b.player) : w.getEntity(b.b) instanceof CitizenEntity c ? c : null;
        if (ca == null || cb == null || !cb.isAlive()) {
            finish(w, s, b, cb == null || !cb.isAlive() ? 1 : 2, "соперник исчез");
            return;
        }
        Vec3d c = center();
        // держим бойцов на ринге и в драке
        if (ca.squaredDistanceTo(c) > 5.5 * 5.5) ca.requestTeleport(c.x + 1.5, FLOOR + 1, c.z);
        if (!ca.chasing(cb)) ca.attack(cb);
        if (cb instanceof CitizenEntity nb) {
            if (nb.squaredDistanceTo(c) > 5.5 * 5.5) nb.requestTeleport(c.x - 1.5, FLOOR + 1, c.z);
            if (!nb.chasing(ca)) nb.attack(ca);
        } else if (cb instanceof ServerPlayerEntity p && p.squaredDistanceTo(c) > 6.5 * 6.5) {
            finish(w, s, b, 1, "ты ушёл с ринга — поражение");
            return;
        }
        if (b.timer <= 0) {
            if (b.round < 3) {
                b.round++;
                b.timer = 20 * 40;
                say(s, "Раунд " + b.round);
                w.playSound(null, BlockPos.ofFloored(c), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.5f, 1.2f);
                if (cb instanceof ServerPlayerEntity p) p.heal(4);
                b.hpA = Math.min(b.maxA, b.hpA + b.maxA * 0.08f);
            } else {
                float ka = b.hpA / b.maxA, kb = cb instanceof ServerPlayerEntity p ? p.getHealth() / p.getMaxHealth() : b.hpB / b.maxB;
                finish(w, s, b, ka >= kb ? 1 : 2, "решение судей");
            }
        }
    }

    /** Урон на ринге. false — отменить ванильный урон. */
    public static boolean allowDamage(LivingEntity e, DamageSource src, float amount) {
        Bout b = bout;
        if (b.phase == Phase.IDLE || !(e.getWorld() instanceof ServerWorld w)) return true;
        Entity att = src.getAttacker();
        boolean isA = e.getUuid().equals(b.a), isB = e.getUuid().equals(b.b) || e.getUuid().equals(b.player);
        if (!isA && !isB) {
            // зрителей и толпу на ринге не бьём
            return !(e instanceof CitizenEntity c && c.getCommandTags().contains(TAG) && att instanceof ServerPlayerEntity);
        }
        if (b.phase != Phase.FIGHT) return false; // до гонга и после — никто никого не бьёт
        boolean fromA = att != null && att.getUuid().equals(b.a), fromB = att != null && (att.getUuid().equals(b.b) || att.getUuid().equals(b.player));
        if (!fromA && !fromB) return !(e instanceof CitizenEntity); // пули и всё постороннее бойцов-NPC не берут
        if (e instanceof ServerPlayerEntity p) {
            // игрок на ринге: настоящий урон, но без смерти — нокаут
            if (p.getHealth() - amount <= 2f) {
                finish(w, w.getServer(), b, 1, "нокаут");
                return false;
            }
            return true;
        }
        float dmg;
        if (att instanceof ServerPlayerEntity p) {
            if (!p.getMainHandStack().isEmpty()) {
                p.sendMessage(Text.literal("Только кулаки! Убери оружие из руки").formatted(Formatting.RED), true);
                return false;
            }
            dmg = amount * 4f * (1 + Life.get(p).strength / 60f);
        } else {
            if (R.nextFloat() < 0.18f) { // уклонился
                w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
                return false;
            }
            dmg = (5 + R.nextFloat() * 5) * (fromA ? b.powA : b.powB);
        }
        if (R.nextFloat() < 0.12f) {
            dmg *= 2;
            w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1.4, e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.NEUTRAL, 0.9f, 0.8f + R.nextFloat() * 0.4f);
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + 1.3, e.getZ(), 2, 0.2, 0.2, 0.2, 0.1);
        if (att != null) {
            Vec3d push = e.getPos().subtract(att.getPos()).multiply(1, 0, 1);
            if (push.lengthSquared() > 0.001) e.addVelocity(push.normalize().multiply(0.25).add(0, 0.12, 0));
        }
        if (isA) b.hpA -= dmg;
        else b.hpB -= dmg;
        if (isA && b.hpA <= 0) finish(w, w.getServer(), b, 2, "нокаут");
        else if (!isA && b.hpB <= 0) finish(w, w.getServer(), b, 1, "нокаут");
        return false;
    }

    private static void finish(ServerWorld w, MinecraftServer s, Bout b, int winner, String how) {
        if (b.phase != Phase.FIGHT) return;
        b.phase = Phase.RESULT;
        b.timer = 20 * 7;
        b.winner = winner;
        CitizenEntity ca = w.getEntity(b.a) instanceof CitizenEntity c ? c : null;
        Entity cb = b.player != null ? s.getPlayerManager().getPlayer(b.player) : w.getEntity(b.b);
        Entity loser = winner == 1 ? cb : ca, win = winner == 1 ? ca : cb;
        if (ca != null) ca.standDown();
        if (cb instanceof CitizenEntity nb) nb.standDown();
        if (loser instanceof CitizenEntity lc && win != null) lc.knockDown(win.getPos(), win instanceof LivingEntity le ? le : null, 120);
        if (win instanceof CitizenEntity wc) wc.lockActivity(CitizenEntity.Activity.CHEER);
        w.playSound(null, BlockPos.ofFloored(center()), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.6f, 0.9f);
        w.playSound(null, BlockPos.ofFloored(center()), SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.2f, 0.8f);
        String wn = winner == 1 ? b.nameA : b.nameB;
        say(s, "Победа: " + wn + " (" + how + ")");
        if (b.player != null && cb instanceof ServerPlayerEntity p) {
            Pulse.send(p, "fight_hud", new NbtCompound());
            if (winner == 2) {
                int prize = PRIZE[b.tier];
                Economy.dirty(p, prize, "приз «Подвала»");
                Activities.title(p, "ТЫ ПОБЕДИЛ!", TIERS[b.tier] + " повержен (" + how + ") · +$" + prize + " наличными");
                Achievements.give(p, "fight_win");
                if (b.tier == 2) {
                    Achievements.give(p, "fight_champ");
                    News.post(s, "Слухи из промзоны: в подпольном клубе «Подвал» появился новый чемпион", "ГОРОД");
                }
                FunUtil.setInt(Life.get(p), "fc_wins", FunUtil.getInt(Life.get(p), "fc_wins") + 1);
            } else {
                p.setHealth(Math.max(p.getHealth(), 6));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 6, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20 * 5, 0));
                Activities.title(p, "НОКАУТ", "Ты проиграл (" + how + "). Отдохни и попробуй снова");
            }
        }
        // ставки
        for (var e : b.bets.entrySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            int side = e.getValue()[0], amount = e.getValue()[1];
            if (side == winner) {
                int pay = (int) Math.round(amount * (winner == 1 ? b.odds1 : b.odds2));
                Economy.dirty(p, pay, "выигрыш ставки");
                Activities.title(p, "СТАВКА СЫГРАЛА", wn + " · +$" + pay + " наличными");
                if (pay - amount >= 5000) Achievements.give(p, "fight_bet");
            } else Activities.title(p, "СТАВКА СГОРЕЛА", "Победил " + wn + " · −$" + amount);
        }
        b.bets.clear();
    }

    private static void reset(ServerWorld w) {
        Bout b = bout;
        for (UUID u : new UUID[]{b.a, b.b}) if (u != null && w.getEntity(u) instanceof CitizenEntity c) c.discard();
        if (b.player != null) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(b.player);
            if (p != null) Pulse.send(p, "fight_hud", new NbtCompound());
        }
        bout = new Bout();
        bout.timer = 20 * 8; // пауза между боями
        bout.phase = Phase.IDLE;
    }

    private static CitizenEntity fighter(ServerWorld w, Vec3d at, String name, float power) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        int skin = CitizenEntity.RANDOM_FROM + 1 + 2 * w.getRandom().nextInt(CitizenEntity.RANDOM_COUNT / 2); // мужчины
        c.setup(skin, CitizenEntity.Role.CITIZEN, false, name);
        c.setCustomNameVisible(true);
        c.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        c.addCommandTag(TAG);
        var hp = c.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) hp.setBaseValue(200);
        var sp = c.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(0.27 + power * 0.03);
        c.setHealth(200);
        w.spawnEntity(c);
        return c;
    }

    private static String nick() {
        return NICKS[R.nextInt(NICKS.length)];
    }

    private static void newNpcBout(ServerWorld w) {
        if (bout.timer > 0) return;
        Bout b = new Bout();
        Vec3d c = center();
        b.powA = 0.75f + R.nextFloat() * 0.6f;
        b.powB = 0.75f + R.nextFloat() * 0.6f;
        b.nameA = "«" + nick() + "»";
        do b.nameB = "«" + nick() + "»"; while (b.nameB.equals(b.nameA));
        b.maxA = b.hpA = 60 + R.nextInt(30);
        b.maxB = b.hpB = 60 + R.nextInt(30);
        double sa = Math.pow(b.powA, 2.2) * b.maxA, sb = Math.pow(b.powB, 2.2) * b.maxB;
        double pa = sa / (sa + sb);
        b.odds1 = Math.max(1.1, Math.round(0.9 / pa * 10) / 10.0);
        b.odds2 = Math.max(1.1, Math.round(0.9 / (1 - pa) * 10) / 10.0);
        b.a = fighter(w, c.add(2.5, 0, 0), b.nameA, b.powA).getUuid();
        b.b = fighter(w, c.add(-2.5, 0, 0), b.nameB, b.powB).getUuid();
        b.phase = Phase.BETS;
        b.timer = 20 * 25;
        bout = b;
        say(w.getServer(), "Следующий бой: " + b.nameA + " (×" + fmt(b.odds1) + ") против " + b.nameB + " (×" + fmt(b.odds2) + "). Ставки — у букмекера");
        for (ServerPlayerEntity p : w.getServer().getPlayerManager().getPlayerList()) if (inside(p)) sendState(p);
    }

    // ------------------------------------------------------------------ игрок
    public static void bet(ServerPlayerEntity p, int side, int amount) {
        Bout b = bout;
        if (!inside(p)) return;
        if (b.phase != Phase.BETS || b.player != null) {
            p.sendMessage(Text.literal("Ставки сейчас не принимаются — дождись объявления следующего боя").formatted(Formatting.YELLOW), true);
            return;
        }
        if (side != 1 && side != 2 || amount < 50 || amount > 20000) return;
        if (b.bets.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Ты уже поставил на этот бой").formatted(Formatting.YELLOW), true);
            return;
        }
        if (!Economy.payBlack(p, amount, "ставка на бой")) return;
        b.bets.put(p.getUuid(), new int[]{side, amount});
        p.sendMessage(Text.literal("Ставка $" + amount + " на " + (side == 1 ? b.nameA : b.nameB) + " ×" + fmt(side == 1 ? b.odds1 : b.odds2)).formatted(Formatting.GOLD), true);
        Life.sound(p, ModContent.CASH, 0.8f);
        sendState(p);
    }

    public static void join(ServerPlayerEntity p, int tier) {
        tier = Math.max(0, Math.min(2, tier));
        ServerWorld w = p.getServerWorld();
        if (!inside(p)) {
            p.sendMessage(Text.literal("Запись на бой — у букмекера в «Подвале»").formatted(Formatting.YELLOW), true);
            return;
        }
        LifeData d = Life.get(p);
        if (tier == 2 && FunUtil.getInt(d, "fc_wins") < 2) {
            p.sendMessage(Text.literal("К чемпиону пускают после двух побед").formatted(Formatting.YELLOW), true);
            return;
        }
        if (bout.phase == Phase.FIGHT || bout.phase == Phase.RESULT) {
            p.sendMessage(Text.literal("Идёт бой — запишись, когда он закончится").formatted(Formatting.YELLOW), true);
            return;
        }
        if (!p.getMainHandStack().isEmpty()) {
            p.sendMessage(Text.literal("На ринг — только с пустыми руками").formatted(Formatting.RED), true);
            return;
        }
        // отменяем объявленный бой NPC, ставки возвращаем
        for (var e : bout.bets.entrySet()) {
            ServerPlayerEntity q = w.getServer().getPlayerManager().getPlayer(e.getKey());
            if (q != null) Economy.dirty(q, e.getValue()[1], "ставка возвращена");
        }
        bout.bets.clear();
        reset(w);
        Bout b = new Bout();
        Vec3d c = center();
        b.player = p.getUuid();
        b.tier = tier;
        b.powA = OPP_POWER[tier];
        b.maxA = b.hpA = OPP_HP[tier];
        b.nameA = "«" + nick() + "» (" + TIERS[tier] + ")";
        b.nameB = p.getName().getString();
        CitizenEntity opp = fighter(w, c.add(2.5, 0, 0), b.nameA, b.powA);
        var dmg = opp.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(1.5 + tier * 1.2);
        b.a = opp.getUuid();
        p.teleport(w, c.x - 2.5, FLOOR + 1, c.z, -90, 0);
        p.setHealth(p.getMaxHealth());
        b.phase = Phase.BETS;
        b.timer = 20 * 4; // «три… два… один…»
        bout = b;
        Activities.title(p, "НА РИНГЕ", "Соперник: " + b.nameA + " · приз $" + PRIZE[tier] + " · только кулаки");
        say(w.getServer(), "На ринге новичок " + b.nameB + " против " + b.nameA + "!");
    }

    // ------------------------------------------------------------------ интерфейс
    public static void openScreen(ServerPlayerEntity p) {
        NbtCompound n = state(p);
        n.putBoolean("open", true);
        Pulse.send(p, "fight", n);
    }

    public static void sendState(ServerPlayerEntity p) {
        Pulse.send(p, "fight", state(p));
    }

    private static NbtCompound state(ServerPlayerEntity p) {
        Bout b = bout;
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        n.putString("phase", b.phase.name());
        n.putBoolean("playerBout", b.player != null);
        n.putString("a", b.nameA);
        n.putString("b", b.nameB);
        n.putDouble("o1", b.odds1);
        n.putDouble("o2", b.odds2);
        n.putFloat("pa", b.powA);
        n.putFloat("pb", b.powB);
        n.putInt("timer", Math.max(0, b.timer / 20));
        int[] my = b.bets.get(p.getUuid());
        n.putInt("mySide", my == null ? 0 : my[0]);
        n.putInt("myBet", my == null ? 0 : my[1]);
        n.putInt("dirty", d.dirty);
        n.putInt("money", d.money);
        n.putInt("wins", FunUtil.getInt(d, "fc_wins"));
        NbtList tiers = new NbtList();
        for (int i = 0; i < TIERS.length; i++) {
            NbtCompound t = new NbtCompound();
            t.putString("name", TIERS[i]);
            t.putInt("prize", PRIZE[i]);
            tiers.add(t);
        }
        n.put("tiers", tiers);
        return n;
    }

    private static void sendHud(ServerPlayerEntity p) {
        Bout b = bout;
        NbtCompound n = new NbtCompound();
        if (b.phase == Phase.FIGHT || b.phase == Phase.BETS || b.phase == Phase.RESULT) {
            n.putString("phase", b.phase.name());
            n.putString("a", b.nameA);
            n.putString("b", b.nameB);
            n.putFloat("ha", Math.max(0, b.hpA / Math.max(1, b.maxA)));
            float hb;
            if (b.player != null) {
                ServerPlayerEntity pl = p.getServer().getPlayerManager().getPlayer(b.player);
                hb = pl == null ? 0 : pl.getHealth() / pl.getMaxHealth();
            } else hb = Math.max(0, b.hpB / Math.max(1, b.maxB));
            n.putFloat("hb", hb);
            n.putInt("round", b.round);
            n.putInt("timer", Math.max(0, b.timer / 20));
            n.putInt("winner", b.winner);
            int[] my = b.bets.get(p.getUuid());
            n.putInt("mySide", my == null ? 0 : my[0]);
        }
        Pulse.send(p, "fight_hud", n);
    }

    private static void say(MinecraftServer s, String msg) {
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList())
            if (inside(p)) p.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), false);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    public static void onLeave(ServerPlayerEntity p) {
        GATE.remove(p.getUuid());
        if (p.getUuid().equals(bout.player)) reset(p.getServerWorld());
    }

    public static String debugTp(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        w.getChunk(EX >> 4, EZ >> 4);
        p.teleport(w, EX + 0.5, Story.topY(w, EX, EZ), EZ + 3.5, 180, 0);
        return "Вход в «Подвал»: " + EX + ", " + EZ + " · зона " + Island.zone(EX, EZ);
    }
}
