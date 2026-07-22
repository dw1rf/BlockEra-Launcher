package space.blockera.core.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRouterTest {
	@Test
	void messageAlwaysRoutesToAllAndAlsoToEveryMatchingTab() {
		ChatFilterRule trade = new ChatFilterRule("trade", "Trade", List.of("sell"), List.of("scam"));
		ChatFilterRule mentions = new ChatFilterRule("mentions", "Mentions", List.of("alex"), List.of());
		mentions.setMention(true);
		mentions.setSound(true);
		ChatConfig config = ChatConfig.defaults();
		config.setFilters(List.of(trade, mentions));
		config.setTabs(List.of(
				ChatTab.all(),
				new ChatTab("market", "Market", List.of("trade")),
				new ChatTab("personal", "Personal", List.of("mentions")),
				new ChatTab("combined", "Combined", List.of("trade", "mentions"))));
		ChatRouter<Object> router = new ChatRouter<>(config);
		Object richPayload = new Object();

		ChatRoutingResult<Object> result = router.route(richPayload, "Alex wants to SELL diamonds", Instant.EPOCH);

		assertSame(richPayload, result.message());
		assertEquals(List.of("all", "market", "personal", "combined"), result.tabIds());
		assertEquals(List.of("trade", "mentions"), result.matchedFilters().stream().map(ChatFilterRule::id).toList());
		assertTrue(result.mention());
		assertTrue(result.sound());
		assertSame(result, router.history("personal").get(0));
	}

	@Test
	void excludeVetoPreventsFilteredRoutingAndMentionSideEffects() {
		ChatFilterRule rule = new ChatFilterRule("mention", "Mention", List.of("alex"), List.of("ignore"));
		rule.setMention(true);
		rule.setSound(true);
		ChatConfig config = ChatConfig.defaults();
		config.setFilters(List.of(rule));
		config.setTabs(List.of(ChatTab.all(), new ChatTab("personal", "Personal", List.of("mention"))));
		ChatRouter<String> router = new ChatRouter<>(config);

		ChatRoutingResult<String> result = router.route("payload", "alex ignore", Instant.EPOCH);

		assertEquals(List.of("all"), result.tabIds());
		assertTrue(result.matchedFilters().isEmpty());
		assertFalse(result.mention());
		assertFalse(result.sound());
		assertTrue(router.history("personal").isEmpty());
	}

	@Test
	void perTabHistoryIsSessionOnlyAndCappedAtFiveHundredMessages() {
		ChatRouter<String> router = new ChatRouter<>(ChatConfig.defaults());
		for (int index = 0; index < ChatRouter.HISTORY_LIMIT + 7; index++) {
			router.route("payload-" + index, "message " + index, Instant.ofEpochSecond(index));
		}

		List<ChatRoutingResult<String>> history = router.history(ChatTab.ALL_ID);
		assertEquals(ChatRouter.HISTORY_LIMIT, history.size());
		assertEquals("payload-7", history.get(0).message());
		assertEquals("payload-506", history.get(history.size() - 1).message());
		router.clear();
		assertTrue(router.history(ChatTab.ALL_ID).isEmpty());
	}

	@Test void allModeRequiresEveryAssignedFilter() {
		ChatFilterRule first = new ChatFilterRule("first", "First", List.of("alpha"), List.of());
		ChatFilterRule second = new ChatFilterRule("second", "Second", List.of("beta"), List.of());
		ChatTab combined = new ChatTab("combined", "Combined", List.of("first", "second"));
		combined.setMatchMode(ChatTabMatchMode.ALL);
		ChatConfig config = ChatConfig.defaults(); config.setFilters(List.of(first, second)); config.setTabs(List.of(ChatTab.all(), combined));
		ChatRouter<String> router = new ChatRouter<>(config);
		assertEquals(List.of("all"), router.route("one", "alpha", Instant.EPOCH).tabIds());
		assertEquals(List.of("all", "combined"), router.route("two", "alpha beta", Instant.EPOCH).tabIds());
	}
}
