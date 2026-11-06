package com.yompix.novograd.gta;

import com.mojang.serialization.Codec;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.item.GunItem;
import com.yompix.novograd.life.Life;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * «Калибр» → «Мастерская»: обвесы и окраска ствола. Всё хранится в самом оружии (компонент novograd:gun_mods):
 * биты 0..3 — глушитель, прицел, удлинённый магазин, рукоятка; биты 8..11 — окраска (0 заводская, 1 камуфляж, 2 неон, 3 золото).
 * <ul>
 * <li>Глушитель: тихий выстрел, маленькая вспышка; звёзд за стрельбу нет, если никто не видел (полиция ~24 м, прохожие ~10 м).</li>
 * <li>Прицел: коллиматор или оптика ×2 — сильнее зум, точка прицеливания поднята.</li>
 * <li>Удлинённый магазин: +50% патронов. Рукоятка: меньше отдача и разброс.</li>
 * </ul>
 * Платят как у оружейника: сначала грязными наличными, потом с карты. Часть обвесов — по ходу сюжета.
 */
public final class GunMods {
    private GunMods() {}

    public static final int SILENCER = 1, SCOPE = 2, EXTMAG = 4, GRIP = 8;
    public static final int[] PARTS = {SILENCER, SCOPE, EXTMAG, GRIP};
    public static final String[] PART_NAMES = {"Глушитель", "Прицел", "Удлинённый магазин", "Рукоятка"};
    public static final String[] PART_INFO = {
            "Тихий выстрел. Без свидетелей — без звёзд.",
            "Коллиматор или оптика: зум ×2, точнее в прицеле.",
            "+50% патронов в магазине.",
            "Меньше отдача и разброс."};
    public static final int[] PART_PRICE = {4000, 3000, 2500, 2000};
    /** С какого акта (Economy.act) Боб ставит обвес. */
    public static final int[] PART_ACT = {1, 0, 0, 0};
    public static final String[] SKIN_NAMES = {"Заводская", "Камуфляж", "Неон", "Золото"};
    public static final String[] SKIN_IDS = {"", "camo", "neon", "gold"};
    public static final int[] SKIN_PRICE = {0, 1500, 6000, 15000};
    public static final int[] SKIN_ACT = {0, 0, 1, 2};

