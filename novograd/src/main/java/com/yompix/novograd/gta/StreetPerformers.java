package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Housing;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Уличные музыканты и танцоры: гитарист и саксофонист на площадях и Стрипе Ривьеры, брейкдансеры с бумбоксом
 * у пляжа и в деловом центре. Играют днём и вечером (10:00–23:00), рядом слышна их музыка (петли из tools/gen_busk.py).
 * ПКМ по артисту — бросить монетку $1–$10: благодарят, монетка падает в чехол, а постоянным слушателям посвящают песню.
 */
public final class StreetPerformers {
    private StreetPerformers() {}

    public static final String TAG = "nvg_busker";
    public static final SoundEvent GUITAR = sound("busk.guitar"), SAX = sound("busk.sax"), BEAT = sound("busk.beat");

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    /** Петли: длина в тиках (как в tools/gen_busk.py). */
    private enum Act {
        GUITAR(CitizenEntity.Activity.GUITAR, 312, "Гитарист"), SAX(CitizenEntity.Activity.SAX, 320, "Саксофонист"), DANCE(CitizenEntity.Activity.BREAKDANCE, 302, "Брейкдансер");
        final CitizenEntity.Activity anim;
        final int loop;
        final String who;

        Act(CitizenEntity.Activity a, int loop, String who) {
            anim = a;
            this.loop = loop;
            this.who = who;
        }

        SoundEvent sound() {
            return switch (this) {
                case GUITAR -> StreetPerformers.GUITAR;
                case SAX -> StreetPerformers.SAX;
                case DANCE -> BEAT;
            };
        }
    }

    /** Точка: место сюжета + сдвиг, кто там выступает (у танцоров — двое). */
    private record Spot(String place, double dx, double dz, Act act, int people, String name) {}

    private static final Spot[] SPOTS = {
            new Spot("downtown_plaza", 6, 4, Act.GUITAR, 1, "Миша"),
            new Spot("downtown_plaza", -8, 6, Act.DANCE, 2, "Крю «Асфальт»"),
            new Spot("riv_strip", 4, 6, Act.SAX, 1, "Арсений"),
            new Spot("riv_strip", -6, -6, Act.GUITAR, 1, "Лера"),
            new Spot("riv_beach", 6, 2, Act.DANCE, 2, "Крю «Прибой»"),
            new Spot("beach_bar", 6, 4, Act.SAX, 1, "Дед Семён"),
            new Spot("park_center", 4, -4, Act.GUITAR, 1, "Саша"),
            new Spot("riv_marina", 4, 4, Act.SAX, 1, "Марк"),
    };

