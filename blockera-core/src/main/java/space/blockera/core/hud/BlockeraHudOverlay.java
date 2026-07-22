package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.ui.DetachedChatPanels;

/** Renders all enabled first-party widgets using the active resolution-independent layout. */
public final class BlockeraHudOverlay {
	private BlockeraHudOverlay() {
	}

	public static void render(ForgeGui gui, PoseStack poseStack, float partialTick, int screenWidth, int screenHeight) {
		Minecraft minecraft = gui.getMinecraft();
		if (!ClientConfig.HUD_ENABLED.get() || minecraft.options.hideGui || minecraft.player == null
				|| minecraft.screen != null) {
			return;
		}
		renderAll(poseStack, minecraft, screenWidth, screenHeight, false);
		DetachedChatPanels.render(poseStack, -1, -1, screenWidth, screenHeight);
	}

	public static void renderAll(PoseStack poseStack, Minecraft minecraft, int screenWidth, int screenHeight,
			boolean preview) {
		HudDataSnapshot data = preview || minecraft.player == null
				? HudDataSnapshot.preview() : HudDataSnapshot.capture(minecraft);
		HudLayoutStore store = HudLayoutStore.instance();
		for (var entry : store.snapshot().entrySet()) {
			HudWidget widget = (HudWidget) BuiltinHudWidgets.registry().get(HudLayoutStore.baseWidgetId(entry.getKey()));
			HudWidgetSettings settings = entry.getValue();
			if (settings.enabled) renderWidget(poseStack, data, widget, settings,
					screenWidth, screenHeight, preview);
		}
	}

	public static HudPoint position(HudDataSnapshot data, HudWidget widget, HudWidgetSettings settings,
			int screenWidth, int screenHeight) {
		return settings.anchor.resolve(screenWidth, screenHeight, widget.width(data), widget.height(data),
				settings.scale, settings.offsetX, settings.offsetY);
	}

	public static void renderWidget(PoseStack poseStack, HudDataSnapshot data, HudWidget widget,
			HudWidgetSettings settings, int screenWidth, int screenHeight, boolean selected) {
		HudPoint point = position(data, widget, settings, screenWidth, screenHeight);
		poseStack.pushPose();
		poseStack.translate(point.x(), point.y(), 0.0D);
		poseStack.scale(settings.scale, settings.scale, 1.0F);
		widget.render(poseStack, data, 0, 0, settings, selected);
		poseStack.popPose();
	}
}
