package space.blockera.core.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolConfigStoreTest {
	@TempDir Path directory;
	@Test void markerAndAutoTextRoundTripLocally() {
		Path path = directory.resolve("tools.json");
		LocalToolConfigStore first = new LocalToolConfigStore(path);
		first.config().setAutoTextTemplate("hello");
		first.config().putMarker("server|dimension", new LocalToolConfig.Marker("dimension", 1, 2, 3));
		first.save();
		LocalToolConfigStore second = new LocalToolConfigStore(path); second.load();
		assertEquals("hello", second.config().autoTextTemplate());
		assertEquals(2, second.config().marker("server|dimension").y());
	}
	@Test void corruptFileIsBackedUp() throws Exception {
		Path path = directory.resolve("tools.json"); Files.writeString(path, "{broken");
		LocalToolConfigStore store = new LocalToolConfigStore(path); store.load();
		assertFalse(Files.exists(path));
		try (var files = Files.list(directory)) { assertTrue(files.anyMatch(file -> file.getFileName().toString().startsWith("tools.json.corrupt-"))); }
	}
}
