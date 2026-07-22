package space.blockera.core.chat;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable routing metadata that keeps the original rich message payload intact. */
public record ChatRoutingResult<T>(
		T message,
		String searchableText,
		Instant receivedAt,
		List<String> tabIds,
		List<ChatFilterRule> matchedFilters,
		boolean mention,
		boolean sound) {
	public ChatRoutingResult {
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(searchableText, "searchableText");
		Objects.requireNonNull(receivedAt, "receivedAt");
		tabIds = List.copyOf(Objects.requireNonNull(tabIds, "tabIds"));
		matchedFilters = List.copyOf(Objects.requireNonNull(matchedFilters, "matchedFilters"));
	}
}
