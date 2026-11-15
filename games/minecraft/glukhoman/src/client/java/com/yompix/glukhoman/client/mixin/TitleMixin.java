package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.menu.IntroScreen;
import com.yompix.glukhoman.client.menu.TitleMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Вместо ванильного главного меню — своё; при запуске — заставка (полная раз за запуск, потом двухсекундная). */
@Mixin(MinecraftClient.class)
public class TitleMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void glukhoman$title(Screen screen, CallbackInfo ci) {
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screen.GameMenuScreen.class && ((net.minecraft.client.gui.screen.GameMenuScreen) screen).shouldShowMenu()
                && com.yompix.glukhoman.client.GConfig.get().kitUi) {
            ci.cancel();   // своя пауза (этап 5)
            ((MinecraftClient) (Object) this).setScreen(new com.yompix.glukhoman.client.ui.PauseScreen());
            return;
        }
        if (screen != null && screen.getClass() == net.minecraft.client.gui.screen.DeathScreen.class && com.yompix.glukhoman.client.GConfig.get().kitUi) {
            ci.cancel();   // «плёнка оборвалась» вместо «вы погибли»
            ((MinecraftClient) (Object) this).setScreen(new com.yompix.glukhoman.client.ui.FailScreen());
            return;
        }
        if (screen == null || screen.getClass() != TitleScreen.class) return;
        String auto = System.getProperty("glukhoman.autotest");
        ci.cancel();
        MinecraftClient c = (MinecraftClient) (Object) this;
        Screen menu = new TitleMenuScreen();
        boolean intro = auto == null || auto.equals("menu");
        Screen first = intro ? new IntroScreen(menu) : menu;
        // заставка студии — один раз за запуск игры, перед заставкой мода (этап 6)
        if (intro && !com.yompix.glukhoman.client.menu.YompixScreen.shown) first = new com.yompix.glukhoman.client.menu.YompixScreen(first);
        c.setScreen(first);
    }
}
