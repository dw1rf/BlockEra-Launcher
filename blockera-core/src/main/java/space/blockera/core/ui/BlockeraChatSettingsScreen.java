package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatConfig;
import space.blockera.core.chat.ChatConfigStore;
import space.blockera.core.chat.ChatFilterRule;
import space.blockera.core.chat.ChatTab;
import space.blockera.core.chat.ChatTimestampMode;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.hud.VanillaHudSettings;
import space.blockera.core.ui.components.SearchField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** First-party display, timestamp, tab and safe substring-filter settings for native Minecraft chat. */
public final class BlockeraChatSettingsScreen extends Screen {
	private static final int[] FILTER_COLORS = {
			0xFF9B7BFF, 0xFF60D6A7, 0xFFF0C66B, 0xFF68B5FF, 0xFFEE00F5, 0xFFE27D8D
	};
	private final ChatConfigStore chatStore = ChatConfigStore.instance();
	private final Screen parent;
	private final VanillaHudElement element;
	private final HudLayoutStore hudStore = HudLayoutStore.instance();
	private Page page = Page.DISPLAY;
	private int selectedFilter;
	private int filterScroll;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private SearchField nameInput;
	private SearchField includeInput;
	private SearchField excludeInput;

	private enum Page { DISPLAY, FILTERS }

	public BlockeraChatSettingsScreen(Screen parent, VanillaHudElement element, Component title) {
		super(title);
		this.parent = parent;
		this.element = element;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(760, width - 24);
		int panelHeight = Math.min(440, height - 24);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		addRenderableWidget(new BlockeraButton(left + 14, top + 12, 76, 22,
				Component.translatable("blockera.common.back"), button -> onClose()));
		addRenderableWidget(new BlockeraButton(left + 104, top + 12, 96, 22,
				Component.translatable("blockera.chat.display"), button -> switchPage(Page.DISPLAY), page == Page.DISPLAY));
		addRenderableWidget(new BlockeraButton(left + 206, top + 12, 96, 22,
				Component.translatable("blockera.chat.filters"), button -> switchPage(Page.FILTERS), page == Page.FILTERS));
		addRenderableWidget(new BlockeraButton(right - 104, top + 12, 90, 22,
				Component.translatable("blockera.common.done"), button -> onClose(), true));
		if (page == Page.DISPLAY) initDisplay(); else initFilters();
	}

	private void initDisplay() {
		int leftColumn = left + 18;
		int rightColumn = (left + right) / 2 + 6;
		int row = top + 76;
		stepper(leftColumn, row, () -> changeScale(-0.05F), () -> changeScale(0.05F));
		stepper(rightColumn, row, () -> changeWidth(-20), () -> changeWidth(20));
		row += 48;
		stepper(leftColumn, row, () -> changeOpenHeight(-10), () -> changeOpenHeight(10));
		stepper(rightColumn, row, () -> changeClosedHeight(-10), () -> changeClosedHeight(10));
		row += 48;
		stepper(leftColumn, row, () -> changeTextOpacity(-0.05F), () -> changeTextOpacity(0.05F));
		stepper(rightColumn, row, () -> changeBackgroundOpacity(-0.05F), () -> changeBackgroundOpacity(0.05F));
		row += 48;
		stepper(leftColumn, row, () -> changeLineSpacing(-0.1F), () -> changeLineSpacing(0.1F));
		stepper(rightColumn, row, () -> changeDelay(-1), () -> changeDelay(1));
		row += 48;
		addRenderableWidget(new BlockeraButton(leftColumn, row, 164, 28, timestampLabel(), button -> {
			ChatTimestampMode[] values = ChatTimestampMode.values();
			config().setTimestampMode(values[(config().timestampMode().ordinal() + 1) % values.length]);
			saveChat(); rebuildWidgets();
		}));
		addRenderableWidget(new BlockeraButton(leftColumn + 170, row, 164, 28, hourLabel(), button -> {
			config().setUse24Hour(!config().use24Hour()); saveChat(); rebuildWidgets();
		}));
		addRenderableWidget(new BlockeraButton(rightColumn, row, 164, 28, colorsLabel(), button -> {
			minecraft.options.chatColors().set(!minecraft.options.chatColors().get()); applyVanillaOptions(); rebuildWidgets();
		}));
		addRenderableWidget(new BlockeraButton(rightColumn + 170, row, 164, 28, linksLabel(), button -> {
			minecraft.options.chatLinks().set(!minecraft.options.chatLinks().get()); applyVanillaOptions(); rebuildWidgets();
		}));
	}

