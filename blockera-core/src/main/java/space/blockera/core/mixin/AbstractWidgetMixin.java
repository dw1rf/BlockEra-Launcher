package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.BlockeraVanillaWidgetRenderer;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void blockera$renderStyledWidget(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
			CallbackInfo callback) {
		AbstractWidget widget = (AbstractWidget) (Object) this;
		if (!widget.visible || !BlockeraVanillaWidgetRenderer.supports(widget)) return;
		BlockeraVanillaWidgetRenderer.render(widget, poseStack, mouseX, mouseY);
		callback.cancel();
	}
}
