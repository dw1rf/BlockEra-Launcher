package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;
import space.blockera.core.ui.settings.SettingFilter;

import java.util.function.Consumer;

/** Compact cycling dropdown for All / Enabled / Available filters. */
public final class FilterDropdown extends AbstractWidget {
	private final ThemeTokens theme;
	private final Consumer<SettingFilter> onChanged;
	private SettingFilter filter = SettingFilter.ALL;

	public FilterDropdown(int x, int y, int width, int height, ThemeTokens theme, Consumer<SettingFilter> onChanged) {
		super(x, y, width, height, Component.translatable(SettingFilter.ALL.translationKey()));
		this.theme = theme;
		this.onChanged = onChanged;
	}

	public SettingFilter filter() { return filter; }

	public void setBounds(int x, int y, int width, int height) {
		this.x = x; this.y = y; setWidth(width); setHeight(height);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		filter = filter.next();
		setMessage(Component.translatable(filter.translationKey()));
		onChanged.accept(filter);
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		int fill = isHoveredOrFocused() ? theme.cardHoverArgb() : theme.cardArgb();
		BlockeraDraw.roundedRect(poseStack, x, y, x + width, y + height, 6, theme.borderArgb());
		BlockeraDraw.roundedRect(poseStack, x + 1, y + 1, x + width - 1, y + height - 1, 5, fill);
		UiFont.draw(poseStack, UiFont.ellipsize(getMessage(), width - 24, false), x + 9, y + (height - 9) / 2.0F,
				theme.textPrimaryArgb());
		int cx = x + width - 12;
		int cy = y + height / 2;
		GuiComponent.fill(poseStack, cx - 2, cy - 1, cx + 3, cy, theme.textMutedArgb());
		GuiComponent.fill(poseStack, cx - 1, cy, cx + 2, cy + 1, theme.textMutedArgb());
		GuiComponent.fill(poseStack, cx, cy + 1, cx + 1, cy + 2, theme.textMutedArgb());
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
