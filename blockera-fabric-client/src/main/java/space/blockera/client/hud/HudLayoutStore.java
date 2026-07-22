package space.blockera.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Schema-5, first-party-only and atomic storage for Blockera HUD profiles. */
public final class HudLayoutStore {
	public static final int SCHEMA_VERSION = 5;
	public static final String MINIMAL = "minimal";
	public static final String SURVIVAL = "survival";
	public static final String PVP = "pvp";
	public static final String BUILDING = "building";
	public static final String STREAM = "stream";
	public static final String CUSTOM = "custom";
	private static final List<String> PROFILE_NAMES = List.of(
		MINIMAL, SURVIVAL, PVP, BUILDING, STREAM, CUSTOM
	);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private HudLayoutDocument document = defaults();

	public HudLayoutStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public synchronized void load() {
		if (!Files.isRegularFile(path)) {
			document = defaults();
			save();
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				throw new IllegalArgumentException("HUD layout root must be an object");
			}
			JsonObject root = parsed.getAsJsonObject();
			int schemaVersion = requiredSchemaVersion(root);
			boolean migrated = schemaVersion == 1;
			HudLayoutDocument loaded = migrated ? migrateFabricSchema(root) : readSchemaFive(root, schemaVersion);
			validate(loaded);
			document = loaded;
			if (migrated) {
				save();
			}
		} catch (IOException | JsonParseException | IllegalArgumentException exception) {
			backupCorruptedFile();
			document = defaults();
			save();
		}
	}

	public synchronized HudWidgetSettings settings(String id) {
		BuiltinHudCatalog.require(id);
		return active().widgets.get(id);
	}

	public synchronized Map<String, HudWidgetSettings> snapshot() {
		Map<String, HudWidgetSettings> copy = new LinkedHashMap<>();
		active().widgets.forEach((id, settings) -> copy.put(id, settings.copy()));
		return Collections.unmodifiableMap(copy);
	}

	public synchronized String activeProfile() {
		return document.activeProfile;
	}

	public synchronized List<String> profileNames() {
		return List.copyOf(document.profiles.keySet());
	}

	public synchronized void setActiveProfile(String profile) {
		if (!document.profiles.containsKey(profile)) {
			throw new IllegalArgumentException("Unknown HUD profile: " + profile);
		}
		document.activeProfile = profile;
	}

	public synchronized void removeWidget(String id) {
		settings(id).enabled = false;
	}

	public synchronized void resetActiveProfile() {
		document.profiles.put(document.activeProfile, defaultProfile());
	}

	public synchronized String json() {
		return GSON.toJson(document);
	}

	public synchronized void save() {
		validate(document);
		try {
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to save Blockera HUD layout", exception);
		}
	}

	private HudProfile active() {
		return document.profiles.get(document.activeProfile);
	}

	private static int requiredSchemaVersion(JsonObject root) {
		if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()) {
			throw new IllegalArgumentException("HUD schema version is missing");
		}
		return root.get("schemaVersion").getAsInt();
	}

	private static HudLayoutDocument readSchemaFive(JsonObject root, int schemaVersion) {
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported HUD schema: " + schemaVersion);
		}
		return GSON.fromJson(root, HudLayoutDocument.class);
	}

	private static HudLayoutDocument migrateFabricSchema(JsonObject root) {
		JsonElement widgetsElement = root.get("widgets");
		if (widgetsElement == null || !widgetsElement.isJsonObject()) {
			throw new IllegalArgumentException("Invalid Fabric HUD schema 1 document");
		}
		HudLayoutDocument migrated = defaults();
		migrated.activeProfile = CUSTOM;
		HudProfile custom = migrated.profiles.get(CUSTOM);
		for (Map.Entry<String, JsonElement> entry : widgetsElement.getAsJsonObject().entrySet()) {
			String migratedId = migrateLegacyId(entry.getKey());
			BuiltinHudCatalog.require(migratedId);
			HudWidgetSettings settings = GSON.fromJson(entry.getValue(), HudWidgetSettings.class);
			if (settings == null) {
				throw new IllegalArgumentException("Missing legacy HUD settings for " + migratedId);
			}
			if (settings.options == null) {
				settings.options = new LinkedHashMap<>();
			}
			settings.validate(migratedId);
			custom.widgets.put(migratedId, settings);
		}
		return migrated;
	}

	private static String migrateLegacyId(String id) {
		return "blockera:target".equals(id) ? "blockera:target_info" : id;
	}

	private static void validate(HudLayoutDocument candidate) {
		if (candidate == null || candidate.schemaVersion != SCHEMA_VERSION
			|| candidate.activeProfile == null || candidate.profiles == null
			|| !candidate.profiles.containsKey(candidate.activeProfile)
			|| !candidate.profiles.keySet().equals(new java.util.LinkedHashSet<>(PROFILE_NAMES))) {
			throw new IllegalArgumentException("Incomplete HUD schema 5 document");
		}
		for (Map.Entry<String, HudProfile> profileEntry : candidate.profiles.entrySet()) {
			HudProfile profile = profileEntry.getValue();
			if (profile == null || profile.widgets == null) {
				throw new IllegalArgumentException("Missing HUD profile: " + profileEntry.getKey());
			}
			for (String id : profile.widgets.keySet()) {
				BuiltinHudCatalog.require(id);
			}
			for (HudWidgetMetadata metadata : BuiltinHudCatalog.widgets()) {
				HudWidgetSettings settings = profile.widgets.get(metadata.id());
				if (settings == null) {
					throw new IllegalArgumentException("Missing HUD widget layout: " + metadata.id());
				}
				settings.validate(metadata.id());
			}
		}
	}

	private void backupCorruptedFile() {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// A safe in-memory default remains available when diagnostic backup fails.
		}
	}

	private static HudLayoutDocument defaults() {
		HudLayoutDocument result = new HudLayoutDocument();
		for (String profile : PROFILE_NAMES) {
			result.profiles.put(profile, defaultProfile());
		}
		return result;
	}

	private static HudProfile defaultProfile() {
		HudProfile result = new HudProfile();
		int index = 0;
		for (HudWidgetMetadata metadata : BuiltinHudCatalog.widgets()) {
			result.widgets.put(metadata.id(), defaultSettings(metadata.id(), index++));
		}
		return result;
	}

	private static HudWidgetSettings defaultSettings(String id, int index) {
		HudAnchor anchor = switch (index % 4) {
			case 1 -> HudAnchor.TOP_RIGHT;
			case 2 -> HudAnchor.BOTTOM_LEFT;
			case 3 -> HudAnchor.BOTTOM_RIGHT;
			default -> HudAnchor.TOP_LEFT;
		};
		int row = index / 4;
		int x = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT ? 12 : 12;
		int y = 12 + row * 36;
		HudWidgetSettings settings = HudWidgetSettings.of("blockera:fps".equals(id), anchor, x, y);
		settings.options = new LinkedHashMap<>();
		return settings;
	}

	private static final class HudLayoutDocument {
		int schemaVersion = SCHEMA_VERSION;
		String activeProfile = MINIMAL;
		Map<String, HudProfile> profiles = new LinkedHashMap<>();
	}

	private static final class HudProfile {
		Map<String, HudWidgetSettings> widgets = new LinkedHashMap<>();
	}
}
