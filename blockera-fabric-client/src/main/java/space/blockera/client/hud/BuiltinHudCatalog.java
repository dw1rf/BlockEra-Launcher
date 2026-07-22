package space.blockera.client.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared allow-list for the fifty reviewed, local-only widgets from the Forge client. */
public final class BuiltinHudCatalog {
	private static final Set<String> NO_OPTIONS = Set.of();
	private static final Set<String> PVP_OPTIONS = Set.of(
		"show_model", "show_health_bar", "show_armor", "show_cps", "show_combo"
	);
	private static final List<HudWidgetMetadata> WIDGETS = List.of(
		widget("fps", HudCategory.PERFORMANCE),
		widget("coordinates", HudCategory.WORLD),
		widget("direction", HudCategory.WORLD),
		widget("biome", HudCategory.WORLD),
		widget("speed", HudCategory.PLAYER),
		widget("memory", HudCategory.PERFORMANCE),
		widget("clock", HudCategory.PERFORMANCE),
		widget("ping", HudCategory.SERVER),
		widget("player_count", HudCategory.SERVER),
		widget("effects", HudCategory.PLAYER),
		widget("durability", HudCategory.PLAYER),
		widget("armor", HudCategory.PLAYER),
		widget("health", HudCategory.PLAYER),
		widget("pvp_hud", HudCategory.PLAYER, PVP_OPTIONS),
		widget("food", HudCategory.PLAYER),
		widget("saturation", HudCategory.PLAYER),
		widget("xp", HudCategory.PLAYER),
		widget("light", HudCategory.WORLD),
		widget("target_block", HudCategory.WORLD),
		widget("entities", HudCategory.WORLD),
		widget("keystrokes", HudCategory.PERFORMANCE),
		widget("cps", HudCategory.PERFORMANCE),
		widget("rotation", HudCategory.WORLD),
		widget("look_direction", HudCategory.WORLD),
		widget("compass", HudCategory.WORLD),
		widget("target_info", HudCategory.WORLD),
		widget("server_address", HudCategory.SERVER),
		widget("danger_radar", HudCategory.WORLD),
		widget("session_distance", HudCategory.PLAYER),
		widget("block_break", HudCategory.WORLD),
		widget("combo", HudCategory.PLAYER),
		widget("arrows", HudCategory.PLAYER),
		widget("main_hand", HudCategory.PLAYER),
		widget("off_hand", HudCategory.PLAYER),
		widget("helmet", HudCategory.PLAYER),
		widget("chestplate", HudCategory.PLAYER),
		widget("leggings", HudCategory.PLAYER),
		widget("boots", HudCategory.PLAYER),
		widget("inventory_tracker", HudCategory.PLAYER),
		widget("player_model", HudCategory.PLAYER),
		widget("afk_timer", HudCategory.PERFORMANCE),
		widget("date", HudCategory.PERFORMANCE),
		widget("real_time", HudCategory.PERFORMANCE),
		widget("world_time", HudCategory.WORLD),
		widget("session_time", HudCategory.PERFORMANCE),
		widget("cpu_usage", HudCategory.PERFORMANCE),
		widget("system_memory", HudCategory.PERFORMANCE),
		widget("battery", HudCategory.PERFORMANCE),
		widget("cpu_temperature", HudCategory.PERFORMANCE),
		widget("marker", HudCategory.WORLD),
		widget("measurement", HudCategory.WORLD)
	);
	private static final Map<String, HudWidgetMetadata> BY_ID = index();

	private BuiltinHudCatalog() {
	}

	public static List<HudWidgetMetadata> widgets() {
		return WIDGETS;
	}

	public static boolean isAllowed(String id) {
		return BY_ID.containsKey(id);
	}

	public static HudWidgetMetadata require(String id) {
		HudWidgetMetadata metadata = BY_ID.get(id);
		if (metadata == null) {
			throw new IllegalArgumentException("Unknown first-party HUD widget: " + id);
		}
		return metadata;
	}

	public static void requireAllowedOption(String id, String key) {
		if (!require(id).allowedOptions().contains(key)) {
			throw new IllegalArgumentException("Unknown option '" + key + "' for HUD widget " + id);
		}
	}

	private static HudWidgetMetadata widget(String suffix, HudCategory category) {
		return widget(suffix, category, NO_OPTIONS);
	}

	private static HudWidgetMetadata widget(String suffix, HudCategory category, Set<String> options) {
		return new HudWidgetMetadata(
			"blockera:" + suffix,
			"blockera.hud.widget." + suffix,
			category,
			options
		);
	}

	private static Map<String, HudWidgetMetadata> index() {
		Map<String, HudWidgetMetadata> result = new LinkedHashMap<>();
		for (HudWidgetMetadata widget : WIDGETS) {
			if (result.put(widget.id(), widget) != null) {
				throw new IllegalStateException("Duplicate built-in HUD widget: " + widget.id());
			}
		}
		return Map.copyOf(result);
	}
}