	private void initFilters() {
		List<ChatFilterRule> filters = config().filters();
		selectedFilter = Math.max(0, Math.min(selectedFilter, Math.max(0, filters.size() - 1)));
		int editorLeft = left + 260;
		int inputWidth = right - editorLeft - 18;
		nameInput = addRenderableWidget(new SearchField(editorLeft, top + 82, inputWidth, 24, ThemeTokens.darkDefault()));
		includeInput = addRenderableWidget(new SearchField(editorLeft, top + 128, inputWidth, 24, ThemeTokens.darkDefault()));
		excludeInput = addRenderableWidget(new SearchField(editorLeft, top + 174, inputWidth, 24, ThemeTokens.darkDefault()));
		nameInput.setMessage(Component.translatable("blockera.chat.filter.name"));
		includeInput.setMessage(Component.translatable("blockera.chat.filter.include"));
		excludeInput.setMessage(Component.translatable("blockera.chat.filter.exclude"));
		loadSelectedFilter();
		int actionTop = top + 220;
		addRenderableWidget(new BlockeraButton(editorLeft, actionTop, 112, 24,
				Component.translatable("blockera.chat.filter.apply"), button -> applyFilterEdits(), true));
		addRenderableWidget(new BlockeraButton(editorLeft + 120, actionTop, 96, 24,
				Component.translatable("blockera.chat.filter.color"), button -> cycleFilterColor()));
		addRenderableWidget(new BlockeraButton(editorLeft, actionTop + 32, 112, 24,
				mentionLabel(), button -> toggleMention()));
		addRenderableWidget(new BlockeraButton(editorLeft + 120, actionTop + 32, 96, 24,
				soundLabel(), button -> toggleSound()));
		addRenderableWidget(new BlockeraButton(editorLeft + 224, actionTop + 32, 104, 24,
				enabledLabel(), button -> toggleFilterEnabled()));
		addRenderableWidget(new BlockeraButton(left + 18, bottom - 42, 104, 24,
				Component.translatable("blockera.chat.filter.add"), button -> addFilter(), true));
		addRenderableWidget(new BlockeraButton(left + 130, bottom - 42, 104, 24,
				Component.translatable("blockera.chat.filter.delete"), button -> deleteFilter()));
	}

