package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.menu.MenuClock;
import com.yompix.glukhoman.client.ui.Kit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Загрузка мира (этап 5): «перемотка плёнки» в стиле интерфейса мода, процент и подсказка вместо ванильной карты чанков. */
@Mixin(LevelLoadingScreen.class)
public class LoadingMixin {
    @Shadow
    @Final
    private WorldGenerationProgressTracker progressProvider;

    private static final String[] TIPS = {"Камера видит то, чего не видно глазами.", "Кассету — в ящик на перроне. Каждое утро.",
            "Колокол на перроне не трогать.", "G — инфракрасный режим камеры.", "Esc — пропустить кат-сцену (в совместной — хозяин или все)."};

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glukhoman$render(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (!com.yompix.glukhoman.client.GConfig.get().kitUi) return;
        ci.cancel();
        var c = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double t = MenuClock.now();
        Kit.backdrop(ctx, w, h, t, false);
        int p = progressProvider == null ? 0 : Math.max(0, Math.min(100, progressProvider.getProgressPercentage()));
        Kit.title(ctx, "Перемотка", w / 2, h / 2 - 40, 1f);
        String arrows = "◀◀".repeat(1 + (int) (t * 3) % 3);
        ctx.drawCenteredTextWithShadow(c.textRenderer, arrows + "  " + p + "%", w / 2, h / 2 - 6, 0xFFE8E2D0);
        int bw = Math.min(260, w - 60), bx = w / 2 - bw / 2, by = h / 2 + 10;
        ctx.fill(bx, by, bx + bw, by + 2, 0x50FFFFFF);
        ctx.fill(bx, by, bx + bw * p / 100, by + 2, 0xFFE02828);
        String tip = TIPS[(int) (t / 4) % TIPS.length];
        ctx.drawCenteredTextWithShadow(c.textRenderer, tip, w / 2, by + 18, 0xFFA8A090);
    }
}
