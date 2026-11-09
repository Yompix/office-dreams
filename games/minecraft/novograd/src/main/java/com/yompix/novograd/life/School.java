package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Школа и вуз. Урок — 5 вопросов по предмету своего уровня, оценка 2–5.
 * Сдал 3 урока (в вузе 4) на тройку и выше — переходишь в следующий класс.
 */
public final class School {
    private School() {}

    public static final int LESSON_COOLDOWN = 900;       // ~45 секунд между уроками
    public static final long DAY_START = 2000, DAY_END = 12000; // 8:00–18:00

    record Question(String text, String[] answers, int correct) {}

    record Lesson(String subject, List<Question> questions, long started) {}

    private static final Map<UUID, Lesson> PENDING = new HashMap<>();

    /** Вопрос из банка: уровень от/до, предмет, текст, правильный ответ первым. */
    record Bank(int from, int to, String subject, String q, String... a) {}

    private static final List<Bank> BANK = List.of(
            new Bank(1, 4, "Русский язык", "Сколько букв в русском алфавите?", "33", "32", "30", "26"),
            new Bank(1, 4, "Русский язык", "Какое слово пишется с большой буквы?", "Москва", "кошка", "дом", "река"),
            new Bank(1, 4, "Окружающий мир", "Сколько дней в неделе?", "7", "5", "6", "10"),
            new Bank(1, 4, "Окружающий мир", "Какое время года наступает после зимы?", "Весна", "Осень", "Лето", "Зима"),
            new Bank(1, 4, "Окружающий мир", "Кто даёт молоко?", "Корова", "Курица", "Свинья", "Лошадь"),
            new Bank(1, 6, "Окружающий мир", "Сколько месяцев в году?", "12", "10", "11", "13"),
            new Bank(2, 6, "Русский язык", "Сколько гласных букв в русском языке?", "10", "6", "8", "12"),
            new Bank(2, 6, "Литература", "Кто написал «Сказку о рыбаке и рыбке»?", "Пушкин", "Толстой", "Чехов", "Гоголь"),
            new Bank(3, 7, "Английский", "Как по-английски «кошка»?", "cat", "dog", "cow", "car"),
            new Bank(3, 7, "Английский", "Как по-английски «школа»?", "school", "house", "shop", "street"),
            new Bank(3, 8, "Английский", "Как по-английски «красный»?", "red", "blue", "green", "black"),
            new Bank(4, 8, "География", "Самая длинная река в мире?", "Нил", "Волга", "Дунай", "Амур"),
            new Bank(4, 8, "География", "Столица Франции?", "Париж", "Берлин", "Рим", "Мадрид"),
            new Bank(4, 9, "География", "Самый большой океан?", "Тихий", "Атлантический", "Индийский", "Северный Ледовитый"),
            new Bank(5, 9, "История", "Кто первым полетел в космос?", "Юрий Гагарин", "Нил Армстронг", "Сергей Королёв", "Валентина Терешкова"),
            new Bank(5, 9, "История", "В каком году человек впервые полетел в космос?", "1961", "1945", "1969", "1957"),
            new Bank(5, 9, "Биология", "Какой орган качает кровь?", "Сердце", "Печень", "Лёгкие", "Желудок"),
            new Bank(5, 9, "Биология", "Что растения выделяют на свету?", "Кислород", "Азот", "Углекислый газ", "Водород"),
            new Bank(6, 10, "Русский язык", "Какая часть речи обозначает действие?", "Глагол", "Существительное", "Прилагательное", "Наречие"),
            new Bank(6, 11, "Литература", "Кто написал «Войну и мир»?", "Лев Толстой", "Фёдор Достоевский", "Иван Тургенев", "Антон Чехов"),
            new Bank(7, 11, "Физика", "В чём измеряется сила?", "В ньютонах", "В джоулях", "В ваттах", "В вольтах"),
            new Bank(7, 11, "Физика", "Скорость света примерно равна…", "300 000 км/с", "300 км/с", "3 000 км/с", "30 000 км/с"),
            new Bank(7, 11, "Химия", "Формула воды?", "H₂O", "CO₂", "O₂", "NaCl"),
            new Bank(8, 11, "Химия", "Какой газ нужен для горения?", "Кислород", "Гелий", "Азот", "Неон"),
            new Bank(8, 11, "История", "В каком году закончилась Вторая мировая война?", "1945", "1941", "1918", "1953"),
            new Bank(8, 11, "Информатика", "Сколько бит в одном байте?", "8", "2", "10", "16"),
            new Bank(8, 13, "Информатика", "Что из этого — язык программирования?", "Java", "HTML-тег", "Excel", "Windows"),
            new Bank(9, 11, "Физика", "Закон Ома: I = …", "U / R", "U · R", "R / U", "P / U"),
            new Bank(9, 11, "Биология", "Где хранится ДНК в клетке?", "В ядре", "В мембране", "В вакуоли", "В рибосоме"),
            new Bank(10, 13, "Обществознание", "Высший закон страны —", "Конституция", "Указ", "Устав", "Приказ"),
            new Bank(10, 14, "Английский", "Past Simple от «go»?", "went", "goed", "gone", "going"),
            new Bank(12, 16, "Экономика", "Что такое инфляция?", "Рост цен", "Падение цен", "Рост зарплат", "Налог"),
            new Bank(12, 16, "Экономика", "Как называется доход банка от кредита?", "Процент", "Акция", "Дивиденд", "Залог"),
            new Bank(12, 16, "Программирование", "Что выведет print(2 ** 3)?", "8", "6", "9", "23"),
            new Bank(12, 16, "Программирование", "Структура «первым пришёл — первым ушёл»?", "Очередь", "Стек", "Дерево", "Граф"),
            new Bank(13, 16, "Программирование", "Сложность бинарного поиска?", "O(log n)", "O(n)", "O(n²)", "O(1)"),
            new Bank(13, 16, "Право", "С какого возраста в РФ наступает полная дееспособность?", "18 лет", "14 лет", "16 лет", "21 год"),
            new Bank(14, 16, "Физика", "Единица энергии в СИ?", "Джоуль", "Ньютон", "Паскаль", "Кельвин")
    );

