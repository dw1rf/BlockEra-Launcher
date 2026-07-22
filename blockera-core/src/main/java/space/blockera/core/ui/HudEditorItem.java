package space.blockera.core.ui;

import net.minecraft.network.chat.Component;
import space.blockera.core.hud.HudCategory;

/** Common editor model for Blockera widgets and the closed set of supported vanilla HUD groups. */
public record HudEditorItem(String id, String typeId, Component title, HudCategory category,
		int width, int height, boolean vanilla) {
}
