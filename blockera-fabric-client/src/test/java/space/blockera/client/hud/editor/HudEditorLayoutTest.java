package space.blockera.client.hud.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudEditorLayoutTest {
	@Test
	void wideLayoutKeepsLibraryViewportAndInspectorOutsideHeaderAndEachOther() {
		HudEditorLayout layout = HudEditorLayout.calculate(1280, 720);

		assertFalse(layout.compactInspector());
		assertFalse(layout.header().intersects(layout.library()));
		assertFalse(layout.header().intersects(layout.viewport()));
		assertFalse(layout.header().intersects(layout.inspector()));
		assertFalse(layout.library().intersects(layout.viewport()));
		assertFalse(layout.viewport().intersects(layout.inspector()));
		assertTrue(layout.viewport().width() > layout.library().width());
	}

	@Test
	void narrowLayoutUsesCompactInspectorAndPreservesUsableViewport() {
		HudEditorLayout layout = HudEditorLayout.calculate(820, 460);

		assertTrue(layout.compactInspector());
		assertTrue(layout.viewport().width() >= 360);
		assertTrue(layout.inspector().right() <= 808);
		assertTrue(layout.inspector().bottom() <= 448);
	}

	@Test
	void previewTransformRoundTripsVirtualCoordinatesWithLetterboxing() {
		HudPreviewTransform transform = HudPreviewTransform.fit(new HudRect(240, 80, 720, 520), 1920, 1080);
		HudPreviewTransform.Point preview = transform.toPreview(960, 540);
		HudPreviewTransform.Point screen = transform.toScreen(preview.x(), preview.y());

		assertEquals(960.0, screen.x(), 0.001);
		assertEquals(540.0, screen.y(), 0.001);
		assertTrue(transform.contentBounds().left() >= 240);
		assertTrue(transform.contentBounds().right() <= 960);
		assertTrue(transform.contentBounds().top() >= 80);
		assertTrue(transform.contentBounds().bottom() <= 600);
	}
}
