package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.entity.WatcherEntity;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.horror.Sched;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.world.MapBuilder;
import com.yompix.glukhoman.world.Part3Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Часть третья (0.7): глава IV «Перекличка» (ночь 4, Лишний, признание Захара, Гена с детьми), глава V «Сверка» (поезд изнутри)
 * и три концовки по поведению. Главы в StoryState: 7 — IV, 8 — V, 9 — финал (st.ending = poimenno / naryad / obkh).
 * Память = шкала Sanity (растёт, когда тебя снимают другие, падает в темноте, одному, рядом с Безымянными).
 */
public final class Part3 {
    private Part3() {}

    static ServerWorld w() {
        return Story.w();
    }

    static StoryState st() {
        return Story.st();
    }

    static String nm(ServerPlayerEntity p) {
        return p.getName().getString();
    }

    // ===================== общее: имена, «кого сняли», цели камеры =====================
    public static List<String> targets() {
        List<String> t = new ArrayList<>();
        StoryState st = st();
        if (st.chapter >= 7 && st.chapter <= 8) for (int i = 0; i < Names.SPOTS; i++) {
            t.add("nc_" + i);
            t.add("nt_" + i);
        }
        return t;
    }

    /** Каждые 10 тиков: улики, кто кого снимает (якорь игрока + Память). */
    static void evidenceTick(StoryState st) {
        int[][] lc = Names.layout(st, true), lt = Names.layout(st, false);
        for (ServerPlayerEntity p : Story.players()) {
            if (!Cam.up(p)) continue;
            var s = Cam.session(p);
            for (int i = 0; i < Names.SPOTS; i++) {
                if (s.getOrDefault("nc_" + i, 0) >= 30 && st.set("spot_c" + i)) found(st, p, "nc:", lc[i], true);
                if (s.getOrDefault("nt_" + i, 0) >= 30 && st.set("spot_t" + i)) found(st, p, "nt:", lt[i], false);
            }
            for (ServerPlayerEntity q : Story.players()) {
                if (q == p || !Cam.inFrame(p, q.getPos().add(0, 1.2, 0), 30, false)) continue;
                if (st.set("filmed:" + nm(q))) Story.msg(q, "Тебя сняли. Теперь ты есть на плёнке.");
                Sanity.add(q, 1.5f);   // тебя снимают — ты есть
            }
        }
    }

    static void found(StoryState st, ServerPlayerEntity p, String prefix, int[] nums, boolean carv) {
        boolean any = Names.mark(st, prefix, nums);
        StringBuilder b = new StringBuilder(carv ? "Фамилии: " : "Бирка: ");
        for (int n : nums) b.append(carv ? Names.surname(n) : "№" + n).append(carv ? ", " : " ");
        Story.msgAll(b.toString().replaceAll(",\\s*$", "") + (any ? " — сошлось. Имён: " + Names.count(st) + "/" + Names.threshold() : ""));
        if (carv) Story.sound(st.point("loudspeaker"), "loudspeaker", 1.2f, 0.85f);
        Story.hudAll();
    }

    // ===================== глава IV «Перекличка» =====================
    public static void startChapter4() {
        StoryState st = st();
        if (st.chapter >= 7) return;
        st.chapter = 7;
        st.markDirty();
        Story.clearCreatures();
        w().setTimeOfDay(1800);
        Story.lamps("station", true);
        Story.tpSpread(st.point("cp_platform"));
        Story.fxAll("fade", 60);
        Story.titleAll("Глава IV", "ПЕРЕКЛИЧКА", "Пустые стулья");
        // старые ch2-надписи (38–40) — это тоже «фамилии»
        if (st.flag("name:carve_1")) st.set("nc:38");
        if (st.flag("name:carve_2")) st.set("nc:39");
        if (st.flag("name:carve_3")) st.set("nc:40");
        Story.setStep("c4_chairs");
        Sched.after(100, () -> Story.scene("c4_chairs", () -> {
            day4();
            Story.setStep("c4_gena");
            Names.refreshBooks();
        }));
    }

    static void day4() {
        Story.dayResidents();
        ResidentEntity g = Story.spawnResident("gena", new BlockPos(2, 66, 26));
        if (g != null) g.setYaw(180);
        Story.spawnResident("kid_a", new BlockPos(1, 66, 27));
        Story.spawnResident("kid_b", new BlockPos(3, 66, 27));
    }

