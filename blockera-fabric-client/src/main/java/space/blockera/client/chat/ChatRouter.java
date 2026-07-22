package space.blockera.client.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Routes messages into per-filter histories. Unlimited mode intentionally retains the full launch session. */
public final class ChatRouter<T> {
	public static final int SAFE_HISTORY_LIMIT = 500;

	private final ChatConfig config;
	private final Map<String, List<ChatRoutingResult<T>>> histories = new LinkedHashMap<>();

	public ChatRouter(ChatConfig config) {
		this.config = Objects.requireNonNull(config, "config");
		config.validate();
		for (ChatTab tab : config.tabs()) histories.put(tab.id(), new ArrayList<>());
	}

	public synchronized ChatRoutingResult<T> route(T message, String searchableText, Instant receivedAt) {
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(searchableText, "searchableText");
		Objects.requireNonNull(receivedAt, "receivedAt");
		List<ChatFilterRule> matches = new ArrayList<>();
		Set<String> matchedIds = new LinkedHashSet<>();
		for (ChatFilterRule filter : config.filters()) {
			if (filter.matches(searchableText)) {
				matches.add(filter);
				matchedIds.add(filter.id());
			}
		}
		List<String> routedTabs = new ArrayList<>();
		routedTabs.add(ChatTab.ALL_ID);
		for (ChatTab tab : config.tabs()) {
			if (!ChatTab.ALL_ID.equals(tab.id()) && tab.filterIds().stream().anyMatch(matchedIds::contains)) {
				routedTabs.add(tab.id());
			}
		}
		ChatRoutingResult<T> result = new ChatRoutingResult<>(message, searchableText, receivedAt,
			List.copyOf(routedTabs), List.copyOf(matches));
		for (String tabId : routedTabs) append(tabId, result);
		return result;
	}

	public synchronized List<ChatRoutingResult<T>> history(String tabId) {
		List<ChatRoutingResult<T>> history = histories.get(tabId);
		if (history == null) throw new IllegalArgumentException("Unknown chat tab: " + tabId);
		return Collections.unmodifiableList(new ArrayList<>(history));
	}

	public synchronized void clear() {
		if (!config.preserveHistory()) histories.values().forEach(List::clear);
	}

	public synchronized void reconfigure() {
		config.validate();
		Map<String, List<ChatRoutingResult<T>>> updated = new LinkedHashMap<>();
		for (ChatTab tab : config.tabs()) {
			updated.put(tab.id(), histories.getOrDefault(tab.id(), new ArrayList<>()));
		}
		histories.clear();
		histories.putAll(updated);
	}

	private void append(String tabId, ChatRoutingResult<T> result) {
		List<ChatRoutingResult<T>> history = histories.get(tabId);
		if (history == null) throw new IllegalStateException("Missing history for configured chat tab");
		history.add(result);
		if (!config.unlimitedHistory() && history.size() > SAFE_HISTORY_LIMIT) {
			history.subList(0, history.size() - SAFE_HISTORY_LIMIT).clear();
		}
	}
}
