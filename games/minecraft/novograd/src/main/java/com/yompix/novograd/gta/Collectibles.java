package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.MotorVehicle;
import com.yompix.novograd.world.Island;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

/**
 * Коллекции острова, как в San Andreas:
 *  • 100 тегов банды на стенах — закрась своим (подойди к стене и держи Shift);
 *  • 50 тайников — это тайники из Activities (флаги pkg:N), здесь только счёт и награды за полный набор;
 *  • 30 фото-точек — встань на точку и сними достопримечательность камерой телефона;
 *  • 25 уникальных трюковых прыжков — трамплины за городом (трюки Activities тоже платят за полёт).
 * Точки одинаковые в каждом мире. На радаре оставшиеся видны после покупки карты у ларька «Карты»
 * или когда собрана половина набора. Прогресс — флаги LifeData (col:tag:N, col:photo:N, col:jump:N).
 */
public final class Collectibles {
    private Collectibles() {}

    public static final String[] CATS = {"tag", "pkg", "photo", "jump"};
    public static final String[] CAT_NAMES = {"Граффити", "Тайники", "Фото-точки", "Трюки"};
    public static final int TAGS = 100, PHOTOS = 30, JUMPS = 25;
    public static final int[] MAP_PRICE = {1500, 2000, 800, 1200};
    private static final int SPRAY_TICKS = 40;

    public static int total(String cat) {
        return switch (cat) {
            case "tag" -> TAGS;
            case "pkg" -> Activities.PACKAGES;
            case "photo" -> PHOTOS;
            default -> JUMPS;
        };
    }

    // ================================================================== точки
    private static List<int[]> tags, jumps;
    private static List<Photo> photos;

    public record Photo(String name, String place, int x, int z, double sx, double sz) {}

    private static int snap(double v) {
        return (int) (Math.round((v - 24) / 48.0) * 48 + 24);
    }

    private static boolean far(List<int[]> l, int x, int z, int min) {
        for (int[] s : l) if (Math.abs(s[0] - x) + Math.abs(s[1] - z) < min) return false;
        return true;
    }

    /** 100 тегов: тротуары у домов во всех городских районах, старый центр и порт. {x, z}. */
    public static List<int[]> tags() {
        if (tags != null) return tags;
        List<int[]> l = new ArrayList<>();
        Random r = new Random(4242);
        Set<Island.Zone> ok = EnumSet.of(Island.Zone.DOWNTOWN, Island.Zone.INDUSTRIAL, Island.Zone.SUBURB, Island.Zone.RIVIERA,
                Island.Zone.VILLAGE, Island.Zone.CORE, Island.Zone.PORT);
        int guard = 0, min = 70;
        while (l.size() < TAGS && guard++ < 60000) {
            if (guard % 15000 == 0) min -= 12; // если места мало — чуть теснее
            int x = r.nextInt(3700) - 1850, z = r.nextInt(3700) - 1850;
            Island.Zone zn = Island.zone(x, z);
            if (!ok.contains(zn)) continue;
            if (Island.urban(zn)) { // тротуар у фасада, между перекрёстками
                x = snap(x) + (r.nextBoolean() ? 6 : -6);
                z = snap(z) + 12 + r.nextInt(25);
                if (r.nextBoolean()) { int t = x; x = z; z = t; } // иногда — вдоль другой улицы
                if (!Island.urban(Island.zone(x, z))) continue;
            }
            if (Island.zone(x, z) == Island.Zone.SEA) continue;
            if (far(l, x, z, min)) l.add(new int[]{x, z});
        }
        tags = l;
        return l;
    }

    private static final String[][] PHOTO_SPOTS = {
            {"downtown_plaza", "Деловая площадь"}, {"bank_tower", "Башня Сити-Банка"}, {"club_neon", "Клуб «Неон»"}, {"casino_dt", "Казино центра"},
            {"gang_hq", "Логово «Чёрных Ящеров»"}, {"chop_shop", "Разборка в промзоне"}, {"industry_street", "Трубы промзоны"},
            {"port_crane", "Портовый кран"}, {"port_dock", "Причал"}, {"port_warehouse", "Склады порта"}, {"beach_bar", "Пляжный бар"},
            {"hill_top", "Вершина холма"}, {"airfield", "Взлётная полоса"}, {"airfield_hangar", "Ангар аэродрома"}, {"suburb_house", "Тихий пригород"},
            {"farm", "Старая ферма"}, {"riv_strip", "Стрип Ривьеры"}, {"riv_casino", "«Золотая корона»"}, {"riv_hotel", "Отель Ривьеры"},
            {"riv_marina", "Пристань Ривьеры"}, {"riv_beach", "Пляж Ривьеры"}, {"riv_villa", "Северная вилла"}, {"riv_villa2", "Южная вилла"},
            {"bridge_n", "Северный мост"}, {"bridge_s", "Южный мост"}, {"village", "Деревня Сосновка"}, {"village_farm", "Поля Сосновки"},
            {"lake_dock", "Озеро Ясное"}, {"mtn_top", "Седые горы"}, {"desert_mesa", "Столовая гора"},
    };

