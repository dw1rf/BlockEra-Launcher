package space.blockera.core.hud;

import java.util.ArrayList;
import java.util.List;

/** Non-aggressive edge/center/grid snapping with guide coordinates for the editor. */
public final class HudSnapManager {
	public record Result(int x, int y, Integer verticalGuide, Integer horizontalGuide) { }

	public Result snap(int x, int y, int width, int height, int screenWidth, int screenHeight,
			List<HudWidgetBounds> targets, boolean smartSnap, boolean gridSnap, int threshold) {
		int resultX = gridSnap ? Math.round(x / 8.0F) * 8 : x;
		int resultY = gridSnap ? Math.round(y / 8.0F) * 8 : y;
		if (!smartSnap) return new Result(resultX, resultY, null, null);

		List<Integer> vertical = new ArrayList<>(List.of(0, screenWidth / 2, screenWidth));
		List<Integer> horizontal = new ArrayList<>(List.of(0, screenHeight / 2, screenHeight));
		for (HudWidgetBounds target : targets) {
			vertical.add(target.left()); vertical.add(target.centerX()); vertical.add(target.right());
			horizontal.add(target.top()); horizontal.add(target.centerY()); horizontal.add(target.bottom());
		}
		Axis xAxis = closest(resultX, width, vertical, threshold);
		Axis yAxis = closest(resultY, height, horizontal, threshold);
		return new Result(xAxis.position, yAxis.position, xAxis.guide, yAxis.guide);
	}

	private static Axis closest(int position, int size, List<Integer> guides, int threshold) {
		int[] points = {position, position + size / 2, position + size};
		int bestDistance = threshold + 1;
		int bestPosition = position;
		Integer bestGuide = null;
		for (int pointIndex = 0; pointIndex < points.length; pointIndex++) {
			for (int guide : guides) {
				int distance = Math.abs(points[pointIndex] - guide);
				if (distance < bestDistance) {
					bestDistance = distance;
					bestPosition = position + guide - points[pointIndex];
					bestGuide = guide;
				}
			}
		}
		return new Axis(bestPosition, bestGuide);
	}

	private record Axis(int position, Integer guide) { }
}
