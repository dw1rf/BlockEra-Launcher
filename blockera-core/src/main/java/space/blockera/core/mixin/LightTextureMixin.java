package space.blockera.core.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.enhancement.FullBrightController;

/** Replaces the already-computed client light map only while the first-party full-bright toggle is enabled. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@Shadow @Final private NativeImage lightPixels;
	@Shadow @Final private DynamicTexture lightTexture;

	@Inject(method = "updateLightTexture", at = @At("TAIL"))
	private void blockera$applyFullBright(float partialTick, CallbackInfo callback) {
		if (ClientConfig.FULL_BRIGHT.get()) {
			FullBrightController.apply(lightPixels, lightTexture);
		}
	}
}
