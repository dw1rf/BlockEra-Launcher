package space.blockera.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.BlockeraCoreServices;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;

/** Public Fabric HUD runtime. AI overlays are supplied by the private optional add-on. */
public final class BlockeraHudManager {
    private final Minecraft minecraft;
    private final HudLayoutStore layouts;
    private final HudWidgetRegistry registry = new HudWidgetRegistry();
	private final long sessionStartedAt = System.currentTimeMillis();
	private final SystemMetricsSampler systemMetrics = new SystemMetricsSampler();
	private final Deque<Long> attackClicks = new ArrayDeque<>();
	private final Deque<Long> useClicks = new ArrayDeque<>();
	private boolean attackWasDown;
	private boolean useWasDown;
	private int combo;
	private long lastAttackAt;
	private double travelledDistance;
	private double lastX = Double.NaN;
	private double lastY = Double.NaN;
	private double lastZ = Double.NaN;
	private long lastActivityAt = System.currentTimeMillis();

    public BlockeraHudManager(
        Minecraft minecraft,
        HudLayoutStore layouts
    ) {
        this.minecraft = minecraft;
        this.layouts = layouts;
        registry.register(textWidget("blockera:fps", Component.translatable("blockera.widget.fps"),
            () -> Integer.toString(minecraft.getFps())));
        registry.register(textWidget("blockera:coordinates", Component.translatable("blockera.widget.coordinates"),
            this::coordinates));
        registry.register(textWidget("blockera:direction", Component.translatable("blockera.widget.direction"),
            this::direction));
        registry.register(textWidget("blockera:speed", Component.translatable("blockera.widget.speed"), this::speed));
        registry.register(textWidget("blockera:memory", Component.translatable("blockera.widget.memory"), this::memory));
		registry.register(textWidget("blockera:clock", Component.translatable("blockera.widget.clock"),
			() -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
		registry.register(textWidget("blockera:biome", Component.literal("Biome"), this::biome));
		registry.register(textWidget("blockera:ping", Component.literal("Ping"), this::ping));
		registry.register(textWidget("blockera:player_count", Component.literal("Players"), this::playerCount));
		registry.register(textWidget("blockera:effects", Component.literal("Effects"), this::effects));
		registry.register(textWidget("blockera:durability", Component.literal("Durability"), this::durability));
		registry.register(textWidget("blockera:armor", Component.literal("Armor"), this::armor));
		registry.register(textWidget("blockera:health", Component.literal("Health"), this::health));
		registry.register(textWidget("blockera:food", Component.literal("Food"), this::food));
		registry.register(textWidget("blockera:xp", Component.literal("Experience"), this::experience));
		registry.register(textWidget("blockera:light", Component.literal("Light"), this::light));
		registry.register(textWidget("blockera:target_block", Component.literal("Target block"), this::targetBlock));
		registry.register(textWidget("blockera:rotation", Component.literal("Yaw / Pitch"), this::rotation));
		registry.register(textWidget("blockera:server_address", Component.literal("Server"), this::serverAddress));
		registry.register(textWidget("blockera:date", Component.literal("Date"),
			() -> LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
		registry.register(textWidget("blockera:real_time", Component.literal("Real time"),
			() -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
		registry.register(textWidget("blockera:world_time", Component.literal("World time"), this::worldTime));
		registry.register(textWidget("blockera:session_time", Component.literal("Session"), this::sessionTime));
		registry.register(textWidget("blockera:saturation", Component.literal("Saturation"), this::saturation));
		registry.register(textWidget("blockera:entities", Component.literal("Entities"), this::entities));
		registry.register(textWidget("blockera:keystrokes", Component.literal("Keys"), this::keystrokes));
		registry.register(textWidget("blockera:cps", Component.literal("CPS"), this::cps));
		registry.register(textWidget("blockera:look_direction", Component.literal("Look"), this::lookDirection));
		registry.register(textWidget("blockera:compass", Component.literal("Compass"), this::compass));
		registry.register(textWidget("blockera:target_info", Component.literal("Target"), this::targetInfo));
		registry.register(textWidget("blockera:danger_radar", Component.literal("Danger"), this::dangerRadar));
		registry.register(textWidget("blockera:session_distance", Component.literal("Distance"), this::sessionDistance));
		registry.register(textWidget("blockera:block_break", Component.literal("Block break"), this::blockBreak));
		registry.register(textWidget("blockera:combo", Component.literal("Combo"), this::combo));
		registry.register(textWidget("blockera:arrows", Component.literal("Arrows"), this::arrows));
		registry.register(textWidget("blockera:main_hand", Component.literal("Main hand"), this::mainHand));
		registry.register(textWidget("blockera:off_hand", Component.literal("Off hand"), this::offHand));
		registry.register(textWidget("blockera:helmet", Component.literal("Helmet"), () -> equipment(EquipmentSlot.HEAD)));
		registry.register(textWidget("blockera:chestplate", Component.literal("Chestplate"), () -> equipment(EquipmentSlot.CHEST)));
		registry.register(textWidget("blockera:leggings", Component.literal("Leggings"), () -> equipment(EquipmentSlot.LEGS)));
		registry.register(textWidget("blockera:boots", Component.literal("Boots"), () -> equipment(EquipmentSlot.FEET)));
		registry.register(textWidget("blockera:inventory_tracker", Component.literal("Inventory"), this::inventory));
		registry.register(playerModelWidget());
		registry.register(textWidget("blockera:afk_timer", Component.literal("AFK"), this::afkTime));
		registry.register(textWidget("blockera:cpu_usage", Component.literal("CPU"), this::cpuUsage));
		registry.register(textWidget("blockera:system_memory", Component.literal("System RAM"), this::systemMemory));
		registry.register(textWidget("blockera:battery", Component.literal("Battery"), () -> "—"));
		registry.register(textWidget("blockera:cpu_temperature", Component.literal("CPU temp"), () -> "—"));
		registry.register(textWidget("blockera:marker", Component.literal("Marker"), () -> "—"));
		registry.register(textWidget("blockera:measurement", Component.literal("Measurement"), () -> "—"));
    }

	public void render(GuiGraphics graphics) {
        if (!BlockeraCoreServices.visualsEnabled() || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
		updateLocalState();
		for (ClientHudWidget widget : registry.widgets()) {
            HudWidgetSettings settings = layouts.settings(widget.id());
            if (!settings.enabled) continue;
            int scaledWidth = Math.round(widget.width() * settings.scale);
            int scaledHeight = Math.round(widget.height() * settings.scale);
            HudPoint point = settings.anchor.resolve(
                graphics.guiWidth(), graphics.guiHeight(), scaledWidth, scaledHeight,
                settings.offsetX, settings.offsetY
            );
            graphics.pose().pushMatrix();
            graphics.pose().translate(point.x(), point.y());
            graphics.pose().scale(settings.scale, settings.scale);
            widget.render(graphics, 0, 0, settings);
            graphics.pose().popMatrix();
        }
    }

    private ClientHudWidget textWidget(String id, Component label, Supplier<String> value) {
		Component localizedLabel = Component.translatable(BuiltinHudCatalog.require(id).translationKey());
        return new ClientHudWidget() {
            @Override public String id() { return id; }
            @Override public int width() { return 112; }
            @Override public int height() { return 34; }
			@Override public void render(GuiGraphics graphics, int x, int y, HudWidgetSettings settings) {
				if (settings.background) {
					BlockeraDraw.roundedRect(graphics, x, y, x + width(), y + height(), ThemeTokens.RADIUS,
						alpha(ThemeTokens.PANEL, settings.opacity));
                    graphics.fill(x, y, x + 2, y + height(), alpha(ThemeTokens.ACCENT, settings.opacity));
                }
				UiText.drawSemibold(graphics, localizedLabel, x + 8, y + 6,
                    alpha(ThemeTokens.MUTED, settings.opacity));
                UiText.draw(graphics, Component.literal(value.get()), x + 8, y + 19,
                    alpha(ThemeTokens.TEXT, settings.opacity));
            }
        };
    }


    private String coordinates() {
        if (minecraft.player == null) return "-";
        return minecraft.player.getBlockX() + " " + minecraft.player.getBlockY() + " " + minecraft.player.getBlockZ();
    }

    private String direction() {
        return minecraft.player == null ? "-" : minecraft.player.getDirection().getName().toUpperCase(Locale.ROOT);
    }

    private String speed() {
        if (minecraft.player == null) return "-";
        return String.format(Locale.ROOT, "%.1f b/s", minecraft.player.getDeltaMovement().horizontalDistance() * 20.0D);
    }

    private String memory() {
        Runtime jvm = Runtime.getRuntime();
        long used = (jvm.totalMemory() - jvm.freeMemory()) / 1_048_576L;
        long maximum = jvm.maxMemory() / 1_048_576L;
        return used + " / " + maximum + " MB";
    }

	private String biome() {
		if (minecraft.player == null) return "—";
		return minecraft.player.level().getBiome(minecraft.player.blockPosition()).unwrapKey()
			.map(Object::toString).map(value -> value.substring(value.lastIndexOf('/') + 1)).orElse("—");
	}

	private String ping() {
		if (minecraft.player == null || minecraft.getConnection() == null) return "—";
		var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
		return info == null ? "—" : info.getLatency() + " ms";
	}

	private String playerCount() {
		return minecraft.getConnection() == null ? "—"
			: Integer.toString(minecraft.getConnection().getOnlinePlayers().size());
	}

	private String effects() {
		return minecraft.player == null ? "—" : Integer.toString(minecraft.player.getActiveEffects().size());
	}

	private String durability() {
		if (minecraft.player == null) return "—";
		var stack = minecraft.player.getMainHandItem();
		return stack.isDamageableItem() ? (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage() : "—";
	}

	private String armor() {
		return minecraft.player == null ? "—" : minecraft.player.getArmorValue() + " / 20";
	}

	private String health() {
		return minecraft.player == null ? "—" : String.format(Locale.ROOT, "%.1f / %.1f",
			minecraft.player.getHealth(), minecraft.player.getMaxHealth());
	}

	private String food() {
		return minecraft.player == null ? "—" : minecraft.player.getFoodData().getFoodLevel() + " / 20";
	}

	private String experience() {
		return minecraft.player == null ? "—" : minecraft.player.experienceLevel + " · "
			+ Math.round(minecraft.player.experienceProgress * 100.0F) + "%";
	}

	private String light() {
		return minecraft.player == null ? "—" : Integer.toString(
			minecraft.player.level().getMaxLocalRawBrightness(minecraft.player.blockPosition()));
	}

	private String targetBlock() {
		return minecraft.hitResult instanceof BlockHitResult hit ? hit.getBlockPos().toShortString() : "—";
	}

	private String rotation() {
		return minecraft.player == null ? "—" : String.format(Locale.ROOT, "%.1f / %.1f",
			minecraft.player.getYRot(), minecraft.player.getXRot());
	}

	private String serverAddress() {
		if (minecraft.isSingleplayer()) return "singleplayer";
		var server = minecraft.getCurrentServer();
		return server == null ? "—" : server.ip;
	}

	private String worldTime() {
		if (minecraft.level == null) return "—";
		long ticks = minecraft.level.getDayTime() % 24_000L;
		long minutes = (ticks + 6_000L) * 60L / 1_000L % (24L * 60L);
		return String.format(Locale.ROOT, "%02d:%02d", minutes / 60L, minutes % 60L);
	}

	private String sessionTime() {
		long seconds = (System.currentTimeMillis() - sessionStartedAt) / 1_000L;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L);
	}

	private void updateLocalState() {
		if (minecraft.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean attackDown = minecraft.options.keyAttack.isDown();
		if (attackDown && !attackWasDown) {
			attackClicks.addLast(now);
			lastActivityAt = now;
			if (minecraft.hitResult instanceof EntityHitResult) {
				combo = now - lastAttackAt > 2_000L ? 1 : combo + 1;
				lastAttackAt = now;
			}
		}
		attackWasDown = attackDown;
		boolean useDown = minecraft.options.keyUse.isDown();
		if (useDown && !useWasDown) {
			useClicks.addLast(now);
			lastActivityAt = now;
		}
		useWasDown = useDown;
		while (!attackClicks.isEmpty() && now - attackClicks.peekFirst() > 1_000L) {
			attackClicks.removeFirst();
		}
		while (!useClicks.isEmpty() && now - useClicks.peekFirst() > 1_000L) {
			useClicks.removeFirst();
		}
		if (now - lastAttackAt > 2_000L) {
			combo = 0;
		}
		double x = minecraft.player.getX();
		double y = minecraft.player.getY();
		double z = minecraft.player.getZ();
		if (Double.isFinite(lastX)) {
			double step = Math.sqrt(square(x - lastX) + square(y - lastY) + square(z - lastZ));
			if (step < 32.0D) {
				travelledDistance += step;
			}
			if (step > 0.01D || movementKeyDown()) {
				lastActivityAt = now;
			}
		}
		lastX = x;
		lastY = y;
		lastZ = z;
	}

	private String saturation() {
		return minecraft.player == null ? "—" : String.format(Locale.ROOT, "%.1f / 20", minecraft.player.getFoodData().getSaturationLevel());
	}

	private String entities() {
		if (minecraft.level == null) return "—";
		int count = 0;
		for (var ignored : minecraft.level.entitiesForRendering()) count++;
		return Integer.toString(count);
	}

	private String keystrokes() {
		return key(minecraft.options.keyUp, "W") + key(minecraft.options.keyLeft, "A")
			+ key(minecraft.options.keyDown, "S") + key(minecraft.options.keyRight, "D");
	}

	private String cps() {
		return "L " + attackClicks.size() + " / R " + useClicks.size();
	}

	private String lookDirection() {
		if (minecraft.player == null) return "—";
		var look = minecraft.player.getLookAngle();
		return String.format(Locale.ROOT, "%.2f %.2f %.2f", look.x, look.y, look.z);
	}

	private String compass() {
		if (minecraft.player == null) return "—";
		float yaw = (minecraft.player.getYRot() % 360.0F + 360.0F) % 360.0F;
		return minecraft.player.getDirection().getName().toUpperCase(Locale.ROOT) + " · " + Math.round(yaw) + "°";
	}

	private String targetInfo() {
		if (minecraft.hitResult instanceof EntityHitResult hit && minecraft.player != null) {
			return hit.getEntity().getDisplayName().getString() + " · "
				+ String.format(Locale.ROOT, "%.1f m", minecraft.player.distanceTo(hit.getEntity()));
		}
		if (minecraft.hitResult instanceof BlockHitResult block && minecraft.level != null) {
			return minecraft.level.getBlockState(block.getBlockPos()).getBlock().getName().getString();
		}
		return "—";
	}

	private String dangerRadar() {
		if (minecraft.level == null || minecraft.player == null) return "—";
		int visible = 0;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof Monster monster && monster.isAlive()
				&& monster.distanceToSqr(minecraft.player) <= 128.0D * 128.0D
				&& minecraft.player.hasLineOfSight(monster)) visible++;
		}
		return Integer.toString(visible);
	}

	private String sessionDistance() {
		return String.format(Locale.ROOT, "%.1f m", travelledDistance);
	}

	private String blockBreak() {
		if (minecraft.gameMode == null || !minecraft.gameMode.isDestroying()) return "—";
		return Math.min(100, (minecraft.gameMode.getDestroyStage() + 1) * 10) + "%";
	}

	private String combo() {
		return Integer.toString(combo);
	}

	private String arrows() {
		if (minecraft.player == null) return "—";
		int count = 0;
		for (var stack : minecraft.player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) count += stack.getCount();
		}
		return Integer.toString(count);
	}

	private String mainHand() {
		return minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()
			? "—" : minecraft.player.getMainHandItem().getHoverName().getString();
	}

	private String offHand() {
		return minecraft.player == null || minecraft.player.getOffhandItem().isEmpty()
			? "—" : minecraft.player.getOffhandItem().getHoverName().getString();
	}

	private String equipment(EquipmentSlot slot) {
		if (minecraft.player == null || minecraft.player.getItemBySlot(slot).isEmpty()) return "—";
		return minecraft.player.getItemBySlot(slot).getHoverName().getString();
	}

	private String inventory() {
		if (minecraft.player == null) return "—";
		int occupied = 0;
		for (var stack : minecraft.player.getInventory().getNonEquipmentItems()) if (!stack.isEmpty()) occupied++;
		return occupied + " / " + minecraft.player.getInventory().getNonEquipmentItems().size();
	}

	private String afkTime() {
		long seconds = Math.max(0L, System.currentTimeMillis() - lastActivityAt) / 1_000L;
		return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
	}

	private String cpuUsage() {
		return systemMetrics.cpu();
	}

	private String systemMemory() {
		return systemMetrics.memory();
	}

	private ClientHudWidget playerModelWidget() {
		return new ClientHudWidget() {
			@Override public String id() { return "blockera:player_model"; }
			@Override public int width() { return 72; }
			@Override public int height() { return 118; }
			@Override public void render(GuiGraphics graphics, int x, int y, HudWidgetSettings settings) {
				if (settings.background) BlockeraDraw.roundedRect(graphics, x, y, x + width(), y + height(),
					ThemeTokens.RADIUS, alpha(ThemeTokens.PANEL, settings.opacity));
				if (minecraft.player != null) {
					InventoryScreen.renderEntityInInventoryFollowsMouse(
						graphics, x, y, x + width(), y + height(), 42, 0.0F,
						x + width() / 2.0F, y + height() / 2.0F, minecraft.player
					);
				}
			}
		};
	}

	private boolean movementKeyDown() {
		return minecraft.options.keyUp.isDown() || minecraft.options.keyDown.isDown()
			|| minecraft.options.keyLeft.isDown() || minecraft.options.keyRight.isDown()
			|| minecraft.options.keyJump.isDown() || minecraft.options.keyShift.isDown();
	}

	private static String key(net.minecraft.client.KeyMapping mapping, String label) {
		return mapping.isDown() ? "[" + label + "]" : " " + label + " ";
	}

	private static double square(double value) {
		return value * value;
	}

    private static int alpha(int argb, float opacity) {
        int sourceAlpha = (argb >>> 24) & 0xFF;
        int resultAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * opacity)));
        return (resultAlpha << 24) | (argb & 0x00FFFFFF);
    }

}
