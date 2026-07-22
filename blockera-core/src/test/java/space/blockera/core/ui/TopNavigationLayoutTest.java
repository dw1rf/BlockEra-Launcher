package space.blockera.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TopNavigationLayoutTest {
	@Test
	void renderAndHitTestUseTheSameTabBoundaries() {
		TopNavigationLayout layout = TopNavigationLayout.calculate(0, 0, 1024, 26, 6);
		assertEquals(780, layout.width());
		for (int index = 0; index < 6; index++) {
			double middle = (layout.tabLeft(index) + layout.tabRight(index)) / 2.0;
			assertEquals(index, layout.tabAt(middle, 13));
		}
	}

	@Test
	void lastRemainderPixelBelongsToLastTab() {
		TopNavigationLayout layout = TopNavigationLayout.calculate(7, 3, 517, 26, 6);
		assertEquals(5, layout.tabAt(layout.right() - 1, layout.bottom() - 1));
		assertEquals(-1, layout.tabAt(layout.right(), layout.bottom() - 1));
	}
}
