package com.yompix.novograd.life;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Жизнь игрока: деньги, учёба, работа, сообщения в телефоне, текущее задание. */
public class LifeData {
    public static final Codec<LifeData> CODEC = NbtCompound.CODEC.xmap(LifeData::fromNbt, LifeData::toNbt);
    public static final int SCHOOL_END = 11, UNI_END = 15;

    public int money = 50;
    /** Грязные наличные (с ограблений): тратятся у оружейника, отмываются через бизнесы или у Рэя. */
    public int dirty;
    /** Акции: тикер → количество. */
    public java.util.Map<String, Integer> shares = new java.util.HashMap<>();
    public int edu = 1;            // 1–11 класс школы, 12–15 курс университета, 16 — выпускник
    public int lessons;            // сданных уроков на текущем уровне
    public List<Integer> grades = new ArrayList<>();
    public String job = "none";
    public int earned, spent, tasksDone;
    public List<String> messages = new ArrayList<>();   // "от кого\tтекст\tдень"
    public int unread;
    public long lastLesson = -100000, lastWork = -100000, lastAllowanceDay = -1;
    public String taskKind = "";
    @Nullable public BlockPos taskPos;
    public int taskReward;
    public boolean started;
    public int casinoWon, casinoLost;

    // сюжет
    public int age;                 // 0 — сюжет не начат (взрослый размер)
    public boolean storyStarted, storyDone;
    public int chapter, mission, step, stepCount;
    public java.util.Set<String> flags = new java.util.HashSet<>();
    /** Побочная миссия, которая идёт сейчас (пусто — нет), и её шаг. */
    public String side = "";
    public int sideStep;
    public int prevMode = -1;       // режим игры до кат-сцены
    public long returnPos;
    public float returnYaw;

    // потребности и прогресс
    public int energy = 100, mood = 70, talks;
    /** Вес (кг) и спортивная форма 0–100: от фастфуда полнеет, от бега и спортзала худеет. */
    public float weight = 62;
    public int fitness = 10, workouts;
    public String questType = "", questNpc = "", questItem = "", questText = "";
    public int questReward, questsDone, fines;
    public String today = "";
    public java.util.Set<String> achievements = new java.util.HashSet<>();
    /** Стиль в духе SA: внешность, гардероб (купленные вещи «id#цвет»), спортзал — сила и выносливость 0–100. */
    public Appearance look = new Appearance();
    public java.util.Set<String> wardrobe = new java.util.HashSet<>();
    public int strength, stamina, gymStrToday, gymStaToday;
    public long gymDay = -1;

    public static int lessonsNeeded(int edu) {
        return edu <= SCHOOL_END ? 3 : 4;
    }

    public String eduName() {
        if (edu <= SCHOOL_END) return edu + " класс";
        if (edu <= UNI_END) return (edu - SCHOOL_END) + " курс вуза";
        return "Выпускник вуза";
    }

    public boolean adult() {
        return true; // в Новограде все взрослые: школы и детства нет
    }

    public void addMessage(String from, String text, long day) {
        messages.add(0, from + "\t" + text + "\t" + day);
        while (messages.size() > 30) messages.remove(messages.size() - 1);
        unread++;
    }

    public void addGrade(int g) {
        grades.add(0, g);
        while (grades.size() > 12) grades.remove(grades.size() - 1);
    }

    private static NbtList strings(java.util.Collection<String> c) {
        NbtList l = new NbtList();
        for (String s : c) l.add(NbtString.of(s));
        return l;
    }

    /** Рост по возрасту: первоклассник — маленький, к 18 — обычный размер. */
    public static float scaleForAge(int age) {
        if (age <= 0) return 1f;
        if (age <= 7) return 0.62f;
        if (age <= 10) return 0.72f;
        if (age <= 13) return 0.82f;
        if (age <= 16) return 0.93f;
        return 1f;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("money", money);
        n.putInt("dirty", dirty);
        NbtCompound sh = new NbtCompound();
        shares.forEach(sh::putInt);
        n.put("shares", sh);
        n.putString("side", side);
        n.putInt("sideStep", sideStep);
        n.putFloat("weight", weight);
        n.putInt("fitness", fitness);
        n.putInt("workouts", workouts);
        n.putInt("edu", edu);
        n.putInt("lessons", lessons);
        n.putIntArray("grades", grades);
        n.putString("job", job);
        n.putInt("earned", earned);
        n.putInt("spent", spent);
        n.putInt("tasksDone", tasksDone);
        NbtList msgs = new NbtList();
        for (String m : messages) msgs.add(NbtString.of(m));
        n.put("messages", msgs);
        n.putInt("unread", unread);
        n.putLong("lastLesson", lastLesson);
        n.putLong("lastWork", lastWork);
        n.putLong("lastAllowanceDay", lastAllowanceDay);
        n.putString("taskKind", taskKind);
        if (taskPos != null) n.putLong("taskPos", taskPos.asLong());
        n.putInt("taskReward", taskReward);
        n.putBoolean("started", started);
        n.putInt("casinoWon", casinoWon);
        n.putInt("casinoLost", casinoLost);
        n.putInt("age", age);
        n.putBoolean("storyStarted", storyStarted);
        n.putBoolean("storyDone", storyDone);
        n.putInt("chapter", chapter);
        n.putInt("mission", mission);
        n.putInt("step", step);
        n.putInt("stepCount", stepCount);
        n.put("flags", strings(flags));
        n.putInt("prevMode", prevMode);
        n.putLong("returnPos", returnPos);
        n.putFloat("returnYaw", returnYaw);
        n.putInt("energy", energy);
        n.putInt("mood", mood);
        n.putInt("talks", talks);
        n.putString("questType", questType);
        n.putString("questNpc", questNpc);
        n.putString("questItem", questItem);
        n.putString("questText", questText);
        n.putInt("questReward", questReward);
        n.putInt("questsDone", questsDone);
        n.putInt("fines", fines);
        n.putString("today", today);
        n.put("achievements", strings(achievements));
        n.put("look", look.toNbt());
        n.put("wardrobe", strings(wardrobe));
        n.putInt("strength", strength);
        n.putInt("stamina", stamina);
        n.putInt("gymStrToday", gymStrToday);
        n.putInt("gymStaToday", gymStaToday);
        n.putLong("gymDay", gymDay);
        return n;
    }

