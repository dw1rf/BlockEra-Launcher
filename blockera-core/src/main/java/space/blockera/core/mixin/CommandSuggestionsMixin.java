package space.blockera.core.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.blockera.core.ui.BlockeraChatStyleScope;

/** Keeps command-suggestion drawing and measurements inside the Blockera chat font scope. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Inject(method = {"updateCommandInfo", "showSuggestions"}, at = @At("HEAD"))
	private void blockera$enterSuggestionScope(CallbackInfo callback) {
		BlockeraChatStyleScope.enter();
	}

	@Inject(method = {"updateCommandInfo", "showSuggestions"}, at = @At("RETURN"))
	private void blockera$exitSuggestionScope(CallbackInfo callback) {
		BlockeraChatStyleScope.exit();
	}
}
