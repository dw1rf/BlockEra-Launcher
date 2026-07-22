package space.blockera.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Blockera-owned button skin ported from Forge; no vanilla button texture is rendered. */
public final class BlockeraButton extends AbstractButton {
    @FunctionalInterface
    public interface Action {
        void run(BlockeraButton button);
    }

    private final Action action;
    private final boolean accent;
    private float hoverProgress;
    private long lastRenderNanos;

    public BlockeraButton(
        int x,
        int y,
        int width,
        int height,
        Component label,
        Action action,
        boolean accent
    ) {
        super(x, y, width, height, label);
        this.action = action;
        this.accent = accent;
    }

    public BlockeraButton(int x, int y, int width, int height, Component label, Action action) {
        this(x, y, width, height, label, action, false);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (active) {
            action.run(this);
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHoverProgress();
        int fill = accent
            ? lerpColor(ThemeTokens.ACCENT, ThemeTokens.ACCENT_HOVER, hoverProgress)
            : lerpColor(ThemeTokens.CARD, ThemeTokens.CARD_HOVER, hoverProgress);
        int text = active ? ThemeTokens.TEXT : ThemeTokens.MUTED;
		BlockeraDraw.button(graphics, getX(), getY(), getRight(), getBottom(),
			active ? fill : ThemeTokens.FIELD, isHoveredOrFocused(), accent && active);
        UiText.drawCentered(graphics, getMessage(), getX() + getWidth() / 2,
            getY() + (getHeight() - 9) / 2, text);
    }

    private void updateHoverProgress() {
        long now = System.nanoTime();
        if (lastRenderNanos == 0L || Boolean.getBoolean("blockera.reduceUiMotion")) {
            hoverProgress = isHoveredOrFocused() ? 1.0F : 0.0F;
        } else {
            float step = Math.min(1.0F, (now - lastRenderNanos) / 80_000_000.0F);
            float target = isHoveredOrFocused() ? 1.0F : 0.0F;
            hoverProgress += (target - hoverProgress) * step;
        }
        lastRenderNanos = now;
    }

    private static int lerpColor(int from, int to, float amount) {
        int a = channel(from, 24) + Math.round((channel(to, 24) - channel(from, 24)) * amount);
        int r = channel(from, 16) + Math.round((channel(to, 16) - channel(from, 16)) * amount);
        int g = channel(from, 8) + Math.round((channel(to, 8) - channel(from, 8)) * amount);
        int b = channel(from, 0) + Math.round((channel(to, 0) - channel(from, 0)) * amount);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
