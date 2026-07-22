package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

import java.util.Objects;
import java.util.function.Function;

/** Compact two-line HUD card shared by scalar local-data widgets. */
public final class TextHudWidget implements HudWidget {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final String id;
	private final String translationKey;
	private final HudCategory category;
	private final Function<HudDataSnapshot, String> value;

	public TextHudWidget(String id, String translationKey, HudCategory category,
			Function<HudDataSnapshot, String> value) {
		this.id = Objects.requireNonNull(id, "id");
		this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
		this.category = Objects.requireNonNull(category, "category");
		this.value = Objects.requireNonNull(value, "value");
	}

	@Override public String id() { return id; }
	@Override public Component title() { return Component.translatable(translationKey); }
	@Override public HudCategory category() { return category; }

	@Override
	public int width(HudDataSnapshot data) {
		return Math.max(82, Math.max(UiFont.width(title()), UiFont.width(Component.literal(value.apply(data)))) + 20);
	}

	@Override public int height(HudDataSnapshot data) { return 34; }

	@Override
	public void render(PoseStack poseStack, HudDataSnapshot data, int x, int y,
			HudWidgetSettings settings, boolean preview) {
		int width = width(data);
		if (settings.background) {
			int alpha = Math.round(218.0F * settings.opacity);
			BlockeraDraw.glassPanel(poseStack, x, y, x + width, y + height(data), 7,
					(alpha << 24) | 0x0D0D14, preview ? THEME.accentArgb() : ((alpha << 24) | 0x343147));
		}
		if (settings.showLabel && !settings.compact) UiFont.drawSmall(poseStack, title(), x + 10, y + 6, settings.labelArgb());
		UiFont.draw(poseStack, value.apply(data), x + 10, y + (settings.showLabel && !settings.compact ? 18 : 12), settings.valueArgb());
	}
}
