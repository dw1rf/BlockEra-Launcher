package space.blockera.core.hud;

import space.blockera.core.api.WidgetRegistry;

/** Single allow-listed registration point for the 0.4.0 first-party HUD. */
public final class BuiltinHudWidgets {
	private static final WidgetRegistry REGISTRY = createRegistry();

	private BuiltinHudWidgets() {
	}

	public static void registerAll(WidgetRegistry registry) {
		registry.register(new TextHudWidget(BuiltinWidgetIds.FPS, "blockera.hud.widget.fps", HudCategory.PERFORMANCE, HudDataSnapshot::fps));
		registry.register(new TextHudWidget(BuiltinWidgetIds.COORDINATES, "blockera.hud.widget.coordinates", HudCategory.WORLD, HudDataSnapshot::coordinates));
		registry.register(new TextHudWidget(BuiltinWidgetIds.DIRECTION, "blockera.hud.widget.direction", HudCategory.WORLD, HudDataSnapshot::direction));
		registry.register(new TextHudWidget(BuiltinWidgetIds.BIOME, "blockera.hud.widget.biome", HudCategory.WORLD, HudDataSnapshot::biome));
		registry.register(new TextHudWidget(BuiltinWidgetIds.SPEED, "blockera.hud.widget.speed", HudCategory.PLAYER, HudDataSnapshot::speed));
		registry.register(new TextHudWidget(BuiltinWidgetIds.MEMORY, "blockera.hud.widget.memory", HudCategory.PERFORMANCE, HudDataSnapshot::memory));
		registry.register(new ClockHudWidget());
		registry.register(new TextHudWidget(BuiltinWidgetIds.PING, "blockera.hud.widget.ping", HudCategory.SERVER, HudDataSnapshot::ping));
		registry.register(new TextHudWidget(BuiltinWidgetIds.PLAYER_COUNT, "blockera.hud.widget.player_count", HudCategory.SERVER, HudDataSnapshot::playerCount));
		registry.register(new EffectsHudWidget());
		registry.register(new TextHudWidget(BuiltinWidgetIds.DURABILITY, "blockera.hud.widget.durability",
				HudCategory.PLAYER, HudDataSnapshot::durability));
		registry.register(new TextHudWidget(BuiltinWidgetIds.ARMOR, "blockera.hud.widget.armor", HudCategory.PLAYER, HudDataSnapshot::armor));
		registry.register(new TextHudWidget(BuiltinWidgetIds.HEALTH, "blockera.hud.widget.health", HudCategory.PLAYER, HudDataSnapshot::health));
		registry.register(new TextHudWidget(BuiltinWidgetIds.FOOD, "blockera.hud.widget.food", HudCategory.PLAYER, HudDataSnapshot::food));
		registry.register(new TextHudWidget(BuiltinWidgetIds.SATURATION, "blockera.hud.widget.saturation", HudCategory.PLAYER, HudDataSnapshot::saturation));
		registry.register(new TextHudWidget(BuiltinWidgetIds.XP, "blockera.hud.widget.xp", HudCategory.PLAYER, HudDataSnapshot::xp));
		registry.register(new TextHudWidget(BuiltinWidgetIds.LIGHT, "blockera.hud.widget.light", HudCategory.WORLD, HudDataSnapshot::light));
		registry.register(new TextHudWidget(BuiltinWidgetIds.TARGET_BLOCK, "blockera.hud.widget.target_block", HudCategory.WORLD, HudDataSnapshot::targetBlock));
		registry.register(new TextHudWidget(BuiltinWidgetIds.ENTITIES, "blockera.hud.widget.entities", HudCategory.WORLD, HudDataSnapshot::entities));
		registry.register(new TextHudWidget(BuiltinWidgetIds.KEYSTROKES, "blockera.hud.widget.keystrokes", HudCategory.PERFORMANCE, HudDataSnapshot::keystrokes));
		registry.register(new TextHudWidget(BuiltinWidgetIds.CPS, "blockera.hud.widget.cps", HudCategory.PERFORMANCE, HudDataSnapshot::cps));
		registerExtra(registry, BuiltinWidgetIds.ROTATION, "rotation", HudCategory.WORLD, ExtraHudData::rotation);
		registerExtra(registry, BuiltinWidgetIds.LOOK_DIRECTION, "look_direction", HudCategory.WORLD, ExtraHudData::lookDirection);
		registerExtra(registry, BuiltinWidgetIds.COMPASS, "compass", HudCategory.WORLD, ExtraHudData::compass);
		registerExtra(registry, BuiltinWidgetIds.TARGET_INFO, "target_info", HudCategory.WORLD, ExtraHudData::targetInfo);
		registerExtra(registry, BuiltinWidgetIds.SERVER_ADDRESS, "server_address", HudCategory.SERVER, ExtraHudData::serverAddress);
		registerExtra(registry, BuiltinWidgetIds.DANGER_RADAR, "danger_radar", HudCategory.WORLD, ExtraHudData::dangerRadar);
		registerExtra(registry, BuiltinWidgetIds.SESSION_DISTANCE, "session_distance", HudCategory.PLAYER, ExtraHudData::sessionDistance);
		registerExtra(registry, BuiltinWidgetIds.BLOCK_BREAK, "block_break", HudCategory.WORLD, ExtraHudData::blockBreak);
		registerExtra(registry, BuiltinWidgetIds.COMBO, "combo", HudCategory.PLAYER, ExtraHudData::combo);
		registerExtra(registry, BuiltinWidgetIds.ARROWS, "arrows", HudCategory.PLAYER, ExtraHudData::arrows);
		registerExtra(registry, BuiltinWidgetIds.MAIN_HAND, "main_hand", HudCategory.PLAYER, ExtraHudData::mainHand);
		registerExtra(registry, BuiltinWidgetIds.OFF_HAND, "off_hand", HudCategory.PLAYER, ExtraHudData::offHand);
		registerExtra(registry, BuiltinWidgetIds.HELMET, "helmet", HudCategory.PLAYER, ExtraHudData::helmet);
		registerExtra(registry, BuiltinWidgetIds.CHESTPLATE, "chestplate", HudCategory.PLAYER, ExtraHudData::chestplate);
		registerExtra(registry, BuiltinWidgetIds.LEGGINGS, "leggings", HudCategory.PLAYER, ExtraHudData::leggings);
		registerExtra(registry, BuiltinWidgetIds.BOOTS, "boots", HudCategory.PLAYER, ExtraHudData::boots);
		registerExtra(registry, BuiltinWidgetIds.INVENTORY_TRACKER, "inventory_tracker", HudCategory.PLAYER, ExtraHudData::inventoryTracker);
		registry.register(new PlayerModelHudWidget());
		registerExtra(registry, BuiltinWidgetIds.AFK_TIMER, "afk_timer", HudCategory.PERFORMANCE, ExtraHudData::afkTimer);
		registerExtra(registry, BuiltinWidgetIds.DATE, "date", HudCategory.PERFORMANCE, ExtraHudData::date);
		registerExtra(registry, BuiltinWidgetIds.REAL_TIME, "real_time", HudCategory.PERFORMANCE, ExtraHudData::realTime);
		registerExtra(registry, BuiltinWidgetIds.WORLD_TIME, "world_time", HudCategory.WORLD, ExtraHudData::worldTime);
		registerExtra(registry, BuiltinWidgetIds.SESSION_TIME, "session_time", HudCategory.PERFORMANCE, ExtraHudData::sessionTime);
		registerExtra(registry, BuiltinWidgetIds.CPU_USAGE, "cpu_usage", HudCategory.PERFORMANCE, ExtraHudData::cpuUsage);
		registerExtra(registry, BuiltinWidgetIds.SYSTEM_MEMORY, "system_memory", HudCategory.PERFORMANCE, ExtraHudData::systemMemory);
		registerExtra(registry, BuiltinWidgetIds.BATTERY, "battery", HudCategory.PERFORMANCE, ExtraHudData::battery);
		registerExtra(registry, BuiltinWidgetIds.CPU_TEMPERATURE, "cpu_temperature", HudCategory.PERFORMANCE, ExtraHudData::cpuTemperature);
		registerExtra(registry, BuiltinWidgetIds.MARKER, "marker", HudCategory.WORLD, space.blockera.core.tools.LocalToolController::markerText);
		registerExtra(registry, BuiltinWidgetIds.MEASUREMENT, "measurement", HudCategory.WORLD, space.blockera.core.tools.LocalToolController::measurementText);
	}

	private static void registerExtra(WidgetRegistry registry, String id, String suffix, HudCategory category,
			java.util.function.Supplier<String> value) {
		registry.register(new TextHudWidget(id, "blockera.hud.widget." + suffix, category, ignored -> value.get()));
	}

	public static WidgetRegistry registry() {
		return REGISTRY;
	}

	private static WidgetRegistry createRegistry() {
		WidgetRegistry registry = WidgetRegistry.firstPartyOnly();
		registerAll(registry);
		return registry;
	}
}
