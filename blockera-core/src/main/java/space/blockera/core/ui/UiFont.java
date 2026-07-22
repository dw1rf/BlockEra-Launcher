package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.render.UiFontRenderer;

/** Typography facade backed exclusively by the Blockera STB TrueType renderer. */
public final class UiFont {
	public static final float REGULAR_SIZE = 9.5F;
	public static final float SMALL_SIZE = 8.0F;
	private static final UiFontRenderer RENDERER = UiFontRenderer.instance();

	private UiFont() {
	}

	public static int width(Component value) {
		return (int) Math.ceil(RENDERER.width(value, REGULAR_SIZE, UiFontRenderer.Weight.REGULAR));
	}

	public static int widthSemibold(Component value) {
		return (int) Math.ceil(RENDERER.width(value, REGULAR_SIZE, UiFontRenderer.Weight.SEMIBOLD));
	}

	public static void draw(PoseStack poseStack, Component value, float x, float y, int color) {
		RENDERER.draw(poseStack, value, x, y, REGULAR_SIZE, color, UiFontRenderer.Weight.REGULAR);
	}

	public static void draw(PoseStack poseStack, String value, float x, float y, int color) {
		RENDERER.draw(poseStack, value, x, y, REGULAR_SIZE, color, UiFontRenderer.Weight.REGULAR);
	}

	public static void drawSemibold(PoseStack poseStack, Component value, float x, float y, int color) {
		RENDERER.draw(poseStack, value, x, y, REGULAR_SIZE, color, UiFontRenderer.Weight.SEMIBOLD);
	}

	public static void drawSmall(PoseStack poseStack, Component value, float x, float y, int color) {
		RENDERER.draw(poseStack, value, x, y, SMALL_SIZE, color, UiFontRenderer.Weight.REGULAR);
	}

	public static void drawSmall(PoseStack poseStack, String value, float x, float y, int color) {
		RENDERER.draw(poseStack, value, x, y, SMALL_SIZE, color, UiFontRenderer.Weight.REGULAR);
	}

	public static void drawCentered(PoseStack poseStack, Component value, float centerX, float y, int color) {
		float width = RENDERER.width(value, REGULAR_SIZE, UiFontRenderer.Weight.REGULAR);
		RENDERER.draw(poseStack, value, centerX - width * 0.5F, y, REGULAR_SIZE, color,
				UiFontRenderer.Weight.REGULAR);
	}

	public static Component ellipsize(Component value, int maximumWidth, boolean semibold) {
		UiFontRenderer.Weight weight = semibold ? UiFontRenderer.Weight.SEMIBOLD : UiFontRenderer.Weight.REGULAR;
		return Component.literal(RENDERER.ellipsize(value.getString(), maximumWidth, REGULAR_SIZE, weight));
	}
}
