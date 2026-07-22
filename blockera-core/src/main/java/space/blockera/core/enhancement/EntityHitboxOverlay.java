package space.blockera.core.enhancement;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import space.blockera.core.config.ClientConfig;

/** Depth-tested local entity outlines. No external renderers or addon code are invoked. */
public final class EntityHitboxOverlay {
	private static final double MAX_DISTANCE_SQUARED = 128.0D * 128.0D;

	private EntityHitboxOverlay() {
	}

	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
		EntityHitboxPolicy.Selection selection = selection();
		if (!selection.anyEnabled()) return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) return;

		Vec3 cameraPosition = event.getCamera().getPosition();
		// The stage pose already contains the exact view rotation/bobbing used for entities.
		// Bounds below are camera-local, so no additional camera translation is applied.
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		HitboxAppearanceConfig appearance = HitboxConfigStore.instance().config();
		RenderSystem.lineWidth(appearance.lineWidth());
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		RenderSystem.enableDepthTest();
		poseStack.pushPose();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			EntityHitboxPolicy.Category category = EntityHitboxPolicy.classify(entity);
			if (!selection.enabled(category) || entity == minecraft.player || !entity.isAlive() || entity.isInvisible()) continue;
			if (entity.distanceToSqr(cameraPosition) > MAX_DISTANCE_SQUARED) continue;

			AABB worldBounds = entity.getBoundingBox();
			if (!event.getFrustum().isVisible(worldBounds)) continue;
			AABB bounds = HitboxGeometry.cameraLocalBounds(entity, event.getPartialTick(), cameraPosition);
			HitboxAppearanceConfig.CategoryStyle style = appearance.style(category);
			int rgb = style.rgb();
			LevelRenderer.renderLineBox(poseStack, lines, bounds,
					((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
					(rgb & 0xFF) / 255.0F, style.opacity());
			if (appearance.eyeDirection()) renderEyeDirection(poseStack, lines, entity,
					event.getPartialTick(), cameraPosition, appearance);
		}
		poseStack.popPose();
		buffers.endBatch(RenderType.lines());
		RenderSystem.lineWidth(1.0F);
		RenderSystem.enableDepthTest();
	}

	private static void renderEyeDirection(PoseStack poseStack, VertexConsumer lines, Entity entity,
			float partialTick, Vec3 camera, HitboxAppearanceConfig appearance) {
		Vec3 start = entity.getEyePosition(partialTick).subtract(camera);
		Vec3 end = start.add(entity.getViewVector(partialTick).scale(2.0D));
		Vec3 normal = end.subtract(start).normalize();
		int rgb = Integer.parseInt(appearance.eyeDirectionColor().substring(1), 16);
		var pose = poseStack.last();
		addLineVertex(lines, pose, start, rgb, normal);
		addLineVertex(lines, pose, end, rgb, normal);
	}

	private static void addLineVertex(VertexConsumer lines, PoseStack.Pose pose, Vec3 point, int rgb, Vec3 normal) {
		lines.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
				.color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255)
				.normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
	}

	static EntityHitboxPolicy.Selection selection() {
		return new EntityHitboxPolicy.Selection(ClientConfig.HITBOX_PLAYERS.get(),
				ClientConfig.HITBOX_ANIMALS.get(), ClientConfig.HITBOX_ITEMS.get());
	}

}
