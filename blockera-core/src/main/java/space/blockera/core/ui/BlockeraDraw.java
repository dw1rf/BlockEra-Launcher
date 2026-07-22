package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import space.blockera.core.ui.render.RoundedRectRenderer;
import space.blockera.core.ui.render.ShadowRenderer;

/** Compatibility facade over the shader-backed Blockera shape pipeline. */
public final class BlockeraDraw {
	private BlockeraDraw() {
	}

	public static void roundedRect(PoseStack poseStack, int left, int top, int right, int bottom, int radius, int argb) {
		RoundedRectRenderer.draw(poseStack, left, top, right, bottom, radius, argb, 0.0F, 0);
	}

	public static void panel(PoseStack poseStack, int left, int top, int right, int bottom,
			int radius, int fill, int border) {
		ShadowRenderer.draw(poseStack, left, top, right, bottom, radius, 2.0F, 0x26000000);
		RoundedRectRenderer.draw(poseStack, left, top, right, bottom, radius, fill, 1.0F, border);
	}

	public static void glassPanel(PoseStack poseStack, int left, int top, int right, int bottom,
			int radius, int fill, int border) {
		ShadowRenderer.draw(poseStack, left, top, right, bottom, radius, 2.0F, 0x1E000000);
		RoundedRectRenderer.draw(poseStack, left, top, right, bottom, radius, fill, 1.0F, border);
	}

	public static void grid(PoseStack poseStack, int width, int height, int spacing, int color) {
		// Intentionally empty: the new interface avoids pixel-grid decoration.
	}
}
