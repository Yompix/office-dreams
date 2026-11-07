package com.yompix.novograd;

import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.LifeEvents;
import com.yompix.novograd.world.LifeCommands;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Novograd implements ModInitializer {
    public static final String MOD_ID = "novograd";
    public static final Logger LOG = LoggerFactory.getLogger("Новоград");

    /** Мостик к клиентскому коду: общий код не может напрямую открыть экран. */
    public static ClientHooks CLIENT = new ClientHooks() {};

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        com.yompix.novograd.vehicle.Vehicles.register(); // мотоцикл, катер, вертолёт, самолёт
        LifeNet.init();
        LifeEvents.init();
        LifeCommands.init();
        com.yompix.novograd.life.Style.init(); // одежда, цирюльня, гардероб, спортзал (стиль SA)
        com.yompix.novograd.world.CasinoBuilder.init(); // зал казино «Золотая корона» на Ривьере
        com.yompix.novograd.world.Elevators.init(); // лифты в небоскрёбах и отелях, сесть на стул/диван
        com.yompix.novograd.world.Housing.init(); // недвижимость, гаражи, жильцы острова, уличные музыканты
        com.yompix.novograd.story.Calls.init(); // звонки и СМС героев между миссиями, дружба и её бонусы
        com.yompix.novograd.world.Props.init(); // автоматы, таксофоны, киоски; ломающиеся фонари, гидранты, стёкла; пожары
        com.yompix.novograd.world.Metro.init(); // метро «Новоград — Ривьера»: эстакада, станции, поезда
        com.yompix.novograd.world.Venues.init(); // кафе и супермаркеты с интерьерами
        com.yompix.novograd.world.DesertCity.init(); // «Мираж» — город казино в Пустоши
        com.yompix.novograd.world.Retrofit.init(); // дописывает новое в старые сохранения
        com.yompix.novograd.gta.Outdoors.init(); // навыки SA, охота и рыбалка, времена года и праздники
        com.yompix.novograd.gta.CopsNet.init(); // умная погоня, маски, тюрьма, «Полицейские и воры»
        LOG.info("Новоград загружен: город, сюжет, машины, оружие");
    }
}
