package space.blockera.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.client.BlockeraCoreClient;

/** Hold-to-zoom changes only the local camera FOV and never player targeting. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void blockera$zoom(Camera camera, float partialTick, boolean useFovSetting,
        CallbackInfoReturnable<Float> result) {
        if (BlockeraCoreClient.isZooming()) {
            result.setReturnValue(Math.max(8.0F, result.getReturnValue() * 0.35F));
        }
    }
}
