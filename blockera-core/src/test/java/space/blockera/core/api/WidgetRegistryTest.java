package space.blockera.core.api;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import space.blockera.core.hud.HudCategory;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WidgetRegistryTest {
	@Test
	void rejectsThirdPartyNamespace() {
		WidgetRegistry registry = WidgetRegistry.firstPartyOnly();
		Widget thirdParty = new Widget() {
			@Override public String id() { return "example:untrusted"; }
			@Override public Component title() { return Component.literal("Untrusted"); }
			@Override public HudCategory category() { return HudCategory.PLAYER; }
		};
		assertThrows(IllegalArgumentException.class, () -> registry.register(thirdParty));
	}
}
