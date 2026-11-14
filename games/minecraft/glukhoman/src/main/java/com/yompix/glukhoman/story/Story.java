package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.entity.WatcherEntity;
import com.yompix.glukhoman.horror.Director;
import com.yompix.glukhoman.horror.Noise;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.horror.Sched;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.world.MapBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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
 * «Порожняк», часть 1 (0.4): пролог «Проверка записи» (ночь 1) и глава I «Еловый» (день, вечер с правилами, ночь 2 на перроне,
 * утренний пересмотр плёнки). Всё состояние — на сервере (StoryState), общее для всех игроков: кооператив видит одну историю.
 * Глава 1 = пролог, глава 2 = «Еловый», глава 3 = конец первой части (свободная игра до продолжения).
 */
public final class Story {
    private Story() {}

    private static MinecraftServer server;
    private static int sceneLock;
    private static final Map<UUID, Integer> LOST = new HashMap<>();
    /** Автотест: «игрок-призрак» без клиента. */
    public static ServerPlayerEntity testPlayer;
    static int prologueT;
    static boolean announcing;

    public static boolean sceneRunning() {
        return sceneLock > 0;
    }

    static StoryState st() {
        return StoryState.get(server.getOverworld());
    }

    static ServerWorld w() {
        return server.getOverworld();
    }

    public static List<ServerPlayerEntity> players() {
        List<ServerPlayerEntity> l = new ArrayList<>();
        if (server == null) return l;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (!p.isSpectator()) l.add(p);
        if (testPlayer != null) l.add(testPlayer);
        return l;
    }

    static int difficulty() {
        return st().difficulty;
    }

    // ===================== запуск =====================

    public static void serverStarted(MinecraftServer s) {
        server = s;
        Sched.clear();
        Noise.clear();
        Cam.clear();
        Night.reset();
        sceneLock = 0;
        StoryState st = st();
        if (!isOurWorld()) return;
        if (!st.built) MapBuilder.build(w(), st);
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
        w().getWorldBorder().setCenter(60, 60);
        w().getWorldBorder().setSize(900);
        if (st.chapter == 0) w().setTimeOfDay(20850);
        if (st.chapter > 0) Sched.after(40, () -> resume(st));
    }

