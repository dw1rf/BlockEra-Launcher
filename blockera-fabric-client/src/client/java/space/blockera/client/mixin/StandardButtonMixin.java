package space.blockera.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

/** Fully replaces ordinary Minecraft button contents with the Blockera control. */
@Mixin({Button.Plain.class, CycleButton.class, Checkbox.class})
abstract class StandardButtonMixin {
    @Inject(method = "renderContents", at = @At("HEAD"), cancellable = true)
    private void blockera$renderStandardButton(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        AbstractButton button = (AbstractButton) (Object) this;
        boolean highlighted = button.isHoveredOrFocused();
        if (button instanceof Checkbox checkbox) {
            int boxSize = Math.min(14, button.getHeight());
            int boxY = button.getY() + (button.getHeight() - boxSize) / 2;
            BlockeraDraw.field(graphics, button.getX(), boxY,
                button.getX() + boxSize, boxY + boxSize, highlighted);
            if (checkbox.selected()) {
                int left = button.getX() + 3;
                int top = boxY + 4;
                graphics.fill(left, top + 2, left + 2, top + 4, ThemeTokens.ACCENT);
                graphics.fill(left + 2, top + 3, left + 4, top + 5, ThemeTokens.ACCENT);
                graphics.fill(left + 4, top + 1, left + 6, top + 4, ThemeTokens.ACCENT);
                graphics.fill(left + 6, top, left + 9, top + 2, ThemeTokens.ACCENT);
            }
            UiText.draw(graphics, button.getMessage(), button.getX() + boxSize + 6,
                button.getY() + (button.getHeight() - 9) / 2,
                button.active ? ThemeTokens.TEXT : ThemeTokens.MUTED);
            callback.cancel();
            return;
        }
        int fill = button.active
            ? (highlighted ? ThemeTokens.CARD_HOVER : ThemeTokens.CARD)
            : ThemeTokens.FIELD;
        int text = button.active ? ThemeTokens.TEXT : ThemeTokens.MUTED;
        BlockeraDraw.button(graphics, button.getX(), button.getY(), button.getRight(), button.getBottom(),
            fill, highlighted, false);
        UiText.drawCentered(graphics, button.getMessage(), button.getX() + button.getWidth() / 2,
            button.getY() + (button.getHeight() - 9) / 2, text);
        callback.cancel();
    }
}
