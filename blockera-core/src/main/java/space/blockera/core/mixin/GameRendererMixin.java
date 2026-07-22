package space.blockera.core.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.config.ClientConfig;

/** Suppresses only Minecraft's local totem activation overlay; game state, sound and particles remain untouched. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void blockera$hideTotemAnimation(ItemStack stack, CallbackInfo callback) {
		if (ClientConfig.HIDE_TOTEM_ANIMATION.get() && stack.is(Items.TOTEM_OF_UNDYING)) {
			callback.cancel();
		}
	}
}
