package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.vehicle.MotorVehicle;
import com.yompix.novograd.world.Island;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * «Режим Хаос»: открывается после финала (сам включается один раз) или вручную в телефоне → «Коллекции» → «Хаос».
 * Уличные события идут втрое чаще, банды устраивают засады, патрули узнают тебя по репортажам — погони чаще,
 * а «Новоград-24» говорит о беспорядках. Флаг "chaos" в LifeData.
 */
public final class Chaos {
    private Chaos() {}

    private static final Map<UUID, long[]> NEXT = new HashMap<>(); // {событие, погоня, новость}
    private static final Random R = new Random();

    private static final String[] HEADLINES = {
            "Хаос на улицах Новограда: полиция не успевает на вызовы",
            "Мэр собрал экстренное совещание: «Город захлестнула волна преступности»",
            "Страховые компании отказываются страховать машины в центре",
            "Банды делят город: стрельба слышна в нескольких районах",
            "Жители пригорода записываются в народные дружины",
            "Полиция Новограда переходит на усиленный режим дежурства",
            "Туристы покидают Ривьеру: «Мы приехали отдыхать, а не прятаться»",
            "Эксперты: «Такого беспорядка остров не видел никогда»",
    };

    public static boolean on(ServerPlayerEntity p) {
        return Life.get(p).flags.contains("chaos");
    }

    public static void set(ServerPlayerEntity p, boolean on) {
        LifeData d = Life.get(p);
        boolean was = d.flags.contains("chaos");
        if (on) d.flags.add("chaos");
        else d.flags.remove("chaos");
        if (was != on) {
            Activities.title(p, on ? "РЕЖИМ ХАОС" : "ХАОС ОТКЛЮЧЁН", on ? "Город сходит с ума: события, засады и погони — чаще" : "На улицах снова спокойнее");
            if (on) News.post(p.getServer(), HEADLINES[R.nextInt(HEADLINES.length)], "ЭКСТРЕННО");
            NEXT.remove(p.getUuid());
        }
        Life.sync(p);
        sendState(p);
        Collectibles.sendState(p, false);
    }

    public static void sendState(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", on(p));
        n.putBoolean("unlocked", Life.get(p).storyDone);
        Pulse.send(p, "chaos", n);
    }

    public static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 11) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            LifeData d = Life.get(p);
            // после финала хаос включается сам (один раз; выключить можно в телефоне)
            if (d.storyDone && d.flags.add("chaos_auto")) {
                set(p, true);
                Life.message(p, "Новоград-24", "Город после тебя уже не тот. Включён «Режим Хаос» — выключить: телефон → «Коллекции» → «Хаос».");
            }
            if (!d.flags.contains("chaos")) continue;
            long now = p.getServerWorld().getTime();
            long[] nx = NEXT.computeIfAbsent(p.getUuid(), u -> new long[]{now + 20 * 25, now + 20 * 120, now + 20 * 90});
            boolean free = FunUtil.freeRoam(p) && !FunUtil.busy(p) && !p.isCreative() && !FightClub.inside(p);
            // уличные события — каждые 30–55 секунд
            if (now >= nx[0]) {
                nx[0] = now + 20 * (30 + R.nextInt(26));
                if (free && Wanted.stars(p) == 0) {
                    StreetEvents.Kind k = pick(p, d);
                    if (k != null && !StreetEvents.start(p, k)) nx[0] = now + 20 * 8;
                }
            }
            // погоня: патруль узнал тебя по репортажу
            if (now >= nx[1]) {
                nx[1] = now + 20 * (140 + R.nextInt(120));
                if (free && Wanted.stars(p) == 0 && Economy.act(d) >= 1 && Island.urban(Island.zone(p.getBlockX(), p.getBlockZ()))) {
                    Wanted.add(p, 2, "патруль узнал тебя по репортажу «Новоград-24»", 2);
                    p.sendMessage(Text.literal("🚨 «Это он, из новостей!» — патруль сел на хвост").formatted(Formatting.RED), false);
                    if (R.nextBoolean()) News.post(p.getServer(), "Погоня в прямом эфире: полиция преследует подозреваемого из вчерашнего выпуска", "ЭКСТРЕННО");
                }
            }
            // новости о беспорядках
            if (now >= nx[2]) {
                nx[2] = now + 20 * (200 + R.nextInt(160));
                News.post(p.getServer(), HEADLINES[R.nextInt(HEADLINES.length)], "ЭКСТРЕННО");
                if (R.nextInt(3) == 0) Economy.shock(p.getServer(), R.nextBoolean() ? "RIVH" : "CITY", -0.03, "беспорядки пугают инвесторов");
            }
        }
    }

    private static StreetEvents.Kind pick(ServerPlayerEntity p, LifeData d) {
        Island.Zone zn = Island.zone(p.getBlockX(), p.getBlockZ());
        boolean urban = Island.urban(zn);
        boolean driving = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p && !(c instanceof MotorVehicle) && !c.isBike() && !c.isScooter();
        List<StreetEvents.Kind> bag = new ArrayList<>();
        if (urban) {
            Collections.addAll(bag, StreetEvents.Kind.SNATCH, StreetEvents.Kind.CRASH, StreetEvents.Kind.DRUNK);
            if (!driving) bag.add(StreetEvents.Kind.ROBBERY);
            if (driving) bag.add(StreetEvents.Kind.RACE);
        } else if (driving && zn != Island.Zone.SEA && zn != Island.Zone.CORE) {
            Collections.addAll(bag, StreetEvents.Kind.HITCH, StreetEvents.Kind.CRASH);
        }
        // засады банд — в хаосе даже без грязных денег
        if (Economy.act(d) >= 1 && (urban || driving)) Collections.addAll(bag, StreetEvents.Kind.AMBUSH, StreetEvents.Kind.AMBUSH);
        return bag.isEmpty() ? null : bag.get(R.nextInt(bag.size()));
    }
}
