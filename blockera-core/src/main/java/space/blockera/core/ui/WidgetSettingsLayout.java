package space.blockera.core.ui;

/** Responsive fixed-header/fixed-footer geometry for the widget settings screen. */
public record WidgetSettingsLayout(
		int left, int top, int right, int bottom,
		int headerBottom, int contentTop, int contentBottom, int footerTop,
		boolean compactHeader, int columns, int contentHeight, int maxScroll) {
	public static final int CONTENT_PADDING = 14;
	public static final int PREVIEW_HEIGHT = 140;
	public static final int SECTION_LABEL_HEIGHT = 18;
	public static final int ROW_HEIGHT = 34;
	public static final int ROW_GAP = 8;
	public static final int SECTION_GAP = 16;
	private static final int HEADER_HEIGHT = 50;
	private static final int COMPACT_HEADER_HEIGHT = 78;
	private static final int FOOTER_HEIGHT = 44;

	public static WidgetSettingsLayout calculate(int screenWidth, int screenHeight) {
		return calculate(screenWidth, screenHeight, 0);
	}

	public static WidgetSettingsLayout calculate(int screenWidth, int screenHeight, int extraSectionItems) {
		int panelWidth = Math.max(1, Math.min(900, screenWidth - 20));
		int panelHeight = Math.max(1, Math.min(500, screenHeight - 20));
		int left = (screenWidth - panelWidth) / 2;
		int top = (screenHeight - panelHeight) / 2;
		int right = left + panelWidth;
		int bottom = top + panelHeight;
		boolean compactHeader = panelWidth < 600;
		int headerBottom = Math.min(bottom, top + (compactHeader ? COMPACT_HEADER_HEIGHT : HEADER_HEIGHT));
		int footerTop = Math.max(headerBottom, bottom - FOOTER_HEIGHT);
		int contentTop = headerBottom;
		int contentBottom = footerTop;
		int innerWidth = Math.max(0, panelWidth - CONTENT_PADDING * 2);
		int columns = innerWidth >= 560 ? 2 : 1;
		int extraSectionRows = extraSectionItems <= 0 ? 0 : (columns == 2 ? (extraSectionItems + 1) / 2 : extraSectionItems);
		int contentHeight = calculateContentHeight(columns, extraSectionRows);
		int maxScroll = Math.max(0, contentHeight - Math.max(0, contentBottom - contentTop));
		return new WidgetSettingsLayout(left, top, right, bottom, headerBottom, contentTop, contentBottom,
				footerTop, compactHeader, columns, contentHeight, maxScroll);
	}

	public int clampScroll(int scroll) {
		return Math.max(0, Math.min(maxScroll, scroll));
	}

	private static int calculateContentHeight(int columns, int extraSectionRows) {
		int layoutRows = columns == 2 ? 2 : 4;
		int colorRows = columns == 2 ? 2 : 3;
		int extra = extraSectionRows <= 0 ? 0 : SECTION_GAP + SECTION_LABEL_HEIGHT
				+ extraSectionRows * ROW_HEIGHT + Math.max(0, extraSectionRows - 1) * ROW_GAP;
		return CONTENT_PADDING + PREVIEW_HEIGHT + SECTION_GAP
				+ SECTION_LABEL_HEIGHT + layoutRows * ROW_HEIGHT + Math.max(0, layoutRows - 1) * ROW_GAP
				+ SECTION_GAP + SECTION_LABEL_HEIGHT + colorRows * ROW_HEIGHT
				+ Math.max(0, colorRows - 1) * ROW_GAP + extra + CONTENT_PADDING;
	}
}
