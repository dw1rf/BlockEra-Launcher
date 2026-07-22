package space.blockera.core.enhancement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Copies camera rotation/normal matrices while explicitly discarding inherited world translation. */
final class HitboxRenderStack {
	private HitboxRenderStack() { }
	static PoseStack cameraRotationOnly(PoseStack source) {
		PoseStack clean = new PoseStack();
		Matrix4f pose = source.last().pose().copy();
		// Matrix4f#setTranslation also overwrites the diagonal in 1.19.2. Store/load
		// lets us clear only m03/m13/m23 while preserving the camera rotation.
		FloatBuffer values = ByteBuffer.allocateDirect(16 * Float.BYTES)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pose.store(values);
		values.put(12, 0.0F);
		values.put(13, 0.0F);
		values.put(14, 0.0F);
		pose.load(values);
		clean.last().pose().load(pose);
		clean.last().normal().load(source.last().normal());
		return clean;
	}
}
