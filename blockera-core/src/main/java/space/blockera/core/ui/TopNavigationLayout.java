package space.blockera.core.ui;

/** Pure geometry shared by top-navigation rendering and hit testing. */
public record TopNavigationLayout(int left, int top, int right, int bottom, int tabCount) {
	private static final int MAX_WIDTH = 780;

	public static TopNavigationLayout calculate(int x, int y, int width, int height, int tabCount) {
		int safeWidth = Math.max(0, width);
		int availableWidth = Math.min(safeWidth, MAX_WIDTH);
		int left = x + (safeWidth - availableWidth) / 2;
		return new TopNavigationLayout(left, y, left + availableWidth, y + Math.max(0, height),
				Math.max(1, tabCount));
	}

	public int tabLeft(int index) {
		return left + width() * clampIndex(index) / tabCount;
	}

	public int tabRight(int index) {
		return left + width() * (clampIndex(index) + 1) / tabCount;
	}

	public int tabAt(double mouseX, double mouseY) {
		if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom || width() == 0) {
			return -1;
		}
		int relativeX = Math.max(0, Math.min(width() - 1, (int) mouseX - left));
		return Math.min(tabCount - 1, relativeX * tabCount / width());
	}

	public int width() {
		return right - left;
	}

	private int clampIndex(int index) {
		return Math.max(0, Math.min(tabCount - 1, index));
	}
}