    static boolean resident(ServerPlayerEntity p, ResidentEntity r) {
        StoryState st = st();
        String who = r.who();
        if (st.step.equals("c5_car1") && who.equals("nameless")) {   // перекличка по номерам: назвать — и пассажир выходит
            int n = 0;
            for (int i = 1; i <= 40 && n == 0; i++) if (Names.confirmed(st, i) && !st.flag("freed:" + i)) n = i;
            if (n > 0) {
                st.set("freed:" + n);
                st.inc("freed");
                Story.say("pass", "Сторож", n + ". " + Names.ALL[n - 1].split(",")[0] + ".", r.getBlockPos());
                Story.gesture(r, "uniform", null, 0);
                Sched.after(40, r::discard);
            } else if (st.flag("listed:petrovich") && !st.flag("taken:petrovich")) {   // ложное имя: встаёт живой
                st.set("taken:petrovich");
                st.inc("taken");
                Story.say("pass", "Сторож", "37. Глотов Виктор Павлович.", r.getBlockPos());
                Story.msgAll("Пассажир встал. У него лицо Петровича. Он вышел — и ты больше его не видишь.");
                Sched.after(40, r::discard);
            } else Story.seq("c5_noname", r.getBlockPos(), false);
            return true;
        }
        if (st.step.equals("c5_car2") && who.equals("gena") && !st.flag("gena5")) {
            int d = Story.seq("c5_gena", r.getBlockPos(), true);
            Sched.after(d, () -> Story.ask(p, "gena5", "Вагон с кассетами. Гена.", "Отдать свою кассету (они уйдут — ты останешься)",
                    "Стереть себя: записать на №1 Гену", "Ничего не отдавать"));
            return true;
        }
        if (st.step.equals("c5_car3") && who.equals("vika")) {
            if (st.flag("vika_tape") || (Cam.up(p) && Tapes.has(st, "interview", "int_vika"))) {
                st.set("vika_out");
                Story.seq("c5_vika_out", r.getBlockPos(), true);
                Sched.after(60, r::discard);
            } else {
                st.set("taken:vika");
                Story.seq("c5_vika_stay", r.getBlockPos(), true);
            }
            return true;
        }
        if (st.step.equals("c4_extra")) {   // указал на «лишнего»
            if (r.extra()) {
                st.set("extra_found");
                Story.seq("c4_extra_ok", p.getBlockPos(), true);
                Story.sound(r.getBlockPos(), "breath_close", 0.8f, 0.6f);
                r.discard();
            } else {
                st.set("offended:" + who);
                Story.seq("c4_extra_bad", r.getBlockPos(), true);
                Story.gesture(r, "recoil", null, 0);
                Sanity.add(p, -15);
            }
            endRollcall();
            return true;
        }
        if (who.startsWith("kid")) {
            Story.seq("talk_kid", r.getBlockPos(), false);
            return true;
        }
        if (who.equals("gena") && st.step.equals("c4_gena")) {
            int d = Story.seq("c4_gena", r.getBlockPos(), true);
            Sched.after(d, () -> Story.ask(p, "gena4", "Гена просит кассету «с тобой».", "Отдать (тебя «сдадут» — его дети в безопасности)", "Не отдавать"));
            return true;
        }
        if (who.equals("zakhar") && st.step.equals("c4_zakhar")) {
            if (Cam.up(p)) {   // признание на плёнке: 12 имён — громкоговоритель говорит их голосами
                int d = Story.seq("c4_conf", r.getBlockPos(), true);
                BlockPos ls = st.point("loudspeaker");
                for (int i = 1; i <= 12; i++) {
                    final int n = i;
                    Sched.after(d + i * 40, () -> {
                        Story.sound(ls, "loudspeaker", 1.4f, 0.8f + n * 0.02f);
                        Story.say("pass", "Громкоговоритель", n + ". " + Names.ALL[n - 1].split(",")[0] + ".", ls);
                        st.set("nconf:" + n);
                    });
                }
                Sched.after(d + 13 * 40, () -> {
                    Tapes.commit(st, "interview", "int_zakhar4", "Признание Захара", java.util.Map.of("zakhar", 300));
                    Names.refreshBooks();
                    Story.setStep("c4_names");
                });
            } else {
                st.set("lost12");
                int d = Story.seq("c4_conf_off", r.getBlockPos(), true);
                Sched.after(d + 10, () -> Story.setStep("c4_names"));
            }
            return true;
        }
        if (Sanity.get(p) < 30 && !who.equals("gena")) {   // Память: тебя не узнают
            Story.seq("talk_who", r.getBlockPos(), false);
            return true;
        }
        return false;
    }

