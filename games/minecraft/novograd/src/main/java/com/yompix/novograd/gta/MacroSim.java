package com.yompix.novograd.gta;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Random;

/**
 * 0.9.7: проверка рынка ({@link Macro}) — 90 игровых дней в трёх сценариях: остров без игроков, обычный игрок,
 * «бешеный» игрок (скупает бензин и жильё, зарабатывает в 5 раз больше обычного). Те же формулы, что в игре.
 * Запуск: {@code ./gradlew macroTest}; таблица — build/macro.csv (графики — tools/macro_charts.py).
 * Цель: всё в пределах (цены категорий 0,75–1,4, инфляция −0,5…5 %/мес., ставка 1–10 %, безработица 3–14 %),
 * индекс цен за 90 дней — не больше +15 %, разгона нет (последние 30 дней не растут быстрее первых).
 */
public final class MacroSim {
    private MacroSim() {}

    public static void main(String[] args) throws Exception {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 90;
        String[] who = {"без игроков", "обычный", "бешеный"};
        double[] spendK = {0, 1, 6}, earnK = {0, 1, 5};
        boolean ok = true;
        try (PrintWriter csv = new PrintWriter("build/macro.csv", "UTF-8")) {
            csv.println("scenario,day,cpi,infl,rate,unemp,wage,fuel,property,food,weapons,event");
            for (int k = 0; k < who.length; k++) {
                Macro.Model m = new Macro.Model();
                Random r = new Random(970 + k);
                double cpi = 1, base = 0.0006, cpi30 = 1, cpi60 = 1;
                double maxInfl = -1, minInfl = 1;
                int events = 0;
                for (int d = 1; d <= days; d++) {
                    long earned = Math.round((1500 + r.nextInt(800)) * earnK[k]);
                    long spent = 0;
                    for (int c = 0; c < Macro.CATS.length; c++) {
                        double extra = k == 2 && (c == 1 || c == 5) ? 3 : 1;
                        double s = Macro.BASE_DEMAND[c] * spendK[k] * extra * (0.7 + r.nextDouble() * 0.6);
                        m.spentToday[c] = s;
                        spent += Math.round(s * m.mul[c]);
                    }
                    m.bizIncomeToday = k == 0 ? 0 : 900 * earnK[k];
                    base = Money.dailyRate(base, earned, spent);
                    var e = m.step(base, Macro.season(d), r);
                    if (e != null) events++;
                    cpi *= 1 + Math.max(-0.0002, base - (m.rate - 0.03) * 0.5 / 30);
                    if (d == 30) cpi30 = cpi;
                    if (d == 60) cpi60 = cpi;
                    maxInfl = Math.max(maxInfl, m.inflation);
                    minInfl = Math.min(minInfl, m.inflation);
                    for (double x : m.mul) if (x < Macro.MUL_LO - 1e-9 || x > Macro.MUL_HI + 1e-9) ok = false;
                    if (m.rate < Macro.RATE_LO - 1e-9 || m.rate > Macro.RATE_HI + 1e-9 || m.unemployment < 0.03 - 1e-9 || m.unemployment > 0.14 + 1e-9) ok = false;
                    csv.printf(Locale.ROOT, "%s,%d,%.5f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s%n", who[k], d, cpi, m.inflation * 100, m.rate * 100,
                            m.unemployment * 100, m.wage, m.mul[1], m.mul[5], m.mul[0], m.mul[4], m.event);
                }
                double g1 = cpi30 - 1, g3 = cpi / cpi60 - 1;
                boolean runaway = g3 > g1 * 1.5 + 0.01;
                if (cpi > 1.15 || runaway || maxInfl > 0.05 + 1e-9 || minInfl < -0.005 - 1e-9) ok = false;
                System.out.printf(Locale.ROOT, "[macro] %s: индекс цен за %d дн. %.3f (дни 1–30 +%.1f%%, 61–90 +%.1f%%), инфляция %.2f…%.2f %%/мес., ставка %.2f%%, безработица %.1f%%, зарплаты ×%.3f, событий %d%s%n",
                        who[k], days, cpi, g1 * 100, g3 * 100, minInfl * 100, maxInfl * 100, m.rate * 100, m.unemployment * 100, m.wage, events, runaway ? " РАЗГОН" : "");
            }
        }
        System.out.println(ok ? "[macro] в пределах, без разгона" : "[macro] ВЫШЛО ЗА ПРЕДЕЛЫ");
        System.exit(ok ? 0 : 1);
    }
}
