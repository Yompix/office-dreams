package com.yompix.novograd.gta;

import java.util.Locale;
import java.util.Random;

/**
 * 0.9.6: проверка баланса денег — 30 игровых дней обычного игрока (миссии, подработка, потом бизнес и своё жильё)
 * теми же формулами счетов и инфляции, что в игре ({@link Money#bills}, {@link Money#dailyRate}).
 * Запуск: {@code ./gradlew economyTest}. Цель: деньги растут, но не взрываются; банкротства (долг) нет.
 */
public final class EconomySim {
    private EconomySim() {}

    public static void main(String[] args) {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        String[] who = {"обычный", "ленивый", "активный"};
        double[][] act = {{1.5, 4}, {0.6, 2}, {3.0, 6}}; // миссий в день, смен подработки
        boolean ok = true;
        for (int k = 0; k < who.length; k++) {
            Random r = new Random(96 + k);
            double cpi = 1, rate = 0.0006;
            long card = 50, cash = 0;
            int biz = 0, homes = 0, cars = 1;
            double owned = 0;
            boolean renting = false;
            long worth0 = 50, minCard = 0;
            StringBuilder curve = new StringBuilder();
            for (int d = 1; d <= days; d++) {
                long earned = 0, spent = 0;
                // доход (индексируется)
                double missions = act[k][0] + r.nextGaussian() * 0.4;
                for (int i = 0; i < Math.max(0, Math.round(missions)); i++) earned += Math.round((400 + r.nextInt(900)) * cpi);
                earned += Math.round(act[k][1] * (40 + r.nextInt(50)) * cpi);
                earned += Math.round(biz * 900 * cpi * 0.85);
                // траты на жизнь
                spent += Math.round((35 + r.nextInt(25)) * cpi); // еда
                spent += Math.round((25 + r.nextInt(40)) * cpi); // бензин
                if (d % 3 == 0) spent += Math.round((60 + r.nextInt(200)) * cpi); // одежда, цирюльня
                if (r.nextInt(4) == 0) spent += Math.round((50 + r.nextInt(250)) * cpi); // ремонт, штраф, больница
                if (d > 8 && r.nextInt(3) == 0) spent += Math.round((200 + r.nextInt(800)) * cpi); // тюнинг, казино
                if (renting) spent += Math.round(120 * cpi);
                int[] b = Money.bills(owned, homes, cars, biz);
                long bills = 0;
                for (int x : b) bills += x;
                spent += Math.round(bills * cpi);
                card += earned - spent;
                // крупные покупки: снять жильё, купить бизнес, купить квартиру
                if (!renting && homes == 0 && card > 1500) {
                    renting = true;
                    homes = 1;
                }
                if (biz < 3 && card > Math.round(30000 * cpi)) {
                    card -= Math.round(25000 * cpi);
                    biz++;
                }
                if (renting && card > Math.round(90000 * cpi)) {
                    card -= Math.round(80000 * cpi);
                    renting = false;
                    owned += 80000;
                }
                // проценты
                card += card >= 0 ? Math.min(500, (long) Math.floor(card * Money.SAVE_RATE)) : (long) Math.floor(card * Money.LOAN_RATE);
                minCard = Math.min(minCard, card);
                rate = Money.dailyRate(rate, earned, spent);
                cpi *= 1 + rate;
                long worth = card + cash + Math.round(biz * 25000 * cpi) + Math.round(owned * cpi);
                if (d % 5 == 0 || d == 1) curve.append(String.format(Locale.ROOT, " д%d:$%d", d, worth));
                if (d == days) {
                    double growthPerDay = (double) (worth - worth0) / days;
                    System.out.printf(Locale.ROOT, "[economy] %s: капитал%s · индекс цен %.3f (%.1f%%/мес.) · бизнесов %d · своё жильё %s · минимум на карте $%d · в среднем +$%.0f/день%n",
                            who[k], curve, cpi, rate * 30 * 100, biz, owned > 0 ? "да" : "нет", minCard, growthPerDay);
                    if (minCard < -2000 || cpi > 1.06 || growthPerDay < 0) ok = false;
                }
            }
        }
        System.out.println(ok ? "[economy] баланс в норме: без банкротства, инфляция 1–3 %/мес." : "[economy] БАЛАНС НЕ В НОРМЕ");
        System.exit(ok ? 0 : 1);
    }
}
