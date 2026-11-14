package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.entity.AnnaEntity;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.horror.Director;
import com.yompix.glukhoman.horror.Light;
import com.yompix.glukhoman.horror.Noise;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.horror.Sched;
import com.yompix.glukhoman.net.Net;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сюжет Глухомани: пять глав, шаги с целями, сцены, чекпоинты, концовки. Всё состояние — на сервере (StoryState),
 * общее для всех игроков: любой может двигать историю, остальные видят то же самое.
 */
public final class Story {
    private Story() {}

    private static MinecraftServer server;
    private static int sceneLock;
    private static final Map<UUID, Integer> LOST = new HashMap<>();

    public static boolean sceneRunning() {
        return sceneLock > 0;
    }

    static StoryState st() {
        return StoryState.get(server.getOverworld());
    }

    static ServerWorld w() {
        return server.getOverworld();
    }

    static List<ServerPlayerEntity> players() {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (!p.isSpectator()) l.add(p);
        return l;
    }

    // ===================== запуск =====================

    public static void serverStarted(MinecraftServer s) {
        server = s;
        Sched.clear();
        Noise.clear();
        sceneLock = 0;
        StoryState st = st();
        if (!isOurWorld()) return;
        if (!st.built) com.yompix.glukhoman.world.MapBuilder.build(w(), st);
        GameRules r = w().getGameRules();
        r.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, s);
        r.get(GameRules.DO_MOB_SPAWNING).set(false, s);
        r.get(GameRules.KEEP_INVENTORY).set(true, s);
        r.get(GameRules.DO_WEATHER_CYCLE).set(false, s);
        r.get(GameRules.DO_INSOMNIA).set(false, s);
        r.get(GameRules.DO_FIRE_TICK).set(false, s);
        r.get(GameRules.DO_PATROL_SPAWNING).set(false, s);
        r.get(GameRules.DO_TRADER_SPAWNING).set(false, s);
        r.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, s);
        BlockPos sp = st.point("start");
        if (sp != null) w().setSpawnPos(sp, 0);
        w().getWorldBorder().setCenter(0, -10);
        w().getWorldBorder().setSize(1000);
        if (st.chapter == 0) w().setTimeOfDay(12600);
        // после перезапуска возвращаем существ текущего шага
        if (st.chapter > 0 && st.ending.isEmpty()) Sched.after(40, () -> respawnCreatures(st));
    }

    public static boolean isOurWorld() {
        return server != null && server.getOverworld().getChunkManager().getChunkGenerator() instanceof com.yompix.glukhoman.world.TaigaChunkGenerator;
    }

    public static void join(ServerPlayerEntity p) {
        if (!isOurWorld()) return;
        StoryState st = st();
        Sched.after(20, () -> {
            sendHud(p);
            Sanity.set(p, Sanity.get(p));
            sendDifficulty(p);
            if (st.chapter == 0 && !st.flag("asked")) {
                Net.send(p, Net.msg("choose"));
            } else if (st.chapter > 0 && st.set("joined:" + p.getUuid())) {
                BlockPos cp = checkpoint();
                if (cp != null) p.teleport(w(), cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, p.getYaw(), p.getPitch());
                if (st.chapter >= 2 && !has(p, GContent.FLASHLIGHT)) {
                    give(p, new ItemStack(GContent.FLASHLIGHT));
                    give(p, new ItemStack(GContent.BATTERY, 2));
                }
                title(p, st.chapter);
            }
        });
        if (st.chapter == 0) st.set("joined:" + p.getUuid());
    }

    public static void chooseDifficulty(ServerPlayerEntity p, int d) {
        chooseDifficulty(p, d, false);
    }

    /** force — команда хозяина мира; иначе сложность выбирается только до начала истории. */
    public static void chooseDifficulty(ServerPlayerEntity p, int d, boolean force) {
        StoryState st = st();
        if (st.chapter != 0) {
            if (!force) return;
            st.difficulty = Math.max(0, Math.min(2, d));
            st.markDirty();
            Net.all(server, diffMsg());
            return;
        }
        st.difficulty = Math.max(0, Math.min(2, d));
        st.set("asked");
        Net.all(server, diffMsg());
        startChapter(1);
    }

    static NbtCompound diffMsg() {
        NbtCompound m = Net.msg("difficulty");
        m.putInt("d", st().difficulty);
        return m;
    }

    static void sendDifficulty(ServerPlayerEntity p) {
        Net.send(p, diffMsg());
    }

    // ===================== главы =====================

    public static void startChapter(int n) {
        StoryState st = st();
        st.chapter = n;
        st.markDirty();
        clearCreatures(n >= 4 && n < 5);
        Director.calm(server, 600);
        ServerWorld w = w();
        switch (n) {
            case 1 -> {
                w.setTimeOfDay(12600);
                tpSpread(st.point("start"));
                for (ServerPlayerEntity p : players()) {
                    p.setYaw(180);
                    Sanity.set(p, 100);
                }
                setStep("c1_road");
                titleAll(1, true);
                Sched.after(160, () -> seq("uaz", st.point("uaz"), false));
                Sched.after(900, () -> {
                    for (ServerPlayerEntity p : players()) Director.apparition(p, false);
                });
            }
            case 2 -> {
                w.setTimeOfDay(18000);
                setStep("c2_fuses");
                titleAll(2, false);
                spawn(GContent.LISTENER, st.point("school_listener"));
                Sched.after(100, () -> seq("numbers", null, true));
            }
            case 3 -> {
                w.setTimeOfDay(18000);
                setStep("c3_path");
                titleAll(3, false);
                Sched.after(400, () -> spawnPole());
            }
            case 4 -> {
                setStep("c4_lift");
                titleAll(4, false);
            }
            case 5 -> {
                w.setTimeOfDay(18000);
                tpSpread(st.point("cp5"));
                AnnaEntity a = spawn(GContent.ANNA, st.point("anna_station"));
                setStep("c5_bunker");
                titleAll(5, false);
                powerStation(true);
            }
        }
        for (ServerPlayerEntity p : players()) p.setSpawnPoint(w.getRegistryKey(), checkpoint(), 0, true, false);
    }

    static void respawnCreatures(StoryState st) {
        if (st.chapter == 2 && !st.flag("power")) ensure(GContent.LISTENER, st.point("school_listener"), 1);
        if (st.chapter == 3 && !st.step.equals("c3_path")) ensurePole();
        if (st.chapter == 3 && st.step.equals("c3_path")) Sched.after(200, Story::spawnPole);
        if (st.chapter == 4) {
            for (String m : new String[]{"mine_l1", "mine_l2", "mine_l3"}) ensure(GContent.LISTENER, st.point(m), 0);
            if (st.flag("anna_found") || st.step.equals("c4_anna")) ensure(GContent.ANNA, st.point("anna_mine"), 1);
            if (st.step.equals("c4_escape")) spawnChoir(st.point("choir_mine"), true);
        }
        if (st.chapter == 5) {
            ensure(GContent.ANNA, st.point("anna_station"), 1);
            powerStation(true);
            if (!st.step.equals("c5_bunker")) spawnChoir(st.point("choir_bunker"), false);
        }
        if (st.chapter >= 2 && st.flag("power")) powerVillage(false);
    }

    static void ensure(net.minecraft.entity.EntityType<? extends LivingEntity> type, BlockPos at, int max) {
        if (at == null) return;
        int have = w().getEntitiesByType(type, new Box(at).expand(60), e -> true).size();
        if (max == 0 ? have == 0 || have < 1 : have < max) spawn(type, at);
    }

    static void ensurePole() {
        if (w().getEntitiesByClass(PoleEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> !e.isApparition()).isEmpty()) spawnPole();
    }

    static void spawnPole() {
        StoryState st = st();
        if (st.chapter != 3) return;
        ServerPlayerEntity p = players().isEmpty() ? null : players().get(0);
        BlockPos at = st.point("gate");
        if (p != null) at = p.getBlockPos().add(0, 0, 30);
        int y = w().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        spawn(GContent.POLE, new BlockPos(at.getX(), y, at.getZ()));
    }

    static ChoirEntity spawnChoir(BlockPos at, boolean chase) {
        for (ChoirEntity c : w().getEntitiesByClass(ChoirEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true)) c.discard();
        ChoirEntity c = spawn(GContent.CHOIR, at);
        if (c != null) c.chase = chase;
        return c;
    }

    @SuppressWarnings("unchecked")
    static <T extends Entity> T spawn(net.minecraft.entity.EntityType<T> type, BlockPos at) {
        if (at == null) return null;
        T e = type.create(w());
        if (e == null) return null;
        e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        w().spawnEntity(e);
        return e;
    }

    static void clearCreatures(boolean keepAnna) {
        for (Entity e : w().getEntitiesByClass(LivingEntity.class, new Box(BlockPos.ORIGIN).expand(700),
                e -> e instanceof PoleEntity || e instanceof ListenerEntity || e instanceof ChoirEntity || (!keepAnna && e instanceof AnnaEntity)))
            e.discard();
    }

    // ===================== шаги и HUD =====================

    public static void setStep(String id) {
        StoryState st = st();
        st.step = id;
        st.markDirty();
        Glukhoman.LOG.info("Глухомань: шаг {} (глава {})", id, st.chapter);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendHud(p);
    }

    static String objectiveText() {
        StoryState st = st();
        String t = Texts.get().objective(st.step);
        return switch (st.step) {
            case "c2_fuses", "c2_power" -> t + " (найдено " + st.count("fuses_found") + "/3, в щите " + st.count("fuses_in") + "/3)";
            case "c4_marks" -> t + " (" + st.count("marks") + "/3)";
            case "c5_amps" -> t + " (" + st.count("amps") + "/3)";
            default -> t;
        };
    }

    public static void sendHud(ServerPlayerEntity p) {
        StoryState st = st();
        NbtCompound m = Net.msg("hud");
        m.putInt("ch", st.chapter);
        m.putString("title", Texts.get().chapterTitle(st.chapter));
        m.putString("obj", st.chapter == 0 ? "" : objectiveText());
        m.putString("step", st.step);
        m.putString("ending", st.ending);
        Net.send(p, m);
    }

    static void hudAll() {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendHud(p);
    }

    static void titleAll(int ch, boolean big) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) title(p, ch);
        for (ServerPlayerEntity p : players()) w().playSound(null, p.getBlockPos(), GContent.sound("title_hit"), SoundCategory.MASTER, 1f, 1f);
    }

    static void title(ServerPlayerEntity p, int ch) {
        NbtCompound m = Net.msg("title");
        m.putString("a", "Глава " + roman(ch));
        m.putString("b", Texts.get().chapterTitle(ch));
        m.putString("c", Texts.get().chapterSub(ch));
        Net.send(p, m);
    }

    static String roman(int n) {
        return new String[]{"", "I", "II", "III", "IV", "V"}[Math.max(0, Math.min(5, n))];
    }

    public static BlockPos checkpoint() {
        StoryState st = st();
        return switch (st.chapter) {
            case 1 -> st.step.equals("c1_road") ? st.point("cp1") : st.point("cp2");
            case 2 -> st.point("cp2");
            case 3 -> st.step.equals("c3_path") ? st.point("cp3") : st.point("cp5");
            case 4 -> st.step.equals("c4_lift") ? st.point("cp4top") : st.point("cp4");
            case 5 -> st.point("cp5");
            default -> st.point("start");
        };
    }

    // ===================== реплики, записки, звуки =====================

    /** Проиграть последовательность реплик: в точке (рация/плёнка) для тех, кто рядом, или всем (global). */
    public static int seq(String id, BlockPos at, boolean global) {
        int t = 0;
        for (String line : Texts.get().sequence(id)) {
            final String l = line;
            Sched.after(t + 1, () -> voice(l, at, global));
            t += (int) (Texts.get().lineText(line).length() / 13.0 * 20) + 30;
        }
        return t;
    }

    public static void voice(String line, BlockPos at, boolean global) {
        NbtCompound m = Net.msg("voice");
        m.putString("id", line);
        m.putString("who", Texts.get().lineWho(line));
        m.putString("text", Texts.get().lineText(line));
        m.putInt("dur", (int) (Texts.get().lineText(line).length() / 13.0 * 20) + 30);
        if (at != null) {
            m.putInt("x", at.getX());
            m.putInt("y", at.getY());
            m.putInt("z", at.getZ());
        }
        m.putBoolean("global", global || at == null);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            if (global || at == null || p.getBlockPos().getSquaredDistance(at) < 40 * 40) Net.send(p, m);
    }

    static void note(ServerPlayerEntity p, String id) {
        NbtCompound m = Net.msg("note");
        m.putString("title", Texts.get().noteTitle(id));
        NbtList pages = new NbtList();
        for (String s : Texts.get().notePages(id)) pages.add(NbtString.of(s));
        m.put("pages", pages);
        Net.send(p, m);
        StoryState st = st();
        if (st.set("book:" + id + ":" + p.getUuid())) give(p, book(id));
    }

    static ItemStack book(String id) {
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        for (String s : Texts.get().notePages(id)) pages.add(RawFilteredPair.of(Text.literal(s)));
        String author = Texts.get().noteAuthor(id);
        b.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of(Texts.get().noteTitle(id)),
                author.isEmpty() ? "—" : author, 0, pages, true));
        return b;
    }

    static void msg(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal(text).formatted(Formatting.GRAY), true);
    }

    static void msgAll(String text) {
        for (ServerPlayerEntity p : players()) msg(p, text);
    }

    static void fx(ServerPlayerEntity p, String kind, int ticks) {
        NbtCompound m = Net.msg("fx");
        m.putString("k", kind);
        m.putInt("t", ticks);
        Net.send(p, m);
    }

    static void fxAll(String kind, int ticks) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) fx(p, kind, ticks);
    }

    static void sound(BlockPos at, String id, float vol, float pitch) {
        if (at != null) w().playSound(null, at, GContent.sound(id), SoundCategory.AMBIENT, vol, pitch);
    }

    static boolean has(ServerPlayerEntity p, Item it) {
        return p.getInventory().count(it) > 0;
    }

    static int take(ServerPlayerEntity p, Item it, int max) {
        int took = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size() && took < max; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(it)) {
                int n = Math.min(max - took, s.getCount());
                s.decrement(n);
                took += n;
            }
        }
        return took;
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        if (s.isOf(GContent.FLASHLIGHT) || s.isOf(GContent.OIL_LAMP)) s.setDamage(s.getMaxDamage());
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    /** Всех игроков — к точке, но рядом друг с другом, а не в одном блоке. */
    static void tpSpread(BlockPos at) {
        if (at == null) return;
        int i = 0;
        for (ServerPlayerEntity p : players()) {
            int dx = new int[]{0, 1, -1, 0, 1}[i % 5], dz = new int[]{0, 0, 0, 1, 1}[i % 5];
            tp(p, at.add(dx, 0, dz));
            i++;
        }
    }

    static void tp(ServerPlayerEntity p, BlockPos at) {
        if (at != null) p.teleport(w(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYaw(), p.getPitch());
    }

    // ===================== зоны =====================

    public static boolean inZone(ServerPlayerEntity p, String zone) {
        StoryState st = StoryState.get(p.getServerWorld());
        BlockPos a = st.point(zone + "_a"), b = st.point(zone + "_b");
        if (a == null || b == null) return false;
        BlockPos q = p.getBlockPos();
        return q.getX() >= a.getX() && q.getX() <= b.getX() && q.getY() >= a.getY() && q.getY() <= b.getY() && q.getZ() >= a.getZ() && q.getZ() <= b.getZ();
    }

    /** Безопасные комнаты: дом Анны (кроме сцены со стуком), избушка, радиорубка (если есть свет), убежище. */
    public static boolean inSafe(ServerPlayerEntity p) {
        StoryState st = StoryState.get(p.getServerWorld());
        if (st.step.equals("c1_knock")) return false;
        for (String s : new String[]{"safe_anna", "safe_hunter", "safe_station", "safe_shelter"}) {
            BlockPos q = st.point(s);
            if (q == null) continue;
            if (s.equals("safe_station") && !st.flag("station_power")) continue;
            if (q.getSquaredDistance(p.getPos()) < 5.5 * 5.5) return true;
        }
        return false;
    }

    // ===================== такт =====================

    public static void tick(MinecraftServer s) {
        server = s;
        if (!isOurWorld()) return;
        long t = s.getTicks();
        Sched.tick(t);
        StoryState st = st();
        if (sceneLock > 0) sceneLock--;
        if (st.chapter == 0) return;
        if (t % 5 == 0) for (ServerPlayerEntity p : players()) Noise.playerStep(p);
        if (t % 10 != 0) return;
        for (ServerPlayerEntity p : players()) area(p, st);
        if (t % 20 == 0 && st.ending.isEmpty()) for (ServerPlayerEntity p : players()) bounds(p, st);
        if (st.step.equals("c1_knock") && !st.flag("door_opened")) {
            BlockPos d = st.point("anna_door");
            BlockState ds = d == null ? null : w().getBlockState(d);
            if (ds != null && ds.getBlock() instanceof DoorBlock && ds.get(DoorBlock.OPEN)) doorOpened(d);
        }
    }

    static void area(ServerPlayerEntity p, StoryState st) {
        switch (st.step) {
            case "c1_road" -> {
                if (inZone(p, "village_in")) setStep("c1_house");
            }
            case "c1_house" -> {
                if (inZone(p, "anna_house")) {
                    setStep("c1_radio");
                    msg(p, "Здесь пахнет печью и холодом. Рация на столе.");
                }
            }
            case "c3_path" -> {
                if (inZone(p, "station")) {
                    setStep(has(p, GContent.CANISTER) || st.flag("canister") ? "c3_power" : "c3_power");
                    Director.calm(server, 300);
                }
            }
            case "c3_run" -> {
                if (inZone(p, "mine_top")) {
                    for (ListenerEntity l : w().getEntitiesByClass(ListenerEntity.class, new Box(p.getBlockPos()).expand(300), e -> true)) l.discard();
                    fxAll("chase_end", 0);
                    Director.calm(server, 600);
                    startChapter(4);
                }
            }
            case "c5_bunker" -> {
                if (inZone(p, "bunker")) {
                    setStep("c5_amps");
                    spawnChoir(st.point("choir_bunker"), false);
                    voice("final_2", st.point("choir_bunker"), true);
                }
            }
            default -> {
            }
        }
    }

    /** Мягкая граница главы: «лес не отпускает». */
    static void bounds(ServerPlayerEntity p, StoryState st) {
        if (p.isCreative()) return;
        BlockPos q = p.getBlockPos();
        boolean ok;
        switch (st.chapter) {
            case 1, 2 -> ok = q.getZ() > 22 && q.getZ() < 254 && Math.abs(q.getX()) < 90;
            case 3 -> ok = (q.getZ() > -275 && q.getZ() < 254 && Math.abs(q.getX()) < 70) || (q.getX() > -160 && q.getX() < 0 && q.getZ() > -265 && q.getZ() < -140);
            default -> ok = q.getZ() > -290 && q.getZ() < 254 && q.getX() > -170 && q.getX() < 80;
        }
        int n = ok ? 0 : LOST.getOrDefault(p.getUuid(), 0) + 1;
        LOST.put(p.getUuid(), n);
        if (n == 3) {
            msg(p, "Лес не отпускает. Тропа пропала…");
            fx(p, "lost", 200);
            Director.far(p, "voice.whisper_2", 0.8f, 2);
        }
        if (n >= 10) {
            LOST.put(p.getUuid(), 0);
            fx(p, "fade", 60);
            Sanity.add(p, -10);
            Sched.after(30, () -> {
                tp(p, checkpoint());
                msg(p, "Ты не помнишь, как вернулся.");
            });
        }
    }

    // ===================== действия игроков =====================

    public static boolean interact(ServerPlayerEntity p, BlockPos pos, String key) {
        if (!isOurWorld()) return false;
        StoryState st = st();
        if (st.chapter == 0) {
            Net.send(p, Net.msg("choose"));
            return true;
        }
        Noise.emit(p.getServerWorld(), Vec3d.ofCenter(pos), 6, p);
        if (key.startsWith("note:")) {
            String id = key.substring(5);
            note(p, id);
            if (id.equals("anna_log") && st.step.equals("c3_log")) {
                st.set("log_read");
                setStep("c3_key");
                msg(p, "На полях журнала: «сейф Шумова — 0917».");
            }
            return true;
        }
        if (key.startsWith("crate")) return crate(p, pos, key, st);
        if (key.startsWith("mark_")) return mark(p, key, st);
        if (key.startsWith("amp_")) return amp(p, pos, key, st);
        switch (key) {
            case "radio_uaz" -> seq("uaz", pos, false);
            case "radio_anna" -> {
                if (st.step.equals("c1_radio")) {
                    int d = seq("anna_last", pos, false);
                    sceneLock = d;
                    Sched.after(d + 20, () -> {
                        if (st.step.equals("c1_radio")) setStep(st.flag("batteries") ? "c1_night" : "c1_battery");
                    });
                } else radioStatic(pos);
            }
            case "radio_council" -> {
                if (st.step.equals("c2_call")) {
                    int d = seq("council", pos, false);
                    sceneLock = d;
                    Sched.after(d + 20, () -> {
                        st.set("called");
                        setStep("c2_key");
                    });
                } else radioStatic(pos);
            }
            case "radio_station" -> {
                if (st.flag("station_power")) seq("numbers", pos, false);
                else msg(p, "Рация мертва — нет питания.");
            }
            case "tape_1", "tape_2", "tape_3" -> {
                if (!st.flag("station_power")) {
                    msg(p, "Магнитофон не работает — нет питания.");
                    return true;
                }
                sound(pos, "tape_click", 1f, 1f);
                seq("tape" + key.charAt(5), pos, false);
            }
            case "stove_anna" -> stove(p, st);
            case "fusebox" -> fusebox(p, pos, st);
            case "lock_gate" -> {
                if (st.chapter == 2 && has(p, GContent.GATE_KEY)) {
                    take(p, GContent.GATE_KEY, 1);
                    BlockPos g = st.point("gate");
                    for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) w().setBlockState(new BlockPos(x, g.getY() + y, g.getZ() - 1), Blocks.AIR.getDefaultState());
                    sound(pos, "door_creak", 1.2f, 0.6f);
                    startChapter(3);
                } else msg(p, st.chapter >= 3 ? "Ворота открыты." : "Тяжёлый замок. Нужен ключ от ворот просеки.");
            }
            case "generator" -> {
                if (st.flag("station_power")) {
                    msg(p, "Генератор тарахтит.");
                } else if (has(p, GContent.CANISTER)) {
                    take(p, GContent.CANISTER, 1);
                    sound(pos, "generator", 1.4f, 1f);
                    powerStation(true);
                    Director.calm(server, 400);
                    if (st.step.equals("c3_power")) setStep("c3_log");
                } else msg(p, "Бак пустой. Нужна канистра (избушка охотника на просеке).");
            }
            case "lift_top" -> {
                if (st.chapter < 4) {
                    msg(p, "Лифт обесточен. Сначала разберись на станции.");
                } else if (st.step.equals("c4_lift")) {
                    sceneLock = 120;
                    sound(pos, "lift", 1.5f, 1f);
                    fxAll("fade", 80);
                    fxAll("shake", 70);
                    Sched.after(50, () -> {
                        tpSpread(st.point("cp4"));
                        for (String m : new String[]{"mine_l1", "mine_l2", "mine_l3"}) spawn(GContent.LISTENER, st.point(m));
                        setStep("c4_marks");
                        for (ServerPlayerEntity q : players()) q.setSpawnPoint(w().getRegistryKey(), st.point("cp4"), 0, true, false);
                    });
                } else {
                    sound(pos, "lift", 1f, 1f);
                    fx(p, "fade", 50);
                    Sched.after(30, () -> tp(p, st.point("cp4")));
                }
            }
            case "lift_bottom" -> {
                if (st.step.equals("c4_escape")) {
                    sceneLock = 160;
                    sound(pos, "lift", 1.5f, 1f);
                    fxAll("fade", 120);
                    fxAll("shake", 60);
                    Sched.after(60, () -> {
                        for (ChoirEntity c : w().getEntitiesByClass(ChoirEntity.class, new Box(pos).expand(200), e -> true)) c.discard();
                        fxAll("chase_end", 0);
                        startChapter(5);
                    });
                } else if (st.chapter >= 4) {
                    sound(pos, "lift", 1f, 1f);
                    fx(p, "fade", 50);
                    Sched.after(30, () -> tp(p, st.point("cp4top")));
                }
            }
            case "anna" -> anna(p, st);
            case "transmitter" -> transmitter(p, st);
            default -> {
                return false;
            }
        }
        return true;
    }

    static void radioStatic(BlockPos pos) {
        sound(pos, "radio_static", 1f, 1f);
    }

    /** Ящики: личные вещи — каждому игроку свои, сюжетные — один раз на всех. */
    static boolean crate(ServerPlayerEntity p, BlockPos pos, String key, StoryState st) {
        record Loot(boolean personal, ItemStack... items) {}
        Loot loot = switch (key) {
            case "crate_start" -> new Loot(true, new ItemStack(GContent.PEBBLE, 4), new ItemStack(Items.BREAD, 3));
            case "crate_anna" -> new Loot(true, new ItemStack(GContent.FLASHLIGHT), new ItemStack(GContent.PEBBLE, 3));
            case "crate_shop", "crate_shop2" -> new Loot(true, new ItemStack(GContent.BATTERY, 3), new ItemStack(Items.BREAD, 2));
            case "crate_council_fuse", "crate_school_fuse", "crate_garage_fuse" -> new Loot(false, new ItemStack(GContent.FUSE));
            case "crate_council_safe" -> new Loot(false, new ItemStack(GContent.GATE_KEY));
            case "crate_school", "crate_garage" -> new Loot(true, new ItemStack(GContent.BATTERY, 1), new ItemStack(GContent.PEBBLE, 3));
            case "crate_hunter_kit" -> new Loot(true, new ItemStack(GContent.OIL_LAMP), new ItemStack(GContent.OIL, 2), new ItemStack(GContent.BATTERY, 2));
            case "crate_hunter_can" -> new Loot(false, new ItemStack(GContent.CANISTER));
            case "crate_shumov" -> new Loot(false, new ItemStack(GContent.MINE_KEY));
            default -> new Loot(true, new ItemStack(GContent.BATTERY, 2), new ItemStack(GContent.OIL, 1), new ItemStack(GContent.PEBBLE, 2));
        };
        if (key.equals("crate_council_safe") && !st.flag("called")) {
            msg(p, "Сейф заперт. На дверце: «ключ от просеки — по связи с базой».");
            return true;
        }
        if (key.equals("crate_shumov") && !st.flag("log_read")) {
            msg(p, "Кодовый сейф. Четыре цифры. Код где-то записан…");
            return true;
        }
        if (key.endsWith("_fuse") && st.chapter < 2) {
            msg(p, "Ящик с хламом. Днём тут делать нечего.");
            return true;
        }
        String flag = loot.personal ? key + ":" + p.getUuid() : key;
        if (!st.set(flag)) {
            msg(p, "Пусто.");
            return true;
        }
        for (ItemStack s : loot.items) give(p, s.copy());
        sound(pos, "door_creak", 0.5f, 1.6f);
        StringBuilder sb = new StringBuilder("Найдено: ");
        for (int i = 0; i < loot.items.length; i++) sb.append(i > 0 ? ", " : "").append(loot.items[i].getName().getString()).append(loot.items[i].getCount() > 1 ? " ×" + loot.items[i].getCount() : "");
        msg(p, sb.toString());
        switch (key) {
            case "crate_shop", "crate_shop2" -> {
                if (st.set("batteries") && st.step.equals("c1_battery")) nightFalls();
            }
            case "crate_council_fuse", "crate_school_fuse", "crate_garage_fuse" -> {
                st.inc("fuses_found");
                if (st.count("fuses_found") >= 3 && st.step.equals("c2_fuses")) setStep("c2_power");
                else hudAll();
            }
            case "crate_council_safe" -> setStep("c2_gate");
            case "crate_hunter_can" -> st.set("canister");
            case "crate_shumov" -> chase();
            default -> {
            }
        }
        return true;
    }

    // ---------- глава 1: ночь и стук ----------
    static void nightFalls() {
        setStep("c1_night");
        sceneLock = 200;
        for (int i = 1; i <= 20; i++) {
            final int k = i;
            Sched.after(i * 6, () -> w().setTimeOfDay(12600 + k * 270L));
        }
        msgAll("Темнеет. Быстро, как будто кто-то задёрнул небо.");
        for (ServerPlayerEntity p : players()) Director.far(p, "distant_scream", 0.8f, 60);
    }

    static void stove(ServerPlayerEntity p, StoryState st) {
        if (!st.step.equals("c1_night")) {
            Sanity.add(p, 15);
            msg(p, "Печь ещё тёплая. Становится спокойнее.");
            return;
        }
        for (ServerPlayerEntity q : players()) if (!inZone(q, "anna_house")) {
            msg(p, "Нужно, чтобы все были в доме: " + q.getName().getString() + " снаружи.");
            return;
        }
        knockScene();
    }

    static void knockScene() {
        StoryState st = st();
        setStep("c1_knock");
        BlockPos door = st.point("anna_door");
        BlockState ds = w().getBlockState(door);
        if (ds.getBlock() instanceof DoorBlock && ds.get(DoorBlock.OPEN)) closeDoor(door);
        sceneLock = 1000;
        List<BlockPos> lamps = lamps("anna");
        Sched.after(40, () -> Director.setLamps(w(), lamps, false));
        Sched.after(41, () -> sound(lamps.isEmpty() ? door : lamps.get(0), "lamp_buzz", 1f, 0.7f));
        BlockPos out = door.east();
        int[] at = {120, 260, 420};
        String[] lines = {"knock_1", "knock_2", "knock_3"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Sched.after(at[i], () -> {
                if (st.flag("door_opened")) return;
                sound(door, "knock", 1.2f + k * 0.2f, 1f - k * 0.1f);
                Sched.after(30, () -> {
                    if (!st.flag("door_opened")) voice(lines[k], out, false);
                });
            });
        }
        Sched.after(620, () -> {
            if (!st.flag("door_opened")) {
                sound(door, "knock", 1.8f, 0.6f);
                for (ServerPlayerEntity p : players()) if (inZone(p, "anna_house")) Sanity.add(p, -10);
            }
        });
        Sched.after(760, Story::endChapter1);
    }

    static void doorOpened(BlockPos door) {
        StoryState st = st();
        st.set("door_opened");
        BlockPos out = door.east(2);
        PoleEntity pole = spawn(GContent.POLE, out);
        if (pole != null) {
            pole.makeApparition(null);
            for (ServerPlayerEntity p : players()) if (p.squaredDistanceTo(Vec3d.ofCenter(out)) < 400) pole.lookAtPlayer(p);
        }
        for (ServerPlayerEntity p : players()) {
            if (p.squaredDistanceTo(Vec3d.ofCenter(door)) > 20 * 20) continue;
            boolean soft = Director.NO_SCARES.contains(p.getUuid());
            w().playSound(null, p.getBlockPos(), GContent.sound(soft ? "stinger_soft" : "stinger"), SoundCategory.HOSTILE, 1.2f, 1f);
            fx(p, soft ? "shake" : "jolt", 20);
            Sanity.add(p, -35);
        }
        Sched.after(25, () -> {
            closeDoor(door);
            sound(door, "door_creak", 1.4f, 0.5f);
            for (PoleEntity e : w().getEntitiesByClass(PoleEntity.class, new Box(door).expand(8), e -> true)) e.vanish();
        });
        Sched.after(80, () -> voice("whisper_3", door, false));
        Sched.after(200, Story::endChapter1);
    }

    static void closeDoor(BlockPos d) {
        BlockState s = w().getBlockState(d);
        if (s.getBlock() instanceof DoorBlock) {
            w().setBlockState(d, s.with(DoorBlock.OPEN, false), 10);
            BlockState u = w().getBlockState(d.up());
            if (u.getBlock() instanceof DoorBlock) w().setBlockState(d.up(), u.with(DoorBlock.OPEN, false), 10);
        }
    }

    static void endChapter1() {
        StoryState st = st();
        if (st.chapter != 1) return;
        fxAll("fade", 140);
        Sched.after(70, () -> {
            Director.setLamps(w(), lamps("anna"), true);
            for (ServerPlayerEntity p : players()) {
                tp(p, st.point("cp2"));
                if (!has(p, GContent.FLASHLIGHT)) give(p, new ItemStack(GContent.FLASHLIGHT));
            }
            msgAll("Ты проснулся. Часы показывают 4:10. За окном — ночь. Утро не наступило.");
            startChapter(2);
        });
    }

    // ---------- глава 2: подстанция ----------
    static void fusebox(ServerPlayerEntity p, BlockPos pos, StoryState st) {
        if (st.flag("power")) {
            msg(p, "Щит гудит. Свет есть.");
            return;
        }
        if (st.chapter < 2) {
            msg(p, "Щит подстанции. Трёх предохранителей не хватает.");
            return;
        }
        int n = take(p, GContent.FUSE, 3);
        if (n == 0) {
            msg(p, "Пустые гнёзда. Нужны предохранители (" + st.count("fuses_in") + "/3 на месте).");
            return;
        }
        for (int i = 0; i < n; i++) st.inc("fuses_in");
        sound(pos, "fuse", 1f, 1f);
        Noise.emit(w(), Vec3d.ofCenter(pos), 16, p);
        if (st.count("fuses_in") >= 3) {
            st.set("power");
            sceneLock = 120;
            sound(pos, "power_on", 2f, 1f);
            Sched.after(20, () -> powerVillage(true));
            for (ServerPlayerEntity q : players()) Sanity.add(q, 25);
            Director.calm(server, 500);
            Sched.after(60, () -> setStep("c2_call"));
            msgAll("Свет! В посёлке загораются фонари.");
        } else hudAll();
    }

    static void powerVillage(boolean flickerIn) {
        ServerWorld w = w();
        List<BlockPos> l = new ArrayList<>(lamps("village"));
        l.addAll(lamps("houses"));
        l.addAll(lamps("school"));
        if (flickerIn) Director.flicker(w, l, 10);
        else Director.setLamps(w, l, true);
        for (ListenerEntity e : w.getEntitiesByClass(ListenerEntity.class, new Box(new BlockPos(0, 64, 90)).expand(80), e -> true)) e.discard();
    }

    static List<BlockPos> lamps(String group) {
        List<BlockPos> out = new ArrayList<>();
        for (long l : st().lamps.getOrDefault(group, List.of())) out.add(BlockPos.fromLong(l));
        return out;
    }

    // ---------- глава 3: станция и погоня ----------
    static void powerStation(boolean on) {
        st().set("station_power");
        List<BlockPos> l = new ArrayList<>(lamps("station"));
        l.addAll(lamps("mast"));
        Director.setLamps(w(), l, on);
    }

    static void chase() {
        StoryState st = st();
        setStep("c3_run");
        sceneLock = 200;
        Director.setLamps(w(), lamps("station"), false);
        sound(st.point("station_hall"), "power_on", 1.5f, 0.5f);
        fxAll("chase", 0);
        PoleEntity pole = spawn(GContent.POLE, st.point("station_pole"));
        for (PoleEntity e : w().getEntitiesByClass(PoleEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> !e.isApparition() && e != pole)) e.discard();
        spawn(GContent.LISTENER, st.point("station_l1"));
        spawn(GContent.LISTENER, st.point("station_l2"));
        for (ServerPlayerEntity p : players()) Noise.emit(w(), p.getPos(), 40, p);
        msgAll("Свет гаснет. В коридоре кто-то стоит.");
        Sched.after(400, () -> powerStation(true));
    }

    // ---------- глава 4: шахта ----------
    static boolean mark(ServerPlayerEntity p, String key, StoryState st) {
        note(p, key);
        if (st.chapter == 4 && st.set(key)) {
            st.inc("marks");
            hudAll();
            if (st.count("marks") >= 3 && st.step.equals("c4_marks")) {
                setStep("c4_anna");
                BlockPos d = st.point("shelter_door");
                BlockState s = w().getBlockState(d);
                if (s.getBlock() instanceof DoorBlock) {
                    w().setBlockState(d, s.with(DoorBlock.OPEN, true), 10);
                    BlockState u = w().getBlockState(d.up());
                    if (u.getBlock() instanceof DoorBlock) w().setBlockState(d.up(), u.with(DoorBlock.OPEN, true), 10);
                }
                sound(d, "door_creak", 1.5f, 0.7f);
                ensure(GContent.ANNA, st.point("anna_mine"), 1);
                msgAll("Где-то впереди скрипнула железная дверь.");
            }
        }
        return true;
    }

    static void anna(ServerPlayerEntity p, StoryState st) {
        if (st.step.equals("c4_anna")) {
            st.set("anna_found");
            for (ServerPlayerEntity q : players()) if (q.squaredDistanceTo(p) < 100) note(q, "anna_meet");
            give(p, new ItemStack(GContent.TUNING_FORK));
            msgAll(p.getName().getString() + " получил камертон Шумова.");
            setStep("c4_escape");
            sceneLock = 100;
            Sched.after(140, () -> {
                fxAll("shake", 60);
                fxAll("chase", 0);
                voice("choir_mine", null, true);
                spawnChoir(st.point("choir_mine"), true);
                for (AnnaEntity a : w().getEntitiesByClass(AnnaEntity.class, new Box(p.getBlockPos()).expand(40), e -> true)) a.discard();
                msgAll("Анна уходит другим ходом. Бегите к лифту!");
            });
        } else if (st.chapter == 5) {
            note(p, "anna_meet");
        } else msg(p, "Анна смотрит на тебя и показывает на уши: не слышу.");
    }

    // ---------- глава 5: бункер ----------
    static boolean amp(ServerPlayerEntity p, BlockPos pos, String key, StoryState st) {
        if (!st.step.equals("c5_amps")) {
            msg(p, st.chapter == 5 ? "Усилитель «Тишина-3». Включать — по журналу Шумова." : "Мёртвый железный шкаф.");
            return true;
        }
        if (st.flag(key)) {
            msg(p, "Этот уже работает.");
            return true;
        }
        if (st.flag("warming:" + key)) return true;
        st.set("warming:" + key);
        msg(p, "Усилитель греется… Будь рядом 5 секунд. Хор идёт на шум!");
        for (int i = 0; i < 5; i++) Sched.after(i * 20 + 1, () -> {
            sound(pos, "generator", 1.2f, 1.4f);
            Noise.emit(w(), Vec3d.ofCenter(pos), 48, p);
        });
        Sched.after(100, () -> {
            st.flags.remove("warming:" + key);
            if (p.isAlive() && p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 5 * 5) {
                st.set(key);
                st.inc("amps");
                sound(pos, "power_on", 1.6f, 1.2f);
                Director.setLamps(w(), lamps("bunker"), true);
                if (st.count("amps") >= 3) setStep("c5_tx");
                else hudAll();
            } else msg(p, "Усилитель остыл. Нужно стоять рядом.");
        });
        return true;
    }

    static void transmitter(ServerPlayerEntity p, StoryState st) {
        if (!st.step.equals("c5_tx")) {
            msg(p, st.chapter == 5 ? "Передатчик. Сначала усилители." : "Передатчик молчит.");
            return;
        }
        if (!has(p, GContent.TUNING_FORK)) {
            msg(p, "Нужен камертон. Он у того, кто говорил с Анной.");
            return;
        }
        if (!st.set("choosing")) return;
        sceneLock = 400;
        for (ChoirEntity c : w().getEntitiesByClass(ChoirEntity.class, new Box(p.getBlockPos()).expand(80), e -> true)) {
            c.stun(p);
        }
        int d = seq("final", p.getBlockPos(), true);
        Sched.after(d, () -> {
            Net.send(p, Net.msg("choice"));
            for (ServerPlayerEntity q : players()) if (q != p) msg(q, p.getName().getString() + " держит камертон над передатчиком…");
        });
    }

    public static void choose(ServerPlayerEntity p, String c) {
        StoryState st = st();
        if (!st.step.equals("c5_tx") || !st.flag("choosing") || !st.ending.isEmpty()) return;
        take(p, GContent.TUNING_FORK, 1);
        ending(c.equals("B") ? "B" : "A");
    }

    static void ending(String e) {
        StoryState st = st();
        st.ending = e;
        st.markDirty();
        setStep("end");
        sceneLock = 2000;
        fxAll(e.equals("A") ? "white" : "black", 140);
        fxAll("chase_end", 0);
        ServerWorld w = w();
        for (ChoirEntity c : w.getEntitiesByClass(ChoirEntity.class, new Box(BlockPos.ORIGIN).expand(700), x -> true)) {
            w.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL, c.getX(), c.getY() + 1.5, c.getZ(), 80, 1, 1.5, 1, 0.05);
            c.discard();
        }
        Sched.after(60, () -> {
            clearCreatures(true);
            w.setTimeOfDay(e.equals("A") ? 1000 : 13000);
            for (ServerPlayerEntity p : players()) {
                tp(p, st.point("cp5"));
                Sanity.set(p, 100);
                Light.off(p);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
            }
            NbtCompound m = Net.msg("title");
            m.putString("a", "Концовка");
            m.putString("b", e.equals("A") ? "Тишина" : "Эхо");
            m.putString("c", e.equals("A") ? "Хор замолчал." : "Ты ответил.");
            Net.all(server, m);
        });
        Sched.after(200, () -> voice(e.equals("A") ? "epi_a" : "epi_b", null, true));
        Sched.after(360, () -> {
            for (ServerPlayerEntity p : players()) note(p, e.equals("A") ? "epilogue_a" : "epilogue_b");
        });
        Sched.after(420, () -> Net.all(server, Net.msg("credits")));
    }

    // ===================== команды и подсказки =====================

    public static BlockPos hintTarget(ServerPlayerEntity p) {
        StoryState st = st();
        return switch (st.step) {
            case "c1_road", "c1_house", "c1_night", "c1_knock" -> st.point("anna_door");
            case "c1_radio" -> st.find("radio_anna");
            case "c1_battery" -> st.point("shop");
            case "c2_fuses" -> !st.flag("crate_council_fuse") ? st.point("council") : !st.flag("crate_school_fuse") ? st.point("school") : st.point("garage");
            case "c2_power" -> st.point("fusebox");
            case "c2_call", "c2_key" -> st.point("council");
            case "c2_gate" -> st.point("gate");
            case "c3_path" -> st.point("station_gate");
            case "c3_power" -> st.flag("canister") ? st.point("generator") : st.point("hunter");
            case "c3_log" -> st.point("safe_station");
            case "c3_key" -> st.point("shumov");
            case "c3_run", "c4_lift" -> st.point("lift_top");
            case "c4_marks" -> !st.flag("mark_1") ? st.point("mark_1") : !st.flag("mark_2") ? st.point("mark_2") : st.point("mark_3");
            case "c4_anna" -> st.point("anna_mine");
            case "c4_escape" -> st.point("lift_bottom");
            case "c5_bunker" -> st.point("hatch");
            case "c5_amps" -> !st.flag("amp_1") ? st.point("amp_1") : !st.flag("amp_2") ? st.point("amp_2") : st.point("amp_3");
            case "c5_tx" -> st.point("transmitter");
            default -> null;
        };
    }

    /** Перепрыгнуть на главу (для отладки и если что-то сломалось): выставляет нужные флаги. */
    public static void jump(int ch) {
        StoryState st = st();
        Sched.clear();
        sceneLock = 0;
        st.ending = "";
        if (ch >= 2) {
            st.set("batteries");
            for (ServerPlayerEntity p : players()) if (!has(p, GContent.FLASHLIGHT)) {
                give(p, new ItemStack(GContent.FLASHLIGHT));
                give(p, new ItemStack(GContent.BATTERY, 3));
            }
        }
        if (ch >= 3) {
            st.set("power");
            st.set("called");
            powerVillage(false);
            BlockPos g = st.point("gate");
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) w().setBlockState(new BlockPos(x, g.getY() + y, g.getZ() - 1), Blocks.AIR.getDefaultState());
        }
        if (ch >= 4) {
            st.set("log_read");
            st.set("canister");
            powerStation(true);
        }
        if (ch >= 5) {
            st.set("anna_found");
            for (ServerPlayerEntity p : players()) if (!has(p, GContent.TUNING_FORK)) give(p, new ItemStack(GContent.TUNING_FORK));
        }
        startChapter(ch);
        if (ch == 4) for (ServerPlayerEntity p : players()) tp(p, st.point("cp4top"));
        if (ch == 3) for (ServerPlayerEntity p : players()) tp(p, st.point("cp3"));
        if (ch == 2) for (ServerPlayerEntity p : players()) tp(p, st.point("cp2"));
    }

    public static void reset() {
        StoryState st = st();
        Sched.clear();
        sceneLock = 0;
        clearCreatures(false);
        st.flags.clear();
        st.counters.clear();
        st.sanity.clear();
        st.chapter = 0;
        st.step = "";
        st.ending = "";
        st.markDirty();
        hudAll();
    }

    public static String status() {
        StoryState st = st();
        return "Глава " + st.chapter + ", шаг " + st.step + ", сложность " + st.difficulty + ", концовка '" + st.ending + "', флагов " + st.flags.size()
                + ", точек " + st.points.size() + ", вещей " + st.interact.size();
    }
}
