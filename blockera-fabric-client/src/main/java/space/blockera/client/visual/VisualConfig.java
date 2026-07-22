package space.blockera.client.visual;

/** Persistent, local-only visual settings; none affect combat calculations. */
public record VisualConfig(
    int schemaVersion,
    boolean masterEnabled,
    boolean crosshairEnabled,
    String crosshairColor,
    int crosshairSize,
    int crosshairGap,
    int crosshairThickness,
    boolean hitColorEnabled,
    String hitColor
) {
    public VisualConfig {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported visual config");
        }
        crosshairColor = normalizeColor(crosshairColor, "#F4F5F6");
        hitColor = normalizeColor(hitColor, "#FF667A");
        crosshairSize = Math.max(2, Math.min(12, crosshairSize));
        crosshairGap = Math.max(0, Math.min(8, crosshairGap));
        crosshairThickness = Math.max(1, Math.min(3, crosshairThickness));
    }

    public static VisualConfig defaults() {
        return new VisualConfig(1, true, true, "#F4F5F6", 5, 1, 1, true, "#FF667A");
    }

    public int crosshairArgb() {
        return argb(crosshairColor);
    }

    public int hitColorArgb() {
        return argb(hitColor);
    }

    public VisualConfig withMasterEnabled(boolean value) {
        return copy(value, crosshairEnabled, crosshairColor, crosshairSize, crosshairGap,
            crosshairThickness, hitColorEnabled, hitColor);
    }

    public VisualConfig withCrosshairEnabled(boolean value) {
        return copy(masterEnabled, value, crosshairColor, crosshairSize, crosshairGap,
            crosshairThickness, hitColorEnabled, hitColor);
    }

    public VisualConfig withCrosshairColor(String value) {
        return copy(masterEnabled, crosshairEnabled, value, crosshairSize, crosshairGap,
            crosshairThickness, hitColorEnabled, hitColor);
    }

    public VisualConfig withCrosshairGeometry(int size, int gap, int thickness) {
        return copy(masterEnabled, crosshairEnabled, crosshairColor, size, gap, thickness,
            hitColorEnabled, hitColor);
    }

    public VisualConfig withHitColorEnabled(boolean value) {
        return copy(masterEnabled, crosshairEnabled, crosshairColor, crosshairSize, crosshairGap,
            crosshairThickness, value, hitColor);
    }

    public VisualConfig withHitColor(String value) {
        return copy(masterEnabled, crosshairEnabled, crosshairColor, crosshairSize, crosshairGap,
            crosshairThickness, hitColorEnabled, value);
    }

    private VisualConfig copy(boolean master, boolean crosshair, String crosshairHex, int size,
        int gap, int thickness, boolean hitEnabled, String hitHex) {
        return new VisualConfig(schemaVersion, master, crosshair, crosshairHex, size, gap,
            thickness, hitEnabled, hitHex);
    }

    private static String normalizeColor(String value, String fallback) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}")
            ? value.toUpperCase() : fallback;
    }

    private static int argb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }
}
