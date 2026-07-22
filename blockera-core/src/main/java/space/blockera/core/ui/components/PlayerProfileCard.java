package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** Local player summary. Network-owned fields deliberately show an em dash until a backend exists. */
public final class PlayerProfileCard {
	private final ThemeTokens theme;

	public PlayerProfileCard(ThemeTokens theme) { this.theme = theme; }

	public void render(PoseStack poseStack, int left, int top, int right, int bottom, String playerName) {
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, theme.panelRadius(), theme.glassPanelArgb(), theme.borderArgb());
		BlockeraDraw.roundedRect(poseStack, left + 12, top + 12, left + 48, top + 48, 10, theme.accentMutedArgb());
		Component initial = Component.literal(playerName.isBlank() ? "B" : playerName.substring(0, 1).toUpperCase());
		UiFont.drawCentered(poseStack, initial, left + 30, top + 25, theme.accentHoverArgb());
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.main.hello", playerName), left + 57, top + 14,
				theme.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.main.status.local"), left + 57, top + 30,
				theme.textSecondaryArgb());
		int rowTop = top + 60;
		row(poseStack, left + 12, right - 12, rowTop, "blockera.main.server_online", "—");
		row(poseStack, left + 12, right - 12, rowTop + 22, "blockera.main.balance", "—");
		row(poseStack, left + 12, right - 12, rowTop + 44, "blockera.main.play_time", "—");
	}

	private void row(PoseStack poseStack, int left, int right, int y, String key, String value) {
		UiFont.drawSmall(poseStack, Component.translatable(key), left, y, theme.textSecondaryArgb());
		int valueWidth = UiFont.width(Component.literal(value));
		UiFont.draw(poseStack, value, right - valueWidth, y - 1, theme.textPrimaryArgb());
	}
}
