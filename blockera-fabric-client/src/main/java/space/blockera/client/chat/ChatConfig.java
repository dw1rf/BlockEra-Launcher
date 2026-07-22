package space.blockera.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Persisted display and routing preferences. Chat messages are never serialized. */
public final class ChatConfig {
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_FILTERS = 16;
	public static final int MAX_TABS = 17;
	public static final int MIN_WIDTH = 120;
	public static final int MIN_HEIGHT = 54;

	private int schemaVersion = SCHEMA_VERSION;
	private String activeTab = ChatTab.ALL_ID;
	private int left = 4;
	private int bottomMargin = 40;
	private int width = 320;
	private int focusedHeight = 180;
	private int unfocusedHeight = 90;
	private boolean unlimitedHistory = true;
	private boolean preserveHistory = true;
	private List<ChatTab> tabs = new ArrayList<>(List.of(ChatTab.all()));
	private List<ChatFilterRule> filters = new ArrayList<>();

	public static ChatConfig defaults() { return new ChatConfig(); }

	public int schemaVersion() { return schemaVersion; }
	public String activeTab() { return activeTab; }
	public int left() { return left; }
	public int bottomMargin() { return bottomMargin; }
	public int width() { return width; }
	public int focusedHeight() { return focusedHeight; }
	public int unfocusedHeight() { return unfocusedHeight; }
	public boolean unlimitedHistory() { return unlimitedHistory; }
	public boolean preserveHistory() { return preserveHistory; }
	public List<ChatTab> tabs() { return Collections.unmodifiableList(tabs); }
	public List<ChatFilterRule> filters() { return Collections.unmodifiableList(filters); }

	public void setActiveTab(String activeTab) { this.activeTab = Objects.requireNonNull(activeTab, "activeTab"); }
	public void setPosition(int left, int bottomMargin) {
		this.left = Math.max(0, left);
		this.bottomMargin = Math.max(0, bottomMargin);
	}
	public void setWidth(int width) { this.width = width; }
	public void setFocusedHeight(int focusedHeight) { this.focusedHeight = focusedHeight; }
	public void setUnfocusedHeight(int unfocusedHeight) { this.unfocusedHeight = unfocusedHeight; }
	public void setUnlimitedHistory(boolean unlimitedHistory) { this.unlimitedHistory = unlimitedHistory; }
	public void setPreserveHistory(boolean preserveHistory) { this.preserveHistory = preserveHistory; }
	public void setTabs(List<ChatTab> tabs) { this.tabs = new ArrayList<>(Objects.requireNonNull(tabs, "tabs")); }
	public void setFilters(List<ChatFilterRule> filters) {
		this.filters = new ArrayList<>(Objects.requireNonNull(filters, "filters"));
	}

	public ChatTab tab(String id) {
		return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown chat tab: " + id));
	}

	public ChatFilterRule filter(String id) {
		return filters.stream().filter(filter -> filter.id().equals(id)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown chat filter: " + id));
	}

	public void validate() {
		if (schemaVersion != SCHEMA_VERSION || activeTab == null || tabs == null || filters == null) {
			throw new IllegalArgumentException("Incomplete chat configuration");
		}
		if (width < MIN_WIDTH || focusedHeight < MIN_HEIGHT || unfocusedHeight < 20
			|| left < 0 || bottomMargin < 0) {
			throw new IllegalArgumentException("Invalid chat geometry");
		}
		if (filters.size() > MAX_FILTERS || tabs.size() > MAX_TABS) {
			throw new IllegalArgumentException("Too many chat filters or tabs");
		}
		Set<String> filterIds = new HashSet<>();
		for (ChatFilterRule filter : filters) {
			if (filter == null) throw new IllegalArgumentException("Missing chat filter");
			filter.validate();
			if (!filterIds.add(filter.id())) throw new IllegalArgumentException("Duplicate chat filter ID");
		}
		Set<String> tabIds = new HashSet<>();
		for (ChatTab tab : tabs) {
			if (tab == null) throw new IllegalArgumentException("Missing chat tab");
			tab.validate();
			if (!tabIds.add(tab.id())) throw new IllegalArgumentException("Duplicate chat tab ID");
			for (String filterId : tab.filterIds()) {
				if (!filterIds.contains(filterId)) throw new IllegalArgumentException("Unknown filter in chat tab");
			}
		}
		if (!tabIds.contains(ChatTab.ALL_ID) || !tabIds.contains(activeTab)) {
			throw new IllegalArgumentException("Missing all tab or invalid active tab");
		}
	}
}
