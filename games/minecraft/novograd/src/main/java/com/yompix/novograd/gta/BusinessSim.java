package com.yompix.novograd.gta;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 0.9.10: проверка модели бизнеса без игры ({@code ./gradlew bizTest}): 30 дней — одиночное кафе с персоналом,
 * два кафе-конкурента (премиум без персонала против обычного с персоналом), автосалон, клуб без запаса.
 */
public final class BusinessSim {
    private BusinessSim() {}

    static Business.Biz biz(int type, int price, int staff, int stock) {
        Business.Biz b = new Business.Biz();
        b.type = type;
        b.price = price;
        b.staff = new int[]{staff, Math.min(staff, 1), 0};
        b.stock = stock;
        b.owner = UUID.randomUUID();
        b.name = Business.TYPE_NAMES[type];
        return b;
    }

    public static void main(String[] a) {
        double[] demand = {1, 1, 1};
        Random r = new Random(1);
        Business.Biz solo = biz(0, 1, 2, 0);
        Business.Biz a1 = biz(0, 2, 0, 0), a2 = biz(0, 1, 2, 0);
        Business.Biz dealer = biz(1, 1, 2, 0), club = biz(2, 1, 2, 0);
        int spentSolo = 0, spentDealer = 0;
        int a1Guests = 0, a2Guests = 0, clubRepMin = 100;
        for (int day = 1; day <= 30; day++) {
            // хозяин докупает запас (кроме клуба — он забыл)
            int need = 90 - solo.stock;
            if (need > 0) { solo.stock += need; spentSolo += need * Business.UNIT_COST[0]; }
            for (Business.Biz x : new Business.Biz[]{a1, a2}) if (x.stock < 90) x.stock = 90;
            if (dealer.stock < 6) { spentDealer += (6 - dealer.stock) * Business.UNIT_COST[1]; dealer.stock = 6; }
            Business.simulate(List.of(solo), demand, r);
            Business.simulate(List.of(a1, a2), demand, r);
            Business.simulate(List.of(dealer, club), demand, r);
            a1Guests += a1.lastGuests;
            a2Guests += a2.lastGuests;
            clubRepMin = Math.min(clubRepMin, club.rep);
            if (day % 10 == 0)
                System.out.printf("[biz] день %2d: кафе касса $%d (рep %d) · конкуренты: премиум %d гостей/день рep %d, обычное %d рep %d · салон $%d · клуб рep %d%n",
                        day, solo.cash - spentSolo, solo.rep, a1.lastGuests, a1.rep, a2.lastGuests, a2.rep, dealer.cash - spentDealer, club.rep);
        }
        boolean pass = solo.cash - spentSolo > 0 && a2Guests > a1Guests && dealer.cash - spentDealer > 0 && clubRepMin < 30;
        System.out.println(pass ? "bizTest: PASS (кафе в плюсе, обычное с персоналом обходит премиум без персонала, салон в плюсе, без запаса репутация падает)" : "bizTest: FAIL");
        if (!pass) System.exit(1);
    }
}
