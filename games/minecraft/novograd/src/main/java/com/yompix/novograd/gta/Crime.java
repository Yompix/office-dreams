package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Что считается преступлением и как на него реагирует город. */
public final class Crime {
    private Crime() {}

    /** Выстрел: прохожие разбегаются, полиция рядом — звезда (или две). */
    public static void gunshot(ServerWorld w, LivingEntity shooter, Vec3d at) {
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, shooter.getBoundingBox().expand(24), c -> c != shooter)) c.panic(at);
        Emergency.gunshot(w, shooter, at); // стреляли бандиты — патруль едет на место
        // стрельба: минимум одна звезда (две, если рядом полиция), но не прибавляет за каждый выстрел
        if (shooter instanceof ServerPlayerEntity p && !p.isCreative()) {
            // 0.9.5: выстрел слышит полиция до 48 блоков — розыск сразу; иначе свидетель звонит (gta/Witness)
            boolean cops = Witness.officerSees(w, p, 32, true) != null;
            if (!Disguise.unnoticed(p, cops)) Witness.crime(p, at, "стрельба на улице", 0, cops ? 2 : 1, true, null); // в маске прохожие не узнают
        }
    }

    /** Игрок ранил или убил жителя. */
    public static void hurt(ServerPlayerEntity p, CitizenEntity victim, boolean killed) {
        if (p.isCreative()) return;
        ServerWorld sw = p.getServerWorld();
        boolean cop = victim.getRole() == CitizenEntity.Role.POLICE;
        boolean defence = !cop && Witness.startedBy(victim, p); // 0.9.5: он напал первым — самооборона
        if (!defence) Witness.playerHit(victim, p);
        // 0.9.5: жильцы помнят — обидел (или на глазах у соседей) / заступился за жильца
        if (!defence && victim.getRole() == CitizenEntity.Role.CITIZEN) com.yompix.novograd.world.ResidentMind.wronged(p, victim, killed);
        else com.yompix.novograd.world.ResidentMind.helped(p, victim);
        if (defence) {
            // ничего: ответ на нападение не преступление
        } else if (cop) Wanted.add(p, killed ? 1 : 0, killed ? "убийство полицейского" : "нападение на полицейского", killed ? 4 : 3);
        else if (victim.getRole() == CitizenEntity.Role.GANG) {
            if (killed && Witness.officerSees(sw, p, 32, false) != null) Wanted.add(p, 0, "перестрелка", 2);
        } else if (!Disguise.unnoticed(p, Witness.officerSees(sw, p, 32, false) != null))
            Witness.crime(p, victim.getPos(), killed ? "убийство" : "нападение", killed ? 1 : 0, killed ? 2 : 1, false, killed ? null : victim);
        // свидетели разбегаются, друзья бандита — в бой
        for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, victim.getBoundingBox().expand(16), c -> c != victim)) {
            if (c.getRole() == CitizenEntity.Role.GANG && victim.getRole() == CitizenEntity.Role.GANG) {
                if (!BigHeists.calm(c)) c.attack(p); // охрана ограбления до тревоги не слышит выстрела с глушителем (BigHeists решает сам)
            }
            else if (c.getRole() == CitizenEntity.Role.POLICE) {
                if (!defence && (cop || c.canSee(p))) c.attack(p);
            }
            else c.panic(victim.getPos());
        }
    }

    /** Угон машины на глазах у полиции. */
    public static void carjack(ServerPlayerEntity p) {
        if (!p.isCreative()) Witness.crime(p, p.getPos(), "угон", 1, 1, false, null); // 0.9.5: полиция видела — сразу, прохожий — звонит
    }
}
