package space.blockera.client.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatFilterRuleTest {
	@Test
	void includeIsCaseInsensitiveAndExcludeAlwaysWins() {
		ChatFilterRule rule = new ChatFilterRule("trade", "Trade",
			List.of("продам", "buy"), List.of("спам"));

		assertTrue(rule.matches("Сегодня ПРОДАМ алмазы"));
		assertTrue(rule.matches("I want to BUY stone"));
		assertFalse(rule.matches("Продам, но это спам"));
	}
}
