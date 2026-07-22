package space.blockera.client.mixin;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.client.BlockeraCoreServices;
import space.blockera.client.chat.BlockeraChatRuntime;
import space.blockera.client.chat.ChatLayout;
import space.blockera.client.chat.ChatMessagePayload;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void blockera$routeMessage(Component message, MessageSignature signature, GuiMessageTag tag,
		CallbackInfo callback) {
		BlockeraChatRuntime runtime = BlockeraChatRuntime.instance();
		if (runtime.replaying()) return;
		BlockeraChatRuntime.RouteDecision decision = runtime.route(new ChatMessagePayload(message, signature, tag));
		if (decision.visible()) {
			ChatComponent self = (ChatComponent) (Object) this;
			runtime.withReplay(() -> self.addMessage(decision.decorated(), signature, tag));
		}
		callback.cancel();
	}

	@Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
	private void blockera$preserveHistory(boolean clearRecentChat, CallbackInfo callback) {
		BlockeraChatRuntime runtime = BlockeraChatRuntime.instance();
		if (BlockeraCoreServices.chat().config().preserveHistory() && !runtime.rebuilding()) {
			callback.cancel();
			return;
		}
		runtime.clearIfAllowed();
	}

	@Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
	private void blockera$chatWidth(CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(BlockeraCoreServices.chat().config().width());
	}

	@Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
	private void blockera$chatHeight(CallbackInfoReturnable<Integer> callback) {
		boolean focused = Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen;
		callback.setReturnValue(focused
			? BlockeraCoreServices.chat().config().focusedHeight()
			: BlockeraCoreServices.chat().config().unfocusedHeight());
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
		at = @At(value = "INVOKE",
			target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;",
			shift = At.Shift.AFTER))
	private void blockera$positionChat(GuiGraphics graphics, Font font, int tick, int mouseX, int mouseY,
		boolean focused, boolean insertionMode, CallbackInfo callback) {
		ChatLayout.Bounds bounds = ChatLayout.bounds(graphics.guiWidth(), graphics.guiHeight());
		graphics.pose().translate(bounds.left(), bounds.bottom() - (graphics.guiHeight() - 40));
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
		at = @At("HEAD"), argsOnly = true, index = 4)
	private int blockera$renderMouseX(int mouseX) {
		return transformedX(mouseX);
	}

	@ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
		at = @At("HEAD"), argsOnly = true, index = 5)
	private int blockera$renderMouseY(int mouseY) {
		return transformedY(mouseY);
	}

	@ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
	private int blockera$unlimitedDisplayHistory(int original) {
		return BlockeraCoreServices.chat().config().unlimitedHistory() ? Integer.MAX_VALUE : original;
	}

	@ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
	private int blockera$unlimitedMessageHistory(int original) {
		return BlockeraCoreServices.chat().config().unlimitedHistory() ? Integer.MAX_VALUE : original;
	}

	private static int transformedX(int mouseX) {
		Minecraft minecraft = Minecraft.getInstance();
		return ChatLayout.toVanillaMouseX(mouseX,
			minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
	}

	private static int transformedY(int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		return ChatLayout.toVanillaMouseY(mouseY,
			minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
	}
}
