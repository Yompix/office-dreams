package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Чит-коды как в San Andreas: набираешь буквы прямо во время игры (без чата) — «ЧИТ АКТИВИРОВАН».
 * Работают классические коды SA и свои, русские (латиницей). Переключатели (бессмертие, без розыска,
 * бесконечные патроны, суперпрыжок) включаются и выключаются тем же кодом.
 */
public final class Cheats {
    private Cheats() {}

    /** Код → описание (для подсказки в телефоне/README). Сами эффекты — в apply(). */
    public static final Map<String, String> LIST = new LinkedHashMap<>();

    static {
        LIST.put("HESOYAM", "здоровье, броня и $250 000");
        LIST.put("BAGUVIX", "бессмертие (вкл/выкл)");
        LIST.put("AEZAKMI", "полиция не ищет (вкл/выкл)");
        LIST.put("ASNAEB", "снять розыск");
        LIST.put("OSRBLHH", "+2 звезды");
        LIST.put("LJSPQK", "5 звёзд");
        LIST.put("LXGIWYL", "оружие: пистолет, дробовик, ПП");
        LIST.put("UZUMYMW", "оружие: автомат, снайперка, гранаты");
        LIST.put("FULLCLIP", "бесконечные патроны (вкл/выкл)");
        LIST.put("KANGAROO", "суперпрыжок (вкл/выкл)");
        LIST.put("CVWKXAM", "дышать под водой");
        LIST.put("AIYPWZQP", "парашют");
        LIST.put("OHDUDE", "вертолёт");
        LIST.put("JUMPJET", "самолёт");
        LIST.put("FOOOXFT", "мотоцикл");
        LIST.put("RIPAZHA", "спорткар с нитро");
        LIST.put("CPKTNWT", "взорвать все машины вокруг");
        LIST.put("AFZLLQLL", "солнце");
        LIST.put("AUIFRVQS", "ливень");
        LIST.put("NIGHTPROWLER", "ночь");
        LIST.put("BOGACH", "+$50 000");
        LIST.put("TACHKA", "спорткар с нитро");
    }

    private static boolean toggle(ServerPlayerEntity p, String tag) {
        if (p.getCommandTags().contains(tag)) {
            p.removeCommandTag(tag);
            return false;
        }
        p.addCommandTag(tag);
        return true;
    }

