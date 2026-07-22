package space.blockera.client.hud.editor;

public record HudEditorLayout(
	HudRect header,
	HudRect library,
	HudRect viewport,
	HudRect inspector,
	boolean compactInspector
) {
	private static final int MARGIN = 12;
	private static final int GAP = 12;
	private static final int HEADER_HEIGHT = 56;
	private static final int COMPACT_THRESHOLD = 900;

	public static HudEditorLayout calculate(int screenWidth, int screenHeight) {
		if (screenWidth < 640 || screenHeight < 360) {
			throw new IllegalArgumentException("HUD editor requires at least 640x360 GUI pixels");
		}
		HudRect header = new HudRect(0, 0, screenWidth, HEADER_HEIGHT);
		int contentTop = HEADER_HEIGHT + MARGIN;
		int contentHeight = screenHeight - contentTop - MARGIN;
		boolean compact = screenWidth < COMPACT_THRESHOLD;
		int libraryWidth = compact ? 190 : Math.min(240, Math.max(210, screenWidth / 6));
		HudRect library = new HudRect(MARGIN, contentTop, libraryWidth, contentHeight);
		if (compact) {
			int viewportLeft = library.right() + GAP;
			HudRect viewport = new HudRect(
				viewportLeft,
				contentTop,
				screenWidth - viewportLeft - MARGIN,
				contentHeight
			);
			int inspectorWidth = Math.min(280, screenWidth - MARGIN * 2);
			int inspectorHeight = Math.min(380, contentHeight);
			HudRect inspector = new HudRect(
				screenWidth - inspectorWidth - MARGIN,
				contentTop,
				inspectorWidth,
				inspectorHeight
			);
			return new HudEditorLayout(header, library, viewport, inspector, true);
		}
		int inspectorWidth = Math.min(300, Math.max(250, screenWidth / 5));
		int viewportLeft = library.right() + GAP;
		int viewportRight = screenWidth - MARGIN - inspectorWidth - GAP;
		HudRect viewport = new HudRect(viewportLeft, contentTop, viewportRight - viewportLeft, contentHeight);
		HudRect inspector = new HudRect(viewportRight + GAP, contentTop, inspectorWidth, contentHeight);
		return new HudEditorLayout(header, library, viewport, inspector, false);
	}
}
