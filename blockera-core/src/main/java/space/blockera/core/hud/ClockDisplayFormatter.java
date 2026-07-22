package space.blockera.core.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure formatter shared by the runtime clock widget and unit tests. */
public final class ClockDisplayFormatter {
	private ClockDisplayFormatter() {
	}

	public static String format(LocalTime realTime, long worldTimeTicks, ClockWidgetSettings settings) {
		settings.validate();
		List<String> values = new ArrayList<>(2);
		if (settings.showRealTime) values.add(formatTime(realTime, settings));
		if (settings.showWorldTime) values.add(formatTime(worldTime(worldTimeTicks), settings));
		return String.join("  ·  ", values);
	}

	private static String formatTime(LocalTime value, ClockWidgetSettings settings) {
		String pattern;
		if (settings.use24Hour) pattern = settings.showSeconds ? "HH:mm:ss" : "HH:mm";
		else pattern = settings.showSeconds ? "h:mm:ss a" : "h:mm a";
		return value.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
	}

	private static LocalTime worldTime(long ticks) {
		long normalized = Math.floorMod(ticks, 24_000L);
		long seconds = Math.floorMod((normalized + 6_000L) * 86_400L / 24_000L, 86_400L);
		return LocalTime.ofSecondOfDay(seconds);
	}
}
