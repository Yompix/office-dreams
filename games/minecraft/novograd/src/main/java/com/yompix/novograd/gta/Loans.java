package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.8: кредит в банке (телефон → «Банк» → «Кредит»).
 * <ul>
 *   <li><b>Можно ли и сколько</b>: кредитный рейтинг 300–850 (новичок — 620) и доход за последние 7 дней + залог
 *       (купленное жильё, свои машины). Один кредит за раз.</li>
 *   <li><b>Ставка</b> — от ставки центробанка (gta/Macro) + надбавка за рейтинг; срок 4/8/12 платежей, платёж —
 *       раз в {@code loanPeriodDays} игровых дней (по умолчанию 3; novograd-server.json), аннуитет.</li>
 *   <li><b>Платёж</b> списывается утром с карты (не хватает — наличными). Вовремя — рейтинг растёт. Пропуск — пеня 5 %,
 *       рейтинг падает; долг пробуем списать каждое утро. 2 пропуска — коллекторы (звонки и визит двух людей),
 *       3 — последнее предупреждение, 4 — изъятие машины или дома (продажа за 70 %, остаток — на карту).</li>
 *   <li><b>Досрочно</b> — остаток тела долга без будущих процентов, рейтинг +15.</li>
 * </ul>
 * Всё на сервере, по UUID; клиент видит {@link LifeData#loan} и предложение (пакет «loan»).
 */
public final class Loans {
    private Loans() {}

    public static final int SCORE0 = 620, SCORE_MIN = 300, SCORE_MAX = 850;
    public static final int[] TERMS = {4, 8, 12};
    public static final int MIN = 100, MAX = 250_000;

    // ================================================================== чистая математика (и для loanTest)
    /** Надбавка к ставке ЦБ (в месяц) за рейтинг: 760+ — 0,5 %, 620 — 2,4 %, 300 — 6 %. */
    public static double premium(int score) {
        return Math.max(0.005, Math.min(0.06, 0.01 + (760 - score) * 0.0001));
    }

    /** Ставка за платёжный период: (ЦБ + надбавка) · период/30. */
    public static double periodRate(double cbMonthly, int score, int periodDays) {
        return (cbMonthly + premium(score)) * periodDays / 30.0;
    }

    /** Аннуитетный платёж. */
    public static int payment(int amount, double r, int n) {
        if (r <= 1e-9) return (int) Math.ceil(amount / (double) n);
        return (int) Math.ceil(amount * r / (1 - Math.pow(1 + r, -n)));
    }

    /** Лимит: доход за день (среднее за 7 дней) × 10 × множитель рейтинга + половина залога; новичку с рейтингом 560+ — хотя бы $300. */
    public static int limit(int incomePerDay, int score, long collateral) {
        double k = Math.max(0, Math.min(1.3, (score - 450) / 300.0));
        double lim = incomePerDay * 10.0 * k + collateral * 0.5 * Math.min(1, k + 0.2);
        if (score >= 560) lim = Math.max(lim, 300);
        if (score < 450) lim = 0;
        return (int) Math.min(MAX, Math.floor(lim / 50) * 50);
    }

    public static String band(int score) {
        return score >= 740 ? "отличный" : score >= 670 ? "хороший" : score >= 580 ? "средний" : "плохой";
    }

    public static int score(NbtCompound l) {
        return l.contains("score") ? l.getInt("score") : SCORE0;
    }

    public static boolean active(NbtCompound l) {
        return l.getInt("left") > 0 || l.getInt("principal") > 0 || l.getInt("overdue") > 0;
    }

    private static void addScore(NbtCompound l, int delta) {
        l.putInt("score", Math.max(SCORE_MIN, Math.min(SCORE_MAX, score(l) + delta)));
    }

    private static void hist(NbtCompound l, char c) {
        String h = l.getString("hist") + c;
        if (h.length() > 12) h = h.substring(h.length() - 12);
        l.putString("hist", h);
    }

    /** Средний доход в день за последние 7 дней (новичку — 5 % заработанного, не больше $300). */
    public static int income(LifeData d) {
        int[] e = d.loan.getIntArray("earn7");
        if (e.length == 0) return (int) Math.min(300, d.earned * 0.05);
        long s = 0;
        for (int x : e) s += Math.max(0, x);
        return (int) (s / e.length);
    }

    /** Взять кредит (проверки — у вызывающего). */
    public static void open(LifeData d, int amount, int term, double r, long day, int period) {
        NbtCompound l = d.loan;
        l.putInt("principal", amount);
        l.putInt("total", amount);
        l.putInt("left", term);
        l.putInt("term", term);
        l.putDouble("rate", r);
        l.putInt("pay", payment(amount, r, term));
        l.putLong("next", day + period);
        l.putLong("taken", day);
        l.putInt("missed", 0);
        l.putInt("stage", 0);
        l.putInt("overdue", 0);
        l.putInt("paidInterest", 0);
        l.putInt("count", l.getInt("count") + 1);
        if (!l.contains("score")) l.putInt("score", SCORE0);
        d.money += amount;
    }

    /** Списать сумму: карта, потом наличные. */
    static boolean take(LifeData d, int sum) {
        if (Math.max(0, d.money) + d.cash < sum) return false;
        int fromCard = Math.min(Math.max(0, d.money), sum);
        d.money -= fromCard;
        d.cash -= sum - fromCard;
        return true;
    }

    /** Один платёж по графику: проценты за период, остальное — в тело. */
    static void applyPayment(NbtCompound l) {
        int principal = l.getInt("principal");
        int interest = (int) Math.round(principal * l.getDouble("rate"));
        int pay = l.getInt("pay");
        int body = Math.max(0, Math.min(principal, pay - interest));
        if (l.getInt("left") <= 1) body = principal; // последний — всё, что осталось
        l.putInt("principal", principal - body);
        l.putInt("paidInterest", l.getInt("paidInterest") + interest);
        l.putInt("left", Math.max(0, l.getInt("left") - 1));
    }

    /** Что случилось за день (для сообщений). */
    public enum Ev { PAID, MISSED, OVERDUE_PAID, COLLECTORS, FINAL_WARNING, REPOSSESS, CLOSED }

    /** Утро: доход за день в историю, платёж по графику, долг по просрочке. Возвращает события. */
    public static List<Ev> daily(LifeData d, long day, int period) {
        NbtCompound l = d.loan;
        List<Ev> ev = new ArrayList<>();
        // доход за вчера → earn7
        int mark = l.contains("emark") ? l.getInt("emark") : d.earned;
        int[] e7 = l.getIntArray("earn7");
        int[] n7 = new int[Math.min(7, e7.length + 1)];
        for (int i = 0; i < n7.length - 1; i++) n7[i] = e7[e7.length - (n7.length - 1) + i];
        n7[n7.length - 1] = Math.max(0, d.earned - mark);
        if (l.contains("emark")) l.putIntArray("earn7", n7);
        l.putInt("emark", d.earned);
        if (!active(l)) return ev;
        // просрочка — пробуем каждое утро
        int overdue = l.getInt("overdue");
        if (overdue > 0 && take(d, overdue)) {
            l.putInt("overdue", 0);
            l.putInt("missed", 0);
            l.putInt("stage", 0);
            addScore(l, 4);
            ev.add(Ev.OVERDUE_PAID);
        }
        if (l.getInt("left") > 0 && day >= l.getLong("next")) {
            l.putLong("next", l.getLong("next") + period);
            int pay = Math.min(l.getInt("pay"), l.getInt("principal") + (int) Math.ceil(l.getInt("principal") * l.getDouble("rate")));
            if (take(d, pay)) {
                applyPayment(l);
                hist(l, '1');
                if (l.getInt("overdue") == 0) {
                    l.putInt("missed", 0);
                    l.putInt("stage", 0);
                }
                addScore(l, 6);
                ev.add(Ev.PAID);
            } else {
                applyPayment(l); // платёж «состоялся» в долг: сумма уходит в просрочку с пеней
                l.putInt("overdue", l.getInt("overdue") + (int) Math.ceil(pay * 1.05));
                hist(l, '0');
                int missed = l.getInt("missed") + 1;
                l.putInt("missed", missed);
                addScore(l, -30);
                ev.add(Ev.MISSED);
                if (missed == 2) {
                    l.putInt("stage", 2);
                    ev.add(Ev.COLLECTORS);
                } else if (missed == 3) {
                    l.putInt("stage", 3);
                    ev.add(Ev.FINAL_WARNING);
                } else if (missed >= 4) {
                    ev.add(Ev.REPOSSESS);
                }
            }
        }
        if (l.getInt("left") == 0 && l.getInt("principal") <= 0 && l.getInt("overdue") == 0) {
            l.putInt("principal", 0);
            l.putInt("stage", 0);
            addScore(l, 25);
            ev.add(Ev.CLOSED);
        }
        return ev;
    }

    /** Сколько нужно, чтобы закрыть всё сейчас: тело + просрочка. */
    public static int payoff(NbtCompound l) {
        return l.getInt("principal") + l.getInt("overdue");
    }

    /** Изъятие: из продажи (70 %) гасим долг, остаток — на карту. Возвращает сколько ушло в долг. */
    public static int repossess(LifeData d, int saleValue) {
        NbtCompound l = d.loan;
        int sale = (int) (saleValue * 0.7);
        int debt = payoff(l);
        int cover = Math.min(sale, debt);
        int overdue = l.getInt("overdue");
        int fromOver = Math.min(overdue, cover);
        l.putInt("overdue", overdue - fromOver);
        l.putInt("principal", Math.max(0, l.getInt("principal") - (cover - fromOver)));
        d.money += sale - cover;
        addScore(l, -120);
        l.putInt("missed", 0);
        if (payoff(l) <= 0) {
            l.putInt("left", 0);
            l.putInt("stage", 0);
        } else {
            l.putInt("stage", 2);
            int left = Math.max(1, l.getInt("left"));
            l.putInt("left", left);
            l.putInt("pay", payment(l.getInt("principal"), l.getDouble("rate"), left));
        }
        return cover;
    }

    // ================================================================== сервер
    public static int periodDays() {
        return Math.max(1, Math.min(30, com.yompix.novograd.life.Admin.cfg.loanPeriodDays));
    }

    public static double cbRate() {
        return Macro.model().rate;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Loans::tick);
    }

    /** Пакеты «loan_*». */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "loan_state" -> send(p);
            case "loan_take" -> takeLoan(p, d.getInt("amount"), d.getInt("term"));
            case "loan_pay" -> pay(p, d.getBoolean("all"));
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Залог: купленное жильё и свои машины. */
    static long collateral(ServerPlayerEntity p) {
        long v = 0;
        var hs = com.yompix.novograd.world.Housing.state(p.getServer()).owner(p.getUuid());
        for (var l : com.yompix.novograd.world.Housing.homes(p)) {
            var h = hs.homes.get(l.id);
            if (h != null && !h.rent) v += l.price;
        }
        var cars = MyCars.state(p.getServer()).cars.get(p.getUuid());
        if (cars != null) v += cars.size() * 4000L;
        return v;
    }

    public static void send(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        int score = score(d.loan);
        int inc = income(d);
        long coll = collateral(p);
        n.putInt("score", score);
        n.putString("band", band(score));
        n.putInt("income", inc);
        n.putLong("collateral", coll);
        n.putInt("limit", active(d.loan) ? 0 : limit(inc, score, coll));
        n.putDouble("rateMonth", (cbRate() + premium(score)) * 100);
        n.putDouble("periodRate", periodRate(cbRate(), score, periodDays()));
        n.putInt("period", periodDays());
        n.putLong("day", Life.day(p));
        n.putInt("payoff", payoff(d.loan));
        LifeNet.send(p, "loan", n);
    }

    private static void takeLoan(ServerPlayerEntity p, int amount, int term) {
        LifeData d = Life.get(p);
        if (active(d.loan)) return;
        boolean okTerm = false;
        for (int t : TERMS) okTerm |= t == term;
        int score = score(d.loan);
        int lim = limit(income(d), score, collateral(p));
        amount = amount / 50 * 50;
        if (!okTerm || amount < MIN || amount > lim) {
            send(p);
            return;
        }
        double r = periodRate(cbRate(), score, periodDays());
        open(d, amount, term, r, Life.day(p), periodDays());
        Novograd.LOG.info("[кредит] {}: ${} на {} платежей, {}%/период, рейтинг {}", p.getName().getString(), amount, term,
                String.format(java.util.Locale.ROOT, "%.2f", r * 100), score);
        Life.message(p, "Банк", "Кредит $" + amount + " на карте. Платёж $" + d.loan.getInt("pay") + " раз в " + periodDays() + " дн., первый — через " + periodDays() + " дн.");
        Life.sound(p, com.yompix.novograd.ModContent.CASH, 1f);
        Life.sync(p);
        send(p);
    }

    private static void pay(ServerPlayerEntity p, boolean all) {
        LifeData d = Life.get(p);
        NbtCompound l = d.loan;
        if (!active(l)) return;
        if (all) {
            int sum = payoff(l);
            if (!take(d, sum)) {
                Life.message(p, "Банк", "Для досрочного погашения нужно $" + sum + ".");
                return;
            }
            l.putInt("principal", 0);
            l.putInt("overdue", 0);
            l.putInt("left", 0);
            l.putInt("missed", 0);
            l.putInt("stage", 0);
            addScore(l, 15);
            hist(l, '1');
            Life.message(p, "Банк", "Кредит погашен досрочно. Рейтинг: " + score(l) + ".");
            dismiss(p);
        } else {
            int overdue = l.getInt("overdue");
            if (overdue > 0) {
                if (!take(d, overdue)) {
                    Life.message(p, "Банк", "Просрочка $" + overdue + " — не хватает денег.");
                    return;
                }
                l.putInt("overdue", 0);
                l.putInt("missed", 0);
                l.putInt("stage", 0);
                addScore(l, 4);
                Life.message(p, "Банк", "Просрочка погашена. Спасибо!");
                dismiss(p);
            } else if (l.getInt("left") > 0) {
                int sum = l.getInt("pay");
                if (!take(d, sum)) {
                    Life.message(p, "Банк", "Платёж $" + sum + " — не хватает денег.");
                    return;
                }
                applyPayment(l);
                hist(l, '1');
                addScore(l, 3);
                l.putLong("next", l.getLong("next") + periodDays()); // внесли заранее — следующий позже
            }
            if (l.getInt("left") == 0 && l.getInt("principal") <= 0) {
                addScore(l, 25);
                Life.message(p, "Банк", "Кредит закрыт. Рейтинг: " + score(l) + ".");
            }
        }
        Life.sound(p, com.yompix.novograd.ModContent.CASH, 1.2f);
        Life.sync(p);
        send(p);
    }

    /** Утро (из Money.daily): платёж, сообщения, изъятие. */
    public static void morning(ServerPlayerEntity p, LifeData d, long day) {
        boolean wasActive = active(d.loan);
        List<Ev> ev = daily(d, day, periodDays());
        if (!wasActive) return;
        NbtCompound l = d.loan;
        for (Ev e : ev) switch (e) {
            case PAID -> Life.message(p, "Банк", "Платёж по кредиту $" + l.getInt("pay") + " списан. Осталось: " + l.getInt("left") + ".");
            case OVERDUE_PAID -> {
                Life.message(p, "Банк", "Просрочка погашена.");
                dismiss(p);
            }
            case MISSED -> Life.message(p, "Банк", "Платёж не прошёл: не хватило денег. Долг $" + l.getInt("overdue") + " (с пеней 5 %) — спишем, как появятся.");
            case COLLECTORS -> Life.message(p, "«Тиски» — взыскание", "Здравствуйте. Ваш долг передан нам. Ждём оплату: телефон → Банк → Кредит.");
            case FINAL_WARNING -> Life.message(p, "Банк", "Последнее предупреждение: при следующем пропуске заберём машину или дом в счёт долга.");
            case REPOSSESS -> repossessAsset(p, d);
            case CLOSED -> Life.message(p, "Банк", "Кредит выплачен. Рейтинг: " + score(l) + " (" + band(score(l)) + ").");
        }
        if (!ev.isEmpty()) Novograd.LOG.info("[кредит] {} день {}: {} (рейтинг {}, долг {})", p.getName().getString(), day, ev, score(l), payoff(l));
    }

    /** Оценка машины для изъятия: по кузову. */
    static int carValue(NbtCompound rec) {
        String type = rec.getCompound("data").getString("nv_type");
        int v = rec.getCompound("data").getInt("Variant");
        return type.contains("scooter") ? 400 : 3000 + Math.floorMod(v * 1337, 9) * 1500;
    }

    private static void repossessAsset(ServerPlayerEntity p, LifeData d) {
        MinecraftServer s = p.getServer();
        // сначала машина (дорогая), потом дом
        var st = MyCars.state(s);
        var mine = st.cars.get(p.getUuid());
        UUID best = null;
        int bestV = 0;
        if (mine != null) for (var e : mine.entrySet()) {
            int v = carValue(e.getValue());
            if (v > bestV) {
                bestV = v;
                best = e.getKey();
            }
        }
        String what;
        int value;
        if (best != null) {
            NbtCompound rec = mine.remove(best);
            st.retired.add(best); // если загружена — исчезнет при следующем обходе (как у «подогнать»)
            for (ServerWorld w : s.getWorlds()) {
                var ent = w.getEntity(best);
                if (ent != null) {
                    ent.removeAllPassengers();
                    ent.discard();
                }
            }
            st.markDirty();
            what = "машину «" + rec.getString("name") + "»";
            value = bestV;
        } else {
            var homes = com.yompix.novograd.world.Housing.homes(p);
            var hs = com.yompix.novograd.world.Housing.state(s).owner(p.getUuid());
            com.yompix.novograd.world.Housing.Listing h = null;
            for (var l : homes) {
                var hold = hs.homes.get(l.id);
                if (hold != null && !hold.rent && (h == null || l.price > h.price)) h = l;
            }
            if (h == null) { // забрать нечего — долг остаётся, коллекторы продолжают
                Life.message(p, "«Тиски» — взыскание", "Имущества нет. Долг $" + payoff(d.loan) + " растёт пенями — платите.");
                d.loan.putInt("missed", 2);
                return;
            }
            com.yompix.novograd.world.Housing.repossess(s, p.getUuid(), h.id);
            what = "жильё «" + h.name + "»";
            value = h.price;
        }
        int cover = repossess(d, value);
        Activities.title(p, "ИЗЪЯТИЕ", what + " — в счёт долга");
        Life.message(p, "Банк", "Изъято " + what + ". Продано за $" + (int) (value * 0.7) + ", в долг ушло $" + cover + "."
                + (payoff(d.loan) > 0 ? " Остаток долга $" + payoff(d.loan) + "." : " Кредит закрыт."));
        Novograd.LOG.info("[кредит] изъятие у {}: {} за ${}", p.getName().getString(), what, value);
        if (payoff(d.loan) <= 0) dismiss(p);
    }

    // ================================================================== коллекторы
    private static final Map<UUID, List<UUID>> COLLECTORS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_VISIT = new HashMap<>();
    private static final String[] LINES = {"«Долг сам себя не заплатит. Телефон → Банк → Кредит»", "«Мы же по-хорошему пока. Платим?»",
            "«Красивая машина. Жалко будет, если банк заберёт»", "«Не бегай. Мы всё равно найдём»"};

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 7) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            LifeData d = Life.get(p);
            List<UUID> mine = COLLECTORS.get(p.getUuid());
            if (d.loan.getInt("stage") < 2) {
                if (mine != null) dismiss(p);
                continue;
            }
            if (mine == null) {
                if (now < NEXT_VISIT.getOrDefault(p.getUuid(), 0L) || p.getServerWorld() != w || p.hasVehicle() || !FunUtil.freeRoam(p)
                        || com.yompix.novograd.gta.Wanted.stars(p) > 0 || p.isCreative()) continue;
                NEXT_VISIT.put(p.getUuid(), now + 20 * 60 * 8); // раз в 8 минут, пока долг
                Vec3d at = FunUtil.curbAround(p, 14, 24);
                if (at == null) continue;
                List<UUID> l = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    CitizenEntity c = FunUtil.citizen(w, at.add(i * 1.2, 0, 0), CitizenEntity.Role.CITIZEN, i == 0 ? "Коллектор Гена" : "Коллектор Стас");
                    c.addCommandTag("nv_collector");
                    c.removeLater(20 * 150);
                    l.add(c.getUuid());
                }
                COLLECTORS.put(p.getUuid(), l);
                Life.message(p, "«Тиски» — взыскание", "Мы рядом. Поговорим?");
                continue;
            }
            int said = 0;
            for (UUID u : mine) {
                if (!(w.getEntity(u) instanceof CitizenEntity c) || !c.isAlive()) continue;
                double dist = c.squaredDistanceTo(p);
                if (dist > 60 * 60) continue;
                if (dist > 3.2 * 3.2) c.getNavigation().startMovingTo(p, dist > 12 * 12 ? 1.15 : 0.9);
                else {
                    c.getNavigation().stop();
                    c.getLookControl().lookAt(p);
                    if (said == 0 && now % 200 < 20) {
                        p.sendMessage(net.minecraft.text.Text.literal(c.getName().getString() + ": ").formatted(net.minecraft.util.Formatting.GRAY)
                                .append(net.minecraft.text.Text.literal(LINES[(int) ((now / 200) % LINES.length)] + "  · долг $" + payoff(d.loan))), false);
                        said++;
                    }
                }
            }
        }
    }

    /** Долг погашен — коллекторы уходят. */
    private static void dismiss(ServerPlayerEntity p) {
        List<UUID> l = COLLECTORS.remove(p.getUuid());
        if (l == null) return;
        ServerWorld w = p.getServer().getOverworld();
        for (UUID u : l) if (w.getEntity(u) instanceof CitizenEntity c) c.discard();
    }
}
