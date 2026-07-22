package space.blockera.core.enhancement;

import java.util.Objects;

/** Persisted first-party hitbox appearance. Category enablement remains in Forge config. */
public final class HitboxAppearanceConfig {
	public static final int SCHEMA_VERSION = 1;

	private int schemaVersion = SCHEMA_VERSION;
	private float lineWidth = 1.5F;
	private boolean eyeDirection;
	private String eyeDirectionColor = "#F8F7FF";
	private CategoryStyle players = new CategoryStyle("#9B7BFF", 0.92F);
	private CategoryStyle animals = new CategoryStyle("#60D6A7", 0.92F);
	private CategoryStyle items = new CategoryStyle("#F0C66B", 0.92F);

	public static HitboxAppearanceConfig defaults() { return new HitboxAppearanceConfig(); }

	public int schemaVersion() { return schemaVersion; }
	public float lineWidth() { return lineWidth; }
	public boolean eyeDirection() { return eyeDirection; }
	public String eyeDirectionColor() { return eyeDirectionColor; }
	public CategoryStyle players() { return players; }
	public CategoryStyle animals() { return animals; }
	public CategoryStyle items() { return items; }

	public void setLineWidth(float value) { lineWidth = clamp(value, 1.0F, 4.0F); }
	public void setEyeDirection(boolean value) { eyeDirection = value; }
	public void setEyeDirectionColor(String value) { eyeDirectionColor = value; }

	public CategoryStyle style(EntityHitboxPolicy.Category category) {
		return switch (Objects.requireNonNull(category, "category")) {
			case PLAYER -> players;
			case ANIMAL -> animals;
			case ITEM -> items;
			case NONE -> throw new IllegalArgumentException("NONE has no hitbox style");
		};
	}

	public void validate() {
		if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported hitbox schema");
		lineWidth = clamp(lineWidth, 1.0F, 4.0F);
		eyeDirectionColor = validColor(eyeDirectionColor, "#F8F7FF");
		if (players == null) players = new CategoryStyle("#9B7BFF", 0.92F);
		if (animals == null) animals = new CategoryStyle("#60D6A7", 0.92F);
		if (items == null) items = new CategoryStyle("#F0C66B", 0.92F);
		players.validate("#9B7BFF");
		animals.validate("#60D6A7");
		items.validate("#F0C66B");
	}

	private static String validColor(String value, String fallback) {
		return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase() : fallback;
	}

	private static float clamp(float value, float min, float max) {
		return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}

	public static final class CategoryStyle {
		private String color;
		private float opacity;

		public CategoryStyle() { this("#F8F7FF", 0.92F); }
		public CategoryStyle(String color, float opacity) { this.color = color; this.opacity = opacity; }
		public String color() { return color; }
		public float opacity() { return opacity; }
		public void setColor(String value) { color = value; }
		public void setOpacity(float value) { opacity = value; }
		public int rgb() { return Integer.parseInt(color.substring(1), 16); }
		private void validate(String fallback) {
			color = validColor(color, fallback);
			opacity = clamp(opacity, 0.05F, 1.0F);
		}
	}
}
