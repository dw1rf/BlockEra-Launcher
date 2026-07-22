package space.blockera.client.hud.editor;

public final class HudPreviewTransform {
	private final HudRect contentBounds;
	private final int screenWidth;
	private final int screenHeight;
	private final double scale;

	private HudPreviewTransform(HudRect contentBounds, int screenWidth, int screenHeight, double scale) {
		this.contentBounds = contentBounds;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.scale = scale;
	}

	public static HudPreviewTransform fit(HudRect viewport, int screenWidth, int screenHeight) {
		if (screenWidth <= 0 || screenHeight <= 0) {
			throw new IllegalArgumentException("Virtual screen dimensions must be positive");
		}
		double scale = Math.min(viewport.width() / (double) screenWidth,
			viewport.height() / (double) screenHeight);
		int contentWidth = Math.max(1, (int) Math.floor(screenWidth * scale));
		int contentHeight = Math.max(1, (int) Math.floor(screenHeight * scale));
		int contentX = viewport.x() + (viewport.width() - contentWidth) / 2;
		int contentY = viewport.y() + (viewport.height() - contentHeight) / 2;
		return new HudPreviewTransform(
			new HudRect(contentX, contentY, contentWidth, contentHeight),
			screenWidth,
			screenHeight,
			scale
		);
	}

	public Point toPreview(double screenX, double screenY) {
		return new Point(contentBounds.x() + screenX * scale, contentBounds.y() + screenY * scale);
	}

	public Point toScreen(double previewX, double previewY) {
		return new Point((previewX - contentBounds.x()) / scale, (previewY - contentBounds.y()) / scale);
	}

	public HudRect contentBounds() {
		return contentBounds;
	}

	public int screenWidth() {
		return screenWidth;
	}

	public int screenHeight() {
		return screenHeight;
	}

	public double scale() {
		return scale;
	}

	public record Point(double x, double y) {
	}
}
