package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

public final class EffectsHudWidget implements HudWidget {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();

	@Override public String id() { return BuiltinWidgetIds.EFFECTS; }
	@Override public Component title() { return Component.translatable("blockera.hud.widget.effects"); }
	@Override public HudCategory category() { return HudCategory.PLAYER; }

	@Override
	public int width(HudDataSnapshot data) {
		int width = UiFont.width(title());
		for (String effect : data.effects()) width = Math.max(width, UiFont.width(Component.literal(effect)));
		return Math.max(96, width + 20);
	}

	@Override public int height(HudDataSnapshot data) { return 22 + Math.min(5, data.effects().size()) * 11; }

	@Override
	public void render(PoseStack poseStack, HudDataSnapshot data, int x, int y,
			HudWidgetSettings settings, boolean preview) {
		int alpha = Math.round(218.0F * settings.opacity);
		if (settings.background) {
			BlockeraDraw.glassPanel(poseStack, x, y, x + width(data), y + height(data), 7,
					(alpha << 24) | 0x0D0D14, preview ? THEME.accentArgb() : ((alpha << 24) | 0x343147));
		}
		UiFont.drawSmall(poseStack, title(), x + 10, y + 6, settings.labelArgb());
		int row = 0;
		for (String effect : data.effects()) {
			if (row == 5) break;
			UiFont.draw(poseStack, effect, x + 10, y + 18 + row * 11, settings.valueArgb());
			row++;
		}
	}
}
