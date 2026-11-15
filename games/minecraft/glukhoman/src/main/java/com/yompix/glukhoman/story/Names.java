package com.yompix.glukhoman.story;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Имена сорока (0.7, механика 9). 40 человек со строкой судьбы. Улики двух типов: <b>фамилия</b> — вырезана на шпалах и столбах
 * (видна только в видоискателе в ИК), <b>номер</b> — жестяная бирка (видна глазами). Раскладка — своя в каждом мире (по сиду),
 * на каждой улике — 4 имени. Имя засчитано, когда сняты оба типа — или когда его назвал Захар на плёнке (признание, 1–12).
 * Книжка «Список» у каждого — обновляется сама.
 */
public final class Names {
    private Names() {}

    public static final String[] ALL = {
            "Абрамов Пётр Ильич, 1902, путеец", "Безруких Тимофей, 1915, конюх", "Василенко Андрей Семёнович, 1908, плотник",
            "Гарипов Ринат, 1920, учётчик", "Данилюк Степан, 1899, бригадир", "Ершов Николай Павлович, 1911, учитель",
            "Жуков Матвей, 1924, ученик слесаря", "Зарецкий Лев Аронович, 1906, бухгалтер", "Иванов Иван Иванович, 1910, — (так в списке)",
            "Ковальчук Олесь, 1917, кузнец", "Ларионов Фёдор Ильич, 1903, машинист", "Мамедов Алескер, 1913, повар",
            "Никитин Глеб, 1922, —", "Осипов Захар Лукич, 1900, сторож", "Петренко Ганна? (так в списке), 1919",
            "Рябов Виктор, 1926, —", "Сидоренко Павел, 1912, тракторист", "Тихонов Аркадий, 1905, фельдшер",
            "Уразов Касым, 1918, —", "Федотов Михаил, 1909, писарь", "Харитонов Семён, 1916, сапожник",
            "Цыбуля Остап, 1901, стрелочник", "Чернов Алексей, 1923, —", "Шульц Генрих, 1907, инженер",
            "Щербина Иван, 1914, печник", "Юсупов Марат, 1921, —", "Яковлев Борис, 1904, счетовод",
            "Белов Кузьма, 1898, —", "Волков Тарас, 1925, —", "Грачёв Ефим, 1910, путеец", "Дорофеев Савва, 1912, обходчик",
            "Ефимов Лука, 1908, —", "Зуев Прохор, 1919, —", "Кротов Илья, 1903, —", "Лукин Демьян, 1915, —",
            "Мельник Остап, 1911, —", "Носов Аким, 1906, —", "Гринюк Семён Петрович, 1911, бригадир — письмо дочери не отправлено",
            "Кравец Иосиф, 1909, —", "Лещенко Михаил Андреевич, 1914, —"};

    /** Короткая фамилия для надписи (до запятой, первое слово + инициалы). */
    public static String surname(int n) {
        String s = ALL[n - 1].split(",")[0];
        String[] p = s.split(" ");
        StringBuilder b = new StringBuilder(p[0].toUpperCase());
        for (int i = 1; i < p.length && i < 3; i++) if (!p[i].isEmpty() && Character.isLetter(p[i].charAt(0))) b.append(i == 1 ? " " : "").append(p[i].charAt(0)).append('.');
        return b.toString();
    }

    public static final int SPOTS = 10, PER = 4;

    /** Раскладка: какие номера на какой улике. carv — ИК-надписи, tag — бирки. Хранится в StoryState (переживает перезагрузку). */
    public static int[][] layout(StoryState st, boolean carv) {
        String key = carv ? "nmap_c" : "nmap_t";
        if (!st.data.contains(key)) {
            long seed = st.data.contains("nseed") ? st.data.getLong("nseed") : new Random().nextLong();
            st.data.putLong("nseed", seed);
            for (String k : new String[]{"nmap_c", "nmap_t"}) {
                List<Integer> l = new ArrayList<>();
                for (int i = 1; i <= 40; i++) l.add(i);
                Collections.shuffle(l, new Random(seed ^ k.hashCode()));
                StringBuilder b = new StringBuilder();
                for (int x : l) b.append(x).append(',');
                st.data.putString(k, b.toString());
            }
            st.markDirty();
        }
        String[] p = st.data.getString(key).split(",");
        int[][] out = new int[SPOTS][PER];
        for (int i = 0; i < SPOTS * PER; i++) out[i / PER][i % PER] = Integer.parseInt(p[i]);
        return out;
    }

    public static boolean confirmed(StoryState st, int n) {
        return st.flag("nconf:" + n) || (st.flag("nc:" + n) && st.flag("nt:" + n));
    }

    public static int count(StoryState st) {
        int k = 0;
        for (int n = 1; n <= 40; n++) if (confirmed(st, n)) k++;
        return k;
    }

    /** Порог лучшей концовки: «Жутко» 24, «Страшно» 30, «Кошмар» 36. */
    public static int threshold() {
        return new int[]{24, 30, 36}[Math.max(0, Math.min(2, Story.difficulty()))];
    }

    /** Отметить улику; true — что-то новое подтвердилось. */
    public static boolean mark(StoryState st, String prefix, int[] nums) {
        boolean any = false;
        for (int n : nums) {
            boolean was = confirmed(st, n);
            st.set(prefix + n);
            if (!was && confirmed(st, n)) any = true;
        }
        if (any) refreshBooks();
        return any;
    }

    /** Книжка «Список»: подтверждённые — полностью, наполовину известные — «?». */
    public static ItemStack book(StoryState st) {
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder("СПИСОК\n" + count(st) + " из 40\n(нужно " + threshold() + ")\n\n");
        int lines = 4;
        for (int n = 1; n <= 40; n++) {
            String row;
            if (confirmed(st, n)) row = n + ". " + ALL[n - 1];
            else if (st.flag("nc:" + n)) row = n + "? " + surname(n) + " — номер?";
            else if (st.flag("nt:" + n)) row = n + ". ? — фамилия?";
            else continue;
            page.append(row).append("\n\n");
            if (++lines >= 6) {
                pages.add(RawFilteredPair.of(Text.literal(page.toString())));
                page = new StringBuilder();
                lines = 0;
            }
        }
        if (page.length() > 0) pages.add(RawFilteredPair.of(Text.literal(page.toString())));
        ItemStack b = new ItemStack(Items.WRITTEN_BOOK);
        b.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of("Список"), "сторож", 0, pages, true));
        return b;
    }

    public static void refreshBooks() {
        StoryState st = Story.st();
        if (st.chapter < 7) return;
        for (ServerPlayerEntity p : Story.players()) {
            var inv = p.getInventory();
            boolean had = false;
            for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(Items.WRITTEN_BOOK)) {
                inv.setStack(i, book(st));
                had = true;
            }
            if (!had) Story.give(p, book(st));
        }
    }
}
