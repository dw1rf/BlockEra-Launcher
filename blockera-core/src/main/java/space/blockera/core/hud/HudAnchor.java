package space.blockera.core.hud;

/** Nine stable screen-relative anchors used by persisted layouts. */
public enum HudAnchor {
	TOP_LEFT(0.0F, 0.0F), TOP_CENTER(0.5F, 0.0F), TOP_RIGHT(1.0F, 0.0F),
	CENTER_LEFT(0.0F, 0.5F), CENTER(0.5F, 0.5F), CENTER_RIGHT(1.0F, 0.5F),
	BOTTOM_LEFT(0.0F, 1.0F), BOTTOM_CENTER(0.5F, 1.0F), BOTTOM_RIGHT(1.0F, 1.0F);

	private final float xFactor;
	private final float yFactor;

	HudAnchor(float xFactor, float yFactor) {
		this.xFactor = xFactor;
		this.yFactor = yFactor;
	}

	public HudPoint resolve(int screenWidth, int screenHeight, int widgetWidth, int widgetHeight,
			float scale, int offsetX, int offsetY) {
		int scaledWidth = Math.round(widgetWidth * scale);
		int scaledHeight = Math.round(widgetHeight * scale);
		int baseX = Math.round((screenWidth - scaledWidth) * xFactor);
		int baseY = Math.round((screenHeight - scaledHeight) * yFactor);
		return new HudPoint(baseX + offsetX, baseY + offsetY);
	}

	public HudPoint offsetsFor(int absoluteX, int absoluteY, int screenWidth, int screenHeight,
			int widgetWidth, int widgetHeight, float scale) {
		HudPoint base = resolve(screenWidth, screenHeight, widgetWidth, widgetHeight, scale, 0, 0);
		return new HudPoint(absoluteX - base.x(), absoluteY - base.y());
	}

	public static HudAnchor nearest(int centerX, int centerY, int screenWidth, int screenHeight) {
		int column = centerX < screenWidth / 3 ? 0 : centerX > screenWidth * 2 / 3 ? 2 : 1;
		int row = centerY < screenHeight / 3 ? 0 : centerY > screenHeight * 2 / 3 ? 2 : 1;
		return values()[row * 3 + column];
	}
}
