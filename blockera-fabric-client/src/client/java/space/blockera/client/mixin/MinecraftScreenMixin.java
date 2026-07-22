package space.blockera.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import space.blockera.client.ui.BlockeraInGameMenuScreen;
import space.blockera.client.ui.BlockeraTitleScreen;

/** Replaces only exact vanilla screens; other mods' subclasses remain untouched. */
@Mixin(Minecraft.class)
abstract class MinecraftScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen blockera$replaceVanillaScreen(Screen screen) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (screen != null && screen.getClass() == TitleScreen.class
            && !Boolean.getBoolean("blockera.disableCustomTitleScreen")) {
            return new BlockeraTitleScreen();
        }
        if (screen != null && screen.getClass() == PauseScreen.class && minecraft.level != null
            && !Boolean.getBoolean("blockera.disableCustomPauseScreen")) {
            return new BlockeraInGameMenuScreen(true);
        }
        return screen;
    }
}
