package space.blockera.core.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A chat view backed by one or more first-party local filter rules. */
public final class ChatTab {
	public static final String ALL_ID = "all";

	private String id = ALL_ID;
	private String name = "All";
	private List<String> filterIds = new ArrayList<>();
	private int color = 0xFF9B7BFF;
	private int order;
	private ChatTabMatchMode matchMode = ChatTabMatchMode.ANY;
	private boolean detached;
	private int detachedX = 24;
	private int detachedY = 44;
	private int detachedWidth = 220;
	private int detachedHeight = 96;

	public ChatTab() {
	}

	public ChatTab(String id, String name, List<String> filterIds) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.filterIds = new ArrayList<>(Objects.requireNonNull(filterIds, "filterIds"));
	}

	public static ChatTab all() {
		return new ChatTab(ALL_ID, "All", List.of());
	}

	public String id() { return id; }
	public String name() { return name; }
	public List<String> filterIds() { return Collections.unmodifiableList(filterIds); }
	public int color() { return color; }
	public ChatTabMatchMode matchMode() { return matchMode; }
	public int order() { return order; }
	public boolean detached() { return detached; }
	public int detachedX() { return detachedX; }
	public int detachedY() { return detachedY; }
	public int detachedWidth() { return detachedWidth; }
	public int detachedHeight() { return detachedHeight; }

	public void setId(String id) { this.id = Objects.requireNonNull(id, "id"); }
	public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }
	public void setFilterIds(List<String> filterIds) {
		this.filterIds = new ArrayList<>(Objects.requireNonNull(filterIds, "filterIds"));
	}
	public void setColor(int color) { this.color = 0xFF000000 | color; }
	public void setOrder(int order) { this.order = Math.max(0, Math.min(100, order)); }
	public void setMatchMode(ChatTabMatchMode matchMode) { this.matchMode = Objects.requireNonNull(matchMode, "matchMode"); }
	public void setDetached(boolean detached) { this.detached = !ALL_ID.equals(id) && detached; }
	public void setDetachedPosition(int x, int y) { detachedX = Math.max(0, x); detachedY = Math.max(0, y); }
	public void setDetachedSize(int width, int height) {
		detachedWidth = Math.max(120, Math.min(640, width));
		detachedHeight = Math.max(56, Math.min(360, height));
	}

	void validate() {
		ChatValidation.requireIdentifier(id, "tab");
		ChatValidation.requireName(name, "tab");
		if (filterIds == null) throw new IllegalArgumentException("Chat tab filter IDs are required");
		if (matchMode == null) matchMode = ChatTabMatchMode.ANY;
		if (ALL_ID.equals(id) && !filterIds.isEmpty()) {
			throw new IllegalArgumentException("The mandatory all tab cannot be filtered");
		}
		if (ALL_ID.equals(id)) detached = false;
		setDetachedPosition(detachedX, detachedY);
		setDetachedSize(detachedWidth, detachedHeight);
		for (String filterId : filterIds) ChatValidation.requireIdentifier(filterId, "filter");
	}
}
