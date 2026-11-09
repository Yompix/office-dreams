package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandChunkGenerator;
import com.yompix.novograd.world.TownState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Тюрьма вместо простого «ЗАДЕРЖАН»: исправительная колония №7 за кольцевой трассой (строится при первом аресте).
 * Срок зависит от звёзд (реальные минуты, на экране — «сутки»). Варианты: отсидеть (промотать время, плата за передачки),
 * взятка охраннику (наличные, грязные — первыми; может не взять), адвокат (чистые с карты) или побег ночью:
 * расковырять вентиляцию (присесть у решётки), через двор к лестнице у стены, перелезть — тревога, охрана и собаки,
 * уйти на 150 м. Телефон изъят, пока сидишь. Грязные деньги изымаются при аресте (Economy.onDownfall), как раньше.
 */
public final class Prison {
    private Prison() {}

    /** Угол комплекса (север-запад): за кольцевой трассой x=744, к югу — трасса на деревню (z=24). */
    public static final int X0 = 900, Z0 = -180, SIZE = 30;
    private static final String GUARD_TAG = "nvg_guard", DOG_TAG = "nvg_dog";

    private static int floorY(ServerWorld w) {
        String v = TownState.get(w).storyPlaces.get("prison_y");
        if (v != null) return Integer.parseInt(v);
        return Island.surface(X0 + SIZE / 2, Z0 + SIZE / 2) + 1;
    }

    private static BlockPos at(int y0, int lx, int ly, int lz) {
        return new BlockPos(X0 + lx, y0 + ly, Z0 + lz);
    }

    // ------------------------------------------------------------------ состояние (флаги игрока — переживают перезаход)
    private static long get(LifeData d, String k) {
        long v = FunUtil.getLong(d, k);
        return v == Long.MIN_VALUE ? 0 : v;
    }

    public static boolean inside(ServerPlayerEntity p) {
        return Life.get(p).flags.stream().anyMatch(f -> f.startsWith("jail_end="));
    }

    /** Пока сидит (и до тревоги при побеге) — новые звёзды не начисляются. */
    public static boolean immune(ServerPlayerEntity p) {
        return inside(p) && get(Life.get(p), "jail_stage") < 3;
    }

    private static boolean island(ServerWorld w) {
        return w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator;
    }

    /** Арест: в камеру. false — не остров (старый «ЗАДЕРЖАН» у участка). */
    public static boolean jail(ServerPlayerEntity p, int stars, int fine) {
        ServerWorld w = p.getServer().getOverworld();
        if (!island(w)) return false;
        stars = Math.max(1, Math.min(5, stars));
        ensureBuilt(w);
        LifeData d = Life.get(p);
        long now = w.getTime();
        boolean again = inside(p);
        long sec = 60 + 60L * stars + (again ? 60 : 0);
        if (!again) FunUtil.setLong(d, "jail_gm", p.interactionManager.getGameMode().getId());
        FunUtil.setLong(d, "jail_end", now + sec * 20);
        FunUtil.setLong(d, "jail_stars", stars);
        FunUtil.setLong(d, "jail_stage", 0);
        FunUtil.setLong(d, "jail_vent", 0);
        d.flags.remove("jail_failed");
        p.stopRiding();
        p.changeGameMode(GameMode.ADVENTURE); // ломать стены камеры нельзя
        toCell(p, w);
        resetVent(w);
        // маску и телефон забрали
        d.flags.removeIf(f -> f.startsWith("mask="));
        Disguise.broadcast(p);
        Activities.title(p, again ? "ПОЙМАН СНОВА" : "ЗАДЕРЖАН", "Колония №7 · срок " + days(sec) + (fine > 0 ? " · штраф $" + fine : "") + " · телефон изъят");
        Life.sync(p);
        state(p);
        FunUtil.setLong(d, "jail_menu_at", now + 50);
        return true;
    }

    private static String days(long sec) {
        long dd = Math.max(1, sec / 60 * 3);
        return dd + (dd % 10 == 1 && dd != 11 ? " сутки" : " суток");
    }

