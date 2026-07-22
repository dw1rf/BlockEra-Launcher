package space.blockera.core.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Collision checks are requested by the editor only after layout interaction. */
public final class HudCollisionManager {
	public record Collision(String firstId, String secondId, HudWidgetBounds intersection) { }

	public List<Collision> detect(Map<String, HudWidgetBounds> bounds) {
		List<Map.Entry<String, HudWidgetBounds>> entries = new ArrayList<>(bounds.entrySet());
		List<Collision> result = new ArrayList<>();
		for (int first = 0; first < entries.size(); first++) {
			for (int second = first + 1; second < entries.size(); second++) {
				HudWidgetBounds intersection = entries.get(first).getValue().intersection(entries.get(second).getValue());
				if (intersection != null) result.add(new Collision(entries.get(first).getKey(),
						entries.get(second).getKey(), intersection));
			}
		}
		return result;
	}

	public HudPoint nearestFree(HudWidgetBounds moving, List<HudWidgetBounds> occupied,
			int screenWidth, int screenHeight, int step) {
		if (free(moving, occupied)) return new HudPoint(moving.left(), moving.top());
		int limit = Math.max(screenWidth, screenHeight);
		for (int radius = step; radius <= limit; radius += step) {
			for (int dx = -radius; dx <= radius; dx += step) {
				HudPoint top = candidate(moving, dx, -radius, occupied, screenWidth, screenHeight);
				if (top != null) return top;
				HudPoint bottom = candidate(moving, dx, radius, occupied, screenWidth, screenHeight);
				if (bottom != null) return bottom;
			}
			for (int dy = -radius + step; dy < radius; dy += step) {
				HudPoint left = candidate(moving, -radius, dy, occupied, screenWidth, screenHeight);
				if (left != null) return left;
				HudPoint right = candidate(moving, radius, dy, occupied, screenWidth, screenHeight);
				if (right != null) return right;
			}
		}
		return new HudPoint(moving.left(), moving.top());
	}

	private static HudPoint candidate(HudWidgetBounds moving, int dx, int dy, List<HudWidgetBounds> occupied,
			int screenWidth, int screenHeight) {
		int x = Math.max(0, Math.min(screenWidth - moving.width(), moving.left() + dx));
		int y = Math.max(0, Math.min(screenHeight - moving.height(), moving.top() + dy));
		return free(moving.moveTo(x, y), occupied) ? new HudPoint(x, y) : null;
	}

	private static boolean free(HudWidgetBounds candidate, List<HudWidgetBounds> occupied) {
		for (HudWidgetBounds bounds : occupied) if (candidate.intersects(bounds)) return false;
		return true;
	}
}
