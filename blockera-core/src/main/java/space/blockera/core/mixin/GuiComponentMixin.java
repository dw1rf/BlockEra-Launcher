package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.ScreenStyleAdapter;
import space.blockera.core.ui.ScreenChromeLayout;
import space.blockera.core.ui.UiFont;

@Mixin(GuiComponent.class)
public abstract class GuiComponentMixin {
	@Inject(method = "drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
			at = @At("HEAD"), cancellable = true)
	private static void blockera$drawCenteredComponent(PoseStack poseStack, Font font, Component text,
			int centerX, int y, int color, CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return;
		UiFont.drawCentered(poseStack, text, centerX, ScreenChromeLayout.titleTop(y), opaque(color));
		callback.cancel();
	}

	@Inject(method = "drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
			at = @At("HEAD"), cancellable = true)
	private static void blockera$drawCenteredString(PoseStack poseStack, Font font, String text,
			int centerX, int y, int color, CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return;
		UiFont.drawCentered(poseStack, Component.literal(text), centerX, ScreenChromeLayout.titleTop(y), opaque(color));
		callback.cancel();
	}

	@Inject(method = "drawString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
			at = @At("HEAD"), cancellable = true)
	private static void blockera$drawComponent(PoseStack poseStack, Font font, Component text,
			int x, int y, int color, CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return;
		UiFont.draw(poseStack, text, x, y, opaque(color));
		callback.cancel();
	}

	@Inject(method = "drawString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
			at = @At("HEAD"), cancellable = true)
	private static void blockera$drawString(PoseStack poseStack, Font font, String text,
			int x, int y, int color, CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return;
		UiFont.draw(poseStack, text, x, y, opaque(color));
		callback.cancel();
	}

	private static int opaque(int color) {
		return (color >>> 24) == 0 ? color | 0xFF000000 : color;
	}
}
