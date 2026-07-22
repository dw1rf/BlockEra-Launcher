package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;
import space.blockera.core.ui.UiFont;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Full-width tab navigation; unavailable destinations are intentionally non-interactive. */
public final class TopNavigation {
	public enum Tab {
		HOME("blockera.nav.home", true),
		MULTIPLAYER("blockera.nav.multiplayer", false),
		CHAT("blockera.nav.chat", false),
		BLOCKERA("blockera.nav.blockera", true),
		PLAYER("blockera.nav.player", false),
		SETTINGS("blockera.nav.settings", true);

		private final String translationKey;
		private final boolean available;
		Tab(String translationKey, boolean available) { this.translationKey = translationKey; this.available = available; }
	}

	private final ThemeTokens theme;
	private final Consumer<Tab> onSelected;
	private final Map<Tab, UiAnimation> hover = new EnumMap<>(Tab.class);
	private int left;
	private int top;
	private int right;
	private int height;

	public TopNavigation(ThemeTokens theme, Consumer<Tab> onSelected) {
		this.theme = theme;
		this.onSelected = onSelected;
		for (Tab tab : Tab.values()) hover.put(tab, new UiAnimation(0.0F));
	}

	public void setBounds(int left, int top, int right, int height) {
		this.left = left; this.top = top; this.right = right; this.height = height;
	}

	public void render(PoseStack poseStack, int mouseX, int mouseY) {
		GuiComponent.fill(poseStack, left, top, right, top + height, theme.topBarArgb());
		int availableWidth = Math.min(right - left, 780);
		int tabsLeft = left + (right - left - availableWidth) / 2;
		int tabWidth = Math.max(1, availableWidth / Tab.values().length);
		for (Tab tab : Tab.values()) {
			int index = tab.ordinal();
			int x = tabsLeft + index * tabWidth;
			boolean hovered = tab.available && contains(mouseX, mouseY, x, top, x + tabWidth, top + height);
			float amount = hover.get(tab).update(hovered ? 1.0F : 0.0F, 130);
			boolean active = tab == Tab.BLOCKERA;
			if (amount > 0.01F && !active) {
				BlockeraDraw.roundedRect(poseStack, x + 3, top + 3, x + tabWidth - 3, top + height - 3,
						5, withAlpha(theme.cardHoverArgb(), Math.round(70.0F * amount)));
			}
			int color = active ? theme.accentHoverArgb()
					: tab.available ? theme.textPrimaryArgb() : withAlpha(theme.textMutedArgb(), 115);
			UiFont.drawCentered(poseStack, Component.translatable(tab.translationKey), x + tabWidth / 2.0F,
					top + (height - 9) / 2.0F, color);
			if (active) GuiComponent.fill(poseStack, x + 10, top + height - 2, x + tabWidth - 10, top + height, theme.accentArgb());
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY, left, top, right, top + height)) return false;
		int availableWidth = Math.min(right - left, 780);
		int tabsLeft = left + (right - left - availableWidth) / 2;
		if (mouseX < tabsLeft || mouseX >= tabsLeft + availableWidth) return true;
		int tabWidth = Math.max(1, availableWidth / Tab.values().length);
		int index = Math.min(Tab.values().length - 1, Math.max(0, ((int) mouseX - tabsLeft) / tabWidth));
		Tab tab = Tab.values()[index];
		if (!tab.available || tab == Tab.BLOCKERA) return true;
		onSelected.accept(tab);
		return true;
	}

	private static boolean contains(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static int withAlpha(int color, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF); }
}
