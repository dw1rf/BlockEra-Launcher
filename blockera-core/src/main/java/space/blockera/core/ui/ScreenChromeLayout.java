package space.blockera.core.ui;

/** Geometry policy for vanilla screens decorated with the Blockera top bar. */
public final class ScreenChromeLayout {
	public static final int CONTENT_GAP = 8;
	public static final int CONTENT_TOP = BlockeraTopNavigation.HEIGHT + CONTENT_GAP;
	public static final int TITLE_TOP = BlockeraTopNavigation.HEIGHT + 2;
	private static final int TOP_CLUSTER_HEIGHT = 24;
	private static final int BOTTOM_CONTROL_RESERVE = 32;

	private ScreenChromeLayout() {
	}

	/** Top controls move as a group so their original spacing is preserved. */
	public static boolean belongsToTopCluster(int currentTop, int widgetHeight, int screenHeight) {
		return currentTop < CONTENT_TOP + TOP_CLUSTER_HEIGHT
				&& currentTop + Math.max(0, widgetHeight) <= screenHeight - BOTTOM_CONTROL_RESERVE;
	}

	public static int insetDelta(int minimumTop) {
		return Math.max(0, CONTENT_TOP - minimumTop);
	}

	/** Moves only vanilla screen titles that would otherwise be painted inside the navigation bar. */
	public static int titleTop(int originalTop) {
		return originalTop < BlockeraTopNavigation.HEIGHT ? TITLE_TOP : originalTop;
	}
}
