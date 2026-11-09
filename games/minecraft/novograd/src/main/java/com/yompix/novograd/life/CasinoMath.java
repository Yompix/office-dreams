package com.yompix.novograd.life;

import java.util.Random;

/**
 * Математика казино без Minecraft: таблицы выплат, вероятности, оценка покерных рук.
 * Её же гоняет {@link #main} — симуляция 100 000 раундов каждой игры для проверки отдачи (RTP).
 * Сервер берёт исходы только отсюда; клиент использует отсюда лишь таблицы для подписей и «совет» в покере.
 */
public final class CasinoMath {
    private CasinoMath() {}

    // ================================================================ «Краш»
    /** Скорость роста: ×2 примерно за 8,7 с, ×10 — за 29 с, ×100 — за 58 с. */
    public static final double CRASH_RATE = 0.004;
    public static final double CRASH_CAP = 100;
    /** Варианты автовывода (×100); 0 — выключен. */
    public static final int[] CRASH_AUTO = {0, 150, 200, 300, 500, 1000};

    /** Множитель через t серверных тиков после старта (всегда с округлением вниз до сотых). */
    public static double crashMult(double ticks) {
        return Math.min(CRASH_CAP, Math.floor(Math.exp(Math.max(0, ticks) * CRASH_RATE) * 100) / 100);
    }

    /**
     * Точка краша. 3% раундов — мгновенный краш на ×1.00, остальные — «0,98/(1−u)» с округлением вниз,
     * итого P(точка ≥ x) = 0,95/x: на любой цели вывода игрок в среднем получает ~95% ставки. Потолок ×100.
     */
    public static double crashPoint(Random r) {
        if (r.nextDouble() < 0.03) return 1.0;
        double u = r.nextDouble();
        double p = Math.floor(0.98 / (1 - u) * 100) / 100;
        return Math.max(1.0, Math.min(CRASH_CAP, p));
    }

    // ================================================================ кости (2d6)
    /** Ставки на сумму двух кубиков: id, название, выплата ×100 (вместе со ставкой). */
    public static final String[] DICE_BETS = {"low", "seven", "high", "double", "edge"};
    public static final String[] DICE_NAMES = {"Меньше 7", "Ровно 7", "Больше 7", "Дубль", "2 или 12"};
    public static final int[] DICE_PAY = {230, 570, 230, 570, 1700};

    public static boolean diceWins(int bet, int a, int b) {
        int s = a + b;
        return switch (bet) {
            case 0 -> s < 7;
            case 1 -> s == 7;
            case 2 -> s > 7;
            case 3 -> a == b;
            case 4 -> s == 2 || s == 12;
            default -> false;
        };
    }

    // ================================================================ колесо фортуны (как Big Six / Dream Catcher)
    /**
     * 54 сектора. Числа 1, 2, 5, 10, 20, 40 платят N к 1 (плюс ставка). Сектора «×3» и «×7» — бонус:
     * колесо крутится ещё раз, и выигрыш следующего сектора умножается (бонусы перемножаются).
     */
    public static final int[] WHEEL = buildWheel();
    public static final int[] WHEEL_BETS = {1, 2, 5, 10, 20, 40};
    /** Значение сектора: число 1–40 или −3/−7 для бонусов-множителей. */
    private static int[] buildWheel() {
        // раскладка, похожая на настоящее колесо: крупные выигрыши разнесены
        String layout = "2 1 5 1 2 1 10 2 1 5 1 2 1 20 2 1 -3 1 2 1 5 2 1 10 1 2 1 5 2 1 40 1 2 1 5 2 1 10 1 2 1 5 2 1 -7 1 2 1 20 2 1 5 1 10";
        String[] t = layout.trim().split(" +");
        int[] w = new int[t.length];
        for (int i = 0; i < t.length; i++) w[i] = Integer.parseInt(t[i]);
        return w;
    }

    /** Крутит колесо: индексы секторов по порядку (все, кроме последнего, — бонусы). Не больше 6 бонусов подряд. */
    public static int[] wheelSpin(Random r) {
        int[] seq = new int[7];
        int n = 0;
        while (true) {
            int i = r.nextInt(WHEEL.length);
            if (WHEEL[i] < 0 && n >= 6) continue;
            seq[n++] = i;
            if (WHEEL[i] > 0) break;
        }
        return java.util.Arrays.copyOf(seq, n);
    }

