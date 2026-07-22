package space.blockera.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScreenChromeLayoutTest {
	@Test
	void topControlsMoveAsOneGroup() {
		assertEquals(ScreenChromeLayout.CONTENT_TOP - 12, ScreenChromeLayout.insetDelta(12));
		assertEquals(0, ScreenChromeLayout.insetDelta(48));
	}

	@Test
	void footerControlNeverBelongsToTopCluster() {
		assertEquals(false, ScreenChromeLayout.belongsToTopCluster(340, 20, 360));
	}

	@Test
	void onlyTitlesInsideNavigationAreMovedBelowIt() {
		assertEquals(ScreenChromeLayout.TITLE_TOP, ScreenChromeLayout.titleTop(15));
		assertEquals(42, ScreenChromeLayout.titleTop(42));
	}
}
