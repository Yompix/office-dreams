package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.9.5: полиция узнаёт о преступлении только от свидетелей — «без мгновенной полиции».
 * <ul>
 *   <li><b>Полицейский видит</b> (в конусе взгляда, без стен, до 32 блоков; выстрел слышно до 48) — звезда сразу.</li>
 *   <li><b>Прохожий видит</b> — достаёт телефон и звонит 10–25 с. Напугал (навёл ствол вплотную), ударил или вырубил его
 *       до конца звонка — звонка нет. Дозвонился — звёзды «по сообщению»: полиция едет туда, где было, а не к тебе,
 *       и ищет вокруг этого места.</li>
 *   <li><b>Никто не видел</b> — последствий нет.</li>
 *   <li><b>Самооборона</b>: кто первым ударил — запоминается для каждой пары. Ответ на нападение — не преступление;
 *       полицейский, увидевший, как житель напал на игрока, велит ему стоять, задерживает его.</li>
 * </ul>
 */
public final class Witness {
    private Witness() {}

    private record Call(UUID witness, UUID player, Vec3d at, String why, int add, int min, long start, long due, int startAge) {}

    private static final List<Call> CALLS = new ArrayList<>();
    /** Житель → игроки, на которых он напал первым. */
    private static final Map<UUID, Set<UUID>> STARTED = new HashMap<>();
    /** Житель → игроки, которые ударили его первыми. */
    private static final Map<UUID, Set<UUID>> PLAYER_FIRST = new HashMap<>();
    /** Полицейский → кого задерживает. */
    private static final Map<UUID, UUID> ARRESTS = new HashMap<>();
    private static final boolean DEBUG = Boolean.getBoolean("novograd.debugwitness");
    /** Для автотестов. */
    public static int calls, reports, cancelled, arrests, instant;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Witness::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> {
            if (e instanceof ServerPlayerEntity p && src.getAttacker() instanceof CitizenEntity c && c.getRole() != CitizenEntity.Role.POLICE
                    && !(c instanceof BodyguardEntity) && !p.isCreative()) attacked(c, p);
            return true;
        });
    }

    // ------------------------------------------------------------------ кто начал
    public static boolean startedBy(CitizenEntity c, ServerPlayerEntity p) {
        return STARTED.getOrDefault(c.getUuid(), Set.of()).contains(p.getUuid());
    }

    /** Игрок ударил жителя (Crime.hurt): если первым — запоминаем. */
    static void playerHit(CitizenEntity c, ServerPlayerEntity p) {
        if (!startedBy(c, p)) PLAYER_FIRST.computeIfAbsent(c.getUuid(), k -> new HashSet<>()).add(p.getUuid());
    }

    /** Житель ударил игрока: первым — значит он напал; полиция рядом вступается. */
    private static void attacked(CitizenEntity c, ServerPlayerEntity p) {
        if (PLAYER_FIRST.getOrDefault(c.getUuid(), Set.of()).contains(p.getUuid())) return; // это ответ на удар игрока
        STARTED.computeIfAbsent(c.getUuid(), k -> new HashSet<>()).add(p.getUuid());
        if (ARRESTS.containsValue(c.getUuid())) return;
        CitizenEntity cop = officerSees(p.getServerWorld(), c, 32, false);
        if (cop == null || cop.getFoe() != null) return;
        ARRESTS.put(cop.getUuid(), c.getUuid());
        cop.say("Стоять! Полиция!", 50);
        cop.attack(c);
        if (!Weapons.isGun(c.getMainHandStack())) c.standDown(); // безоружный подчиняется
        Novograd.LOG.info("[witness] полицейский вступился: {} напал на {}", c.getName().getString(), p.getGameProfile().getName());
    }

    // ------------------------------------------------------------------ кто видит
    /** Полицейский (пеший или экипаж машины), который видит subject: конус взгляда и прямая видимость; громкое — слышно. */
    @Nullable
    public static CitizenEntity officerSees(ServerWorld w, Entity subject, double range, boolean loud) {
        double r = loud ? Math.max(range, 48) : range;
        Entity body = subject.getVehicle() != null ? subject.getVehicle() : subject;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, subject.getBoundingBox().expand(r),
                c -> c.getRole() == CitizenEntity.Role.POLICE && c.isAlive() && c != subject && c.knockPhase() == 0)) {
            double d = c.distanceTo(subject);
            if (DEBUG) Novograd.LOG.info("[witness] коп {} d={} взгляд={} видит={}", c.getId(), String.format("%.1f", d),
                    String.format("%.2f", Vec3d.fromPolar(0, c.getHeadYaw()).dotProduct(subject.getPos().subtract(c.getPos()).multiply(1, 0, 1).normalize())), c.canSee(body));
            if (loud && d < 48) return c; // выстрел, взрыв — слышно
            if (d > range) continue;
            Vec3d look = c.hasVehicle() ? Vec3d.fromPolar(0, c.getVehicle().getYaw()) : Vec3d.fromPolar(0, c.getHeadYaw());
            Vec3d to = subject.getPos().subtract(c.getPos()).multiply(1, 0, 1);
            if (d > 4 && to.lengthSquared() > 0.01 && look.dotProduct(to.normalize()) < 0.2) continue; // смотрит в другую сторону
            if (c.hasVehicle() ? Wanted.policeSees(w, body, range) : c.canSee(body)) return c;
        }
        return null;
    }

    /** Лучший свидетель среди жителей: видит игрока (или слышал), не бандит, не полицейский, не герой сюжета. */
    @Nullable
    private static CitizenEntity citizenWitness(ServerPlayerEntity p, boolean loud, @Nullable LivingEntity victim) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity best = null; // пострадавший тоже звонит — если видел, кто его ударил (сзади — не видел)
        double bd = Double.MAX_VALUE;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(loud ? 30 : 24),
                c -> c.isAlive() && c.knockPhase() == 0 && c.getStoryId().isEmpty() && !(c instanceof BodyguardEntity)
                        && c.getRole() != CitizenEntity.Role.GANG && c.getRole() != CitizenEntity.Role.POLICE && !c.isKid())) {
            double d = c.distanceTo(p);
            if (!loud || d > 14) {
                if (!c.canSee(p)) continue;
                // пострадавший — по корпусу (голова уже повернулась на удар): ударили сзади — не видел кто
                Vec3d look = Vec3d.fromPolar(0, c == victim ? c.getBodyYaw() : c.getHeadYaw());
                Vec3d to = p.getPos().subtract(c.getPos()).multiply(1, 0, 1);
                if ((d > 3 || c == victim) && to.lengthSquared() > 0.01 && look.dotProduct(to.normalize()) < (c == victim ? 0.3 : -0.2)) continue; // спиной не видно
            }
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ преступление игрока
    /**
     * Преступление: видит полицейский — розыск сразу; видит прохожий — звонок с задержкой; никто — ничего.
     * @return 2 — полиция видела, 1 — свидетель звонит, 0 — без свидетелей
     */
    public static int crime(ServerPlayerEntity p, Vec3d at, String why, int add, int min, boolean loud, @Nullable LivingEntity victim) {
        if (p.isCreative() || Pursuit.immune(p)) return 0;
        ServerWorld w = p.getServerWorld();
        if (Wanted.seen(p) || officerSees(w, p, 32, loud) != null) {
            Wanted.add(p, add, why, min);
            instant++;
            return 2;
        }
        long now = w.getTime();
        for (int i = 0; i < CALLS.size(); i++) {
            Call c = CALLS.get(i);
            if (c.player.equals(p.getUuid()) && c.at.squaredDistanceTo(at) < 40 * 40) { // уже звонят — дописывают
                CALLS.set(i, new Call(c.witness, c.player, c.at, c.why, Math.max(c.add, add), Math.max(c.min, min), c.start, c.due, c.startAge));
                return 1;
            }
        }
        CitizenEntity wit = citizenWitness(p, loud, victim);
        if (wit == null) {
            Novograd.LOG.info("[witness] без свидетелей: {}", why);
            return 0;
        }
        int delay = 200 + w.getRandom().nextInt(300);
        wit.busy(CitizenEntity.Activity.PHONE, delay + 40, null, new ItemStack(ModContent.PHONE));
        wit.say("Алло, полиция?", 60);
        CALLS.add(new Call(wit.getUuid(), p.getUuid(), at, why, add, min, now, now + delay, wit.age));
        calls++;
        p.sendMessage(Text.literal("Свидетель звонит в полицию").formatted(Formatting.GRAY), true);
        Novograd.LOG.info("[witness] звонок: {} ({} с), звонит {}", why, delay / 20, wit.getName().getString());
        return 1;
    }

    /** Этот житель сейчас звонит в полицию («Наблюдение»). */
    public static boolean calling(CitizenEntity c) {
        for (Call x : CALLS) if (x.witness.equals(c.getUuid())) return true;
        return false;
    }

    /** Автотест: сколько звонков сейчас идёт. */
    public static int pending() {
        return CALLS.size();
    }

    // ------------------------------------------------------------------ тик
    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 10 != 4) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (Iterator<Call> it = CALLS.iterator(); it.hasNext(); ) {
            Call c = it.next();
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(c.player);
            Entity e = w.getEntity(c.witness);
            if (p == null) {
                it.remove();
                continue;
            }
            if (!(e instanceof CitizenEntity wit) || !wit.isAlive() || wit.knockPhase() != 0 || wit.getLastAttackedTime() > c.startAge + 5) {
                it.remove(); // убит, вырублен или его ударили — не дозвонился
                cancelled++;
                Novograd.LOG.info("[witness] звонок сорван: {}", !(e instanceof CitizenEntity x) ? "пропал" : !x.isAlive() ? "убит" : x.knockPhase() != 0 ? "вырублен" : "ударили");
                continue;
            }
            // напугал: стоит рядом со стволом и смотрит на свидетеля
            if (p.distanceTo(wit) < 8 && Weapons.isGun(p.getMainHandStack())
                    && p.getRotationVector().dotProduct(wit.getEyePos().subtract(p.getEyePos()).normalize()) > 0.9) {
                it.remove();
                cancelled++;
                wit.panic(p.getPos());
                Novograd.LOG.info("[witness] свидетель напуган");
                continue;
            }
            if (wit.getActivity() != CitizenEntity.Activity.PHONE && !wit.fleeing())
                wit.busy(CitizenEntity.Activity.PHONE, (int) Math.max(40, c.due - now + 40), null, new ItemStack(ModContent.PHONE));
            if (now < c.due) continue;
            it.remove();
            reports++;
            Wanted.reported(p, c.why, c.add, c.min, c.at);
            Emergency.npcCrime(w, p, c.at, c.why, 0);
            Novograd.LOG.info("[witness] сообщение принято: {} {} — звёзд {}", c.why, Dispatch.where(c.at), Wanted.stars(p));
        }
        for (Iterator<Map.Entry<UUID, UUID>> it = ARRESTS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (!(w.getEntity(en.getKey()) instanceof CitizenEntity cop) || !cop.isAlive() || !(w.getEntity(en.getValue()) instanceof CitizenEntity a) || !a.isAlive()) {
                it.remove();
                continue;
            }
            if (!cop.chasing(a)) cop.attack(a);
            if (cop.distanceTo(a) < 2.4) {
                a.standDown();
                a.getNavigation().stop();
                a.lockActivity(CitizenEntity.Activity.UPSET);
                a.setStationary(true);
                a.removeLater(20 * 20);
                cop.standDown();
                cop.face(a);
                cop.say("Вы задержаны", 60);
                arrests++;
                Novograd.LOG.info("[witness] задержан: {}", a.getName().getString());
                it.remove();
            }
        }
        if (now % 1200 == 4) { // забыть пары драк ушедших жителей
            STARTED.keySet().removeIf(id -> w.getEntity(id) == null);
            PLAYER_FIRST.keySet().removeIf(id -> w.getEntity(id) == null);
        }
    }
}
