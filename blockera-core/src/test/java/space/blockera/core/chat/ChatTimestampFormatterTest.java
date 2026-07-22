package space.blockera.core.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTimestampFormatterTest {
	private static final Instant FIXED_TIME = Instant.parse("2026-07-18T21:05:09Z");
	private static final ZoneId UTC = ZoneId.of("UTC");

	@Test
	void formatsAllTimestampModesInTwentyFourHourTime() {
		assertEquals("", ChatTimestampFormatter.format(FIXED_TIME, UTC, ChatTimestampMode.OFF, true));
		assertEquals("21:05", ChatTimestampFormatter.format(FIXED_TIME, UTC, ChatTimestampMode.HH_MM, true));
		assertEquals("21:05:09", ChatTimestampFormatter.format(FIXED_TIME, UTC, ChatTimestampMode.HH_MM_SS, true));
	}

	@Test
	void formatsTwelveHourPrefixWithoutDependingOnSystemZone() {
		ChatConfig config = ChatConfig.defaults();
		config.setTimestampMode(ChatTimestampMode.HH_MM_SS);
		config.setUse24Hour(false);

		assertEquals("[09:05:09 PM] ", ChatTimestampFormatter.prefix(FIXED_TIME, UTC, config));
	}
}
