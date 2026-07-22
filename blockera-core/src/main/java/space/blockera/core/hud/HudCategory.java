package space.blockera.core.hud;

public enum HudCategory {
	PERFORMANCE("blockera.hud.category.performance"),
	WORLD("blockera.hud.category.world"),
	PLAYER("blockera.hud.category.player"),
	SERVER("blockera.hud.category.server");

	private final String translationKey;

	HudCategory(String translationKey) {
		this.translationKey = translationKey;
	}

	public String translationKey() {
		return translationKey;
	}
}
