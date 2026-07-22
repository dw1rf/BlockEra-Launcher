package space.blockera.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.client.BlockeraCoreServices;

/** Changes only the local hurt overlay tint; damage and hit registration stay vanilla. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void blockera$hitColor(LivingEntityRenderState state,
        CallbackInfoReturnable<Integer> callback) {
        var config = BlockeraCoreServices.visuals().config();
        if (BlockeraCoreServices.visualsEnabled()
            && config.hitColorEnabled()
            && state.hasRedOverlay) {
            callback.setReturnValue(config.hitColorArgb());
        }
    }
}
