package space.blockera.core.hud;

import java.util.ArrayList;
import java.util.List;

/** Resolution-aware standard Minecraft HUD zones shown as editor guidance only. */
public final class HudSafeAreaManager {
	public record SafeArea(String id, HudWidgetBounds bounds) { }

	public List<SafeArea> calculate(int screenWidth, int screenHeight, HudLayoutStore store) {
		List<SafeArea> result = new ArrayList<>();
		for (VanillaHudElement element : VanillaHudElement.values()) {
			VanillaHudSettings settings = store.vanillaSettings(element.id());
			if (!settings.enabled) continue;
			HudPoint point = settings.anchor.resolve(screenWidth, screenHeight, element.width(), element.height(),
					settings.scale, settings.offsetX, settings.offsetY);
			result.add(new SafeArea(element.id(), new HudWidgetBounds(point.x(), point.y(),
					point.x() + Math.round(element.width() * settings.scale),
					point.y() + Math.round(element.height() * settings.scale))));
		}
		result.add(new SafeArea("player_list", new HudWidgetBounds(screenWidth / 2 - 160, 12, screenWidth / 2 + 160, 130)));
		result.add(new SafeArea("subtitles", new HudWidgetBounds(screenWidth - 220, screenHeight - 90, screenWidth - 8, screenHeight - 34)));
		result.add(new SafeArea("toasts", new HudWidgetBounds(screenWidth - 170, 4, screenWidth - 4, 70)));
		return result;
	}
}