    /** Выигрыш (вместе со ставкой) за ставку на число pick при выпадении seq. */
    public static long wheelWin(int bet, int pick, int[] seq) {
        int last = WHEEL[seq[seq.length - 1]];
        if (last != pick) return 0;
        long mult = 1;
        for (int i = 0; i < seq.length - 1; i++) mult *= -WHEEL[seq[i]];
        return (long) bet * pick * mult + bet;
    }

    // ================================================================ тараканьи бега
    public static final String[] RUNNERS = {"Шустрый", "Усач", "Молния", "Тапок", "Граф Таракан", "Бочонок", "Рыжик", "Пулемёт", "Кузя", "Дед Мороз"};

    /** Шансы на забег: 5 бегунов, вероятности из случайных «сил». Коэффициент = вниз(0,95/p) до десятых. */
    public static double[] raceProbs(Random r) {
        double[] s = new double[5];
        double sum = 0;
        for (int i = 0; i < 5; i++) {
            s[i] = 0.4 + Math.pow(r.nextDouble(), 1.6) * 2.6;
            sum += s[i];
        }
        for (int i = 0; i < 5; i++) s[i] /= sum;
        return s;
    }

    /** Коэффициент ×10 (выплата вместе со ставкой). */
    public static int raceOdds10(double p) {
        return Math.max(11, (int) Math.floor(0.95 / p * 10));
    }

    /** Порядок финиша: победитель по вероятностям, остальные — тоже по весам. */
    public static int[] raceOrder(Random r, double[] probs) {
        int n = probs.length;
        boolean[] used = new boolean[n];
        int[] order = new int[n];
        for (int k = 0; k < n; k++) {
            double left = 0;
            for (int i = 0; i < n; i++) if (!used[i]) left += probs[i];
            double x = r.nextDouble() * left;
            int pick = -1;
            for (int i = 0; i < n; i++) {
                if (used[i]) continue;
                pick = i;
                x -= probs[i];
                if (x < 0) break;
            }
            used[pick] = true;
            order[k] = pick;
        }
        return order;
    }

    // ================================================================ плинко (12 рядов, 13 лунок)
    public static final String[] PLINKO_RISK = {"Низкий", "Средний", "Высокий"};
    /** Множители ×10 для лунок 0–12 (симметрично). */
    public static final int[][] PLINKO = {
            {100, 30, 16, 14, 10, 10, 5, 10, 10, 14, 16, 30, 100},
            {300, 100, 40, 20, 10, 6, 3, 6, 10, 20, 40, 100, 300},
            {1400, 240, 80, 20, 6, 2, 2, 2, 6, 20, 80, 240, 1400}};
    public static final int PLINKO_ROWS = 12;

    /** Путь шарика: бит i = 1 — вправо на ряду i. Лунка = число единиц. */
    public static int plinkoPath(Random r) {
        return r.nextInt(1 << PLINKO_ROWS);
    }

    public static int plinkoSlot(int path) {
        return Integer.bitCount(path & ((1 << PLINKO_ROWS) - 1));
    }

    // ================================================================ мины (5×5)
    public static final int[] MINES_COUNTS = {1, 3, 5, 10};

    /** Множитель после k открытых безопасных клеток при m минах: 0,96 / P(k подряд без мины), вниз до сотых. */
    public static double minesMult(int mines, int k) {
        double p = 1;
        for (int i = 0; i < k; i++) p *= (25.0 - mines - i) / (25.0 - i);
        return k == 0 ? 1 : Math.floor(0.96 / p * 100) / 100;
    }

    /** Выплата в минах: до ближайшего доллара (иначе на малых ставках множители ×1,04 съедались бы округлением). */
    public static long minesPay(int bet, double mult) {
        return Math.round(bet * mult);
    }

    // ================================================================ видеопокер «Джекпот-покер» (Jacks or Better 8/5)
    public static final String[] POKER_HANDS = {"—", "Валеты и выше", "Две пары", "Тройка", "Стрит", "Флеш", "Фулл-хаус", "Каре", "Стрит-флеш", "Роял-флеш"};
    /** Выплата за руку (во сколько раз ставка, вместе со ставкой). 8/5 — отдача ≈97% при лучшей игре. */
    public static final int[] POKER_PAY = {0, 1, 2, 3, 4, 5, 8, 25, 50, 800};

    /** Карта 0–51: достоинство c % 13 (0 — туз, 10–12 — В, Д, К), масть c / 13. Ранг для покера: 2..14. */
    public static int pr(int c) {
        int r = c % 13;
        return r == 0 ? 14 : r + 1;
    }

