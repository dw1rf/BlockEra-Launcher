package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import space.blockera.core.hud.VanillaHudElement;

/** Blockera-styled top-level options hub; nested vanilla screens remain intact for compatibility. */
public final class BlockeraOptionsHubScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final Screen parent;
	private int panelLeft;
	private int panelTop;
	private int panelRight;
	private int panelBottom;
	private boolean compact;

	public BlockeraOptionsHubScreen(Screen parent) {
		super(Component.translatable("blockera.options.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(620, Math.max(1, width - 24));
		int panelHeight = Math.min(300, Math.max(1, height - 24));
		panelLeft = (width - panelWidth) / 2;
		panelTop = (height - panelHeight) / 2;
		panelRight = panelLeft + panelWidth;
		panelBottom = panelTop + panelHeight;
		compact = panelHeight < 240 || panelWidth < 430;
		int gap = compact ? 5 : 8;
		int cardWidth = (panelWidth - 52 - gap) / 2;
		int cardHeight = compact ? 24 : 38;
		int left = panelLeft + 22;
		int right = left + cardWidth + gap;
		int top = panelTop + (compact ? 32 : 58);
		int step = cardHeight + (compact ? 4 : 8);
		addOption(left, top, cardWidth, cardHeight, "video", button -> minecraft.setScreen(new VideoSettingsScreen(this, minecraft.options)));
		addOption(right, top, cardWidth, cardHeight, "sound", button -> minecraft.setScreen(new SoundOptionsScreen(this, minecraft.options)));
		addOption(left, top + step, cardWidth, cardHeight, "controls", button -> minecraft.setScreen(new ControlsScreen(this, minecraft.options)));
		addOption(right, top + step, cardWidth, cardHeight, "language", button -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())));
		addOption(left, top + step * 2, cardWidth, cardHeight, "chat", button -> minecraft.setScreen(
				new BlockeraChatSettingsScreen(this, VanillaHudElement.CHAT,
						Component.translatable("blockera.setting.ingame_chat"))));
		addOption(right, top + step * 2, cardWidth, cardHeight, "resources", button -> minecraft.setScreen(new PackSelectionScreen(this,
				minecraft.getResourcePackRepository(), repository -> minecraft.reloadResourcePacks(),
				minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"))));
		addOption(left, top + step * 3, cardWidth, cardHeight, "accessibility", button -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options)));
		addOption(right, top + step * 3, cardWidth, cardHeight, "blockera", button -> minecraft.setScreen(new BlockeraMenuScreen(this)));
		addRenderableWidget(new BlockeraButton(panelRight - (compact ? 70 : 104), compact ? panelTop + 6 : panelBottom - 31,
				compact ? 58 : 82, compact ? 20 : 21,
				Component.translatable("blockera.common.done"), button -> onClose(), true));
	}

	private void addOption(int x, int y, int cardWidth, int cardHeight, String key, BlockeraButton.OnPress action) {
		addRenderableWidget(new BlockeraButton(x, y, cardWidth, cardHeight,
				Component.translatable("blockera.options." + key), action));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, THEME.menuBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, panelLeft, panelTop, panelRight, panelBottom,
				THEME.panelRadius(), THEME.glassPanelArgb(), THEME.borderArgb());
		UiFont.drawSemibold(poseStack, UiFont.ellipsize(title, panelRight - panelLeft - (compact ? 116 : 44), true),
				panelLeft + 22, panelTop + (compact ? 12 : 20), THEME.textPrimaryArgb());
		if (!compact) UiFont.drawSmall(poseStack, Component.translatable("blockera.options.subtitle"),
				panelLeft + 22, panelTop + 37, THEME.textMutedArgb());
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	@Override public void onClose() { minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
