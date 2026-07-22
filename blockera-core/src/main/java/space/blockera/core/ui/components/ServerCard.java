package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** Planned first-party server row without fabricated online or ping values. */
public final class ServerCard {
	private final ThemeTokens theme;

	public ServerCard(ThemeTokens theme) { this.theme = theme; }

	public void render(PoseStack poseStack, int left, int top, int right, String nameKey) {
		BlockeraDraw.roundedRect(poseStack, left, top, right, top + 31, theme.smallRadius(), theme.cardArgb());
		BlockeraDraw.roundedRect(poseStack, left + 9, top + 11, left + 17, top + 19, 4, theme.textDisabledArgb());
		UiFont.drawSemibold(poseStack, Component.translatable(nameKey), left + 24, top + 7, theme.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.main.server.unavailable"), left + 24, top + 19,
				theme.textSecondaryArgb());
		UiFont.draw(poseStack, "—", right - 17, top + 9, theme.textDisabledArgb());
	}
}
