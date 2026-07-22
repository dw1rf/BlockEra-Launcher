package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ScreenStyleAdapter;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.TopInsettable;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin implements TopInsettable {
	private static final ThemeTokens BLOCKERA_THEME = ThemeTokens.darkDefault();

	@Shadow protected int y0;
	@Shadow protected int y1;
	@Shadow private boolean renderBackground;
	@Shadow private boolean renderTopAndBottom;
	@Shadow protected abstract int getScrollbarPosition();
	@Shadow public abstract int getMaxScroll();
	@Shadow protected abstract int getMaxPosition();
	@Shadow public abstract double getScrollAmount();

	@Override
	public void blockera$ensureTopInset(int minimumTop) {
		y0 = Math.min(y1, Math.max(y0, minimumTop));
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void blockera$disableVanillaListBackdrop(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
			CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return;
		renderBackground = false;
		renderTopAndBottom = false;
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void blockera$skipVanillaListBackground(PoseStack poseStack, CallbackInfo callback) {
		if (ScreenStyleAdapter.shouldStyleCurrentScreen()) callback.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void blockera$renderScrollbar(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
			CallbackInfo callback) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen() || getMaxScroll() <= 0) return;
		int left = getScrollbarPosition();
		int right = left + 6;
		BlockeraDraw.roundedRect(poseStack, left, y0, right, y1, 3, 0xE012141B);

		int viewport = y1 - y0;
		int thumbHeight = Mth.clamp((int) ((float) (viewport * viewport) / getMaxPosition()),
				18, Math.max(18, viewport - 8));
		int thumbTop = y0 + (int) getScrollAmount() * (viewport - thumbHeight) / Math.max(1, getMaxScroll());
		thumbTop = Math.max(y0, thumbTop);
		boolean hovered = mouseX >= left && mouseX < right && mouseY >= thumbTop && mouseY < thumbTop + thumbHeight;
		BlockeraDraw.roundedRect(poseStack, left + 1, thumbTop, right - 1, thumbTop + thumbHeight, 2,
				hovered ? BLOCKERA_THEME.accentHoverArgb() : 0xCC71627F);
	}
}
