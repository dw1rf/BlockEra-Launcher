package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;
import space.blockera.core.mixin.AbstractSliderButtonAccessor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Visual-only adapter for vanilla buttons and option sliders. Callbacks remain owned by Minecraft. */
public final class BlockeraVanillaWidgetRenderer {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final Map<AbstractWidget, UiAnimation> HOVER =
			Collections.synchronizedMap(new WeakHashMap<>());

	private BlockeraVanillaWidgetRenderer() {
	}

	public static boolean supports(AbstractWidget widget) {
		if (!ScreenStyleAdapter.shouldStyleCurrentScreen()) return false;
		if (widget instanceof LockIconButton || widget instanceof ImageButton) return false;
		return widget instanceof Button || widget instanceof CycleButton<?> || widget instanceof AbstractSliderButton;
	}

	public static void render(AbstractWidget widget, PoseStack poseStack, int mouseX, int mouseY) {
		boolean hovered = widget.active && widget.isHoveredOrFocused();
		float hover = HOVER.computeIfAbsent(widget, ignored -> new UiAnimation(0.0F))
				.update(hovered ? 1.0F : 0.0F, UiMotionTokens.HOVER_MILLIS);
		boolean pressed = hovered && GLFW.glfwGetMouseButton(
				net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
				== GLFW.GLFW_PRESS;

		if (widget instanceof AbstractSliderButton slider) {
			renderSlider(slider, widget, poseStack, hover, pressed);
		} else {
			renderButton(widget, poseStack, hover, pressed);
		}
		if (hovered && widget instanceof Button button) {
			button.renderToolTip(poseStack, mouseX, mouseY);
		}
	}

	private static void renderButton(AbstractWidget widget, PoseStack poseStack, float hover, boolean pressed) {
		boolean primary = isPrimary(widget.getMessage());
		int fill;
		int border;
		int text;
		if (!widget.active) {
			fill = 0x9912141C;
			border = 0x18FFFFFF;
			text = THEME.textDisabledArgb();
		} else if (primary) {
			fill = pressed ? 0xFF7551DB : blend(THEME.accentArgb(), THEME.accentHoverArgb(), hover);
			border = blend(0x668D68FF, 0xB8B49CFF, hover);
			text = THEME.textPrimaryArgb();
		} else {
			fill = pressed ? THEME.cardPressedArgb() : blend(THEME.glassCardArgb(), THEME.cardHoverArgb(), hover);
			border = blend(THEME.borderArgb(), THEME.borderHoverArgb(), hover);
			text = widget.active ? blend(THEME.textSecondaryArgb(), THEME.textPrimaryArgb(), hover)
					: THEME.textDisabledArgb();
		}
		BlockeraDraw.glassPanel(poseStack, widget.x, widget.y, widget.x + widget.getWidth(),
				widget.y + widget.getHeight(), THEME.buttonRadius(), fill, border);
		UiFont.drawCentered(poseStack, UiFont.ellipsize(widget.getMessage(), widget.getWidth() - 12, primary),
				widget.x + widget.getWidth() * 0.5F,
				widget.y + (widget.getHeight() - UiFont.REGULAR_SIZE) * 0.5F, text);
	}

	private static void renderSlider(AbstractSliderButton slider, AbstractWidget widget, PoseStack poseStack,
			float hover, boolean pressed) {
		int fill = widget.active ? (pressed ? THEME.cardPressedArgb() : blend(THEME.glassCardArgb(), THEME.cardHoverArgb(), hover))
				: 0x9912141C;
		int border = widget.active ? blend(THEME.borderArgb(), THEME.borderHoverArgb(), hover) : 0x18FFFFFF;
		BlockeraDraw.glassPanel(poseStack, widget.x, widget.y, widget.x + widget.getWidth(),
				widget.y + widget.getHeight(), THEME.buttonRadius(), fill, border);

		int trackLeft = widget.x + 9;
		int trackRight = widget.x + widget.getWidth() - 9;
		int trackTop = widget.y + widget.getHeight() - 5;
		BlockeraDraw.roundedRect(poseStack, trackLeft, trackTop, trackRight, trackTop + 2, 1, 0x662F3340);
		double value = ((AbstractSliderButtonAccessor) slider).blockera$getValue();
		int thumbX = trackLeft + (int) Math.round((trackRight - trackLeft) * Math.max(0.0D, Math.min(1.0D, value)));
		BlockeraDraw.roundedRect(poseStack, trackLeft, trackTop, Math.max(trackLeft + 1, thumbX), trackTop + 2,
				1, widget.active ? THEME.accentArgb() : THEME.textDisabledArgb());
		BlockeraDraw.roundedRect(poseStack, thumbX - 3, trackTop - 2, thumbX + 3, trackTop + 4,
				3, widget.active ? THEME.textPrimaryArgb() : THEME.textDisabledArgb());
		int text = widget.active ? THEME.textPrimaryArgb() : THEME.textDisabledArgb();
		UiFont.drawCentered(poseStack, UiFont.ellipsize(widget.getMessage(), widget.getWidth() - 12, false),
				widget.x + widget.getWidth() * 0.5F,
				widget.y + (widget.getHeight() - UiFont.REGULAR_SIZE) * 0.5F - 1, text);
	}

	static boolean isPrimary(Component message) {
		if (!(message.getContents() instanceof TranslatableContents translated)) return false;
		return switch (translated.getKey()) {
			case "gui.done", "menu.returnToGame", "blockera.hud.configure" -> true;
			default -> false;
		};
	}

	private static int blend(int from, int to, float amount) {
		float value = Math.max(0.0F, Math.min(1.0F, amount));
		int alpha = mix(from >>> 24, to >>> 24, value);
		int red = mix(from >> 16 & 255, to >> 16 & 255, value);
		int green = mix(from >> 8 & 255, to >> 8 & 255, value);
		int blue = mix(from & 255, to & 255, value);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int mix(int from, int to, float amount) {
		return Math.round(from + (to - from) * amount);
	}
}
