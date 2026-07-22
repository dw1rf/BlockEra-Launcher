package space.blockera.core.ui.render;

import net.minecraft.client.Minecraft;

/** Converts logical UI measurements into framebuffer-aware anti-alias widths. */
public record UiScale(double guiScale, int framebufferWidth, int framebufferHeight) {
	public static UiScale current() {
		var window = Minecraft.getInstance().getWindow();
		return new UiScale(window.getGuiScale(), window.getWidth(), window.getHeight());
	}

	public float oneFramebufferPixel() { return (float) (1.0D / Math.max(1.0D, guiScale)); }
}
