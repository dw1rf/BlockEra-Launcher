package space.blockera.core.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudAnchorTest {
	@Test
	void resolvesAndReconstructsOffsetsAtEveryAnchor() {
		for (HudAnchor anchor : HudAnchor.values()) {
			HudPoint point = anchor.resolve(1920, 1080, 120, 34, 1.5F, -16, 24);
			HudPoint offsets = anchor.offsetsFor(point.x(), point.y(), 1920, 1080, 120, 34, 1.5F);
			assertEquals(new HudPoint(-16, 24), offsets);
		}
	}

	@Test
	void scaleChangesTheRightAnchorOrigin() {
		assertEquals(new HudPoint(1800, 1046), HudAnchor.BOTTOM_RIGHT.resolve(1920, 1080, 120, 34, 1.0F, 0, 0));
		assertEquals(new HudPoint(1680, 1012), HudAnchor.BOTTOM_RIGHT.resolve(1920, 1080, 120, 34, 2.0F, 0, 0));
	}
}