    private static void give(ServerPlayerEntity p, net.minecraft.item.Item item, int n) {
        ItemStack s = new ItemStack(item, n);
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    private static void spawn(ServerPlayerEntity p, Entity e) {
        double rad = Math.toRadians(p.getYaw());
        e.refreshPositionAndAngles(p.getX() - Math.sin(rad) * 5, p.getY() + 0.5, p.getZ() + Math.cos(rad) * 5, p.getYaw(), 0);
        p.getServerWorld().spawnEntity(e);
    }

    /** null — такого кода нет. */
    public static String apply(ServerPlayerEntity p, String raw) {
        String code = raw.trim().toUpperCase(Locale.ROOT);
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        String r = switch (code) {
            case "HESOYAM" -> {
                p.setHealth(p.getMaxHealth());
                give(p, Arsenal.ARMOR, 1);
                d.money += 250_000;
                Life.sync(p);
                yield "Здоровье, броня, $250 000";
            }
            case "BAGUVIX" -> toggle(p, "cheat_god") ? "Бессмертие ВКЛ" : "Бессмертие выкл";
            case "AEZAKMI" -> {
                boolean on = toggle(p, "cheat_nowanted");
                if (on) Wanted.clear(p);
                yield on ? "Полиция тебя не ищет" : "Розыск снова работает";
            }
            case "ASNAEB", "TURNDOWNHEATWAVE", "TISHINA" -> { Wanted.clear(p); yield "Розыск снят"; }
            case "OSRBLHH" -> { Wanted.add(p, 2, "чит-код", 0); yield "+2 звезды"; }
            case "LJSPQK", "HAOS" -> { Wanted.add(p, 5, "чит-код", 5); yield "★★★★★"; }
            case "LXGIWYL", "ARSENAL" -> {
                give(p, ModContent.PISTOL, 1);
                give(p, Arsenal.SHOTGUN, 1);
                give(p, ModContent.SMG, 1);
                give(p, ModContent.AMMO, 64);
                give(p, ModContent.AMMO, 64);
                yield "Оружие: набор 1";
            }
            case "UZUMYMW", "PROFESSIONALSKIT" -> {
                give(p, Arsenal.RIFLE, 1);
                give(p, Arsenal.SNIPER, 1);
                give(p, Arsenal.GRENADE, 8);
                give(p, ModContent.AMMO, 64);
                give(p, ModContent.AMMO, 64);
                yield "Оружие: набор 3";
            }
            case "FULLCLIP" -> toggle(p, "cheat_ammo") ? "Бесконечные патроны ВКЛ" : "Бесконечные патроны выкл";
            case "KANGAROO" -> toggle(p, "cheat_jump") ? "Суперпрыжок ВКЛ" : "Суперпрыжок выкл";
            case "CVWKXAM" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 20 * 60 * 20, 0, true, false, true));
                yield "Дыхание под водой";
            }
            case "AIYPWZQP" -> { give(p, Skydive.PARACHUTE, 1); yield "Парашют"; }
            case "OHDUDE" -> { spawn(p, new com.yompix.novograd.vehicle.HeliEntity(com.yompix.novograd.vehicle.Vehicles.HELI, w)); yield "Вертолёт"; }
            case "JUMPJET" -> { spawn(p, new com.yompix.novograd.vehicle.PlaneEntity(com.yompix.novograd.vehicle.Vehicles.PLANE, w)); yield "Самолёт"; }
            case "FOOOXFT" -> { spawn(p, new CarEntity(com.yompix.novograd.vehicle.Vehicles.BIKE, w)); yield "Мотоцикл"; }
            case "RIPAZHA", "TACHKA" -> {
                CarEntity car = new CarEntity(ModContent.CAR, w);
                car.setVariant(9);
                car.setTune(true, 2, 2);
                spawn(p, car);
                yield "Спорткар с нитро";
            }
            case "CPKTNWT" -> {
                int n = 0;
                for (CarEntity c : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(60), c -> c != p.getVehicle() && c.getPassengerList().stream().noneMatch(e -> e instanceof net.minecraft.entity.player.PlayerEntity))) {
                    c.hitByBullet(999, null);
                    n++;
                }
                yield "Взорвано машин: " + n;
            }
            case "AFZLLQLL", "SOLNCE" -> { w.setWeather(6000, 0, false, false); yield "Солнце"; }
            case "AUIFRVQS", "LIVEN" -> { w.setWeather(0, 6000, true, false); yield "Ливень"; }
            case "NIGHTPROWLER", "NOCH" -> { w.setTimeOfDay(18000); yield "Ночь"; }
            case "DEN" -> { w.setTimeOfDay(6000); yield "День"; }
            case "BOGACH" -> { d.money += 50000; Life.sync(p); yield "+$50 000"; }
            case "ZDOROV" -> { p.setHealth(p.getMaxHealth()); yield "Здоровье полное"; }
            default -> null;
        };
        if (r != null) {
            p.sendMessage(Text.literal("ЧИТ АКТИВИРОВАН · " + r).formatted(Formatting.YELLOW), true);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 0.6f);
        }
        return r;
    }

    /** Раз в секунду: действуют включённые переключатели. */
    public static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            var tags = p.getCommandTags();
            if (tags.contains("cheat_god")) {
                p.setHealth(p.getMaxHealth());
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 4, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false, false));
            }
            if (tags.contains("cheat_nowanted")) Wanted.clear(p);
            if (tags.contains("cheat_jump")) p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 4, true, false, false));
        }
    }

    public static boolean infiniteAmmo(net.minecraft.entity.player.PlayerEntity p) {
        return p.getCommandTags().contains("cheat_ammo");
    }
}
