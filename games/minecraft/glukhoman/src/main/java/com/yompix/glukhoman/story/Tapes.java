package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.net.Net;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Плёнка — это память. Кассета E-180 = 6 сюжетов. Сюжет: что снимали (kind), контекст (ctx), название и кто был в кадре
 * (subj: кто → тики в кадре). Кассеты общие для бригады. Сданная в ящик кассета — список посадки; своя — «якорь»:
 * снятого на ней жителя поезд позвать не может. Новый сюжет на полную кассету стирает старый (по указателю).
 */
public final class Tapes {
    private Tapes() {}

    public static final int SLOTS = 6;
    /** Сколько тиков в кадре нужно, чтобы человек «зацепился» за плёнку. */
    public static final int ANCHOR = 40;

    public static NbtList list(StoryState st) {
        if (!st.data.contains("tapes")) st.data.put("tapes", new NbtList());
        return st.data.getList("tapes", NbtElement.COMPOUND_TYPE);
    }

    public static int newTape(StoryState st) {
        NbtList l = list(st);
        NbtCompound t = new NbtCompound();
        t.putInt("n", l.size() + 1);
        t.putBoolean("dep", false);
        t.put("slots", new NbtList());
        t.putInt("ptr", 0);
        l.add(t);
        st.markDirty();
        return l.size();
    }

    public static NbtList slots(NbtCompound tape) {
        return tape.getList("slots", NbtElement.COMPOUND_TYPE);
    }

