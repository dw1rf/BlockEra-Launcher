package space.blockera.core.hud;

/** Integer editor/runtime bounds after scale has been applied. */
public record HudWidgetBounds(int left, int top, int right, int bottom) {
	public int width() { return Math.max(0, right - left); }
	public int height() { return Math.max(0, bottom - top); }
	public int centerX() { return left + width() / 2; }
	public int centerY() { return top + height() / 2; }

	public boolean contains(double x, double y) {
		return x >= left && x <= right && y >= top && y <= bottom;
	}

	public boolean intersects(HudWidgetBounds other) {
		return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
	}

	public HudWidgetBounds intersection(HudWidgetBounds other) {
		if (!intersects(other)) return null;
		return new HudWidgetBounds(Math.max(left, other.left), Math.max(top, other.top),
				Math.min(right, other.right), Math.min(bottom, other.bottom));
	}

	public HudWidgetBounds moveTo(int x, int y) { return new HudWidgetBounds(x, y, x + width(), y + height()); }
}
