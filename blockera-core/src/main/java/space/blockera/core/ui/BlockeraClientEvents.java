package space.blockera.core.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import space.blockera.core.BlockeraCore;
import space.blockera.core.hud.BlockeraHudOverlay;
import space.blockera.core.hud.CpsMeter;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.enhancement.EntityHitboxOverlay;
import space.blockera.core.ui.render.UiShaders;
import space.blockera.core.tools.LocalToolController;

import java.io.IOException;

/** Client-only event bridge. The Dist gate prevents Minecraft client classes from loading on a server. */
public final class BlockeraClientEvents {
	private static final KeyMapping OPEN_MENU = new KeyMapping(
			"key.blockera_core.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"key.categories.blockera_core");

	private BlockeraClientEvents() {
	}

	@Mod.EventBusSubscriber(modid = BlockeraCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static final class ModBus {
		private ModBus() {
		}

		@SubscribeEvent
		public static void registerOverlays(RegisterGuiOverlaysEvent event) {
			event.registerAboveAll("runtime_hud", BlockeraHudOverlay::render);
		}

		@SubscribeEvent
		public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
			event.register(OPEN_MENU);
			LocalToolController.register(event);
		}

		@SubscribeEvent
		@SuppressWarnings("removal")
		public static void registerShaders(RegisterShadersEvent event) throws IOException {
			event.registerShader(new ShaderInstance(event.getResourceManager(),
					new ResourceLocation(BlockeraCore.MOD_ID, "rounded_rect"), DefaultVertexFormat.POSITION_TEX),
					UiShaders::setRoundedRect);
		}
	}

	@Mod.EventBusSubscriber(modid = BlockeraCore.MOD_ID, value = Dist.CLIENT)
	public static final class ForgeBus {
		private ForgeBus() {
		}

		@SubscribeEvent
		public static void onScreenOpening(ScreenEvent.Opening event) {
			if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class
					&& ClientConfig.CUSTOM_TITLE_SCREEN.get()
					&& !Boolean.getBoolean("blockera.disableCustomTitleScreen")) {
				event.setNewScreen(new BlockeraTitleScreen());
			}
		}

		@SubscribeEvent
		public static void onScreenInit(ScreenEvent.Init.Post event) {
			ScreenStyleAdapter.attach(event);
		}

		@SubscribeEvent
		public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
			ScreenStyleAdapter.renderMenuBackdrop(event);
		}

		@SubscribeEvent
		public static void onScreenRender(ScreenEvent.Render.Post event) {
			ScreenStyleAdapter.renderChrome(event);
		}

		@SubscribeEvent
		public static void onMouseButton(InputEvent.MouseButton event) {
			CpsMeter.record(event.getButton(), event.getAction());
		}

		@SubscribeEvent
		public static void onBlockHighlight(RenderHighlightEvent.Block event) {
			if (!ClientConfig.BLOCK_HIGHLIGHT.get()) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public static void onRenderLevelStage(RenderLevelStageEvent event) {
			EntityHitboxOverlay.render(event);
		}

		@SubscribeEvent
		public static void onComputeFov(ViewportEvent.ComputeFov event) {
			LocalToolController.fov(event);
		}

		@SubscribeEvent
		public static void onClientTick(TickEvent.ClientTickEvent event) {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}

			Minecraft minecraft = Minecraft.getInstance();
			LocalToolController.tick();
			while (OPEN_MENU.consumeClick()) {
				if (minecraft.player != null && !(minecraft.screen instanceof BlockeraMenuScreen)) {
					minecraft.setScreen(new BlockeraMenuScreen(minecraft.screen));
				}
			}
		}
	}
}
