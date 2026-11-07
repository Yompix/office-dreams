package com.yompix.novograd.gta;

import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Уличные экраны «Новоград-24»: большие видеостены на фасадах (деловая площадь, стрип Ривьеры, ворота порта).
 * Экран ставится сам, когда игрок впервые подходит (подходит и для старых миров): ищем глухую стену рядом,
 * вешаем на неё раму 11×7 из чёрного бетона. Картинку — бегущие заголовки, биржу, погоду — рисует клиент
 * (client/NewsTvRender) по списку экранов и пакету "tv". Телевизоры в квартирах на канале «Новости 24» — тоже.
 */
public final class NewsTv {
    private NewsTv() {}

    /** Где ищем стену под экран. */
    private static final String[] ANCHORS = {"downtown_plaza", "riv_strip", "port_gate"};
    /** Сдвиг от точки (riv_strip — перекрёсток: вдоль улиц стен нет, ищем от середины квартала). */
    private static final int[][] SHIFT = {{0, 0}, {0, 24}, {0, 0}};

    private static double[] anchor(int i) {
        double[] pl = Island.PLACES.get(ANCHORS[i]);
        return pl == null ? null : new double[]{pl[0] + SHIFT[i][0], 0, pl[2] + SHIFT[i][1]};
    }
    public static final int W = 9, H = 5;

    static final class State extends PersistentState {
        final Set<String> done = new HashSet<>();
        final List<long[]> screens = new ArrayList<>(); // {центр экрана (блок рамы), сторона}

        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> load(n), null);

