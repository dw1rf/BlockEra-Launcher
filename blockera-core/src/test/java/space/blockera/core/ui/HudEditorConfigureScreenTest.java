package space.blockera.core.ui;

import org.junit.jupiter.api.Test;
import space.blockera.core.hud.BuiltinWidgetIds;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HudEditorConfigureScreenTest {
	@Test
	void blockeraWidgetConfigureActionOpensFullSettingsScreen() {
		assertEquals(HudEditorScreen.ConfigureMode.WIDGET,
				HudEditorScreen.configureMode(BuiltinWidgetIds.PLAYER_COUNT));
	}

	@Test
	void vanillaHudGroupOpensSettingsScreen() {
		assertEquals(HudEditorScreen.ConfigureMode.VANILLA,
				HudEditorScreen.configureMode("vanilla:hotbar"));
	}

	@Test
	void chatOpensAdvancedSettingsScreen() {
		assertEquals(HudEditorScreen.ConfigureMode.CHAT,
				HudEditorScreen.configureMode("vanilla:chat"));
	}
}
