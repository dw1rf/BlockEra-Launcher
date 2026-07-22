package space.blockera.core.ui;

import space.blockera.core.hud.HudPoint;

/** Uniform screen-to-preview mapping that preserves the current game-window aspect ratio. */
public final class HudPreviewTransform {
	private final int left;
	private final int top;
	private final int width;
	private final int height;
	private final int screenWidth;
	private final int screenHeight;
	private final float scale;

	private HudPreviewTransform(int left, int top, int width, int height, int screenWidth, int screenHeight, float scale) {
		this.left = left;
		this.top = top;
		this.width = width;
		this.height = height;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.scale = scale;
	}

	public static HudPreviewTransform fit(int areaLeft, int areaTop, int areaWidth, int areaHeight,
			int screenWidth, int screenHeight) {
		if (areaWidth <= 0 || areaHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
			throw new IllegalArgumentException("HUD preview dimensions must be positive");
		}
		float scale = Math.min(areaWidth / (float) screenWidth, areaHeight / (float) screenHeight);
		int width = Math.max(1, Math.round(screenWidth * scale));
		int height = Math.max(1, Math.round(screenHeight * scale));
		return new HudPreviewTransform(areaLeft + (areaWidth - width) / 2, areaTop + (areaHeight - height) / 2,
				width, height, screenWidth, screenHeight, scale);
	}

	public HudPoint toPreview(float screenX, float screenY) {
		return new HudPoint(Math.round(left + screenX * scale), Math.round(top + screenY * scale));
	}

	public HudPoint toScreen(float previewX, float previewY) {
		return new HudPoint(Math.round((previewX - left) / scale), Math.round((previewY - top) / scale));
	}

	public boolean contains(double x, double y) {
		return x >= left && x <= left + width && y >= top && y <= top + height;
	}

	public int left() { return left; }
	public int top() { return top; }
	public int width() { return width; }
	public int height() { return height; }
	public int screenWidth() { return screenWidth; }
	public int screenHeight() { return screenHeight; }
	public float scale() { return scale; }
}
