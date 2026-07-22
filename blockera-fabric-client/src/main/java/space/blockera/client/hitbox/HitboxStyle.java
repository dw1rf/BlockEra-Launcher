package space.blockera.client.hitbox;

public record HitboxStyle(boolean enabled, String color, float opacity) {
    public HitboxStyle {
        if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Invalid hitbox color");
        }
        color = color.toUpperCase();
        opacity = Float.isFinite(opacity) ? Math.max(0.05F, Math.min(1.0F, opacity)) : 0.9F;
    }

    public HitboxStyle withEnabled(boolean value) {
        return new HitboxStyle(value, color, opacity);
    }

    public HitboxStyle withColor(String value) {
        return new HitboxStyle(enabled, value, opacity);
    }

    public HitboxStyle withOpacity(float value) {
        return new HitboxStyle(enabled, color, value);
    }

    public int rgb() {
        return Integer.parseInt(color.substring(1), 16);
    }
}
