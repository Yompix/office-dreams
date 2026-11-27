package com.yompix.novograd.gta;

import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * 0.9.9: «Фотограм» — соцсеть города. Горожане выкладывают ролики твоих погонь, гонок, концертов и громких дел,
 * ты — свои фото (телефон → «Камера»). Лайки, подписчики и слава ({@code city.fame}): известного игрока узнают,
 * о нём рассказывают в «Новоград-24». Лента общая для всех игроков сервера (последние 80 постов, в мире).
 */
public final class Social {
    private Social() {}

    private static final String[] NICKS = {"@lena_v_gorode", "@stas.drive", "@kotik_mira", "@ne_spit_novograd", "@dron_vasilich", "@rivierka", "@port_life",
            "@tetya_valya", "@neon_boy", "@mama_trolleybus", "@gonshchik_99", "@foto_dima"};

    // ------------------------------------------------------------------ лента в мире
    static final class State extends PersistentState {
        final ArrayDeque<NbtCompound> feed = new ArrayDeque<>();

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (NbtCompound c : feed) l.add(c);
            n.put("feed", l);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (var e : n.getList("feed", 10)) s.feed.addLast((NbtCompound) e);
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_social");
    }

    public static void init() {
        if (!Boolean.getBoolean("novograd.no099")) ServerTickEvents.END_SERVER_TICK.register(Social::tick); // замер «до/после» (MSPT)
    }

    /** Пост: автор, текст, про кого (ник игрока или ""), лайки; слава героя растёт. */
    public static void post(MinecraftServer s, String author, String text, ServerPlayerEntity about, int likes, int fame) {
        State st = state(s);
        NbtCompound c = new NbtCompound();
        c.putString("a", author);
        c.putString("t", text);
        c.putString("p", about == null ? "" : about.getName().getString());
        c.putInt("l", likes);
        c.putLong("d", s.getOverworld().getTimeOfDay() / 24000L + 1);
        st.feed.addFirst(c);
        while (st.feed.size() > 80) st.feed.removeLast();
        st.markDirty();
        if (about != null && fame > 0) {
            LifeData d = Life.get(about);
            int f = d.city.getInt("fame") + fame;
            d.city.putInt("fame", f);
            d.city.putInt("followers", followers(f, d.city.getInt("posts")));
            if (f / 100 > (f - fame) / 100) Life.message(about, "Фотограм", "Тебя смотрят! Подписчиков: " + d.city.getInt("followers"));
            Life.sync(about);
            if (fame >= 25) News.post(s, about.getName().getString() + ": " + text, "ГОРОД");
        }
    }

    /** Подписчики из славы и своих постов (плавно, без взрывного роста). */
    public static int followers(int fame, int posts) {
        return (int) (Math.pow(Math.max(0, fame), 1.15) * 3 + posts * 7);
    }

    /** Слава → кто ты для города (подпись в приложении). */
    public static String rank(int fame) {
        return fame >= 2000 ? "звезда города" : fame >= 800 ? "знаменитость" : fame >= 300 ? "узнают на улице" : fame >= 80 ? "местный блогер" : "новичок";
    }

    // ------------------------------------------------------------------ события
    /** Громкое дело (большой титр): горожане снимают и выкладывают. */
    public static void onTitle(ServerPlayerEntity p, String title, String sub) {
        if (p.getServer() == null || title == null || title.isEmpty()) return;
        String t = title.toLowerCase();
        Random r = new Random(p.getServerWorld().getTime() ^ title.hashCode());
        String who = p.getName().getString();
        String text = null;
        int fame = 0;
        if (t.contains("ограблен")) { text = "кто-то в маске взял «" + sub + "»… говорят, это " + who + " 😳 #ограбление"; fame = 40; }
        else if (t.contains("победа") || t.contains("финиш") || t.contains("рекорд")) { text = who + " снова первый! " + title.toLowerCase() + " 🔥"; fame = 15; }
        else if (t.contains("ушёл") || t.contains("оторвал")) { text = "видели, как " + who + " ушёл от погони?! 🚓💨 #погоня"; fame = 20; }
        else if (t.contains("арест")) { text = "ну всё, " + who + " поймали. Сидеть ему теперь… #новоград"; fame = 5; }
        if (text != null) post(p.getServer(), NICKS[r.nextInt(NICKS.length)], text, p, 20 + r.nextInt(80) + fame * 3, fame);
    }

    /** Погоня: на 3★+ кто-то снимает ролик с тротуара (не чаще раза в 2 минуты). */
    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 200 != 51) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            int stars = Wanted.stars(p);
            LifeData d = Life.get(p);
            long now = s.getOverworld().getTime();
            if (stars >= 3 && now - d.city.getLong("clipAt") > 20 * 120) {
                d.city.putLong("clipAt", now);
                Random r = new Random(now);
                String[] lines = {"снимаю из окна: " + p.getName().getString() + " и " + stars + " машины полиции 😱 #погоня",
                        "это кино?! нет, это " + p.getName().getString() + " на " + (p.hasVehicle() ? "машине" : "своих двоих") + " и ★".repeat(stars),
                        "весь квартал в мигалках, а он уходит… " + p.getName().getString() + " легенда"};
                post(s, NICKS[r.nextInt(NICKS.length)], lines[r.nextInt(lines.length)], p, 50 + stars * 40 + r.nextInt(60), stars * 8);
            }
        }
    }

    /** Своё фото (телефон → «Камера»): лайки от места, времени и славы. */
    public static void onPhoto(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        long now = p.getServerWorld().getTime();
        if (now - d.city.getLong("photoAt") < 20 * 30) return; // не чаще раза в 30 с
        d.city.putLong("photoAt", now);
        d.city.putInt("posts", d.city.getInt("posts") + 1);
        long tod = p.getServerWorld().getTimeOfDay() % 24000;
        boolean golden = tod > 11500 && tod < 13500 || tod < 1000;
        String zone = com.yompix.novograd.world.Island.zone(p.getBlockX(), p.getBlockZ()).name();
        String[] caps = {"вечер в Новограде", "мой район", "вид что надо", "просто гуляю", "#новоград #город"};
        int likes = 5 + d.city.getInt("followers") / 12 + (golden ? 25 : 0) + p.getRandom().nextInt(15);
        post(p.getServer(), "@" + p.getName().getString(), (golden ? "золотой час · " : "") + caps[p.getRandom().nextInt(caps.length)] + " · " + zone.toLowerCase(),
                p, likes, golden ? 3 : 1);
        Life.message(p, "Фотограм", "Фото опубликовано: " + likes + " ♥");
    }

    // ------------------------------------------------------------------ телефон
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        if (!type.equals("social_feed")) return false;
        send(p);
        return true;
    }

    public static void send(ServerPlayerEntity p) {
        State st = state(p.getServer());
        LifeData d = Life.get(p);
        NbtCompound out = new NbtCompound();
        NbtList l = new NbtList();
        int i = 0;
        for (NbtCompound c : st.feed) {
            if (i++ >= 40) break;
            l.add(c.copy());
        }
        out.put("feed", l);
        out.putInt("fame", d.city.getInt("fame"));
        out.putInt("followers", d.city.getInt("followers"));
        out.putInt("posts", d.city.getInt("posts"));
        out.putString("rank", rank(d.city.getInt("fame")));
        LifeNet.send(p, "social", out);
    }
}