	private void stepper(int x, int y, Runnable minus, Runnable plus) {
		addRenderableWidget(new BlockeraButton(x + 244, y + 7, 34, 22, Component.literal("−"), button -> minus.run()));
		addRenderableWidget(new BlockeraButton(x + 286, y + 7, 34, 22, Component.literal("+"), button -> plus.run()));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, ThemeTokens.darkDefault().gameBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, 14, 0xC40E111A,
				ThemeTokens.darkDefault().borderHoverArgb());
		Component fittedTitle = UiFont.ellipsize(title, Math.max(48, right - left - 430), true);
		UiFont.drawCentered(poseStack, fittedTitle, (left + right) * 0.5F, top + 19,
				ThemeTokens.darkDefault().textPrimaryArgb());
		if (page == Page.DISPLAY) renderDisplay(poseStack, mouseX, mouseY); else renderFilters(poseStack, mouseX, mouseY);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void renderDisplay(PoseStack poseStack, int mouseX, int mouseY) {
		int leftColumn = left + 18;
		int rightColumn = (left + right) / 2 + 6;
		int row = top + 76;
		displayRow(poseStack, leftColumn, row, "blockera.hud.editor.scale", Math.round(settings().scale * 100) + "%", mouseX, mouseY);
		displayRow(poseStack, rightColumn, row, "blockera.chat.width", config().width() + " px", mouseX, mouseY);
		row += 48;
		displayRow(poseStack, leftColumn, row, "blockera.chat.open_height", config().openHeight() + " px", mouseX, mouseY);
		displayRow(poseStack, rightColumn, row, "blockera.chat.closed_height", config().closedHeight() + " px", mouseX, mouseY);
		row += 48;
		displayRow(poseStack, leftColumn, row, "blockera.chat.text_opacity", Math.round(config().textOpacity() * 100) + "%", mouseX, mouseY);
		displayRow(poseStack, rightColumn, row, "blockera.chat.background_opacity", Math.round(config().backgroundOpacity() * 100) + "%", mouseX, mouseY);
		row += 48;
		displayRow(poseStack, leftColumn, row, "blockera.chat.line_spacing", Math.round(config().lineSpacing() * 100) + "%", mouseX, mouseY);
		displayRow(poseStack, rightColumn, row, "blockera.chat.delay", config().messageDelaySeconds() + " s", mouseX, mouseY);
	}

	private void displayRow(PoseStack poseStack, int x, int y, String key, String value, int mouseX, int mouseY) {
		int rightEdge = x + 328;
		boolean hovered = mouseX >= x && mouseX < rightEdge && mouseY >= y && mouseY < y + 36;
		ThemeTokens theme = ThemeTokens.darkDefault();
		BlockeraDraw.glassPanel(poseStack, x, y, rightEdge, y + 36, 7,
				hovered ? theme.cardHoverArgb() : theme.glassCardArgb(), theme.borderArgb());
		UiFont.drawSemibold(poseStack, Component.translatable(key), x + 10, y + 10, theme.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.literal(value), x + 184, y + 13, theme.textMutedArgb());
	}