    public static final ComponentType<Integer> MODS = Registry.register(Registries.DATA_COMPONENT_TYPE, Novograd.id("gun_mods"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

    public static void register() {}

    public static int get(ItemStack s) {
        return s.isEmpty() ? 0 : s.getOrDefault(MODS, 0);
    }

    public static boolean has(ItemStack s, int part) {
        return (get(s) & part) != 0;
    }

    public static int skin(ItemStack s) {
        return (get(s) >> 8) & 15;
    }

    public static String skinId(ItemStack s) {
        int k = skin(s);
        return k > 0 && k < SKIN_IDS.length ? SKIN_IDS[k] : "";
    }

    /** Какие обвесы встают на ствол (по модели: куда крепить). */
    public static int allowed(String gun) {
        return switch (gun) {
            case "pistol", "eagle" -> SILENCER | SCOPE | EXTMAG;
            case "micro" -> SILENCER | SCOPE | EXTMAG;
            case "smg", "rifle", "carbine" -> SILENCER | SCOPE | EXTMAG | GRIP;
            case "saiga", "lmg" -> SCOPE | EXTMAG | GRIP;
            case "sniper" -> SILENCER | EXTMAG;
            case "shotgun" -> EXTMAG;
            case "revolver" -> SCOPE;
            default -> 0;
        };
    }

    public static int magSize(GunItem g, ItemStack s) {
        int m = g.spec().magazine();
        return has(s, EXTMAG) && (allowed(g.gunId()) & EXTMAG) != 0 ? m + Math.max(1, m / 2) : m;
    }

    public static boolean silenced(ItemStack s) {
        return s.getItem() instanceof GunItem g && has(s, SILENCER) && (allowed(g.gunId()) & SILENCER) != 0;
    }

    public static String describe(ItemStack s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < PARTS.length; i++) if (has(s, PARTS[i])) b.append(b.length() > 0 ? ", " : "").append(PART_NAMES[i].toLowerCase());
        if (skin(s) > 0) b.append(b.length() > 0 ? ", " : "").append("окраска «").append(SKIN_NAMES[skin(s)]).append("»");
        return b.toString();
    }

    /**
     * Мастерская (Arsenal.C2S «mod»): value = слот·256 + код. Код 0..3 — поставить/снять обвес, 10..13 — окраска.
     * Работает только у Боба в «Калибре» (окно открыто недавно).
     */
    public static void handle(ServerPlayerEntity p, int value) {
        int slot = value >> 8, code = value & 255;
        var inv = p.getInventory();
        if (slot < 0 || slot >= inv.main.size()) return;
        ItemStack s = inv.getStack(slot);
        if (!(s.getItem() instanceof GunItem g)) return;
        if (!com.yompix.novograd.life.Shop.atGunShop(p)) {
            p.sendMessage(Text.literal("Мастерская — только в «Калибре», у Боба").formatted(Formatting.RED), true);
            return;
        }
        int act = Economy.act(Life.get(p));
        int mods = get(s);
        if (code < PARTS.length) {
            int part = PARTS[code];
            if ((allowed(g.gunId()) & part) == 0) return;
            if ((mods & part) != 0) { // снять — бесплатно
                s.set(MODS, mods & ~part);
                if (part == EXTMAG) s.set(Arsenal.MAG, Math.min(g.mag(s), g.magSize(s)));
                p.sendMessage(Text.literal("Боб снял: " + PART_NAMES[code].toLowerCase()).formatted(Formatting.GRAY), true);
                return;
            }
            if (act < PART_ACT[code]) {
                p.sendMessage(Text.literal("Оружейник Боб: «Глушители — только своим. Приходи, когда тебя узнают (акт " + Armory.roman(PART_ACT[code]) + ").»").formatted(Formatting.GOLD), false);
                return;
            }
            if (!Economy.payBlack(p, PART_PRICE[code], PART_NAMES[code])) return;
            s.set(MODS, mods | part);
            Activities.title(p, "МАСТЕРСКАЯ", PART_NAMES[code] + " — " + s.getName().getString());
        } else if (code >= 10 && code < 10 + SKIN_NAMES.length) {
            int k = code - 10;
            if (k == skin(s)) return;
            if (act < SKIN_ACT[k]) {
                p.sendMessage(Text.literal("Оружейник Боб: «" + SKIN_NAMES[k] + " — это для серьёзных людей (акт " + Armory.roman(SKIN_ACT[k]) + ").»").formatted(Formatting.GOLD), false);
                return;
            }
            if (SKIN_PRICE[k] > 0 && !Economy.payBlack(p, SKIN_PRICE[k], "окраска «" + SKIN_NAMES[k] + "»")) return;
            s.set(MODS, (mods & ~(15 << 8)) | (k << 8));
            if (k == 3) {
                Activities.title(p, "ЗОЛОТОЙ СТВОЛ", s.getName().getString() + " — весь город узнает");
                News.post(p.getServer(), "В Новограде видели золотое оружие — «" + s.getName().getString() + "». Полиция: «Это вызов»", "КРИМИНАЛ");
            } else Activities.title(p, "МАСТЕРСКАЯ", "Окраска «" + SKIN_NAMES[k] + "» — " + s.getName().getString());
        } else return;
        Life.sound(p, com.yompix.novograd.ModContent.CASH, 1.1f);
        p.playerScreenHandler.sendContentUpdates();
    }

    /**
     * Выстрел с глушителем: прохожие пугаются только совсем рядом; розыск — если видела полиция (до 24 м)
     * или кто-то из прохожих (до 10 м) смотрит прямо на стрелка.
     */
    public static void quietShot(ServerWorld w, LivingEntity shooter, Vec3d at) {
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, shooter.getBoundingBox().expand(6), c -> c != shooter)) c.panic(at);
        if (!(shooter instanceof ServerPlayerEntity p) || p.isCreative()) return;
        boolean cop = Wanted.policeSees(w, p, 24);
        boolean witness = cop || !w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(10),
                c -> c.isAlive() && c.getRole() != CitizenEntity.Role.GANG && !(c instanceof com.yompix.novograd.entity.BodyguardEntity) && c.canSee(p)).isEmpty();
        if (witness) Wanted.add(p, 0, cop ? "стрельба на глазах у полиции" : "свидетели видели стрельбу", cop ? 2 : 1);
    }
}
