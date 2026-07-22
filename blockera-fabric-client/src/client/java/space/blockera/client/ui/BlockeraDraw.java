package space.blockera.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Small loader-independent-looking facade; render and hit bounds share the same integers. */
public final class BlockeraDraw {
    private BlockeraDraw() {
    }

	public static void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
		roundedBorder(graphics, left, top, right, bottom, ThemeTokens.RADIUS,
			ThemeTokens.BORDER, ThemeTokens.PANEL);
	}

	public static void card(GuiGraphics graphics, int left, int top, int right, int bottom, boolean hovered) {
		roundedBorder(graphics, left, top, right, bottom, ThemeTokens.RADIUS,
			ThemeTokens.BORDER, hovered ? ThemeTokens.CARD_HOVER : ThemeTokens.CARD);
	}

	public static void field(GuiGraphics graphics, int left, int top, int right, int bottom, boolean focused) {
		roundedRect(graphics, left, top, right, bottom, ThemeTokens.RADIUS,
			focused ? ThemeTokens.FIELD_FOCUSED : ThemeTokens.FIELD);
		if (focused) {
			insideOutline(graphics, left, top, right, bottom, ThemeTokens.RADIUS, ThemeTokens.ACCENT);
		}
	}

	/** A single-surface control. Nothing is drawn outside its actual hit bounds. */
	public static void button(GuiGraphics graphics, int left, int top, int right, int bottom,
		int fill, boolean focused, boolean accent) {
		roundedRect(graphics, left, top, right, bottom, ThemeTokens.RADIUS, fill);
		int highlight = accent ? 0x55FFFFFF : 0x28FFFFFF;
		graphics.fill(left + ThemeTokens.RADIUS, top, right - ThemeTokens.RADIUS, top + 1, highlight);
		if (focused) {
			graphics.fill(left + ThemeTokens.RADIUS, bottom - 1, right - ThemeTokens.RADIUS, bottom,
				accent ? 0x88FFFFFF : ThemeTokens.ACCENT);
		}
	}

	public static void roundedBorder(GuiGraphics graphics, int left, int top, int right, int bottom,
		int radius, int border, int fill) {
		roundedRect(graphics, left, top, right, bottom, radius, fill);
		insideOutline(graphics, left, top, right, bottom, radius, border);
	}

	private static void insideOutline(GuiGraphics graphics, int left, int top, int right, int bottom,
		int radius, int color) {
		if (right - left < 3 || bottom - top < 3) return;
		graphics.fill(left + radius, top, right - radius, top + 1, color);
		graphics.fill(left + radius, bottom - 1, right - radius, bottom, color);
		graphics.fill(left, top + radius, left + 1, bottom - radius, color);
		graphics.fill(right - 1, top + radius, right, bottom - radius, color);
	}

	public static void roundedRect(GuiGraphics graphics, int left, int top, int right, int bottom,
		int radius, int color) {
		if (right <= left || bottom <= top) return;
		int safeRadius = Math.max(0, Math.min(radius, Math.min((right - left) / 2, (bottom - top) / 2)));
		if (safeRadius == 0) {
			graphics.fill(left, top, right, bottom, color);
			return;
		}
		graphics.fill(left + safeRadius, top, right - safeRadius, bottom, color);
		graphics.fill(left, top + safeRadius, right, bottom - safeRadius, color);
		for (int row = 0; row < safeRadius; row++) {
			int inset = cornerInset(safeRadius, row);
			graphics.fill(left + inset, top + row, right - inset, top + row + 1, color);
			graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color);
		}
	}

	private static int cornerInset(int radius, int row) {
		double y = radius - row - 0.5D;
		return Math.max(0, (int) Math.ceil(radius - Math.sqrt(radius * radius - y * y)));
	}
}
