package space.blockera.client.hitbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HitboxConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] COLORS = {"#9B7BFF", "#60D6A7", "#F0C66B", "#EE66A6", "#F8F7FF"};
    private final Path path;
    private HitboxConfig config = HitboxConfig.defaults();

    public HitboxConfigStore(Path path) {
        this.path = path;
    }

    public synchronized void load() {
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try {
            HitboxConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), HitboxConfig.class);
            config = loaded == null ? HitboxConfig.defaults() : loaded;
        } catch (RuntimeException | IOException exception) {
            backupCorruptedFile();
            config = HitboxConfig.defaults();
            save();
        }
    }

    public synchronized HitboxConfig config() {
        return config;
    }

    public synchronized void toggle(HitboxCategory category) {
        HitboxStyle style = config.style(category);
        setEnabled(category, !style.enabled());
    }

    public synchronized void setEnabled(HitboxCategory category, boolean enabled) {
        HitboxStyle style = config.style(category);
        config = config.withStyle(category, style.withEnabled(enabled));
        save();
    }

    public synchronized void cycleColor(HitboxCategory category) {
        HitboxStyle style = config.style(category);
        int current = 0;
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index].equals(style.color())) current = index;
        }
        config = config.withStyle(category, style.withColor(COLORS[(current + 1) % COLORS.length]));
        save();
    }

    public synchronized void adjustOpacity(HitboxCategory category, float delta) {
        HitboxStyle style = config.style(category);
        config = config.withStyle(category, style.withOpacity(style.opacity() + delta));
        save();
    }

    public synchronized void adjustLineWidth(float delta) {
        config = config.withLineWidth(config.lineWidth() + delta);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Blockera hitboxes", exception);
        }
    }

    private void backupCorruptedFile() {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Safe disabled defaults remain active.
        }
    }
}
