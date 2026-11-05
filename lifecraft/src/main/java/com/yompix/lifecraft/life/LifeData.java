package com.yompix.lifecraft.life;

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

    public static int lessonsNeeded(int edu) {
        return edu <= SCHOOL_END ? 3 : 4;
    }

    public String eduName() {
        if (edu <= SCHOOL_END) return edu + " класс";
        if (edu <= UNI_END) return (edu - SCHOOL_END) + " курс вуза";
        return "Выпускник вуза";
    }

    public boolean adult() {
        return edu > SCHOOL_END;
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

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("money", money);
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
        return n;
    }

    public static LifeData fromNbt(NbtCompound n) {
        LifeData d = new LifeData();
        if (n.contains("money")) d.money = n.getInt("money");
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
        return d;
    }
}
