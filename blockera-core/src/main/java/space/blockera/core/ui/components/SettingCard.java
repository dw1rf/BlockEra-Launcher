package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.BlockeraIcon;
import space.blockera.core.ui.BlockeraMenuScreen;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;
import space.blockera.core.ui.UiFont;
import space.blockera.core.ui.settings.ClientSettingModel;
import space.blockera.core.ui.settings.ClientSettingType;

/** Animated compact setting row with original icon, description, toggle/action and unavailable states. */
public final class SettingCard {
	private final ClientSettingModel model;
	private final UiAnimation hover = new UiAnimation(0.0F);
	private final UiAnimation toggle;
	private final UiAnimation press = new UiAnimation(0.0F);
	private int left;
	private int top;
	private int right;
	private int bottom;

	public SettingCard(ClientSettingModel model) {
		this.model = model;
		toggle = new UiAnimation(model.enabled() ? 1.0F : 0.0F);
	}

	public ClientSettingModel model() { return model; }

	public void setBounds(int left, int top, int right, int bottom) {
		this.left = left; this.top = top; this.right = right; this.bottom = bottom;
	}

	public void render(PoseStack poseStack, int mouseX, int mouseY, ThemeTokens theme, boolean selected) {
		boolean hovered = contains(mouseX, mouseY);
		float hoverAmount = hover.update(hovered ? 1.0F : 0.0F, 130);
		float pressAmount = press.update(0.0F, 120);
		int fill = blend(theme.glassCardArgb(), theme.cardHoverArgb(), Math.min(1.0F, hoverAmount + pressAmount * 0.35F));
		if (!model.available()) fill = withAlpha(fill, 190);
		int border = selected ? theme.accentArgb() : theme.borderArgb();
		BlockeraDraw.roundedRect(poseStack, left, top, right, bottom, theme.cardRadius(), border);
		BlockeraDraw.roundedRect(poseStack, left + 1, top + 1, right - 1, bottom - 1,
				Math.max(0, theme.cardRadius() - 1), fill);

		int iconColor = model.available() ? (model.enabled() ? theme.accentHoverArgb() : theme.textMutedArgb())
				: withAlpha(theme.textMutedArgb(), 110);
		BlockeraDraw.roundedRect(poseStack, left + 7, top + (bottom - top - 24) / 2,
				left + 31, top + (bottom - top - 24) / 2 + 24, 6,
				model.enabled() ? theme.accentSoftArgb() : 0x7A151821);
		model.icon().draw(poseStack, left + 12, top + (bottom - top - 14) / 2, iconColor);

		int controlWidth = model.hasSettings() ? 62 : model.type() == ClientSettingType.TOGGLE ? 35 : 42;
		int textWidth = Math.max(20, right - left - 42 - controlWidth);
		Component title = Component.translatable(model.titleKey());
		int textColor = model.available() ? theme.textPrimaryArgb() : withAlpha(theme.textMutedArgb(), 145);
		if (model.descriptionKey() == null) {
			UiFont.drawSemibold(poseStack, UiFont.ellipsize(title, textWidth, true), left + 37,
					top + (bottom - top - 9) / 2.0F, textColor);
		} else {
			UiFont.drawSemibold(poseStack, UiFont.ellipsize(title, textWidth, true), left + 37, top + 7, textColor);
			UiFont.drawSmall(poseStack, UiFont.ellipsize(Component.translatable(model.descriptionKey()), textWidth, false),
					left + 37, top + 20, theme.textMutedArgb());
		}

		if (model.hasSettings()) renderSettingsButton(poseStack, theme);
		if (model.type() == ClientSettingType.TOGGLE) renderToggle(poseStack, theme);
		else if (model.type() == ClientSettingType.ACTION) renderArrow(poseStack, theme.textMutedArgb());
		else renderUnavailable(poseStack, theme);
	}

	public boolean activate(BlockeraMenuScreen screen) {
		return activate(screen, Double.NEGATIVE_INFINITY);
	}

	public boolean activate(BlockeraMenuScreen screen, double mouseX) {
		if (!model.available()) return false;
		press.snap(1.0F);
		if (model.hasSettings() && mouseX >= right - 38) model.toggleValue();
		else model.activate(screen);
		return true;
	}

	public boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }

	public Component tooltip() {
		if (!model.available()) return Component.translatable("blockera.state.coming_soon.description");
		return model.descriptionKey() == null ? null : Component.translatable(model.descriptionKey());
	}

	private void renderToggle(PoseStack poseStack, ThemeTokens theme) {
		float amount = toggle.update(model.enabled() ? 1.0F : 0.0F, 160);
		int trackLeft = right - 31;
		int trackTop = top + (bottom - top - 12) / 2;
		int track = blend(0xFF343843, theme.accentArgb(), amount);
		BlockeraDraw.roundedRect(poseStack, trackLeft, trackTop, right - 7, trackTop + 12, 6, track);
		int knobX = trackLeft + 2 + Math.round(amount * 12.0F);
		BlockeraDraw.roundedRect(poseStack, knobX, trackTop + 2, knobX + 8, trackTop + 10, 4, 0xFFF2F3F7);
	}

	private void renderSettingsButton(PoseStack poseStack, ThemeTokens theme) {
		int buttonLeft = right - 55;
		int buttonTop = top + (bottom - top - 18) / 2;
		BlockeraDraw.roundedRect(poseStack, buttonLeft, buttonTop, buttonLeft + 18, buttonTop + 18,
				6, 0x7A151821);
		BlockeraIcon.SETTINGS.draw(poseStack, buttonLeft + 2, buttonTop + 2, theme.textMutedArgb());
	}

	private void renderArrow(PoseStack poseStack, int color) {
		int x = right - 16;
		int y = (top + bottom) / 2;
		GuiComponent.fill(poseStack, x - 2, y - 3, x, y - 2, color);
		GuiComponent.fill(poseStack, x, y - 2, x + 2, y - 1, color);
		GuiComponent.fill(poseStack, x + 2, y - 1, x + 3, y + 1, color);
		GuiComponent.fill(poseStack, x, y + 1, x + 2, y + 2, color);
		GuiComponent.fill(poseStack, x - 2, y + 2, x, y + 3, color);
	}

	private void renderUnavailable(PoseStack poseStack, ThemeTokens theme) {
		Component label = Component.translatable("blockera.state.coming_soon");
		int width = UiFont.width(label);
		BlockeraDraw.roundedRect(poseStack, right - width - 15, top + (bottom - top - 15) / 2,
				right - 7, top + (bottom - top - 15) / 2 + 15, 5, 0x78343843);
		UiFont.drawSmall(poseStack, label, right - width - 11, top + (bottom - top - 8) / 2.0F,
				withAlpha(theme.textMutedArgb(), 165));
	}

	private static int blend(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int a = mix((from >>> 24) & 255, (to >>> 24) & 255, t);
		int r = mix((from >>> 16) & 255, (to >>> 16) & 255, t);
		int g = mix((from >>> 8) & 255, (to >>> 8) & 255, t);
		int b = mix(from & 255, to & 255, t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int mix(int from, int to, float amount) { return Math.round(from + (to - from) * amount); }
	private static int withAlpha(int color, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF); }
}
