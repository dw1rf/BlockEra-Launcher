package space.blockera.client.chat;

import java.time.Instant;
import java.util.List;

public record ChatRoutingResult<T>(
	T message,
	String searchableText,
	Instant receivedAt,
	List<String> tabIds,
	List<ChatFilterRule> matchedFilters
) {
}
