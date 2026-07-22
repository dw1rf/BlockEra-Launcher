package space.blockera.client.hud;

import java.util.Objects;
import java.util.Set;

public record HudWidgetMetadata(
	String id,
	String translationKey,
	HudCategory category,
	Set<String> allowedOptions
) {
	public HudWidgetMetadata {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(translationKey, "translationKey");
		Objects.requireNonNull(category, "category");
		allowedOptions = Set.copyOf(allowedOptions);
		if (!id.matches("blockera:[a-z0-9_]+")) {
			throw new IllegalArgumentException("Invalid built-in HUD widget id: " + id);
		}
	}
}
