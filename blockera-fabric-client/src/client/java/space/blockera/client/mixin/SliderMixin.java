package space.blockera.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

/** Full Blockera slider renderer; no vanilla slider texture remains underneath. */
@Mixin(AbstractSliderButton.class)
abstract class SliderMixin {
    @Shadow protected double value;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void blockera$renderSlider(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        AbstractWidget slider = (AbstractWidget) (Object) this;
        int centerY = slider.getY() + slider.getHeight() / 2;
        int trackLeft = slider.getX() + 5;
        int trackRight = slider.getRight() - 5;
        int handleX = trackLeft + (int) Math.round((trackRight - trackLeft) * value);
        BlockeraDraw.roundedRect(graphics, trackLeft, centerY - 2,
            trackRight, centerY + 2, 2, ThemeTokens.FIELD);
        BlockeraDraw.roundedRect(graphics, trackLeft, centerY - 2,
            Math.max(trackLeft + 1, handleX), centerY + 2, 2, ThemeTokens.ACCENT);
        BlockeraDraw.roundedRect(graphics, handleX - 3, centerY - 6,
            handleX + 3, centerY + 6, 3,
            slider.active ? ThemeTokens.TEXT : ThemeTokens.MUTED);
        UiText.drawCentered(graphics, slider.getMessage(),
            slider.getX() + slider.getWidth() / 2, slider.getY() - 10,
            slider.active ? ThemeTokens.TEXT : ThemeTokens.MUTED);
        callback.cancel();
    }
}
