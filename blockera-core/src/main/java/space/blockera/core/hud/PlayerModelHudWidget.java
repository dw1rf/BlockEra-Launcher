package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** Local player preview. It never downloads skins or cosmetics outside Minecraft's normal profile pipeline. */
public final class PlayerModelHudWidget implements HudWidget {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	@Override public String id() { return BuiltinWidgetIds.PLAYER_MODEL; }
	@Override public Component title() { return Component.translatable("blockera.hud.widget.player_model"); }
	@Override public HudCategory category() { return HudCategory.PLAYER; }
	@Override public int width(HudDataSnapshot data) { return 52; }
	@Override public int height(HudDataSnapshot data) { return 68; }

	@Override public void render(PoseStack poseStack, HudDataSnapshot data, int x, int y,
			HudWidgetSettings settings, boolean preview) {
		if (settings.background) {
			int alpha = Math.round(218.0F * settings.opacity);
			BlockeraDraw.glassPanel(poseStack, x, y, x + width(data), y + height(data), 7,
					(alpha << 24) | 0x0D0D14, preview ? THEME.accentArgb() : ((alpha << 24) | 0x343147));
		}
		var player = Minecraft.getInstance().player;
		if (player != null) {
			// InventoryScreen renders through the global model-view stack and ignores
			// the caller's PoseStack. Convert the widget-local center and scale back to
			// actual GUI coordinates so the model is visible in both HUD and preview.
			Vector4f center = transformed(poseStack, x + 26.0F, y + 62.0F);
			Vector4f unit = transformed(poseStack, x + 27.0F, y + 62.0F);
			float effectiveScale = (float) Math.hypot(unit.x() - center.x(), unit.y() - center.y());
			InventoryScreen.renderEntityInInventory(Math.round(center.x()), Math.round(center.y()),
					Math.max(8, Math.round(27.0F * effectiveScale)), 18.0F, 0.0F, player);
		} else {
			UiFont.drawCentered(poseStack, Component.literal("B"), x + 26, y + 29, settings.valueArgb());
		}
	}

	private static Vector4f transformed(PoseStack poseStack, float x, float y) {
		Vector4f point = new Vector4f(x, y, 0.0F, 1.0F);
		point.transform(poseStack.last().pose());
		return point;
	}
}