    public static void answer(ServerPlayerEntity p, String id, int i) {
        StoryState st = st();
        if (id.equals("gena4") && st.set("gena4")) {
            if (i == 0) {
                st.set("gave_self");
                st.inc("dep_after");
                Story.seq("c4_gena_yes", p.getBlockPos(), true);
            } else {
                Story.seq("c4_gena_no", p.getBlockPos(), true);
                for (ServerPlayerEntity q : Story.players()) {   // забирает батареи
                    var inv = q.getInventory();
                    for (int k = 0; k < inv.size(); k++) if (inv.getStack(k).isOf(GContent.BATTERY)) inv.setStack(k, ItemStack.EMPTY);
                }
                Story.msgAll("Гена забрал батареи. Камера — на том, что осталось.");
            }
            Sched.after(80, () -> Story.setStep("c4_zakhar"));
        } else if (id.equals("box4") && i == 0) {
            Tapes.deposit(st, p);
            st.inc("dep_after");
            st.inc("deposits");
            Story.msg(p, "Кассета упала в ящик. Уже после нарядной — ты знаешь, куда она уходит.");
        } else if (id.equals("gena5") && st.set("gena5")) {
            if (i == 0) st.set("gave_self");
            if (i == 1) {
                st.set("erased_self");
                st.set("listed:gena");
            }
            Story.msgAll(i == 0 ? "Гена взял твою кассету. Ушёл с детьми в тамбур. Дверь закрылась." : i == 1 ? "На номер 1 — Гена. Он кивнул. Будто ждал." : "Ты ничего не отдал.");
        }
    }

    /** Стул в дежурке (глава IV). */
    static boolean chair(ServerPlayerEntity p) {
        StoryState st = st();
        if (st.step.equals("c4_names")) {
            rollcall();
            return true;
        }
        if (st.step.equals("c4_wait")) {
            Story.fxAll("fade", 80);
            st.data.putInt("night", 4);
            st.markDirty();
            Sched.after(40, Night::start);
            return true;
        }
        return false;
    }

    /** Ящик: в главах IV–V сдать — можно, но это «сдача после поворота». */
    static boolean box(ServerPlayerEntity p) {
        if (st().chapter < 7) return false;
        Story.ask(p, "box4", "Ящик на перроне. Ты знаешь, куда уходят кассеты.", "Сдать кассету", "Оставить себе");
        return true;
    }

    static void rollcall() {
        StoryState st = st();
        Story.fxAll("fade", 40);
        w().setTimeOfDay(13500);
        Story.setStep("c4_rollcall");
        Story.scene("c4_rollcall", () -> {
            // в зале — все, и один лишний: копия того, кого меньше всех снимали (глазами есть, на плёнке нет)
            Story.clearResidents();
            List<String> who = new ArrayList<>(List.of("zakhar", "zina", "vika", "gena", "kid_a", "kid_b"));
            who.removeIf(x -> st.flag("taken:" + x));
            int i = 0;
            for (String x : who) Story.spawnResident(x, new BlockPos(-1 + (i++ % 6), 66, 24 + (i % 2) * 2));
            var own = Tapes.weight(st, false);
            String ex = who.stream().min((a, b) -> own.getOrDefault(a, 0) - own.getOrDefault(b, 0)).orElse("zina");
            ResidentEntity e = Story.spawnResident(ex, new BlockPos(5, 66, 25));
            if (e != null) e.extra(true);
            st.data.putString("extra_who", ex);
            Story.setStep("c4_extra");
            Sched.after(20 * 150, () -> {
                if (st().step.equals("c4_extra")) {
                    st().set("extra_missed");
                    endRollcall();
                }
            });
        });
    }

    static void endRollcall() {
        StoryState st = st();
        if (!st.step.equals("c4_extra")) return;
        for (ResidentEntity r : w().getEntitiesByClass(ResidentEntity.class, new net.minecraft.util.math.Box(BlockPos.ORIGIN).expand(700), ResidentEntity::extra)) {
            if (!st.flag("extra_found")) st.set("extra_missed");
            r.discard();
        }
        Story.setStep("c4_wait");
    }

    // ---------- ночь 4: Вика идёт сама; Безымянные охотятся; «станция занята» ----------
    static final List<ResidentEntity> NAMELESS = new ArrayList<>();