    /** 30 фото-точек: в 18–26 блоках от достопримечательности, на дороге или на земле (не в воде). */
    public static List<Photo> photos() {
        if (photos != null) return photos;
        List<Photo> l = new ArrayList<>();
        for (int i = 0; i < PHOTO_SPOTS.length && l.size() < PHOTOS; i++) {
            double[] s = Island.PLACES.get(PHOTO_SPOTS[i][0]);
            if (s == null) continue;
            int best = -1;
            int bx = (int) s[0], bz = (int) s[2];
            for (int pass = 0; pass < 2 && best < 0; pass++)
                for (int k = 0; k < 16; k++) {
                    double a = (k * 2 + i) * Math.PI / 8;
                    for (int dist = 18; dist <= 30; dist += 4) {
                        int x = (int) Math.round(s[0] + Math.cos(a) * dist), z = (int) Math.round(s[2] + Math.sin(a) * dist);
                        Island.Zone zn = Island.zone(x, z);
                        if (zn == Island.Zone.SEA) continue;
                        boolean good = pass == 1 || Island.road(x, z) || !Island.urban(zn) && zn != Island.Zone.CORE;
                        if (good) { bx = x; bz = z; best = k; break; }
                    }
                    if (best >= 0) break;
                }
            l.add(new Photo(PHOTO_SPOTS[i][1], PHOTO_SPOTS[i][0], bx, bz, s[0], s[2]));
        }
        photos = l;
        return l;
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** 25 трамплинов за городом: {x, z, dir 0..3}. Ровный разгон, за трамплином — свободная посадка. */
    public static List<int[]> jumps() {
        if (jumps != null) return jumps;
        List<int[]> l = new ArrayList<>();
        Random r = new Random(2525);
        Set<Island.Zone> ok = EnumSet.of(Island.Zone.COUNTRY, Island.Zone.BEACH, Island.Zone.AIRFIELD, Island.Zone.PORT);
        int guard = 0, min = 260;
        while (l.size() < JUMPS && guard++ < 80000) {
            if (guard % 20000 == 0) min -= 50;
            int x = r.nextInt(3700) - 1850, z = r.nextInt(3700) - 1850, dir = r.nextInt(4);
            if (!ok.contains(Island.zone(x, z))) continue;
            if (!far(l, x, z, min) || !corridorOk(x, z, dir)) continue;
            l.add(new int[]{x, z, dir});
        }
        jumps = l;
        return l;
    }

    private static boolean corridorOk(int x, int z, int dir) {
        int s0 = Island.surface(x, z);
        int[] d = DIRS[dir];
        for (int t = -16; t <= 44; t++)
            for (int lat = -3; lat <= 3; lat++) {
                int cx = x + d[0] * t - d[1] * lat, cz = z + d[1] * t + d[0] * lat;
                Island.Zone zn = Island.zone(cx, cz);
                if (zn == Island.Zone.SEA || zn == Island.Zone.CORE || zn == Island.Zone.VILLAGE || Island.urban(zn)) return false;
                int s = Island.surface(cx, cz);
                if (t <= 8 && Math.abs(s - s0) > 1) return false;           // ровный разгон и сам трамплин
                if (t > 8 && (s > s0 + 1 || s < s0 - 12)) return false;   // посадка — не в стену и не в пропасть
                if (t >= 0 && t <= 6 && Island.road(cx, cz)) return false; // трамплин не на проезжей части
            }
        return true;
    }

    /** Точка отрыва трамплина (центр края верхней ступени), y — высота верха. */
    public static Vec3d lip(int[] j) {
        int[] d = DIRS[j[2]];
        int base = Island.surface(j[0], j[1]) + 1;
        return new Vec3d(j[0] + 0.5 + d[0] * 5.5, base + 3, j[1] + 0.5 + d[1] * 5.5);
    }

    /** Ларьки «Карты коллекций»: деловая площадь, ворота порта, стрип Ривьеры. */
    public static final String[][] STALLS = {{"downtown_plaza", "6", "6"}, {"port_gate", "4", "-7"}, {"riv_strip", "5", "-7"}};

    public static Vec3d stall(ServerWorld w, int i) {
        double[] s = Island.PLACES.get(STALLS[i][0]);
        int x = (int) Math.floor(s[0]) + Integer.parseInt(STALLS[i][1]), z = (int) Math.floor(s[2]) + Integer.parseInt(STALLS[i][2]);
        return new Vec3d(x + 0.5, Story.topY(w, x, z), z + 0.5);
    }

    // ================================================================== прогресс
    public static boolean done(LifeData d, String cat, int i) {
        return d.flags.contains(cat.equals("pkg") ? "pkg:" + i : "col:" + cat + ":" + i);
    }

    public static int count(LifeData d, String cat) {
        int n = 0, t = total(cat);
        for (int i = 0; i < t; i++) if (done(d, cat, i)) n++;
        return n;
    }

    public static boolean radar(LifeData d, String cat) {
        return d.flags.contains("colmap:" + cat) || count(d, cat) * 2 >= total(cat);
    }

    private static int catIndex(String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return i;
        return -1;
    }

    // ================================================================== тик
    private static final Map<Integer, long[]> WALL = new HashMap<>(); // тег → {стена, сторона}
    private static final Set<Integer> RAMPS = new HashSet<>();
    private static final Map<UUID, int[]> SPRAY = new HashMap<>();   // {тег, тики}
    private static final Map<UUID, Jump> JUMP = new HashMap<>();
    private static final Map<UUID, Integer> AT_STALL = new HashMap<>();

    private static final class Jump {
        boolean air;
        int ticks, idx = -1;
        Vec3d ground;
        long cd;
    }

    public static void tick(MinecraftServer s) {
        long t = s.getTicks();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            LifeData d = Life.get(p);
            tickJump(p, d);
            if (t % 5 == 1) tickSpray(p, d);
            if (t % 10 == 3) tickStalls(p, d);
            if (t % 40 == 7) {
                prepare(p);
                sendNear(p, d);
                perks(p, d);
            }
            if (t % 200 == 9) checkSets(p, d); // тайники собирают в Activities — награду за набор выдаём здесь
        }
    }

