package space.blockera.core.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Persisted local chat preferences. No message history or credentials are stored here. */
public final class ChatConfig {
	public static final int SCHEMA_VERSION = 3;
	public static final int MAX_FILTERS = 32;
	public static final int MAX_CUSTOM_TABS = 8;

	private int schemaVersion = SCHEMA_VERSION;
	private String activeTab = ChatTab.ALL_ID;
	private ChatTimestampMode timestampMode = ChatTimestampMode.OFF;
	private boolean use24Hour = true;
	private int width = 320;
	private int openHeight = 180;
	private int closedHeight = 90;
	private float textOpacity = 1.0F;
	private float backgroundOpacity = 0.5F;
	private float lineSpacing;
	private int messageDelaySeconds;
	private List<ChatTab> tabs = new ArrayList<>(List.of(ChatTab.all()));
	private List<ChatFilterRule> filters = new ArrayList<>();

	public ChatConfig() {
	}

	public static ChatConfig defaults() { return new ChatConfig(); }

	public int schemaVersion() { return schemaVersion; }
	public String activeTab() { return activeTab; }
	public ChatTimestampMode timestampMode() { return timestampMode; }
	public boolean use24Hour() { return use24Hour; }
	public int width() { return width; }
	public int openHeight() { return openHeight; }
	public int closedHeight() { return closedHeight; }
	public float textOpacity() { return textOpacity; }
	public float backgroundOpacity() { return backgroundOpacity; }
	public float lineSpacing() { return lineSpacing; }
	public int messageDelaySeconds() { return messageDelaySeconds; }
	public List<ChatTab> tabs() { return Collections.unmodifiableList(tabs); }
	public List<ChatFilterRule> filters() { return Collections.unmodifiableList(filters); }

	public void setActiveTab(String activeTab) { this.activeTab = Objects.requireNonNull(activeTab, "activeTab"); }
	public void setTimestampMode(ChatTimestampMode timestampMode) {
		this.timestampMode = Objects.requireNonNull(timestampMode, "timestampMode");
	}
	public void setUse24Hour(boolean use24Hour) { this.use24Hour = use24Hour; }
	public void setWidth(int width) { this.width = width; }
	public void setOpenHeight(int openHeight) { this.openHeight = openHeight; }
	public void setClosedHeight(int closedHeight) { this.closedHeight = closedHeight; }
	public void setTextOpacity(float textOpacity) { this.textOpacity = textOpacity; }
	public void setBackgroundOpacity(float backgroundOpacity) { this.backgroundOpacity = backgroundOpacity; }
	public void setLineSpacing(float lineSpacing) { this.lineSpacing = lineSpacing; }
	public void setMessageDelaySeconds(int messageDelaySeconds) { this.messageDelaySeconds = messageDelaySeconds; }
	public void setTabs(List<ChatTab> tabs) { this.tabs = new ArrayList<>(Objects.requireNonNull(tabs, "tabs")); }
	public void setFilters(List<ChatFilterRule> filters) {
		this.filters = new ArrayList<>(Objects.requireNonNull(filters, "filters"));
	}

	void migrateLegacy() {
		if (schemaVersion < 1 || schemaVersion >= SCHEMA_VERSION) return;
		int sourceVersion = schemaVersion;
		schemaVersion = SCHEMA_VERSION;
		for (int index = 0; tabs != null && index < tabs.size(); index++) {
			ChatTab tab = tabs.get(index);
			if (sourceVersion == 1) tab.setMatchMode(ChatTabMatchMode.ANY);
			if (sourceVersion == 1) tab.setOrder(index);
			if (sourceVersion == 1 && !tab.filterIds().isEmpty()) {
				String first = tab.filterIds().get(0);
				filters.stream().filter(filter -> filter.id().equals(first)).findFirst()
						.ifPresent(filter -> tab.setColor(filter.color()));
			}
			tab.setDetached(false);
		}
	}

	public ChatFilterRule filter(String id) {
		return filters.stream().filter(filter -> filter.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown chat filter: " + id));
	}

	public ChatTab tab(String id) {
		return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown chat tab: " + id));
	}

	/** Validates the persisted shape and all security/resource limits. */
	public void validate() {
		if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported chat config schema");
		if (timestampMode == null || tabs == null || filters == null) {
			throw new IllegalArgumentException("Incomplete chat config");
		}
		if (filters.size() > MAX_FILTERS) throw new IllegalArgumentException("Too many chat filters");
		if (tabs.size() > MAX_CUSTOM_TABS + 1) throw new IllegalArgumentException("Too many chat tabs");
		validateDimensions();

		Set<String> filterIds = new HashSet<>();
		for (ChatFilterRule filter : filters) {
			if (filter == null) throw new IllegalArgumentException("Missing chat filter");
			filter.validate();
			if (!filterIds.add(filter.id())) throw new IllegalArgumentException("Duplicate chat filter ID: " + filter.id());
		}

		Set<String> tabIds = new HashSet<>();
		for (ChatTab tab : tabs) {
			if (tab == null) throw new IllegalArgumentException("Missing chat tab");
			tab.validate();
			if (!tabIds.add(tab.id())) throw new IllegalArgumentException("Duplicate chat tab ID: " + tab.id());
			for (String filterId : tab.filterIds()) {
				if (!filterIds.contains(filterId)) throw new IllegalArgumentException("Unknown filter in chat tab: " + filterId);
			}
		}
		tabs.sort(java.util.Comparator.comparingInt(ChatTab::order));
		if (!tabIds.contains(ChatTab.ALL_ID)) throw new IllegalArgumentException("The mandatory all chat tab is missing");
		if (activeTab == null || !tabIds.contains(activeTab)) throw new IllegalArgumentException("Unknown active chat tab");
	}

	private void validateDimensions() {
		if (width < 40 || openHeight < 20 || closedHeight < 20 || messageDelaySeconds < 0
				|| !inUnitRange(textOpacity) || !inUnitRange(backgroundOpacity)
				|| !Float.isFinite(lineSpacing) || lineSpacing < 0.0F) {
			throw new IllegalArgumentException("Invalid chat display settings");
		}
	}

	private static boolean inUnitRange(float value) {
		return Float.isFinite(value) && value >= 0.0F && value <= 1.0F;
	}
}