    public static boolean night4() {
        return Night.running && st().data.getInt("night") == 4;
    }

    static void night4Start() {
        NAMELESS.clear();
        StoryState st = st();
        for (String pt : new String[]{"petrovich_door", "zina_day", "polya_door", "bell"}) {
            BlockPos q = st.point(pt);
            if (q == null) continue;
            ResidentEntity r = Story.spawnResident("nameless", q.add(2, 0, 2));
            if (r != null) {
                r.speed = 0.5;
                NAMELESS.add(r);
            }
        }
        Sched.after(500, () -> {   // табло: «станция» занята. В дежурке — пусто, стул крутится.
            if (!night4()) return;
            Story.voice("c4_board", null, true);
            for (ServerPlayerEntity p : Story.players()) Net.send(p, Net.msg("chairspin"));
        });
    }

    static void night4Tick() {
        NAMELESS.removeIf(r -> {
            if (r.isRemoved()) return true;
            if (Cam.filming(w(), r) != null) {   // на плёнке — встают и пятятся
                r.getNavigation().stop();
                return false;
            }
            ServerPlayerEntity t = null;
            double best = 26 * 26;
            for (ServerPlayerEntity p : Story.players()) {   // охотятся на забытых: чем ниже Память, тем дальше чуют
                double d = p.squaredDistanceTo(r) * (Sanity.get(p) < 40 ? 0.5 : 1);
                if (d < best) {
                    best = d;
                    t = p;
                }
            }
            if (t == null) return false;
            r.getMoveControl().moveTo(t.getX(), t.getY(), t.getZ(), 0.55);
            if (r.squaredDistanceTo(t) < 1.6 * 1.6) {
                Sanity.add(t, -30);
                Story.tug(t, r.getEyePos());
                Story.msg(t, "Без лица. Холодные руки. На секунду ты не помнишь, как тебя зовут.");
                BlockPos cp = st().point("cp_dispatch");
                if (cp != null) t.teleport(w(), cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, t.getYaw(), t.getPitch());
                r.discard();
                return true;
            }
            return false;
        });
    }

    /** Вика: будит только её запись «для мамы» (камера у глаза + её интервью цело). */
    static boolean wakeVika(ServerPlayerEntity p, ResidentEntity r) {
        if (!night4() || !r.who().equals("vika")) return false;
        StoryState st = st();
        if (Cam.up(p) && Tapes.has(st, "interview", "int_vika")) {
            Story.seq("c4_vika_tape", r.getBlockPos(), true);
            st.set("vika_tape");
            return false;   // дальше — обычное «разбудить»
        }
        Story.seq("c4_vika", r.getBlockPos(), false);
        Story.msg(p, "Её не будит ни голос, ни звонок. Включи ей запись «для мамы» — камера к глазу (если её интервью цело).");
        return true;
    }

    static void night4Over() {
        StoryState st = st();
        for (ResidentEntity r : NAMELESS) r.discard();
        NAMELESS.clear();
        st.data.putInt("night", 0);
        st.markDirty();
        Story.scene("c4_face", () -> {
            w().setTimeOfDay(1000);
            Story.tpSpread(st.point("cp_platform"));
            Story.dayResidents();
            BlockPos ls = st.point("loudspeaker");
            Story.sound(ls, "loudspeaker", 2.5f, 1f);
            int d = Story.seq("c4_lastrule", ls, true);
            Sched.after(d + 60, Part3::startChapter5);
        });
    }

    // ===================== глава V «Сверка» =====================
    public static void startChapter5() {
        StoryState st = st();
        if (st.chapter >= 8) return;
        st.chapter = 8;
        st.markDirty();
        Story.clearCreatures();
        w().setTimeOfDay(21170);
        Story.lamps("station", true);
        Story.titleAll("Глава V", "СВЕРКА", "Пятой ночью садитесь сами");
        Story.tpSpread(st.point("cp_platform"));
        // вся деревня спит лунатиками на перроне; поезд — видимый глазами
        int i = 0;
        for (String x : new String[]{"zakhar", "zina", "vika", "petrovich"}) {
            if (st.flag("taken:" + x)) continue;
            ResidentEntity r = Story.spawnResident(x, new BlockPos(-6 + i * 3, 66, 19));
            if (r != null) r.sleepwalking(true);
            i++;
        }
        TrainEntity tr = Story.spawn(GContent.TRAIN, new BlockPos(8, 66, MapBuilder.LZ));
        if (tr != null) tr.refreshPositionAndAngles(8.5, TrainEntity.Y, MapBuilder.LZ + 0.5, 90, 0);
        NbtCompound m = Net.msg("trainvis");
        m.putBoolean("on", true);
        Net.all(w().getServer(), m);
        Story.setStep("c5_board");
        Story.seq("c5_start", st.point("cp_platform"), true);
    }