    /** Рядом с игроком: найти стены для тегов, построить трамплины. */
    private static void prepare(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        List<int[]> tl = tags();
        for (int i = 0; i < tl.size(); i++) {
            int[] s = tl.get(i);
            if (WALL.containsKey(i) || sq(s[0] - p.getX(), s[1] - p.getZ()) > 80 * 80 || !loaded(w, s[0], s[1])) continue;
            WALL.put(i, findWall(w, s[0], s[1]));
        }
        List<int[]> jl = jumps();
        for (int i = 0; i < jl.size(); i++) {
            int[] j = jl.get(i);
            if (RAMPS.contains(i) || sq(j[0] - p.getX(), j[1] - p.getZ()) > 90 * 90) continue;
            int[] d = DIRS[j[2]];
            boolean all = true;
            for (int k : new int[]{-16, 0, 44}) all &= loaded(w, j[0] + d[0] * k, j[1] + d[1] * k);
            if (!all) continue;
            buildRamp(w, j);
            RAMPS.add(i);
        }
    }

    private static double sq(double a, double b) {
        return a * a + b * b;
    }

    private static boolean loaded(ServerWorld w, int x, int z) {
        return w.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
    }

    private static boolean solid(ServerWorld w, BlockPos b) {
        BlockState st = w.getBlockState(b);
        return st.isOpaqueFullCube(w, b) && !st.isOf(Blocks.GRASS_BLOCK) && !st.isIn(BlockTags.LEAVES);
    }

