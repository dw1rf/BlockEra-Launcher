package space.blockera.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable values read only from the local Minecraft client for one render frame. */
public record HudDataSnapshot(
		String fps,
		String coordinates,
		String direction,
		String biome,
		String speed,
		String memory,
		LocalTime realTime,
		long worldTimeTicks,
		String ping,
		String playerCount,
		String durability,
		String armor,
		String health,
		String food,
		String saturation,
		String xp,
		String light,
		String targetBlock,
		String entities,
		String keystrokes,
		String cps,
		List<String> effects) {
	private static final String EMPTY = "—";

	public static HudDataSnapshot capture(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) return preview();

		String biomeName = player.level.getBiome(player.blockPosition()).unwrapKey()
				.map(key -> key.location()).map(ResourceLocation::getPath).orElse(EMPTY).replace('_', ' ');
		double horizontalSpeed = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z) * 20.0D;
		Runtime runtime = Runtime.getRuntime();
		long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
		long maxMiB = runtime.maxMemory() / 1_048_576L;
		long dayTicks = player.level.getDayTime();

		String ping = EMPTY;
		String online = EMPTY;
		if (!minecraft.isLocalServer() && minecraft.getConnection() != null) {
			PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
			if (info != null) ping = info.getLatency() + " ms";
			online = Integer.toString(minecraft.getConnection().getOnlinePlayers().size());
		}

		List<String> effects = new ArrayList<>();
		player.getActiveEffects().stream()
				.sorted(Comparator.comparing(instance -> instance.getEffect().getDescriptionId()))
				.map(HudDataSnapshot::effectName)
				.forEach(effects::add);
		if (effects.isEmpty()) effects.add(EMPTY);

		String fps = minecraft.fpsString == null || minecraft.fpsString.isBlank()
				? EMPTY : minecraft.fpsString.split(" ", 2)[0];
		var heldItem = player.getMainHandItem();
		String durability = heldItem.isEmpty() || !heldItem.isDamageableItem() ? EMPTY
				: (heldItem.getMaxDamage() - heldItem.getDamageValue()) + " / " + heldItem.getMaxDamage();
		String health = Math.round(player.getHealth()) + " / " + Math.round(player.getMaxHealth());
		String food = player.getFoodData().getFoodLevel() + " / 20";
		String saturation = String.format("%.1f", player.getFoodData().getSaturationLevel());
		String xp = player.experienceLevel + " lvl  " + Math.round(player.experienceProgress * 100.0F) + "%";
		String entities = Integer.toString(player.level.getEntities(player,
				new AABB(player.blockPosition()).inflate(16.0D), Entity::isAlive).size());
		return new HudDataSnapshot(
				fps,
				String.format("%.0f  %.0f  %.0f", player.getX(), player.getY(), player.getZ()),
				player.getDirection().getName().toUpperCase(),
				biomeName,
				String.format("%.1f b/s", horizontalSpeed),
				usedMiB + " / " + maxMiB + " MiB",
				LocalTime.now(),
				dayTicks,
				ping,
				online,
				durability,
				player.getArmorValue() + " / 20",
				health,
				food,
				saturation,
				xp,
				Integer.toString(player.level.getMaxLocalRawBrightness(player.blockPosition())),
				targetBlock(minecraft),
				entities,
				keystrokes(minecraft),
				CpsMeter.value(),
				List.copyOf(effects));
	}

	public static HudDataSnapshot preview() {
		return new HudDataSnapshot("144", "128  64  -256", "NORTH", "plains", "4.3 b/s",
				"1024 / 4096 MiB", LocalTime.of(18, 42, 7), 6_000L, "42 ms", "18", "156 / 250",
				"18 / 20", "20 / 20", "20 / 20", "5.0", "18 lvl  72%", "15", "stone", "7",
				"W A S D", "8 | 6", List.of("Speed II", "Haste"));
	}

	private static String targetBlock(Minecraft minecraft) {
		if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK
				|| minecraft.level == null) {
			return EMPTY;
		}
		BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
		return minecraft.level.getBlockState(hit.getBlockPos()).getBlock().getDescriptionId()
				.replace("block.minecraft.", "").replace('_', ' ');
	}

	private static String keystrokes(Minecraft minecraft) {
		long window = minecraft.getWindow().getWindow();
		StringBuilder result = new StringBuilder();
		appendKey(result, window, GLFW.GLFW_KEY_W, "W");
		appendKey(result, window, GLFW.GLFW_KEY_A, "A");
		appendKey(result, window, GLFW.GLFW_KEY_S, "S");
		appendKey(result, window, GLFW.GLFW_KEY_D, "D");
		appendKey(result, window, GLFW.GLFW_KEY_SPACE, "SPACE");
		appendKey(result, window, GLFW.GLFW_KEY_LEFT_SHIFT, "SHIFT");
		return result.isEmpty() ? EMPTY : result.toString();
	}

	private static void appendKey(StringBuilder result, long window, int key, String label) {
		if (GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) return;
		if (!result.isEmpty()) result.append(' ');
		result.append(label);
	}

	private static String effectName(MobEffectInstance instance) {
		String name = net.minecraft.network.chat.Component.translatable(instance.getDescriptionId()).getString();
		return instance.getAmplifier() > 0 ? name + " " + (instance.getAmplifier() + 1) : name;
	}
}
