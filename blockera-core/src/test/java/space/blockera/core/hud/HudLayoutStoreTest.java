package space.blockera.core.hud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutStoreTest {
	@TempDir Path temporaryDirectory;

	@Test
	void jsonRoundTripPreservesProfileAndWidgetSettings() {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		HudLayoutStore first = new HudLayoutStore(path);
		first.setActiveProfile(HudLayoutStore.PVP);
		HudWidgetSettings settings = first.settings(BuiltinWidgetIds.CLOCK);
		settings.anchor = HudAnchor.BOTTOM_CENTER;
		settings.offsetX = 24;
		settings.scale = 1.7F;
		settings.opacity = 0.4F;
		settings.background = false;
		settings.clock.showSeconds = true;
		settings.clock.use24Hour = false;
		first.save();

		HudLayoutStore second = new HudLayoutStore(path);
		second.load();
		HudWidgetSettings restored = second.settings(BuiltinWidgetIds.CLOCK);
		assertEquals(HudLayoutStore.PVP, second.activeProfile());
		assertEquals(HudAnchor.BOTTOM_CENTER, restored.anchor);
		assertEquals(24, restored.offsetX);
		assertEquals(1.7F, restored.scale);
		assertEquals(0.4F, restored.opacity);
		assertFalse(restored.background);
		assertTrue(restored.clock.showSeconds);
		assertFalse(restored.clock.use24Hour);
	}

	@Test
	void schemaTwoIsMigratedWithSafeClockDefaults() throws IOException {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		Files.writeString(path, """
				{
				  "schemaVersion": 2,
				  "activeProfile": "survival",
				  "profiles": {
				    "survival": {
				      "widgets": {
				        "blockera:clock": {"enabled": true, "anchor": "TOP_RIGHT", "scale": 1.25}
				      },
				      "vanilla": {}
				    }
				  }
				}
				""");

		HudLayoutStore store = new HudLayoutStore(path);
		store.load();
		HudWidgetSettings clock = store.settings(BuiltinWidgetIds.CLOCK);
		assertEquals(1.25F, clock.scale);
		assertTrue(clock.clock.showRealTime);
		assertTrue(clock.clock.showWorldTime);
		assertTrue(clock.clock.use24Hour);
		assertTrue(store.json().contains("\"schemaVersion\": 5"));
	}

	@Test
	void corruptLayoutIsBackedUpAndSafeDefaultsAreLoaded() throws IOException {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		Files.writeString(path, "{not json");
		HudLayoutStore store = new HudLayoutStore(path);
		store.load();

		assertEquals(HudLayoutStore.MINIMAL, store.activeProfile());
		assertTrue(store.settings(BuiltinWidgetIds.FPS).enabled);
		assertFalse(Files.exists(path));
		try (var entries = Files.list(temporaryDirectory)) {
			assertEquals(1L, entries.filter(file -> file.getFileName().toString().startsWith("hud-layouts.json.corrupt-")).count());
		}
	}

	@Test
	void schemaOneIsMigratedWithoutLosingBlockeraWidgetSettings() throws IOException {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		Files.writeString(path, """
				{
				  "schemaVersion": 1,
				  "activeProfile": "survival",
				  "profiles": {
				    "survival": {
				      "widgets": {
				        "blockera:fps": {"enabled": false, "anchor": "BOTTOM_RIGHT", "scale": 1.4}
				      }
				    }
				  }
				}
				""");
		HudLayoutStore store = new HudLayoutStore(path);
		store.load();

		assertFalse(store.settings(BuiltinWidgetIds.FPS).enabled);
		assertEquals(HudAnchor.BOTTOM_RIGHT, store.settings(BuiltinWidgetIds.FPS).anchor);
		assertEquals(1.4F, store.settings(BuiltinWidgetIds.FPS).scale);
		assertTrue(store.vanillaSettings(VanillaHudElement.HOTBAR.id()).enabled);
		assertTrue(store.json().contains("\"schemaVersion\": 5"));
	}

	@Test
	void vanillaSettingsRoundTripAndClampScale() {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		HudLayoutStore first = new HudLayoutStore(path);
		VanillaHudSettings chat = first.vanillaSettings(VanillaHudElement.CHAT.id());
		chat.enabled = false;
		chat.anchor = HudAnchor.TOP_CENTER;
		chat.offsetX = 32;
		chat.scale = 5.0F;
		first.save();

		HudLayoutStore second = new HudLayoutStore(path);
		second.load();
		VanillaHudSettings restored = second.vanillaSettings(VanillaHudElement.CHAT.id());
		assertFalse(restored.enabled);
		assertEquals(HudAnchor.TOP_CENTER, restored.anchor);
		assertEquals(32, restored.offsetX);
		assertEquals(2.0F, restored.scale);
	}

	@Test
	void unknownVanillaIdInvalidatesDocument() throws IOException {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		Files.writeString(path, """
				{
				  "schemaVersion": 2,
				  "activeProfile": "survival",
				  "profiles": {
				    "survival": {"widgets": {}, "vanilla": {"third_party": {"enabled": true}}}
				  }
				}
				""");
		HudLayoutStore store = new HudLayoutStore(path);
		store.load();

		assertTrue(store.vanillaSettings(VanillaHudElement.HOTBAR.id()).enabled);
		assertFalse(Files.exists(path));
	}

	@Test
	void duplicateWidgetCreatesFirstPartyInstanceWithIndependentState() {
		HudLayoutStore store = new HudLayoutStore(temporaryDirectory.resolve("hud-layouts.json"));
		store.settings(BuiltinWidgetIds.FPS).scale = 1.3F;
		String duplicate = store.duplicateWidget(BuiltinWidgetIds.FPS);

		assertEquals("blockera:fps#2", duplicate);
		assertEquals(1.3F, store.settings(duplicate).scale);
		store.settings(duplicate).scale = 0.8F;
		assertEquals(1.3F, store.settings(BuiltinWidgetIds.FPS).scale);
	}

	@Test
	void unknownBlockeraWidgetTypeIsNotAcceptedAsFirstParty() {
		HudLayoutStore store = new HudLayoutStore(temporaryDirectory.resolve("hud-layouts.json"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> store.settings("blockera:unreviewed_module"));
	}

	@Test
	void schemaFourDropsOptionsNotDeclaredByTheWidget() {
		Path path = temporaryDirectory.resolve("hud-layouts.json");
		HudLayoutStore first = new HudLayoutStore(path);
		first.settings(BuiltinWidgetIds.FPS).options.put("unreviewed_option", "true");
		first.save();

		HudLayoutStore second = new HudLayoutStore(path);
		second.load();
		assertTrue(second.settings(BuiltinWidgetIds.FPS).options.isEmpty());
	}

	@Test
	void defaultsAndRemovalKeepOnlyFpsEnabledWithoutZombieRecreation() {
		HudLayoutStore store = new HudLayoutStore(temporaryDirectory.resolve("hud-layouts.json"));
		assertTrue(store.settings(BuiltinWidgetIds.FPS).enabled);
		assertFalse(store.settings(BuiltinWidgetIds.COORDINATES).enabled);
		store.removeWidget(BuiltinWidgetIds.FPS);
		assertFalse(store.settings(BuiltinWidgetIds.FPS).enabled);
	}
}
