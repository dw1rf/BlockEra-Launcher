package space.blockera.core.api;

import net.minecraft.network.chat.Component;
import space.blockera.core.hud.HudCategory;

/** Base contract for first-party Blockera UI elements. */
public interface Widget {
	String id();

	Component title();

	HudCategory category();
}
