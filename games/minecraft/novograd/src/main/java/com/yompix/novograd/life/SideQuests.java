package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.TownState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.UUID;

/**
 * Побочные задания: у некоторых жителей над головой «!». Принести покупку, отнести письмо,
 * найти сбежавшего кота. Награда — деньги и настроение.
 */
public final class SideQuests {
    private SideQuests() {}

    record Fetch(String text, Item item, String itemName, int reward) {}

    private static final List<Fetch> FETCH = List.of(
            new Fetch("Ох, так пить хочется… Принесёшь мне колу из супермаркета?", ModContent.COLA, "колу", 12),
            new Fetch("Я с работы, умираю от голода. Купи мне бургер, а?", ModContent.BURGER, "бургер", 18),
            new Fetch("У жены сегодня день рождения, а я забыл цветы! Выручишь?", Items.POPPY, "цветы", 20),
            new Fetch("Бабушка просила хлеба, а я никак не дойду до магазина…", Items.BREAD, "хлеб", 10),
            new Fetch("У дочки день рождения, а я без торта… Выручишь?", Items.CAKE, "торт", 35),
            new Fetch("Принеси аптечку, а? После вчерашней разборки всё болит…", com.yompix.novograd.gta.Arsenal.MEDKIT, "аптечку", 30));

    private static int lastSecond;

