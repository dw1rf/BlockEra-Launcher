package space.blockera.core.hud;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudEditorManagersTest {
	@Test
	void collisionManagerReportsExactIntersectionAndFindsFreePosition() {
		HudCollisionManager manager = new HudCollisionManager();
		var bounds = new LinkedHashMap<String, HudWidgetBounds>();
		bounds.put("a", new HudWidgetBounds(10, 10, 50, 40));
		bounds.put("b", new HudWidgetBounds(40, 20, 80, 50));
		var collisions = manager.detect(bounds);

		assertEquals(1, collisions.size());
		assertEquals(new HudWidgetBounds(40, 20, 50, 40), collisions.get(0).intersection());
		HudPoint free = manager.nearestFree(bounds.get("a"), List.of(bounds.get("b")), 200, 120, 8);
		assertFalse(bounds.get("a").moveTo(free.x(), free.y()).intersects(bounds.get("b")));
	}

	@Test
	void smartSnapAlignsCentersAndCanBeDisabled() {
		HudSnapManager manager = new HudSnapManager();
		HudSnapManager.Result snapped = manager.snap(47, 12, 20, 20, 120, 80,
				List.of(), true, false, 6);
		assertEquals(50, snapped.x());
		assertEquals(60, snapped.verticalGuide());

		HudSnapManager.Result free = manager.snap(47, 12, 20, 20, 120, 80,
				List.of(), false, false, 6);
		assertEquals(47, free.x());
		assertNull(free.verticalGuide());
	}

	@Test
	void historySupportsUndoRedoWithoutRecordingRenderFrames() {
		HudHistoryManager history = new HudHistoryManager();
		history.record("before");
		assertTrue(history.canUndo());
		assertEquals("before", history.undo("after"));
		assertTrue(history.canRedo());
		assertEquals("after", history.redo("before"));
		assertNotNull(history.undo("after"));
	}
}
