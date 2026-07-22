package space.blockera.core.enhancement;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Applies a deterministic white client light map without changing world or player state. */
public final class FullBrightController {
	private static final int FULL_BRIGHT_PIXEL = 0xFFFFFFFF;

	private FullBrightController() {
	}

	public static void apply(NativeImage lightPixels, DynamicTexture lightTexture) {
		lightPixels.fillRect(0, 0, lightPixels.getWidth(), lightPixels.getHeight(), FULL_BRIGHT_PIXEL);
		lightTexture.upload();
	}
}
