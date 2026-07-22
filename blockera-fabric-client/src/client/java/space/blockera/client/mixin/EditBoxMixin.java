package space.blockera.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.client.ui.BlockeraDraw;

/** Replaces the vanilla text-field frame while retaining its mature text editing logic. */
@Mixin(EditBox.class)
abstract class EditBoxMixin {
    @Shadow private boolean bordered;
    @Unique private boolean blockera$restoreBorder;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void blockera$beginField(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        EditBox field = (EditBox) (Object) this;
        blockera$restoreBorder = bordered;
        if (bordered) {
            BlockeraDraw.field(graphics, field.getX(), field.getY(),
                field.getRight(), field.getBottom(), field.isFocused());
            bordered = false;
        }
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void blockera$finishField(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callback
    ) {
        bordered = blockera$restoreBorder;
    }
}
