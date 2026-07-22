package space.blockera.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.blockera.client.chat.BlockeraChatRuntime;
import space.blockera.client.chat.ChatInteractionController;
import space.blockera.client.chat.DetachedChatPanels;
import space.blockera.client.hitbox.FabricHitboxRenderer;
import space.blockera.client.hud.BlockeraHudManager;
import space.blockera.client.ui.BlockeraInGameMenuScreen;

/** Public Blockera runtime: UI, HUD, widgets and first-party visual modules. */
public final class BlockeraCoreClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("Blockera Core");
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath("blockera_core", "controls")
	);
	private static KeyMapping zoom;
	private static boolean toggleSprint;

	@Override
	public void onInitializeClient() {
		BlockeraCoreServices.initialize();
		BlockeraChatRuntime.instance().initialize();
		KeyMapping openControlCenter = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.blockera_core.open_control_center",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			KEY_CATEGORY
		));
		zoom = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.blockera_core.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KEY_CATEGORY
		));
		KeyMapping toggleSprintKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.blockera_core.toggle_sprint", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_CAPS_LOCK, KEY_CATEGORY
		));
		BlockeraHudManager hud = new BlockeraHudManager(
			net.minecraft.client.Minecraft.getInstance(), BlockeraCoreServices.hudLayouts()
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ChatInteractionController.tick(client);
			while (openControlCenter.consumeClick()) {
				if (client.level != null && client.screen == null) {
					client.setScreen(new BlockeraInGameMenuScreen(false));
				}
			}
			while (toggleSprintKey.consumeClick()) toggleSprint = !toggleSprint;
			if (toggleSprint && client.player != null && client.options.keyUp.isDown()
				&& !client.options.keyShift.isDown()) {
				client.player.setSprinting(true);
			}
		});
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath("blockera_core", "widgets"),
			(graphics, tickCounter) -> hud.render(graphics)
		);
		HudElementRegistry.addLast(
			Identifier.fromNamespaceAndPath("blockera_core", "filtered_chats"),
			(graphics, tickCounter) -> {
				if (BlockeraCoreServices.visualsEnabled()) DetachedChatPanels.render(graphics);
			}
		);
		FabricHitboxRenderer hitboxes = new FabricHitboxRenderer(BlockeraCoreServices.hitboxes());
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(hitboxes::render);
		LOGGER.info("Blockera Core visual runtime initialized");
	}

	public static boolean isZooming() {
		return zoom != null && zoom.isDown();
	}
}
