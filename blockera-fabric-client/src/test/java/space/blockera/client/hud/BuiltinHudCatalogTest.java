package space.blockera.client.hud;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BuiltinHudCatalogTest {
	@Test
	void exposesExactlyTheFiftyReviewedForgeWidgets() {
		assertEquals(50, BuiltinHudCatalog.widgets().size());
		assertEquals(50, BuiltinHudCatalog.widgets().stream().map(HudWidgetMetadata::id).distinct().count());
		assertEquals("blockera:fps", BuiltinHudCatalog.widgets().getFirst().id());
		assertEquals("blockera:measurement", BuiltinHudCatalog.widgets().getLast().id());
		assertFalse(BuiltinHudCatalog.isAllowed("blockera:balance"));
		assertFalse(BuiltinHudCatalog.isAllowed("other:fps"));
	}

	@Test
	void metadataProvidesAllForgeCategoriesAndRejectsUnknownOptions() {
		Set<HudCategory> categories = BuiltinHudCatalog.widgets().stream()
			.map(HudWidgetMetadata::category)
			.collect(Collectors.toSet());
		assertEquals(Set.of(HudCategory.PERFORMANCE, HudCategory.WORLD, HudCategory.PLAYER, HudCategory.SERVER), categories);
		assertThrows(IllegalArgumentException.class,
			() -> BuiltinHudCatalog.requireAllowedOption("blockera:fps", "unreviewed"));
	}
}
