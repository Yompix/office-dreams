package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.horror.Sched;
import com.yompix.glukhoman.entity.WatcherEntity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.world.MapBuilder;
import com.yompix.glukhoman.world.Part2Map;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Часть вторая «Порожняка» (0.6): глава II «Порожняк» (день-расследование, ночь 3) и глава III «Перегон» (слепая дрезина,
 * 41-й км, нарядная — поворот). Главы в StoryState: 4 — глава II, 5 — глава III, 6 — конец второй части.
 * Ничего не «принеси»: улики — это то, что ты решаешь снять (и одно имя на шпалах — живой человек: снял — внёс его в список),
 * правило — проверяешь плёнкой, дрезина — навигация на слух и по схеме, нарядная — выбор.
 */
public final class Part2 {
    private Part2() {}

    static ServerWorld w() {
        return Story.w();
    }

    static StoryState st() {
        return Story.st();
    }

    // ===================== глава II «Порожняк» =====================
    public static void startChapter2() {
        StoryState st = st();
        if (st.chapter >= 4) return;
        st.chapter = 4;
        st.markDirty();
        Story.clearCreatures();
        w().setTimeOfDay(1500);
        Story.lamps("station", false);
        for (BlockPos q : Story.lampsOf("signal")) Story.lampSet(q, false);
        Story.tpSpread(st.point("cp_platform"));
        for (ServerPlayerEntity p : Story.players()) p.teleport(w(), p.getX(), p.getY(), p.getZ(), 90, 4);
        Story.dayResidents();
        Story.fxAll("fade", 60);
        Story.titleAll("Глава II", "ПОРОЖНЯК", "Табло горит без тока");
        Story.setStep("c2_gena");
        Sched.after(140, () -> Story.scene(st.flag("kept1") ? "c2_gena" : "c2_gena_ok", () -> {
            spawnGena();
            Story.setStep("c2_board");
        }));
    }

    /** Гена — живьём: стоит у ящика, курит, ждёт кассету. */
    public static void spawnGena() {
        if (Story.resident("gena") != null) return;
        ResidentEntity g = Story.spawnResident("gena", new BlockPos(5, 66, 19));
        if (g != null) g.setYaw(0);
    }

    /** Разговор с жителем в главах II–III. true — обработано. */
    static boolean resident(ServerPlayerEntity p, ResidentEntity r) {
        StoryState st = st();
        String who = r.who();
        if (who.equals("gena")) {
            Story.seq("talk_gena", r.getBlockPos(), false);
            Story.gesture(r, "watch", null, 0);
            return true;
        }
        if (!who.equals("zakhar")) return false;
        if (st.step.equals("c2_board")) {
            int d = Story.seq("c2_zakhar", r.getBlockPos(), false);
            for (BlockPos q : Story.lampsOf("board")) Story.lampSet(q, true);   // табло горит без тока
            Story.sound(Story.lampsOf("board").isEmpty() ? r.getBlockPos() : Story.lampsOf("board").get(0), "relay", 1f, 0.9f);
            Sched.after(120, () -> {
                for (BlockPos q : Story.lampsOf("board")) Story.lampSet(q, false);
            });
            Story.gesture(r, "point", Vec3d.ofCenter(new BlockPos(14, 66, 37)), 1.6);   // к дому Поли
            Sched.after(d + 10, Part2::clues);
            return true;
        }
        if (st.step.equals("c2_morning")) {
            int d = Story.seq("c2_end", r.getBlockPos(), false);
            Story.gesture(r, "give", null, 0);
            Sched.after(Math.min(d, 120), () -> {
                for (ServerPlayerEntity q : Story.players()) Story.give(q, scheme());
                Story.msgAll("Захар отдал путевую схему Саввы. На обороте — «Не сдавай кассеты».");
            });
            Sched.after(d + 20, Part2::startChapter3);
            return true;
        }
        return false;
    }