    public static boolean schoolTime(ServerPlayerEntity p) {
        long t = p.getServerWorld().getTimeOfDay() % 24000;
        return t >= DAY_START && t < DAY_END;
    }

    /** Начать урок (у доски или у учителя). */
    public static void start(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.edu > LifeData.UNI_END) {
            p.sendMessage(Text.literal("Ты уже закончил университет. Пора работать!").formatted(Formatting.GOLD), true);
            return;
        }
        boolean story = com.yompix.novograd.story.Story.forcedLesson(p);
        if (!story && !schoolTime(p)) {
            p.sendMessage(Text.literal("Уроки с 8:00 до 18:00. Ночью можно поспать — кровать перемотает время.").formatted(Formatting.YELLOW), false);
            return;
        }
        long now = p.getServerWorld().getTime();
        long wait = d.lastLesson + LESSON_COOLDOWN - now;
        if (wait > 0 && !story) {
            p.sendMessage(Text.literal("Перемена! Следующий урок через " + (wait / 20 + 1) + " с").formatted(Formatting.YELLOW), true);
            return;
        }
        Lesson lesson = create(d.edu, p.getRandom(), now);
        PENDING.put(p.getUuid(), lesson);
        NbtCompound n = new NbtCompound();
        n.putString("subject", lesson.subject());
        n.putString("level", d.eduName());
        n.putBoolean("hasBook", p.getInventory().count(ModContent.TEXTBOOK) > 0);
        NbtList qs = new NbtList();
        for (Question q : lesson.questions()) {
            NbtCompound qn = new NbtCompound();
            qn.putString("q", q.text());
            NbtList as = new NbtList();
            for (String a : q.answers()) as.add(NbtString.of(a));
            qn.put("a", as);
            if (n.getBoolean("hasBook")) qn.putInt("wrong", (q.correct() + 1 + p.getRandom().nextInt(3)) % 4);
            qs.add(qn);
        }
        n.put("questions", qs);
        LifeNet.send(p, "open_lesson", n);
    }

    static void answer(ServerPlayerEntity p, int[] answers, boolean usedHint) {
        Lesson lesson = PENDING.remove(p.getUuid());
        if (lesson == null) return;
        LifeData d = Life.get(p);
        long now = p.getServerWorld().getTime();
        if (now - lesson.started() < 40) return; // слишком быстро — не засчитываем
        int right = 0;
        for (int i = 0; i < lesson.questions().size() && i < answers.length; i++)
            if (answers[i] == lesson.questions().get(i).correct()) right++;
        int grade = right >= 5 ? 5 : right == 4 ? 4 : right == 3 ? 3 : 2;
        if (usedHint) takeOne(p, ModContent.TEXTBOOK);
        d.lastLesson = now;
        d.addGrade(grade);
        Achievements.give(p, "first_lesson");
        if (grade == 5) Achievements.give(p, "excellent");
        String who = d.adult() ? "Деканат" : "Классный руководитель";
        if (grade >= 3) {
            d.lessons++;
            int bonus = d.adult() ? (grade == 5 ? 15 : grade == 4 ? 8 : 0) : (grade == 5 ? 5 : grade == 4 ? 2 : 0);
            if (bonus > 0) Life.earn(p, bonus, d.adult() ? "стипендия" : "мама: молодец!");
            Life.sound(p, ModContent.ANSWER_RIGHT, 1f);
        } else {
            Life.sound(p, ModContent.ANSWER_WRONG, 1f);
            Life.message(p, who, "Двойка по предмету «" + lesson.subject() + "». Подготовься и попробуй ещё раз!");
        }
        if (d.lessons >= LifeData.lessonsNeeded(d.edu) && !com.yompix.novograd.story.Story.active(p)) promote(p, d);
        NbtCompound n = new NbtCompound();
        n.putInt("grade", grade);
        n.putInt("right", right);
        NbtList corr = new NbtList();
        for (Question q : lesson.questions()) corr.add(NbtString.of(q.answers()[q.correct()]));
        n.put("correct", corr);
        LifeNet.send(p, "lesson_result", n);
        Life.sync(p);
        com.yompix.novograd.story.Story.onLesson(p, grade);
    }

    private static void promote(ServerPlayerEntity p, LifeData d) {
        d.lessons = 0;
        d.edu++;
        Life.sound(p, ModContent.LEVEL_UP, 1f);
        if (d.edu == LifeData.SCHOOL_END + 1) {
            give(p, new ItemStack(ModContent.DIPLOMA));
            Life.earn(p, 100, "выпускной! подарок от родителей");
            Life.message(p, "Школа", "Поздравляем с окончанием школы! Аттестат у тебя в инвентаре. Теперь можно поступить в вуз (уроки у той же доски), работать таксистом и ходить в казино.");
        } else if (d.edu == LifeData.UNI_END + 1) {
            give(p, new ItemStack(ModContent.DIPLOMA));
            Life.earn(p, 300, "красный диплом");
            Life.message(p, "Университет", "Ты получил диплом о высшем образовании! Открыта работа программистом.");
        } else {
            Life.message(p, d.adult() ? "Университет" : "Школа", "Ты переведён: теперь " + d.eduName() + "!");
        }
        unlockJobsMessage(p, d);
    }

    private static void unlockJobsMessage(ServerPlayerEntity p, LifeData d) {
        for (Jobs.Job j : Jobs.Job.values())
            if (j.minEdu == d.edu) Life.message(p, "Центр занятости", "Новая вакансия доступна: " + j.title + ". Открой «Работа» в телефоне.");
    }

    static boolean takeOne(ServerPlayerEntity p, net.minecraft.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) {
                inv.getStack(i).decrement(1);
                return true;
            }
        }
        return false;
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    // ---------- генерация уроков ----------
    static Lesson create(int edu, Random r, long now) {
        List<String> subjects = new ArrayList<>();
        subjects.add("Математика");
        subjects.add("Математика");
        for (Bank b : BANK) if (edu >= b.from() && edu <= b.to() && !subjects.contains(b.subject())) subjects.add(b.subject());
        String subject = subjects.get(r.nextInt(subjects.size()));
        List<Question> qs = new ArrayList<>();
        if (subject.equals("Математика")) {
            for (int i = 0; i < 5; i++) qs.add(math(edu, r));
        } else {
            List<Bank> pool = new ArrayList<>();
            for (Bank b : BANK) if (b.subject().equals(subject) && edu >= b.from() && edu <= b.to()) pool.add(b);
            for (Bank b : BANK) if (!b.subject().equals(subject) && edu >= b.from() && edu <= b.to()) pool.add(b);
            Collections.shuffle(pool.subList(0, pool.size()), new java.util.Random(r.nextLong()));
            for (int i = 0; i < pool.size() && qs.size() < 4; i++) qs.add(shuffle(pool.get(i).q(), pool.get(i).a(), r));
            while (qs.size() < 5) qs.add(math(edu, r));
        }
        return new Lesson(subject, qs, now);
    }

    static Question math(int edu, Random r) {
        String text;
        int ans;
        if (edu <= 2) {
            int a = 1 + r.nextInt(10), b = 1 + r.nextInt(10);
            if (r.nextBoolean()) { text = a + " + " + b + " = ?"; ans = a + b; }
            else { int x = Math.max(a, b), y = Math.min(a, b); text = x + " − " + y + " = ?"; ans = x - y; }
        } else if (edu <= 4) {
            int a = 2 + r.nextInt(8), b = 2 + r.nextInt(8);
            if (r.nextBoolean()) { text = a + " × " + b + " = ?"; ans = a * b; }
            else { text = (a * b) + " : " + a + " = ?"; ans = b; }
        } else if (edu <= 6) {
            int a = 12 + r.nextInt(80), b = 11 + r.nextInt(40);
            switch (r.nextInt(3)) {
                case 0 -> { text = a + " + " + b + " = ?"; ans = a + b; }
                case 1 -> { text = (a + b) + " − " + b + " = ?"; ans = a; }
                default -> { int p = (1 + r.nextInt(4)) * 25; int n = (1 + r.nextInt(8)) * 4; text = p + "% от " + n + " = ?"; ans = p * n / 100; }
            }
        } else if (edu <= 9) {
            int x = 1 + r.nextInt(12), a = 2 + r.nextInt(7), b = r.nextInt(20);
            switch (r.nextInt(3)) {
                case 0 -> { text = a + "x + " + b + " = " + (a * x + b) + ".  x = ?"; ans = x; }
                case 1 -> { text = x + "² = ?"; ans = x * x; }
                default -> { int n = (2 + r.nextInt(9)) * 10, p = 10 * (1 + r.nextInt(5)); text = "Цена $" + n + ", скидка " + p + "%. Сколько платить?"; ans = n - n * p / 100; }
            }
        } else if (edu <= 11) {
            switch (r.nextInt(3)) {
                case 0 -> { int b = 2 + r.nextInt(2), e = 2 + r.nextInt(4); text = b + "^" + e + " = ?"; ans = (int) Math.pow(b, e); }
                case 1 -> { int v = 2 + r.nextInt(14); text = "√" + (v * v) + " = ?"; ans = v; }
                default -> { int e = 1 + r.nextInt(7); text = "log₂ " + (1 << e) + " = ?"; ans = e; }
            }
        } else {
            switch (r.nextInt(3)) {
                case 0 -> { int n = 2 + r.nextInt(3), x = 1 + r.nextInt(4); text = "f(x) = x^" + n + ". f′(" + x + ") = ?"; ans = n * (int) Math.pow(x, n - 1); }
                case 1 -> { int v = 3 + r.nextInt(28); text = "Двоичное " + Integer.toBinaryString(v) + " = ? (в десятичной)"; ans = v; }
                default -> { int n = 5 + r.nextInt(16); text = "1 + 2 + … + " + n + " = ?"; ans = n * (n + 1) / 2; }
            }
        }
        List<Integer> opts = new ArrayList<>(List.of(ans));
        while (opts.size() < 4) {
            int w = ans + (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(Math.max(3, Math.abs(ans) / 4 + 2)));
            if (w >= 0 && !opts.contains(w)) opts.add(w);
        }
        String[] a = new String[4];
        for (int i = 0; i < 4; i++) a[i] = String.valueOf(opts.get(i));
        return shuffle(text, a, r);
    }

    static Question shuffle(String text, String[] answers, Random r) {
        List<Integer> idx = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(idx, new java.util.Random(r.nextLong()));
        String[] out = new String[4];
        int correct = 0;
        for (int i = 0; i < 4; i++) {
            out[i] = answers[idx.get(i)];
            if (idx.get(i) == 0) correct = i;
        }
        return new Question(text, out, correct);
    }
}
