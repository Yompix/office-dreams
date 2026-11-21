package com.yompix.novograd.gta;

import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.LifeAi;
import com.yompix.novograd.world.Residents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.9.5 «Наблюдение» (скрытая настройка, по умолчанию выключено; клиент: команда /наблюдение): над головой горожан —
 * имя, характер, чем занят сейчас и шкалы нужд. Чтобы было видно: у каждого своя жизнь.
 */
public final class Observe {
    private Observe() {}

    private static final Set<UUID> ON = new HashSet<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Observe::tick);
    }

    public static void set(ServerPlayerEntity p, boolean on) {
        if (on) ON.add(p.getUuid());
        else ON.remove(p.getUuid());
    }

    private static final Map<String, String> DOING = Map.of("bench", "отдыхает на скамейке", "jog", "пробежка", "leave", "уходит по делам",
            "queue", "в очереди за едой", "shop", "идёт в магазин", "shopping", "за покупками", "show", "смотрит выступление",
            "stroll", "гуляет", "taxi", "ловит такси");

    public static String act(CitizenEntity.Activity a) {
        return switch (a) {
            case TALK -> "болтает";
            case LAUGH -> "смеётся";
            case ARGUE -> "спорит";
            case PHONE, PHONEWALK -> "говорит по телефону";
            case SIT, SITCROSS -> "сидит";
            case EAT -> "ест";
            case SIT_EAT -> "обедает сидя";
            case DRINK -> "пьёт";
            case SMOKE -> "перекур";
            case TYPE -> "печатает";
            case SWEEP -> "метёт";
            case CARRY, BAGS -> "несёт груз";
            case HAMMER -> "чинит";
            case DANCE, DANCE2, DANCE3 -> "танцует";
            case JOG -> "бежит";
            case UMBRELLA -> "под зонтом";
            case DOGWALK -> "гуляет с собакой";
            case WAVE -> "машет";
            case POINT -> "показывает пальцем";
            case SLEEP -> "спит";
            case FIGHT -> "дерётся";
            case AIM -> "целится";
            case HAIL -> "ловит такси";
            case WATCH -> "смотрит на часы";
            case STRETCH -> "потягивается";
            case HANDSHAKE -> "здоровается";
            case CHEER, CLAP -> "радуется";
            case THINK -> "думает";
            case UPSET -> "расстроен";
            default -> "";
        };
    }

    private static NbtCompound entry(ServerWorld w, CitizenEntity c) {
        NbtCompound n = new NbtCompound();
        n.putInt("id", c.getId());
        String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
        String l1, l2 = "", needs = "";
        String act = act(c.getActivity());
        if (Witness.calling(c)) l2 = "звонит в полицию";
        if (c.getRole() == CitizenEntity.Role.POLICE) {
            l1 = "полиция";
            if (l2.isEmpty()) l2 = c.getFoe() instanceof ServerPlayerEntity ? (Pursuit.searchPoint(c, c.getFoe()) != null ? "ищет подозреваемого" : "погоня")
                    : c.getFoe() != null ? "задерживает" : PoliceLife.doing(c);
        } else if (c.getRole() == CitizenEntity.Role.GANG) {
            l1 = "банда";
            if (l2.isEmpty()) l2 = c.getFoe() != null ? "в драке" : "держит район";
        } else {
            String[] r = Residents.is(c) ? Residents.observe(w, c) : null;
            String[] b = r == null ? LifeAi.observe(c) : null;
            if (r != null) {
                name = r[0];
                l1 = "жилец · " + r[1];
                if (l2.isEmpty()) l2 = r[2];
                needs = r[3];
            } else if (b != null) {
                l1 = "прохожий · " + b[0];
                if (l2.isEmpty()) l2 = DOING.getOrDefault(b[1], b[1]);
                needs = b[2];
            } else l1 = c instanceof BodyguardEntity ? "охрана" : c.getRole() == CitizenEntity.Role.CITIZEN ? "прохожий" : "работник";
        }
        if (c.getVehicle() instanceof CarEntity car && l2.isEmpty()) l2 = car.onRoute() ? "за рулём" : "в машине";
        if (c.fleeing()) l2 = "убегает";
        if (!act.isEmpty() && !l2.contains(act)) l2 = l2.isEmpty() ? act : l2 + " · " + act;
        n.putString("name", name);
        n.putString("l1", l1);
        n.putString("l2", l2);
        n.putString("needs", needs);
        return n;
    }

    private static void tick(MinecraftServer s) {
        if (ON.isEmpty() || s.getTicks() % 10 != 6) return;
        for (UUID u : ON) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            ServerWorld w = p.getServerWorld();
            List<CitizenEntity> near = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(28, 12, 28), CitizenEntity::isAlive);
            near.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(p)));
            NbtList l = new NbtList();
            for (int i = 0; i < near.size() && i < 18; i++) l.add(entry(w, near.get(i)));
            NbtCompound n = new NbtCompound();
            n.put("list", l);
            LifeNet.send(p, "observe", n);
        }
    }
}