    static ItemStack scheme() {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Путевая схема Саввы"));
        s.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("57… 58 — НЕ ОСТАНАВЛИВАТЬСЯ"), Text.literal("3-я стрелка — ВЛЕВО (вправо — тупик к яме)"),
                Text.literal("Стук сзади — не оборачивайся, качай"), Text.literal("На обороте: «Не сдавай кассеты»"))));
        return s;
    }

    /** День: кто ещё пропадал. Сумерки, на 58-м пикете на путях кто-то стоит (просто стоит). */
    static void clues() {
        StoryState st = st();
        Story.setStep("c2_clues");
        w().setTimeOfDay(12400);
        WatcherEntity wt = Story.spawn(GContent.WATCHER, new BlockPos(124, 66, MapBuilder.LZ));
        if (wt != null) {
            wt.posted = true;
            wt.setYaw(90);
            watcher = wt;
        }
        Story.msgAll("Смеркается. Восточнее переезда, на шпалах — что-то вырезано. Видно только в ИК (G).");
    }

    static WatcherEntity watcher;

    /** Камера сейчас «держит» эти цели (Cam.scan): расписание — сквозь щель в досках; надписи — только в ИК. */
    public static List<String> targets() {
        StoryState st = st();
        List<String> t = new ArrayList<>();
        if (st.chapter == 4 && st.step.equals("c2_clues")) {
            t.add("c2_schedule");
            for (Object[] c : Part2Map.CARVINGS) t.add((String) c[2]);
        }
        return t;
    }

    static void cluesTick(StoryState st) {
        for (ServerPlayerEntity p : Story.players()) {
            if (!Cam.up(p)) continue;
            var s = Cam.session(p);
            if (s.getOrDefault("c2_schedule", 0) >= 30 && st.set("clue_schedule")) {
                Story.seq("c2_schedule", p.getBlockPos(), true);
                Story.msgAll("Есть: расписание бабы Поли — «Порожняк 3:14, ежедн. с 14.10. 1953»");
            }
            for (Object[] c : Part2Map.CARVINGS) {
                String id = (String) c[2];
                if (s.getOrDefault(id, 0) < 30 || !st.set("name:" + id)) continue;
                BlockPos ls = st.point("loudspeaker");
                Story.sound(ls, "loudspeaker", 1.6f, 0.9f);   // громкоговоритель за километр произносит имя
                if (id.equals("carve_false")) {
                    st.set("listed:petrovich");
                    int d = Story.seq("c2_name_false", ls, true);
                    Sched.after(d + 10, () -> Story.seq("c2_listed", p.getBlockPos(), true));
                } else {
                    st.inc("names");
                    Story.seq("c2_name_" + id.substring(6), ls, true);
                }
            }
        }
        if (st.flag("clue_schedule") && st.count("names") >= 2 && st.step.equals("c2_clues")) {
            Story.setStep("c2_evening");
            if (watcher != null) watcher.discard();
            Story.msgAll("Имена сходятся с номерами. Сорок мест. Вечереет — в дежурку.");
        }
    }

    /** Стул в дежурке. true — обработано. */
    static boolean chair(ServerPlayerEntity p) {
        StoryState st = st();
        switch (st.step) {
            case "c2_evening" -> evening3();
            case "c2_wait" -> {
                Story.fxAll("fade", 80);
                st.data.putInt("night", 3);
                st.markDirty();
                Sched.after(40, Night::start);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static void evening3() {
        StoryState st = st();
        Story.fxAll("fade", 60);
        w().setTimeOfDay(16000);
        Story.lamps("station", true);
        Story.setStep("c2_announce");
        st.data.putString("false_rule", "rule_n3_2");   // «Лунатиков не будить» — ложь
        st.data.putString("rules_seq", "rules_n3");
        BlockPos ls = st.point("loudspeaker");
        Sched.after(80, () -> {
            Story.announcing = true;
            Story.sound(ls, "loudspeaker", 2.5f, 1f);
            int d = Story.seq("rules_n3", ls, true);
            Sched.after(d + 20, () -> {
                for (ServerPlayerEntity p : Story.players()) if (Cam.up(p)) {
                    Cam.lower(p, true);
                    Cam.raise(p);
                }
                Story.announcing = false;
                Story.setStep("c2_wait");
                Story.clearResidents();
            });
        });
    }

    // ---------- ночь 3: Безымянные, ложная угроза, Зина в вагоне ----------
    static final List<ResidentEntity> NAMELESS = new ArrayList<>();
    static boolean ghostSent;

    public static boolean night3() {
        return Night.running && st().data.getInt("night") == 3;
    }

    /** Из Night.start: у крайних домов стоят Безымянные (без лиц), смотрят. Снимешь — отступят в темноту. */
    static void night3Start() {
        NAMELESS.clear();
        ghostSent = false;
        StoryState st = st();
        for (String pt : new String[]{"petrovich_door", "vika_door", "polya_door"}) {
            BlockPos q = st.point(pt);
            if (q == null) continue;
            ResidentEntity r = Story.spawnResident("nameless", q.add(0, 0, -3));
            if (r != null) NAMELESS.add(r);
        }
        // ложная угроза: стук в окно дежурки — и никого
        Sched.after(160, () -> Story.sound(new BlockPos(-11, 67, 25), "knock", 1.2f, 0.9f));
    }

    static void night3Tick() {
        StoryState st = st();
        NAMELESS.removeIf(r -> {
            if (r.isRemoved()) return true;
            if (Cam.filming(w(), r) != null) {   // на плёнке их держат — отступают
                Story.sound(r.getBlockPos(), "breath_close", 0.6f, 0.7f);
                r.discard();
                return true;
            }
            return false;
        });
        // поезд встал дверью напротив — в видоискателе на лавке сидит Зина (живая, спит дома) и машет
        if (!ghostSent && Night.phase() == 2) {
            ghostSent = true;
            String who = st.data.getString("n2_called").contains("zina") || st.flag("taken:zina") ? "vika" : "zina";
            for (ServerPlayerEntity p : Story.players()) {
                NbtCompound m = Net.msg("ghost");
                m.putString("who", who);
                m.putDouble("x", Math.max(-10, Math.min(20, p.getX())));
                m.putDouble("y", 66.4);
                m.putDouble("z", MapBuilder.LZ + 0.2);
                Net.send(p, m);
            }
            Sched.after(60, () -> {
                for (ServerPlayerEntity p : Story.players()) if (Cam.up(p)) {
                    Story.seq("n3_ghost", p.getBlockPos(), true);
                    break;
                }
            });
        }
    }

    /** Ночь 3 кончилась: «Проход поезда» одним планом из видоискателя, потом утро. */
    static void night3Over() {
        StoryState st = st();
        for (ResidentEntity r : NAMELESS) r.discard();
        NAMELESS.clear();
        st.data.putInt("night", 0);
        st.markDirty();
        Story.scene("c2_pass", () -> {
            w().setTimeOfDay(1000);
            Story.lamps("station", false);
            Story.fxAll("fade", 80);
            Story.tpSpread(st.point("cp_platform"));
            Story.dayResidents();
            spawnGena();
            Story.setStep("c2_morning");
            String called = st.data.getString("n2_called");
            Glukhoman.LOG.info("Глухомань: ночь 3 окончена, позвали {}, забрал {}", called, st.count("taken"));
        });
    }

    // ===================== глава III «Перегон» =====================
    static MinecartEntity cart;
    static double cartX, v, slowT, stopT, jointT;
    static boolean leftSwitch;

    public static void startChapter3() {
        StoryState st = st();
        if (st.chapter >= 5) return;
        st.chapter = 5;
        st.markDirty();
        Story.clearCreatures();
        w().setTimeOfDay(18500);
        Story.lamps("station", false);
        Story.fxAll("fade", 80);
        Story.titleAll("Глава III", "ПЕРЕГОН", "Сорок первый километр");
        spawnCart();
        Story.tpSpread(new BlockPos(66, 66, MapBuilder.LZ + 2));
        Story.setStep("c3_start");
        Sched.after(100, () -> Story.seq("c3_start", st.find("radio"), true));
    }

    static void spawnCart() {
        if (cart != null && !cart.isRemoved()) cart.discard();
        for (MinecartEntity m : w().getEntitiesByClass(MinecartEntity.class, new Box(BlockPos.ORIGIN).expand(700), e -> e.getCommandTags().contains("glk_handcar"))) m.discard();
        BlockPos q = st().point("c3_cart");
        cart = EntityType.MINECART.create(w());
        if (cart == null || q == null) return;
        cartX = q.getX() + 0.5;
        cart.refreshPositionAndAngles(cartX, q.getY() + 0.2, q.getZ() + 0.5, 90, 0);
        cart.setNoGravity(true);
        cart.setCustomName(Text.literal("Дрезина"));
        cart.addCommandTag("glk_handcar");
        w().spawnEntity(cart);
        v = 0;
        leftSwitch = false;
    }

    static boolean onRail(double x) {
        for (int[] r : Part2Map.RAILS) if (x >= r[0] && x <= r[1]) return true;
        return x <= MapBuilder.RAIL_END;
    }

    static void rideTick(StoryState st) {
        if (cart == null || cart.isRemoved()) {
            spawnCart();
            return;
        }
        ServerPlayerEntity rider = cart.getFirstPassenger() instanceof ServerPlayerEntity sp ? sp : testDriver;
        if (rider == testDriver && rider != null) rider.refreshPositionAndAngles(cartX, cart.getY() + 0.4, cart.getZ(), 90, 0);   // автотест: FakePlayer не умеет садиться
        if (rider != null && st.step.equals("c3_start")) Story.setStep("c3_ride");
        double dt = 0.05;
        boolean rail = onRail(cartX);
        float fwd = rider == null ? 0 : rider == testDriver ? 1 : rider.forwardSpeed, side = rider == null ? 0 : rider == testDriver ? 1 : rider.sidewaysSpeed;
        if (fwd > 0.1) v += 2.4 * dt;   // качаешь рычаг
        else v -= (rail ? 0.4 : 1.4) * dt;                             // по голым шпалам — сразу встаёт
        v = Math.max(0, Math.min(rail ? 6.0 : 4.2, v));
        if (side > 0.1 && Math.abs(cartX - Part2Map.SWITCH_X) < 14) leftSwitch = true;   // A — влево
        if (w().getTime() % 400 == 0) Glukhoman.LOG.info("Глухомань: дрезина x={} v={} рельс {}", (int) cartX, String.format("%.1f", v), rail);
        double nx = cartX + v * dt;
        // 3-я стрелка: вправо — тупик к яме
        if (cartX < Part2Map.SWITCH_X && nx >= Part2Map.SWITCH_X && !leftSwitch) {
            Story.fxAll("black", 40);
            Story.sound(cart.getBlockPos(), "crash_buzz", 1f, 0.6f);
            Story.msgAll("Вправо — тупик. Яма. Дрезину вытащили назад. (Схема Саввы: у 3-й стрелки — влево, A)");
            if (rider != null) {   // разбитая батарея: камера теряет заряд
                for (int i = 0; i < rider.getInventory().size(); i++) {
                    ItemStack c = rider.getInventory().getStack(i);
                    if (c.isOf(GContent.CAMCORDER)) c.setDamage(Math.min(c.getMaxDamage() - 1, c.getDamage() + 600));
                }
            }
            nx = Part2Map.SWITCH_X - 60;
            v = 0;
        }
        cartX = nx;
        cart.refreshPositionAndAngles(cartX, cart.getY(), cart.getZ(), 90, 0);
        cart.setVelocity(v / 20, 0, 0);
        cart.velocityModified = true;
        // стук на стыках: есть рельс — тук-тук под тобой; стоишь на голых шпалах — тук-тук СЗАДИ
        jointT += dt;
        if (rail && v > 1.5 && jointT > 3.0) {
            jointT = 0;
            Story.sound(cart.getBlockPos(), "joints", 0.8f, 1f);
        }
        boolean at58 = cartX > 116 && cartX < 134;
        if (v < 0.8 && !rail && rider != null) slowT += dt;
        else slowT = 0;
        if ((slowT > 3 || (at58 && v < 1.0 && rider != null)) && jointT > 2.5) {
            jointT = 0;
            Story.sound(new BlockPos((int) cartX - 14, 66, MapBuilder.LZ), "joints", 1.1f, 0.9f);
            if (st.set("behind_said")) Story.seq("c3_behind", cart.getBlockPos(), true);
            if (at58 && st.set("said58")) Story.seq("c3_58", cart.getBlockPos(), true);
            if (watcher == null || watcher.isRemoved()) {   // он идёт следом, пока стоишь
                watcher = Story.spawn(GContent.WATCHER, new BlockPos((int) cartX - 26, 66, MapBuilder.LZ));
                if (watcher != null) watcher.setYaw(-90);
            }
        }
        if (watcher != null && !watcher.isRemoved() && v > 3.5 && watcher.squaredDistanceTo(cart) > 30 * 30) {
            watcher.discard();   // отстал в тумане
            watcher = null;
        }
        // остальные бригадиры «на дрезине» (места одно — переносим следом)
        if (rider != null && w().getTime() % 40 == 0) for (ServerPlayerEntity p : Story.players())
            if (p != rider && p.squaredDistanceTo(cart) > 12 * 12) p.teleport(w(), cartX - 1.5, 66, MapBuilder.LZ + 2.5, 90, 0);
        if (w().getTime() % 10 == 0) Story.hudAll();
        if (cartX >= Part2Map.END_X) arrive();
    }

    public static String rideHud() {
        int pk = (int) Math.max(57, Math.min(67, 57 + (cartX - 96) / 28));
        return "Пикет " + pk + " · " + (onRail(cartX) ? "стук на стыках — рельс есть" : "тихо — голые шпалы, качай") + " · " + (int) cartX + " м";
    }

    static void arrive() {
        StoryState st = st();
        testDriver = null;
        if (cart != null) {
            cart.removeAllPassengers();
            cart.discard();
            cart = null;
        }
        if (watcher != null) watcher.discard();
        Story.setStep("c3_camp");
        Story.seq("c3_camp", st.point("camp_w"), true);
        Story.tpSpread(st.point("camp_w"));
        // Обходчик учится: приходит с той стороны, где ты чаще прятался на станции
        ServerPlayerEntity p0 = Story.players().isEmpty() ? null : Story.players().get(0);
        Vec3d spot = p0 == null ? null : Night.learnedSpot(w(), p0);
        boolean west = spot != null && spot.x < 0;
        Sched.after(200, () -> {
            watcher = Story.spawn(GContent.WATCHER, st.point(west ? "camp_w" : "camp_e").add(west ? -6 : 0, 0, 10));
            Story.msgAll(west ? "Он вышел с запада — оттуда, где ты прятался на станции. Он помнит." : "Он вышел с востока. Он помнит, куда ты смотрел.");
        });
    }

    /** Присел в бараке на нарах — Обходчик тебя не видит. */
    public static boolean hidden(ServerPlayerEntity p) {
        return st().step.equals("c3_camp") && p.isSneaking() && Story.inZone(p, "barrack");
    }

    static void campTick(StoryState st) {
        for (ServerPlayerEntity p : Story.players()) {
            if (!Story.inZone(p, "naryad") || !st.set("naryad_in")) continue;
            BlockPos q = st.point("naryad_mine");
            ServerPlayerEntity listed = p;
            if (q != null) Part2Map.nameSign(w(), q, listed.getName().getString());
            if (watcher != null) watcher.discard();
            Story.scene("c3_naryad", () -> {
                for (ServerPlayerEntity x : Story.players()) {
                    ItemStack t = new ItemStack(GContent.CASSETTE);
                    t.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Кассета Саввы, 1991"));
                    Story.give(x, t);
                }
                Story.setStep("c3_choice");
                for (ServerPlayerEntity x : Story.players())
                    Story.ask(x, "naryad", "Нарядная. Все кассеты из ящика — здесь.", "Забрать свои кассеты (якоря вернутся, но сдача засчитана)",
                            "Сжечь нарядную (сданные кассеты погибнут — и список поезда тоже)");
            });
        }
    }

    public static void answer(ServerPlayerEntity p, int i) {
        StoryState st = st();
        if (!st.step.equals("c3_choice") || !st.set("naryad_choice")) return;
        if (i == 0) {
            st.set("took_tapes");
            Story.msgAll("Кассеты — в рюкзак. Наклейки с номерами не отклеиваются.");
        } else {
            st.set("burned_naryad");
            BlockPos t = st.point("naryad_table");
            if (t != null) for (BlockPos q : BlockPos.iterate(t.add(-4, 0, -2), t.add(4, 2, 0)))
                if (w().getBlockState(q.up()).isAir() && !w().getBlockState(q).isAir()) w().setBlockState(q.up(), Blocks.FIRE.getDefaultState());
            Story.sound(t, "fuse", 1f, 0.7f);
            Story.msgAll("Нарядная горит. Наклейки сворачиваются. Где-то на линии — гудок, как вскрик.");
        }
        Story.scene("c3_savva", Part2::endPart2);
    }

    static void endPart2() {
        StoryState st = st();
        if (st.chapter >= 6) return;
        st.chapter = 6;
        st.ending = "2";
        st.markDirty();
        Story.setStep("end2");
        int d = Story.seq("end2", st.find("radio"), true);
        Sched.after(d + 20, () -> {
            Story.titleAll("Конец второй части", "ПОРОЖНЯК", "Дальше — глава IV: перекличка");
            Sched.after(300, Part3::startChapter4);   // 0.7
            Glukhoman.LOG.info("Глухомань: часть 2 пройдена. Имён {}, внесён Петрович {}, нарядная {}", st.count("names"), st.flag("listed:petrovich"),
                    st.flag("burned_naryad") ? "сожжена" : "кассеты забраны");
        });
    }

    // ===================== тик и загрузка =====================
    public static void tick(StoryState st) {
        if (st.chapter == 4) {
            if (st.step.equals("c2_clues")) cluesTick(st);
            if (night3()) night3Tick();
        } else if (st.chapter == 5) {
            if (st.step.equals("c3_camp")) campTick(st);
        }
    }

    /** Загрузка мира: достроить карту части 2 и продолжить с того же места. */
    public static void loaded(StoryState st) {
        cart = null;
        watcher = null;
        NAMELESS.clear();
        if (st.chapter == 3 && st.step.equals("end1")) Sched.after(300, Part2::startChapter2);   // старые сохранения: часть 2 продолжается
        if (st.chapter == 4) {
            spawnGena();
            if (st.step.equals("c2_gena")) Story.setStep("c2_board");
            if (st.step.equals("c2_announce")) Story.setStep("c2_evening");
            if (st.step.equals("c2_clues")) clues();
        }
        if (st.chapter == 5) {
            if (st.step.equals("c3_ride") || st.step.equals("c3_start")) {
                spawnCart();
                Story.setStep("c3_start");
            }
            if (st.step.equals("c3_choice")) st.flags.remove("naryad_choice");
            if (st.step.equals("c3_choice") || st.step.equals("c3_camp")) {
                st.flags.remove("naryad_in");
                Story.setStep("c3_camp");
            }
        }
    }

    /** Выбор главы в новом мире: 4 — глава II, 5 — глава III (пролог и предыдущие главы — «пройдены» на сервере). */
    public static void jump(int chapter) {
        StoryState st = st();
        st.set("p_scene");
        Tapes.commit(st, "prologue", "rec", Story.titleOf("prologue"), java.util.Map.of("polya", 400, "train", 300));
        Story.clearResidents();
        st.chapter = 3;
        st.ending = "1";
        Story.setStep("end1");
        startChapter2();
        if (chapter == 5) {
            Sched.clear();
            startChapter3();
        }
    }

    // ---------- автотест ----------
    /** FakePlayer в Fabric не может сесть в вагонетку — автотест «качает» за него (те же правила: W, A, скорость, стрелка, 58-й). */
    public static ServerPlayerEntity testDriver;


    public static MinecartEntity qaCart() {
        return cart;
    }
}
