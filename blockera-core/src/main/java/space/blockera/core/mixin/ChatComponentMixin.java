package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.BlockeraChatStyleScope;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatMessagePayload;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void blockera$routeMessage(Component message, MessageSignature signature, GuiMessageTag tag,
			CallbackInfo callback) {
		BlockeraChatRuntime runtime = BlockeraChatRuntime.instance();
		if (runtime.replaying()) return;
		BlockeraChatRuntime.RouteDecision decision = runtime.route(new ChatMessagePayload(message, signature, tag));
		runtime.notifyMention(decision);
		if (decision.visible()) {
			ChatComponent self = (ChatComponent) (Object) this;
			runtime.withReplay(() -> self.addMessage(decision.decorated(), signature, tag));
		}
		callback.cancel();
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void blockera$clearRoutedMessages(boolean clearRecentChat, CallbackInfo callback) {
		BlockeraChatRuntime runtime = BlockeraChatRuntime.instance();
		if (!runtime.rebuilding()) runtime.clear();
	}
	@Inject(method = "render", at = @At("HEAD"))
	private void blockera$enterChatTextScope(PoseStack poseStack, int tick, CallbackInfo callback) {
		BlockeraChatStyleScope.enter();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void blockera$exitChatTextScope(PoseStack poseStack, int tick, CallbackInfo callback) {
		BlockeraChatStyleScope.exit();
	}
}
