package space.blockera.client.visual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic, corruption-tolerant storage for crosshair and hit-color settings. */
public final class VisualConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] COLORS = {
        "#F4F5F6", "#168ED1", "#58C98C", "#F0C66B", "#FF667A", "#9B7BFF"
    };

    private final Path path;
    private VisualConfig config = VisualConfig.defaults();

    public VisualConfigStore(Path path) {
        this.path = path;
    }

    public synchronized void load() {
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try {
            VisualConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                VisualConfig.class);
            config = loaded == null ? VisualConfig.defaults() : loaded;
        } catch (RuntimeException | IOException error) {
            backupCorruptedFile();
            config = VisualConfig.defaults();
            save();
        }
    }

    public synchronized VisualConfig config() {
        return config;
    }

    public synchronized void setMasterEnabled(boolean enabled) {
        config = config.withMasterEnabled(enabled);
        save();
    }

    public synchronized void toggleCrosshair() {
        config = config.withCrosshairEnabled(!config.crosshairEnabled());
        save();
    }

    public synchronized void cycleCrosshairColor() {
        config = config.withCrosshairColor(nextColor(config.crosshairColor()));
        save();
    }

    public synchronized void adjustCrosshairSize(int amount) {
        config = config.withCrosshairGeometry(config.crosshairSize() + amount,
            config.crosshairGap(), config.crosshairThickness());
        save();
    }

    public synchronized void adjustCrosshairGap(int amount) {
        config = config.withCrosshairGeometry(config.crosshairSize(),
            config.crosshairGap() + amount, config.crosshairThickness());
        save();
    }

    public synchronized void adjustCrosshairThickness(int amount) {
        config = config.withCrosshairGeometry(config.crosshairSize(), config.crosshairGap(),
            config.crosshairThickness() + amount);
        save();
    }

    public synchronized void toggleHitColor() {
        config = config.withHitColorEnabled(!config.hitColorEnabled());
        save();
    }

    public synchronized void cycleHitColor() {
        config = config.withHitColor(nextColor(config.hitColor()));
        save();
    }

    private String nextColor(String current) {
        int index = 0;
        for (int candidate = 0; candidate < COLORS.length; candidate++) {
            if (COLORS[candidate].equals(current)) {
                index = candidate;
                break;
            }
        }
        return COLORS[(index + 1) % COLORS.length];
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save Blockera visual settings", error);
        }
    }

    private void backupCorruptedFile() {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Safe defaults remain active.
        }
    }
}
