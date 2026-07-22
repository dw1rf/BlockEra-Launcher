package space.blockera.client.chat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import space.blockera.client.BlockeraCoreServices;
import space.blockera.client.ui.BlockeraButton;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Public editor for safe local chat filters and their tabs. */
public final class BlockeraChatSettingsScreen extends Screen {
	private static final int[] COLORS = {
		0xFF168ED1, 0xFF59D69A, 0xFF62B7FF, 0xFFF0C66B, 0xFFFF7597, 0xFFB8C0CC
	};
	private final Screen parent;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private int selectedIndex;
	private int scroll;
	private EditBox name;
	private EditBox include;
	private EditBox exclude;
	private BlockeraButton detachedWindowButton;

	public BlockeraChatSettingsScreen(Screen parent) {
		super(Component.translatable("blockera.chat.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(760, width - 24);
		int panelHeight = Math.min(430, height - 24);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		int formLeft = left + 244;
		int formWidth = right - formLeft - 18;
		name = field(formLeft, top + 104, formWidth, "blockera.chat.filter.name");
		include = field(formLeft, top + 164, formWidth, "blockera.chat.filter.include");
		exclude = field(formLeft, top + 224, formWidth, "blockera.chat.filter.exclude");
		addRenderableWidget(name);
		addRenderableWidget(include);
		addRenderableWidget(exclude);
		addRenderableWidget(new BlockeraButton(left + 18, bottom - 46, 96, 28,
			Component.translatable("blockera.chat.filter.add"), button -> addFilter(), true));
		addRenderableWidget(new BlockeraButton(left + 122, bottom - 46, 96, 28,
			Component.translatable("blockera.chat.filter.delete"), button -> deleteFilter()));
		addRenderableWidget(new BlockeraButton(formLeft, bottom - 80, 104, 28,
			Component.translatable("blockera.chat.filter.color"), button -> cycleColor()));
		addRenderableWidget(new BlockeraButton(formLeft + 112, bottom - 80, 104, 28,
			Component.translatable("blockera.chat.filter.toggle"), button -> toggleFilter()));
		detachedWindowButton = addRenderableWidget(new BlockeraButton(formLeft + 224, bottom - 80, 124, 28,
			Component.translatable("blockera.chat.filter.window"), button -> toggleDetached()));
		addRenderableWidget(new BlockeraButton(formLeft + 356, bottom - 80, 124, 28,
			Component.translatable("blockera.chat.filter.background"), button -> toggleBackground()));
		addRenderableWidget(new BlockeraButton(right - 122, bottom - 46, 104, 28,
			Component.translatable("gui.done"), button -> onClose(), true));
		selectedIndex = clamp(selectedIndex, 0, config().filters().size());
		loadSelected();
	}

	private EditBox field(int x, int y, int fieldWidth, String hintKey) {
		EditBox result = new EditBox(font, x, y, fieldWidth, 22, Component.translatable(hintKey));
		result.setMaxLength(512);
		result.setBordered(false);
		result.setTextColor(ThemeTokens.TEXT);
		result.setTextColorUneditable(ThemeTokens.DIM);
		result.setHint(UiText.regular(Component.translatable(hintKey)));
		return result;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
		BlockeraDraw.panel(graphics, left, top, right, bottom);
		graphics.fill(left + 226, top, left + 227, bottom, ThemeTokens.BORDER);
		UiText.drawSemibold(graphics, getTitle(), left + 18, top + 18, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.translatable("blockera.chat.settings.subtitle"), left + 18, top + 36,
			ThemeTokens.MUTED);
		renderFilters(graphics, mouseX, mouseY);
		renderForm(graphics);
		renderFields(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderFields(GuiGraphics graphics) {
		for (EditBox field : List.of(name, include, exclude)) {
			BlockeraDraw.field(graphics, field.getX() - 2, field.getY() - 1,
				field.getRight() + 2, field.getBottom() + 1, field.isFocused());
		}
	}

	private void renderFilters(GuiGraphics graphics, int mouseX, int mouseY) {
		UiText.drawSemibold(graphics, Component.translatable("blockera.chat.filters"), left + 18, top + 68,
			ThemeTokens.TEXT);
		List<ChatFilterRule> filters = config().filters();
		int rowCount = filters.size() + 1;
		for (int index = scroll; index < rowCount; index++) {
			int y = top + 92 + (index - scroll) * 36;
			if (y + 30 > bottom - 58) break;
			boolean all = index == 0;
			ChatFilterRule filter = all ? null : filters.get(index - 1);
			ChatTab tab = all ? config().tab(ChatTab.ALL_ID) : config().tab(filter.id());
			boolean selected = index == selectedIndex;
			boolean hovered = inside(mouseX, mouseY, left + 16, y, left + 212, y + 30);
			BlockeraDraw.roundedRect(graphics, left + 16, y, left + 212, y + 30, ThemeTokens.RADIUS,
				selected ? ThemeTokens.SELECTION : hovered ? ThemeTokens.CARD_HOVER : ThemeTokens.CARD);
			graphics.fill(left + 16, y, left + 20, y + 30,
				all || filter.enabled() ? tab.color() : ThemeTokens.MUTED);
			UiText.draw(graphics, all ? Component.translatable("blockera.chat.tab.all")
				: Component.literal(filter.name()), left + 28, y + 10,
				selected ? ThemeTokens.TEXT : ThemeTokens.MUTED);
		}
	}

	private void renderForm(GuiGraphics graphics) {
		int x = left + 244;
		UiText.drawSemibold(graphics, Component.translatable("blockera.chat.filter.editor"), x, top + 68,
			ThemeTokens.TEXT);
		if (selectedAll()) {
			UiText.draw(graphics, Component.translatable("blockera.chat.tab.all_hint"), x, top + 96,
				ThemeTokens.MUTED);
			UiText.draw(graphics, Component.translatable("blockera.chat.tab.all_main"), x, top + 150,
				ThemeTokens.ACCENT);
			UiText.draw(graphics, Component.translatable("blockera.chat.filter.background_status",
				config().tab(ChatTab.ALL_ID).background() ? Component.translatable("blockera.state.enabled")
					: Component.translatable("blockera.state.disabled")), x, top + 170,
				config().tab(ChatTab.ALL_ID).background() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
			UiText.draw(graphics, Component.translatable("blockera.chat.history.unlimited"), x, top + 202,
				ThemeTokens.ACCENT);
			UiText.draw(graphics, Component.translatable("blockera.chat.history.session_only"), x, top + 220,
				ThemeTokens.MUTED);
			return;
		}
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.name"), x, top + 90, ThemeTokens.MUTED);
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.include_hint"), x, top + 150,
			ThemeTokens.MUTED);
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.exclude_hint"), x, top + 210,
			ThemeTokens.MUTED);
		ChatFilterRule filter = selected();
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.status",
			filter.enabled() ? Component.translatable("blockera.state.enabled")
				: Component.translatable("blockera.state.disabled")), x, top + 260,
			filter.enabled() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.window_status",
			config().tab(filter.id()).detached() ? Component.translatable("blockera.state.enabled")
				: Component.translatable("blockera.state.disabled")), x, top + 278,
			config().tab(filter.id()).detached() ? ThemeTokens.ACCENT : ThemeTokens.MUTED);
		UiText.draw(graphics, Component.translatable("blockera.chat.filter.background_status",
			config().tab(filter.id()).background() ? Component.translatable("blockera.state.enabled")
				: Component.translatable("blockera.state.disabled")), x, top + 296,
			config().tab(filter.id()).background() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
		UiText.draw(graphics, Component.translatable("blockera.chat.history.unlimited"), x, top + 316,
			ThemeTokens.ACCENT);
		UiText.draw(graphics, Component.translatable("blockera.chat.history.session_only"), x, top + 333,
			ThemeTokens.MUTED);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			EditBox clicked = fieldAt(event.x(), event.y());
			if (clicked != null && clicked.active) {
				focusOnly(clicked);
				clicked.mouseClicked(event, doubleClick);
				return true;
			}
		}
		if (event.button() == 0 && event.x() >= left + 16 && event.x() < left + 212
			&& event.y() >= top + 92 && event.y() < bottom - 58) {
			int index = scroll + (int) (event.y() - top - 92) / 36;
			if (index >= 0 && index <= config().filters().size()) {
				applySelected();
				selectedIndex = index;
				loadSelected();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX >= left && mouseX < left + 226) {
			int visible = Math.max(1, (bottom - top - 150) / 36);
			scroll = clamp(scroll - (int) Math.signum(verticalAmount), 0,
				Math.max(0, config().filters().size() + 1 - visible));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void addFilter() {
		applySelected();
		int suffix = 1;
		String id;
		do {
			id = "filter_" + suffix++;
		} while (hasFilter(id));
		ChatFilterRule filter = new ChatFilterRule(id,
			Component.translatable("blockera.chat.filter.default_name", config().filters().size() + 1).getString(),
			List.of(), List.of());
		List<ChatFilterRule> filters = new ArrayList<>(config().filters());
		filters.add(filter);
		config().setFilters(filters);
		List<ChatTab> tabs = new ArrayList<>(config().tabs());
		ChatTab tab = new ChatTab(id, filter.name(), List.of(id));
		tab.setColor(filter.color());
		int windowIndex = filters.size() - 1;
		tab.setWindowBounds(Math.max(8, width - 300 - windowIndex * 18),
			Math.min(Math.max(8, height - 150), 36 + windowIndex * 26), 280, 130);
		tabs.add(tab);
		config().setTabs(tabs);
		selectedIndex = filters.size();
		loadSelected();
		focusOnly(name);
		saveAndRefresh();
	}

	private void deleteFilter() {
		ChatFilterRule selected = selected();
		if (selected == null) return;
		String id = selected.id();
		List<ChatFilterRule> filters = new ArrayList<>(config().filters());
		filters.remove(selectedIndex - 1);
		config().setFilters(filters);
		config().setTabs(config().tabs().stream().filter(tab -> !tab.id().equals(id)).toList());
		if (config().activeTab().equals(id)) config().setActiveTab(ChatTab.ALL_ID);
		selectedIndex = clamp(selectedIndex, 0, filters.size());
		loadSelected();
		saveAndRefresh();
	}

	private void cycleColor() {
		ChatFilterRule filter = selected();
		ChatTab tab = selectedAll() ? config().tab(ChatTab.ALL_ID)
			: filter == null ? null : config().tab(filter.id());
		if (tab == null) return;
		int current = 0;
		for (int index = 0; index < COLORS.length; index++) if (COLORS[index] == tab.color()) current = index;
		tab.setColor(COLORS[(current + 1) % COLORS.length]);
		if (filter != null) filter.setColor(tab.color());
		saveAndRefresh();
	}

	private void toggleFilter() {
		ChatFilterRule filter = selected();
		if (filter == null) return;
		filter.setEnabled(!filter.enabled());
		saveAndRefresh();
	}

	private void toggleDetached() {
		if (selectedAll()) return;
		ChatFilterRule filter = selected();
		applySelected();
		ChatTab tab = selectedAll() ? config().tab(ChatTab.ALL_ID)
			: filter == null ? null : config().tab(filter.id());
		if (tab == null) return;
		tab.setDetached(!tab.detached());
		saveAndRefresh();
		rebuildWidgets();
	}

	private void toggleBackground() {
		ChatFilterRule filter = selected();
		ChatTab tab = selectedAll() ? config().tab(ChatTab.ALL_ID)
			: filter == null ? null : config().tab(filter.id());
		if (tab == null) return;
		tab.setBackground(!tab.background());
		saveAndRefresh();
		rebuildWidgets();
	}

	private void applySelected() {
		ChatFilterRule filter = selected();
		if (filter == null || name == null) return;
		String value = name.getValue().trim();
		if (!value.isEmpty()) filter.setName(value.substring(0, Math.min(48, value.length())));
		filter.setInclude(phrases(include.getValue()));
		filter.setExclude(phrases(exclude.getValue()));
		ChatTab tab = config().tab(filter.id());
		tab.setName(filter.name());
		tab.setColor(filter.color());
	}

	private void loadSelected() {
		ChatFilterRule filter = selected();
		if (name == null) return;
		name.setValue(selectedAll() ? Component.translatable("blockera.chat.tab.all").getString()
			: filter == null ? "" : filter.name());
		include.setValue(filter == null ? "" : String.join(", ", filter.include()));
		exclude.setValue(filter == null ? "" : String.join(", ", filter.exclude()));
		name.active = filter != null;
		include.active = filter != null;
		exclude.active = filter != null;
		if (detachedWindowButton != null) detachedWindowButton.active = !selectedAll();
	}

	private EditBox fieldAt(double mouseX, double mouseY) {
		for (EditBox field : List.of(name, include, exclude)) {
			if (mouseX >= field.getX() - 2 && mouseX < field.getRight() + 2
				&& mouseY >= field.getY() - 1 && mouseY < field.getBottom() + 1) return field;
		}
		return null;
	}

	private void focusOnly(EditBox focused) {
		name.setFocused(name == focused);
		include.setFocused(include == focused);
		exclude.setFocused(exclude == focused);
		setFocused(focused);
	}

	private static List<String> phrases(String value) {
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(part -> !part.isEmpty())
			.map(part -> part.substring(0, Math.min(ChatFilterRule.MAX_PHRASE_LENGTH, part.length())))
			.limit(ChatFilterRule.MAX_PHRASES)
			.toList();
	}

	private ChatFilterRule selected() {
		return selectedIndex <= 0 || selectedIndex > config().filters().size()
			? null : config().filters().get(selectedIndex - 1);
	}

	private boolean selectedAll() { return selectedIndex == 0; }

	private boolean hasFilter(String id) {
		return config().filters().stream().anyMatch(filter -> filter.id().equals(id));
	}

	private ChatConfig config() { return BlockeraCoreServices.chat().config(); }

	private void saveAndRefresh() {
		applySelected();
		BlockeraChatRuntime.instance().refreshConfiguration();
	}

	@Override
	public void onClose() {
		applySelected();
		BlockeraChatRuntime.instance().refreshConfiguration();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() { return false; }

	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
