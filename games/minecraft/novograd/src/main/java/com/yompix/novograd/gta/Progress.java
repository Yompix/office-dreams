package com.yompix.novograd.gta;

import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 0.9: «Прогресс 100 %» (телефон → «Прогресс»): всё, чем можно заняться, с процентом у каждого раздела и общим
 * процентом (среднее разделов). Данные — уже существующие счётчики (флаги LifeData, достижения, коллекции, районы,
 * дружба); ничего нового в сохранение, кроме отметок наград ({@code prog50}, {@code prog100}).
 * Награды: 50 % — $50 000, 100 % — $250 000 и звание «Ключ от города».
 */
public final class Progress {
    private Progress() {}

    /** {название, сделано, всего}. */
    public static int[][] parts(ServerPlayerEntity p, java.util.List<String> names) {
        LifeData d = Life.get(p);
        java.util.List<int[]> out = new java.util.ArrayList<>();
        // сюжет: пройденные миссии
        int total = 0, done = 0;
        var chs = Story.root().getAsJsonArray("chapters");
        for (int c = 0; c < chs.size(); c++) {
            int ms = chs.get(c).getAsJsonObject().getAsJsonArray("missions").size();
            if (c == 5 && !d.flags.contains("end_cop") && !d.storyDone) continue; // акт VI — только на пути полиции
            for (int m = 0; m < ms; m++) {
                total++;
                if (d.storyDone || c < d.chapter || c == d.chapter && m < d.mission) done++;
            }
        }
        add(names, out, "Сюжет", done, total);
        int side = 0, sideAll = 0;
        if (Story.root().has("side")) for (var e : Story.root().getAsJsonObject("side").entrySet()) {
            sideAll++;
            if (d.flags.contains("side:" + e.getKey())) side++;
        }
        add(names, out, "Задания", side, Math.max(1, sideAll));
        int col = 0, colAll = 0;
        for (String c : Collectibles.CATS) {
            col += Collectibles.count(d, c);
            colAll += Collectibles.total(c);
        }
        add(names, out, "Коллекции", col, colAll);
        int perks = 0;
        for (String s : new String[]{"medic", "firefighter", "pizzaman"}) if (d.flags.contains("perk:" + s)) perks++;
        add(names, out, "Подработки", perks, 3);
        int biz = 0;
        for (int i = 0; i < Heists.businessCount(); i++) if (d.flags.contains("biz:" + i)) biz++;
        add(names, out, "Бизнесы", biz, Heists.businessCount());
        add(names, out, "Районы", Territories.ownedCount(p.getServer()), Territories.TURF.length);
        add(names, out, "Недвижимость", Math.min(5, com.yompix.novograd.world.Housing.homes(p).size()), 5);
        int fr = 0, frAll = 0;
        for (var e : Story.root().getAsJsonObject("characters").entrySet()) {
            if (e.getKey().equals("narrator")) continue;
            frAll += 6;
            fr += Math.min(6, com.yompix.novograd.story.Calls.friend(p, e.getKey()));
        }
        add(names, out, "Дружба", fr, Math.max(1, frAll));
        int ev = 0;
        for (String e : com.yompix.novograd.world.CityEvents.IDS) if (d.flags.contains("evt:" + e)) ev++;
        add(names, out, "События", ev, com.yompix.novograd.world.CityEvents.IDS.length);
        add(names, out, "Сеть", Grid.level(d), 5);
        add(names, out, "Достижения", d.achievements.size(), Achievements.ALL.size());
        return out.toArray(new int[0][]);
    }

    private static void add(java.util.List<String> names, java.util.List<int[]> out, String n, int done, int total) {
        names.add(n);
        out.add(new int[]{Math.min(done, total), total});
    }

    /** Общий процент 0…100 (среднее разделов). */
    public static int percent(int[][] parts) {
        double s = 0;
        for (int[] x : parts) s += x[1] == 0 ? 0 : x[0] / (double) x[1];
        return (int) Math.floor(s / parts.length * 100);
    }

    /** Значки активностей для карты (M): бизнесы, рейд, своё жильё, свои районы, событие города. */
    public static void mapActs(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        var w = p.getServerWorld();
        NbtList l = new NbtList();
        for (int i = 0; i < Heists.businessCount(); i++) {
            var at = Story.place(w, Heists.businessPlace(i));
            if (at == null) continue;
            boolean own = d.flags.contains("biz:" + i);
            l.add(act(Empire.raidAt(p, i) ? "raid" : own ? "biz_own" : "biz", Heists.businessName(i), at.x, at.z));
        }
        for (var h : com.yompix.novograd.world.Housing.homes(p)) l.add(act("home", h.name, h.inside.x, h.inside.z));
        var st = Territories.state(p.getServer());
        for (int i = 0; i < Territories.TURF.length; i++) {
            Object[] t = Territories.TURF[i];
            double x = ((int) t[1] + (int) t[3]) / 2.0, z = ((int) t[2] + (int) t[4]) / 2.0;
            l.add(act(st.ownerOf(i) == Territories.MINE ? "turf_own" : "turf", (String) t[0], x, z));
        }
        String ev = com.yompix.novograd.world.CityEvents.active();
        if (ev != null) {
            double[] c = com.yompix.novograd.world.CityEvents.center();
            l.add(act("event", com.yompix.novograd.world.CityEvents.NAMES[java.util.Arrays.asList(com.yompix.novograd.world.CityEvents.IDS).indexOf(ev)], c[0], c[1]));
        }
        NbtCompound n = new NbtCompound();
        n.put("acts", l);
        LifeNet.send(p, "map_acts", n);
    }

    private static NbtCompound act(String k, String name, double x, double z) {
        NbtCompound c = new NbtCompound();
        c.putString("k", k);
        c.putString("name", name);
        c.putDouble("x", x);
        c.putDouble("z", z);
        return c;
    }

    public static void send(ServerPlayerEntity p) {
        java.util.List<String> names = new java.util.ArrayList<>();
        int[][] parts = parts(p, names);
        int pct = percent(parts);
        LifeData d = Life.get(p);
        if (pct >= 50 && d.flags.add("prog50")) {
            Life.earn(p, 50_000, "прогресс 50%");
            Activities.title(p, "ПРОГРЕСС 50%", "+$50 000");
        }
        if (pct >= 100 && d.flags.add("prog100")) {
            Life.earn(p, 250_000, "прогресс 100%");
            Activities.title(p, "КЛЮЧ ОТ ГОРОДА", "100% · +$250 000");
        }
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (int i = 0; i < parts.length; i++) {
            NbtCompound c = new NbtCompound();
            c.putString("name", names.get(i));
            c.putInt("done", parts[i][0]);
            c.putInt("total", parts[i][1]);
            l.add(c);
        }
        n.put("parts", l);
        n.putInt("pct", pct);
        LifeNet.send(p, "progress_state", n);
    }
}
