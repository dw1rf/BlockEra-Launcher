package space.blockera.core.chat;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Routes local chat messages and owns bounded, session-only per-tab history. */
public final class ChatRouter<T> {
	public static final int HISTORY_LIMIT = 500;

	private final ChatConfig config;
	private final Map<String, Deque<ChatRoutingResult<T>>> histories = new LinkedHashMap<>();

	public ChatRouter(ChatConfig config) {
		this.config = Objects.requireNonNull(config, "config");
		config.validate();
		for (ChatTab tab : config.tabs()) histories.put(tab.id(), new ArrayDeque<>());
	}

	public synchronized ChatRoutingResult<T> route(T message, String searchableText, Instant receivedAt) {
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(searchableText, "searchableText");
		Objects.requireNonNull(receivedAt, "receivedAt");

		List<ChatFilterRule> matches = new ArrayList<>();
		Set<String> matchedIds = new LinkedHashSet<>();
		boolean mention = false;
		boolean sound = false;
		for (ChatFilterRule filter : config.filters()) {
			if (!filter.matches(searchableText)) continue;
			matches.add(filter);
			matchedIds.add(filter.id());
			mention |= filter.mention();
			sound |= filter.sound();
		}

		List<String> routedTabs = new ArrayList<>();
		routedTabs.add(ChatTab.ALL_ID);
		for (ChatTab tab : config.tabs()) {
			if (ChatTab.ALL_ID.equals(tab.id())) continue;
			boolean routed = tab.matchMode() == ChatTabMatchMode.ALL
					? !tab.filterIds().isEmpty() && tab.filterIds().stream().allMatch(matchedIds::contains)
					: tab.filterIds().stream().anyMatch(matchedIds::contains);
			if (routed) routedTabs.add(tab.id());
		}

		ChatRoutingResult<T> result = new ChatRoutingResult<>(message, searchableText, receivedAt,
				routedTabs, matches, mention, sound);
		for (String tabId : routedTabs) append(tabId, result);
		return result;
	}

	public synchronized List<ChatRoutingResult<T>> history(String tabId) {
		Deque<ChatRoutingResult<T>> history = histories.get(tabId);
		if (history == null) throw new IllegalArgumentException("Unknown chat tab: " + tabId);
		return Collections.unmodifiableList(new ArrayList<>(history));
	}

	public synchronized void clear() {
		histories.values().forEach(Deque::clear);
	}

	/** Applies tab/filter edits without discarding the session-only history of tabs that still exist. */
	public synchronized void reconfigure() {
		config.validate();
		Map<String, Deque<ChatRoutingResult<T>>> updated = new LinkedHashMap<>();
		for (ChatTab tab : config.tabs()) {
			Deque<ChatRoutingResult<T>> existing = histories.get(tab.id());
			updated.put(tab.id(), existing == null ? new ArrayDeque<>() : existing);
		}
		histories.clear();
		histories.putAll(updated);
	}

	private void append(String tabId, ChatRoutingResult<T> result) {
		Deque<ChatRoutingResult<T>> history = histories.get(tabId);
		if (history == null) throw new IllegalStateException("Missing history for configured chat tab: " + tabId);
		history.addLast(result);
		while (history.size() > HISTORY_LIMIT) history.removeFirst();
	}
}
