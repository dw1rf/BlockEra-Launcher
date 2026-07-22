package space.blockera.client.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChatRouterTest {
	@Test
	void messagesRouteToAllAndEveryMatchingFilterTab() {
		ChatFilterRule trade = new ChatFilterRule("trade", "Trade", List.of("sell"), List.of());
		ChatFilterRule mentions = new ChatFilterRule("mentions", "Mentions", List.of("alex"), List.of());
		ChatConfig config = ChatConfig.defaults();
		config.setFilters(List.of(trade, mentions));
		config.setTabs(List.of(ChatTab.all(),
			new ChatTab("trade", "Trade", List.of("trade")),
			new ChatTab("mentions", "Mentions", List.of("mentions"))));
		ChatRouter<String> router = new ChatRouter<>(config);

		ChatRoutingResult<String> result = router.route("payload", "Alex wants to SELL diamonds", Instant.EPOCH);

		assertEquals(List.of("all", "trade", "mentions"), result.tabIds());
		assertEquals(1, router.history("mentions").size());
	}

	@Test
	void unlimitedAndPreservedHistoryIsNotTrimmedOrCleared() {
		ChatRouter<String> router = new ChatRouter<>(ChatConfig.defaults());
		for (int index = 0; index < 10_000; index++) {
			router.route("payload-" + index, "message", Instant.ofEpochSecond(index));
		}

		router.clear();

		assertEquals(10_000, router.history(ChatTab.ALL_ID).size());
	}
}
