package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.ScreenStyleAdapter;
import space.blockera.core.ui.ThemeTokens;

@Mixin(Screen.class)
public abstract class ScreenMixin {
	private static final ThemeTokens BLOCKERA_THEME = ThemeTokens.darkDefault();

	@Inject(method = "renderBackground(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
	private void blockera$renderTransparentGameBackground(PoseStack poseStack, CallbackInfo callback) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ScreenStyleAdapter.shouldStyle((Screen) (Object) this)) {
			return;
		}
		Screen screen = (Screen) (Object) this;
		int color = minecraft.level == null ? BLOCKERA_THEME.menuBackdropArgb() : BLOCKERA_THEME.gameBackdropArgb();
		GuiComponent.fill(poseStack, 0, 0, screen.width, screen.height, color);
		callback.cancel();
	}

	@Inject(method = "renderBackground(Lcom/mojang/blaze3d/vertex/PoseStack;I)V", at = @At("HEAD"), cancellable = true)
	private void blockera$renderTransparentGameBackgroundWithOffset(PoseStack poseStack, int verticalOffset,
			CallbackInfo callback) {
		blockera$renderTransparentGameBackground(poseStack, callback);
	}

	@Inject(method = "renderDirtBackground(I)V", at = @At("HEAD"), cancellable = true)
	private void blockera$skipDirtBackground(int verticalOffset, CallbackInfo callback) {
		if (ScreenStyleAdapter.shouldStyle((Screen) (Object) this)) {
			callback.cancel();
		}
	}
}