    public static int pokerHand(int[] h) {
        int[] cnt = new int[15];
        boolean flush = true;
        for (int i = 0; i < 5; i++) {
            cnt[pr(h[i])]++;
            if (h[i] / 13 != h[0] / 13) flush = false;
        }
        int pairs = 0, three = 0, four = 0, distinct = 0, hiPair = 0;
        for (int v = 2; v <= 14; v++) {
            if (cnt[v] > 0) distinct++;
            if (cnt[v] == 2) {
                pairs++;
                if (v >= 11) hiPair++;
            }
            if (cnt[v] == 3) three++;
            if (cnt[v] == 4) four++;
        }
        boolean straight = false, royal = false;
        if (distinct == 5) {
            int lo = 99, hi = 0;
            for (int i = 0; i < 5; i++) {
                lo = Math.min(lo, pr(h[i]));
                hi = Math.max(hi, pr(h[i]));
            }
            if (hi - lo == 4) straight = true;
            if (cnt[14] == 1 && cnt[2] == 1 && cnt[3] == 1 && cnt[4] == 1 && cnt[5] == 1) straight = true;
            royal = straight && lo == 10;
        }
        if (straight && flush) return royal ? 9 : 8;
        if (four == 1) return 7;
        if (three == 1 && pairs == 1) return 6;
        if (flush) return 5;
        if (straight) return 4;
        if (three == 1) return 3;
        if (pairs == 2) return 2;
        if (hiPair == 1) return 1;
        return 0;
    }

    /**
     * Подсказка «что оставить» — упрощённая стратегия Jacks or Better (её же использует симуляция).
     * Возвращает маску: бит i — держать карту i.
     */
    public static int pokerAdvice(int[] h) {
        int hand = pokerHand(h);
        int royal4 = suitedOf(h, true, 4);
        if (hand >= 8 || hand == 7 || hand == 6) return 31;
        if (royal4 != 0) return royal4;
        if (hand == 5 || hand == 4) return 31;
        if (hand == 3) return rankCountMask(h, 3);
        if (hand == 2) return rankCountMask(h, 2);
        int sf4 = straightFlushDraw(h);
        if (hand == 1) return rankCountMask(h, 2);
        if (sf4 != 0) return sf4;
        int royal3 = suitedOf(h, true, 3);
        if (royal3 != 0) return royal3;
        int flush4 = flushDraw(h, 4);
        if (flush4 != 0) return flush4;
        int lowPair = rankCountMask(h, 2);
        if (lowPair != 0) return lowPair;
        int open = straightDraw(h);
        if (open != 0) return open;
        int suitedHigh = suitedOf(h, false, 2);
        if (suitedHigh != 0) return suitedHigh;
        // до двух старших карт (В и выше), самые младшие из них
        int mask = 0, n = 0;
        for (int v = 11; v <= 14 && n < 2; v++)
            for (int i = 0; i < 5 && n < 2; i++)
                if (pr(h[i]) == v && (mask & (1 << i)) == 0) {
                    mask |= 1 << i;
                    n++;
                }
        return mask;
    }

    /** Маска карт, чьё достоинство встречается ровно count раз. */
    private static int rankCountMask(int[] h, int count) {
        int[] cnt = new int[15];
        for (int c : h) cnt[pr(c)]++;
        int mask = 0;
        for (int i = 0; i < 5; i++) if (cnt[pr(h[i])] == count) mask |= 1 << i;
        return mask;
    }

    /** need карт одной масти из 10–Т (royal=true) или из В–Т (royal=false). */
    private static int suitedOf(int[] h, boolean royal, int need) {
        for (int s = 0; s < 4; s++) {
            int mask = 0, n = 0;
            for (int i = 0; i < 5; i++)
                if (h[i] / 13 == s && pr(h[i]) >= (royal ? 10 : 11)) {
                    mask |= 1 << i;
                    n++;
                }
            if (n >= need && n < 5) return n == need ? mask : 0;
        }
        return 0;
    }

    private static int flushDraw(int[] h, int need) {
        for (int s = 0; s < 4; s++) {
            int mask = 0, n = 0;
            for (int i = 0; i < 5; i++)
                if (h[i] / 13 == s) {
                    mask |= 1 << i;
                    n++;
                }
            if (n == need) return mask;
        }
        return 0;
    }

    /** 4 карты одной масти в окне из 5 достоинств. */
    private static int straightFlushDraw(int[] h) {
        int f = flushDraw(h, 4);
        if (f == 0) return 0;
        int lo = 99, hi = 0;
        for (int i = 0; i < 5; i++)
            if ((f & (1 << i)) != 0) {
                lo = Math.min(lo, pr(h[i]));
                hi = Math.max(hi, pr(h[i]));
            }
        return hi - lo <= 4 ? f : 0;
    }

