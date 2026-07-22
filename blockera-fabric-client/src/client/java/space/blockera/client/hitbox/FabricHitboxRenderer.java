package space.blockera.client.hitbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import space.blockera.client.BlockeraCoreServices;

/** Camera-local, depth-tested first-party hitbox renderer. */
public final class FabricHitboxRenderer {
    private static final double MAX_DISTANCE_SQUARED = 128.0D * 128.0D;
    private final HitboxConfigStore store;

    public FabricHitboxRenderer(HitboxConfigStore store) {
        this.store = store;
    }

    public void render(WorldRenderContext context) {
        if (!BlockeraCoreServices.visualsEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.options.hideGui) return;
        HitboxConfig config = store.config();
        if (!config.players().enabled() && !config.animals().enabled() && !config.items().enabled()) return;

		var gameRenderer = preferContextValue(context.gameRenderer(), minecraft.gameRenderer);
		if (gameRenderer == null) return;
		var mainCamera = gameRenderer.getMainCamera();
		if (mainCamera == null) return;
		Vec3 camera = mainCamera.position();
		float partialTick = mainCamera.getPartialTickTime();
		PoseStack poseStack = context.matrices();
		var consumers = context.consumers();
		if (!hasRenderResources(poseStack, consumers)) return;
		VertexConsumer lines = consumers.getBuffer(RenderTypes.linesTranslucent());
        PoseStack.Pose pose = poseStack.last();
        for (Entity entity : level.entitiesForRendering()) {
            HitboxCategory category = classify(entity);
            if (category == null || entity == minecraft.player || !entity.isAlive() || entity.isInvisible()) continue;
            HitboxStyle style = config.style(category);
            if (!style.enabled()) continue;
            Vec3 center = entity.getBoundingBox().getCenter();
            if (center.distanceToSqr(camera) > MAX_DISTANCE_SQUARED) continue;
            AABB bounds = cameraLocalBounds(entity, partialTick, camera).inflate(0.002D);
            renderBox(lines, pose, bounds, style, config.lineWidth());
        }
    }

	static AABB cameraLocalBounds(Entity entity, float partialTick, Vec3 camera) {
        double x = Mth.lerp((double) partialTick, entity.xo, entity.getX());
        double y = Mth.lerp((double) partialTick, entity.yo, entity.getY());
        double z = Mth.lerp((double) partialTick, entity.zo, entity.getZ());
        return entity.getBoundingBox()
            .move(-entity.getX(), -entity.getY(), -entity.getZ())
            .move(x - camera.x, y - camera.y, z - camera.z);
	}

	static <T> T preferContextValue(T contextValue, T minecraftFallback) {
		return contextValue != null ? contextValue : minecraftFallback;
	}

	static boolean hasRenderResources(Object matrices, Object consumers) {
		return matrices != null && consumers != null;
	}

    private static HitboxCategory classify(Entity entity) {
        if (entity instanceof AbstractClientPlayer) return HitboxCategory.PLAYER;
        if (entity instanceof Animal) return HitboxCategory.ANIMAL;
        if (entity instanceof ItemEntity) return HitboxCategory.ITEM;
        return null;
    }

    private static void renderBox(
        VertexConsumer lines,
        PoseStack.Pose pose,
        AABB box,
        HitboxStyle style,
        float lineWidth
    ) {
        double x0 = box.minX; double x1 = box.maxX;
        double y0 = box.minY; double y1 = box.maxY;
        double z0 = box.minZ; double z1 = box.maxZ;
        line(lines, pose, x0,y0,z0, x1,y0,z0, style, lineWidth);
        line(lines, pose, x0,y0,z1, x1,y0,z1, style, lineWidth);
        line(lines, pose, x0,y1,z0, x1,y1,z0, style, lineWidth);
        line(lines, pose, x0,y1,z1, x1,y1,z1, style, lineWidth);
        line(lines, pose, x0,y0,z0, x0,y1,z0, style, lineWidth);
        line(lines, pose, x1,y0,z0, x1,y1,z0, style, lineWidth);
        line(lines, pose, x0,y0,z1, x0,y1,z1, style, lineWidth);
        line(lines, pose, x1,y0,z1, x1,y1,z1, style, lineWidth);
        line(lines, pose, x0,y0,z0, x0,y0,z1, style, lineWidth);
        line(lines, pose, x1,y0,z0, x1,y0,z1, style, lineWidth);
        line(lines, pose, x0,y1,z0, x0,y1,z1, style, lineWidth);
        line(lines, pose, x1,y1,z0, x1,y1,z1, style, lineWidth);
    }

    private static void line(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double ax, double ay, double az,
        double bx, double by, double bz,
        HitboxStyle style,
        float lineWidth
    ) {
        float dx = (float) (bx - ax);
        float dy = (float) (by - ay);
        float dz = (float) (bz - az);
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        int rgb = style.rgb();
        int alpha = Math.round(style.opacity() * 255.0F);
        addVertex(consumer, pose, ax, ay, az, rgb, alpha, dx / length, dy / length, dz / length, lineWidth);
        addVertex(consumer, pose, bx, by, bz, rgb, alpha, dx / length, dy / length, dz / length, lineWidth);
    }

    private static void addVertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        double x, double y, double z,
        int rgb, int alpha,
        float nx, float ny, float nz,
        float lineWidth
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
            .setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
            .setNormal(pose, nx, ny, nz)
            .setLineWidth(lineWidth);
    }
}