    /** Перезапуск посреди истории: пролог — заново, ночь — с вечера, днём — жители на местах. */
    static void resume(StoryState st) {
        clearResidents();
        if (st.step.startsWith("p_")) {
            startPrologue();
            return;
        }
        if (st.step.startsWith("n2")) {
            setStep("c1_wait");
            w().setTimeOfDay(16000);
        }
        if (st.chapter == 2) dayResidents();
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
                kit(p, false);
                msg(p, "Ты — в бригаде сторожей. Камера одна на всех: попроси у того, кто держит.");
            }
        });
        if (st.chapter == 0) st.set("joined:" + p.getUuid());
    }

    public static void leave(ServerPlayerEntity p) {
        Cam.lower(p, true);
        Cam.forget(p);
    }

    public static void chooseDifficulty(ServerPlayerEntity p, int d) {
        chooseDifficulty(p, d, false);
    }

    public static void chooseDifficulty(ServerPlayerEntity p, int d, boolean force) {
        StoryState st = st();
        if (st.chapter != 0 && !force) return;
        st.difficulty = Math.max(0, Math.min(2, d));
        st.markDirty();
        Net.all(server, diffMsg());
        if (st.chapter != 0) return;
        st.set("asked");
        startPrologue();
    }

    static NbtCompound diffMsg() {
        NbtCompound m = Net.msg("difficulty");
        m.putInt("d", st().difficulty);
        return m;
    }

    static void sendDifficulty(ServerPlayerEntity p) {
        Net.send(p, diffMsg());
    }

    /** Камера — одна на бригаду (у первого), у остальных фонарь и батарейка. */
    static void kit(ServerPlayerEntity p, boolean camera) {
        if (camera && !has(p, GContent.CAMCORDER)) {
            p.getInventory().selectedSlot = 0;
            ItemStack old = p.getInventory().getStack(0);
            p.getInventory().setStack(0, new ItemStack(GContent.CAMCORDER));
            if (!old.isEmpty()) give(p, old);
        }
        if (!has(p, GContent.FLASHLIGHT)) give(p, new ItemStack(GContent.FLASHLIGHT));
        if (!camera) give(p, new ItemStack(GContent.BATTERY, 1));
    }

    // ===================== пролог: «Проверка записи» =====================

    static void startPrologue() {
        StoryState st = st();
        st.chapter = 1;
        st.markDirty();
        clearCreatures();
        Director.calm(server, 20 * 60 * 30);
        w().setTimeOfDay(20850);   // 02:51
        if (Tapes.list(st).isEmpty()) Tapes.newTape(st);
        tpSpread(st.point("start"));
        List<ServerPlayerEntity> ps = players();
        for (int i = 0; i < ps.size(); i++) {
            ServerPlayerEntity p = ps.get(i);
            p.teleport(w(), p.getX(), p.getY(), p.getZ(), -90, 4);   // лицом вдоль перрона, к переезду (поворот уходит клиенту только телепортом)
            Sanity.set(p, 100);
            kit(p, i == 0);
            if (i == 0) {
                if (!has(p, GContent.CASSETTE)) Tapes.give(p);
                give(p, new ItemStack(GContent.BATTERY, 2));
            }
            p.setSpawnPoint(w().getRegistryKey(), st.point("cp_platform"), -90, true, false);
        }
        lamps("station", true);
        ResidentEntity polya = spawnResident("polya", st.point("polya_window"));
        if (polya != null) {
            polya.setYaw(180);
            polya.act("slowwave");
        }
        ResidentEntity pet = spawnResident("petrovich", st.point("petrovich_day"));
        if (pet != null) pet.setYaw(180);
        prologueT = 0;
        setStep("p_rec");
        for (ServerPlayerEntity p : ps) if (has(p, GContent.CAMCORDER)) Sched.after(30, () -> Cam.raise(p));
        Sched.after(60, () -> seq("p_gena", st.find("radio"), true));
    }

    static void prologueTick(StoryState st) {
        if (Boolean.getBoolean("glukhoman.freeze")) return;   // автотест-съёмка: пролог стоит на месте
        prologueT += 10;
        if (st.step.equals("p_rec")) {
            int n = 0;
            for (String t : new String[]{"t_platform", "t_switch", "t_crossing"}) {
                if (!st.flag("rec_" + t)) for (ServerPlayerEntity p : players()) if (Cam.up(p) && Cam.session(p).getOrDefault(t, 0) >= 30) {
                    st.set("rec_" + t);
                    msg(p, "Есть: " + Texts.get().objective("rec_" + t));
                    hudAll();
                }
                if (st.flag("rec_" + t)) n++;
            }
            if (n == 3 || prologueT > 20 * 75) {
                setStep("p_east");
                seq("p_gena2", st.find("radio"), true);
            }
        } else if (st.step.equals("p_east")) {
            for (ServerPlayerEntity p : players()) if (inZone(p, "east")) bell();
            if (prologueT > 20 * 100) bell();   // крючок — не позже двух минут
        } else if (st.step.equals("p_bell") && !st.flag("p_scene")) {
            boolean raised = false;
            for (ServerPlayerEntity p : players()) raised |= Cam.up(p);
            if ((raised && prologueT > st.count("p_bell_t") + 60) || prologueT > st.count("p_bell_t") + 20 * 10) {
                st.set("p_scene");
                for (ServerPlayerEntity p : players()) Cam.lower(p, true);
                Tapes.commit(st, "prologue", "rec", titleOf("prologue"), Map.of("polya", 400, "train", 300));
                scene("pr_train", () -> {
                    clearResidents();
                    scene("pr_board", Story::morning);
                });
            }
        }
    }

    /** На мёртвой линии звонит переезд. Мигают красные. Глазами — ничего. */
    static void bell() {
        StoryState st = st();
        if (!st.step.equals("p_east")) return;
        setStep("p_bell");
        st.counters.put("p_bell_t", prologueT);
        st.markDirty();
        for (int i = 0; i < 12; i++) {
            final int k = i;
            Sched.after(i * 40, () -> {
                sound(st.point("crossing"), "crossing_bell", 2.4f, 1f);
                for (BlockPos q : lampsOf("signal")) lampSet(q, k % 2 == 0);
            });
            Sched.after(i * 40 + 20, () -> {
                for (BlockPos q : lampsOf("signal")) lampSet(q, k % 2 != 0);
            });
        }
        Sched.after(12 * 40 + 2, () -> {
            for (BlockPos q : lampsOf("signal")) lampSet(q, false);
        });
        msgAll("Звонит переезд. Ветку закрыли пять лет назад. Подними камеру.");
        voice("p_bell", st.point("crossing"), true);
    }

    // ===================== глава I «Еловый» =====================

    static void morning() {
        StoryState st = st();
        st.chapter = 2;
        st.set("taken:polya");
        st.markDirty();
        w().setTimeOfDay(700);
        MapBuilder.boardUp(w(), st);
        lamps("station", false);
        for (BlockPos q : lampsOf("signal")) lampSet(q, false);
        tpSpread(new BlockPos(12, 66, 19));   // восточный конец перрона: видно дом бабы Поли
        for (ServerPlayerEntity p : players()) {
            p.teleport(w(), p.getX(), p.getY(), p.getZ(), -16, 6);   // утро: лицом к дому бабы Поли
            p.setSpawnPoint(w().getRegistryKey(), st.point("cp_platform"), 0, true, false);
        }
        fxAll("fade", 60);
        dayResidents();
        setStep("c1_house");
        Sched.after(80, () -> seq("c1_gena_morning", st.find("radio"), true));
    }

    static void dayResidents() {
        StoryState st = st();
        clearResidents();
        for (String who : new String[]{"zakhar", "vika", "petrovich", "zina"}) {
            if (st.flag("taken:" + who)) continue;
            ResidentEntity r = spawnResident(who, st.point(who + "_day"));
            if (r != null) r.setYaw(who.equals("zina") || who.equals("petrovich") ? 180 : 0);
        }
    }

    static void houseVisited() {
        StoryState st = st();
        if (!st.step.equals("c1_house")) return;
        setStep("c1_zina");
        int d = seq("c1_zina", st.point("zina_day"), true);
        Sched.after(d + 20, () -> {
            titleAll("", "ГЛУХОМАНЬ", "Еловый. 14 октября 1996-го. Помнит только кассета.");
            Sched.after(140, () -> {
                setStep("c1_box");
                seq("c1_gena_box", st.find("radio"), true);
            });
        });
    }

    static void boxChoice(ServerPlayerEntity p) {
        StoryState st = st();
        if (st.chapter != 2 || st.flag("box_day1")) {
            msg(p, st.flag("box_day1") ? "Ящик пустой и холодный. Сегодня уже решили." : "Старый почтовый ящик лагпункта. Щель для писем.");
            return;
        }
        NbtCompound t = Tapes.current(st);
        if (t == null) {
            msg(p, "Сдавать нечего.");
            return;
        }
        ask(p, "box", "Кассета №" + t.getInt("n") + ". На ней: " + subjText(Tapes.weight(st, false)),
                "Сдать в ящик (Гена привезёт батарейки и новую кассету)", "Оставить себе (всё, что на ней, останется с тобой)");
    }

    public static void answer(ServerPlayerEntity p, String id, int i) {
        StoryState st = st();
        if (!id.equals("box") || st.flag("box_day1")) return;
        st.set("box_day1");
        if (i == 0) {
            int n = Tapes.deposit(st, p);
            st.set("deposited1");
            st.inc("deposits");
            sound(st.find("box"), "tape_click", 1f, 0.7f);
            msg(p, "Кассета №" + n + " упала на дно ящика. Глухо, как в колодец.");
            List<BlockPos> b = lampsOf("board");
            if (!b.isEmpty()) sound(b.get(0), "relay", 0.6f, 1.3f);
            Sched.after(200, () -> {
                sound(st.point("start"), "motor", 2f, 1f);
                Sched.after(100, () -> {
                    for (ServerPlayerEntity q : players()) give(q, new ItemStack(GContent.BATTERY, q == p ? 3 : 1));
                    Tapes.newTape(st);
                    Tapes.give(p);
                    hudAll();
                    seq("c1_gena_paid", st.find("radio"), true);
                    msgAll("Мотовоз привёз батарейки и новую кассету. Всё честно.");
                });
            });
        } else {
            st.set("kept1");
            seq("c1_gena_angry", st.find("radio"), true);
        }
        hudAll();
        Sched.after(60, () -> {
            if (st.step.equals("c1_box")) setStep("c1_zakhar");
        });
    }

    /** Житель: днём — разговор; поднятая камера — интервью (это сюжет на плёнке, «якорь»). Ночью лунатика — будить. */
    public static void resident(ServerPlayerEntity p, ResidentEntity r) {
        StoryState st = st();
        String who = r.who();
        if (r.sleepwalking()) {
            Night.wake(p, r);
            return;
        }
        if (st.chapter == 2 && who.equals("zakhar") && st.step.equals("c1_zakhar")) {
            int d = seq("c1_zakhar", r.getBlockPos(), false);
            if (st.flag("kept1") && st.set("zakhar_battery")) Sched.after(d, () -> {
                give(p, new ItemStack(GContent.BATTERY, 1));
                msg(p, "Захар Ильич сунул тебе батарейку из ящика стола. «Только Гене не говори».");
            });
            Sched.after(d + 10, () -> setStep("c1_evening"));
            return;
        }
        if (Cam.up(p) && st.chapter == 2 && !st.flag("int_" + who) && !Texts.get().sequence("int_" + who).isEmpty()) {
            st.set("int_" + who);
            st.inc("interviews");
            int d = seq("int_" + who, r.getBlockPos(), false);
            Sched.after(d, () -> {
                Map<String, Integer> s = new HashMap<>(Cam.session(p));
                s.merge(who, 200, Integer::sum);
                String m = Tapes.commit(st, "interview", "int_" + who, "Интервью: " + Texts.get().whoName(who), s);
                Cam.session(p).clear();
                msg(p, m != null ? m : "Интервью на плёнке. " + Texts.get().whoName(who) + " теперь на кассете.");
                hudAll();
            });
            return;
        }
        String id = "talk_" + who + "_" + st.step;
        seq(Texts.get().sequence(id).isEmpty() ? "talk_" + who : id, r.getBlockPos(), false);
    }

    /** Стул в дежурке: ждать вечера / ночи. */
    static void waitChair(ServerPlayerEntity p) {
        StoryState st = st();
        switch (st.step) {
            case "c1_evening" -> evening();
            case "c1_wait" -> {
                fxAll("fade", 80);
                setStep("n2_wait");
                Sched.after(40, Night::start);
            }
            case "c1_box", "c1_zakhar", "c1_house", "c1_zina" -> msg(p, "Рано. Сначала: " + objectiveText());
            default -> msg(p, "Стул скрипит. Сидеть некогда.");
        }
    }

    /** 22:00 — Захар объявляет правила ночи по громкоговорителю. Одно правило — ложное: его говорит линия его голосом. */
    static void evening() {
        StoryState st = st();
        fxAll("fade", 60);
        w().setTimeOfDay(16000);
        lamps("station", true);
        setStep("c1_announce");
        st.data.putString("false_rule", "rule_n2_4");
        BlockPos ls = st.point("loudspeaker");
        Sched.after(80, () -> {
            announcing = true;
            sound(ls, "loudspeaker", 2.5f, 1f);
            int d = seq("rules_n2", ls, true);
            Sched.after(d + 20, () -> {
                for (ServerPlayerEntity p : players()) if (Cam.up(p)) {
                    Cam.lower(p, true);
                    Cam.raise(p);
                }
                announcing = false;
                int d2 = seq("vika_rule", st.point("vika_day"), true);
                Sched.after(d2 + 20, () -> {
                    setStep("c1_wait");
                    clearResidents();
                });
            });
        });
    }

    public static void nightOver() {
        StoryState st = st();
        w().setTimeOfDay(1000);
        lamps("station", false);
        fxAll("fade", 80);
        tpSpread(st.point("cp_platform"));
        dayResidents();
        setStep("c1_replay");
        String called = st.data.getString("n2_called");
        int taken = 0;
        for (String x : called.split(",")) if (!x.isEmpty() && st.flag("taken:" + x)) taken++;
        Glukhoman.LOG.info("Глухомань: ночь 2 окончена, позвали {}, забрал {}", called, taken);
        final int tk = taken;
        Sched.after(60, () -> seq(tk > 0 ? "c1_morning_lost" : "c1_morning_saved", st.point("cp_platform"), true));
    }

    /** Пересмотр сюжета: клиент разыгрывает «запись» как VHS-сцену или как плёнку с субтитрами. */
    public static void replay(ServerPlayerEntity p, NbtCompound slot) {
        StoryState st = st();
        String kind = slot.getString("kind");
        sound(p.getBlockPos(), "rewind", 0.7f, 1f);
        switch (kind) {
            case "prologue" -> sceneFor(p, "pr_board", null);
            case "night", "death" -> {
                NbtCompound a = new NbtCompound();
                Vec3d at = Night.wakeAt != null ? Night.wakeAt : Vec3d.ofBottomCenter(st.point("t_platform"));
                a.putDouble("x", at.x);
                a.putDouble("y", at.y);
                a.putDouble("z", at.z);
                a.putString("who", Night.wakeWho);
                a.putString("me", p.getName().getString());
                sceneFor(p, "rp_night", a);
                if (st.step.equals("c1_replay") && st.set("replayed")) Sched.after(Scenes.ticks("rp_night") + 30, Story::endPart1);
            }
            default -> {
                NbtCompound m = Net.msg("playback");
                m.putString("title", slot.getString("title"));
                NbtList lines = new NbtList();
                String ctx = slot.getString("ctx");
                if (kind.equals("interview") || kind.equals("announce")) {
                    for (String l : Texts.get().sequence(kind.equals("announce") ? "rules_n2" : ctx)) {
                        String text = Texts.get().lineText(l);
                        if (l.equals(st.data.getString("false_rule"))) text = "[тишина. На плёнке громкоговоритель молчит — а ты помнишь голос.]";
                        lines.add(NbtString.of(Texts.get().lineWho(l) + ": " + text));
                    }
                } else lines.add(NbtString.of("В кадре: " + subjText(slot.getCompound("subj"))));
                m.put("lines", lines);
                Net.send(p, m);
            }
        }
    }

    static void endPart1() {
        StoryState st = st();
        if (st.chapter >= 3) return;
        st.chapter = 3;
        st.ending = "1";
        st.markDirty();
        setStep("end1");
        int d = seq("end1", st.find("radio"), true);
        Sched.after(d + 20, () -> {
            titleAll("Конец первой части", "ПОРОЖНЯК", "Продолжение — глава II: табло горит без тока");
            Glukhoman.LOG.info("Глухомань: часть 1 пройдена. Сдано кассет {}, интервью {}, забрал поезд {}", st.count("deposits"), st.count("interviews"), st.count("taken"));
        });
    }

    /** Стёрли сюжет: всё, что держала эта запись, больше ничто не держит. */
    static void erased(NbtCompound old) {
        Glukhoman.LOG.info("Глухомань: стёрт сюжет «{}»", old.getString("title"));
    }

    // ===================== «что пишет камера» =====================
    public static String recKind() {
        StoryState st = st();
        if (st.step.startsWith("p_")) return "prologue";
        if (announcing) return "announce";
        if (Night.running) return "night";
        return "footage";
    }

    public static String recCtx() {
        return switch (recKind()) {
            case "prologue" -> "rec";
            case "announce" -> "rules_n2";
            case "night" -> "n2";
            default -> st().step + ":" + st().count("foot");
        };
    }

    public static String recTitle() {
        return titleOf(recKind());
    }

    public static String titleOf(String kind) {
        return switch (kind) {
            case "prologue" -> "Проверка записи. 14.10, 02:51";
            case "announce" -> "Объявление Захара, 22:00";
            case "night" -> "Перрон, ночь на 15.10";
            default -> {
                StoryState st = st();
                st.inc("foot");
                yield "Сюжет " + st.count("foot") + " (" + (st.chapter == 2 && !st.step.startsWith("n2") ? "день" : "ночь") + ")";
            }
        };
    }

    static void camRaised(ServerPlayerEntity p) {
        if (announcing) st().data.putBoolean("rules_filmed", true);
    }

    public static String subjText(NbtCompound subj) {
        Map<String, Integer> m = new HashMap<>();
        for (String k : subj.getKeys()) m.put(k, subj.getInt(k));
        return subjText(m);
    }

    public static String subjText(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder b = new StringBuilder();
        for (var e : l) {
            if (e.getValue() < 10) continue;
            if (b.length() > 0) b.append(", ");
            b.append(Texts.get().whoName(e.getKey()));
            if (b.length() > 90) break;
        }
        return b.length() == 0 ? "никого" : b.toString();
    }

    // ===================== жители и существа =====================
    public static ResidentEntity spawnResident(String who, BlockPos at) {
        ResidentEntity r = spawn(GContent.RESIDENT, at);
        if (r != null) {
            r.who(who);
            r.setCustomName(Text.literal(Texts.get().whoName(who)));
            r.setCustomNameVisible(false);
        }
        return r;
    }

    public static void clearResidents() {
        for (Entity e : w().getEntitiesByClass(ResidentEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true)) e.discard();
    }

    static void clearCreatures() {
        for (Entity e : w().getEntitiesByClass(LivingEntity.class, new Box(BlockPos.ORIGIN).expand(700),
                e -> e instanceof PoleEntity || e instanceof ListenerEntity || e instanceof ChoirEntity || e instanceof WatcherEntity || e instanceof ResidentEntity))
            e.discard();
        for (Entity e : w().getEntitiesByClass(TrainEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> true)) e.discard();
    }

    static <T extends Entity> T spawn(net.minecraft.entity.EntityType<T> type, BlockPos at) {
        if (at == null) return null;
        T e = type.create(w());
        if (e == null) return null;
        e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        w().spawnEntity(e);
        return e;
    }

    // ===================== кат-сцены =====================
    private static int sceneSeq;
    private static Runnable sceneAfter;
    private static final java.util.Set<UUID> SKIPPED = new java.util.HashSet<>();

    public static void scene(String id, Runnable after) {
        int len = Scenes.ticks(id);
        int token = ++sceneSeq;
        sceneAfter = after;
        SKIPPED.clear();
        sceneLock = Math.max(sceneLock, len + 10);
        NbtCompound m = Net.msg("cut");
        m.putString("id", id);
        Net.all(server, m);
        Glukhoman.LOG.info("Глухомань: кат-сцена {} ({} тиков)", id, len);
        Sched.after(len, () -> {
            if (token == sceneSeq) finishScene();
        });
    }

    /** Сцена только для одного игрока (пересмотр кассеты); сюжет её не ждёт. */
    static void sceneFor(ServerPlayerEntity p, String id, NbtCompound args) {
        NbtCompound m = Net.msg("cut");
        m.putString("id", id);
        if (args != null) m.put("args", args);
        m.putBoolean("solo", true);
        Net.send(p, m);
    }

    static void finishScene() {
        sceneSeq++;
        sceneLock = 0;
        Runnable r = sceneAfter;
        sceneAfter = null;
        Net.all(server, Net.msg("cutend"));
        if (r != null) r.run();
    }

    public static void skip(ServerPlayerEntity p) {
        if (sceneAfter == null && sceneLock == 0) return;
        SKIPPED.add(p.getUuid());
        var list = server.getPlayerManager().getPlayerList();
        boolean host = !list.isEmpty() && list.get(0) == p || server.isHost(p.getGameProfile());
        if (host || list.stream().allMatch(q -> SKIPPED.contains(q.getUuid()))) finishScene();
        else msg(p, "Ждём, пока пропустят остальные…");
    }

    // ===================== шаги и HUD =====================

    public static void setStep(String id) {
        StoryState st = st();
        st.step = id;
        st.markDirty();
        Glukhoman.LOG.info("Глухомань: шаг {} (глава {})", id, st.chapter);
        hudAll();
    }

    static String objectiveText() {
        StoryState st = st();
        String t = Texts.get().objective(st.step);
        return switch (st.step) {
            case "p_rec" -> {
                int n = 0;
                for (String x : new String[]{"t_platform", "t_switch", "t_crossing"}) if (st.flag("rec_" + x)) n++;
                yield t + " (" + n + "/3)";
            }
            case "c1_box", "c1_zakhar" -> t + (st.count("interviews") > 0 ? " · интервью: " + st.count("interviews") : "");
            case "n2_night" -> {
                int left = 0;
                for (ResidentEntity r : Night.walkersList()) if (r.sleepwalking()) left++;
                yield t + (left > 0 ? " · идут к поезду: " + left : "");
            }
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
        NbtCompound t = Tapes.current(st);
        m.putString("tape", t == null ? "нет кассеты" : "№" + t.getInt("n") + " " + Tapes.slots(t).size() + "/" + Tapes.SLOTS);
        Net.send(p, m);
    }

    static void hudAll() {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendHud(p);
    }

    static void titleAll(String a, String b, String c) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            NbtCompound m = Net.msg("title");
            m.putString("a", a);
            m.putString("b", b);
            m.putString("c", c);
            Net.send(p, m);
        }
        for (ServerPlayerEntity p : players()) w().playSound(null, p.getBlockPos(), GContent.sound("title_hit"), SoundCategory.MASTER, 1f, 1f);
    }

    public static BlockPos checkpoint() {
        StoryState st = st();
        if (st.step.startsWith("n2") || st.step.equals("c1_wait")) return st.point("cp_dispatch");
        return st.point("cp_platform");
    }

    // ===================== реплики, звуки, вещи =====================

    public static int seq(String id, BlockPos at, boolean global) {
        int t = 0;
        for (String line : Texts.get().sequence(id)) {
            final String l = line;
            Sched.after(t + 1, () -> voice(l, at, global));
            t += dur(line);
        }
        return t;
    }

    static int dur(String line) {
        return (int) (Texts.get().lineText(line).length() / 13.0 * 20) + 30;
    }

    public static void voice(String line, BlockPos at, boolean global) {
        NbtCompound m = Net.msg("voice");
        m.putString("id", line);
        m.putString("who", Texts.get().lineWho(line));
        m.putString("text", Texts.get().lineText(line));
        m.putInt("dur", dur(line));
        if (at != null) {
            m.putInt("x", at.getX());
            m.putInt("y", at.getY());
            m.putInt("z", at.getZ());
        }
        m.putBoolean("global", global || at == null);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            if (global || at == null || p.getBlockPos().getSquaredDistance(at) < 40 * 40) Net.send(p, m);
    }

    public static void msg(ServerPlayerEntity p, String text) {
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
        if (at != null && GContent.sound(id) != null) w().playSound(null, at, GContent.sound(id), SoundCategory.AMBIENT, vol, pitch);
    }

    static void ask(ServerPlayerEntity p, String id, String title, String... options) {
        NbtCompound m = Net.msg("ask");
        m.putString("id", id);
        m.putString("title", title);
        NbtList l = new NbtList();
        for (String o : options) l.add(NbtString.of(o));
        m.put("opts", l);
        Net.send(p, m);
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
        if (s.isOf(GContent.FLASHLIGHT) || s.isOf(GContent.OIL_LAMP)) s.setDamage(s.getMaxDamage() / 3);
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    static void tpSpread(BlockPos at) {
        if (at == null) return;
        int i = 0;
        for (ServerPlayerEntity p : players()) {
            int dx = new int[]{0, 1, -1, 2, -2}[i % 5];
            tp(p, at.add(dx, 0, 0));
            i++;
        }
    }

    static void tp(ServerPlayerEntity p, BlockPos at) {
        if (at != null) p.teleport(w(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYaw(), p.getPitch());
    }

    static List<BlockPos> lampsOf(String group) {
        List<BlockPos> out = new ArrayList<>();
        List<Long> l = st().lamps.get(group);
        if (l != null) for (long x : l) out.add(BlockPos.fromLong(x));
        return out;
    }

    static void lampSet(BlockPos q, boolean lit) {
        BlockState s = w().getBlockState(q);
        if (s.getBlock() instanceof LampBlock && s.get(LampBlock.LIT) != lit) w().setBlockState(q, s.with(LampBlock.LIT, lit), 3);
    }

    static void lamps(String group, boolean lit) {
        for (BlockPos q : lampsOf(group)) lampSet(q, lit);
    }

    // ===================== зоны =====================

    public static boolean inZone(ServerPlayerEntity p, String zone) {
        StoryState st = StoryState.get(p.getServerWorld());
        BlockPos a = st.point(zone + "_a"), b = st.point(zone + "_b");
        if (a == null || b == null) return false;
        BlockPos q = p.getBlockPos();
        return q.getX() >= a.getX() && q.getX() <= b.getX() && q.getY() >= a.getY() && q.getY() <= b.getY() && q.getZ() >= a.getZ() && q.getZ() <= b.getZ();
    }

    public static boolean inSafe(ServerPlayerEntity p) {
        return !Night.running && inZone(p, "dispatch");
    }

    // ===================== такт =====================

    public static void tick(MinecraftServer s) {
        server = s;
        if (!isOurWorld()) return;
        long t = s.getTicks();
        Sched.tick(t);
        Cam.tick(s);
        Night.tick();
        StoryState st = st();
        if (sceneLock > 0) sceneLock--;
        if (st.chapter == 0) return;
        if (t % 5 == 0) for (ServerPlayerEntity p : players()) Noise.playerStep(p);
        if (t % 10 != 0) return;
        if (st.chapter == 1) prologueTick(st);
        for (ServerPlayerEntity p : players()) area(p, st);
        if (t % 20 == 0 && st.ending.isEmpty()) for (ServerPlayerEntity p : players()) bounds(p, st);
        if (t % 40 == 0 && st.step.equals("n2_night")) hudAll();
        if (t % 100 == 0 && Night.running) for (ServerPlayerEntity p : players()) if (p.getVelocity().horizontalLengthSquared() < 0.001) Night.heat(p, 1);
    }

    static void area(ServerPlayerEntity p, StoryState st) {
        if (st.step.equals("c1_house") && inZone(p, "polya_yard")) houseVisited();
        if (Night.running && inZone(p, "wagon") && st.set("wagon_hid")) {
            msg(p, "В вагоне пахнет мокрыми досками. Снаружи кто-то останавливается.");
            Night.heat(p, 6);
        }
    }

    static void bounds(ServerPlayerEntity p, StoryState st) {
        if (p.isCreative()) return;
        BlockPos q = p.getBlockPos();
        boolean ok = q.getX() > -90 && q.getX() < 140 && q.getZ() > -16 && q.getZ() < 172;
        int n = ok ? 0 : LOST.getOrDefault(p.getUuid(), 0) + 1;
        LOST.put(p.getUuid(), n);
        if (n == 3) {
            msg(p, "Шпалы уходят в темноту. Дальше — 41-й километр. Не сейчас.");
            fx(p, "lost", 200);
        }
        if (n >= 10) {
            LOST.put(p.getUuid(), 0);
            fx(p, "fade", 60);
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
        switch (key) {
            case "box" -> boxChoice(p);
            case "radio" -> {
                sound(pos, "radio_static", 0.6f, 1f);
                String line = "radio_" + st.step;
                if (!Texts.get().sequence(line).isEmpty()) seq(line, pos, false);
                else msg(p, "Рация шипит. Гена выходит на связь утром и вечером.");
            }
            case "board" -> msg(p, Night.running ? Night.readout() : "Табло рельсовых цепей. Тока нет. Лампочки тёплые.");
            case "wait" -> waitChair(p);
            case "bell" -> Night.bell(p);
            case "switch_w", "switch_e" -> Night.lever(p, key);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static BlockPos hintTarget(ServerPlayerEntity p) {
        StoryState st = st();
        return switch (st.step) {
            case "p_rec" -> !st.flag("rec_t_platform") ? st.point("t_platform") : !st.flag("rec_t_switch") ? st.point("t_switch") : st.point("t_crossing");
            case "p_east", "p_bell" -> new BlockPos(64, 66, 16);
            case "c1_house", "c1_zina" -> st.point("polya_door");
            case "c1_box" -> st.find("box");
            case "c1_zakhar" -> st.point("zakhar_day");
            case "c1_evening", "c1_wait" -> st.find("wait");
            case "n2_wait", "n2_night" -> st.point("t_platform");
            default -> null;
        };
    }

    public static void jump(int ch) {
        StoryState st = st();
        st.set("asked");
        if (ch <= 1) startPrologue();
        else {
            if (Tapes.list(st).isEmpty()) Tapes.newTape(st);
            List<ServerPlayerEntity> ps = players();
            for (int i = 0; i < ps.size(); i++) kit(ps.get(i), i == 0);
            morning();
        }
    }

    public static void reset() {
        StoryState st = st();
        st.chapter = 0;
        st.step = "";
        st.ending = "";
        st.flags.removeIf(f -> !f.equals("v2"));
        st.counters.clear();
        st.data = new NbtCompound();
        st.markDirty();
        Sched.clear();
        Night.reset();
        Cam.clear();
        announcing = false;
        clearCreatures();
    }

    public static String status() {
        StoryState st = st();
        return "глава " + st.chapter + ", шаг " + st.step + ", сложность " + st.difficulty + (st.ending.isEmpty() ? "" : ", конец " + st.ending)
                + ", кассет " + Tapes.list(st).size() + " (у себя " + Tapes.held(st) + ")";
    }
}
