package space.blockera.core.enhancement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitboxConfigStoreTest {
	@TempDir Path directory;

	@Test void roundTripClampsAndPreservesCategoryAppearance() {
		Path path = directory.resolve("hitboxes.json");
		HitboxConfigStore first = new HitboxConfigStore(path);
		first.config().setLineWidth(9.0F);
		first.config().players().setColor("#112233");
		first.config().players().setOpacity(0.4F);
		first.save();
		HitboxConfigStore second = new HitboxConfigStore(path); second.load();
		assertEquals(4.0F, second.config().lineWidth());
		assertEquals("#112233", second.config().players().color());
		assertEquals(0.4F, second.config().players().opacity());
	}

	@Test void corruptFileIsBackedUp() throws Exception {
		Path path = directory.resolve("hitboxes.json"); Files.writeString(path, "{broken");
		HitboxConfigStore store = new HitboxConfigStore(path); store.load();
		assertFalse(Files.exists(path));
		try (var files = Files.list(directory)) {
			assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith("hitboxes.json.corrupt-")));
		}
	}
}
