package space.blockera.core.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic persistence for explicit first-party local tools. */
public final class LocalToolConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path path;
	private LocalToolConfig config = new LocalToolConfig();
	public LocalToolConfigStore(Path path) { this.path = path; }
	public static LocalToolConfigStore instance() { return Holder.INSTANCE; }
	public synchronized LocalToolConfig config() { return config; }
	public synchronized void load() {
		if (!Files.exists(path)) return;
		try {
			LocalToolConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LocalToolConfig.class);
			if (loaded == null) throw new IllegalArgumentException("Empty tools config");
			loaded.validate(); config = loaded;
		} catch (IOException | RuntimeException exception) {
			try { Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING); }
			catch (IOException ignored) { }
			config = new LocalToolConfig();
		}
	}
	public synchronized void save() {
		config.validate(); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
			try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException exception) { throw new IllegalStateException("Unable to persist local tools", exception); }
	}
	private static final class Holder {
		private static final LocalToolConfigStore INSTANCE = new LocalToolConfigStore(
				FMLPaths.CONFIGDIR.get().resolve("blockera-core").resolve("tools.json"));
	}
}
