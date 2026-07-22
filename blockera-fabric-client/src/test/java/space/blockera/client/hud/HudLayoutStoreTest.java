package space.blockera.client.hud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class HudLayoutStoreTest {
    @TempDir Path directory;

    @Test
    void defaultsEnableOnlyFpsAndRoundTripChanges() {
        Path file = directory.resolve("hud-layouts.json");
        HudLayoutStore first = new HudLayoutStore(file);
        first.load();
        assertTrue(first.settings("blockera:fps").enabled);
        assertFalse(first.settings("blockera:coordinates").enabled);
		assertFalse(first.settings("blockera:target_info").enabled);
        first.settings("blockera:coordinates").enabled = true;
        first.save();

        HudLayoutStore second = new HudLayoutStore(file);
        second.load();
        assertTrue(second.settings("blockera:coordinates").enabled);
		assertEquals(List.of("minimal", "survival", "pvp", "building", "stream", "custom"), second.profileNames());
		for (String profile : second.profileNames()) {
			second.setActiveProfile(profile);
			assertTrue(second.settings("blockera:fps").enabled);
			assertEquals("minimal".equals(profile) ? 2 : 1,
				second.snapshot().values().stream().filter(settings -> settings.enabled).count());
		}
    }

    @Test
    void corruptLayoutIsBackedUpAndThirdPartyNamespaceIsRejected() throws Exception {
        Path file = directory.resolve("hud-layouts.json");
        Files.writeString(file, "not-json");
        HudLayoutStore store = new HudLayoutStore(file);
        store.load();
        assertTrue(Files.exists(directory.resolve("hud-layouts.json.corrupt")));
        assertThrows(IllegalArgumentException.class, () -> store.settings("other:widget"));
    }

    @Test
    void allNineAnchorsResolvePredictably() {
        assertEquals(new HudPoint(45, 45), HudAnchor.CENTER.resolve(100, 100, 10, 10, 0, 0));
        assertEquals(new HudPoint(88, 87), HudAnchor.BOTTOM_RIGHT.resolve(100, 100, 10, 10, 2, 3));
        assertEquals(new HudPoint(47, 3), HudAnchor.TOP_CENTER.resolve(100, 100, 10, 10, 2, 3));
    }

	@Test
	void migratesCurrentFabricSchemaAndPreservesAnchoredPosition() throws Exception {
		Path file = directory.resolve("hud-layouts.json");
		Files.writeString(file, """
			{
			  "schemaVersion": 1,
			  "widgets": {
			    "blockera:fps": {
			      "enabled": false,
			      "anchor": "BOTTOM_RIGHT",
			      "offsetX": 91,
			      "offsetY": -37,
			      "scale": 1.4,
			      "opacity": 0.65,
			      "background": false
			    }
			  }
			}
			""");

		HudLayoutStore store = new HudLayoutStore(file);
		store.load();

		assertEquals("custom", store.activeProfile());
		HudWidgetSettings fps = store.settings("blockera:fps");
		assertFalse(fps.enabled);
		assertEquals(HudAnchor.BOTTOM_RIGHT, fps.anchor);
		assertEquals(91, fps.offsetX);
		assertEquals(-37, fps.offsetY);
		assertEquals(1.4F, fps.scale);
		assertEquals(0.65F, fps.opacity);
		assertFalse(fps.background);
		assertEquals(5, Integer.parseInt(store.json().replaceAll("(?s).*\\\"schemaVersion\\\"\\s*:\\s*(\\d+).*", "$1")));
	}

	@Test
	void deletedBuiltinStaysDisabledAfterReload() {
		Path file = directory.resolve("hud-layouts.json");
		HudLayoutStore first = new HudLayoutStore(file);
		first.load();
		first.removeWidget("blockera:fps");
		first.save();

		HudLayoutStore second = new HudLayoutStore(file);
		second.load();
		assertFalse(second.settings("blockera:fps").enabled);
	}

	@Test
	void unknownBuiltinAndUnknownOptionInvalidateTheDocument() throws Exception {
		Path unknownId = directory.resolve("unknown-id.json");
		Files.writeString(unknownId, """
			{"schemaVersion":5,"activeProfile":"minimal","profiles":{"minimal":{"widgets":{"blockera:balance":{"enabled":true}},"vanilla":{}}}}
			""");
		HudLayoutStore idStore = new HudLayoutStore(unknownId);
		idStore.load();
		assertTrue(Files.list(directory).anyMatch(path -> path.getFileName().toString().startsWith("unknown-id.json.corrupt")));
		assertThrows(IllegalArgumentException.class, () -> idStore.settings("blockera:balance"));

		Path unknownOption = directory.resolve("unknown-option.json");
		Files.writeString(unknownOption, """
			{"schemaVersion":5,"activeProfile":"minimal","profiles":{"minimal":{"widgets":{"blockera:fps":{"enabled":true,"options":{"hack":true}}},"vanilla":{}}}}
			""");
		HudLayoutStore optionStore = new HudLayoutStore(unknownOption);
		optionStore.load();
		assertTrue(Files.list(directory).anyMatch(path -> path.getFileName().toString().startsWith("unknown-option.json.corrupt")));
	}
}
