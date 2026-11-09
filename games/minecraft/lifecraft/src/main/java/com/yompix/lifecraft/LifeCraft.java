package com.yompix.lifecraft;

import com.yompix.lifecraft.life.LifeNet;
import com.yompix.lifecraft.life.LifeEvents;
import com.yompix.lifecraft.world.LifeCommands;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifeCraft implements ModInitializer {
    public static final String MOD_ID = "lifecraft";
    public static final Logger LOG = LoggerFactory.getLogger("LifeCraft");

    /** Мостик к клиентскому коду: общий код не может напрямую открыть экран. */
    public static ClientHooks CLIENT = new ClientHooks() {};

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        LifeNet.init();
        LifeEvents.init();
        LifeCommands.init();
        LOG.info("LifeCraft загружен: школа, работа, деньги, машины и город");
    }
}