    public static LifeData fromNbt(NbtCompound n) {
        LifeData d = new LifeData();
        if (n.contains("money")) d.money = n.getInt("money");
        d.dirty = n.getInt("dirty");
        NbtCompound sh = n.getCompound("shares");
        for (String k : sh.getKeys()) d.shares.put(k, sh.getInt(k));
        d.side = n.getString("side");
        d.sideStep = n.getInt("sideStep");
        if (n.contains("weight")) d.weight = n.getFloat("weight");
        if (n.contains("fitness")) d.fitness = n.getInt("fitness");
        d.workouts = n.getInt("workouts");
        if (n.contains("edu")) d.edu = Math.max(1, n.getInt("edu"));
        d.lessons = n.getInt("lessons");
        if (n.get("grades") instanceof NbtIntArray arr) for (int g : arr.getIntArray()) d.grades.add(g);
        if (n.contains("job")) d.job = n.getString("job");
        d.earned = n.getInt("earned");
        d.spent = n.getInt("spent");
        d.tasksDone = n.getInt("tasksDone");
        for (NbtElement e : n.getList("messages", NbtElement.STRING_TYPE)) d.messages.add(e.asString());
        d.unread = n.getInt("unread");
        if (n.contains("lastLesson")) d.lastLesson = n.getLong("lastLesson");
        if (n.contains("lastWork")) d.lastWork = n.getLong("lastWork");
        if (n.contains("lastAllowanceDay")) d.lastAllowanceDay = n.getLong("lastAllowanceDay");
        d.taskKind = n.getString("taskKind");
        d.taskPos = n.contains("taskPos") ? BlockPos.fromLong(n.getLong("taskPos")) : null;
        d.taskReward = n.getInt("taskReward");
        d.started = n.getBoolean("started");
        d.casinoWon = n.getInt("casinoWon");
        d.casinoLost = n.getInt("casinoLost");
        d.age = n.getInt("age");
        d.storyStarted = n.getBoolean("storyStarted");
        d.storyDone = n.getBoolean("storyDone");
        d.chapter = n.getInt("chapter");
        d.mission = n.getInt("mission");
        d.step = n.getInt("step");
        d.stepCount = n.getInt("stepCount");
        for (NbtElement e : n.getList("flags", NbtElement.STRING_TYPE)) d.flags.add(e.asString());
        d.prevMode = n.contains("prevMode") ? n.getInt("prevMode") : -1;
        d.returnPos = n.getLong("returnPos");
        d.returnYaw = n.getFloat("returnYaw");
        if (n.contains("energy")) d.energy = n.getInt("energy");
        if (n.contains("mood")) d.mood = n.getInt("mood");
        d.talks = n.getInt("talks");
        d.questType = n.getString("questType");
        d.questNpc = n.getString("questNpc");
        d.questItem = n.getString("questItem");
        d.questText = n.getString("questText");
        d.questReward = n.getInt("questReward");
        d.questsDone = n.getInt("questsDone");
        d.fines = n.getInt("fines");
        d.today = n.getString("today");
        for (NbtElement e : n.getList("achievements", NbtElement.STRING_TYPE)) d.achievements.add(e.asString());
        d.look = Appearance.fromNbt(n.getCompound("look"));
        for (NbtElement e : n.getList("wardrobe", NbtElement.STRING_TYPE)) d.wardrobe.add(e.asString());
        d.strength = n.getInt("strength");
        d.stamina = n.getInt("stamina");
        d.gymStrToday = n.getInt("gymStrToday");
        d.gymStaToday = n.getInt("gymStaToday");
        d.gymDay = n.contains("gymDay") ? n.getLong("gymDay") : -1;
        return d;
    }
}
