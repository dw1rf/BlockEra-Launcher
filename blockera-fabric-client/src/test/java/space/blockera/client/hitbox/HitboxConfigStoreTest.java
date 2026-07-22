package space.blockera.client.hitbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class HitboxConfigStoreTest {
    @TempDir Path directory;

    @Test
    void categoriesAreIndependentAndValuesAreClamped() {
        HitboxConfigStore store = new HitboxConfigStore(directory.resolve("hitboxes.json"));
        store.load();
        store.setEnabled(HitboxCategory.PLAYER, true);
        store.adjustOpacity(HitboxCategory.PLAYER, -10.0F);
        store.adjustLineWidth(20.0F);
        assertTrue(store.config().players().enabled());
        assertFalse(store.config().animals().enabled());
        assertEquals(0.05F, store.config().players().opacity());
        assertEquals(4.0F, store.config().lineWidth());
    }

    @Test
    void corruptConfigIsBackedUpAndReplacedWithDisabledDefaults() throws Exception {
        Path file = directory.resolve("hitboxes.json");
        Files.writeString(file, "{");
        HitboxConfigStore store = new HitboxConfigStore(file);
        store.load();
        assertTrue(Files.exists(directory.resolve("hitboxes.json.corrupt")));
        assertFalse(store.config().players().enabled());
        assertFalse(store.config().animals().enabled());
        assertFalse(store.config().items().enabled());
    }
}