    /** Раз в секунду: раздать «!» случайным жителям рядом с игроками. */
    public static void tick(MinecraftServer server, int second) {
        if (second - lastSecond < 90) return;
        lastSecond = second;
        ServerWorld w = server.getOverworld();
        for (ServerPlayerEntity p : w.getPlayers()) {
            List<CitizenEntity> near = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60),
                    c -> c.getRole() == CitizenEntity.Role.CITIZEN && c.getStoryId().isEmpty() && !c.hasVehicle());
            long withQuest = near.stream().filter(CitizenEntity::hasQuest).count();
            if (withQuest < 2 && !near.isEmpty()) near.get(w.getRandom().nextInt(near.size())).setQuest(true);
        }
    }

    /** Нажали на жителя с «!» или на того, кто ждёт выполненное задание. true — обработано. */
    public static boolean talk(ServerPlayerEntity p, CitizenEntity c) {
        LifeData d = Life.get(p);
        if (d.questNpc.equals(c.getUuidAsString())) {
            if (d.questType.equals("fetch")) {
                Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(d.questItem));
                if (School.takeOne(p, item)) complete(p, c, "Спасибо огромное! Держи, это тебе.");
                else say(p, c, "Ну что, принёс " + d.questText + "?");
                return true;
            }
            if (d.questType.equals("cat")) {
                say(p, c, "Пушок ещё не нашёлся? Он рыжий, отзывается на «кис-кис»…");
                return true;
            }
            return false;
        }
        if (!c.hasQuest()) return false;
        if (!d.questType.isEmpty()) {
            say(p, c, "Вижу, ты уже кому-то помогаешь. Заходи потом!");
            return true;
        }
        Random r = p.getRandom();
        int kind = r.nextInt(10);
        NbtCompound n = new NbtCompound();
        String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
        if (kind < 6) {
            Fetch f = FETCH.get(r.nextInt(FETCH.size()));
            d.questType = "fetch";
            d.questItem = net.minecraft.registry.Registries.ITEM.getId(f.item()).toString();
            d.questText = f.itemName();
            d.questReward = f.reward();
            n.put("lines", line(name, f.text() + " Заплачу $" + f.reward() + ".", c));
        } else if (kind < 8) {
            BlockPos target = TownState.get(p.getServerWorld()).randomMailbox(r, p.getBlockPos(), 25);
            if (target == null) return false;
            d.questType = "letter";
            d.questReward = 15 + r.nextInt(10);
            ItemStack letter = new ItemStack(ModContent.PARCEL);
            letter.set(ModContent.DELIVERY_TARGET, target);
            letter.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Письмо: " + TownState.get(p.getServerWorld()).address(target)).formatted(Formatting.AQUA));
            School.give(p, letter);
            d.taskKind = "quest";
            d.taskPos = target;
            d.taskReward = d.questReward;
            n.put("lines", line(name, "Отнесёшь письмо? Адрес на конверте: " + TownState.get(p.getServerWorld()).address(target) + ". Держи $" + d.questReward + " за труды — когда доставишь.", c));
        } else {
            d.questType = "cat";
            d.questReward = 30;
            CatEntity cat = EntityType.CAT.create(p.getServerWorld());
            if (cat == null) return false;
            double a = r.nextDouble() * Math.PI * 2;
            int x = (int) (c.getX() + Math.cos(a) * 35), z = (int) (c.getZ() + Math.sin(a) * 35);
            cat.refreshPositionAndAngles(x + 0.5, com.yompix.novograd.story.Story.topY(p.getServerWorld(), x, z), z + 0.5, 0, 0);
            cat.setCustomName(Text.literal("Пушок").formatted(Formatting.GOLD));
            cat.setCustomNameVisible(true);
            cat.addCommandTag("novograd_lost:" + p.getUuidAsString());
            p.getServerWorld().spawnEntity(cat);
            d.taskKind = "quest";
            d.taskPos = cat.getBlockPos();
            n.put("lines", line(name, "Мой кот Пушок сбежал! Он где-то в городе… Найди его и нажми на него, он сам прибежит домой. Награда $30!", c));
        }
        d.questNpc = c.getUuidAsString();
        c.setQuest(false);
        LifeNet.send(p, "dialog_view", n);
        Life.sync(p);
        return true;
    }

    /** Нажали на сбежавшего кота. */
    public static boolean catFound(ServerPlayerEntity p, Entity e) {
        if (!(e instanceof CatEntity cat) || !cat.getCommandTags().contains("novograd_lost:" + p.getUuidAsString())) return false;
        LifeData d = Life.get(p);
        if (!d.questType.equals("cat")) return false;
        Entity owner = p.getServerWorld().getEntity(UUID.fromString(d.questNpc));
        if (owner != null) cat.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        cat.removeCommandTag("novograd_lost:" + p.getUuidAsString());
        cat.setPersistent();
        if (owner instanceof CitizenEntity c) complete(p, c, "Пушок! Нашёлся! Спасибо тебе, ты настоящий герой!");
        else complete(p, null, "");
        return true;
    }

    /** Письмо доставлено в нужный ящик. */
    public static void letterDelivered(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.questType.equals("letter")) complete(p, null, "");
    }

    private static void complete(ServerPlayerEntity p, CitizenEntity c, String thanks) {
        LifeData d = Life.get(p);
        int reward = d.questReward;
        if (c != null && !thanks.isEmpty()) say(p, c, thanks);
        d.questType = "";
        d.questNpc = "";
        d.questItem = "";
        d.questText = "";
        if (d.taskKind.equals("quest")) {
            d.taskKind = "";
            d.taskPos = null;
        }
        d.questsDone++;
        d.mood = Math.min(100, d.mood + 8);
        Life.earn(p, reward, "помощь жителю");
        if (d.questsDone >= 5) Achievements.give(p, "quest5");
    }

    private static void say(ServerPlayerEntity p, CitizenEntity c, String text) {
        String name = c.hasCustomName() ? c.getCustomName().getString() : "Житель";
        p.sendMessage(Text.literal(name + ": ").formatted(Formatting.YELLOW).append(Text.literal(text).formatted(Formatting.WHITE)), false);
        NbtCompound vn = new NbtCompound();
        vn.putInt("entity", c.getId());
        vn.putString("voice", "");
        LifeNet.send(p, "ambient", vn);
    }

    private static NbtList line(String name, String text, CitizenEntity c) {
        NbtList l = new NbtList();
        NbtCompound ln = new NbtCompound();
        ln.putString("who", "citizen");
        ln.putString("name", name);
        ln.putInt("color", 0xFFD36E);
        ln.putString("text", text);
        ln.putString("voice", "");
        ln.putInt("entity", c.getId());
        l.add(ln);
        return l;
    }
}
