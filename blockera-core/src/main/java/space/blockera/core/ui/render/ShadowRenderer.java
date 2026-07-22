package space.blockera.core.ui.render;

import com.mojang.blaze3d.vertex.PoseStack;

/** Soft SDF shadow rendered as a low-opacity expanded rounded rectangle. */
public final class ShadowRenderer {
	private ShadowRenderer() {
	}

	public static void draw(PoseStack poseStack, float left, float top, float right, float bottom,
			float radius, float spread, int argb) {
		RoundedRectRenderer.draw(poseStack, left - spread, top - spread * 0.35F, right + spread,
				bottom + spread, radius + spread, argb, 0.0F, 0, Math.max(1.0F, spread * 0.55F));
	}
}
