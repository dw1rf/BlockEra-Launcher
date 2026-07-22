package space.blockera.client.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Atomic local storage for chat layout and filter definitions. */
public final class ChatConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private ChatConfig config = ChatConfig.defaults();

	public ChatConfigStore(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public synchronized void load() {
		if (!Files.isRegularFile(path)) {
			config = ChatConfig.defaults();
			return;
		}
		try {
			ChatConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ChatConfig.class);
			if (loaded == null) throw new IllegalArgumentException("Empty chat configuration");
			loaded.validate();
			config = loaded;
		} catch (IOException | JsonParseException | IllegalArgumentException exception) {
			backupCorruptedFile();
			config = ChatConfig.defaults();
		}
	}

	public synchronized void save() {
		config.validate();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to save Blockera chat configuration", exception);
		}
	}

	public synchronized ChatConfig config() { return config; }

	public synchronized void replace(ChatConfig replacement) {
		Objects.requireNonNull(replacement, "replacement").validate();
		config = replacement;
	}

	private void backupCorruptedFile() {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis()),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// A safe in-memory default remains available when diagnostic backup fails.
		}
	}
}
