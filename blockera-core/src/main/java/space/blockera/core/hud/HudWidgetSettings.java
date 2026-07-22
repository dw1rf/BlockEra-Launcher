package space.blockera.core.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable per-widget settings. Values are validated after JSON deserialization. */
public final class HudWidgetSettings {
	public boolean enabled;
	public HudAnchor anchor = HudAnchor.TOP_LEFT;
	public int offsetX = 12;
	public int offsetY = 12;
	public float scale = 1.0F;
	public float opacity = 0.9F;
	public boolean background = true;
	public String labelColor = "#9C98AE";
	public String valueColor = "#F8F7FF";
	public int width = -1;
	public int height = -1;
	public int zIndex;
	public boolean locked;
	public boolean showLabel = true;
	public boolean showIcon = true;
	public boolean compact;
	public String orientation = "horizontal";
	public Map<String, String> options = new LinkedHashMap<>();
	public ClockWidgetSettings clock = new ClockWidgetSettings();

	public void validate() {
		if (anchor == null) anchor = HudAnchor.TOP_LEFT;
		scale = Math.max(0.5F, Math.min(2.0F, scale));
		opacity = Math.max(0.0F, Math.min(1.0F, opacity));
		labelColor = validColor(labelColor, "#9C98AE");
		valueColor = validColor(valueColor, "#F8F7FF");
		width = Math.max(-1, width);
		height = Math.max(-1, height);
		zIndex = Math.max(-1000, Math.min(1000, zIndex));
		if (clock == null) clock = new ClockWidgetSettings();
		if (orientation == null || !(orientation.equals("horizontal") || orientation.equals("vertical"))) orientation = "horizontal";
		if (options == null) options = new LinkedHashMap<>();
		options.entrySet().removeIf(entry -> entry.getKey() == null || !entry.getKey().matches("[a-z][a-z0-9_]{0,31}")
				|| entry.getValue() == null || entry.getValue().length() > 128);
		clock.validate();
	}

	public HudWidgetSettings copy() {
		HudWidgetSettings copy = new HudWidgetSettings();
		copy.enabled = enabled;
		copy.anchor = anchor;
		copy.offsetX = offsetX;
		copy.offsetY = offsetY;
		copy.scale = scale;
		copy.opacity = opacity;
		copy.background = background;
		copy.labelColor = labelColor;
		copy.valueColor = valueColor;
		copy.width = width;
		copy.height = height;
		copy.zIndex = zIndex;
		copy.locked = locked;
		copy.showLabel = showLabel;
		copy.showIcon = showIcon;
		copy.compact = compact;
		copy.orientation = orientation;
		copy.options = new LinkedHashMap<>(options);
		copy.clock = clock.copy();
		return copy;
	}

	public int labelArgb() { return parseColor(labelColor, opacity); }
	public int valueArgb() { return parseColor(valueColor, opacity); }

	private static String validColor(String value, String fallback) {
		return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase() : fallback;
	}

	private static int parseColor(String value, float opacity) {
		int rgb = Integer.parseInt(value.substring(1), 16);
		return (Math.round(255.0F * opacity) << 24) | rgb;
	}
}