    private static void toCell(ServerPlayerEntity p, ServerWorld w) {
        int y = floorY(w);
        p.teleport(w, X0 + 5.5, y, Z0 + 25.6, 0, 0);
    }

    private static Box cellBox(ServerWorld w) {
        int y = floorY(w);
        return new Box(X0 + 4, y - 1, Z0 + 25, X0 + 7, y + 3, Z0 + 28).expand(0.4, 0, 0.4);
    }

    private static Box compound(ServerWorld w) {
        int y = floorY(w);
        return new Box(X0, y - 2, Z0, X0 + SIZE + 1, y + 7, Z0 + SIZE + 1);
    }

    private static void state(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        boolean in = inside(p);
        n.putBoolean("on", in);
        if (in) {
            long left = Math.max(0, (get(d, "jail_end") - p.getServerWorld().getTime()) / 20);
            n.putLong("left", left);
            n.putInt("stage", (int) get(d, "jail_stage"));
        }
        CopsNet.send(p, "jail", n);
    }

    public static void onJoin(ServerPlayerEntity p) {
        if (inside(p)) {
            if (get(Life.get(p), "jail_stage") < 3) p.changeGameMode(GameMode.ADVENTURE);
            state(p);
        }
    }

    // ------------------------------------------------------------------ тик (раз в секунду)
    private static long lastGuards;

