package space.blockera.core.ui.settings;

public enum SettingFilter {
	ALL("blockera.filter.all"),
	ENABLED("blockera.filter.enabled"),
	AVAILABLE("blockera.filter.available");

	private final String translationKey;

	SettingFilter(String translationKey) { this.translationKey = translationKey; }
	public String translationKey() { return translationKey; }

	public SettingFilter next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
