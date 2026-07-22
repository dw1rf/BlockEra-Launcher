package space.blockera.core.ui;

/** Responsive screen geometry derived from the current GUI-scaled viewport. */
public record LayoutMetrics(
		int left, int top, int right, int bottom, int topBarHeight,
		int sidebarWidth, int gap, int panelLeft, int panelTop, int panelRight, int panelBottom,
		boolean compact, boolean twoColumns) {
	public static LayoutMetrics calculate(int width, int height) {
		boolean compact = width < 560 || height < 300;
		int horizontalMargin = compact ? 6 : Math.max(10, (width - 980) / 2);
		int verticalMargin = compact ? 5 : 9;
		int topBarHeight = compact ? 22 : 26;
		int left = horizontalMargin;
		int right = width - horizontalMargin;
		int top = verticalMargin;
		int bottom = height - verticalMargin;
		int gap = compact ? 5 : 8;
		int sidebarWidth = compact ? Math.min(104, Math.max(88, (right - left) / 3)) : 144;
		int panelLeft = left + sidebarWidth + gap;
		int panelTop = top + topBarHeight + gap;
		int panelRight = right;
		int panelBottom = bottom;
		boolean twoColumns = panelRight - panelLeft >= 410;
		return new LayoutMetrics(left, top, right, bottom, topBarHeight, sidebarWidth, gap,
				panelLeft, panelTop, panelRight, panelBottom, compact, twoColumns);
	}
}
