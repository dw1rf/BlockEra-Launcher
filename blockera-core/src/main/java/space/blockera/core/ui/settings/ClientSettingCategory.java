package space.blockera.core.ui.settings;

import space.blockera.core.ui.BlockeraIcon;

public enum ClientSettingCategory {
	INTERFACE("blockera.menu.interface", BlockeraIcon.APPEARANCE),
	GAME_ENHANCEMENTS("blockera.menu.game_enhancements", BlockeraIcon.LIGHT),
	PVP("blockera.menu.pvp", BlockeraIcon.COMBAT),
	TOOLS("blockera.menu.tools", BlockeraIcon.TOOLS),
	WIDGETS("blockera.menu.widgets", BlockeraIcon.HUD),
	SETTINGS("blockera.menu.settings", BlockeraIcon.SETTINGS);

	private final String translationKey;
	private final BlockeraIcon icon;

	ClientSettingCategory(String translationKey, BlockeraIcon icon) {
		this.translationKey = translationKey;
		this.icon = icon;
	}

	public String translationKey() { return translationKey; }
	public BlockeraIcon icon() { return icon; }
}
