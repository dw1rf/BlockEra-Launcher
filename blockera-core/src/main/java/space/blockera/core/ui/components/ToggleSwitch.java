package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;

/** Reusable animated switch rendered entirely by the SDF shape pipeline. */
public final class ToggleSwitch {
	private final UiAnimation animation;

	public ToggleSwitch(boolean enabled) { animation = new UiAnimation(enabled ? 1.0F : 0.0F); }

	public void render(PoseStack poseStack, int left, int top, boolean enabled, boolean available, ThemeTokens theme) {
		float amount = animation.update(enabled ? 1.0F : 0.0F, 160);
		int offColor = available ? theme.cardPressedArgb() : 0xFF20232C;
		int track = blend(offColor, theme.accentArgb(), available ? amount : 0.0F);
		BlockeraDraw.roundedRect(poseStack, left, top, left + 28, top + 14, 7, track);
		int knob = left + 2 + Math.round(amount * 14.0F);
		BlockeraDraw.roundedRect(poseStack, knob, top + 2, knob + 10, top + 12, 5,
				available ? theme.textPrimaryArgb() : theme.textDisabledArgb());
	}

	private static int blend(int from, int to, float amount) {
		float value = Math.max(0.0F, Math.min(1.0F, amount));
		int a = mix(from >>> 24, to >>> 24, value);
		int r = mix(from >> 16 & 255, to >> 16 & 255, value);
		int g = mix(from >> 8 & 255, to >> 8 & 255, value);
		int b = mix(from & 255, to & 255, value);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int mix(int from, int to, float amount) { return Math.round(from + (to - from) * amount); }
}
