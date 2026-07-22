package space.blockera.core.enhancement;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HitboxGeometryTest {
	@Test void convertsToCameraLocalCoordinatesExactlyOnce() {
		AABB currentBounds = new AABB(10, 64, 20, 11, 66, 21);
		AABB result = HitboxGeometry.cameraLocalBounds(currentBounds, new Vec3(10, 64, 20),
				new Vec3(8, 64, 18), 0.5F, new Vec3(5, 60, 10));
		assertEquals(4.0D, result.minX);
		assertEquals(4.0D, result.minY);
		assertEquals(9.0D, result.minZ);
		assertEquals(5.0D, result.maxX);
		assertEquals(6.0D, result.maxY);
		assertEquals(10.0D, result.maxZ);
	}
}
