package space.blockera.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;

/** Replaces scroll-area tracks and thumbs with compact Blockera controls. */
@Mixin(AbstractScrollArea.class)
abstract class ScrollAreaMixin {
    @Shadow protected abstract int scrollBarX();
    @Shadow protected abstract int scrollBarY();
    @Shadow protected abstract int scrollerHeight();
    @Shadow protected abstract int maxScrollAmount();

    @Inject(method = "renderScrollbar", at = @At("HEAD"), cancellable = true)
    private void blockera$renderScrollbar(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        CallbackInfo callback
    ) {
        AbstractScrollArea area = (AbstractScrollArea) (Object) this;
        // Vanilla still invokes renderScrollbar for lists whose content fits exactly.
        // Its scrollBarY() divides by maxScrollAmount(), so never query the thumb
        // geometry when there is nothing to scroll.
        if (maxScrollAmount() <= 0) {
            callback.cancel();
            return;
        }
        int left = scrollBarX();
        int thumbY = scrollBarY();
        int thumbHeight = scrollerHeight();
        BlockeraDraw.roundedRect(graphics, left, area.getY(), left + 4,
            area.getBottom(), 2, ThemeTokens.FIELD);
        BlockeraDraw.roundedRect(graphics, left, thumbY, left + 4,
            thumbY + thumbHeight, 2, ThemeTokens.ACCENT);
        callback.cancel();
    }
}
