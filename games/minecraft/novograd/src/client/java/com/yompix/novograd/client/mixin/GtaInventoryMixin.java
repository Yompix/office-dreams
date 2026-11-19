package com.yompix.novograd.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Клавиша E (и всё, что открывает обычный инвентарь игрока) — наш инвентарь в стиле города. Творческий режим — как был. */
@Mixin(MinecraftClient.class)
public class GtaInventoryMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen novograd$gtaInventory(Screen screen) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (screen != null && screen.getClass() == InventoryScreen.class && mc.player != null && !mc.player.isCreative()
                && !Boolean.getBoolean("novograd.vanillaInventory"))
            return new com.yompix.novograd.client.screen.GtaInventoryScreen(mc.player);
        // экран смерти — «ПОТРАЧЕНО» в стиле города (кнопки и возрождение те же)
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screen.DeathScreen.class) {
            var a = (DeathScreenAccessor) screen;
            return new com.yompix.novograd.client.screen.GtaDeathScreen(a.novograd$message(), a.novograd$hardcore());
        }
        return screen;
    }
}