    /** 4 разных подряд идущих достоинства (открытый стрит-дро). */
    private static int straightDraw(int[] h) {
        for (int lo = 2; lo <= 10; lo++) {
            int mask = 0;
            boolean[] got = new boolean[4];
            for (int i = 0; i < 5; i++) {
                int v = pr(h[i]);
                if (v >= lo && v < lo + 4 && !got[v - lo]) {
                    got[v - lo] = true;
                    mask |= 1 << i;
                }
            }
            if (Integer.bitCount(mask) == 4 && lo + 3 < 14) return mask;
        }
        return 0;
    }

    // ================================================================ симуляция (RTP)
    /** Запуск: java CasinoMath.java [раундов]. Печатает отдачу каждой игры. */
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        Random r = new Random(20261106L);
        int bet = 10;
        System.out.printf("Раундов на каждую игру: %d, ставка $%d%n", n, bet);
        // краш: разные цели автовывода
        for (double target : new double[]{1.01, 1.5, 2, 5, 10, 50}) {
            long paid = 0;
            for (int i = 0; i < n; i++) if (crashPoint(r) > target) paid += (long) Math.floor(bet * target);
            report("Краш, вывод ×" + target, paid, (long) n * bet);
        }
        int instant = 0, big = 0;
        for (int i = 0; i < n; i++) {
            double p = crashPoint(r);
            if (p <= 1.0) instant++;
            if (p >= 10) big++;
        }
        System.out.printf("  краш: на ×1.00 — %.1f%% раундов, ×10 и выше — %.1f%%%n", instant * 100.0 / n, big * 100.0 / n);
        // кости
        for (int k = 0; k < DICE_BETS.length; k++) {
            long paid = 0;
            for (int i = 0; i < n; i++) {
                int a = 1 + r.nextInt(6), b = 1 + r.nextInt(6);
                if (diceWins(k, a, b)) paid += bet * DICE_PAY[k] / 100;
            }
            report("Кости: " + DICE_NAMES[k], paid, (long) n * bet);
        }
        // колесо
        for (int pick : WHEEL_BETS) {
            long paid = 0;
            for (int i = 0; i < n * 4; i++) paid += wheelWin(bet, pick, wheelSpin(r));
            report("Колесо: ставка на " + pick + " (×4 раундов)", paid, (long) n * 4 * bet);
        }
        // бега
        long paid = 0;
        for (int i = 0; i < n; i++) {
            double[] pr = raceProbs(r);
            int pick = r.nextInt(5);
            if (raceOrder(r, pr)[0] == pick) paid += (long) bet * raceOdds10(pr[pick]) / 10;
        }
        report("Тараканьи бега (случайный бегун)", paid, (long) n * bet);
        // плинко
        for (int risk = 0; risk < 3; risk++) {
            paid = 0;
            for (int i = 0; i < n; i++) paid += (long) bet * PLINKO[risk][plinkoSlot(plinkoPath(r))] / 10;
            report("Плинко: риск " + PLINKO_RISK[risk], paid, (long) n * bet);
        }
        // мины: открываем k клеток и забираем
        for (int mines : MINES_COUNTS)
            for (int k : new int[]{1, 3, 5}) {
                if (k > 25 - mines) continue;
                paid = 0;
                for (int i = 0; i < n; i++) {
                    boolean[] grid = minesGrid(r, mines);
                    java.util.List<Integer> cells = new java.util.ArrayList<>();
                    for (int c = 0; c < 25; c++) cells.add(c);
                    java.util.Collections.shuffle(cells, r);
                    boolean ok = true;
                    for (int j = 0; j < k; j++) if (grid[cells.get(j)]) ok = false;
                    if (ok) paid += minesPay(bet, minesMult(mines, k));
                }
                report("Мины: " + mines + " мин, открыть " + k, paid, (long) n * bet);
            }
        // видеопокер с подсказкой
        paid = 0;
        int[] hands = new int[10];
        for (int i = 0; i < n; i++) {
            int[] deck = deck(r);
            int[] h = java.util.Arrays.copyOf(deck, 5);
            int hold = pokerAdvice(h);
            int next = 5;
            for (int j = 0; j < 5; j++) if ((hold & (1 << j)) == 0) h[j] = deck[next++];
            int res = pokerHand(h);
            hands[res]++;
            paid += (long) bet * POKER_PAY[res];
        }
        report("Видеопокер (подсказка)", paid, (long) n * bet);
        // блэкджек: упрощённая базовая стратегия, без сплита
        paid = 0;
        long staked = 0;
        for (int i = 0; i < n; i++) {
            long[] res = blackjackRound(r, bet);
            staked += res[0];
            paid += res[1];
        }
        report("Блэкджек (базовая стратегия, без сплита)", paid, staked);
        // монетка, автомат, рулетка — точный расчёт
        paid = 0;
        for (int i = 0; i < n; i++) if (r.nextInt(100) < 49) paid += bet * 2L;
        report("Монетка", paid, (long) n * bet);
        System.out.printf("  %-44s RTP %.2f%% (точно)%n", "Автомат «Фортуна»", slotsRtp() * 100);
        System.out.printf("  %-44s RTP %.2f%% (точно)%n", "Рулетка (любая ставка)", 36 / 37.0 * 100);
    }

    private static void report(String name, long paid, long staked) {
        System.out.printf("  %-44s RTP %.2f%%%n", name, paid * 100.0 / staked);
    }

    static boolean[] minesGrid(Random r, int mines) {
        boolean[] g = new boolean[25];
        for (int placed = 0; placed < mines; ) {
            int c = r.nextInt(25);
            if (!g[c]) {
                g[c] = true;
                placed++;
            }
        }
        return g;
    }

    static int[] deck(Random r) {
        int[] d = new int[52];
        for (int i = 0; i < 52; i++) d[i] = i;
        for (int i = 51; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = d[i];
            d[i] = d[j];
            d[j] = t;
        }
        return d;
    }

    private static int bjScore(java.util.List<Integer> cards) {
        int s = 0, aces = 0;
        for (int c : cards) {
            int v = c % 13;
            if (v == 0) {
                aces++;
                s += 11;
            } else s += Math.min(10, v + 1);
        }
        while (s > 21 && aces-- > 0) s -= 10;
        return s;
    }

    /** Те же правила, что в OnlineCasino: дилер добирает до 17, блэкджек 3:2, удвоение на двух картах. */
    private static long[] blackjackRound(Random r, int bet) {
        int[] d = deck(r);
        int k = 0;
        java.util.List<Integer> pl = new java.util.ArrayList<>(), dl = new java.util.ArrayList<>();
        pl.add(d[k++]);
        dl.add(d[k++]);
        pl.add(d[k++]);
        dl.add(d[k++]);
        int up = Math.min(10, dl.get(0) % 13 == 0 ? 11 : dl.get(0) % 13 + 1);
        long stake = bet;
        if (bjScore(pl) != 21) {
            int ps = bjScore(pl);
            if ((ps == 11 || (ps == 10 && up < 10)) && pl.size() == 2) {
                stake *= 2;
                pl.add(d[k++]);
            } else
                while (true) {
                    ps = bjScore(pl);
                    if (ps >= 17 || (ps >= 12 && up >= 2 && up <= 6) || (ps == 12 && up <= 3 && false)) break;
                    pl.add(d[k++]);
                }
        }
        int ps = bjScore(pl);
        if (ps <= 21) while (bjScore(dl) < 17) dl.add(d[k++]);
        int ds = bjScore(dl);
        boolean bj = ps == 21 && pl.size() == 2;
        long win;
        if (ps > 21) win = 0;
        else if (bj && !(ds == 21 && dl.size() == 2)) win = stake * 5 / 2;
        else if (ds > 21 || ps > ds) win = stake * 2;
        else if (ps == ds) win = stake;
        else win = 0;
        return new long[]{stake, win};
    }

    /** Отдача автомата из Casino (веса и выплаты продублированы оттуда). */
    private static double slotsRtp() {
        double[] w = {.30, .25, .20, .12, .08, .05};
        int[] three = {4, 8, 12, 20, 40, 100};
        double rtp = 0;
        for (int a = 0; a < 6; a++)
            for (int b = 0; b < 6; b++)
                for (int c = 0; c < 6; c++) {
                    double p = w[a] * w[b] * w[c];
                    double win;
                    if (a == b && b == c) win = three[a];
                    else {
                        int ch = (a == 0 ? 1 : 0) + (b == 0 ? 1 : 0) + (c == 0 ? 1 : 0);
                        win = ch == 2 ? 2 : ch == 1 ? 0.4 : 0;
                    }
                    rtp += p * win;
                }
        return rtp;
    }
}
