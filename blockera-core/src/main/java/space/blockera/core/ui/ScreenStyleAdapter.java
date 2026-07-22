package space.blockera.core.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.ChatOptionsScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraftforge.client.event.ScreenEvent;
import space.blockera.core.config.ClientConfig;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds Blockera chrome to vanilla screens without replacing their layout, state or callbacks. */
public final class ScreenStyleAdapter {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final PanoramaRenderer MENU_PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
	private static final Map<Screen, BlockeraTopNavigation> NAVIGATION =
			Collections.synchronizedMap(new WeakHashMap<>());

	private ScreenStyleAdapter() {
	}

	public static void attach(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (!shouldStyle(screen)) {
			NAVIGATION.remove(screen);
			return;
		}

		BlockeraTopNavigation navigation = new BlockeraTopNavigation(screen, activeTab(screen));
		NAVIGATION.put(screen, navigation);
		applyTopInset(event, screen.height);
		// addListener participates in input only. Rendering is intentionally centralized in Render.Post.
		event.addListener(navigation);
	}

	private static void applyTopInset(ScreenEvent.Init.Post event, int screenHeight) {
		int minimumTop = Integer.MAX_VALUE;
		for (var listener : event.getListenersList()) {
			if (listener instanceof TopInsettable insettable) {
				insettable.blockera$ensureTopInset(ScreenChromeLayout.CONTENT_TOP);
			}
			if (listener instanceof AbstractWidget widget
					&& ScreenChromeLayout.belongsToTopCluster(widget.y, widget.getHeight(), screenHeight)) {
				minimumTop = Math.min(minimumTop, widget.y);
			}
		}
		if (minimumTop == Integer.MAX_VALUE) return;
		int delta = ScreenChromeLayout.insetDelta(minimumTop);
		for (var listener : event.getListenersList()) {
			if (listener instanceof AbstractWidget widget
					&& ScreenChromeLayout.belongsToTopCluster(widget.y, widget.getHeight(), screenHeight)) {
				widget.y += delta;
			}
		}
	}

	public static void renderChrome(ScreenEvent.Render.Post event) {
		BlockeraTopNavigation navigation = NAVIGATION.get(event.getScreen());
		if (navigation != null) {
			// Repaint only the navigation bar; titles are relocated into the reserved content gap.
			GuiComponent.fill(event.getPoseStack(), 0, 0, event.getScreen().width,
					BlockeraTopNavigation.HEIGHT, THEME.topBarArgb());
			navigation.setBounds(0, 0, event.getScreen().width, BlockeraTopNavigation.HEIGHT);
			navigation.renderButton(event.getPoseStack(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
		}
	}

	/** Clears stale title-screen widgets while retaining the same panorama behind transparent menu screens. */
	public static void renderMenuBackdrop(ScreenEvent.Render.Pre event) {
		if (Minecraft.getInstance().level != null || !shouldStyle(event.getScreen())) return;
		MENU_PANORAMA.render(event.getPartialTick(), 1.0F);
		GuiComponent.fill(event.getPoseStack(), 0, 0, event.getScreen().width, event.getScreen().height,
				THEME.menuBackdropArgb());
	}

	public static boolean shouldStyle(Screen screen) {
		if (screen == null || !ClientConfig.FANCY_THEME.get()) {
			return false;
		}
		if (screen instanceof PauseScreen) {
			return ClientConfig.CUSTOM_PAUSE_SCREEN.get()
					&& !Boolean.getBoolean("blockera.disableCustomPauseScreen");
		}
		return screen instanceof OptionsScreen
				|| screen instanceof VideoSettingsScreen
				|| screen instanceof SoundOptionsScreen
				|| screen instanceof ControlsScreen
				|| screen instanceof LanguageSelectScreen
				|| screen instanceof ChatOptionsScreen
				|| screen instanceof AccessibilityOptionsScreen
				|| screen instanceof PackSelectionScreen
				|| screen instanceof JoinMultiplayerScreen
				|| screen instanceof SelectWorldScreen
				|| isAdditionalVanillaSettings(screen);
	}

	/** Used by renderer mixins; custom Blockera screens already draw through UiFont directly. */
	public static boolean shouldStyleCurrentScreen() {
		return shouldStyle(Minecraft.getInstance().screen);
	}

	private static BlockeraTopNavigation.Tab activeTab(Screen screen) {
		if (screen instanceof PauseScreen || screen instanceof SelectWorldScreen) return BlockeraTopNavigation.Tab.MENU;
		if (screen instanceof JoinMultiplayerScreen) return BlockeraTopNavigation.Tab.MULTIPLAYER;
		if (screen instanceof ChatOptionsScreen) return BlockeraTopNavigation.Tab.CHAT;
		return BlockeraTopNavigation.Tab.SETTINGS;
	}

	private static boolean isAdditionalVanillaSettings(Screen screen) {
		String name = screen.getClass().getName();
		return name.equals("net.minecraft.client.gui.screens.SkinCustomizationScreen")
				|| name.equals("net.minecraft.client.gui.screens.TelemetryInfoScreen")
				|| name.equals("net.minecraft.client.gui.screens.CreditsAndAttributionScreen")
				|| name.startsWith("net.minecraft.client.gui.screens.controls.")
				|| name.startsWith("net.minecraft.client.gui.screens.packs.");
	}
}
