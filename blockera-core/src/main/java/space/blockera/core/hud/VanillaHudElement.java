package space.blockera.core.hud;

import java.util.Arrays;

/** Closed first-party allow-list of vanilla HUD groups exposed by the editor. */
public enum VanillaHudElement {
	HOTBAR("hotbar", 182, 62, HudAnchor.BOTTOM_CENTER, 0, -2),
	CHAT("chat", 320, 90, HudAnchor.BOTTOM_LEFT, 2, -40),
	BOSSBAR("bossbar", 182, 20, HudAnchor.TOP_CENTER, 0, 12),
	SCOREBOARD("scoreboard", 120, 80, HudAnchor.CENTER_RIGHT, -3, 0),
	EFFECTS("effects", 100, 52, HudAnchor.TOP_RIGHT, -2, 2),
	CROSSHAIR("crosshair", 15, 15, HudAnchor.CENTER, 0, 0);

	private final String id;
	private final int width;
	private final int height;
	private final HudAnchor defaultAnchor;
	private final int defaultOffsetX;
	private final int defaultOffsetY;

	VanillaHudElement(String id, int width, int height, HudAnchor defaultAnchor, int defaultOffsetX, int defaultOffsetY) {
		this.id = id;
		this.width = width;
		this.height = height;
		this.defaultAnchor = defaultAnchor;
		this.defaultOffsetX = defaultOffsetX;
		this.defaultOffsetY = defaultOffsetY;
	}

	public String id() { return id; }
	public int width() { return width; }
	public int height() { return height; }

	public VanillaHudSettings defaults() {
		VanillaHudSettings settings = new VanillaHudSettings();
		settings.anchor = defaultAnchor;
		settings.offsetX = defaultOffsetX;
		settings.offsetY = defaultOffsetY;
		return settings;
	}

	public HudPoint defaultPosition(int screenWidth, int screenHeight) {
		return defaultAnchor.resolve(screenWidth, screenHeight, width, height, 1.0F, defaultOffsetX, defaultOffsetY);
	}

	public static VanillaHudElement byId(String id) {
		return Arrays.stream(values()).filter(element -> element.id.equals(id)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown vanilla HUD element: " + id));
	}
}
