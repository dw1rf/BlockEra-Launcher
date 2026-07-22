package space.blockera.core.enhancement;

import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityHitboxPolicyTest {
	@Test
	void classifiesOnlyAllowListedEntityGroups() {
		assertEquals(EntityHitboxPolicy.Category.PLAYER, EntityHitboxPolicy.classifyClass(Player.class));
		assertEquals(EntityHitboxPolicy.Category.ANIMAL, EntityHitboxPolicy.classifyClass(Cow.class));
		assertEquals(EntityHitboxPolicy.Category.ITEM, EntityHitboxPolicy.classifyClass(ItemEntity.class));
		assertEquals(EntityHitboxPolicy.Category.NONE, EntityHitboxPolicy.classifyClass(Zombie.class));
	}

	@Test
	void groupSwitchesRemainIndependent() {
		EntityHitboxPolicy.Selection playersOnly = new EntityHitboxPolicy.Selection(true, false, false);
		assertTrue(playersOnly.enabled(EntityHitboxPolicy.Category.PLAYER));
		assertFalse(playersOnly.enabled(EntityHitboxPolicy.Category.ANIMAL));
		assertFalse(playersOnly.enabled(EntityHitboxPolicy.Category.ITEM));

		EntityHitboxPolicy.Selection itemsOnly = new EntityHitboxPolicy.Selection(false, false, true);
		assertFalse(itemsOnly.enabled(EntityHitboxPolicy.Category.PLAYER));
		assertFalse(itemsOnly.enabled(EntityHitboxPolicy.Category.ANIMAL));
		assertTrue(itemsOnly.enabled(EntityHitboxPolicy.Category.ITEM));
		assertFalse(itemsOnly.enabled(EntityHitboxPolicy.Category.NONE));
	}
}