	private void renderFilters(PoseStack poseStack, int mouseX, int mouseY) {
		ThemeTokens theme = ThemeTokens.darkDefault();
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.chat.tabs"), left + 18, top + 56, theme.textPrimaryArgb());
		List<ChatFilterRule> filters = config().filters();
		for (int index = 0; index < filters.size(); index++) {
			if (index < filterScroll) continue;
			int y = top + 78 + (index - filterScroll) * 30;
			if (y + 26 >= bottom - 48) break;
			ChatFilterRule filter = filters.get(index);
			boolean active = index == selectedFilter;
			BlockeraDraw.glassPanel(poseStack, left + 18, y, left + 238, y + 26, 6,
					active ? theme.accentSoftArgb() : theme.glassCardArgb(), active ? filter.color() : theme.borderArgb());
			UiFont.draw(poseStack, Component.literal(filter.name()), left + 30, y + 8,
					active ? theme.textPrimaryArgb() : theme.textMutedArgb());
		}
		UiFont.drawSmall(poseStack, Component.translatable("blockera.chat.filter.hint"), left + 260, top + 58, theme.textMutedArgb());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (page == Page.FILTERS && button == 0 && mouseX >= left + 18 && mouseX < left + 238) {
			int index = filterScroll + ((int) mouseY - top - 78) / 30;
			if (index >= 0 && index < config().filters().size()) {
				applyFilterEdits(); selectedFilter = index; rebuildWidgets(); return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (page == Page.FILTERS && mouseX >= left + 18 && mouseX < left + 238
				&& mouseY >= top + 78 && mouseY < bottom - 48) {
			int visible = Math.max(1, (bottom - 48 - (top + 78)) / 30);
			int maximum = Math.max(0, config().filters().size() - visible);
			filterScroll = Math.max(0, Math.min(maximum, filterScroll - (int) Math.signum(delta)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	private void switchPage(Page next) { if (page == Page.FILTERS) applyFilterEdits(); page = next; rebuildWidgets(); }
	private ChatConfig config() { return chatStore.config(); }
	private VanillaHudSettings settings() { return hudStore.vanillaSettings(element.id()); }
	private void save() { hudStore.save(); }
	private ChatFilterRule selected() { return config().filters().isEmpty() ? null : config().filters().get(selectedFilter); }
	private void loadSelectedFilter() {
		ChatFilterRule filter = selected();
		if (filter == null) return;
		nameInput.setValue(filter.name());
		includeInput.setValue(String.join(", ", filter.include()));
		excludeInput.setValue(String.join(", ", filter.exclude()));
	}
	private void applyFilterEdits() {
		ChatFilterRule filter = selected();
		if (filter == null || nameInput == null) return;
		String name = nameInput.value().trim();
		List<String> include = phrases(includeInput.value());
		if (name.isEmpty() || include.isEmpty()) return;
		filter.setName(name);
		filter.setInclude(include);
		filter.setExclude(phrases(excludeInput.value()));
		List<ChatTab> tabs = new ArrayList<>(config().tabs());
		for (ChatTab tab : tabs) if (tab.filterIds().contains(filter.id())) tab.setName(name);
		config().setTabs(tabs);
		saveChat();
	}
	private void addFilter() {
		if (config().filters().size() >= ChatConfig.MAX_FILTERS) return;
		int suffix = 1;
		String id;
		do { id = "filter_" + suffix++; } while (hasFilter(id));
		ChatFilterRule filter = new ChatFilterRule(id, "Filter " + (config().filters().size() + 1), List.of("mention"), List.of());
		List<ChatFilterRule> filters = new ArrayList<>(config().filters()); filters.add(filter); config().setFilters(filters);
		List<ChatTab> tabs = new ArrayList<>(config().tabs());
		if (tabs.size() - 1 < ChatConfig.MAX_CUSTOM_TABS) tabs.add(new ChatTab(id, filter.name(), List.of(id)));
		config().setTabs(tabs);
		selectedFilter = filters.size() - 1;
		int visible = Math.max(1, (bottom - 48 - (top + 78)) / 30);
		filterScroll = Math.max(0, selectedFilter - visible + 1);
		saveChat(); rebuildWidgets();
	}
	private void deleteFilter() {
		ChatFilterRule filter = selected(); if (filter == null) return;
		List<ChatFilterRule> filters = new ArrayList<>(config().filters()); filters.remove(selectedFilter); config().setFilters(filters);
		List<ChatTab> tabs = new ArrayList<>(config().tabs()); tabs.removeIf(tab -> tab.filterIds().contains(filter.id())); config().setTabs(tabs);
		if (config().activeTab().equals(filter.id())) config().setActiveTab(ChatTab.ALL_ID);
		selectedFilter = Math.max(0, selectedFilter - 1); saveChat(); rebuildWidgets();
	}
	private void cycleFilterColor() { ChatFilterRule f = selected(); if (f == null) return; int i = 0; while (i < FILTER_COLORS.length && FILTER_COLORS[i] != f.color()) i++; f.setColor(FILTER_COLORS[(i + 1) % FILTER_COLORS.length]); saveChat(); }
	private void toggleMention() { ChatFilterRule f = selected(); if (f != null) { f.setMention(!f.mention()); saveChat(); rebuildWidgets(); } }
	private void toggleSound() { ChatFilterRule f = selected(); if (f != null) { f.setSound(!f.sound()); saveChat(); rebuildWidgets(); } }
	private void toggleFilterEnabled() { ChatFilterRule f = selected(); if (f != null) { f.setEnabled(!f.enabled()); saveChat(); rebuildWidgets(); } }
	private boolean hasFilter(String id) { return config().filters().stream().anyMatch(filter -> filter.id().equals(id)); }
	private static List<String> phrases(String value) { return Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).limit(16).toList(); }

	private void changeScale(float delta) { settings().scale = clamp(settings().scale + delta, 0.5F, 2.0F); save(); applyVanillaOptions(); rebuildWidgets(); }
	private void changeWidth(int delta) { config().setWidth(Math.max(40, Math.min(320, config().width() + delta))); saveChat(); rebuildWidgets(); }
	private void changeOpenHeight(int delta) { config().setOpenHeight(Math.max(20, Math.min(180, config().openHeight() + delta))); saveChat(); rebuildWidgets(); }
	private void changeClosedHeight(int delta) { config().setClosedHeight(Math.max(20, Math.min(180, config().closedHeight() + delta))); saveChat(); rebuildWidgets(); }
	private void changeTextOpacity(float delta) { config().setTextOpacity(clamp(config().textOpacity() + delta, 0, 1)); saveChat(); rebuildWidgets(); }
	private void changeBackgroundOpacity(float delta) { config().setBackgroundOpacity(clamp(config().backgroundOpacity() + delta, 0, 1)); saveChat(); rebuildWidgets(); }
	private void changeLineSpacing(float delta) { config().setLineSpacing(clamp(config().lineSpacing() + delta, 0, 1)); saveChat(); rebuildWidgets(); }
	private void changeDelay(int delta) { config().setMessageDelaySeconds(Math.max(0, Math.min(6, config().messageDelaySeconds() + delta))); saveChat(); rebuildWidgets(); }

	private void saveChat() { chatStore.save(); BlockeraChatRuntime.instance().refreshConfiguration(); applyVanillaOptions(); }
	private void applyVanillaOptions() {
		if (minecraft == null) return;
		minecraft.options.chatWidth().set(unit((config().width() - 40.0D) / 280.0D));
		minecraft.options.chatHeightFocused().set(unit((config().openHeight() - 20.0D) / 160.0D));
		minecraft.options.chatHeightUnfocused().set(unit((config().closedHeight() - 20.0D) / 160.0D));
		minecraft.options.chatOpacity().set((double) config().textOpacity());
		minecraft.options.textBackgroundOpacity().set((double) config().backgroundOpacity());
		minecraft.options.chatLineSpacing().set(unit(config().lineSpacing()));
		minecraft.options.chatDelay().set(Math.max(0.0D, Math.min(6.0D, config().messageDelaySeconds())));
		minecraft.options.save();
		if (minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
	}

	private Component timestampLabel() { return Component.translatable("blockera.chat.timestamp", config().timestampMode().name()); }
	private Component hourLabel() { return Component.translatable("blockera.chat.hour_format", config().use24Hour() ? "24" : "12"); }
	private Component colorsLabel() { return Component.translatable("blockera.chat.colors", state(minecraft.options.chatColors().get())); }
	private Component linksLabel() { return Component.translatable("blockera.chat.links", state(minecraft.options.chatLinks().get())); }
	private Component mentionLabel() { ChatFilterRule f = selected(); return Component.translatable("blockera.chat.mention", state(f != null && f.mention())); }
	private Component soundLabel() { ChatFilterRule f = selected(); return Component.translatable("blockera.chat.sound", state(f != null && f.sound())); }
	private Component enabledLabel() { ChatFilterRule f = selected(); return Component.translatable("blockera.chat.filter.enabled", state(f != null && f.enabled())); }
	private static String state(boolean enabled) { return Component.translatable(enabled ? "blockera.state.enabled" : "blockera.state.disabled").getString(); }
	private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
	private static double unit(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }

	@Override
	public void onClose() { if (page == Page.FILTERS) applyFilterEdits(); saveChat(); minecraft.setScreen(parent); }

	@Override
	public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
