package space.blockera.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LayoutMetricsTest {
	@Test
	void usesTwoColumnsOnWideGui() {
		LayoutMetrics metrics = LayoutMetrics.calculate(960, 540);

		assertTrue(metrics.twoColumns());
		assertFalse(metrics.compact());
		assertTrue(metrics.panelLeft() < metrics.panelRight());
	}

	@Test
	void collapsesCardsOnNarrowGui() {
		LayoutMetrics metrics = LayoutMetrics.calculate(320, 180);

		assertFalse(metrics.twoColumns());
		assertTrue(metrics.compact());
		assertTrue(metrics.panelBottom() > metrics.panelTop());
	}
}
