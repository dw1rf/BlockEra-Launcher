package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** News surface with a truthful backend-unavailable empty state. */
public final class NewsCard {
	private final ThemeTokens theme;

	public NewsCard(ThemeTokens theme) { this.theme = theme; }

	public void render(PoseStack poseStack, int left, int top, int right, int bottom) {
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, theme.cardRadius(), theme.glassCardArgb(), theme.borderArgb());
		int imageWidth = Math.min(88, Math.max(54, (right - left) / 4));
		BlockeraDraw.roundedRect(poseStack, left + 8, top + 8, left + imageWidth, bottom - 8,
				theme.smallRadius(), theme.accentMutedArgb());
		BlockeraDraw.roundedRect(poseStack, left + 17, top + 17, left + imageWidth - 9, bottom - 17,
				theme.smallRadius(), 0x241F2330);
		int textLeft = left + imageWidth + 9;
		UiFont.drawSmall(poseStack, Component.translatable("blockera.main.news.tag"), textLeft, top + 9, theme.accentHoverArgb());
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.main.news.unavailable"), textLeft, top + 23,
				theme.textPrimaryArgb());
		UiFont.drawSmall(poseStack, UiFont.ellipsize(Component.translatable("blockera.main.news.unavailable.description"),
				right - textLeft - 8, false), textLeft, top + 39, theme.textSecondaryArgb());
	}
}
