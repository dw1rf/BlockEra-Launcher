package space.blockera.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import space.blockera.core.mixin.MultiPlayerGameModeAccessor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Additional local-only values used by the expanded first-party widget catalog. */
public final class ExtraHudData {
	private static final String EMPTY = "—";
	private static final long SESSION_STARTED = System.currentTimeMillis();
	private static long lastActivity = SESSION_STARTED;
	private static int lastTick = -1;
	private static double distance;
	private static double lastX;
	private static double lastZ;

	private ExtraHudData() { }

	public static String rotation() { LocalPlayer p = player(); return p == null ? EMPTY : Math.round(p.getYRot()) + "° / " + Math.round(p.getXRot()) + "°"; }
	public static String lookDirection() { LocalPlayer p = player(); return p == null ? EMPTY : p.getDirection().getName().toUpperCase(); }
	public static String compass() { LocalPlayer p = player(); return p == null ? EMPTY : compass(Math.round(p.getYRot())); }
	public static String serverAddress() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getCurrentServer() == null ? EMPTY : mc.getCurrentServer().ip;
	}
	public static String targetInfo() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.hitResult == null) return EMPTY;
		if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
			var state = mc.level.getBlockState(pos);
			return state.getBlock().getName().getString() + "  " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
		}
		if (mc.hitResult.getType() == HitResult.Type.ENTITY && mc.crosshairPickEntity != null) return mc.crosshairPickEntity.getName().getString();
		return EMPTY;
	}
	public static String dangerRadar() {
		LocalPlayer p = player(); if (p == null) return EMPTY;
		int count = 0; double nearest = Double.MAX_VALUE;
		for (Entity entity : p.level.getEntities(p, p.getBoundingBox().inflate(32.0D), e -> e instanceof Monster && e.isAlive())) {
			if (!p.hasLineOfSight(entity)) continue;
			count++; nearest = Math.min(nearest, Math.sqrt(p.distanceToSqr(entity)));
		}
		return count == 0 ? "0" : count + "  " + Math.round(nearest) + "m";
	}
	public static String sessionDistance() { updateMovement(); return String.format("%.1f m", distance); }
	public static String blockBreak() {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.options.keyAttack.isDown() || mc.gameMode == null || mc.hitResult == null
				|| mc.hitResult.getType() != HitResult.Type.BLOCK) return EMPTY;
		float progress = ((MultiPlayerGameModeAccessor) mc.gameMode).blockera$getDestroyProgress();
		return Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * 100.0F) + "%";
	}
	public static String combo() { return ComboTracker.value(); }
	public static String arrows() { return Integer.toString(count(Items.ARROW) + count(Items.SPECTRAL_ARROW) + count(Items.TIPPED_ARROW)); }
	public static String mainHand() { LocalPlayer p = player(); return p == null ? EMPTY : stack(p.getMainHandItem()); }
	public static String offHand() { LocalPlayer p = player(); return p == null ? EMPTY : stack(p.getOffhandItem()); }
	public static String helmet() { return armor(3); }
	public static String chestplate() { return armor(2); }
	public static String leggings() { return armor(1); }
	public static String boots() { return armor(0); }
	public static String inventoryTracker() {
		LocalPlayer p = player(); if (p == null) return EMPTY;
		int occupied = 0; for (ItemStack stack : p.getInventory().items) if (!stack.isEmpty()) occupied++;
		return occupied + " / " + p.getInventory().items.size();
	}
	public static String afkTimer() { updateMovement(); return formatDuration(System.currentTimeMillis() - lastActivity); }
	public static String date() { return LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
	public static String realTime() { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")); }
	public static String worldTime() {
		LocalPlayer p = player(); if (p == null) return EMPTY;
		long ticks = p.level.getDayTime() % 24_000L;
		long minutes = (ticks + 6_000L) * 60L / 1_000L % (24L * 60L);
		return String.format("%02d:%02d", minutes / 60L, minutes % 60L);
	}
	public static String sessionTime() { return formatDuration(System.currentTimeMillis() - SESSION_STARTED); }
	public static String cpuUsage() { return SystemMetricsSampler.snapshot().cpu(); }
	public static String systemMemory() { return SystemMetricsSampler.snapshot().memory(); }
	public static String battery() { return SystemMetricsSampler.snapshot().battery(); }
	public static String cpuTemperature() { return SystemMetricsSampler.snapshot().temperature(); }

	private static void updateMovement() {
		LocalPlayer p = player(); if (p == null || p.tickCount == lastTick) return;
		if (lastTick >= 0) {
			double step = Math.hypot(p.getX() - lastX, p.getZ() - lastZ);
			if (step > 0.001D && step < 16.0D) { distance += step; lastActivity = System.currentTimeMillis(); }
		}
		if (Minecraft.getInstance().options.keyAttack.isDown() || Minecraft.getInstance().options.keyUse.isDown()
				|| Minecraft.getInstance().options.keyJump.isDown()) lastActivity = System.currentTimeMillis();
		lastX = p.getX(); lastZ = p.getZ(); lastTick = p.tickCount;
	}
	private static int count(net.minecraft.world.item.Item item) { LocalPlayer p = player(); if (p == null) return 0; int total = 0; for (ItemStack s : p.getInventory().items) if (s.is(item)) total += s.getCount(); return total; }
	private static String armor(int index) { LocalPlayer p = player(); return p == null ? EMPTY : stack(p.getInventory().armor.get(index)); }
	private static String stack(ItemStack stack) { return stack.isEmpty() ? EMPTY : stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""); }
	private static String compass(int yaw) {
		String[] values = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		int index = Math.floorMod(Math.round(yaw / 45.0F), 8); return values[index] + "  " + Math.floorMod(yaw, 360) + "°";
	}
	private static String formatDuration(long millis) { Duration d = Duration.ofMillis(Math.max(0, millis)); return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart()); }
	private static LocalPlayer player() { return Minecraft.getInstance().player; }
}