    public static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        boolean any = false;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!inside(p)) continue;
            any = true;
            LifeData d = Life.get(p);
            long now = w.getTime();
            long stage = get(d, "jail_stage");
            if (p.isDead()) {
                if (stage >= 3) d.flags.add("jail_failed");
                continue;
            }
            if (p.getServerWorld() != w) continue;
            long menuAt = get(d, "jail_menu_at");
            if (menuAt > 0 && now >= menuAt) {
                FunUtil.setLong(d, "jail_menu_at", 0);
                menu(p);
            }
            if (d.flags.contains("jail_failed")) {
                // погиб при побеге — снова в камере, срок больше
                d.flags.remove("jail_failed");
                FunUtil.setLong(d, "jail_stage", 0);
                FunUtil.setLong(d, "jail_end", now + 20 * 120);
                p.changeGameMode(GameMode.ADVENTURE);
                Wanted.clear(p);
                toCell(p, w);
                resetVent(w);
                dogsOff(w);
                Activities.title(p, "ПОБЕГ ПРОВАЛЕН", "Ты снова в камере · срок +2 минуты");
                state(p);
                continue;
            }
            if (stage == 0) {
                if (!cellBox(w).contains(p.getPos())) toCell(p, w); // из камеры не выйти
                if (now >= get(d, "jail_end")) {
                    release(p, "Срок окончен", "Ты отсидел своё. Веди себя хорошо!");
                    continue;
                }
            } else escape(p, w, d, stage, now);
            if (now % 100 < 20) state(p);
        }
        if (any && w.getTime() - lastGuards > 20 * 30) {
            lastGuards = w.getTime();
            guards(w);
        }
    }

    // ------------------------------------------------------------------ меню камеры (вместо телефона — G)
    public static void menu(ServerPlayerEntity p) {
        if (!inside(p)) return;
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        int st = (int) Math.max(1, get(d, "jail_stars"));
        long left = Math.max(0, (get(d, "jail_end") - w.getTime()) / 20);
        long stage = get(d, "jail_stage");
        long tod = w.getTimeOfDay() % 24000;
        boolean night = tod > 13000 && tod < 23000;
        boolean solo = p.getServer().getPlayerManager().getCurrentPlayerCount() == 1;
        CopsNet.Menu m = new CopsNet.Menu("jail", "Колония №7 · камера 1", 0xFFFF7043)
                .line("Осталось сидеть: " + left / 60 + ":" + String.format("%02d", left % 60) + " (" + days(left) + ")  · звёзд при аресте: " + "★".repeat(st))
                .line("Телефон изъят. Карта: $" + d.money + (d.dirty > 0 ? " · грязными $" + d.dirty : "") + ".")
                .line(stage > 0 ? "Идёт побег — меню закрыто для охраны." : night ? "Ночь: охрана дремлет — самое время для побега." : "День: охрана начеку, бежать лучше ночью.");
        if (stage == 0) {
            m.button("serve", "Отсидеть (промотать срок) — $" + 50 * st + " на передачки", 0xFF5A6ACF, d.money >= 50 * st);
            m.button("bribe", "Взятка охраннику — $" + 250 * st + " наличными", 0xFFFFB300, d.money + d.dirty >= 250 * st);
            m.button("lawyer", "Позвонить адвокату — $" + 400 * st + " с карты", 0xFF2FA84F, d.money >= 400 * st);
            if (night) m.button("escape", "Побег: расковырять вентиляцию", 0xFFE53935, true);
            else if (solo) m.button("night", "Дождаться ночи (для побега)", 0xFF7E57C2, true);
            else m.button("", "Побег — только ночью", 0xFF555555, false);
        }
        m.button("close", "Закрыть", 0xFF8E8E93, true);
        m.open(p);
    }

    public static void click(ServerPlayerEntity p, String id) {
        if (!inside(p)) return;
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        int st = (int) Math.max(1, get(d, "jail_stars"));
        if (get(d, "jail_stage") > 0 && !id.equals("close")) return;
        switch (id) {
            case "serve" -> {
                if (!Life.pay(p, 50 * st, "передачки")) return;
                long left = Math.max(0, (get(d, "jail_end") - w.getTime()) / 20);
                if (p.getServer().getPlayerManager().getCurrentPlayerCount() == 1) w.setTimeOfDay(w.getTimeOfDay() + Math.max(1, left / 60 * 3) * 1000L);
                release(p, "СРОК ОТБЫТ", "Время прошло. Ты вышел за ворота колонии");
            }
            case "bribe" -> {
                if (!Economy.payBlack(p, 250 * st, "взятка охраннику")) return;
                if (p.getRandom().nextFloat() < 0.75f) release(p, "ВЗЯТКА СРАБОТАЛА", "Охранник «забыл» запереть ворота");
                else {
                    FunUtil.setLong(d, "jail_end", get(d, "jail_end") + 20 * 60);
                    Activities.title(p, "НЕ ВЗЯЛ", "Охранник забрал деньги и доложил начальству · срок +1 минута");
                    state(p);
                }
            }
            case "lawyer" -> {
                if (!Life.pay(p, 400 * st, "адвокат")) return;
                Life.message(p, "Адвокат Лев Борисович", "Дело развалилось: свидетели путаются, улик нет. Вы свободны.");
                release(p, "ДЕЛО ЗАКРЫТО", "Адвокат вытащил тебя под залог");
            }
            case "night" -> {
                if (p.getServer().getPlayerManager().getCurrentPlayerCount() == 1) {
                    long day = w.getTimeOfDay() / 24000;
                    w.setTimeOfDay(day * 24000 + 13800 + (w.getTimeOfDay() % 24000 > 13800 ? 24000 : 0));
                }
                menu(p);
            }
            case "escape" -> {
                long tod = w.getTimeOfDay() % 24000;
                if (tod <= 13000 || tod >= 23000) return;
                FunUtil.setLong(d, "jail_stage", 1);
                FunUtil.setLong(d, "jail_vent", 0);
                Activities.title(p, "ПОБЕГ", "Встань у решётки вентиляции в задней стене и присядь (Shift)");
                FunUtil.target(p, Vec3d.ofCenter(at(floorY(w), 5, 0, 28)), "Вентиляция");
                state(p);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ побег
    private static void escape(ServerPlayerEntity p, ServerWorld w, LifeData d, long stage, long now) {
        int y = floorY(w);
        if (stage == 1) {
            if (!cellBox(w).contains(p.getPos())) toCell(p, w);
            Vec3d vent = Vec3d.ofCenter(at(y, 5, 0, 28));
            long prog = get(d, "jail_vent");
            if (p.isSneaking() && p.getPos().squaredDistanceTo(vent.x, p.getY(), vent.z) < 1.9 * 1.9) {
                prog++;
                FunUtil.setLong(d, "jail_vent", prog);
                w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.35f, 1.6f);
                p.sendMessage(Text.literal("Ковыряешь решётку ложкой: " + "▮".repeat((int) prog) + "▯".repeat((int) Math.max(0, 8 - prog))).formatted(Formatting.YELLOW), true);
                if (prog >= 8) {
                    for (int ly = 0; ly <= 1; ly++) w.setBlockState(at(y, 5, ly, 28), Blocks.AIR.getDefaultState());
                    w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.7f, 0.6f);
                    for (int ly = 0; ly <= 5; ly++)
                        w.setBlockState(at(y, 8, ly, 29), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
                    w.setBlockState(at(y, 8, 6, 30), Blocks.AIR.getDefaultState());
                    w.setBlockState(at(y, 8, 0, 32), Blocks.HAY_BLOCK.getDefaultState());
                    FunUtil.setLong(d, "jail_stage", 2);
                    FunUtil.target(p, Vec3d.ofCenter(at(y, 8, 0, 29)), "Лестница");
                    Activities.title(p, "РЕШЁТКА СНЯТА", "Пролезь во двор и тихо к лестнице у стены. Не попадись охране!");
                    state(p);
                }
            } else if (now % 200 < 20) p.sendMessage(Text.literal("Решётка вентиляции — в задней стене камеры. Встань вплотную и присядь (Shift)").formatted(Formatting.GRAY), true);
            return;
        }
        Box comp = compound(w);
        boolean out = !comp.contains(p.getPos());
        if (stage == 2) {
            boolean onWall = p.getY() >= y + 4.5;
            boolean spotted = false;
            for (CitizenEntity g : w.getEntitiesByClass(CitizenEntity.class, comp.expand(4), g -> g.getCommandTags().contains(GUARD_TAG) && g.isAlive()))
                if (g.distanceTo(p) < 9 && g.canSee(p)) spotted = true;
            if (onWall || out || spotted) alarm(p, w, d, spotted && !onWall && !out);
            return;
        }
        // stage 3: бег
        Vec3d c = comp.getCenter();
        double dist = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
        if (now % 60 < 20 && dist < 150) FunUtil.hint(p, "Беги! До свободы " + (int) (150 - dist) + " м — уйди от охраны и собак", Formatting.RED);
        if (dist >= 150) {
            clearState(p, d);
            dogsOff(w);
            FunUtil.clearHud(p);
            Activities.title(p, "ТЫ НА СВОБОДЕ", "Побег удался! Звёзды погаснут, если скроешься от полиции");
            Life.message(p, "Неизвестный", "Слышал, ты сбежал из «семёрки». Уважаю. Сменишь одежду — копы потеряют след.");
            state(p);
        }
    }

    private static void alarm(ServerPlayerEntity p, ServerWorld w, LifeData d, boolean inYard) {
        FunUtil.setLong(d, "jail_stage", 3);
        restoreMode(p, d);
        Wanted.add(p, 0, "побег из тюрьмы", 3);
        Activities.title(p, "ТРЕВОГА!", inYard ? "Охрана заметила тебя во дворе — к лестнице и через стену!" : "Побег из колонии — беги от охраны и собак!");
        w.playSound(null, p.getBlockPos(), ModContent.SIREN, SoundCategory.HOSTILE, 2f, 0.8f);
        FunUtil.target(p, null, "");
        Box comp = compound(w);
        for (CitizenEntity g : w.getEntitiesByClass(CitizenEntity.class, comp.expand(6), g -> g.getCommandTags().contains(GUARD_TAG) && g.isAlive())) {
            g.setStationary(false);
            g.attack(p);
        }
        int y = floorY(w);
        for (int i = 0; i < 2; i++) {
            WolfEntity dog = EntityType.WOLF.create(w);
            if (dog == null) continue;
            dog.refreshPositionAndAngles(X0 + 14.5 + i * 2, y, Z0 - 2.5, 0, 0);
            dog.setCustomName(Text.literal("Овчарка охраны"));
            dog.addCommandTag(DOG_TAG);
            w.spawnEntity(dog);
            dog.setAngryAt(p.getUuid());
            dog.setAngerTime(20 * 90);
            dog.setTarget(p);
        }
        state(p);
    }

    private static void dogsOff(ServerWorld w) {
        for (Entity e : w.getEntitiesByClass(WolfEntity.class, compound(w).expand(250, 40, 250), e -> e.getCommandTags().contains(DOG_TAG))) e.discard();
        for (Entity e : w.getEntitiesByClass(CitizenEntity.class, compound(w).expand(250, 40, 250), e -> e.getCommandTags().contains(GUARD_TAG) && !((CitizenEntity) e).isStationary()))
            e.discard();
        resetVent(w);
    }

    private static void resetVent(ServerWorld w) {
        if (!TownState.get(w).storyPlaces.containsKey("built:prison")) return;
        int y = floorY(w);
        for (int ly = 0; ly <= 1; ly++) w.setBlockState(at(y, 5, ly, 28), Blocks.IRON_BARS.getDefaultState());
        for (int ly = 0; ly <= 5; ly++) w.setBlockState(at(y, 8, ly, 29), Blocks.AIR.getDefaultState());
        w.setBlockState(at(y, 8, 6, 30), Blocks.IRON_BARS.getDefaultState());
        w.setBlockState(at(y, 8, 0, 32), Blocks.AIR.getDefaultState());
    }

    // ------------------------------------------------------------------ выход
    private static void restoreMode(ServerPlayerEntity p, LifeData d) {
        GameMode gm = GameMode.byId((int) get(d, "jail_gm"));
        p.changeGameMode(gm == GameMode.SPECTATOR ? GameMode.SURVIVAL : gm);
    }

    private static void clearState(ServerPlayerEntity p, LifeData d) {
        if (get(d, "jail_stage") < 3) restoreMode(p, d);
        d.flags.removeIf(f -> f.startsWith("jail_"));
        Life.sync(p);
    }

    public static void release(ServerPlayerEntity p, String title, String sub) {
        ServerWorld w = p.getServer().getOverworld();
        LifeData d = Life.get(p);
        clearState(p, d);
        FunUtil.clearHud(p);
        int y = floorY(w);
        p.teleport(w, X0 + 15.5, y, Z0 - 4.5, 180, 0);
        Activities.title(p, title, sub);
        Life.sound(p, ModContent.LEVEL_UP, 1.2f);
        state(p);
    }

    // ------------------------------------------------------------------ охрана
    private static final int[][] GUARDS = {{15, 2}, {12, 12}, {22, 17}};

    private static void guards(ServerWorld w) {
        if (!TownState.get(w).storyPlaces.containsKey("built:prison")) return;
        Box comp = compound(w);
        int have = w.getEntitiesByClass(CitizenEntity.class, comp.expand(2), g -> g.getCommandTags().contains(GUARD_TAG) && g.isAlive()).size();
        int y = floorY(w);
        for (int i = have; i < GUARDS.length; i++) {
            CitizenEntity g = new CitizenEntity(ModContent.CITIZEN, w);
            g.refreshPositionAndAngles(X0 + GUARDS[i][0] + 0.5, y, Z0 + GUARDS[i][1] + 0.5, w.random.nextFloat() * 360, 0);
            g.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, true, "Охранник");
            g.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            g.addCommandTag(GUARD_TAG);
            w.spawnEntity(g);
        }
    }

    // ------------------------------------------------------------------ постройка
    public static void ensureBuilt(ServerWorld w) {
        TownState t = TownState.get(w);
        if (t.storyPlaces.containsKey("built:prison")) return;
        for (int cx = (X0 - 4) >> 4; cx <= (X0 + SIZE + 4) >> 4; cx++)
            for (int cz = (Z0 - 6) >> 4; cz <= (Z0 + SIZE + 4) >> 4; cz++) w.getChunk(cx, cz);
        int y = Island.surface(X0 + SIZE / 2, Z0 + SIZE / 2) + 1;
        BlockState air = Blocks.AIR.getDefaultState(), stoneB = Blocks.STONE_BRICKS.getDefaultState(), smooth = Blocks.SMOOTH_STONE.getDefaultState();
        // площадка: подсыпка, двор из гравия, вокруг — бетонная отмостка
        for (int lx = -4; lx <= SIZE + 4; lx++)
            for (int lz = -6; lz <= SIZE + 4; lz++) {
                int s = Island.surface(X0 + lx, Z0 + lz);
                for (int yy = Math.min(s, y - 2); yy <= y - 2; yy++) set(w, X0 + lx, yy, Z0 + lz, Blocks.STONE.getDefaultState());
                boolean inside = lx > 0 && lx < SIZE && lz > 0 && lz < SIZE;
                set(w, X0 + lx, y - 1, Z0 + lz, inside ? ((lx * 7 + lz * 3) % 11 == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRAVEL.getDefaultState())
                        : Blocks.GRAY_CONCRETE.getDefaultState());
                for (int yy = y; yy <= Math.max(y + 12, s + 2); yy++) set(w, X0 + lx, yy, Z0 + lz, air);
            }
        // дорожка к воротам
        for (int lz = -6; lz <= 0; lz++) for (int lx = 13; lx <= 17; lx++) set(w, X0 + lx, y - 1, Z0 + lz, Blocks.POLISHED_ANDESITE.getDefaultState());
        // стены 6 блоков + решётка-«колючка» поверху, вышки по углам
        for (int lx = 0; lx <= SIZE; lx++)
            for (int lz = 0; lz <= SIZE; lz++) {
                if (lx != 0 && lx != SIZE && lz != 0 && lz != SIZE) continue;
                for (int ly = 0; ly <= 5; ly++) put(w, y, lx, ly, lz, ly == 2 ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : stoneB);
                put(w, y, lx, 6, lz, Blocks.IRON_BARS.getDefaultState());
            }
        for (int[] c : new int[][]{{0, 0}, {SIZE, 0}, {0, SIZE}, {SIZE, SIZE}}) {
            for (int ly = 0; ly <= 8; ly++) put(w, y, c[0], ly, c[1], Blocks.POLISHED_ANDESITE.getDefaultState());
            put(w, y, c[0], 9, c[1], Blocks.SEA_LANTERN.getDefaultState());
        }
        // ворота
        for (int lx = 13; lx <= 17; lx++) for (int ly = 0; ly <= 3; ly++) put(w, y, lx, ly, 0, Blocks.IRON_BARS.getDefaultState());
        for (int lx = 12; lx <= 18; lx++) put(w, y, lx, 4, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
        Disguise.label(w, X0 + 15.5, y + 6.5, Z0 - 0.5, "ИСПРАВИТЕЛЬНАЯ КОЛОНИЯ №7");
        // корпус с камерами (x 3..15, z 21..28)
        for (int lx = 3; lx <= 15; lx++)
            for (int lz = 21; lz <= 28; lz++) {
                boolean wall = lx == 3 || lx == 15 || lz == 21 || lz == 28;
                put(w, y, lx, -1, lz, Blocks.POLISHED_ANDESITE.getDefaultState());
                for (int ly = 0; ly <= 3; ly++) put(w, y, lx, ly, lz, wall ? (ly == 2 && lz == 21 && lx % 3 == 0 ? Blocks.IRON_BARS.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState()) : air);
                put(w, y, lx, 4, lz, (lx + lz) % 4 == 0 && !wall ? Blocks.GLOWSTONE.getDefaultState() : smooth);
            }
        // три камеры у задней стены: x 4..6, 8..10, 12..14; решётки спереди (z=24), стенки между камерами
        for (int lx = 4; lx <= 14; lx++) for (int ly = 0; ly <= 3; ly++) put(w, y, lx, ly, 24, ly == 3 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.IRON_BARS.getDefaultState());
        for (int lx : new int[]{7, 11}) for (int lz = 24; lz <= 27; lz++) for (int ly = 0; ly <= 3; ly++) put(w, y, lx, ly, lz, Blocks.WHITE_CONCRETE.getDefaultState());
        for (int k = 0; k < 3; k++) {
            int bx = 4 + k * 4;
            bed(w, at(y, bx, 0, 27), at(y, bx, 0, 26));
            put(w, y, bx + 2, 0, 27, Blocks.CAULDRON.getDefaultState());
        }
        // решётка вентиляции в задней стене камеры 1 (её ковыряют при побеге)
        for (int ly = 0; ly <= 1; ly++) put(w, y, 5, ly, 28, Blocks.IRON_BARS.getDefaultState());
        // дверь корпуса во двор
        put(w, y, 9, 0, 21, Blocks.IRON_DOOR.getDefaultState());
        put(w, y, 9, 1, 21, Blocks.IRON_DOOR.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
        // караулка (x 19..27, z 21..28)
        for (int lx = 19; lx <= 27; lx++)
            for (int lz = 21; lz <= 28; lz++) {
                boolean wall = lx == 19 || lx == 27 || lz == 21 || lz == 28;
                for (int ly = 0; ly <= 3; ly++) put(w, y, lx, ly, lz, wall ? (ly == 1 && (lx == 23 || lz == 24) ? Blocks.GLASS_PANE.getDefaultState() : Blocks.BRICKS.getDefaultState()) : air);
                put(w, y, lx, 4, lz, Blocks.DARK_OAK_SLAB.getDefaultState());
            }
        put(w, y, 22, 0, 21, air);
        put(w, y, 22, 1, 21, air);
        put(w, y, 21, 0, 26, Blocks.CRAFTING_TABLE.getDefaultState());
        put(w, y, 25, 0, 26, Blocks.BARREL.getDefaultState());
        put(w, y, 23, 2, 25, Blocks.LANTERN.getDefaultState());
        // двор: скамейки, турник, фонари
        for (int lx : new int[]{5, 6, 7}) put(w, y, lx, 0, 9, Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        for (int lx : new int[]{22, 23, 24}) put(w, y, lx, 0, 9, Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        for (int ly = 0; ly <= 2; ly++) {
            put(w, y, 10, ly, 14, Blocks.IRON_BARS.getDefaultState());
            put(w, y, 13, ly, 14, Blocks.IRON_BARS.getDefaultState());
        }
        for (int lx = 10; lx <= 13; lx++) put(w, y, lx, 3, 14, Blocks.IRON_BARS.getDefaultState());
        for (int[] l : new int[][]{{8, 5}, {22, 5}, {8, 18}, {22, 13}}) {
            put(w, y, l[0], 0, l[1], Blocks.ANDESITE_WALL.getDefaultState());
            put(w, y, l[0], 1, l[1], Blocks.ANDESITE_WALL.getDefaultState());
            put(w, y, l[0], 2, l[1], Blocks.LANTERN.getDefaultState());
        }
        t.storyPlaces.put("built:prison", "1");
        t.storyPlaces.put("prison_y", String.valueOf(y));
        t.markDirty();
        guards(w);
        com.yompix.novograd.Novograd.LOG.info("[cops] колония построена у {} {} {}", X0, y, Z0);
    }

    private static void bed(ServerWorld w, BlockPos head, BlockPos foot) {
        w.setBlockState(foot, Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.FOOT), 2 | 16);
        w.setBlockState(head, Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.HEAD), 2 | 16);
    }

    private static void put(ServerWorld w, int y0, int lx, int ly, int lz, BlockState s) {
        w.setBlockState(at(y0, lx, ly, lz), s, 2 | 16);
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, 2 | 16);
    }
}
