package space.blockera.core.ui;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockeraVanillaWidgetRendererTest {
	@Test
	void onlyExplicitActionsUsePrimaryStyle() {
		assertTrue(BlockeraVanillaWidgetRenderer.isPrimary(Component.translatable("gui.done")));
		assertTrue(BlockeraVanillaWidgetRenderer.isPrimary(Component.translatable("menu.returnToGame")));
		assertFalse(BlockeraVanillaWidgetRenderer.isPrimary(Component.translatable("options.video")));
		assertFalse(BlockeraVanillaWidgetRenderer.isPrimary(Component.literal("Done")));
	}
}