    private static boolean free(ServerWorld w, BlockPos b) {
        return w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    /** Стена у точки тега: ближайший сплошной фасад в 2+ блока высотой. Нет — ставим бетонную стенку. */
    private static long[] findWall(ServerWorld w, int x, int z) {
        int y = Island.surface(x, z) + 1;
        BlockPos spot = null;
        outer:
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos b = new BlockPos(x + dx, y + dy, z + dz);
                        if (free(w, b) && free(w, b.up()) && !free(w, b.down())) { spot = b; break outer; }
                    }
                }
        if (spot == null) spot = new BlockPos(x, Story.topY(w, x, z), z);
        long[] best = null;
        int bestK = 99;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos prev = spot;
            for (int k = 1; k <= 10; k++) {
                BlockPos b = spot.offset(dir, k);
                if (solid(w, b) && solid(w, b.up())) {
                    if (k < bestK && free(w, prev) && free(w, prev.up())) {
                        bestK = k;
                        best = new long[]{b.asLong(), dir.getOpposite().getId()}; // лицо стены смотрит на точку
                    }
                    break;
                }
                if (!free(w, b) || !free(w, b.up())) break; // столб, забор, машина — не та стена
                prev = b;
            }
        }
        if (best != null) return best;
        // стенки нет — строим короткую бетонную стенку (граффити на ней)
        BlockState c = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
        Direction face = Direction.NORTH;
        BlockPos base = spot.offset(face.getOpposite(), 1);
        for (int a = -1; a <= 1; a++)
            for (int h = 0; h < 2; h++) {
                BlockPos b = base.add(a, h, 0);
                if (free(w, b)) w.setBlockState(b, c);
            }
        return new long[]{base.asLong(), face.getId()};
    }

    private static void buildRamp(ServerWorld w, int[] j) {
        int[] d = DIRS[j[2]];
        int base = Island.surface(j[0], j[1]) + 1;
        // разгон и полоса посадки — без деревьев и кустов
        for (int t = -16; t <= 44; t++)
            for (int lat = -3; lat <= 3; lat++)
                for (int h = 0; h < 16; h++) {
                    int cx = j[0] + d[0] * t - d[1] * lat, cz = j[1] + d[1] * t + d[0] * lat;
                    BlockPos b = new BlockPos(cx, Island.surface(cx, cz) + 1 + h, cz);
                    BlockState st = w.getBlockState(b);
                    if (st.isIn(BlockTags.LOGS) || st.isIn(BlockTags.LEAVES) || st.isReplaceable() && !st.isAir() && st.getFluidState().isEmpty())
                        w.setBlockState(b, Blocks.AIR.getDefaultState(), 2);
                }
        for (int t = 0; t <= 5; t++)
            for (int lat = -1; lat <= 1; lat++) {
                int cx = j[0] + d[0] * t - d[1] * lat, cz = j[1] + d[1] * t + d[0] * lat;
                boolean side = lat != 0;
                BlockState full = (side ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE).getDefaultState();
                BlockState slab = (side ? Blocks.SMOOTH_SANDSTONE_SLAB : Blocks.SMOOTH_STONE_SLAB).getDefaultState();
                int halves = t + 1;
                for (int h = 0; h < halves / 2; h++) w.setBlockState(new BlockPos(cx, base + h, cz), full, 2);
                if (halves % 2 == 1) w.setBlockState(new BlockPos(cx, base + halves / 2, cz), slab, 2);
            }
        // полосатые столбики у края трамплина
        for (int lat : new int[]{-3, 3}) {
            int cx = j[0] + d[0] * 5 - d[1] * lat, cz = j[1] + d[1] * 5 + d[0] * lat;
            for (int h = 0; h < 4; h++)
                w.setBlockState(new BlockPos(cx, base + h, cz), (h % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE).getDefaultState(), 2);
            w.setBlockState(new BlockPos(cx, base + 4, cz), Blocks.SEA_LANTERN.getDefaultState(), 2);
        }
    }

    // ------------------------------------------------------------------ граффити
    private static void tickSpray(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        int near = -1;
        Vec3d nearAt = null;
        Direction nearFace = null;
        for (var e : WALL.entrySet()) {
            int i = e.getKey();
            if (done(d, "tag", i)) continue;
            BlockPos b = BlockPos.fromLong(e.getValue()[0]);
            Direction f = Direction.byId((int) e.getValue()[1]);
            Vec3d at = Vec3d.ofCenter(b).add(f.getOffsetX() * 0.55, 0.5, f.getOffsetZ() * 0.55);
            if (at.squaredDistanceTo(eye) < 3.6 * 3.6) {
                near = i;
                nearAt = at;
                nearFace = f;
                break;
            }
        }
        if (near < 0) {
            SPRAY.remove(p.getUuid());
            return;
        }
        Vec3d look = p.getRotationVec(1f);
        boolean facing = look.x * -nearFace.getOffsetX() + look.z * -nearFace.getOffsetZ() > 0.3;
        int[] st = SPRAY.get(p.getUuid());
        if (st == null || st[0] != near) SPRAY.put(p.getUuid(), st = new int[]{near, 0});
        if (!p.isSneaking() || !facing || p.hasVehicle()) {
            if (st[1] == 0) p.sendMessage(Text.literal("Тег банды на стене — держи Shift, глядя на стену, чтобы закрасить своим").formatted(Formatting.GOLD), true);
            return;
        }
        st[1] += 5;
        float k = Math.min(1, st[1] / (float) SPRAY_TICKS);
        int bars = (int) (k * 10);
        p.sendMessage(Text.literal("Граффити  " + "▮".repeat(bars) + "▯".repeat(10 - bars)).formatted(Formatting.GOLD), true);
        int[] cols = {0xFF2E88, 0x3FD8FF, 0x7CFF4A, 0xFFD000};
        int c = cols[(st[1] / 5) % cols.length];
        w.spawnParticles(new DustParticleEffect(new Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 1.4f),
                nearAt.x, nearAt.y, nearAt.z, 8, 0.4, 0.5, 0.4, 0);
        w.playSound(null, BlockPos.ofFloored(nearAt), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.25f, 1.9f);
        if (st[1] < SPRAY_TICKS) return;
        SPRAY.remove(p.getUuid());
        found(p, d, "tag", near);
    }

    // ------------------------------------------------------------------ фото
    /** Камера телефона (пакет "photo" из LifeNet): снимок с фото-точки засчитывается. */
    public static void onPhoto(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        List<Photo> l = photos();
        for (int i = 0; i < l.size(); i++) {
            Photo ph = l.get(i);
            double dx = ph.x() + 0.5 - p.getX(), dz = ph.z() + 0.5 - p.getZ();
            if (dx * dx + dz * dz > 7 * 7) continue;
            if (done(d, "photo", i)) {
                p.sendMessage(Text.literal("Этот кадр уже в коллекции: " + ph.name()).formatted(Formatting.GRAY), true);
                return;
            }
            double want = Math.toDegrees(Math.atan2(-(ph.sx() - p.getX()), ph.sz() - p.getZ()));
            float diff = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees((float) (want - p.getYaw())));
            if (diff > 45) {
                p.sendMessage(Text.literal("Не в кадре: повернись к «" + ph.name() + "» и сними ещё раз").formatted(Formatting.YELLOW), true);
                return;
            }
            found(p, d, "photo", i);
            return;
        }
    }

    // ------------------------------------------------------------------ уникальные прыжки
    private static void tickJump(ServerPlayerEntity p, LifeData d) {
        if (!(p.getVehicle() instanceof CarEntity car) || car instanceof MotorVehicle || car.getControllingPassenger() != p) {
            JUMP.remove(p.getUuid());
            return;
        }
        Jump j = JUMP.computeIfAbsent(p.getUuid(), u -> new Jump());
        // onGround у машины игрока на сервере ненадёжен (её двигает клиент) — проверяем опору под колёсами сами
        boolean ground = !p.getServerWorld().isSpaceEmpty(car, car.getBoundingBox().offset(0, -0.25, 0)) || car.isTouchingWater();
        if (ground) {
            if (j.air && j.idx >= 0) {
                int[] jp = jumps().get(j.idx);
                Vec3d lip = lip(jp);
                double dist = Math.hypot(car.getX() - lip.x, car.getZ() - lip.z);
                if (System.getProperty("novograd.autotest") != null)
                    com.yompix.novograd.Novograd.LOG.info("[pulse] прыжок {}: {} тиков в воздухе, {} м от края", j.idx, j.ticks, (int) dist);
                if (j.ticks >= 12 && dist >= 10 && !car.wrecked() && !car.isTouchingWater()) {
                    if (!done(d, "jump", j.idx)) found(p, d, "jump", j.idx);
                    else Activities.title(p, "УНИКАЛЬНЫЙ ПРЫЖОК", String.format("Уже пройден · %.0f м", dist));
                } else if (!done(d, "jump", j.idx) && p.getServerWorld().getTime() > j.cd) {
                    j.cd = p.getServerWorld().getTime() + 60;
                    p.sendMessage(Text.literal("Не долетел! Разгонись сильнее и прыгай прямо по трамплину").formatted(Formatting.YELLOW), true);
                }
            }
            j.air = false;
            j.idx = -1;
            j.ground = car.getPos();
            return;
        }
        if (!j.air) {
            j.air = true;
            j.ticks = 0;
            j.idx = -1;
            Vec3d from = j.ground != null ? j.ground : car.getPos();
            List<int[]> l = jumps();
            for (int i = 0; i < l.size(); i++) {
                Vec3d lip = lip(l.get(i));
                if (from.squaredDistanceTo(lip) > 5.5 * 5.5) continue;
                int[] dd = DIRS[l.get(i)[2]];
                Vec3d v = car.getVelocity();
                if (v.x * dd[0] + v.z * dd[1] > 0.15 || car.getPos().subtract(from).dotProduct(new Vec3d(dd[0], 0, dd[1])) >= 0) j.idx = i;
                break;
            }
            if (System.getProperty("novograd.autotest") != null && from.squaredDistanceTo(lip(l.get(0))) < 40 * 40)
                com.yompix.novograd.Novograd.LOG.info("[pulse] отрыв от земли: {} трамплин {}", from, j.idx);
        }
        j.ticks++;
    }

    // ------------------------------------------------------------------ находка и наборы
    private static void found(ServerPlayerEntity p, LifeData d, String cat, int i) {
        d.flags.add("col:" + cat + ":" + i);
        int n = count(d, cat), tot = total(cat);
        ServerWorld w = p.getServerWorld();
        int pay = switch (cat) {
            case "tag" -> 100;
            case "photo" -> 300;
            default -> 500;
        };
        String head = switch (cat) {
            case "tag" -> "ГРАФФИТИ " + n + "/" + tot;
            case "photo" -> "ФОТО " + n + "/" + tot;
            default -> "УНИКАЛЬНЫЙ ПРЫЖОК " + n + "/" + tot;
        };
        String sub = cat.equals("photo") ? photos().get(i).name() + " · +$" + pay : "+$" + pay;
        Life.earn(p, pay, head.toLowerCase(Locale.ROOT));
        Activities.title(p, head, sub);
        w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.5f, 1.4f);
        if (cat.equals("tag") && n % 25 == 0 && n < tot)
            News.post(p.getServer(), "Граффити по всему городу: банды в ярости, кто-то закрашивает их метки (" + n + " из " + tot + ")", "ГОРОД");
        checkSets(p, d);
        sendState(p, false);
        sendNear(p, d);
    }

    /** Половина набора — радар показывает остальные; полный набор — награда. Тайники проверяются здесь же. */
    public static void checkSets(ServerPlayerEntity p, LifeData d) {
        for (int c = 0; c < CATS.length; c++) {
            String cat = CATS[c];
            int n = count(d, cat), tot = total(cat);
            if (n * 2 >= tot && d.flags.add("colhalf:" + cat)) {
                Life.earn(p, 1000, "половина набора «" + CAT_NAMES[c] + "»");
                Life.message(p, "Коллекции", "Половина набора «" + CAT_NAMES[c] + "» собрана! Остальные теперь видны на радаре.");
            }
            if (n >= tot && d.flags.add("colset:" + cat)) setReward(p, d, cat);
        }
        boolean all = true;
        for (String cat : CATS) all &= d.flags.contains("colset:" + cat);
        if (all && d.flags.add("colset:all")) {
            Achievements.give(p, "collector");
            Life.earn(p, 50000, "все коллекции острова");
            Activities.title(p, "ВСЕ КОЛЛЕКЦИИ!", "+$50 000 · ещё +2 сердца здоровья");
            News.post(p.getServer(), "Человек, который знает Новоград лучше всех: собраны все тайники, теги, кадры и прыжки острова", "ЭКСТРЕННО");
        }
    }

    private static void setReward(ServerPlayerEntity p, LifeData d, String cat) {
        switch (cat) {
            case "tag" -> {
                Life.earn(p, 20000, "все 100 граффити");
                Achievements.give(p, "tags100");
                Activities.title(p, "ВСЕ ГРАФФИТИ!", "+$20 000 · в убежище каждый день: пистолет, ПП, дробовик, бронежилет");
                News.post(p.getServer(), "Город перекрашен: все метки банд закрашены одним и тем же тегом. Банды обещают найти художника", "ЭКСТРЕННО");
            }
            case "pkg" -> {
                Life.earn(p, 10000, "все 50 тайников");
                Activities.title(p, "ВСЕ ТАЙНИКИ!", "+$10 000 · в убежище каждый день: автомат, снайперка, гранаты, аптечки");
            }
            case "photo" -> {
                Life.earn(p, 15000, "все 30 фото");
                Achievements.give(p, "photos30");
                Activities.title(p, "ВСЕ ФОТО!", "+$15 000 · бесплатный механик: телефон → «Коллекции»");
                News.post(p.getServer(), "Фотограф-любитель снял весь остров: выставка «Новоград с тротуара» открывается в центре", "ГОРОД");
            }
            case "jump" -> {
                Life.earn(p, 20000, "все 25 прыжков");
                Achievements.give(p, "jumps25");
                Activities.title(p, "ВСЕ ТРЮКИ!", "+$20 000 · +4 сердца здоровья навсегда");
                News.post(p.getServer(), "Каскадёр-призрак перепрыгал все трамплины острова. Страховые компании в панике", "ЭКСТРЕННО");
            }
            default -> {}
        }
        Economy.shock(p.getServer(), cat.equals("jump") ? "NITR" : "RIVH", 0.04, "о коллекционере говорит весь остров");
    }

    private static final Identifier HP_ID = com.yompix.novograd.Novograd.id("collect_health");

    /** Награды, которые действуют всегда: здоровье, оружие в убежище. */
    private static void perks(ServerPlayerEntity p, LifeData d) {
        double bonus = (d.flags.contains("colset:jump") ? 8 : 0) + (d.flags.contains("colset:all") ? 4 : 0);
        var hp = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) {
            var m = hp.getModifier(HP_ID);
            if (bonus > 0 && (m == null || m.value() != bonus)) {
                hp.removeModifier(HP_ID);
                hp.addPersistentModifier(new EntityAttributeModifier(HP_ID, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
            } else if (bonus == 0 && m != null) hp.removeModifier(HP_ID);
        }
        boolean tag = d.flags.contains("colset:tag"), pkg = d.flags.contains("colset:pkg");
        if (!tag && !pkg) return;
        long day = Life.day(p);
        if (FunUtil.getLong(d, "colweap_day") == day || !Activities.nearOwnedSafehouse(p, 10)) return;
        FunUtil.setLong(d, "colweap_day", day);
        List<ItemStack> l = new ArrayList<>();
        if (tag) {
            l.add(new ItemStack(ModContent.PISTOL));
            l.add(new ItemStack(ModContent.SMG));
            l.add(new ItemStack(Arsenal.SHOTGUN));
            l.add(new ItemStack(Arsenal.ARMOR));
            l.add(new ItemStack(ModContent.AMMO, 64));
        }
        if (pkg) {
            l.add(new ItemStack(Arsenal.RIFLE));
            l.add(new ItemStack(Arsenal.SNIPER));
            l.add(new ItemStack(Arsenal.GRENADE, 4));
            l.add(new ItemStack(Arsenal.MEDKIT, 2));
            l.add(new ItemStack(ModContent.AMMO, 64));
        }
        ServerWorld w = p.getServerWorld();
        for (int i = 0; i < l.size(); i++) {
            double a = i * Math.PI * 2 / l.size();
            ItemEntity it = new ItemEntity(w, p.getX() + Math.cos(a) * 1.6, p.getY() + 0.5, p.getZ() + Math.sin(a) * 1.6, l.get(i));
            it.setVelocity(0, 0.15, 0);
            it.setToDefaultPickupDelay();
            w.spawnEntity(it);
        }
        Activities.title(p, "ОРУЖИЕ В УБЕЖИЩЕ", "Награда за коллекции — забирай");
    }

    /** Бесплатный механик (все 30 фото): чинит машину, в которой сидишь. */
    public static void freeRepair(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (!d.flags.contains("colset:photo")) {
            p.sendMessage(Text.literal("Механик приезжает только к тем, кто собрал все 30 фото").formatted(Formatting.YELLOW), true);
            return;
        }
        if (!(p.getVehicle() instanceof CarEntity car) || car.wrecked()) {
            p.sendMessage(Text.literal("Сядь в машину — механик починит её на месте").formatted(Formatting.YELLOW), true);
            return;
        }
        long now = p.getServerWorld().getTime();
        if (now < FunUtil.getLong(d, "colrepair")) {
            p.sendMessage(Text.literal("Механик ещё в пути: через " + (FunUtil.getLong(d, "colrepair") - now) / 20 + " с").formatted(Formatting.YELLOW), true);
            return;
        }
        FunUtil.setLong(d, "colrepair", now + 20 * 60);
        car.repair();
        p.getServerWorld().playSound(null, car.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.6f, 1.3f);
        Activities.title(p, "МАШИНА КАК НОВАЯ", "Бесплатный механик · награда за фото");
    }

    // ------------------------------------------------------------------ ларёк «Карты»
    private static void tickStalls(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        int at = -1;
        for (int i = 0; i < STALLS.length; i++) {
            double[] s = Island.PLACES.get(STALLS[i][0]);
            if (s == null || sq(s[0] - p.getX(), s[2] - p.getZ()) > 64 * 64) continue;
            Vec3d v = stall(w, i);
            var it = FunUtil.marker(w, v, "col_stall_" + i, new ItemStack(Items.FILLED_MAP),
                    Text.literal("Карты коллекций — граффити, тайники, фото, трюки").formatted(Formatting.GOLD), true);
            if (p.squaredDistanceTo(it) < 2.2 * 2.2 && !p.hasVehicle()) at = i;
        }
        Integer was = AT_STALL.put(p.getUuid(), at);
        if (at >= 0 && (was == null || was != at)) sendState(p, true);
    }

    public static boolean nearStall(ServerPlayerEntity p) {
        for (int i = 0; i < STALLS.length; i++)
            if (stall(p.getServerWorld(), i).squaredDistanceTo(p.getPos()) < 7 * 7) return true;
        return false;
    }

    public static void buyMap(ServerPlayerEntity p, String cat) {
        int c = catIndex(cat);
        LifeData d = Life.get(p);
        if (c < 0 || d.flags.contains("colmap:" + cat)) return;
        if (!nearStall(p)) {
            p.sendMessage(Text.literal("Карты продают в ларьках: деловая площадь, ворота порта, стрип Ривьеры").formatted(Formatting.YELLOW), true);
            return;
        }
        if (!Life.pay(p, MAP_PRICE[c], "карта «" + CAT_NAMES[c] + "»")) return;
        d.flags.add("colmap:" + cat);
        Life.message(p, "Карты Новограда", "Карта «" + CAT_NAMES[c] + "» загружена в радар: оставшиеся точки видны рядом с тобой.");
        sendState(p, true);
        sendNear(p, d);
    }

    // ------------------------------------------------------------------ клиенту
    public static void sendState(ServerPlayerEntity p, boolean open) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        NbtList cats = new NbtList();
        for (int c = 0; c < CATS.length; c++) {
            NbtCompound e = new NbtCompound();
            e.putString("id", CATS[c]);
            e.putString("name", CAT_NAMES[c]);
            e.putInt("n", count(d, CATS[c]));
            e.putInt("total", total(CATS[c]));
            e.putBoolean("map", d.flags.contains("colmap:" + CATS[c]));
            e.putBoolean("radar", radar(d, CATS[c]));
            e.putBoolean("set", d.flags.contains("colset:" + CATS[c]));
            e.putInt("price", MAP_PRICE[c]);
            cats.add(e);
        }
        n.put("cats", cats);
        NbtList ph = new NbtList();
        List<Photo> pl = photos();
        for (int i = 0; i < pl.size(); i++) {
            NbtCompound e = new NbtCompound();
            e.putString("name", pl.get(i).name());
            e.putBoolean("done", done(d, "photo", i));
            ph.add(e);
        }
        n.put("photos", ph);
        n.putBoolean("stall", nearStall(p));
        n.putBoolean("open", open);
        n.putBoolean("all", d.flags.contains("colset:all"));
        n.putBoolean("chaos", Chaos.on(p));
        n.putBoolean("chaosUnlocked", d.storyDone);
        Pulse.send(p, "col_state", n);
    }

    /** Что рисовать рядом (теги на стенах, фото-точки, трамплины) и что показать на радаре. */
    public static void sendNear(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        NbtCompound n = new NbtCompound();
        NbtList near = new NbtList(), rad = new NbtList();
        double px = p.getX(), pz = p.getZ();
        List<int[]> tl = tags();
        boolean rTag = radar(d, "tag"), rPkg = radar(d, "pkg"), rPh = radar(d, "photo"), rJ = radar(d, "jump");
        for (int i = 0; i < tl.size(); i++) {
            int[] s = tl.get(i);
            double dd = sq(s[0] - px, s[1] - pz);
            boolean dn = done(d, "tag", i);
            long[] wl = WALL.get(i);
            if (dd < 72 * 72 && wl != null) {
                NbtCompound e = new NbtCompound();
                e.putString("k", "tag");
                e.putInt("i", i);
                e.putLong("b", wl[0]);
                e.putInt("f", (int) wl[1]);
                e.putBoolean("done", dn);
                near.add(e);
            }
            if (rTag && !dn && dd < 380 * 380) rad.add(blip("tag", s[0], s[1]));
        }
        List<int[]> pk = Activities.packages();
        if (rPkg) for (int i = 0; i < pk.size(); i++)
            if (!done(d, "pkg", i) && sq(pk.get(i)[0] - px, pk.get(i)[1] - pz) < 380 * 380) rad.add(blip("pkg", pk.get(i)[0], pk.get(i)[1]));
        List<Photo> pl = photos();
        for (int i = 0; i < pl.size(); i++) {
            Photo ph = pl.get(i);
            double dd = sq(ph.x() - px, ph.z() - pz);
            boolean dn = done(d, "photo", i);
            if (dd < 96 * 96 && loaded(w, ph.x(), ph.z())) {
                NbtCompound e = new NbtCompound();
                e.putString("k", "photo");
                e.putInt("i", i);
                e.putDouble("x", ph.x() + 0.5);
                e.putDouble("y", Story.topY(w, ph.x(), ph.z()));
                e.putDouble("z", ph.z() + 0.5);
                e.putDouble("sx", ph.sx());
                e.putDouble("sz", ph.sz());
                e.putString("name", ph.name());
                e.putBoolean("done", dn);
                near.add(e);
            }
            if (rPh && !dn && dd < 600 * 600) rad.add(blip("photo", ph.x(), ph.z()));
        }
        List<int[]> jl = jumps();
        for (int i = 0; i < jl.size(); i++) {
            int[] j = jl.get(i);
            double dd = sq(j[0] - px, j[1] - pz);
            boolean dn = done(d, "jump", i);
            if (dd < 120 * 120) {
                NbtCompound e = new NbtCompound();
                e.putString("k", "jump");
                e.putInt("i", i);
                e.putInt("x", j[0]);
                e.putInt("z", j[1]);
                e.putInt("y", Island.surface(j[0], j[1]) + 1);
                e.putInt("dir", j[2]);
                e.putBoolean("done", dn);
                near.add(e);
            }
            if (rJ && !dn && dd < 700 * 700) rad.add(blip("jump", j[0], j[1]));
        }
        n.put("near", near);
        n.put("radar", rad);
        Pulse.send(p, "col_near", n);
    }

    private static NbtCompound blip(String k, int x, int z) {
        NbtCompound e = new NbtCompound();
        e.putString("k", k);
        e.putInt("x", x);
        e.putInt("z", z);
        return e;
    }

    public static void onJoin(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        checkSets(p, d); // тайники могли собрать раньше — награда за набор всё равно положена
        sendState(p, false);
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (int i = 0; i < STALLS.length; i++) {
            double[] s = Island.PLACES.get(STALLS[i][0]);
            NbtCompound e = new NbtCompound();
            e.putString("id", "pulse_maps_" + i);
            e.putInt("x", (int) Math.floor(s[0]) + Integer.parseInt(STALLS[i][1]));
            e.putInt("z", (int) Math.floor(s[2]) + Integer.parseInt(STALLS[i][2]));
            l.add(e);
        }
        n.put("list", l);
        Pulse.send(p, "places", n);
    }

    public static void onLeave(ServerPlayerEntity p) {
        SPRAY.remove(p.getUuid());
        JUMP.remove(p.getUuid());
        AT_STALL.remove(p.getUuid());
    }

    // ------------------------------------------------------------------ отладка
    public static String debugGive(ServerPlayerEntity p, String cat, int n) {
        LifeData d = Life.get(p);
        int t = total(cat), got = 0;
        for (int i = 0; i < t && got < n; i++) if (!done(d, cat, i)) {
            d.flags.add(cat.equals("pkg") ? "pkg:" + i : "col:" + cat + ":" + i);
            got++;
        }
        checkSets(p, d);
        sendState(p, false);
        Life.sync(p);
        return cat + ": " + count(d, cat) + "/" + t;
    }

    public static String debugTp(ServerPlayerEntity p, String cat, int i) {
        ServerWorld w = p.getServerWorld();
        int x, z;
        float yaw = p.getYaw();
        switch (cat) {
            case "tag" -> { x = tags().get(i)[0]; z = tags().get(i)[1]; }
            case "photo" -> {
                Photo ph = photos().get(i);
                x = ph.x(); z = ph.z();
                yaw = (float) Math.toDegrees(Math.atan2(-(ph.sx() - (x + 0.5)), ph.sz() - (z + 0.5)));
            }
            case "pkg" -> { x = Activities.packages().get(i)[0] + 3; z = Activities.packages().get(i)[1]; }
            case "stall" -> { Vec3d v = stall(w, i); x = (int) v.x + 3; z = (int) v.z; }
            default -> {
                int[] j = jumps().get(i);
                int[] d = DIRS[j[2]];
                x = j[0] - d[0] * 14; z = j[1] - d[1] * 14;
                yaw = (float) Math.toDegrees(Math.atan2(-d[0], d[1]));
            }
        }
        w.getChunk(x >> 4, z >> 4);
        p.teleport(w, x + 0.5, Story.topY(w, x, z), z + 0.5, yaw, 0);
        return cat + " #" + i + " → " + x + ", " + z;
    }

    /** Для автотеста: встать перед стеной тега i. */
    public static String debugView(ServerPlayerEntity p, int i) {
        long[] wl = WALL.get(i);
        if (wl == null) return "Стена тега " + i + " ещё не найдена";
        BlockPos b = BlockPos.fromLong(wl[0]);
        Direction f = Direction.byId((int) wl[1]);
        BlockPos at = b.offset(f, 2);
        p.teleport(p.getServerWorld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, f.getOpposite().asRotation(), 5);
        return "Тег " + i + " на " + b.toShortString() + " лицом " + f;
    }

    public static String debugList(String cat) {
        StringBuilder sb = new StringBuilder(cat + ": ");
        switch (cat) {
            case "photo" -> { for (Photo ph : photos()) sb.append(ph.name()).append(" ").append(ph.x()).append(",").append(ph.z()).append("; "); }
            case "jump" -> { for (int[] j : jumps()) sb.append(j[0]).append(",").append(j[1]).append(" d").append(j[2]).append("; "); }
            default -> { for (int[] t : tags()) sb.append(t[0]).append(",").append(t[1]).append(" ").append(Island.zone(t[0], t[1])).append("; "); }
        }
        return sb.toString();
    }
}
