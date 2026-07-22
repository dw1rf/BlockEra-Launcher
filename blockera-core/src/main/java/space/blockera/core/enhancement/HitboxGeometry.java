package space.blockera.core.enhancement;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure coordinate conversion used by the renderer and regression tests. */
public final class HitboxGeometry {
	private HitboxGeometry() { }

	public static AABB cameraLocalBounds(Entity entity, float partialTick, Vec3 camera) {
		return cameraLocalBounds(entity.getBoundingBox(), new Vec3(entity.getX(), entity.getY(), entity.getZ()),
				new Vec3(entity.xo, entity.yo, entity.zo), partialTick, camera).inflate(0.002D);
	}

	static AABB cameraLocalBounds(AABB worldBounds, Vec3 current, Vec3 previous, float partialTick, Vec3 camera) {
		double x = Mth.lerp((double) partialTick, previous.x, current.x);
		double y = Mth.lerp((double) partialTick, previous.y, current.y);
		double z = Mth.lerp((double) partialTick, previous.z, current.z);
		return worldBounds.move(-current.x, -current.y, -current.z).move(x - camera.x, y - camera.y, z - camera.z);
	}
}
