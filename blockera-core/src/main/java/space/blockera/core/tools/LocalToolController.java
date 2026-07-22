package space.blockera.core.tools;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

/** Explicit-key local Marker, Measurement, Zoom and confirm-before-send AutoText. */
public final class LocalToolController {
	private static final String CATEGORY = "key.categories.blockera_core";
	private static final KeyMapping MARKER = key("key.blockera_core.marker", GLFW.GLFW_KEY_M);
	private static final KeyMapping MEASUREMENT = key("key.blockera_core.measurement", GLFW.GLFW_KEY_V);
	private static final KeyMapping ZOOM = key("key.blockera_core.zoom", GLFW.GLFW_KEY_C);
	private static final KeyMapping AUTO_TEXT = key("key.blockera_core.autotext", GLFW.GLFW_KEY_UNKNOWN);
	private static BlockPos measurementStart;
	private static String measurementDimension;
	private static double measurementDistance = -1.0D;
	private static long autoTextArmedUntil;
	private static long autoTextCooldownUntil;
	private LocalToolController() { }
	private static KeyMapping key(String name, int key) { return new KeyMapping(name, InputConstants.Type.KEYSYM, key, CATEGORY); }
	public static void register(RegisterKeyMappingsEvent event) { event.register(MARKER); event.register(MEASUREMENT); event.register(ZOOM); event.register(AUTO_TEXT); }
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.screen != null) return;
		while (MARKER.consumeClick()) marker(mc);
		while (MEASUREMENT.consumeClick()) measurement(mc);
		while (AUTO_TEXT.consumeClick()) autoText(mc);
	}
	public static void fov(ViewportEvent.ComputeFov event) { if (ZOOM.isDown()) event.setFOV(Math.min(event.getFOV(), 30.0D)); }
	public static String markerText() {
		Minecraft mc = Minecraft.getInstance(); if (mc.level == null) return "—";
		LocalToolConfig.Marker marker = LocalToolConfigStore.instance().config().marker(scope(mc));
		return marker == null ? "—" : marker.x() + " " + marker.y() + " " + marker.z();
	}
	public static String measurementText() { return measurementDistance < 0.0D ? "—" : String.format("%.2f m", measurementDistance); }
	private static void marker(Minecraft mc) {
		BlockPos pos = mc.player.blockPosition();
		LocalToolConfig.Marker marker = new LocalToolConfig.Marker(mc.level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
		LocalToolConfigStore.instance().config().putMarker(scope(mc), marker); LocalToolConfigStore.instance().save();
		message(mc, Component.translatable("blockera.tool.marker.saved", pos.getX(), pos.getY(), pos.getZ()));
	}
	private static void measurement(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) { message(mc, Component.translatable("blockera.tool.measurement.aim")); return; }
		String dimension = mc.level.dimension().location().toString(); BlockPos pos = hit.getBlockPos();
		if (measurementStart == null || !dimension.equals(measurementDimension)) {
			measurementStart = pos.immutable(); measurementDimension = dimension; measurementDistance = -1.0D;
			message(mc, Component.translatable("blockera.tool.measurement.start")); return;
		}
		measurementDistance = Math.sqrt(measurementStart.distSqr(pos)); measurementStart = null;
		message(mc, Component.translatable("blockera.tool.measurement.result", String.format("%.2f", measurementDistance)));
	}
	private static void autoText(Minecraft mc) {
		long now = System.currentTimeMillis(); if (now < autoTextCooldownUntil) return;
		String text = LocalToolConfigStore.instance().config().autoTextTemplate();
		if (text.isBlank()) { message(mc, Component.translatable("blockera.tool.autotext.empty")); return; }
		if (now > autoTextArmedUntil) { autoTextArmedUntil = now + 5_000L; message(mc, Component.translatable("blockera.tool.autotext.confirm")); return; }
		autoTextArmedUntil = 0L; autoTextCooldownUntil = now + 2_000L;
		if (text.startsWith("/")) mc.player.commandUnsigned(text.substring(1)); else mc.player.chatSigned(text, Component.literal(text));
	}
	private static String scope(Minecraft mc) {
		String server = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
		String dimension = mc.level == null ? "unknown" : mc.level.dimension().location().toString();
		return server + "|" + dimension;
	}
	private static void message(Minecraft mc, Component message) { mc.player.displayClientMessage(message, false); }
}
