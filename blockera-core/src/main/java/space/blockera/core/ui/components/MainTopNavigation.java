package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;
import space.blockera.core.ui.UiFont;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Compact launcher-like navigation for the custom Minecraft title screen. */
public final class MainTopNavigation {
	public enum Tab {
		HOME("blockera.nav.home"), MULTIPLAYER("blockera.nav.multiplayer"), MODS("blockera.title.mods"),
		SETTINGS("blockera.nav.settings"), COSMETICS("blockera.title.cosmetics");
		private final String key;
		Tab(String key) { this.key = key; }
	}

	private final ThemeTokens theme;
	private final Consumer<Tab> callback;
	private final Map<Tab, UiAnimation> hover = new EnumMap<>(Tab.class);
	private int left;
	private int top;
	private int right;
	private int bottom;

	public MainTopNavigation(ThemeTokens theme, Consumer<Tab> callback) {
		this.theme = theme;
		this.callback = callback;
		for (Tab tab : Tab.values()) hover.put(tab, new UiAnimation(0.0F));
	}

	public void setBounds(int left, int top, int right, int bottom) {
		this.left = left; this.top = top; this.right = right; this.bottom = bottom;
	}

	public void render(PoseStack poseStack, int mouseX, int mouseY) {
		int tabWidth = Math.max(1, (right - left) / Tab.values().length);
		for (Tab tab : Tab.values()) {
			int x = left + tab.ordinal() * tabWidth;
			boolean hovered = contains(mouseX, mouseY, x, top, x + tabWidth, bottom);
			float amount = hover.get(tab).update(hovered ? 1.0F : 0.0F, space.blockera.core.ui.UiMotionTokens.HOVER_MILLIS);
			if (amount > 0.01F) BlockeraDraw.roundedRect(poseStack, x + 3, top + 3,
					x + tabWidth - 3, bottom - 3, theme.smallRadius(), withAlpha(theme.cardHoverArgb(), Math.round(170 * amount)));
			UiFont.drawCentered(poseStack, Component.translatable(tab.key), x + tabWidth * 0.5F,
					top + (bottom - top - UiFont.REGULAR_SIZE) * 0.5F,
					tab == Tab.HOME ? theme.textPrimaryArgb() : theme.textSecondaryArgb());
		}
		BlockeraDraw.roundedRect(poseStack, left + 8, bottom - 2, left + tabWidth - 8, bottom,
				1, theme.accentArgb());
	}

	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY, left, top, right, bottom)) return false;
		int tabWidth = Math.max(1, (right - left) / Tab.values().length);
		int index = Math.max(0, Math.min(Tab.values().length - 1, ((int) mouseX - left) / tabWidth));
		callback.accept(Tab.values()[index]);
		return true;
	}

	private static boolean contains(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static int withAlpha(int color, int alpha) { return alpha << 24 | color & 0xFFFFFF; }
}