    static final java.util.Map<java.util.UUID, Integer> CAR = new java.util.HashMap<>();
    static final java.util.Map<java.util.UUID, Integer> OUT = new java.util.HashMap<>();
    static final List<ResidentEntity> PASSENGERS = new ArrayList<>();

    static void boardTick(StoryState st) {
        for (ServerPlayerEntity p : Story.players()) {
            if (CAR.containsKey(p.getUuid())) continue;
            if (p.getZ() < 18.6 && p.getZ() > 17 && p.getX() > -10 && p.getX() < 25) toCar(p, 1);   // к двери вагона
        }
    }

    static void toCar(ServerPlayerEntity p, int k) {
        StoryState st = st();
        CAR.put(p.getUuid(), k);
        BlockPos q = st.point("car" + k);
        if (q != null) p.teleport(w(), q.getX() + 0.5, q.getY(), q.getZ() + 0.5, -90, 0);
        if (k == 1 && st.step.equals("c5_board")) {
            Story.setStep("c5_car1");
            Story.seq("c5_car1", q, true);
            PASSENGERS.clear();
            for (int i = 0; i < 4; i++) {
                ResidentEntity r = Story.spawnResident("nameless", q.add(1 + i * 2, 0, 2));
                if (r != null) {
                    r.setYaw(180);
                    r.act("sit");
                    PASSENGERS.add(r);
                }
            }
        }
        if (k == 2) {
            Story.setStep("c5_car2");
            ResidentEntity g = Story.resident("gena");
            if (g == null && !st.flag("gena5")) g = Story.spawnResident("gena", q.add(5, 0, 1));
        }
        if (k == 3) {
            Story.setStep("c5_car3");
            if (!st.flag("taken:vika") && Story.resident("vika") == null) Story.spawnResident("vika", q.add(3, 0, -1));
        }
        if (k == 4) {
            Story.setStep("c5_car4");
            Story.seq("c5_car4", q, true);
            BlockPos s = st.point("car4_seat");
            if (s != null && w().getEntitiesByClass(WatcherEntity.class, new net.minecraft.util.math.Box(s).expand(6), e -> true).isEmpty()) {
                WatcherEntity wt = Story.spawn(GContent.WATCHER, s);
                if (wt != null) {
                    wt.posted = true;
                    wt.setYaw(180);
                }
            }
        }
    }

    static void carsTick(StoryState st) {
        for (ServerPlayerEntity p : Story.players()) {
            Integer k = CAR.get(p.getUuid());
            if (k == null || OUT.containsKey(p.getUuid())) continue;
            if (k < 4 && Story.inZone(p, "car" + k + "_exit")) toCar(p, k + 1);
            if (k == 4) car4(p);
        }
        if (!CAR.isEmpty() && OUT.size() >= Story.players().size()) finale();
    }

    /** Место 40: выйти, только если тебя снял кто-то другой — или (один) снял себя рядом с ним. */
    static void car4(ServerPlayerEntity p) {
        StoryState st = st();
        if (Sanity.get(p) <= 1) {   // Память на нуле — ты остаёшься
            st.set("stayed:" + nm(p));
            OUT.put(p.getUuid(), 0);
            return;
        }
        boolean byOther = false;
        for (ServerPlayerEntity q : Story.players())
            if (q != p && Cam.up(q) && Cam.inFrame(q, p.getPos().add(0, 1.2, 0), 20, false)) byOther = true;
        WatcherEntity wt = null;
        for (WatcherEntity e : w().getEntitiesByClass(WatcherEntity.class, p.getBoundingBox().expand(8), e -> true)) wt = e;
        boolean self = Story.players().size() == 1 && Cam.up(p) && wt != null && Cam.inFrame(p, wt.getPos().add(0, 1.5, 0), 12, true);
        if (byOther || self) {
            int n = Story.st().count("c4t:" + nm(p)) + 1;
            st.counters.put("c4t:" + nm(p), n);
            if (n >= 6) {   // ~3 с в кадре
                st.set("filmed:" + nm(p));
                OUT.put(p.getUuid(), 1);
                Story.msg(p, self ? "В кадре вы рядом. Ты видишь, кем он был. Дверь открылась." : "Тебя сняли. Дверь открылась.");
            }
        }
    }

