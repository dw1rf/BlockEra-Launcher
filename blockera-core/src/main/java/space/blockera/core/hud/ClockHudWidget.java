package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** Clock widget with typed real/world-time formatting options. */
public final class ClockHudWidget implements HudWidget {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();

	@Override public String id() { return BuiltinWidgetIds.CLOCK; }
	@Override public Component title() { return Component.translatable("blockera.hud.widget.clock"); }
	@Override public HudCategory category() { return HudCategory.WORLD; }

	@Override
	public int width(HudDataSnapshot data) {
		ClockWidgetSettings widest = new ClockWidgetSettings();
		widest.showRealTime = true;
		widest.showWorldTime = true;
		widest.showSeconds = true;
		widest.use24Hour = false;
		String value = value(data, widest);
		return Math.max(82, Math.max(UiFont.width(title()), UiFont.width(Component.literal(value))) + 20);
	}

	@Override public int height(HudDataSnapshot data) { return 34; }

	@Override
	public void render(PoseStack poseStack, HudDataSnapshot data, int x, int y,
			HudWidgetSettings settings, boolean preview) {
		String value = value(data, settings.clock);
		int width = Math.max(82, Math.max(UiFont.width(title()), UiFont.width(Component.literal(value))) + 20);
		if (settings.background) {
			int alpha = Math.round(218.0F * settings.opacity);
			BlockeraDraw.glassPanel(poseStack, x, y, x + width, y + height(data), 7,
					(alpha << 24) | 0x0D0D14, preview ? THEME.accentArgb() : ((alpha << 24) | 0x343147));
		}
		UiFont.drawSmall(poseStack, title(), x + 10, y + 6, settings.labelArgb());
		UiFont.draw(poseStack, value, x + 10, y + 18, settings.valueArgb());
	}

	private static String value(HudDataSnapshot data, ClockWidgetSettings settings) {
		return ClockDisplayFormatter.format(data.realTime(), data.worldTimeTicks(), settings);
	}
}
