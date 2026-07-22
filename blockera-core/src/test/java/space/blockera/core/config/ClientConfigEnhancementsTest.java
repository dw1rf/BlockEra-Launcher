package space.blockera.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigEnhancementsTest {
	@Test
	void enhancementKeysAreStableAndSafeByDefault() {
		assertEquals(List.of("client", "enhancements", "fullBright"), ClientConfig.FULL_BRIGHT.getPath());
		assertEquals(List.of("client", "enhancements", "hideTotemAnimation"), ClientConfig.HIDE_TOTEM_ANIMATION.getPath());
		assertEquals(List.of("client", "enhancements", "blockHighlight"), ClientConfig.BLOCK_HIGHLIGHT.getPath());
		assertEquals(List.of("client", "enhancements", "hitboxPlayers"), ClientConfig.HITBOX_PLAYERS.getPath());
		assertEquals(List.of("client", "enhancements", "hitboxAnimals"), ClientConfig.HITBOX_ANIMALS.getPath());
		assertEquals(List.of("client", "enhancements", "hitboxItems"), ClientConfig.HITBOX_ITEMS.getPath());

		assertFalse(ClientConfig.FULL_BRIGHT.getDefault());
		assertFalse(ClientConfig.HIDE_TOTEM_ANIMATION.getDefault());
		assertTrue(ClientConfig.BLOCK_HIGHLIGHT.getDefault());
		assertFalse(ClientConfig.HITBOX_PLAYERS.getDefault());
		assertFalse(ClientConfig.HITBOX_ANIMALS.getDefault());
		assertFalse(ClientConfig.HITBOX_ITEMS.getDefault());
	}
}
