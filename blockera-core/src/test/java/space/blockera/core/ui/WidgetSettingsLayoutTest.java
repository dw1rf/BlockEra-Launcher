package space.blockera.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WidgetSettingsLayoutTest {
	@Test
	void fixedRegionsNeverOverlapAtDesktopSize() {
		WidgetSettingsLayout layout = WidgetSettingsLayout.calculate(1024, 576);
		assertTrue(layout.headerBottom() <= layout.contentTop());
		assertTrue(layout.contentBottom() <= layout.footerTop());
		assertTrue(layout.footerTop() < layout.bottom());
		assertEquals(2, layout.columns());
	}

	@Test
	void compactScreenScrollsInsteadOfCoveringFooter() {
		WidgetSettingsLayout layout = WidgetSettingsLayout.calculate(480, 270);
		assertTrue(layout.compactHeader());
		assertEquals(1, layout.columns());
		assertTrue(layout.maxScroll() > 0);
		assertEquals(layout.maxScroll(), layout.clampScroll(Integer.MAX_VALUE));
		assertEquals(0, layout.clampScroll(-50));
	}
}
