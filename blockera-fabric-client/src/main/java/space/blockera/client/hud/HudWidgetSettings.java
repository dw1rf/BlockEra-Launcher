package space.blockera.client.hud;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudWidgetSettings {
	public boolean enabled;
	public HudAnchor anchor = HudAnchor.TOP_LEFT;
	public int offsetX = 12;
	public int offsetY = 12;
	public float scale = 1.0F;
	public float opacity = 0.9F;
	public boolean background = true;
	public Map<String, Object> options = new LinkedHashMap<>();

	public void validate(String widgetId) {
		if (anchor == null) {
			throw new IllegalArgumentException("Missing HUD anchor for " + widgetId);
		}
		if (!Float.isFinite(scale) || !Float.isFinite(opacity)) {
			throw new IllegalArgumentException("Non-finite HUD layout value for " + widgetId);
		}
		offsetX = Math.max(-10_000, Math.min(10_000, offsetX));
		offsetY = Math.max(-10_000, Math.min(10_000, offsetY));
		scale = Math.max(0.5F, Math.min(2.0F, scale));
		opacity = Math.max(0.1F, Math.min(1.0F, opacity));
		if (options == null) {
			throw new IllegalArgumentException("Missing HUD options for " + widgetId);
		}
		for (String option : options.keySet()) {
			BuiltinHudCatalog.requireAllowedOption(widgetId, option);
		}
	}

	public HudWidgetSettings copy() {
		HudWidgetSettings copy = of(enabled, anchor, offsetX, offsetY);
		copy.scale = scale;
		copy.opacity = opacity;
		copy.background = background;
		copy.options = new LinkedHashMap<>(options);
		return copy;
	}

	public boolean booleanOption(String widgetId, String key, boolean fallback) {
		BuiltinHudCatalog.requireAllowedOption(widgetId, key);
		Object value = options.get(key);
		return value instanceof Boolean bool ? bool : fallback;
	}

	public void setBooleanOption(String widgetId, String key, boolean value) {
		BuiltinHudCatalog.requireAllowedOption(widgetId, key);
		options.put(key, value);
	}

	public static HudWidgetSettings of(boolean enabled, HudAnchor anchor, int x, int y) {
		HudWidgetSettings settings = new HudWidgetSettings();
		settings.enabled = enabled;
		settings.anchor = anchor;
		settings.offsetX = x;
		settings.offsetY = y;
		return settings;
	}
}
