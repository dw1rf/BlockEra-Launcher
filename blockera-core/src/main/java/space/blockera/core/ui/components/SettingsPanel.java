package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import space.blockera.core.ui.BlockeraMenuScreen;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiAnimation;
import space.blockera.core.ui.UiFont;
import space.blockera.core.ui.UiScissor;
import space.blockera.core.ui.settings.ClientSettingCategory;
import space.blockera.core.ui.settings.ClientSettingModel;
import space.blockera.core.ui.settings.ClientSettingsCatalog;
import space.blockera.core.ui.settings.ClientSettingType;
import space.blockera.core.ui.settings.SettingFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Filtered, clipped and independently scrolling data-driven card grid. */
public final class SettingsPanel {
	private static final int CARD_HEIGHT = 38;
	private static final int GAP = 5;
	private final ThemeTokens theme;
	private final Map<String, SettingCard> cards = new LinkedHashMap<>();
	private final UiAnimation scroll = new UiAnimation(0.0F);
	private ClientSettingCategory category = ClientSettingCategory.INTERFACE;
	private SettingFilter filter = SettingFilter.ALL;
	private String query = "";
	private int left;
	private int top;
	private int right;
	private int bottom;
	private boolean twoColumns;
	private float targetScroll;
	private int contentHeight;
	private int selectedIndex;
	private SettingCard hoveredCard;

	public SettingsPanel(ThemeTokens theme) {
		this.theme = theme;
		for (ClientSettingModel model : ClientSettingsCatalog.all()) cards.put(model.id(), new SettingCard(model));
	}

	public void setBounds(int left, int top, int right, int bottom, boolean twoColumns) {
		this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.twoColumns = twoColumns;
		clampScroll();
	}

	public void setCategory(ClientSettingCategory category) {
		this.category = category;
		targetScroll = 0.0F;
		scroll.snap(0.0F);
		selectedIndex = 0;
	}

	public void setFilter(SettingFilter filter) { this.filter = filter; targetScroll = 0.0F; selectedIndex = 0; }
	public void setQuery(String query) { this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT); }

	public void render(PoseStack poseStack, int mouseX, int mouseY) {
		List<SettingCard> visible = visibleCards();
		Component section = query.isEmpty() ? Component.translatable(category.translationKey())
				: Component.translatable("blockera.search.results");
		UiFont.drawSemibold(poseStack, section, left, top, theme.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.settings.count", visible.size()),
				right - 55, top + 1, theme.textMutedArgb());

		int viewportTop = top + 16;
		float currentScroll = scroll.update(targetScroll, 150);
		int columns = twoColumns ? 2 : 1;
		int columnGap = twoColumns ? 6 : 0;
		int cardWidth = (right - left - columnGap) / columns;
		int rows = (visible.size() + columns - 1) / columns;
		contentHeight = Math.max(0, rows * (CARD_HEIGHT + GAP) - GAP);
		clampScroll();
		hoveredCard = null;
		UiScissor.enable(left, viewportTop, right, bottom);
		for (int index = 0; index < visible.size(); index++) {
			int column = index % columns;
			int row = index / columns;
			int x = left + column * (cardWidth + columnGap);
			int y = Math.round(viewportTop + row * (CARD_HEIGHT + GAP) - currentScroll);
			SettingCard card = visible.get(index);
			card.setBounds(x, y, x + cardWidth, y + CARD_HEIGHT);
			card.render(poseStack, mouseX, mouseY, theme, index == selectedIndex);
			if (card.contains(mouseX, mouseY) && mouseY >= viewportTop && mouseY < bottom) hoveredCard = card;
		}
		UiScissor.disable();
		renderScrollbar(poseStack, viewportTop);
	}

	public boolean mouseClicked(double mouseX, double mouseY, BlockeraMenuScreen screen) {
		if (mouseX < left || mouseX >= right || mouseY < top + 16 || mouseY >= bottom) return false;
		List<SettingCard> visible = visibleCards();
		for (int index = 0; index < visible.size(); index++) {
			SettingCard card = visible.get(index);
			if (card.contains(mouseX, mouseY)) {
				selectedIndex = index;
				card.activate(screen, mouseX);
				return true;
			}
		}
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) return false;
		targetScroll -= delta * 30.0F;
		clampScroll();
		return true;
	}

	public void moveSelection(int delta) {
		List<SettingCard> visible = visibleCards();
		if (visible.isEmpty()) { selectedIndex = 0; return; }
		selectedIndex = Math.max(0, Math.min(visible.size() - 1, selectedIndex + delta));
		int columns = twoColumns ? 2 : 1;
		int row = selectedIndex / columns;
		int viewport = Math.max(1, bottom - (top + 16));
		int rowTop = row * (CARD_HEIGHT + GAP);
		if (rowTop < targetScroll) targetScroll = rowTop;
		else if (rowTop + CARD_HEIGHT > targetScroll + viewport) targetScroll = rowTop + CARD_HEIGHT - viewport;
		clampScroll();
	}

	public boolean activateSelected(BlockeraMenuScreen screen) {
		List<SettingCard> visible = visibleCards();
		if (visible.isEmpty()) return false;
		selectedIndex = Math.max(0, Math.min(visible.size() - 1, selectedIndex));
		return visible.get(selectedIndex).activate(screen);
	}

	public Component hoveredTooltip() { return hoveredCard == null ? null : hoveredCard.tooltip(); }

	private List<SettingCard> visibleCards() {
		return cards.values().stream().filter(card -> {
			ClientSettingModel model = card.model();
			if (query.isEmpty() && model.category() != category) return false;
			if (!query.isEmpty() && !Component.translatable(model.titleKey()).getString().toLowerCase(Locale.ROOT).contains(query)) return false;
			return switch (filter) {
				case ALL -> true;
				case ENABLED -> model.type() == ClientSettingType.TOGGLE && model.enabled();
				case AVAILABLE -> model.available();
			};
		}).toList();
	}

	private void clampScroll() {
		int viewport = Math.max(0, bottom - (top + 16));
		targetScroll = Math.max(0.0F, Math.min(targetScroll, Math.max(0, contentHeight - viewport)));
	}

	private void renderScrollbar(PoseStack poseStack, int viewportTop) {
		int viewport = bottom - viewportTop;
		if (contentHeight <= viewport || viewport <= 0) return;
		int thumb = Math.max(18, viewport * viewport / contentHeight);
		int track = viewport - thumb;
		int maxScroll = contentHeight - viewport;
		int y = viewportTop + Math.round(track * scroll.value() / Math.max(1, maxScroll));
		GuiComponent.fill(poseStack, right - 2, y, right, y + thumb, 0x709B9EAC);
	}
}
