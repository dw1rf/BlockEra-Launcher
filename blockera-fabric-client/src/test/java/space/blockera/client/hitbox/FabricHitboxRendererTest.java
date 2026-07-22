package space.blockera.client.hitbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FabricHitboxRendererTest {
	@Test
	void fallsBackWhenFabricWorldContextOmitsGameRenderer() {
		assertEquals("minecraft-renderer",
			FabricHitboxRenderer.preferContextValue(null, "minecraft-renderer"));
		assertEquals("context-renderer",
			FabricHitboxRenderer.preferContextValue("context-renderer", "minecraft-renderer"));
	}

	@Test
	void rejectsIncompleteFabricRenderResourcesInsteadOfCrashing() {
		assertEquals(false, FabricHitboxRenderer.hasRenderResources(null, new Object()));
		assertEquals(false, FabricHitboxRenderer.hasRenderResources(new Object(), null));
		assertEquals(true, FabricHitboxRenderer.hasRenderResources(new Object(), new Object()));
	}
}
