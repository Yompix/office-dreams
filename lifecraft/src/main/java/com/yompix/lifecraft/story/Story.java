package com.yompix.lifecraft.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
import com.yompix.lifecraft.entity.CitizenEntity;
import com.yompix.lifecraft.life.Jobs;
import com.yompix.lifecraft.life.Life;
import com.yompix.lifecraft.life.LifeData;
import com.yompix.lifecraft.life.LifeNet;
import com.yompix.lifecraft.world.TownState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сюжет: главы → миссии → шаги из lifecraft_story.json (его пишет tools/story.py).
 * Сервер ведёт каждого игрока по шагам, клиент показывает разговоры, кат-сцены, цели и заставки.
 */
public final class Story {
    private Story() {}

    private static JsonObject root;
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    /** То, что живёт только пока игрок в игре: таймеры, состояние гонки и экзамена. */
    static final class Run {
        int ticks, timer, countdown = -1, rivalIndex, violations;
        boolean waitingDialog, dialogSent, started, ready;
        Vec3d lastCarPos;
        int lastStep = -1;
    }

    public static JsonObject root() {
        if (root == null) {
            try (var in = Story.class.getResourceAsStream("/lifecraft_story.json")) {
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                LifeCraft.LOG.error("Не удалось прочитать сюжет", e);
                root = new JsonObject();
                root.add("chapters", new JsonArray());
                root.add("characters", new JsonObject());
            }
        }
        return root;
    }

    static JsonArray chapters() {
        return root().getAsJsonArray("chapters");
    }

    @Nullable
    static JsonObject chapter(LifeData d) {
        return d.chapter < chapters().size() ? chapters().get(d.chapter).getAsJsonObject() : null;
    }

    @Nullable
    static JsonObject mission(LifeData d) {
        JsonObject c = chapter(d);
        if (c == null) return null;
        JsonArray ms = c.getAsJsonArray("missions");
        return d.mission < ms.size() ? ms.get(d.mission).getAsJsonObject() : null;
    }

    @Nullable
    public static JsonObject step(LifeData d) {
        JsonObject m = mission(d);
        if (m == null) return null;
        JsonArray ss = m.getAsJsonArray("steps");
        return d.step < ss.size() ? ss.get(d.step).getAsJsonObject() : null;
    }

    static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    static int num(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    static Run run(ServerPlayerEntity p) {
        return RUNS.computeIfAbsent(p.getUuid(), u -> new Run());
    }

    public static boolean active(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        return d.storyStarted && !d.storyDone;
    }

    // ------------------------------------------------------------------------------------ старт и вход в шаг
    public static void onJoin(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        applyAge(p, d.age);
        if (d.prevMode >= 0) restoreAfterCutscene(p, d); // вышел посреди кат-сцены
        RUNS.remove(p.getUuid());
        TownState town = TownState.get(p.getServerWorld());
        if (town.places.isEmpty()) return;
        // вышла новая глава: кто уже прошёл сюжет — продолжает с неё
        if (d.storyDone && chapter(d) != null) {
            d.storyDone = false;
            d.flags.remove("entered:" + stepKey(d));
            Life.message(p, "Новоград", "Вышла новая глава сюжета: «" + str(chapter(d), "subtitle", "") + "»!");
            run(p).timer = 60;
        }
        // игра с друзьями: новичок подхватывает сюжет того, кто ушёл дальше всех
        ServerPlayerEntity lead = leader(p);
        if (lead != null && (!d.storyStarted || stepKey(Life.get(lead)) > stepKey(d))) {
            copyProgress(Life.get(lead), d);
            applyAge(p, d.age);
            // появиться рядом с другом, а не на точке входа
            p.teleport(lead.getServerWorld(), lead.getX() + 1, lead.getY(), lead.getZ() + 1, lead.getYaw(), 0);
            p.sendMessage(net.minecraft.text.Text.literal("Ты присоединился к сюжету друзей: " + str(chapter(d) != null ? chapter(d) : new JsonObject(), "title", "")).formatted(Formatting.AQUA), false);
            run(p).timer = 20;
            return;
        }
        if (!d.storyStarted) {
            d.storyStarted = true;
            d.chapter = d.mission = d.step = 0;
            run(p).timer = 30;
        } else if (!d.storyDone) {
            run(p).timer = 20;
        }
    }

    /** Игрок онлайн, который дальше всех по сюжету (кроме p). */
    @Nullable
    static ServerPlayerEntity leader(ServerPlayerEntity p) {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) {
            if (q == p || !active(q)) continue;
            if (best == null || stepKey(Life.get(q)) > stepKey(Life.get(best))) best = q;
        }
        return best;
    }

