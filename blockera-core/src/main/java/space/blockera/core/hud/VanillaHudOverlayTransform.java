package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.blockera.core.BlockeraCore;

import java.util.HashSet;
import java.util.Set;

/** Moves only the six explicitly supported vanilla overlay groups while preserving their native renderers. */
@Mod.EventBusSubscriber(modid = BlockeraCore.MOD_ID, value = Dist.CLIENT)
public final class VanillaHudOverlayTransform {
	private static final Set<ResourceLocation> TRANSFORMED = new HashSet<>();

	private VanillaHudOverlayTransform() {
	}

	@SubscribeEvent
	public static void beforeOverlay(RenderGuiOverlayEvent.Pre event) {
		VanillaHudElement element = elementFor(event.getOverlay().id());
		if (element == null || Minecraft.getInstance().options.hideGui) return;
		VanillaHudSettings settings = HudLayoutStore.instance().vanillaSettings(element.id());
		if (!settings.enabled) {
			event.setCanceled(true);
			return;
		}
		apply(event.getPoseStack(), element, settings,
				event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
		TRANSFORMED.add(event.getOverlay().id());
	}

	@SubscribeEvent
	public static void afterOverlay(RenderGuiOverlayEvent.Post event) {
		if (TRANSFORMED.remove(event.getOverlay().id())) event.getPoseStack().popPose();
	}

	public static void apply(PoseStack poseStack, VanillaHudElement element, VanillaHudSettings settings,
			int screenWidth, int screenHeight) {
		HudPoint source = element.defaultPosition(screenWidth, screenHeight);
		HudPoint target = settings.anchor.resolve(screenWidth, screenHeight, element.width(), element.height(),
				settings.scale, settings.offsetX, settings.offsetY);
		poseStack.pushPose();
		poseStack.translate(target.x(), target.y(), 0.0D);
		poseStack.scale(settings.scale, settings.scale, 1.0F);
		poseStack.translate(-source.x(), -source.y(), 0.0D);
	}

	public static double inverseX(VanillaHudElement element, VanillaHudSettings settings,
			int screenWidth, int screenHeight, double mouseX) {
		HudPoint source = element.defaultPosition(screenWidth, screenHeight);
		HudPoint target = settings.anchor.resolve(screenWidth, screenHeight, element.width(), element.height(),
				settings.scale, settings.offsetX, settings.offsetY);
		return source.x() + (mouseX - target.x()) / settings.scale;
	}

	public static double inverseY(VanillaHudElement element, VanillaHudSettings settings,
			int screenWidth, int screenHeight, double mouseY) {
		HudPoint source = element.defaultPosition(screenWidth, screenHeight);
		HudPoint target = settings.anchor.resolve(screenWidth, screenHeight, element.width(), element.height(),
				settings.scale, settings.offsetX, settings.offsetY);
		return source.y() + (mouseY - target.y()) / settings.scale;
	}

	private static VanillaHudElement elementFor(ResourceLocation id) {
		if (matches(id, VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.PLAYER_HEALTH, VanillaGuiOverlay.ARMOR_LEVEL,
				VanillaGuiOverlay.FOOD_LEVEL, VanillaGuiOverlay.MOUNT_HEALTH, VanillaGuiOverlay.AIR_LEVEL,
				VanillaGuiOverlay.JUMP_BAR, VanillaGuiOverlay.EXPERIENCE_BAR, VanillaGuiOverlay.ITEM_NAME)) {
			return VanillaHudElement.HOTBAR;
		}
		if (id.equals(VanillaGuiOverlay.CHAT_PANEL.id())) return VanillaHudElement.CHAT;
		if (id.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) return VanillaHudElement.BOSSBAR;
		if (id.equals(VanillaGuiOverlay.SCOREBOARD.id())) return VanillaHudElement.SCOREBOARD;
		if (id.equals(VanillaGuiOverlay.POTION_ICONS.id())) return VanillaHudElement.EFFECTS;
		if (id.equals(VanillaGuiOverlay.CROSSHAIR.id())) return VanillaHudElement.CROSSHAIR;
		return null;
	}

	private static boolean matches(ResourceLocation id, VanillaGuiOverlay... overlays) {
		for (VanillaGuiOverlay overlay : overlays) if (id.equals(overlay.id())) return true;
		return false;
	}
}
