package space.blockera.core.chat;

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

/** Schema-v1 atomic storage for local chat preferences. */
public final class ChatConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private ChatConfig config = ChatConfig.defaults();

	public ChatConfigStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public static ChatConfigStore instance() { return Holder.INSTANCE; }

	public synchronized void load() {
		if (!Files.exists(path)) {
			config = ChatConfig.defaults();
			return;
		}
		try {
			ChatConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ChatConfig.class);
			if (loaded == null) throw new IllegalArgumentException("Empty chat config");
			if (loaded.schemaVersion() < ChatConfig.SCHEMA_VERSION) loaded.migrateLegacy();
			loaded.validate();
			config = loaded;
		} catch (IOException | JsonParseException | IllegalArgumentException exception) {
			backupCorruptFile();
			config = ChatConfig.defaults();
		}
	}

	public synchronized void save() {
		config.validate();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// Preserve the original persistence exception below.
			}
			throw new IllegalStateException("Unable to persist Blockera chat config", exception);
		}
	}

	public synchronized ChatConfig config() { return config; }

	public synchronized void replace(ChatConfig replacement) {
		Objects.requireNonNull(replacement, "replacement").validate();
		config = replacement;
	}

	public synchronized String json() { return GSON.toJson(config); }

	public synchronized void restoreJson(String json) {
		try {
			ChatConfig restored = GSON.fromJson(Objects.requireNonNull(json, "json"), ChatConfig.class);
			if (restored == null) throw new IllegalArgumentException("Empty chat config");
			if (restored.schemaVersion() < ChatConfig.SCHEMA_VERSION) restored.migrateLegacy();
			restored.validate();
			config = restored;
		} catch (JsonParseException exception) {
			throw new IllegalArgumentException("Invalid chat config snapshot", exception);
		}
	}

	private void backupCorruptFile() {
		if (!Files.exists(path)) return;
		try {
			Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Loading must remain safe if diagnostic backup creation fails.
		}
	}

	private static final class Holder {
		private static final ChatConfigStore INSTANCE = new ChatConfigStore(
				FMLPaths.CONFIGDIR.get().resolve("blockera-core").resolve("chat.json"));
	}
}
