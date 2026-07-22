package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;

/** Original, texture-free Blockera icon set with a consistent one-pixel stroke. */
public enum BlockeraIcon {
	APPEARANCE, CHAT, PLAYERS, CONNECTION, SERVER, HUD, LIGHT, CAMERA, BLOCKS,
	COMBAT, TOOLS, SETTINGS, PERFORMANCE, WORLD, EFFECTS, CLOCK;

	public void draw(PoseStack poseStack, int x, int y, int color) {
		switch (this) {
			case APPEARANCE -> {
				line(poseStack, x + 2, y + 2, x + 11, y + 3, color);
				line(poseStack, x + 2, y + 6, x + 9, y + 7, color);
				line(poseStack, x + 2, y + 10, x + 7, y + 11, color);
			}
			case CHAT -> {
				box(poseStack, x + 2, y + 2, x + 12, y + 10, color);
				fill(poseStack, x + 4, y + 10, x + 7, y + 13, color);
			}
			case PLAYERS -> {
				fill(poseStack, x + 3, y + 2, x + 7, y + 6, color);
				fill(poseStack, x + 8, y + 3, x + 11, y + 6, color);
				fill(poseStack, x + 2, y + 8, x + 8, y + 12, color);
				fill(poseStack, x + 8, y + 8, x + 12, y + 11, color);
			}
			case CONNECTION -> {
				fill(poseStack, x + 2, y + 8, x + 4, y + 12, color);
				fill(poseStack, x + 6, y + 5, x + 8, y + 12, color);
				fill(poseStack, x + 10, y + 2, x + 12, y + 12, color);
			}
			case SERVER -> {
				box(poseStack, x + 2, y + 2, x + 12, y + 6, color);
				box(poseStack, x + 2, y + 8, x + 12, y + 12, color);
				fill(poseStack, x + 4, y + 3, x + 5, y + 4, color);
				fill(poseStack, x + 4, y + 9, x + 5, y + 10, color);
			}
			case HUD -> {
				box(poseStack, x + 1, y + 2, x + 13, y + 11, color);
				fill(poseStack, x + 5, y + 12, x + 9, y + 13, color);
			}
			case LIGHT -> {
				box(poseStack, x + 4, y + 2, x + 10, y + 9, color);
				fill(poseStack, x + 5, y + 10, x + 9, y + 12, color);
			}
			case CAMERA -> {
				box(poseStack, x + 2, y + 4, x + 12, y + 11, color);
				box(poseStack, x + 5, y + 6, x + 9, y + 10, color);
			}
			case BLOCKS -> {
				box(poseStack, x + 2, y + 2, x + 8, y + 8, color);
				box(poseStack, x + 6, y + 6, x + 12, y + 12, color);
			}
			case COMBAT -> {
				line(poseStack, x + 3, y + 2, x + 11, y + 10, color);
				line(poseStack, x + 9, y + 2, x + 2, y + 9, color);
				fill(poseStack, x + 10, y + 9, x + 13, y + 12, color);
			}
			case TOOLS -> {
				line(poseStack, x + 2, y + 11, x + 11, y + 2, color);
				box(poseStack, x + 1, y + 9, x + 5, y + 13, color);
			}
			case SETTINGS -> {
				box(poseStack, x + 3, y + 3, x + 11, y + 11, color);
				box(poseStack, x + 6, y + 6, x + 8, y + 8, color);
			}
			case PERFORMANCE -> {
				line(poseStack, x + 2, y + 11, x + 6, y + 6, color);
				line(poseStack, x + 6, y + 6, x + 8, y + 9, color);
				line(poseStack, x + 8, y + 9, x + 12, y + 2, color);
			}
			case WORLD -> {
				box(poseStack, x + 2, y + 2, x + 12, y + 12, color);
				line(poseStack, x + 2, y + 7, x + 12, y + 7, color);
				line(poseStack, x + 7, y + 2, x + 7, y + 12, color);
			}
			case EFFECTS -> {
				fill(poseStack, x + 5, y + 2, x + 9, y + 7, color);
				box(poseStack, x + 3, y + 7, x + 11, y + 12, color);
			}
			case CLOCK -> {
				box(poseStack, x + 2, y + 2, x + 12, y + 12, color);
				line(poseStack, x + 7, y + 4, x + 7, y + 8, color);
				line(poseStack, x + 7, y + 8, x + 10, y + 8, color);
			}
		}
	}

	private static void box(PoseStack poseStack, int left, int top, int right, int bottom, int color) {
		fill(poseStack, left, top, right, top + 1, color);
		fill(poseStack, left, bottom - 1, right, bottom, color);
		fill(poseStack, left, top, left + 1, bottom, color);
		fill(poseStack, right - 1, top, right, bottom, color);
	}

	private static void line(PoseStack poseStack, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		for (int step = 0; step <= steps; step++) {
			int x = Math.round(x1 + (x2 - x1) * step / (float) Math.max(1, steps));
			int y = Math.round(y1 + (y2 - y1) * step / (float) Math.max(1, steps));
			fill(poseStack, x, y, x + 1, y + 1, color);
		}
	}

	private static void fill(PoseStack poseStack, int left, int top, int right, int bottom, int color) {
		GuiComponent.fill(poseStack, left, top, right, bottom, color);
	}
}
