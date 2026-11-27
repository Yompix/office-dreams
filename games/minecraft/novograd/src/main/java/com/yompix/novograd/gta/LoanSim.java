package com.yompix.novograd.gta;

import com.yompix.novograd.life.LifeData;

import java.util.List;

/**
 * 0.9.8: проверка кредита без игры ({@code ./gradlew loanTest}): три игрока — платит вовремя, перестал платить
 * (пропуски → коллекторы → предупреждение → изъятие машины), гасит досрочно. Печатает дни, рейтинг, долг и переплату;
 * в конце — PASS/FAIL по правилам (рейтинг в 300–850, долг не уходит в минус, вовремя — закрыт, досрочно — дешевле,
 * изъятие — на 4-м пропуске).
 */
public final class LoanSim {
    private LoanSim() {}

    record Result(int paid, int score, boolean closed, int repoDay, int stageMax) {}

    static Result run(String name, int income, int stopAfterDay, int earlyDay, boolean verbose) {
        LifeData d = new LifeData();
        d.money = 500;
        d.earned = 0;
        int period = 3, amount = 3000, term = 8;
        double r = Loans.periodRate(0.03, Loans.SCORE0, period);
        Loans.open(d, amount, term, r, 1, period);
        d.money -= amount; // кредит потратил сразу (машина, ремонт квартиры)
        int spentOnLoan = 0, repoDay = -1, stageMax = 0;
        boolean ok = true;
        for (int day = 2; day <= 40; day++) {
            int before = d.money + d.cash;
            if (day <= stopAfterDay) {
                d.money += income;
                d.earned += income;
            }
            d.money -= 60; // жизнь: еда, счета
            if (earlyDay == day && Loans.active(d.loan)) {
                int sum = Loans.payoff(d.loan);
                if (Loans.take(d, sum)) {
                    d.loan.putInt("principal", 0);
                    d.loan.putInt("overdue", 0);
                    d.loan.putInt("left", 0);
                    spentOnLoan += sum;
                }
            }
            int pre = d.money + d.cash;
            List<Loans.Ev> ev = Loans.daily(d, day, period);
            int paidNow = Math.max(0, pre - (d.money + d.cash));
            for (Loans.Ev e : ev) {
                if (e == Loans.Ev.PAID || e == Loans.Ev.OVERDUE_PAID) spentOnLoan += paidNow;
                if (e == Loans.Ev.REPOSSESS && repoDay < 0) {
                    repoDay = day;
                    Loans.repossess(d, 6000); // машина за $6000 → продана за 70 %
                }
            }
            stageMax = Math.max(stageMax, d.loan.getInt("stage"));
            int sc = Loans.score(d.loan);
            if (sc < Loans.SCORE_MIN || sc > Loans.SCORE_MAX || d.loan.getInt("principal") < 0) ok = false;
            if (verbose && !ev.isEmpty())
                System.out.printf("  %-10s день %2d: %-34s рейтинг %3d, долг $%5d, стадия %d%n", name, day, ev, sc, Loans.payoff(d.loan), d.loan.getInt("stage"));
        }
        if (!ok) System.out.println("  " + name + ": НАРУШЕНИЕ пределов");
        return new Result(spentOnLoan, Loans.score(d.loan), !Loans.active(d.loan), repoDay, stageMax);
    }

    public static void main(String[] a) {
        System.out.println("Кредит $3000 на 8 платежей раз в 3 дня, ставка ЦБ 3 %/мес., рейтинг 620:");
        System.out.printf("  ставка за период %.3f%%, платёж $%d, лимит при доходе $300/день и рейтинге 620: $%d%n",
                Loans.periodRate(0.03, 620, 3) * 100, Loans.payment(3000, Loans.periodRate(0.03, 620, 3), 8), Loans.limit(300, 620, 0));
        Result onTime = run("вовремя", 300, 999, -1, true);
        Result stop = run("перестал", 300, 3, -1, true);
        Result early = run("досрочно", 900, 999, 6, true);
        System.out.printf("Итог: вовремя — заплатил $%d, рейтинг %d, закрыт %s; досрочно — $%d, рейтинг %d; перестал — изъятие на день %d, стадии до %d, рейтинг %d%n",
                onTime.paid(), onTime.score(), onTime.closed(), early.paid(), early.score(), stop.repoDay(), stop.stageMax(), stop.score());
        boolean pass = onTime.closed() && onTime.score() > 620 && early.paid() < onTime.paid() && early.closed() && stop.repoDay() > 0 && stop.stageMax() >= 3
                && stop.score() < 620 && Loans.limit(0, 400, 0) == 0 && Loans.limit(0, 600, 0) == 300;
        System.out.println(pass ? "loanTest: PASS" : "loanTest: FAIL");
        if (!pass) System.exit(1);
    }
}