    private static final Map<Integer, List<UUID>> LIVE = new HashMap<>();
    private static final Map<Integer, Long> NEXT_SONG = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<String, Integer> FANS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StreetPerformers::tick);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof CitizenEntity c) || !c.getCommandTags().contains(TAG)) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity p) tip(p, c);
            return ActionResult.SUCCESS;
        });
    }

    @Nullable
    private static Vec3d at(ServerWorld w, Spot s) {
        Vec3d v = Story.place(w, s.place());
        if (v == null) return null;
        int x = (int) Math.floor(v.x + s.dx()), z = (int) Math.floor(v.z + s.dz());
        // точка сюжета часто посреди улицы — артисты встают на тротуар
        com.yompix.novograd.world.Island.Zone zn = com.yompix.novograd.world.Island.zone(x, z);
        int gx = com.yompix.novograd.world.Island.gridDist(x), gz = com.yompix.novograd.world.Island.gridDist(z);
        if (com.yompix.novograd.world.Island.urban(zn) && Math.min(gx, gz) < 5) {
            if (gx <= gz) {
                int line = (int) (Math.round((x - 24) / 48.0) * 48 + 24);
                x = line + (x >= line ? 5 : -5);
            } else {
                int line = (int) (Math.round((z - 24) / 48.0) * 48 + 24);
                z = line + (z >= line ? 5 : -5);
            }
        }
        return new Vec3d(x + 0.5, com.yompix.novograd.world.Island.surface(x, z) + 1, z + 0.5);
    }

    private static boolean showtime(ServerWorld w) {
        double h = ((w.getTimeOfDay() % 24000) / 1000.0 + 6) % 24;
        return h >= 10 && h < 23 && !w.isRaining();
    }

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        ServerWorld w = server.getOverworld();
        if (t % 20 == 7) for (int i = 0; i < SPOTS.length; i++) manage(w, i);
        // музыка по кругу, пока кто-то рядом слушает
        for (var e : LIVE.entrySet()) {
            Spot s = SPOTS[e.getKey()];
            long next = NEXT_SONG.getOrDefault(e.getKey(), 0L);
            if (t < next) continue;
            CitizenEntity c = first(w, e.getValue());
            if (c == null) continue;
            NEXT_SONG.put(e.getKey(), t + s.act().loop);
            w.playSound(null, c.getX(), c.getY() + 1, c.getZ(), s.act().sound(), SoundCategory.RECORDS, 1.6f, 1f);
        }
        if (t % 10 == 0) for (var e : LIVE.entrySet()) { // ноты над музыкантом
            CitizenEntity c = first(w, e.getValue());
            if (c != null && w.getRandom().nextInt(3) == 0)
                w.spawnParticles(ParticleTypes.NOTE, c.getX(), c.getY() + 2.3, c.getZ(), 1, 0.3, 0.1, 0.3, w.getRandom().nextDouble());
        }
    }

    @Nullable
    private static CitizenEntity first(ServerWorld w, List<UUID> ids) {
        for (UUID u : ids) if (w.getEntity(u) instanceof CitizenEntity c && c.isAlive()) return c;
        return null;
    }

    private static void manage(ServerWorld w, int i) {
        Spot s = SPOTS[i];
        Vec3d at = at(w, s);
        List<UUID> live = LIVE.get(i);
        boolean near = at != null && w.getClosestPlayer(at.x, at.y, at.z, 56, false) != null;
        if (live != null) {
            boolean alive = live.stream().anyMatch(u -> w.getEntity(u) instanceof CitizenEntity c && c.isAlive());
            if (!near || !showtime(w) || !alive) {
                for (UUID u : live) if (w.getEntity(u) instanceof CitizenEntity c) {
                    if (!c.calm()) continue; // испугались стрельбы — убегут сами, потом исчезнут
                    c.discard();
                }
                props(w, i, at, true);
                if (!alive && near && showtime(w)) NEXT_SONG.put(i, (long) w.getServer().getTicks() + 20 * 120); // напугали — вернутся через пару минут
                LIVE.remove(i);
                return;
            }
            // кого напугали — тот бросает выступление и убегает (обычная паника горожанина)
            for (UUID u : live) if (w.getEntity(u) instanceof CitizenEntity c && !c.calm() && c.getActivity() == s.act().anim) {
                c.setStationary(false);
                c.lockActivity(null);
            }
            return;
        }
        if (at == null || !near || !showtime(w) || w.getServer().getTicks() < NEXT_SONG.getOrDefault(i, 0L) - 20 * 60) return;
        BlockPos bp = BlockPos.ofFloored(at);
        if (!w.isChunkLoaded(bp)) return;
        // чужие копии (после перезапуска) — убрать
        for (CitizenEntity old : w.getEntitiesByClass(CitizenEntity.class, new Box(bp).expand(12), c -> c.getCommandTags().contains(TAG + ":" + i))) old.discard();
        List<UUID> ids = new ArrayList<>();
        var r = net.minecraft.util.math.random.Random.create(i * 7919L);
        for (int k = 0; k < s.people(); k++) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            int skin = CitizenEntity.RANDOM_FROM + r.nextInt(CitizenEntity.RANDOM_COUNT);
            String name = s.act().who + " " + s.name();
            c.setup(skin, CitizenEntity.Role.CITIZEN, true, name);
            c.setCustomName(Text.literal(name + " · ПКМ — монетка"));
            c.setCustomNameVisible(true);
            c.addCommandTag(TAG);
            c.addCommandTag(TAG + ":" + i);
            double ox = k * 2.2 - (s.people() - 1) * 1.1;
            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x + ox), (int) Math.floor(at.z));
            c.refreshPositionAndAngles(at.x + ox, Math.min(y, at.y + 3), at.z, 180 + (float) (r.nextDouble() * 40 - 20), 0);
            c.lockActivity(s.act().anim);
            if (s.act() == Act.SAX) c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOAT_HORN));
            w.spawnEntity(c);
            c.setStationary(true);
            ids.add(c.getUuid());
        }
        LIVE.put(i, ids);
        NEXT_SONG.put(i, 0L);
        props(w, i, at, false);
    }

    /** Чехол для монет (или бумбокс у танцоров) перед артистом. */
    private static void props(ServerWorld w, int i, @Nullable Vec3d at, boolean remove) {
        if (at == null) return;
        String tag = TAG + "_prop:" + i;
        List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new Box(BlockPos.ofFloored(at)).expand(6), e -> e.getCommandTags().contains(tag));
        if (remove) {
            have.forEach(ItemEntity::discard);
            return;
        }
        if (!have.isEmpty()) return;
        Spot s = SPOTS[i];
        int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z - 1.6));
        ItemEntity it = new ItemEntity(w, at.x + 0.6, Math.min(y, at.y + 3), at.z - 1.4, new ItemStack(s.act() == Act.DANCE ? Items.JUKEBOX : Items.NOTE_BLOCK));
        it.setVelocity(0, 0, 0);
        it.setPickupDelayInfinite();
        it.setNeverDespawn();
        it.addCommandTag(tag);
        w.spawnEntity(it);
    }

    /** Бросить монетку: $1–$10 с карты, артист благодарит, монетка летит в чехол. */
    private static void tip(ServerPlayerEntity p, CitizenEntity c) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (now < COOLDOWN.getOrDefault(p.getUuid(), 0L)) return;
        COOLDOWN.put(p.getUuid(), now + 15);
        LifeData d = Life.get(p);
        int coin = 1 + w.getRandom().nextInt(10);
        if (d.money < coin) {
            p.sendMessage(Text.literal("В карманах пусто — даже монетки нет").formatted(Formatting.GRAY), true);
            return;
        }
        d.money -= coin;
        d.spent += coin;
        Life.sync(p);
        // монетка летит к ногам артиста
        ItemEntity it = new ItemEntity(w, p.getX(), p.getEyeY() - 0.3, p.getZ(), new ItemStack(Items.GOLD_NUGGET));
        Vec3d v = c.getPos().subtract(p.getPos()).multiply(0.09).add(0, 0.25, 0);
        it.setVelocity(v);
        it.setPickupDelayInfinite();
        it.addCommandTag(TAG + "_coin");
        w.spawnEntity(it);
        List<ItemEntity> coins = w.getEntitiesByClass(ItemEntity.class, c.getBoundingBox().expand(5), e -> e.getCommandTags().contains(TAG + "_coin"));
        if (coins.size() > 8) coins.get(0).discard();
        w.spawnParticles(ParticleTypes.HEART, c.getX(), c.getY() + 2.2, c.getZ(), 2, 0.3, 0.2, 0.3, 0);
        w.playSound(null, c.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.6f, 1.6f);
        String name = c.getCustomName() == null ? "Артист" : c.getCustomName().getString().replace(" · ПКМ — монетка", "");
        String[] thanks = {"Спасибо, дружище!", "Благодарю! Хорошего вечера!", "О, спасибо! Эта — для тебя!", "Ты лучший слушатель за сегодня!", "Мерси! Заходи ещё!"};
        p.sendMessage(Text.literal(name + ": «" + thanks[w.getRandom().nextInt(thanks.length)] + "»  (−$" + coin + ")").formatted(Formatting.LIGHT_PURPLE), true);
        // постоянный слушатель: каждая пятая монетка этому артисту — посвящение, десятая за всё время — «карма» в городе
        String key = p.getUuidAsString() + "|" + name;
        int n = FANS.merge(key, 1, Integer::sum);
        Housing.State st = Housing.state(p.getServer());
        Housing.Owner o = st.owner(p.getUuid());
        o.tips++;
        st.markDirty();
        if (n % 5 == 0) Activities.title(p, "ПЕСНЯ ДЛЯ ТЕБЯ", name + " посвящает следующую песню тебе");
        if (o.tips == 10) Life.message(p, "Новоград-24", "Уличные музыканты говорят о щедром незнакомце. Город тебя запомнил — без всякого розыска.");
    }
}
