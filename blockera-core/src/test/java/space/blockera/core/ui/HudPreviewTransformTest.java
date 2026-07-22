package space.blockera.core.ui;

import org.junit.jupiter.api.Test;
import space.blockera.core.hud.HudPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPreviewTransformTest {
	@Test
	void fitPreservesAspectRatioAndCentersViewport() {
		HudPreviewTransform transform = HudPreviewTransform.fit(10, 20, 800, 600, 1920, 1080);
		assertEquals(800, transform.width());
		assertEquals(450, transform.height());
		assertEquals(10, transform.left());
		assertEquals(95, transform.top());
	}

	@Test
	void screenAndPreviewCoordinatesRoundTrip() {
		HudPreviewTransform transform = HudPreviewTransform.fit(30, 40, 640, 360, 1280, 720);
		HudPoint preview = transform.toPreview(512, 288);
		HudPoint screen = transform.toScreen(preview.x(), preview.y());
		assertEquals(512, screen.x(), 1);
		assertEquals(288, screen.y(), 1);
		assertTrue(transform.contains(preview.x(), preview.y()));
		assertFalse(transform.contains(0, 0));
	}
}
