package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Blockera-owned button; no vanilla Button skin, font or hover renderer is involved. */
public final class BlockeraButton extends AbstractWidget {
	@FunctionalInterface
	public interface OnPress { void onPress(BlockeraButton button); }

	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final OnPress onPress;
	private final boolean accent;
	private final UiAnimation hover = new UiAnimation(0.0F);

	public BlockeraButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		this(x, y, width, height, message, onPress, false);
	}

	public BlockeraButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean accent) {
		super(x, y, width, height, message);
		this.onPress = onPress;
		this.accent = accent;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		setWidth(width);
		setHeight(height);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		if (active) onPress.onPress(this);
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		float amount = hover.update(active && isHoveredOrFocused() ? 1.0F : 0.0F, 130);
		int fill;
		int border;
		int text;
		if (!active) {
			fill = 0x86121620;
			border = THEME.borderArgb();
			text = THEME.textDisabledArgb();
		} else if (accent) {
			fill = blend(THEME.accentArgb(), THEME.accentHoverArgb(), amount);
			border = fill;
			text = 0xFFF8F7FF;
		} else {
			fill = blend(THEME.cardArgb(), THEME.cardHoverArgb(), amount);
			border = blend(THEME.borderArgb(), THEME.borderHoverArgb(), amount);
			text = amount > 0.1F ? THEME.textPrimaryArgb() : THEME.textMutedArgb();
		}
		BlockeraDraw.glassPanel(poseStack, x, y, x + width, y + height, THEME.buttonRadius(), fill, border);
		UiFont.drawCentered(poseStack, UiFont.ellipsize(getMessage(), width - 12, accent),
				x + width / 2.0F, y + (height - UiFont.REGULAR_SIZE) / 2.0F, text);
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	private static int blend(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int a = mix(from >>> 24, to >>> 24, t);
		int r = mix(from >> 16 & 255, to >> 16 & 255, t);
		int g = mix(from >> 8 & 255, to >> 8 & 255, t);
		int b = mix(from & 255, to & 255, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int mix(int from, int to, float amount) { return Math.round(from + (to - from) * amount); }
}
