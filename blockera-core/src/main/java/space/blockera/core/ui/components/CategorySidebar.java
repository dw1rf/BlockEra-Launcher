package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import space.blockera.core.BlockeraCore;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;
import space.blockera.core.ui.UiFont;
import space.blockera.core.ui.UiScissor;
import space.blockera.core.ui.settings.ClientSettingCategory;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Compact independently scrollable category rail. */
public final class CategorySidebar {
	private final ThemeTokens theme;
	private final Consumer<ClientSettingCategory> onSelected;
	private final Map<ClientSettingCategory, UiAnimation> hover = new EnumMap<>(ClientSettingCategory.class);
	private final UiAnimation scroll = new UiAnimation(0.0F);
	private ClientSettingCategory selected = ClientSettingCategory.INTERFACE;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private float targetScroll;

	public CategorySidebar(ThemeTokens theme, Consumer<ClientSettingCategory> onSelected) {
		this.theme = theme;
		this.onSelected = onSelected;
		for (ClientSettingCategory category : ClientSettingCategory.values()) hover.put(category, new UiAnimation(0.0F));
	}

	public void setBounds(int left, int top, int right, int bottom) {
		this.left = left; this.top = top; this.right = right; this.bottom = bottom;
		clampScroll();
	}

	public void render(PoseStack poseStack, int mouseX, int mouseY, boolean compact) {
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, theme.cornerRadius(), theme.glassPanelArgb(), theme.borderArgb());
		int headerHeight = compact ? 37 : 47;
		BlockeraDraw.roundedRect(poseStack, left + 9, top + 9, left + 31, top + 31, 6, theme.accentArgb());
		UiFont.drawSemibold(poseStack, Component.literal("B"), left + 17, top + 15, 0xFFFFFFFF);
		if (!compact || right - left > 94) {
			UiFont.drawSemibold(poseStack, Component.literal("BLOCKERA"), left + 37, top + 10, theme.textPrimaryArgb());
			UiFont.drawSmall(poseStack, Component.literal("CLIENT  " + BlockeraCore.VERSION), left + 37, top + 22, theme.textMutedArgb());
		}

		int viewportTop = top + headerHeight;
		int viewportBottom = bottom - 7;
		float currentScroll = scroll.update(targetScroll, 150);
		UiScissor.enable(left + 2, viewportTop, right - 2, viewportBottom);
		int rowHeight = compact ? 20 : 23;
		int gap = 3;
		for (ClientSettingCategory category : ClientSettingCategory.values()) {
			int y = Math.round(viewportTop + category.ordinal() * (rowHeight + gap) - currentScroll);
			boolean hovered = contains(mouseX, mouseY, left + 7, y, right - 7, y + rowHeight);
			float amount = hover.get(category).update(hovered ? 1.0F : 0.0F, 130);
			boolean active = category == selected;
			int fill = active ? theme.accentSoftArgb() : blend(theme.glassCardArgb(), theme.cardHoverArgb(), amount * 0.55F);
			BlockeraDraw.roundedRect(poseStack, left + 7, y, right - 7, y + rowHeight, 6, fill);
			if (active) GuiComponent.fill(poseStack, left + 7, y + 4, left + 9, y + rowHeight - 4, theme.accentArgb());
			category.icon().draw(poseStack, left + 13, y + (rowHeight - 14) / 2,
					active ? theme.accentHoverArgb() : theme.textMutedArgb());
			Component label = Component.translatable(category.translationKey());
			UiFont.draw(poseStack, UiFont.ellipsize(label, right - left - 45, false), left + 32,
					y + (rowHeight - 9) / 2.0F, active ? theme.textPrimaryArgb() : theme.textMutedArgb());
		}
		UiScissor.disable();
		renderScrollbar(poseStack, viewportTop, viewportBottom, rowHeight, gap);
	}

	public boolean mouseClicked(double mouseX, double mouseY, boolean compact) {
		int headerHeight = compact ? 37 : 47;
		int rowHeight = compact ? 20 : 23;
		int gap = 3;
		int viewportTop = top + headerHeight;
		if (!contains(mouseX, mouseY, left, viewportTop, right, bottom)) return false;
		int index = (int) ((mouseY - viewportTop + scroll.value()) / (rowHeight + gap));
		if (index < 0 || index >= ClientSettingCategory.values().length) return true;
		selected = ClientSettingCategory.values()[index];
		onSelected.accept(selected);
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta, boolean compact) {
		if (!contains(mouseX, mouseY, left, top, right, bottom)) return false;
		targetScroll -= delta * (compact ? 18.0F : 24.0F);
		clampScroll();
		return true;
	}

	private void clampScroll() {
		int headerHeight = bottom - top < 220 ? 37 : 47;
		int rowHeight = bottom - top < 220 ? 20 : 23;
		int content = ClientSettingCategory.values().length * (rowHeight + 3) - 3;
		int viewport = Math.max(0, bottom - (top + headerHeight) - 7);
		targetScroll = Math.max(0.0F, Math.min(targetScroll, Math.max(0, content - viewport)));
	}

	private void renderScrollbar(PoseStack poseStack, int viewportTop, int viewportBottom, int rowHeight, int gap) {
		int content = ClientSettingCategory.values().length * (rowHeight + gap) - gap;
		int viewport = viewportBottom - viewportTop;
		if (content <= viewport) return;
		int thumb = Math.max(16, viewport * viewport / content);
		int track = viewport - thumb;
		int maxScroll = content - viewport;
		int y = viewportTop + Math.round(track * scroll.value() / Math.max(1, maxScroll));
		GuiComponent.fill(poseStack, right - 4, y, right - 2, y + thumb, 0x709B9EAC);
	}

	private static int blend(int from, int to, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
		int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
		int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
		int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static boolean contains(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}
}