        static State load(NbtCompound n) {
            State s = new State();
            for (NbtElement e : n.getList("done", NbtElement.STRING_TYPE)) s.done.add(e.asString());
            for (NbtElement e : n.getList("screens", NbtElement.COMPOUND_TYPE)) {
                NbtCompound c = (NbtCompound) e;
                s.screens.add(new long[]{c.getLong("b"), c.getInt("f")});
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            NbtList d = new NbtList();
            for (String s : done) d.add(net.minecraft.nbt.NbtString.of(s));
            n.put("done", d);
            NbtList l = new NbtList();
            for (long[] s : screens) {
                NbtCompound c = new NbtCompound();
                c.putLong("b", s[0]);
                c.putInt("f", (int) s[1]);
                l.add(c);
            }
            n.put("screens", l);
            return n;
        }
    }

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_newstv");
    }

    public static void tick(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 40 == 13) {
            State st = state(s);
            ServerWorld w = s.getOverworld();
            for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                if (p.getServerWorld() != w) continue;
                for (int ai = 0; ai < ANCHORS.length; ai++) {
                    String a = ANCHORS[ai];
                    double[] pl = anchor(ai);
                    if (pl == null || st.done.contains(a)) continue;
                    if (Math.hypot(pl[0] - p.getX(), pl[2] - p.getZ()) > 60) continue;
                    if (!loadedAround(w, (int) pl[0], (int) pl[2], 48)) continue;
                    // дома вокруг могут достраиваться позже — несколько попыток, отдельный экран на опорах — только под конец
                    int tries = TRIES.merge(a, 1, Integer::sum);
                    long[] scr = build(w, (int) Math.floor(pl[0]), (int) Math.floor(pl[2]), tries >= 60);
                    if (scr == null && tries < 90) continue;
                    st.done.add(a);
                    if (scr != null) st.screens.add(scr);
                    st.markDirty();
                    for (ServerPlayerEntity q : s.getPlayerManager().getPlayerList()) sendList(q);
                }
            }
        }
        if (t % 200 == 17) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) sendFeed(p);
    }

    private static boolean loadedAround(ServerWorld w, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += 16)
            for (int dz = -r; dz <= r; dz += 16)
                if (!w.getChunkManager().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
        return true;
    }

    private static boolean solid(ServerWorld w, BlockPos b) {
        var st = w.getBlockState(b);
        return !st.getCollisionShape(w, b).isEmpty() && st.isFullCube(w, b); // бетон, кирпич, стекло фасада
    }

    private static boolean free(ServerWorld w, BlockPos b) {
        return w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    /** Ищем глухую стену 11×7 (на высоте 4–10 над землёй) в 6–34 блоках от точки; вешаем экран. */
    private static final Map<String, Integer> TRIES = new HashMap<>();

    private static long[] build(ServerWorld w, int x, int z, boolean standalone) {
        int y = Island.surface(x, z) + 1;
        BlockPos at = new BlockPos(x, y, z);
        BlockPos bestWall = null;
        Direction bestDir = null;
        int bestK = 999;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Direction side = dir.rotateYClockwise();
            for (int k = 6; k <= 44 && k < bestK; k++) {
                BlockPos c = at.offset(dir, k);
                if (!solid(w, c.up(7))) continue;
                if (!solid(w, c.offset(side, 3).up(7)) || !solid(w, c.offset(side, -3).up(7))) continue; // ствол пальмы, столб — не стена
                int good = 0; // фасад с окнами и вывесками: хватит 85% сплошной стены
                for (int u = -5; u <= 5; u++)
                    for (int v = 4; v <= 10; v++) {
                        BlockPos b = c.offset(side, u).up(v);
                        if (solid(w, b) && free(w, b.offset(dir.getOpposite()))) good++;
                    }
                if (good >= 66) {
                    bestK = k;
                    bestWall = c;
                    bestDir = dir;
                }
                break; // первая стена в этом направлении — либо подходит, либо нет
            }
        }
        BlockState frame = Blocks.POLISHED_BLACKSTONE.getDefaultState(), screen = Blocks.BLACK_CONCRETE.getDefaultState(),
                lamp = Blocks.SEA_LANTERN.getDefaultState();
        if (bestWall != null) {
            Direction face = bestDir.getOpposite();
            Direction side = bestDir.rotateYClockwise();
            BlockPos front = bestWall.offset(face);
            for (int u = -5; u <= 5; u++)
                for (int v = 4; v <= 10; v++) {
                    boolean edge = Math.abs(u) == 5 || v == 4 || v == 10;
                    boolean corner = Math.abs(u) == 5 && (v == 4 || v == 10);
                    w.setBlockState(front.offset(side, u).up(v), corner ? lamp : edge ? frame : screen, 2);
                }
            return new long[]{front.up(7).asLong(), face.getId()};
        }
        // стены нет — отдельно стоящий экран на двух опорах, лицом к точке
        if (!standalone) return null;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Direction side = dir.rotateYClockwise();
            BlockPos c = at.offset(dir, 7);
            c = new BlockPos(c.getX(), y, c.getZ());
            boolean ok = true;
            for (int u : new int[]{-4, 0, 4}) if (Island.road(c.offset(side, u).getX(), c.offset(side, u).getZ())) ok = false; // не на проезжей части
            for (int u = -5; u <= 5 && ok; u++)
                for (int v = 0; v <= 10 && ok; v++) ok = free(w, c.offset(side, u).up(v)) && !free(w, c.offset(side, u).down());
            if (!ok) continue;
            Direction face = dir.getOpposite();
            for (int v = 0; v < 4; v++) for (int u : new int[]{-4, 4}) w.setBlockState(c.offset(side, u).up(v), frame, 2);
            for (int u = -5; u <= 5; u++)
                for (int v = 4; v <= 10; v++) {
                    boolean edge = Math.abs(u) == 5 || v == 4 || v == 10;
                    boolean corner = Math.abs(u) == 5 && (v == 4 || v == 10);
                    w.setBlockState(c.offset(side, u).up(v), corner ? lamp : edge ? frame : screen, 2);
                }
            return new long[]{c.up(7).asLong(), face.getId()};
        }
        return null;
    }

    public static void onJoin(ServerPlayerEntity p) {
        sendList(p);
        sendFeed(p);
    }

    static void sendList(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (long[] s : state(p.getServer()).screens) {
            NbtCompound c = new NbtCompound();
            c.putLong("b", s[0]);
            c.putInt("f", (int) s[1]);
            l.add(c);
        }
        n.put("list", l);
        Pulse.send(p, "tv_list", n);
    }

    /** Что показывают экраны: последние заголовки, биржа, погода; для игрока в розыске — сводка погони. */
    static void sendFeed(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        NbtCompound n = new NbtCompound();
        NbtList h = new NbtList();
        for (String[] f : News.latest(6)) {
            NbtCompound c = new NbtCompound();
            c.putString("h", f[0]);
            c.putString("t", f[1]);
            c.putString("c", f[2]);
            h.add(c);
        }
        int stars = Wanted.stars(p);
        if (stars > 0) {
            NbtCompound c = new NbtCompound();
            String zone = zoneName(Island.zone(p.getBlockX(), p.getBlockZ()));
            c.putString("h", "ПРЯМОЙ ЭФИР: полиция преследует подозреваемого — " + zone + ". Уровень угрозы: " + "★".repeat(stars));
            c.putString("t", "");
            c.putString("c", "ЭКСТРЕННО");
            h.add(0, c);
        }
        if (h.isEmpty()) {
            NbtCompound c = new NbtCompound();
            c.putString("h", "Новоград-24: круглосуточные новости острова. Смотрите нас на всех экранах города");
            c.putString("t", "");
            c.putString("c", "ГОРОД");
            h.add(c);
        }
        n.put("head", h);
        Economy.Market m = Economy.market(s);
        StringBuilder sb = new StringBuilder();
        for (String[] st : Economy.STOCKS) {
            double price = m.price.getOrDefault(st[0], 0.0);
            Deque<Double> hist = m.history.get(st[0]);
            double first = hist == null || hist.isEmpty() ? Double.parseDouble(st[2]) : hist.peekFirst();
            double pct = first == 0 ? 0 : (price - first) / first * 100;
            sb.append(st[0]).append(' ').append(String.format(Locale.ROOT, "%.1f", price)).append(pct >= 0 ? " ▲" : " ▼")
                    .append(String.format(Locale.ROOT, "%.1f%%", Math.abs(pct))).append("   ");
        }
        n.putString("stocks", sb.toString().trim());
        ServerWorld w = s.getOverworld();
        long tod = w.getTimeOfDay() % 24000;
        int hh = (int) ((tod / 1000 + 6) % 24), mm = (int) (tod % 1000 * 60 / 1000);
        boolean night = tod > 13000 && tod < 23000;
        String sky = w.isThundering() ? "⛈ гроза" : w.isRaining() ? "☂ дождь" : night ? "☾ ясно" : "☀ солнечно";
        int temp = (w.isRaining() ? 17 : 24) + (night ? -5 : 2) + (int) (Life.day(p) % 4);
        n.putString("weather", sky + "  +" + temp + "°");
        n.putString("time", String.format("%02d:%02d", hh, mm));
        Pulse.send(p, "tv", n);
    }

    static String zoneName(Island.Zone z) {
        return switch (z) {
            case DOWNTOWN -> "деловой центр";
            case INDUSTRIAL -> "промзона";
            case SUBURB -> "пригород";
            case PORT -> "порт";
            case BEACH -> "побережье";
            case CORE -> "старый город";
            case AIRFIELD -> "аэродром";
            case RIVIERA -> "Ривьера";
            case VILLAGE -> "Сосновка";
            case MIRAGE -> "Мираж";
            case SEA -> "море";
            default -> "трасса за городом";
        };
    }

    /** Для автотеста: встать в 16 блоках перед экраном i и смотреть на него. */
    public static String debugView(ServerPlayerEntity p, int i) {
        List<long[]> l = state(p.getServer()).screens;
        if (i >= l.size()) return "Экранов: " + l.size();
        BlockPos b = BlockPos.fromLong(l.get(i)[0]);
        Direction f = Direction.byId((int) l.get(i)[1]);
        ServerWorld w = p.getServerWorld();
        int x = b.getX() + f.getOffsetX() * 16, z = b.getZ() + f.getOffsetZ() * 16;
        float yaw = f.getOpposite().asRotation();
        p.teleport(w, x + 0.5, Story.topY(w, x, z), z + 0.5, yaw, -12);
        return "Экран " + i + " у " + b.toShortString() + " лицом " + f;
    }

    public static String debugTp(ServerPlayerEntity p, int i) {
        int ai = Math.max(0, Math.min(ANCHORS.length - 1, i));
        String a = ANCHORS[ai];
        double[] pl = anchor(ai);
        ServerWorld w = p.getServerWorld();
        int x = (int) pl[0], z = (int) pl[2];
        w.getChunk(x >> 4, z >> 4);
        p.teleport(w, pl[0], Story.topY(w, x, z), pl[2], p.getYaw(), 0);
        return "Экран у " + a + "; экранов в мире: " + state(p.getServer()).screens.size();
    }
}
