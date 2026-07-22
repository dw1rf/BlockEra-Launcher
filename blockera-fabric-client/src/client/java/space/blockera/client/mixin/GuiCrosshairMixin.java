package space.blockera.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.client.BlockeraCoreServices;

/** Crisp local-only crosshair with no influence on ray casting or attack range. */
@Mixin(Gui.class)
abstract class GuiCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void blockera$renderCrosshair(GuiGraphics graphics, DeltaTracker tickCounter,
        CallbackInfo callback) {
        var config = BlockeraCoreServices.visuals().config();
        if (!BlockeraCoreServices.visualsEnabled() || !config.crosshairEnabled()) return;
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        int size = config.crosshairSize();
        int gap = config.crosshairGap();
        int thickness = config.crosshairThickness();
        int color = config.crosshairArgb();
        graphics.fill(x - gap - size, y, x - gap, y + thickness, color);
        graphics.fill(x + gap + thickness, y, x + gap + thickness + size, y + thickness, color);
        graphics.fill(x, y - gap - size, x + thickness, y - gap, color);
        graphics.fill(x, y + gap + thickness, x + thickness,
            y + gap + thickness + size, color);
        callback.cancel();
    }
}
