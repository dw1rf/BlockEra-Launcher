package space.blockera.client.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatConfigStoreTest {
	@TempDir Path directory;

	@Test
	void layoutFiltersAndUnlimitedHistoryRoundTripWithoutMessages() throws Exception {
		Path file = directory.resolve("chat.json");
		ChatConfig config = ChatConfig.defaults();
		ChatFilterRule filter = new ChatFilterRule("mentions", "Mentions", List.of("alex"), List.of("ignore"));
		config.setFilters(List.of(filter));
		ChatTab mentions = new ChatTab("mentions", "Mentions", List.of("mentions"));
		mentions.setWindowBounds(510, 32, 300, 144);
		config.setTabs(List.of(ChatTab.all(), mentions));
		config.setActiveTab("mentions");
		config.setPosition(88, 64);
		config.setWidth(420);
		config.setFocusedHeight(240);

		ChatConfigStore first = new ChatConfigStore(file);
		first.replace(config);
		first.save();
		ChatConfigStore second = new ChatConfigStore(file);
		second.load();

		assertEquals("mentions", second.config().activeTab());
		assertEquals(88, second.config().left());
		assertEquals(64, second.config().bottomMargin());
		assertEquals(420, second.config().width());
		assertEquals(240, second.config().focusedHeight());
		assertTrue(second.config().tab("mentions").detached());
		assertEquals(510, second.config().tab("mentions").windowLeft());
		assertEquals(32, second.config().tab("mentions").windowTop());
		assertEquals(300, second.config().tab("mentions").windowWidth());
		assertEquals(144, second.config().tab("mentions").windowHeight());
		assertTrue(second.config().unlimitedHistory());
		assertTrue(second.config().preserveHistory());
		assertFalse(Files.exists(file.resolveSibling("chat.json.tmp")));
		assertFalse(Files.readString(file).contains("payload"));
	}

	@Test
	void corruptConfigurationIsBackedUpAndDefaultsAreRestored() throws Exception {
		Path file = directory.resolve("chat.json");
		Files.writeString(file, "{broken");
		ChatConfigStore store = new ChatConfigStore(file);

		store.load();

		assertEquals(ChatTab.ALL_ID, store.config().activeTab());
		assertTrue(Files.list(directory).anyMatch(path -> path.getFileName().toString().startsWith("chat.json.corrupt-")));
	}
}
