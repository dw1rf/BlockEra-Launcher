package space.blockera.core.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned, atomic and first-party-only storage for all HUD profiles. */
public final class HudLayoutStore {
	public static final int SCHEMA_VERSION = 5;
	public static final String MINIMAL = "minimal";
	public static final String SURVIVAL = "survival";
	public static final String PVP = "pvp";
	public static final String BUILDING = "building";
	public static final String STREAM = "stream";
	public static final String CUSTOM = "custom";
	public static final String MINI_GAMES = "mini_games"; // Retained for schema v1/v2 compatibility.
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FIRST_PARTY_PREFIX = "blockera:";
	private static final Set<String> ALLOWED_WIDGET_TYPES = Set.of(widgetIds());

	private final Path path;
	private HudLayoutDocument document = defaults();

	public HudLayoutStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public static HudLayoutStore instance() {
		return Holder.INSTANCE;
	}

	public synchronized void load() {
		if (!Files.exists(path)) {
			document = defaults();
			return;
		}
		try {
			HudLayoutDocument loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
					HudLayoutDocument.class);
			migrate(loaded);
			validate(loaded);
			document = loaded;
		} catch (IOException | JsonParseException | IllegalArgumentException exception) {
			backupCorruptFile();
			document = loadLastValidBackup();
		}
	}

	public synchronized void save() {
		try {
			Files.createDirectories(path.getParent());
			if (Files.exists(path)) Files.copy(path, backupPath(), StandardCopyOption.REPLACE_EXISTING);
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to persist Blockera HUD layouts", exception);
		}
	}

	public synchronized String activeProfile() {
		return document.activeProfile;
	}

	public synchronized void setActiveProfile(String profile) {
		if (!document.profiles.containsKey(profile)) {
			throw new IllegalArgumentException("Unknown HUD profile: " + profile);
		}
		document.activeProfile = profile;
	}

	public synchronized HudWidgetSettings settings(String widgetId) {
		requireFirstParty(widgetId);
		return active().widgets.computeIfAbsent(widgetId, ignored -> defaultSetting(widgetId, 0, false));
	}

	public synchronized Map<String, HudWidgetSettings> snapshot() {
		Map<String, HudWidgetSettings> copy = new LinkedHashMap<>();
		active().widgets.forEach((id, settings) -> copy.put(id, settings.copy()));
		return Collections.unmodifiableMap(copy);
	}

	public synchronized String duplicateWidget(String widgetId) {
		requireFirstParty(widgetId);
		HudWidgetSettings source = settings(widgetId);
		String base = baseWidgetId(widgetId);
		int suffix = 2;
		while (active().widgets.containsKey(base + "#" + suffix)) suffix++;
		String duplicateId = base + "#" + suffix;
		HudWidgetSettings duplicate = source.copy();
		duplicate.offsetX += 16;
		duplicate.offsetY += 16;
		duplicate.zIndex = highestZIndex() + 1;
		active().widgets.put(duplicateId, duplicate);
		return duplicateId;
	}

	public synchronized void removeWidget(String widgetId) {
		requireFirstParty(widgetId);
		if (widgetId.contains("#")) active().widgets.remove(widgetId);
		else settings(widgetId).enabled = false;
	}

	public static String baseWidgetId(String widgetId) {
		int separator = widgetId.indexOf('#');
		return separator < 0 ? widgetId : widgetId.substring(0, separator);
	}

	public synchronized VanillaHudSettings vanillaSettings(String elementId) {
		VanillaHudElement element = VanillaHudElement.byId(elementId);
		return active().vanilla.computeIfAbsent(element.id(), ignored -> element.defaults());
	}

	public synchronized Map<String, VanillaHudSettings> vanillaSnapshot() {
		Map<String, VanillaHudSettings> copy = new LinkedHashMap<>();
		active().vanilla.forEach((id, settings) -> copy.put(id, settings.copy()));
		return Collections.unmodifiableMap(copy);
	}

	public synchronized void resetActiveProfile() {
		HudLayoutDocument clean = defaults();
		document.profiles.put(document.activeProfile, clean.profiles.get(document.activeProfile));
	}

	public synchronized void resetWidget(String widgetId) {
		requireFirstParty(widgetId);
		HudProfile clean = defaults().profiles.get(document.activeProfile);
		HudWidgetSettings replacement = clean == null ? defaultSetting(widgetId, 0, false)
				: clean.widgets.getOrDefault(widgetId, defaultSetting(widgetId, 0, false));
		active().widgets.put(widgetId, replacement.copy());
	}

	public synchronized void resetVanilla(String elementId) {
		VanillaHudElement element = VanillaHudElement.byId(elementId);
		active().vanilla.put(element.id(), element.defaults());
	}

	public synchronized int highestZIndex() {
		return active().widgets.values().stream().mapToInt(settings -> settings.zIndex).max().orElse(0);
	}

	public synchronized int lowestZIndex() {
		return active().widgets.values().stream().mapToInt(settings -> settings.zIndex).min().orElse(0);
	}

	public synchronized void restoreJson(String json) {
		try {
			HudLayoutDocument restored = GSON.fromJson(json, HudLayoutDocument.class);
			migrate(restored);
			validate(restored);
			document = restored;
		} catch (JsonParseException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid HUD history snapshot", exception);
		}
	}

	public synchronized java.util.List<String> profileNames() {
		return java.util.List.copyOf(document.profiles.keySet());
	}

	public synchronized String json() {
		return GSON.toJson(document);
	}

	private HudProfile active() {
		return document.profiles.get(document.activeProfile);
	}

	private void backupCorruptFile() {
		if (!Files.exists(path)) return;
		try {
			Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Keep startup safe even if the filesystem cannot create the diagnostic backup.
		}
	}

	private HudLayoutDocument loadLastValidBackup() {
		Path backup = backupPath();
		if (!Files.exists(backup)) return defaults();
		try {
			HudLayoutDocument restored = GSON.fromJson(Files.readString(backup, StandardCharsets.UTF_8), HudLayoutDocument.class);
			migrate(restored);
			validate(restored);
			return restored;
		} catch (IOException | JsonParseException | IllegalArgumentException ignored) {
			return defaults();
		}
	}

	private Path backupPath() { return path.resolveSibling(path.getFileName() + ".bak"); }

	private static void validate(HudLayoutDocument candidate) {
		if (candidate == null || candidate.schemaVersion != SCHEMA_VERSION || candidate.profiles == null
				|| candidate.activeProfile == null || !candidate.profiles.containsKey(candidate.activeProfile)) {
			throw new IllegalArgumentException("Unsupported or incomplete HUD layout");
		}
		candidate.profiles.forEach((name, profile) -> {
			if (name == null || profile == null || profile.widgets == null || profile.vanilla == null) {
				throw new IllegalArgumentException("Invalid HUD profile");
			}
			profile.widgets.forEach((id, settings) -> {
				requireFirstParty(id);
				if (settings == null) throw new IllegalArgumentException("Missing widget settings");
				settings.validate();
				HudWidget widget = (HudWidget) BuiltinHudWidgets.registry().get(baseWidgetId(id));
				Set<String> allowedOptions = widget.options().stream()
						.map(WidgetOptionSchema::key).collect(java.util.stream.Collectors.toUnmodifiableSet());
				settings.options.keySet().removeIf(key -> !allowedOptions.contains(key));
				for (WidgetOptionSchema option : widget.options()) {
					settings.options.putIfAbsent(option.key(), option.defaultValue());
				}
			});
			profile.vanilla.forEach((id, settings) -> {
				VanillaHudElement.byId(id);
				if (settings == null) throw new IllegalArgumentException("Missing vanilla HUD settings");
				settings.validate();
			});
			for (VanillaHudElement element : VanillaHudElement.values()) {
				profile.vanilla.computeIfAbsent(element.id(), ignored -> element.defaults());
			}
		});
	}

	private static void migrate(HudLayoutDocument candidate) {
		if (candidate == null || candidate.schemaVersion == SCHEMA_VERSION) return;
		if (candidate.schemaVersion < 1 || candidate.schemaVersion > SCHEMA_VERSION || candidate.profiles == null) {
			throw new IllegalArgumentException("Unsupported legacy HUD layout");
		}
		int sourceVersion = candidate.schemaVersion;
		if (candidate.schemaVersion == 1) {
			candidate.profiles.values().forEach(profile -> {
				if (profile == null) throw new IllegalArgumentException("Invalid legacy HUD profile");
				profile.vanilla = defaultVanillaSettings();
			});
		}
		candidate.schemaVersion = SCHEMA_VERSION;
		// Schema 5 repairs the old preset behavior which re-enabled a large set of
		// widgets after deletion/reload. Existing v4 presets receive the safe,
		// predictable baseline requested for the dev client: FPS only.
		if (sourceVersion == 4) {
			candidate.profiles.values().forEach(profile -> {
				if (profile == null || profile.widgets == null) return;
				profile.widgets.forEach((id, settings) -> {
					if (settings != null) settings.enabled = BuiltinWidgetIds.FPS.equals(baseWidgetId(id));
				});
			});
		}
		HudLayoutDocument clean = defaults();
		clean.profiles.forEach(candidate.profiles::putIfAbsent);
	}

	private static void requireFirstParty(String id) {
		if (id == null || !id.startsWith(FIRST_PARTY_PREFIX)
				|| !id.matches("blockera:[a-z0-9_]+(?:#[1-9][0-9]*)?")
				|| !ALLOWED_WIDGET_TYPES.contains(baseWidgetId(id))) {
			throw new IllegalArgumentException("Only first-party Blockera widget layouts are allowed");
		}
	}

	private static HudLayoutDocument defaults() {
		HudLayoutDocument result = new HudLayoutDocument();
		result.activeProfile = MINIMAL;
		HudProfile minimal = profile(new boolean[] {true});
		minimal.widgets.values().forEach(settings -> settings.background = false);
		result.profiles.put(MINIMAL, minimal);
		result.profiles.put(SURVIVAL, profile(new boolean[] {true}));
		result.profiles.put(PVP, profile(new boolean[] {true}));
		result.profiles.put(BUILDING, profile(new boolean[] {true}));
		result.profiles.put(STREAM, profile(new boolean[] {true}));
		result.profiles.put(CUSTOM, profile(new boolean[] {true}));
		return result;
	}

	private static HudProfile profile(boolean[] enabled) {
		String[] ids = widgetIds();
		HudProfile profile = new HudProfile();
		for (int index = 0; index < ids.length; index++) {
			profile.widgets.put(ids[index], defaultSetting(ids[index], index, index < enabled.length && enabled[index]));
		}
		profile.vanilla = defaultVanillaSettings();
		return profile;
	}

	private static Map<String, VanillaHudSettings> defaultVanillaSettings() {
		Map<String, VanillaHudSettings> result = new LinkedHashMap<>();
		for (VanillaHudElement element : VanillaHudElement.values()) result.put(element.id(), element.defaults());
		return result;
	}

	private static HudWidgetSettings defaultSetting(String id, int index, boolean enabled) {
		HudWidgetSettings settings = new HudWidgetSettings();
		settings.enabled = enabled;
		settings.zIndex = index;
		settings.anchor = index < 3 ? HudAnchor.TOP_LEFT
				: index < 5 ? HudAnchor.TOP_RIGHT
				: index < 7 ? HudAnchor.BOTTOM_LEFT
				: index < 9 ? HudAnchor.TOP_CENTER : HudAnchor.CENTER_RIGHT;
		settings.offsetX = switch (settings.anchor) {
			case TOP_RIGHT -> -12;
			default -> 12;
		};
		settings.offsetY = switch (index) {
			case 0, 3, 7 -> 12;
			case 1, 4, 8 -> 44;
			case 2 -> 76;
			case 5 -> -44;
			case 6 -> -12;
			default -> 0;
		};
		if (settings.anchor == HudAnchor.TOP_CENTER) settings.offsetX = 0;
		if (settings.anchor == HudAnchor.CENTER_RIGHT) settings.offsetX = -12;
		return settings;
	}

	private static String[] widgetIds() {
		return new String[] {BuiltinWidgetIds.FPS, BuiltinWidgetIds.COORDINATES, BuiltinWidgetIds.DIRECTION,
				BuiltinWidgetIds.BIOME, BuiltinWidgetIds.SPEED, BuiltinWidgetIds.MEMORY, BuiltinWidgetIds.CLOCK,
				BuiltinWidgetIds.PING, BuiltinWidgetIds.PLAYER_COUNT, BuiltinWidgetIds.EFFECTS, BuiltinWidgetIds.DURABILITY,
				BuiltinWidgetIds.ARMOR, BuiltinWidgetIds.HEALTH, BuiltinWidgetIds.FOOD, BuiltinWidgetIds.SATURATION,
				BuiltinWidgetIds.XP, BuiltinWidgetIds.LIGHT, BuiltinWidgetIds.TARGET_BLOCK, BuiltinWidgetIds.ENTITIES,
				BuiltinWidgetIds.KEYSTROKES, BuiltinWidgetIds.CPS,
				BuiltinWidgetIds.ROTATION, BuiltinWidgetIds.LOOK_DIRECTION, BuiltinWidgetIds.COMPASS,
				BuiltinWidgetIds.TARGET_INFO, BuiltinWidgetIds.SERVER_ADDRESS, BuiltinWidgetIds.DANGER_RADAR,
				BuiltinWidgetIds.SESSION_DISTANCE, BuiltinWidgetIds.BLOCK_BREAK, BuiltinWidgetIds.COMBO,
				BuiltinWidgetIds.ARROWS, BuiltinWidgetIds.MAIN_HAND, BuiltinWidgetIds.OFF_HAND,
				BuiltinWidgetIds.HELMET, BuiltinWidgetIds.CHESTPLATE, BuiltinWidgetIds.LEGGINGS,
				BuiltinWidgetIds.BOOTS, BuiltinWidgetIds.INVENTORY_TRACKER, BuiltinWidgetIds.PLAYER_MODEL, BuiltinWidgetIds.AFK_TIMER,
				BuiltinWidgetIds.DATE, BuiltinWidgetIds.REAL_TIME, BuiltinWidgetIds.WORLD_TIME,
				BuiltinWidgetIds.SESSION_TIME, BuiltinWidgetIds.CPU_USAGE, BuiltinWidgetIds.SYSTEM_MEMORY,
				BuiltinWidgetIds.BATTERY, BuiltinWidgetIds.CPU_TEMPERATURE,
				BuiltinWidgetIds.MARKER, BuiltinWidgetIds.MEASUREMENT};
	}

	private static final class Holder {
		private static final HudLayoutStore INSTANCE = new HudLayoutStore(
				FMLPaths.CONFIGDIR.get().resolve("blockera-core").resolve("hud-layouts.json"));
	}

	private static final class HudLayoutDocument {
		int schemaVersion = SCHEMA_VERSION;
		String activeProfile = MINIMAL;
		Map<String, HudProfile> profiles = new LinkedHashMap<>();
	}

	private static final class HudProfile {
		Map<String, HudWidgetSettings> widgets = new LinkedHashMap<>();
		Map<String, VanillaHudSettings> vanilla = new LinkedHashMap<>();
	}
}
