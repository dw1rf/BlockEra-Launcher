package space.blockera.core.enhancement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/** Strict first-party allow-list for the three supported entity hitbox groups. */
public final class EntityHitboxPolicy {
	public enum Category {
		PLAYER,
		ANIMAL,
		ITEM,
		NONE
	}

	public record Selection(boolean players, boolean animals, boolean items) {
		public boolean anyEnabled() {
			return players || animals || items;
		}

		public boolean enabled(Category category) {
			return switch (Objects.requireNonNull(category, "category")) {
				case PLAYER -> players;
				case ANIMAL -> animals;
				case ITEM -> items;
				case NONE -> false;
			};
		}
	}

	private EntityHitboxPolicy() {
	}

	public static Category classify(Entity entity) {
		return classifyClass(Objects.requireNonNull(entity, "entity").getClass());
	}

	public static Category classifyClass(Class<?> entityClass) {
		Objects.requireNonNull(entityClass, "entityClass");
		if (Player.class.isAssignableFrom(entityClass)) return Category.PLAYER;
		if (Animal.class.isAssignableFrom(entityClass)) return Category.ANIMAL;
		if (ItemEntity.class.isAssignableFrom(entityClass)) return Category.ITEM;
		return Category.NONE;
	}
}
