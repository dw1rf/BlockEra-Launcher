package space.blockera.core.enhancement;

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
import java.util.Objects;

/** Atomic local storage for hitbox appearance; it never contains server data or credentials. */
public final class HitboxConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path path;
	private HitboxAppearanceConfig config = HitboxAppearanceConfig.defaults();

	public HitboxConfigStore(Path path) { this.path = Objects.requireNonNull(path, "path"); }
	public static HitboxConfigStore instance() { return Holder.INSTANCE; }
	public synchronized HitboxAppearanceConfig config() { return config; }

	public synchronized void load() {
		if (!Files.exists(path)) { config = HitboxAppearanceConfig.defaults(); return; }
		try {
			HitboxAppearanceConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), HitboxAppearanceConfig.class);
			if (loaded == null) throw new IllegalArgumentException("Empty hitbox config");
			loaded.validate();
			config = loaded;
		} catch (IOException | JsonParseException | IllegalArgumentException exception) {
			backupCorrupt();
			config = HitboxAppearanceConfig.defaults();
		}
	}

	public synchronized void save() {
		config.validate();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
			try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException exception) {
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			throw new IllegalStateException("Unable to persist Blockera hitbox config", exception);
		}
	}

	private void backupCorrupt() {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis()),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) { }
	}

	private static final class Holder {
		private static final HitboxConfigStore INSTANCE = new HitboxConfigStore(
				FMLPaths.CONFIGDIR.get().resolve("blockera-core").resolve("hitboxes.json"));
	}
}
