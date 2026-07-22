package space.blockera.core.hud;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClockDisplayFormatterTest {
	@Test
	void formatsRealAndWorldTimeInTwentyFourHourMode() {
		ClockWidgetSettings settings = new ClockWidgetSettings();
		settings.showSeconds = true;

		assertEquals("18:42:07  ·  12:00:00",
				ClockDisplayFormatter.format(LocalTime.of(18, 42, 7), 6_000L, settings));
	}

	@Test
	void supportsTwelveHourModeAndSingleSource() {
		ClockWidgetSettings settings = new ClockWidgetSettings();
		settings.showWorldTime = false;
		settings.use24Hour = false;

		assertEquals("6:42 PM",
				ClockDisplayFormatter.format(LocalTime.of(18, 42, 7), 6_000L, settings));
	}

	@Test
	void validationKeepsAtLeastOneTimeSourceEnabled() {
		ClockWidgetSettings settings = new ClockWidgetSettings();
		settings.showRealTime = false;
		settings.showWorldTime = false;
		settings.validate();

		assertEquals(true, settings.showRealTime);
	}
}
