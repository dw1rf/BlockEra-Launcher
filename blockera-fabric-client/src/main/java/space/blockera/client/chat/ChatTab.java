package space.blockera.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A visible chat page backed by one or more local filters. */
public final class ChatTab {
	public static final String ALL_ID = "all";

	private String id = ALL_ID;
	private String name = "All";
	private List<String> filterIds = new ArrayList<>();
	private int color = 0xFF168ED1;
	private boolean detached = true;
	private boolean background = true;
	private int windowLeft = -1;
	private int windowTop = -1;
	private int windowWidth = 280;
	private int windowHeight = 130;

	public ChatTab() {
	}

	public ChatTab(String id, String name, List<String> filterIds) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.filterIds = new ArrayList<>(Objects.requireNonNull(filterIds, "filterIds"));
	}

	public static ChatTab all() {
		ChatTab tab = new ChatTab(ALL_ID, "All", List.of());
		tab.detached = false;
		return tab;
	}

	public String id() { return id; }
	public String name() { return name; }
	public List<String> filterIds() { return Collections.unmodifiableList(filterIds); }
	public int color() { return color; }
	public boolean detached() { return !ALL_ID.equals(id) && detached; }
	public boolean background() { return background; }
	public int windowLeft() { return windowLeft; }
	public int windowTop() { return windowTop; }
	public int windowWidth() { return windowWidth; }
	public int windowHeight() { return windowHeight; }

	public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }
	public void setColor(int color) { this.color = 0xFF000000 | color; }
	public void setDetached(boolean detached) { this.detached = !ALL_ID.equals(id) && detached; }
	public void setBackground(boolean background) { this.background = background; }
	public void setWindowBounds(int left, int top, int width, int height) {
		windowLeft = Math.max(0, left);
		windowTop = Math.max(0, top);
		windowWidth = Math.max(140, width);
		windowHeight = Math.max(70, height);
	}

	void validate() {
		ChatValidation.requireIdentifier(id, "tab");
		ChatValidation.requireName(name, "tab");
		if (ALL_ID.equals(id)) detached = false;
		if (filterIds == null) {
			throw new IllegalArgumentException("Chat tab filter IDs are required");
		}
		if (ALL_ID.equals(id) && !filterIds.isEmpty()) {
			throw new IllegalArgumentException("The all tab cannot be filtered");
		}
		if (windowLeft < -1 || windowTop < -1 || windowWidth < 140 || windowHeight < 70) {
			throw new IllegalArgumentException("Invalid detached chat geometry");
		}
		for (String filterId : filterIds) {
			ChatValidation.requireIdentifier(filterId, "filter");
		}
	}
}