    static void copyProgress(LifeData from, LifeData to) {
        to.storyStarted = true;
        to.storyDone = from.storyDone;
        to.chapter = from.chapter;
        to.mission = from.mission;
        to.step = from.step;
        to.stepCount = 0;
        for (String f : from.flags) if (!f.startsWith("entered:")) to.flags.add(f);
        to.age = from.age;
        if (from.edu > to.edu) to.edu = from.edu;
    }

    public static int playersInStory(ServerPlayerEntity p) {
        int n = 0;
        for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) if (active(q)) n++;
        return n;
    }

    /** Клиент догрузил мир и закрыл экран загрузки — можно показывать кат-сцены и разговоры. */
    public static void onClientReady(ServerPlayerEntity p) {
        run(p).ready = true;
    }

    public static void onLeave(ServerPlayerEntity p) {
        RUNS.remove(p.getUuid());
    }

    /** Каждый тик: таймеры, проверки целей. */
    public static void tick(ServerPlayerEntity p) {
        if (!active(p)) return;
        LifeData d = Life.get(p);
        Run r = run(p);
        r.ticks++;
        if (!r.started) {
            if (!r.ready || --r.timer > 0) return;
            r.started = true;
            enter(p, d, freshStep(d));
            return;
        }
        JsonObject s = step(d);
        if (s == null) return;
        StepLogic.tick(p, d, r, s);
    }

    private static boolean freshStep(LifeData d) {
        return !d.flags.contains("entered:" + stepKey(d));
    }

    static int stepKey(LifeData d) {
        return d.chapter * 10000 + d.mission * 100 + d.step;
    }

    /** Войти в текущий шаг. doActions=false при повторном входе после перезахода — чтобы не выдать награды дважды. */
    static void enter(ServerPlayerEntity p, LifeData d, boolean doActions) {
        JsonObject s = step(d);
        Run r = run(p);
        r.lastStep = stepKey(d);
        r.waitingDialog = false;
        r.dialogSent = false;
        r.timer = 0;
        r.countdown = -1;
        r.violations = 0;
        r.rivalIndex = 0;
        if (s == null) {
            finish(p, d);
            return;
        }
        if (doActions && s.has("do")) {
            for (JsonElement a : s.getAsJsonArray("do")) Actions.run(p, d, a.getAsJsonArray());
            d.flags.add("entered:" + stepKey(d));
        }
        StepLogic.enter(p, d, r, s);
        sendObjective(p, d);
        Life.sync(p);
    }

    /** Шаг выполнен — следующий. */
    public static void advance(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        JsonObject m = mission(d);
        d.flags.remove("entered:" + stepKey(d));
        d.step++;
        d.stepCount = 0;
        if (m != null && d.step >= m.getAsJsonArray("steps").size()) {
            LifeNet.send(p, "mission_done", text("title", str(m, "title", "")));
            p.playSoundToPlayer(ModContent.LEVEL_UP, SoundCategory.MASTER, 0.8f, 1f);
            Life.earn(p, 15, "миссия выполнена");
            d.step = 0;
            d.mission++;
            JsonObject c = chapter(d);
            if (c != null && d.mission >= c.getAsJsonArray("missions").size()) {
                d.mission = 0;
                d.chapter++;
            }
        }
        enter(p, d, true);
        // друзья в той же истории идут дальше вместе
        for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) {
            if (q == p || !active(q)) continue;
            LifeData dq = Life.get(q);
            boolean newMission = dq.mission != d.mission || dq.chapter != d.chapter;
            dq.flags.remove("entered:" + stepKey(dq));
            copyProgress(d, dq);
            LifeNet.send(q, "dialog_close", new NbtCompound());
            if (newMission && m != null) {
                LifeNet.send(q, "mission_done", text("title", str(m, "title", "")));
                Life.earn(q, 15, "миссия выполнена");
                com.yompix.lifecraft.life.Achievements.give(q, "coop");
                com.yompix.lifecraft.life.Achievements.give(p, "coop");
            }
            enter(q, dq, true);
        }
    }

    private static void finish(ServerPlayerEntity p, LifeData d) {
        d.storyDone = true;
        d.achievements.add("story");
        Life.message(p, "Новоград", "Сюжет пройден! Теперь город твой: работай, учись, копи на машины, играй в казино с друзьями. Удачи!");
        LifeNet.send(p, "objective", new NbtCompound());
        Life.sync(p);
    }

    // ------------------------------------------------------------------------------------ события от игры
    /** Нажали на жителя. true — разговор взял сюжет. */
    public static boolean onTalk(ServerPlayerEntity p, CitizenEntity c) {
        if (!active(p)) return false;
        LifeData d = Life.get(p);
        JsonObject s = step(d);
        if (s == null) return false;
        String type = str(s, "type", "");
        if ((type.equals("talk") || type.equals("party")) && str(s, "npc", "").equals(c.getStoryId())) {
            Run r = run(p);
            if (!r.dialogSent) {
                for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) {
                    if (!active(q) || q.distanceTo(p) > 32) continue;
                    sendDialog(q, linesFor(q, s), s.has("choices") && q == p ? s.getAsJsonArray("choices") : null);
                    run(q).dialogSent = true;
                    run(q).waitingDialog = true;
                }
            }
            return true;
        }
        return !c.getStoryId().isEmpty() && type.equals("talk");
    }

    public static void onDialogDone(ServerPlayerEntity p, int choice) {
        if (!active(p)) return;
        LifeData d = Life.get(p);
        Run r = run(p);
        JsonObject s = step(d);
        if (s == null || !r.waitingDialog) return;
        r.waitingDialog = false;
        if (s.has("choices") && choice >= 0 && choice < s.getAsJsonArray("choices").size()) {
            String flag = str(s.getAsJsonArray("choices").get(choice).getAsJsonObject(), "flag", "");
            if (!flag.isEmpty()) d.flags.add(flag);
        }
        for (String id : NPC_IDS) {
            CitizenEntity c = Npcs.find(p.getServerWorld(), id);
            if (c != null && c.getActivity().scene()) c.lockActivity(null);
        }
        advance(p);
    }

    static final String[] NPC_IDS = {"mom", "dad", "tema", "lisa", "gleb", "teacher", "irina", "instructor", "petrov", "baron", "guard", "marco", "zhanna", "coach"};

    public static void onCutsceneDone(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.prevMode >= 0) restoreAfterCutscene(p, d);
        JsonObject s = step(d);
        if (active(p) && s != null && str(s, "type", "").equals("cutscene")) advance(p);
    }

    public static void onCreditsDone(ServerPlayerEntity p) {
        JsonObject s = step(Life.get(p));
        if (active(p) && s != null && str(s, "type", "").equals("credits")) advance(p);
    }

    public static void onClue(ServerPlayerEntity p, BlockPos pos) {
        if (active(p)) StepLogic.clue(p, pos);
    }

    public static void onPhoneOpened(ServerPlayerEntity p) {
        progress(p, "phone", null, 1);
    }

    public static void onLesson(ServerPlayerEntity p, int grade) {
        JsonObject s = step(Life.get(p));
        if (s != null && grade >= num(s, "min", 2)) progress(p, "lesson", null, num(s, "count", 1));
    }

    /** Сейчас сюжет ждёт именно эту работу (пускаем к пульту/печи без выбора работы). */
    public static boolean wantsJob(ServerPlayerEntity p, String job) {
        JsonObject s = active(p) ? step(Life.get(p)) : null;
        return s != null && str(s, "type", "").equals("job") && str(s, "job", "").equals(job);
    }

    public static void onJobDone(ServerPlayerEntity p, String job) {
        JsonObject s = step(Life.get(p));
        if (s != null && str(s, "job", "").equals(job)) progress(p, "job", null, num(s, "count", 1));
    }

    /** Разрешён ли урок сейчас вне расписания (сюжетный урок). */
    public static boolean forcedLesson(ServerPlayerEntity p) {
        JsonObject s = active(p) ? step(Life.get(p)) : null;
        return s != null && str(s, "type", "").equals("lesson") && s.has("force");
    }

    /** Разрешена ли работа без нужного образования (подработка по сюжету). */
    public static boolean storyJob(ServerPlayerEntity p, String job) {
        JsonObject s = active(p) ? step(Life.get(p)) : null;
        return s != null && str(s, "job", "").equals(job);
    }

    private static void progress(ServerPlayerEntity p, String type, @Nullable Object unused, int need) {
        if (!active(p)) return;
        LifeData d = Life.get(p);
        JsonObject s = step(d);
        if (s == null || !str(s, "type", "").equals(type)) return;
        d.stepCount++;
        if (d.stepCount >= need) advance(p);
        else {
            sendObjective(p, d);
            Life.sync(p);
        }
    }

    // ------------------------------------------------------------------------------------ отправка клиенту
    static NbtCompound text(String k, String v) {
        NbtCompound n = new NbtCompound();
        n.putString(k, v);
        return n;
    }

    /** Если в сюжете несколько игроков и у шага есть реплики для компании — берём их. */
    static JsonArray linesFor(ServerPlayerEntity p, JsonObject s) {
        return playersInStory(p) > 1 && s.has("coop") ? s.getAsJsonArray("coop") : s.getAsJsonArray("lines");
    }

    static void sendDialog(ServerPlayerEntity p, JsonArray lines, @Nullable JsonArray choices) {
        NbtCompound n = new NbtCompound();
        n.put("lines", lines(p, lines));
        if (choices != null) {
            NbtList cs = new NbtList();
            for (JsonElement e : choices) {
                JsonObject c = e.getAsJsonObject();
                NbtCompound cn = new NbtCompound();
                cn.putString("text", str(c, "text", ""));
                cn.put("reply", lines(p, c.getAsJsonArray("reply")));
                cs.add(cn);
            }
            n.put("choices", cs);
        }
        LifeNet.send(p, "dialog", n);
    }

    static NbtList lines(ServerPlayerEntity p, JsonArray arr) {
        JsonObject chars = root().getAsJsonObject("characters");
        NbtList out = new NbtList();
        for (JsonElement e : arr) {
            JsonObject l = e.getAsJsonObject();
            String who = str(l, "who", "narrator");
            JsonObject ch = chars.has(who) ? chars.getAsJsonObject(who) : new JsonObject();
            NbtCompound ln = new NbtCompound();
            ln.putString("who", who);
            ln.putString("name", str(ch, "name", who));
            ln.putInt("color", Integer.parseInt(str(ch, "color", "ffffff"), 16));
            ln.putString("text", str(l, "text", "").replace("{player}", p.getName().getString()));
            ln.putString("voice", str(l, "voice", ""));
            CitizenEntity c = Npcs.find(p.getServerWorld(), who);
            if (c != null) {
                ln.putInt("entity", c.getId());
                // сюжетная сценка (спор, слёзы, рукопожатие) важнее обычного «говорит»
                if (!c.getActivity().scene() || c.getActivity() == CitizenEntity.Activity.TALK) c.lockActivity(CitizenEntity.Activity.TALK);
            }
            out.add(ln);
        }
        return out;
    }

    /** Текущая цель: название миссии, подсказка, координаты маркера, прогресс и таймер. */
    public static void sendObjective(ServerPlayerEntity p, LifeData d) {
        JsonObject s = step(d), m = mission(d), c = chapter(d);
        if (s == null || m == null || c == null) {
            LifeNet.send(p, "objective", new NbtCompound());
            return;
        }
        NbtCompound n = new NbtCompound();
        n.putString("chapter", str(c, "title", "") + " · " + str(c, "subtitle", ""));
        n.putString("mission", str(m, "title", ""));
        n.putString("hint", str(s, "hint", ""));
        n.putString("type", str(s, "type", ""));
        Vec3d target = StepLogic.target(p, d, run(p), s);
        if (target != null) {
            n.putDouble("x", target.x);
            n.putDouble("y", target.y);
            n.putDouble("z", target.z);
        }
        String extra = StepLogic.extra(p, d, run(p), s);
        if (!extra.isEmpty()) n.putString("extra", extra);
        LifeNet.send(p, "objective", n);
    }

    // ------------------------------------------------------------------------------------ места, возраст, кат-сцены
    @Nullable
    public static Vec3d place(ServerWorld w, String name) {
        double[] v = TownState.get(w).place(name);
        return v == null ? null : new Vec3d(v[0], v[1], v[2]);
    }

    public static float placeYaw(ServerWorld w, String name) {
        double[] v = TownState.get(w).place(name);
        return v == null ? 0 : (float) v[3];
    }

    /** Безопасная точка на земле рядом с местом. */
    static BlockPos ground(ServerWorld w, Vec3d v) {
        BlockPos p = BlockPos.ofFloored(v);
        for (int i = 0; i < 6 && !w.getBlockState(p).isAir(); i++) p = p.up();
        return p;
    }

    public static void applyAge(ServerPlayerEntity p, int age) {
        var s = p.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (s != null) s.setBaseValue(LifeData.scaleForAge(age));
    }

    static void startCutscene(ServerPlayerEntity p, LifeData d, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        d.prevMode = p.interactionManager.getGameMode().getId();
        d.returnPos = p.getBlockPos().asLong();
        d.returnYaw = p.getYaw();
        NbtList shots = new NbtList();
        Vec3d first = null;
        for (JsonElement e : s.getAsJsonArray("shots")) {
            JsonObject sh = e.getAsJsonObject();
            double[] a = TownState.get(w).place(str(sh, "from", "")), b = TownState.get(w).place(str(sh, "to", ""));
            if (a == null || b == null) continue;
            NbtCompound sn = new NbtCompound();
            sn.put("from", doubles(a));
            sn.put("to", doubles(b));
            sn.putFloat("sec", sh.get("sec").getAsFloat());
            shots.add(sn);
            if (first == null) first = new Vec3d(a[0], a[1], a[2]);
        }
        p.changeGameMode(GameMode.SPECTATOR);
        if (first != null) p.teleport(w, first.x, first.y, first.z, p.getYaw(), p.getPitch());
        NbtCompound n = new NbtCompound();
        n.put("shots", shots);
        n.put("lines", lines(p, s.getAsJsonArray("lines")));
        LifeNet.send(p, "cutscene", n);
    }

    static void restoreAfterCutscene(ServerPlayerEntity p, LifeData d) {
        GameMode mode = GameMode.byId(d.prevMode);
        d.prevMode = -1;
        p.changeGameMode(mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : mode);
        BlockPos b = BlockPos.fromLong(d.returnPos);
        p.teleport(p.getServerWorld(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5, d.returnYaw, 0);
    }

    private static NbtList doubles(double[] v) {
        NbtList l = new NbtList();
        for (double x : v) l.add(NbtDouble.of(x));
        return l;
    }

    @Nullable
    static Item item(String id) {
        Identifier i = Identifier.tryParse(id);
        return i == null || !Registries.ITEM.containsId(i) ? null : Registries.ITEM.get(i);
    }

    static int count(ServerPlayerEntity p, @Nullable Item item) {
        return item == null ? 0 : p.getInventory().count(item);
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    static void spawnCar(ServerPlayerEntity p, Vec3d at, float yaw, int variant, boolean owned) {
        ServerWorld w = p.getServerWorld();
        CarEntity car = new CarEntity(ModContent.CAR, w);
        BlockPos g = ground(w, at);
        car.refreshPositionAndAngles(at.x, g.getY(), at.z, yaw, 0);
        car.setVariant(variant);
        if (owned) car.setOwner(p.getUuid());
        w.spawnEntity(car);
    }

    static void music(ServerPlayerEntity p) {
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(SoundEvents.MUSIC_DISC_CHIRP,
                SoundCategory.RECORDS, p.getX(), p.getY(), p.getZ(), 1.5f, 1f, p.getRandom().nextLong()));
    }

    public static int topY(ServerWorld w, int x, int z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public static List<String> npcIds() {
        return List.of(NPC_IDS);
    }

    static Jobs.Job jobOf(String id) {
        return Jobs.Job.of(id);
    }

    @SuppressWarnings("unused")
    static Entity entity(ServerWorld w, UUID id) {
        return w.getEntity(id);
    }

    static void say(ServerPlayerEntity p, String text, Formatting f) {
        p.sendMessage(Text.literal(text).formatted(f), true);
    }
}
