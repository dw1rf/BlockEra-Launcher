package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.hud.VanillaHudOverlayTransform;
import space.blockera.core.hud.VanillaHudSettings;
import space.blockera.core.ui.BlockeraChatStyleScope;
import space.blockera.core.ui.BlockeraChatTabs;
import space.blockera.core.ui.ChatInteractionController;
import space.blockera.core.ui.DetachedChatPanels;

/** Keeps chat input, suggestions and mouse hit testing aligned with the transformed vanilla chat panel. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Unique private boolean blockera$chatPosePushed;
	@Unique private int blockera$rawMouseX;
	@Unique private int blockera$rawMouseY;

	@Inject(method = "render", at = @At("HEAD"))
	private void blockera$beforeRender(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		blockera$rawMouseX = mouseX;
		blockera$rawMouseY = mouseY;
		BlockeraChatStyleScope.enter();
		VanillaHudSettings settings = blockera$settings();
		if (settings.enabled) {
			Minecraft minecraft = Minecraft.getInstance();
			VanillaHudOverlayTransform.apply(poseStack, VanillaHudElement.CHAT, settings,
					minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
			blockera$chatPosePushed = true;
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void blockera$afterRender(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		BlockeraChatTabs.render(poseStack, minecraft.getWindow().getGuiScaledHeight(),
				(int) Math.round(blockera$inverseX(blockera$rawMouseX)),
				(int) Math.round(blockera$inverseY(blockera$rawMouseY)));
		if (blockera$chatPosePushed) {
			poseStack.popPose();
			blockera$chatPosePushed = false;
		}
		DetachedChatPanels.render(poseStack, blockera$rawMouseX, blockera$rawMouseY,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		ChatInteractionController.render(poseStack, blockera$rawMouseX, blockera$rawMouseY,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		BlockeraChatStyleScope.exit();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void blockera$clickChatTab(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> callback) {
		Minecraft minecraft = Minecraft.getInstance();
		if (DetachedChatPanels.mouseClicked(blockera$rawMouseX, blockera$rawMouseY, button,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) {
			callback.setReturnValue(true); return;
		}
		if (ChatInteractionController.mouseClicked(blockera$rawMouseX, blockera$rawMouseY, button,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) {
			callback.setReturnValue(true); return;
		}
		if (button != 0) return;
		if (BlockeraChatTabs.mouseClicked(minecraft.getWindow().getGuiScaledHeight(),
				blockera$inverseX(blockera$rawMouseX), blockera$inverseY(blockera$rawMouseY))) callback.setReturnValue(true);
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (DetachedChatPanels.mouseDragged(mouseX, mouseY, button,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) return true;
		if (ChatInteractionController.mouseDragged(mouseX, mouseY, button,
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) return true;
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (DetachedChatPanels.mouseReleased(button)) return true;
		return ChatInteractionController.mouseReleased(button);
	}

	@Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
	private void blockera$closePopup(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callback) {
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && ChatInteractionController.escape()) callback.setReturnValue(true);
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, index = 2)
	private int blockera$renderMouseX(int mouseX) { return (int) Math.round(blockera$inverseX(mouseX)); }

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, index = 3)
	private int blockera$renderMouseY(int mouseY) { return (int) Math.round(blockera$inverseY(mouseY)); }

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, index = 1)
	private double blockera$clickMouseX(double mouseX) { return blockera$inverseX(mouseX); }

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, index = 3)
	private double blockera$clickMouseY(double mouseY) { return blockera$inverseY(mouseY); }

	@ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, index = 1)
	private double blockera$scrollMouseX(double mouseX) { return blockera$inverseX(mouseX); }

	@ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, index = 3)
	private double blockera$scrollMouseY(double mouseY) { return blockera$inverseY(mouseY); }

	@Unique
	private static VanillaHudSettings blockera$settings() {
		return HudLayoutStore.instance().vanillaSettings(VanillaHudElement.CHAT.id());
	}

	@Unique
	private static double blockera$inverseX(double mouseX) {
		Minecraft minecraft = Minecraft.getInstance();
		return VanillaHudOverlayTransform.inverseX(VanillaHudElement.CHAT, blockera$settings(),
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), mouseX);
	}

	@Unique
	private static double blockera$inverseY(double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		return VanillaHudOverlayTransform.inverseY(VanillaHudElement.CHAT, blockera$settings(),
				minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), mouseY);
	}
}
