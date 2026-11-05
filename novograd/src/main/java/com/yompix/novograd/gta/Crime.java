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
        // стрельба: минимум одна звезда (две, если рядом полиция), но не прибавляет за каждый выстрел
        if (shooter instanceof ServerPlayerEntity p && !p.isCreative()) Wanted.add(p, 0, "стрельба на улице", Wanted.policeNear(p, 40) ? 2 : 1);
    }

    /** Игрок ранил или убил жителя. */
    public static void hurt(ServerPlayerEntity p, CitizenEntity victim, boolean killed) {
        if (p.isCreative()) return;
        boolean cop = victim.getRole() == CitizenEntity.Role.POLICE;
        if (cop) Wanted.add(p, killed ? 1 : 0, killed ? "убийство полицейского" : "нападение на полицейского", killed ? 4 : 3);
        else if (victim.getRole() == CitizenEntity.Role.GANG) {
            if (killed && Wanted.policeNear(p, 30)) Wanted.add(p, 0, "перестрелка", 2);
        } else Wanted.add(p, killed ? 1 : 0, killed ? "убийство" : "нападение", killed ? 2 : 1);
        // свидетели разбегаются, друзья бандита — в бой
        for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, victim.getBoundingBox().expand(16), c -> c != victim)) {
            if (c.getRole() == CitizenEntity.Role.GANG && victim.getRole() == CitizenEntity.Role.GANG) c.attack(p);
            else if (c.getRole() == CitizenEntity.Role.POLICE) c.attack(p);
            else c.panic(victim.getPos());
        }
    }

    /** Угон машины на глазах у полиции. */
    public static void carjack(ServerPlayerEntity p) {
        if (!p.isCreative() && Wanted.policeNear(p, 32)) Wanted.add(p, 1, "угон", 1);
    }
}
