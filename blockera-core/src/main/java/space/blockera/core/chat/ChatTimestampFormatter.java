package space.blockera.core.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Deterministic formatter for the optional local timestamp prefix. */
public final class ChatTimestampFormatter {
	private ChatTimestampFormatter() {
	}

	public static String format(Instant timestamp, ZoneId zoneId, ChatTimestampMode mode, boolean use24Hour) {
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(zoneId, "zoneId");
		Objects.requireNonNull(mode, "mode");
		if (mode == ChatTimestampMode.OFF) return "";
		String pattern = switch (mode) {
			case HH_MM -> use24Hour ? "HH:mm" : "hh:mm a";
			case HH_MM_SS -> use24Hour ? "HH:mm:ss" : "hh:mm:ss a";
			case OFF -> throw new IllegalStateException("OFF timestamps were handled above");
		};
		return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(zoneId).format(timestamp);
	}

	public static String prefix(Instant timestamp, ZoneId zoneId, ChatConfig config) {
		Objects.requireNonNull(config, "config");
		String formatted = format(timestamp, zoneId, config.timestampMode(), config.use24Hour());
		return formatted.isEmpty() ? "" : "[" + formatted + "] ";
	}
}
