package space.blockera.client;

import net.fabricmc.loader.api.FabricLoader;
import space.blockera.client.chat.ChatConfigStore;
import space.blockera.client.hitbox.HitboxConfigStore;
import space.blockera.client.hud.HudLayoutStore;
import space.blockera.client.visual.VisualConfigStore;

/** Services that are always present in the public Blockera Core. */
public final class BlockeraCoreServices {
	private static HitboxConfigStore hitboxes;
	private static HudLayoutStore hudLayouts;
	private static ChatConfigStore chat;
	private static VisualConfigStore visuals;

	private BlockeraCoreServices() {
	}

	public static synchronized void initialize() {
		if (hitboxes != null) {
			return;
		}
		var configDir = FabricLoader.getInstance().getConfigDir().resolve("blockera-core");
		hitboxes = new HitboxConfigStore(configDir.resolve("hitboxes.json"));
		hitboxes.load();
		hudLayouts = new HudLayoutStore(configDir.resolve("hud-layouts.json"));
		hudLayouts.load();
		chat = new ChatConfigStore(configDir.resolve("chat.json"));
		chat.load();
		visuals = new VisualConfigStore(configDir.resolve("visuals.json"));
		visuals.load();
	}

	public static HitboxConfigStore hitboxes() {
		requireInitialized();
		return hitboxes;
	}

	public static HudLayoutStore hudLayouts() {
		requireInitialized();
		return hudLayouts;
	}

	public static ChatConfigStore chat() {
		requireInitialized();
		return chat;
	}

	public static boolean visualsEnabled() {
		requireInitialized();
		return visuals.config().masterEnabled();
	}

	public static void setVisualsEnabled(boolean enabled) {
		requireInitialized();
		visuals.setMasterEnabled(enabled);
	}

	public static VisualConfigStore visuals() {
		requireInitialized();
		return visuals;
	}

	private static void requireInitialized() {
		if (hitboxes == null || hudLayouts == null || chat == null || visuals == null) {
			throw new IllegalStateException("Blockera Core services are not initialized");
		}
	}
}
