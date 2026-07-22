package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import space.blockera.core.hud.VanillaHudElement;

/** Thin navigation chrome shared by styled vanilla screens; it never owns their content layout. */
public final class BlockeraTopNavigation extends AbstractWidget {
	public static final int HEIGHT = 26;
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();

	public enum Tab {
		MENU("blockera.nav.home", true),
		MULTIPLAYER("blockera.nav.multiplayer", true),
		CHAT("blockera.nav.chat", true),
		BLOCKERA("blockera.nav.blockera", true),
		PLAYER("blockera.nav.player", false),
		SETTINGS("blockera.nav.settings", true);

		private final String key;
		private final boolean available;

		Tab(String key, boolean available) {
			this.key = key;
			this.available = available;
		}
	}

	private final Screen owner;
	private final Tab activeTab;

	public BlockeraTopNavigation(Screen owner, Tab activeTab) {
		super(0, 0, owner.width, HEIGHT, Component.translatable("blockera.navigation"));
		this.owner = owner;
		this.activeTab = activeTab;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		setWidth(width);
		setHeight(height);
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, x, y, x + width, y + height, THEME.topBarArgb());
		TopNavigationLayout layout = layout();
		for (Tab tab : Tab.values()) {
			int tabLeft = layout.tabLeft(tab.ordinal());
			int tabRight = layout.tabRight(tab.ordinal());
			int tabWidth = tabRight - tabLeft;
			boolean hovered = tab.available && layout.tabAt(mouseX, mouseY) == tab.ordinal();
			if (hovered && tab != activeTab) {
				BlockeraDraw.roundedRect(poseStack, tabLeft + 10, y + 4, tabRight - 10,
						y + height - 4, 5, 0x2CFFFFFF);
			}
			int color = tab == activeTab ? THEME.accentHoverArgb()
					: tab.available ? (hovered ? THEME.textPrimaryArgb() : THEME.textSecondaryArgb())
					: THEME.textDisabledArgb();
			Component label = UiFont.ellipsize(Component.translatable(tab.key), Math.max(18, tabWidth - 10), false);
			UiFont.drawCentered(poseStack, label, tabLeft + tabWidth * 0.5F,
					y + (height - UiFont.REGULAR_SIZE) * 0.5F, color);
			if (tab == activeTab) {
				BlockeraDraw.roundedRect(poseStack, tabLeft + 12, y + height - 2,
						tabRight - 12, y + height, 1, THEME.accentArgb());
			}
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		int index = layout().tabAt(mouseX, mouseY);
		if (index < 0) return;
		Tab tab = Tab.values()[index];
		if (!tab.available || tab == activeTab) return;
		navigate(tab);
	}

	private TopNavigationLayout layout() {
		return TopNavigationLayout.calculate(x, y, width, height, Tab.values().length);
	}

	private void navigate(Tab tab) {
		Minecraft minecraft = Minecraft.getInstance();
		switch (tab) {
			case MENU -> minecraft.setScreen(minecraft.level == null ? new BlockeraTitleScreen() : new PauseScreen(true));
			case MULTIPLAYER -> minecraft.setScreen(new JoinMultiplayerScreen(owner));
			case CHAT -> minecraft.setScreen(new BlockeraChatSettingsScreen(owner, VanillaHudElement.CHAT,
					Component.translatable("blockera.setting.ingame_chat")));
			case BLOCKERA -> minecraft.setScreen(new BlockeraMenuScreen(owner));
			case SETTINGS -> minecraft.setScreen(new OptionsScreen(owner, minecraft.options));
			case PLAYER -> { }
		}
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
