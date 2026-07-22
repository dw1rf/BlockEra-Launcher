package space.blockera.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.client.chat.BlockeraChatOverlay;
import space.blockera.client.chat.ChatLayout;
import space.blockera.client.ui.BlockeraDraw;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin extends Screen {
	@Shadow protected EditBox input;

	protected ChatScreenMixin(net.minecraft.network.chat.Component title) {
		super(title);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void blockera$alignInput(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
		CallbackInfo callback) {
		ChatLayout.Bounds bounds = ChatLayout.bounds(width, height);
		int inputY = Math.min(height - 12, bounds.bottom() + ChatLayout.INPUT_GAP);
		input.setX(bounds.left() + 2);
		input.setY(inputY);
		input.setWidth(Math.max(20, bounds.width() - 4));
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
	private void blockera$alignInputBackground(GuiGraphics graphics, int left, int top, int right, int bottom,
		int color) {
		BlockeraDraw.field(graphics, input.getX() - 2, input.getY() - 2,
			input.getRight() + 2, input.getBottom() + 2, true);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void blockera$renderControls(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
		CallbackInfo callback) {
		BlockeraChatOverlay.render(graphics, mouseX, mouseY, width, height);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void blockera$chatControls(MouseButtonEvent event, boolean doubleClick,
		CallbackInfoReturnable<Boolean> callback) {
		if (event.button() == 0 && BlockeraChatOverlay.mouseClicked((Screen) (Object) this,
			event.x(), event.y(), width, height)) {
			callback.setReturnValue(true);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (event.button() == 0 && BlockeraChatOverlay.mouseDragged(event.x(), event.y(), width, height)) {
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && BlockeraChatOverlay.mouseReleased()) return true;
		return super.mouseReleased(event);
	}

	@ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;<init>(Lnet/minecraft/client/gui/Font;II)V"),
		index = 1)
	private int blockera$clickableMouseX(int mouseX) {
		return ChatLayout.toVanillaMouseX(mouseX, width, height);
	}

	@ModifyArg(method = "mouseClicked", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;<init>(Lnet/minecraft/client/gui/Font;II)V"),
		index = 2)
	private int blockera$clickableMouseY(int mouseY) {
		return ChatLayout.toVanillaMouseY(mouseY, width, height);
	}
}