    // ===================== концовка =====================
    public static String evaluate() {
        StoryState st = st();
        boolean stayedAll = Story.players().stream().allMatch(p -> st.flag("stayed:" + nm(p)));
        ServerPlayerEntity holder = Story.players().stream().filter(p -> p.getInventory().count(GContent.CAMCORDER) > 0).findFirst().orElse(null);
        boolean holderFilmed = holder == null || st.flag("filmed:" + nm(holder));
        if (st.flag("gave_self") || stayedAll || !holderFilmed) return "obkh";
        boolean everyone = Story.players().stream().allMatch(p -> st.flag("filmed:" + nm(p)));
        if (Names.count(st) >= Names.threshold() && st.count("dep_after") == 0 && everyone) return "poimenno";
        return "naryad";
    }

    static void finale() {
        StoryState st = st();
        if (st.chapter >= 9) return;
        String e = evaluate();
        st.chapter = 9;
        st.ending = e;
        st.markDirty();
        CAR.clear();
        Glukhoman.LOG.info("Глухомань: финал «{}». Имён {}/{}, сдач после нарядной {}, отдал себя {}, Лишний {}, мамина кассета {}",
                e, Names.count(st), Names.threshold(), st.count("dep_after"), st.flag("gave_self"),
                st.flag("extra_found") ? "найден" : "нет", Tapes.has(st, "interview", "int_vika") ? "цела" : "стёрта");
        Story.tpSpread(st.point("cp_platform"));
        NbtCompound tv = Net.msg("trainvis");
        tv.putBoolean("on", false);
        Net.all(w().getServer(), tv);
        // кооп: кто остался в «40» — видит «Обходчика» сам; остальные — общую
        for (ServerPlayerEntity p : Story.players()) if (st.flag("stayed:" + nm(p)) && !e.equals("obkh")) Story.sceneFor(p, "end_obkh", null);
        Story.scene("end_" + e, () -> {
            Story.setStep("end_" + e);
            int d = 0;
            if (!Tapes.has(st, "interview", "int_vika")) d = Story.seq("end_mom", null, true);   // модификатор: мамина кассета стёрта
            if (!st.flag("extra_found")) Sched.after(d + 10, () -> Story.seq("end_extra", null, true));
            Sched.after(d + 120, () -> {
                Story.titleAll("Конец", e.equals("poimenno") ? "ПОИМЁННО" : e.equals("naryad") ? "НАРЯД ЗАКРЫТ" : "ОБХОДЧИК", "Порожняк · Yompix Games");
                Net.all(w().getServer(), Net.msg("credits"));
            });
        });
    }

    // ===================== тик и загрузка =====================
    public static void tick(StoryState st) {
        if (st.chapter == 7 || st.chapter == 8) evidenceTick(st);
        if (st.chapter == 7 && night4()) night4Tick();
        if (st.chapter == 8) {
            if (st.step.equals("c5_board")) boardTick(st);
            else if (st.step.startsWith("c5_car")) carsTick(st);
        }
    }

    public static void loaded(StoryState st) {
        CAR.clear();
        OUT.clear();
        NAMELESS.clear();
        if (st.chapter == 6 && st.step.equals("end2")) Sched.after(300, Part3::startChapter4);   // сохранения 0.6: часть 3 продолжается
        if (st.chapter == 7) {
            day4();
            if (st.step.equals("c4_chairs")) Story.setStep("c4_gena");
            if (st.step.equals("c4_rollcall") || st.step.equals("c4_extra")) Story.setStep("c4_names");
        }
        if (st.chapter == 8) {   // поезд и вагоны — заново с перрона
            st.chapter = 7;
            Sched.after(40, Part3::startChapter5);
        }
    }

    /** Выбор главы: 7 — IV, 8 — V. */
    public static void jump(int chapter) {
        Part2.jump(5);
        StoryState st = st();
        Sched.clear();
        st.chapter = 6;
        st.ending = "2";
        Story.setStep("end2");
        startChapter4();
        if (chapter == 8) {
            Sched.clear();
            st.chapter = 7;
            startChapter5();
        }
    }
}
