package com.yompix.novograd.story;

import com.google.gson.JsonObject;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Побочные миссии (их больше сотни): гонки Ники, угоны на заказ, дела Петрова, войны за районы,
 * курьерка Тёмы, тест-драйвы Глеба, тайники. Берутся в телефоне («Задания»), идут на том же движке,
 * что и сюжет: на время задания сюжет встаёт на паузу и потом продолжается с того же места.
 */
public final class SideMissions {
    private SideMissions() {}

    public static JsonObject get(String id) {
        JsonObject all = Story.root().has("side") ? Story.root().getAsJsonObject("side") : new JsonObject();
        return all.has(id) ? all.getAsJsonObject(id) : null;
    }

    /** Открыта ли миссия: нужен пройденный акт сюжета (unlock — номер акта, 0 — сразу). */
    static boolean unlocked(LifeData d, JsonObject m) {
        int need = Story.num(m, "unlock", 0);
        return d.storyDone || d.chapter >= need;
    }

    /** Список для телефона. */
    public static void sendList(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        NbtList list = new NbtList();
        JsonObject all = Story.root().has("side") ? Story.root().getAsJsonObject("side") : new JsonObject();
        int done = 0;
        for (var e : all.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            if (m.has("hidden")) continue; // просьбы героев по телефону (story/Calls) — не в списке «Заданий»
            boolean ok = d.flags.contains("side:" + e.getKey());
            if (ok) done++;
            NbtCompound c = new NbtCompound();
            c.putString("id", e.getKey());
            c.putString("title", Story.str(m, "title", e.getKey()));
            c.putString("cat", Story.str(m, "category", ""));
            c.putString("giver", Story.str(m, "giver", ""));
            c.putInt("reward", Story.num(m, "reward", 100));
            c.putBoolean("done", ok);
            c.putBoolean("open", unlocked(d, m));
            list.add(c);
        }
        n.put("list", list);
        n.putString("active", d.side);
        n.putInt("done", done);
        n.putInt("total", (int) all.entrySet().stream().filter(e -> !e.getValue().getAsJsonObject().has("hidden")).count());
        LifeNet.send(p, "side_list", n);
    }

    public static void start(ServerPlayerEntity p, String id) {
        LifeData d = Life.get(p);
        JsonObject m = get(id);
        if (m == null || !unlocked(d, m)) return;
        if (!d.side.isEmpty()) {
            p.sendMessage(Text.literal("Сначала закончи или отмени текущее задание").formatted(Formatting.RED), true);
            return;
        }
        GtaSteps.cleanup(p);
        d.side = id;
        d.sideStep = 0;
        d.stepCount = 0;
        NbtCompound t = new NbtCompound();
        t.putString("title", Story.str(m, "category", "Задание"));
        t.putString("subtitle", Story.str(m, "title", ""));
        LifeNet.send(p, "title", t);
        Story.reenter(p);
        Life.sync(p);
    }

    public static void cancel(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.side.isEmpty()) return;
        GtaSteps.cleanup(p);
        d.flags.remove("entered:" + Story.stepKey(d));
        d.side = "";
        d.sideStep = 0;
        p.sendMessage(Text.literal("Задание отменено").formatted(Formatting.GRAY), true);
        backToStory(p, d);
    }

    static void advance(ServerPlayerEntity p, LifeData d) {
        JsonObject m = get(d.side);
        d.flags.remove("entered:" + Story.stepKey(d));
        d.sideStep++;
        d.stepCount = 0;
        if (m == null || d.sideStep >= m.getAsJsonArray("steps").size()) {
            int reward = m == null ? 0 : Story.num(m, "reward", 100);
            LifeNet.send(p, "mission_done", Story.text("title", m == null ? "" : Story.str(m, "title", "")));
            p.playSoundToPlayer(ModContent.LEVEL_UP, SoundCategory.MASTER, 0.8f, 1f);
            if (reward > 0) Life.earn(p, reward, "задание выполнено");
            if (m != null && m.has("hidden")) Calls.onHelpDone(p, d.side, Story.str(m, "who", "")); // просьба героя по телефону — +дружба
            else d.flags.add("side:" + d.side);
            GtaSteps.cleanup(p);
            d.side = "";
            d.sideStep = 0;
            long done = d.flags.stream().filter(f -> f.startsWith("side:")).count();
            if (done == 25 || done == 50 || done == 100)
                p.sendMessage(Text.literal("🏆 Выполнено заданий: " + done + "!").formatted(Formatting.GOLD), false);
            backToStory(p, d);
            return;
        }
        Story.reenter(p);
    }

    /** Вернуться к сюжету: показать его текущий шаг без повторной выдачи наград. */
    private static void backToStory(ServerPlayerEntity p, LifeData d) {
        Life.sync(p);
        if (d.storyStarted && !d.storyDone) Story.reenter(p);
        else LifeNet.send(p, "objective", new NbtCompound());
    }
}
