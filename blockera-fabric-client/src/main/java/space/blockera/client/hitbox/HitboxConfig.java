package space.blockera.client.hitbox;

public record HitboxConfig(
    int schemaVersion,
    float lineWidth,
    HitboxStyle players,
    HitboxStyle animals,
    HitboxStyle items
) {
    public HitboxConfig {
        if (schemaVersion != 1 || players == null || animals == null || items == null) {
            throw new IllegalArgumentException("Unsupported hitbox config");
        }
        lineWidth = Float.isFinite(lineWidth) ? Math.max(1.0F, Math.min(4.0F, lineWidth)) : 1.5F;
    }

    public static HitboxConfig defaults() {
        return new HitboxConfig(
            1,
            1.5F,
            new HitboxStyle(false, "#9B7BFF", 0.92F),
            new HitboxStyle(false, "#60D6A7", 0.92F),
            new HitboxStyle(false, "#F0C66B", 0.92F)
        );
    }

    public HitboxStyle style(HitboxCategory category) {
        return switch (category) {
            case PLAYER -> players;
            case ANIMAL -> animals;
            case ITEM -> items;
        };
    }

    public HitboxConfig withStyle(HitboxCategory category, HitboxStyle style) {
        return switch (category) {
            case PLAYER -> new HitboxConfig(schemaVersion, lineWidth, style, animals, items);
            case ANIMAL -> new HitboxConfig(schemaVersion, lineWidth, players, style, items);
            case ITEM -> new HitboxConfig(schemaVersion, lineWidth, players, animals, style);
        };
    }

    public HitboxConfig withLineWidth(float value) {
        return new HitboxConfig(schemaVersion, value, players, animals, items);
    }
}
