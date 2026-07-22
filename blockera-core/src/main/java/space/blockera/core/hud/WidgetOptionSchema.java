package space.blockera.core.hud;

import java.util.List;
import java.util.Objects;

/** Declarative, data-only option exposed by a built-in widget. */
public record WidgetOptionSchema(String key, Type type, String defaultValue, List<String> choices) {
	public enum Type { BOOLEAN, RANGE, CHOICE, COLOR }
	public WidgetOptionSchema {
		if (key == null || !key.matches("[a-z][a-z0-9_]{0,31}")) throw new IllegalArgumentException("Invalid widget option key");
		Objects.requireNonNull(type, "type"); Objects.requireNonNull(defaultValue, "defaultValue");
		choices = choices == null ? List.of() : List.copyOf(choices);
	}
	public static WidgetOptionSchema bool(String key, boolean value) {
		return new WidgetOptionSchema(key, Type.BOOLEAN, Boolean.toString(value), List.of("true", "false"));
	}
	public static WidgetOptionSchema range(String key, float value, float min, float max) {
		if (!Float.isFinite(value) || !Float.isFinite(min) || !Float.isFinite(max) || min > value || value > max) {
			throw new IllegalArgumentException("Invalid widget option range");
		}
		return new WidgetOptionSchema(key, Type.RANGE, Float.toString(value), List.of(Float.toString(min), Float.toString(max)));
	}
	public static WidgetOptionSchema choice(String key, String value, List<String> choices) {
		if (choices == null || choices.isEmpty() || !choices.contains(value)) throw new IllegalArgumentException("Invalid widget option choice");
		return new WidgetOptionSchema(key, Type.CHOICE, value, choices);
	}
	public static WidgetOptionSchema color(String key, String value) {
		if (value == null || !value.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Invalid widget option color");
		return new WidgetOptionSchema(key, Type.COLOR, value.toUpperCase(), List.of());
	}
}
