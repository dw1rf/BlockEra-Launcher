package space.blockera.core.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterRuleTest {
	@Test
	void includeIsCaseInsensitiveAndExcludeAlwaysWins() {
		ChatFilterRule rule = new ChatFilterRule("trade", "Trade",
				List.of("ПРОДАМ", "buy"), List.of("спам"));

		assertTrue(rule.matches("Сегодня продам алмазы"));
		assertTrue(rule.matches("I want to BUY stone"));
		assertFalse(rule.matches("ПРОДАМ, но это СПАМ"));
		assertFalse(rule.matches("обычное сообщение"));
	}

	@Test
	void disabledOrEmptyIncludeRuleNeverMatches() {
		ChatFilterRule disabled = new ChatFilterRule("disabled", "Disabled", List.of("hello"), List.of());
		disabled.setEnabled(false);
		ChatFilterRule empty = new ChatFilterRule("empty", "Empty", List.of(), List.of());

		assertFalse(disabled.matches("hello"));
		assertFalse(empty.matches("anything"));
	}

	@Test
	void configValidationEnforcesPhraseCountAndLength() {
		ChatFilterRule tooMany = new ChatFilterRule("many", "Many",
				new ArrayList<>(java.util.Collections.nCopies(ChatFilterRule.MAX_PHRASES + 1, "x")), List.of());
		ChatConfig first = configWith(tooMany);
		assertThrows(IllegalArgumentException.class, first::validate);

		ChatFilterRule tooLong = new ChatFilterRule("long", "Long",
				List.of("x".repeat(ChatFilterRule.MAX_PHRASE_LENGTH + 1)), List.of());
		ChatConfig second = configWith(tooLong);
		assertThrows(IllegalArgumentException.class, second::validate);
	}

	private static ChatConfig configWith(ChatFilterRule rule) {
		ChatConfig config = ChatConfig.defaults();
		config.setFilters(List.of(rule));
		return config;
	}
}
