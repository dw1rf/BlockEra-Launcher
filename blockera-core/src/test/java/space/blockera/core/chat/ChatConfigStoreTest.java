package space.blockera.core.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConfigStoreTest {
	@TempDir Path temporaryDirectory;

	@Test
	void jsonRoundTripPreservesDisplayTabsFiltersAndTimestampSettings() {
		Path path = temporaryDirectory.resolve("chat.json");
		ChatFilterRule mention = new ChatFilterRule("mentions", "Mentions", List.of("alex"), List.of("ignore"));
		mention.setColor(0xFF60D6A7);
		mention.setMention(true);
		mention.setSound(true);
		ChatConfig config = ChatConfig.defaults();
		config.setFilters(List.of(mention));
		ChatTab personal = new ChatTab("personal", "Personal", List.of("mentions"));
		personal.setDetached(true);
		personal.setDetachedPosition(160, 72);
		personal.setDetachedSize(280, 120);
		config.setTabs(List.of(ChatTab.all(), personal));
		config.setActiveTab("personal");
		config.setTimestampMode(ChatTimestampMode.HH_MM_SS);
		config.setUse24Hour(false);
		config.setWidth(420);
		config.setOpenHeight(220);
		config.setClosedHeight(70);
		config.setTextOpacity(0.8F);
		config.setBackgroundOpacity(0.3F);
		config.setLineSpacing(2.0F);
		config.setMessageDelaySeconds(5);

		ChatConfigStore first = new ChatConfigStore(path);
		first.replace(config);
		first.save();
		ChatConfigStore second = new ChatConfigStore(path);
		second.load();
		ChatConfig restored = second.config();

		assertEquals(ChatConfig.SCHEMA_VERSION, restored.schemaVersion());
		assertEquals("personal", restored.activeTab());
		assertEquals(ChatTimestampMode.HH_MM_SS, restored.timestampMode());
		assertFalse(restored.use24Hour());
		assertEquals(420, restored.width());
		assertEquals(220, restored.openHeight());
		assertEquals(70, restored.closedHeight());
		assertEquals(0.8F, restored.textOpacity());
		assertEquals(0.3F, restored.backgroundOpacity());
		assertEquals(2.0F, restored.lineSpacing());
		assertEquals(5, restored.messageDelaySeconds());
		assertEquals(0xFF60D6A7, restored.filter("mentions").color());
		assertTrue(restored.filter("mentions").mention());
		assertTrue(restored.filter("mentions").sound());
		assertEquals(List.of("mentions"), restored.tab("personal").filterIds());
		assertTrue(restored.tab("personal").detached());
		assertEquals(160, restored.tab("personal").detachedX());
		assertEquals(72, restored.tab("personal").detachedY());
		assertEquals(280, restored.tab("personal").detachedWidth());
		assertEquals(120, restored.tab("personal").detachedHeight());
		assertFalse(restored.tab(ChatTab.ALL_ID).detached());
		assertFalse(Files.exists(path.resolveSibling("chat.json.tmp")));
	}

	@Test
	void corruptJsonIsBackedUpAndSafeDefaultsAreLoaded() throws IOException {
		Path path = temporaryDirectory.resolve("chat.json");
		Files.writeString(path, "{broken json");
		ChatConfigStore store = new ChatConfigStore(path);
		store.load();

		assertEquals(ChatTab.ALL_ID, store.config().activeTab());
		assertEquals(List.of(ChatTab.ALL_ID), store.config().tabs().stream().map(ChatTab::id).toList());
		assertFalse(Files.exists(path));
		try (var entries = Files.list(temporaryDirectory)) {
			assertEquals(1L, entries.filter(file -> file.getFileName().toString().startsWith("chat.json.corrupt-")).count());
		}
	}

	@Test
	void invalidSchemaOrMissingAllTabIsRejectedAndBackedUp() throws IOException {
		Path path = temporaryDirectory.resolve("chat.json");
		Files.writeString(path, """
				{"schemaVersion":2,"activeTab":"all","timestampMode":"OFF","tabs":[],"filters":[]}
				""");
		ChatConfigStore store = new ChatConfigStore(path);
		store.load();
		assertEquals(ChatConfig.SCHEMA_VERSION, store.config().schemaVersion());

		Files.writeString(path, """
				{"schemaVersion":1,"activeTab":"custom","timestampMode":"OFF","use24Hour":true,
				 "width":320,"openHeight":180,"closedHeight":90,"textOpacity":1.0,"backgroundOpacity":0.5,
				 "lineSpacing":0.0,"messageDelaySeconds":0,
				 "tabs":[{"id":"custom","name":"Custom","filterIds":[]}],"filters":[]}
				""");
		store.load();
		assertEquals(ChatTab.ALL_ID, store.config().activeTab());
	}

	@Test
	void validationEnforcesFilterAndCustomTabLimitsAndReferences() {
		ChatConfig tooManyFilters = ChatConfig.defaults();
		List<ChatFilterRule> filters = new ArrayList<>();
		for (int index = 0; index <= ChatConfig.MAX_FILTERS; index++) {
			filters.add(new ChatFilterRule("f" + index, "Filter " + index, List.of("x"), List.of()));
		}
		tooManyFilters.setFilters(filters);
		assertThrows(IllegalArgumentException.class, tooManyFilters::validate);

		ChatConfig tooManyTabs = ChatConfig.defaults();
		List<ChatTab> tabs = new ArrayList<>();
		tabs.add(ChatTab.all());
		for (int index = 0; index <= ChatConfig.MAX_CUSTOM_TABS; index++) {
			tabs.add(new ChatTab("tab" + index, "Tab " + index, List.of()));
		}
		tooManyTabs.setTabs(tabs);
		assertThrows(IllegalArgumentException.class, tooManyTabs::validate);

		ChatConfig badReference = ChatConfig.defaults();
		badReference.setTabs(List.of(ChatTab.all(), new ChatTab("bad", "Bad", List.of("missing"))));
		assertThrows(IllegalArgumentException.class, badReference::validate);
	}

	@Test
	void restoreJsonValidatesBeforeReplacingLiveConfig() {
		ChatConfigStore store = new ChatConfigStore(temporaryDirectory.resolve("chat.json"));
		String invalid = "{\"schemaVersion\":1,\"activeTab\":\"all\",\"timestampMode\":\"OFF\",\"tabs\":[],\"filters\":[]}";

		assertThrows(IllegalArgumentException.class, () -> store.restoreJson(invalid));
		assertEquals(ChatTab.ALL_ID, store.config().activeTab());
	}

	@Test
	void schemaOneMigratesTabsToAnyModeAndKeepsTheirFilters() throws IOException {
		Path path = temporaryDirectory.resolve("chat.json");
		Files.writeString(path, """
				{"schemaVersion":1,"activeTab":"personal","timestampMode":"OFF","use24Hour":true,
				 "width":320,"openHeight":180,"closedHeight":90,"textOpacity":1.0,"backgroundOpacity":0.5,
				 "lineSpacing":0.0,"messageDelaySeconds":0,
				 "tabs":[{"id":"all","name":"All","filterIds":[]},{"id":"personal","name":"Personal","filterIds":["mention"]}],
				 "filters":[{"id":"mention","name":"Mention","include":["alex"],"exclude":[],"color":-10430809,"enabled":true}]}
				""");
		ChatConfigStore store = new ChatConfigStore(path);
		store.load();

		assertEquals(ChatConfig.SCHEMA_VERSION, store.config().schemaVersion());
		assertEquals(ChatTabMatchMode.ANY, store.config().tab("personal").matchMode());
		assertEquals(List.of("mention"), store.config().tab("personal").filterIds());
		assertEquals(-10430809, store.config().tab("personal").color());
	}
}
