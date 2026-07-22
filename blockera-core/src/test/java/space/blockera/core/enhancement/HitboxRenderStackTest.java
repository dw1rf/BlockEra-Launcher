package space.blockera.core.enhancement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HitboxRenderStackTest {
	@Test void inheritedTranslationIsRemovedButCameraRotationIsPreserved() {
		PoseStack source = new PoseStack();
		source.translate(32.0D, -7.0D, 19.0D);
		source.mulPose(Vector3f.YP.rotationDegrees(37.0F));
		PoseStack clean = HitboxRenderStack.cameraRotationOnly(source);

		Vector4f origin = new Vector4f(0, 0, 0, 1); origin.transform(clean.last().pose());
		assertEquals(0.0F, origin.x(), 0.0001F);
		assertEquals(0.0F, origin.y(), 0.0001F);
		assertEquals(0.0F, origin.z(), 0.0001F);
		Vector4f expectedDirection = new Vector4f(1, 0, 0, 0); expectedDirection.transform(source.last().pose());
		Vector4f actualDirection = new Vector4f(1, 0, 0, 0); actualDirection.transform(clean.last().pose());
		assertEquals(expectedDirection.x(), actualDirection.x(), 0.0001F);
		assertEquals(expectedDirection.z(), actualDirection.z(), 0.0001F);
	}
}