    /** Кассета в камере: первая несданная со свободным местом, иначе последняя несданная. */
    public static NbtCompound current(StoryState st) {
        NbtCompound last = null;
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            if (t.getBoolean("dep")) continue;
            if (slots(t).size() < SLOTS) return t;
            last = t;
        }
        return last;
    }

    public static int held(StoryState st) {
        int n = 0;
        for (NbtElement e : list(st)) if (!((NbtCompound) e).getBoolean("dep")) n++;
        return n;
    }

    /** Записать сюжет. Тот же kind+ctx, что у последнего сюжета, дописывается в него. Возвращает текст для игрока (или null). */
    public static String commit(StoryState st, String kind, String ctx, String title, Map<String, Integer> subj) {
        NbtCompound tape = current(st);
        if (tape == null) return "Кассеты нет — камера смотрит, но не пишет.";
        NbtList sl = slots(tape);
        NbtCompound last = sl.isEmpty() ? null : sl.getCompound(sl.size() - 1);
        String msg = null;
        NbtCompound slot;
        if (last != null && last.getString("kind").equals(kind) && last.getString("ctx").equals(ctx)) slot = last;
        else {
            slot = new NbtCompound();
            slot.putString("kind", kind);
            slot.putString("ctx", ctx);
            slot.putString("title", title);
            slot.put("subj", new NbtCompound());
            if (sl.size() < SLOTS) sl.add(slot);
            else {
                int p = tape.getInt("ptr") % SLOTS;
                NbtCompound old = sl.getCompound(p);
                msg = "Записано поверх: «" + old.getString("title") + "». Этого больше нет.";
                sl.set(p, slot);
                tape.putInt("ptr", (p + 1) % SLOTS);
                Story.erased(old);
            }
        }
        NbtCompound s = slot.getCompound("subj");
        subj.forEach((k, v) -> s.putInt(k, s.getInt(k) + v));
        slot.put("subj", s);
        tape.put("slots", sl);
        st.markDirty();
        return msg;
    }

    /** Дописать строку-метку в последний сюжет (например, что ложное правило на плёнке молчит). */
    public static void tag(StoryState st, String kind, String ctx, String key, String val) {
        NbtCompound tape = current(st);
        if (tape == null) return;
        NbtList sl = slots(tape);
        for (int i = sl.size() - 1; i >= 0; i--) {
            NbtCompound s = sl.getCompound(i);
            if (s.getString("kind").equals(kind) && s.getString("ctx").equals(ctx)) {
                s.putString(key, val);
                st.markDirty();
                return;
            }
        }
    }

    public static boolean has(StoryState st, String kind, String ctx) {
        for (NbtElement e : list(st)) for (NbtElement s : slots((NbtCompound) e)) {
            NbtCompound c = (NbtCompound) s;
            if (c.getString("kind").equals(kind) && (ctx == null || c.getString("ctx").equals(ctx))) return true;
        }
        return false;
    }

    /** Кто «заякорен»: есть на несданной кассете хотя бы 2 секунды. */
    public static boolean anchored(StoryState st, String who) {
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            if (t.getBoolean("dep")) continue;
            for (NbtElement s : slots(t)) if (((NbtCompound) s).getCompound("subj").getInt(who) >= ANCHOR) return true;
        }
        return false;
    }

    /** Сколько кто «весит» на плёнке: на сданных (список) или на своих. */
    public static Map<String, Integer> weight(StoryState st, boolean deposited) {
        Map<String, Integer> out = new HashMap<>();
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            if (t.getBoolean("dep") != deposited) continue;
            for (NbtElement s : slots(t)) {
                NbtCompound sj = ((NbtCompound) s).getCompound("subj");
                for (String k : sj.getKeys()) out.merge(k, sj.getInt(k), Integer::sum);
            }
        }
        return out;
    }

    /** Сдать кассету в ящик: первая несданная. Номер или 0. */
    public static int deposit(StoryState st, ServerPlayerEntity p) {
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            if (t.getBoolean("dep")) continue;
            t.putBoolean("dep", true);
            st.markDirty();
            Story.take(p, GContent.CASSETTE, 1);
            return t.getInt("n");
        }
        return 0;
    }

    /** Обходчик вырвал плёнку: последний сюжет текущей кассеты пропал. */
    public static String ripLast(StoryState st) {
        NbtCompound tape = current(st);
        if (tape == null) return null;
        NbtList sl = slots(tape);
        if (sl.isEmpty()) return null;
        NbtCompound old = sl.getCompound(sl.size() - 1);
        sl.remove(sl.size() - 1);
        tape.put("slots", sl);
        Story.erased(old);
        st.markDirty();
        return old.getString("title");
    }

    public static void give(ServerPlayerEntity p) {
        Story.give(p, new ItemStack(GContent.CASSETTE));
    }

    // ----- экран кассет -----
    public static void open(ServerPlayerEntity p) {
        StoryState st = StoryState.get(p.getServerWorld());
        NbtCompound m = Net.msg("tapes");
        NbtList out = new NbtList();
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            NbtCompound o = new NbtCompound();
            o.putInt("n", t.getInt("n"));
            o.putBoolean("dep", t.getBoolean("dep"));
            o.putInt("ptr", t.getInt("ptr"));
            NbtList sl = new NbtList();
            for (NbtElement s : slots(t)) {
                NbtCompound c = (NbtCompound) s;
                NbtCompound q = new NbtCompound();
                q.putString("title", c.getString("title"));
                q.putString("kind", c.getString("kind"));
                q.putString("who", Story.subjText(c.getCompound("subj")));
                sl.add(q);
            }
            o.put("slots", sl);
            out.add(o);
        }
        m.put("list", out);
        Net.send(p, m);
    }

    /** Ответ экрана: play — пересмотреть, over — следующей записью стереть этот сюжет. */
    public static void action(ServerPlayerEntity p, int n, int slot, String act) {
        StoryState st = StoryState.get(p.getServerWorld());
        for (NbtElement e : list(st)) {
            NbtCompound t = (NbtCompound) e;
            if (t.getInt("n") != n) continue;
            NbtList sl = slots(t);
            if (slot < 0 || slot >= sl.size()) return;
            if (act.equals("over")) {
                t.putInt("ptr", slot);
                st.markDirty();
                Story.msg(p, "Следующая запись пойдёт поверх: «" + sl.getCompound(slot).getString("title") + "».");
            } else if (!t.getBoolean("dep")) Story.replay(p, sl.getCompound(slot));
            else Story.msg(p, "Эта кассета в ящике. Её больше нет у тебя.");
        }
    }
}
