package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CitizenEntity;
import com.yompix.lifecraft.story.Story;
import com.yompix.lifecraft.world.TownState;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Городская жизнь: потребности (энергия, настроение), праздники по дням (ярмарка, концерт, салют) и покупка домов.
 */
public final class CityLife {
    private CityLife() {}

    public static final String[] EVENTS = {"", "fair", "concert", "fireworks"};
    public static final String[][] HOUSES = {{"house4", "Дом на ул. Садовой", "2500"}, {"house6", "Дом на ул. Победы", "3000"}, {"gleb", "Дом по соседству с родителями", "2000"}};

    /** Какой праздник сегодня (по номеру дня). */
    public static String event(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000 + 1;
        return day < 3 ? "" : EVENTS[(int) (day % EVENTS.length)];
    }

    public static float shopDiscount(ServerWorld w) {
        return event(w).equals("fair") ? 0.7f : 1f;
    }

    private static long lastAnnounceDay = -1;
    private static boolean concertDone, fireworksDone;

    /** Раз в секунду для мира. */
    public static void worldTick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        long day = w.getTimeOfDay() / 24000 + 1, tod = w.getTimeOfDay() % 24000;
        String ev = event(w);
        if (tod >= 1000 && lastAnnounceDay != day) {
            lastAnnounceDay = day;
            concertDone = fireworksDone = false;
            String text = switch (ev) {
                case "fair" -> "Сегодня в городе ярмарка: в магазине скидка 30% на всё!";
                case "concert" -> "Вечером в 18:00 в парке концерт! Приходите танцевать!";
                case "fireworks" -> "Сегодня День города! В 21:00 над школой — праздничный салют!";
                default -> "";
            };
            if (!text.isEmpty()) for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) Life.message(p, "Новоград-гид", text);
        }
        Vec3d park = Story.place(w, "park_center"), school = Story.place(w, "school_yard");
        // мячи во дворе школы и в парке: укатился — появится новый
        if (tod % 1200 < 20) for (Vec3d at : new Vec3d[]{school, park}) {
            if (at != null && w.getEntitiesByClass(com.yompix.lifecraft.entity.BallEntity.class, new Box(BlockPos.ofFloored(at)).expand(30), e -> true).isEmpty()
                    && w.isChunkLoaded(BlockPos.ofFloored(at))) com.yompix.lifecraft.world.TownBuilder.ball(w, BlockPos.ofFloored(at).up());
        }
        if (ev.equals("concert") && tod >= 12000 && tod < 12100 && !concertDone && park != null) {
            concertDone = true;
            Random r = w.getRandom();
            for (int i = 0; i < 8; i++) {
                CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
                c.setup(CitizenEntity.randomSkin(r), CitizenEntity.Role.CITIZEN, true, Names.random(r));
                c.randomizeBody(r, r.nextBoolean());
                BlockPos g = BlockPos.ofFloored(park.add(-6 + r.nextInt(12), 0, -6 + r.nextInt(6)));
                c.refreshPositionAndAngles(g.getX() + 0.5, Story.topY(w, g.getX(), g.getZ()), g.getZ() + 0.5, r.nextFloat() * 360, 0);
                c.lockActivity(CitizenEntity.Activity.DANCE);
                c.removeLater(20 * 60 * 3);
                w.spawnEntity(c);
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.getPos().distanceTo(park) < 80) {
                    p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(SoundEvents.MUSIC_DISC_OTHERSIDE,
                            SoundCategory.RECORDS, park.x, park.y, park.z, 3f, 1f, r.nextLong()));
                    Achievements.give(p, "event");
                }
            }
        }
        if (ev.equals("fireworks") && tod >= 15000 && tod < 16200 && school != null) {
            if (!fireworksDone) {
                fireworksDone = true;
                for (ServerPlayerEntity p : w.getPlayers()) if (p.getPos().distanceTo(school) < 120) Achievements.give(p, "event");
            }
            Random r = w.getRandom();
            for (int i = 0; i < 2; i++) {
                ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                int[] palette = {0xFF3B30, 0xFFCC00, 0x34C759, 0x1E88E5, 0xAF52DE, 0xFFFFFF, 0xFF8A00};
                FireworkExplosionComponent ex = new FireworkExplosionComponent(FireworkExplosionComponent.Type.values()[r.nextInt(5)],
                        IntList.of(palette[r.nextInt(palette.length)], palette[r.nextInt(palette.length)]), IntList.of(palette[r.nextInt(palette.length)]), r.nextBoolean(), r.nextBoolean());
                rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1 + r.nextInt(2), List.of(ex)));
                FireworkRocketEntity e = new FireworkRocketEntity(w, school.x - 12 + r.nextInt(24), school.y + 1, school.z - 18 + r.nextInt(14), rocket);
                w.spawnEntity(e);
            }
        }
    }

    /** Раз в секунду для игрока: энергия и настроение. */
    public static void playerTick(ServerPlayerEntity p, int second) {
        LifeData d = Life.get(p);
        boolean changed = false;
        if (p.isSleeping()) {
            if (d.energy < 100) {
                d.energy = Math.min(100, d.energy + 4);
                changed = true;
            }
        } else if (second % 30 == 0 && d.energy > 0) {
            d.energy--;
            changed = true;
        }
        if (second % 10 == 0) {
            // телевизор и компания поднимают настроение
            boolean tv = BlockPos.stream(new Box(p.getBlockPos()).expand(6, 3, 6)).anyMatch(b -> p.getWorld().getBlockState(b).isOf(ModContent.TV)
                    && p.getWorld().getBlockState(b).get(com.yompix.lifecraft.block.TvBlock.CHANNEL) > 0);
            if (tv && d.mood < 100) {
                d.mood = Math.min(100, d.mood + 2);
                changed = true;
            } else if (second % 60 == 0 && d.mood > 40) {
                d.mood--;
                changed = true;
            }
        }
        if (d.energy < 12 && second % 5 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 0, true, false, true));
            if (second % 60 == 0) p.sendMessage(Text.literal("Ты очень устал. Поспи или выпей колы!").formatted(Formatting.YELLOW), true);
        }
        if (changed) Life.sync(p);
    }

    public static void buyHouse(ServerPlayerEntity p, String id) {
        for (String[] h : HOUSES) {
            if (!h[0].equals(id)) continue;
            LifeData d = Life.get(p);
            if (d.flags.contains("house:" + id)) return;
            if (!d.adult()) {
                p.sendMessage(Text.literal("Покупать дома можно только взрослым").formatted(Formatting.RED), true);
                return;
            }
            if (!Life.pay(p, Integer.parseInt(h[2]), h[1])) return;
            d.flags.add("house:" + id);
            double[] bed = TownState.get(p.getServerWorld()).place(id + "_bed");
            if (bed != null) p.setSpawnPoint(p.getServerWorld().getRegistryKey(), BlockPos.ofFloored(bed[0], bed[1], bed[2]), 0, true, false);
            Life.message(p, "Риелтор", "Поздравляем с покупкой: " + h[1] + "! Теперь вы появляетесь там после сна.");
            Achievements.give(p, "house");
            NbtCompound n = new NbtCompound();
            LifeNet.send(p, "noop", n);
            return;
        }
    }

    /** Кола и бургеры бодрят. */
    public static void ate(ServerPlayerEntity p, boolean cola) {
        LifeData d = Life.get(p);
        d.energy = Math.min(100, d.energy + (cola ? 15 : 8));
        d.mood = Math.min(100, d.mood + 2);
        Life.sync(p);
    }
}
