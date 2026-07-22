package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.ui.components.CategorySidebar;
import space.blockera.core.ui.components.FilterDropdown;
import space.blockera.core.ui.components.SearchField;
import space.blockera.core.ui.components.SettingsPanel;
import space.blockera.core.ui.components.TopNavigation;
import space.blockera.core.ui.settings.ClientSettingCategory;

/** Responsive first-party Blockera control center. */
public final class BlockeraMenuScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final UiAnimation opening = new UiAnimation(0.0F);
	private final TopNavigation navigation = new TopNavigation(THEME, this::openTab);
	private final CategorySidebar sidebar = new CategorySidebar(THEME, this::selectCategory);
	private final SettingsPanel settings = new SettingsPanel(THEME);
	private LayoutMetrics layout;
	private SearchField search;
	private FilterDropdown filter;
	private BlockeraButton hudButton;
	private final Screen parent;

	public BlockeraMenuScreen() {
		this(null);
	}

	public BlockeraMenuScreen(Screen parent) {
		super(Component.translatable("blockera.control.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout = LayoutMetrics.calculate(width, height);
		navigation.setBounds(0, 0, width, BlockeraTopNavigation.HEIGHT);
		sidebar.setBounds(layout.left(), layout.panelTop(), layout.left() + layout.sidebarWidth(), layout.panelBottom());

		int inset = layout.compact() ? 7 : 10;
		int toolbarLeft = layout.panelLeft() + inset;
		int toolbarRight = layout.panelRight() - inset;
		int toolbarTop = layout.panelTop() + inset;
		int toolbarHeight = layout.compact() ? 20 : 22;
		int available = toolbarRight - toolbarLeft;
		int filterWidth = Math.min(94, Math.max(68, available / 4));
		int hudWidth = Math.min(116, Math.max(88, available / 3));
		boolean wrapToolbar = available < 310;

		search = addRenderableWidget(new SearchField(toolbarLeft, toolbarTop,
				wrapToolbar ? available : Math.max(92, available - filterWidth - hudWidth - 10), toolbarHeight, THEME));
		filter = addRenderableWidget(new FilterDropdown(0, 0, filterWidth, toolbarHeight, THEME, settings::setFilter));
		hudButton = addRenderableWidget(new BlockeraButton(0, 0, hudWidth, toolbarHeight,
				Component.translatable("blockera.hud.configure"), button -> openHudEditor(), true));

		int contentTop;
		if (wrapToolbar) {
			int secondRow = toolbarTop + toolbarHeight + 5;
			filter.setBounds(toolbarLeft, secondRow, filterWidth, toolbarHeight);
			hudButton.setBounds(toolbarRight - hudWidth, secondRow, hudWidth, toolbarHeight);
			contentTop = secondRow + toolbarHeight + 8;
		} else {
			filter.setBounds(toolbarRight - hudWidth - filterWidth - 5, toolbarTop, filterWidth, toolbarHeight);
			hudButton.setBounds(toolbarRight - hudWidth, toolbarTop, hudWidth, toolbarHeight);
			contentTop = toolbarTop + toolbarHeight + 9;
		}
		settings.setBounds(toolbarLeft, contentTop, toolbarRight, layout.panelBottom() - inset, layout.twoColumns());
		opening.snap(0.0F);
	}

	private void selectCategory(ClientSettingCategory category) {
		settings.setCategory(category);
	}

	private void openTab(TopNavigation.Tab tab) {
		if (minecraft == null) return;
		if (tab == TopNavigation.Tab.HOME) onClose();
		else if (tab == TopNavigation.Tab.SETTINGS) minecraft.setScreen(new OptionsScreen(this, minecraft.options));
	}

	public void openHudEditor() {
		if (minecraft != null) minecraft.setScreen(new HudEditorScreen(this));
	}

	public void openWidgetSettings(String widgetId, String titleKey) {
		if (minecraft != null) minecraft.setScreen(new BlockeraWidgetSettingsScreen(this, widgetId, titleKey));
	}

	public void openChatSettings() {
		if (minecraft != null) minecraft.setScreen(new BlockeraChatSettingsScreen(this, VanillaHudElement.CHAT,
				Component.translatable("blockera.setting.ingame_chat")));
	}

	public void openHitboxSettings() {
		if (minecraft != null) minecraft.setScreen(new BlockeraHitboxSettingsScreen(this));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		float progress = opening.update(1.0F, 190);
		int backdropAlpha = Math.round(104.0F * progress);
		GuiComponent.fill(poseStack, 0, 0, width, height, backdropAlpha << 24 | (THEME.backgroundArgb() & 0xFFFFFF));
		navigation.render(poseStack, mouseX, mouseY);
		sidebar.render(poseStack, mouseX, mouseY, layout.compact());
		BlockeraDraw.glassPanel(poseStack, layout.panelLeft(), layout.panelTop(), layout.panelRight(), layout.panelBottom(),
				THEME.cornerRadius(), THEME.glassPanelArgb(), THEME.borderArgb());
		GuiComponent.fill(poseStack, layout.panelLeft() + 1, layout.panelTop() + 1,
				layout.panelLeft() + Math.round((layout.panelRight() - layout.panelLeft() - 2) * progress),
				layout.panelTop() + 2, THEME.accentArgb());
		settings.setQuery(search.value());
		settings.render(poseStack, mouseX, mouseY);
		super.render(poseStack, mouseX, mouseY, partialTick);
		Component tooltip = settings.hoveredTooltip();
		if (tooltip != null) renderBlockeraTooltip(poseStack, tooltip, mouseX, mouseY);
	}

	private void renderBlockeraTooltip(PoseStack poseStack, Component tooltip, int mouseX, int mouseY) {
		Component text = UiFont.ellipsize(tooltip, Math.min(250, width - 28), false);
		int tooltipWidth = UiFont.width(text) + 14;
		int left = Math.min(width - tooltipWidth - 6, mouseX + 10);
		int top = Math.min(height - 24, mouseY + 10);
		BlockeraDraw.glassPanel(poseStack, left, top, left + tooltipWidth, top + 20,
				THEME.smallRadius(), THEME.surfaceElevatedArgb(), THEME.borderHoverArgb());
		UiFont.drawSmall(poseStack, text, left + 7, top + 6, THEME.textPrimaryArgb());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (navigation.mouseClicked(mouseX, mouseY)) return true;
		if (sidebar.mouseClicked(mouseX, mouseY, layout.compact())) return true;
		return settings.mouseClicked(mouseX, mouseY, this);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (sidebar.mouseScrolled(mouseX, mouseY, delta, layout.compact())) return true;
		if (settings.mouseScrolled(mouseX, mouseY, delta)) return true;
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (search.isFocused()) return false;
		if (keyCode == GLFW.GLFW_KEY_UP) { settings.moveSelection(layout.twoColumns() ? -2 : -1); return true; }
		if (keyCode == GLFW.GLFW_KEY_DOWN) { settings.moveSelection(layout.twoColumns() ? 2 : 1); return true; }
		if (keyCode == GLFW.GLFW_KEY_LEFT && layout.twoColumns()) { settings.moveSelection(-1); return true; }
		if (keyCode == GLFW.GLFW_KEY_RIGHT && layout.twoColumns()) { settings.moveSelection(1); return true; }
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) return settings.activateSelected(this);
		return false;
	}

	@Override
	public void onClose() {
		HudLayoutStore.instance().save();
		if (minecraft != null) minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return parent != null && parent.isPauseScreen();
	}
}
