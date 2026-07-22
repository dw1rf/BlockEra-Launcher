package space.blockera.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import space.blockera.client.BlockeraCoreServices;
import space.blockera.client.chat.BlockeraChatSettingsScreen;
import space.blockera.client.hitbox.HitboxCategory;
import space.blockera.client.hitbox.HitboxStyle;
import space.blockera.client.hud.BlockeraHudEditorScreen;

import java.util.Locale;

/** AI-free public Blockera control center. */
public final class BlockeraMenuScreen extends Screen {
	public enum Tab {
		OVERVIEW,
		HUD,
		CHAT,
		HITBOXES,
		VISUALS,
		MODULES
	}

	private final Screen parent;
	private Tab selectedTab;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private String moduleQuery = "";
	private EditBox moduleSearch;

	public BlockeraMenuScreen(Screen parent) {
		this(parent, Tab.OVERVIEW);
	}

	public BlockeraMenuScreen(Screen parent, Tab selectedTab) {
		super(Component.translatable("blockera.control.title"));
		this.parent = parent;
		this.selectedTab = selectedTab;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(860, width - 28);
		int panelHeight = Math.min(480, height - 28);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		int sidebar = Math.min(190, panelWidth / 4);
		int buttonLeft = left + 16;
		int buttonWidth = sidebar - 32;
		int y = top + 74;
		addTabButton(buttonLeft, y, buttonWidth, Tab.OVERVIEW, "blockera.control.overview");
		addTabButton(buttonLeft, y + 39, buttonWidth, Tab.HUD, "blockera.control.hud");
		addTabButton(buttonLeft, y + 70, buttonWidth, Tab.CHAT, "blockera.control.chat");
		addTabButton(buttonLeft, y + 105, buttonWidth, Tab.HITBOXES, "blockera.control.hitboxes");
		addTabButton(buttonLeft, y + 140, buttonWidth, Tab.VISUALS, "blockera.control.visuals");
		addTabButton(buttonLeft, y + 175, buttonWidth, Tab.MODULES, "blockera.control.modules");
		addRenderableWidget(new BlockeraButton(buttonLeft, bottom - 46, buttonWidth, 28,
			Component.translatable("gui.back"), button -> onClose()));
		if (selectedTab == Tab.HUD) {
			addHudControls(sidebar);
		} else if (selectedTab == Tab.CHAT) {
			addChatControls(sidebar);
		} else if (selectedTab == Tab.HITBOXES) {
			addHitboxControls(sidebar);
		} else if (selectedTab == Tab.VISUALS) {
			addVisualControls(sidebar);
		} else if (selectedTab == Tab.MODULES) {
			addModuleControls(sidebar);
		}
	}

	private void addVisualControls(int sidebarWidth) {
		int contentLeft = left + sidebarWidth + 24;
		int contentRight = right - 24;
		var visuals = BlockeraCoreServices.visuals();
		var config = visuals.config();
		addRenderableWidget(new BlockeraButton(contentRight - 184, top + 92, 88, 24,
			Component.translatable(config.crosshairEnabled()
				? "blockera.state.enabled" : "blockera.state.disabled"), button -> {
				visuals.toggleCrosshair();
				rebuildWidgets();
			}, config.crosshairEnabled()));
		addRenderableWidget(new BlockeraButton(contentRight - 90, top + 92, 90, 24,
			Component.translatable("blockera.visual.color"), button -> {
				visuals.cycleCrosshairColor();
				rebuildWidgets();
			}));
		addMinusPlus(contentRight, top + 159,
			() -> visuals.adjustCrosshairSize(-1), () -> visuals.adjustCrosshairSize(1));
		addMinusPlus(contentRight, top + 226,
			() -> visuals.adjustCrosshairGap(-1), () -> visuals.adjustCrosshairGap(1));
		addMinusPlus(contentRight, top + 293,
			() -> visuals.adjustCrosshairThickness(-1), () -> visuals.adjustCrosshairThickness(1));
		addRenderableWidget(new BlockeraButton(contentRight - 184, top + 360, 88, 24,
			Component.translatable(config.hitColorEnabled()
				? "blockera.state.enabled" : "blockera.state.disabled"), button -> {
				visuals.toggleHitColor();
				rebuildWidgets();
			}, config.hitColorEnabled()));
		addRenderableWidget(new BlockeraButton(contentRight - 90, top + 360, 90, 24,
			Component.translatable("blockera.visual.color"), button -> {
				visuals.cycleHitColor();
				rebuildWidgets();
			}));
	}

	private void addMinusPlus(int contentRight, int y, Runnable decrease, Runnable increase) {
		addRenderableWidget(new BlockeraButton(contentRight - 88, y, 40, 24,
			Component.literal("−"), button -> {
				decrease.run();
				rebuildWidgets();
			}));
		addRenderableWidget(new BlockeraButton(contentRight - 42, y, 40, 24,
			Component.literal("+"), button -> {
				increase.run();
				rebuildWidgets();
			}));
	}

	private void addModuleControls(int sidebarWidth) {
		int contentLeft = left + sidebarWidth + 24;
		int contentRight = right - 24;
		moduleSearch = new EditBox(font, contentLeft, top + 72,
			contentRight - contentLeft - 132, ThemeTokens.CONTROL_HEIGHT,
			Component.translatable("blockera.control.search"));
		moduleSearch.setHint(Component.translatable("blockera.control.search"));
		moduleSearch.setValue(moduleQuery);
		moduleSearch.setResponder(value -> moduleQuery = value);
		addRenderableWidget(moduleSearch);
		addRenderableWidget(new BlockeraButton(contentRight - 124, top + 72, 124,
			ThemeTokens.CONTROL_HEIGHT,
			Component.translatable(BlockeraCoreServices.visualsEnabled()
				? "blockera.control.disable_all" : "blockera.control.enable_all"),
			button -> {
				BlockeraCoreServices.setVisualsEnabled(!BlockeraCoreServices.visualsEnabled());
				rebuildWidgets();
			}, true));
		addRenderableWidget(new BlockeraButton(contentLeft, top + 310, 120, 24,
			Component.translatable("blockera.control.hud"), button -> selectTab(Tab.HUD)));
		addRenderableWidget(new BlockeraButton(contentLeft + 128, top + 310, 120, 24,
			Component.translatable("blockera.control.chat"), button -> selectTab(Tab.CHAT)));
		addRenderableWidget(new BlockeraButton(contentLeft + 256, top + 310, 120, 24,
			Component.translatable("blockera.control.hitboxes"), button -> selectTab(Tab.HITBOXES)));
	}

	private void addChatControls(int sidebarWidth) {
		int contentLeft = left + sidebarWidth + 24;
		int contentRight = right - 24;
		addRenderableWidget(new BlockeraButton(contentLeft, top + 286, contentRight - contentLeft, 30,
			Component.translatable("blockera.chat.settings.open"),
			button -> minecraft.setScreen(new BlockeraChatSettingsScreen(this)), true));
	}

	private void addHudControls(int sidebarWidth) {
		int contentLeft = left + sidebarWidth + 24;
		int contentRight = right - 24;
		addRenderableWidget(new BlockeraButton(contentLeft, top + 286, contentRight - contentLeft, 30,
			Component.translatable("blockera.hud.editor.open"),
			button -> minecraft.setScreen(new BlockeraHudEditorScreen(this, BlockeraCoreServices.hudLayouts())), true));
	}

	private void addTabButton(int x, int y, int width, Tab tab, String key) {
		addRenderableWidget(new BlockeraButton(x, y, width, 30, Component.translatable(key),
			button -> selectTab(tab), selectedTab == tab));
	}

	private void addHitboxControls(int sidebarWidth) {
		int contentRight = right - 24;
		int y = top + 92;
		for (HitboxCategory category : HitboxCategory.values()) {
			int controlsLeft = contentRight - 296;
			addRenderableWidget(new BlockeraButton(controlsLeft, y, 88, 24,
				hitboxEnabledLabel(category), button -> {
					BlockeraCoreServices.hitboxes().toggle(category);
					rebuildWidgets();
				}, BlockeraCoreServices.hitboxes().config().style(category).enabled()));
			addRenderableWidget(new BlockeraButton(controlsLeft + 94, y, 72, 24,
				Component.translatable("blockera.hitbox.color"), button -> {
					BlockeraCoreServices.hitboxes().cycleColor(category);
					rebuildWidgets();
				}));
			addRenderableWidget(new BlockeraButton(controlsLeft + 172, y, 38, 24,
				Component.literal("−"), button -> BlockeraCoreServices.hitboxes().adjustOpacity(category, -0.1F)));
			addRenderableWidget(new BlockeraButton(controlsLeft + 216, y, 38, 24,
				Component.literal("+"), button -> BlockeraCoreServices.hitboxes().adjustOpacity(category, 0.1F)));
			y += 62;
		}
		addRenderableWidget(new BlockeraButton(contentRight - 88, top + 286, 38, 24,
			Component.literal("−"), button -> BlockeraCoreServices.hitboxes().adjustLineWidth(-0.25F)));
		addRenderableWidget(new BlockeraButton(contentRight - 44, top + 286, 38, 24,
			Component.literal("+"), button -> BlockeraCoreServices.hitboxes().adjustLineWidth(0.25F)));
	}

	private void selectTab(Tab tab) {
		if (selectedTab != tab) {
			selectedTab = tab;
			rebuildWidgets();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
		BlockeraDraw.panel(graphics, left, top, right, bottom);
		int sidebarRight = left + Math.min(190, (right - left) / 4);
		graphics.fill(sidebarRight, top, sidebarRight + 1, bottom, ThemeTokens.BORDER);
		UiText.drawSemibold(graphics, Component.literal("BLOCKERA"), left + 18, top + 20, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.translatable("blockera.client.version"), left + 18, top + 38,
			ThemeTokens.MUTED);
		int contentLeft = sidebarRight + 24;
		UiText.drawSemibold(graphics, Component.translatable("blockera.control.title"), contentLeft, top + 22,
			ThemeTokens.TEXT);
		UiText.draw(graphics, Component.translatable("blockera.control.subtitle"), contentLeft, top + 41,
			ThemeTokens.MUTED);
			switch (selectedTab) {
			case OVERVIEW -> renderOverview(graphics, contentLeft);
			case HUD -> renderHud(graphics, contentLeft);
			case CHAT -> renderChat(graphics, contentLeft);
			case HITBOXES -> renderHitboxes(graphics, contentLeft);
			case VISUALS -> renderVisuals(graphics, contentLeft);
			case MODULES -> renderModules(graphics, contentLeft);
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderVisuals(GuiGraphics graphics, int contentLeft) {
		var config = BlockeraCoreServices.visuals().config();
		drawFeature(graphics, contentLeft, top + 78, right - 24,
			Component.translatable("blockera.visual.crosshair"),
			Component.literal(config.crosshairColor()),
			config.crosshairEnabled() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
		drawFeature(graphics, contentLeft, top + 145, right - 24,
			Component.translatable("blockera.visual.crosshair_size"),
			Component.literal(Integer.toString(config.crosshairSize())), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 212, right - 24,
			Component.translatable("blockera.visual.crosshair_gap"),
			Component.literal(Integer.toString(config.crosshairGap())), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 279, right - 24,
			Component.translatable("blockera.visual.crosshair_thickness"),
			Component.literal(Integer.toString(config.crosshairThickness())), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 346, right - 24,
			Component.translatable("blockera.visual.hit_color"),
			Component.literal(config.hitColor()),
			config.hitColorEnabled() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
	}

	private void renderChat(GuiGraphics graphics, int contentLeft) {
		var chat = BlockeraCoreServices.chat().config();
		drawFeature(graphics, contentLeft, top + 78, right - 24,
			Component.translatable("blockera.chat.filters"),
			Component.translatable("blockera.chat.filter.count", chat.filters().size()), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 145, right - 24,
			Component.translatable("blockera.chat.layout"),
			Component.literal(chat.width() + " × " + chat.focusedHeight()), ThemeTokens.SUCCESS);
		drawFeature(graphics, contentLeft, top + 212, right - 24,
			Component.translatable("blockera.chat.history"),
			Component.translatable("blockera.chat.history.unlimited"), ThemeTokens.ACCENT);
	}

	private void renderOverview(GuiGraphics graphics, int contentLeft) {
		drawFeature(graphics, contentLeft, top + 78, right - 24,
			Component.translatable("blockera.control.runtime"),
			Component.translatable("blockera.control.runtime_value"), ThemeTokens.SUCCESS);
		drawFeature(graphics, contentLeft, top + 145, right - 24,
			Component.translatable("blockera.control.hud"),
			Component.translatable("blockera.core.hud_value"), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 212, right - 24,
			Component.translatable("blockera.control.modules"),
			Component.translatable("blockera.core.public_value"), ThemeTokens.MUTED);
	}

	private void renderHud(GuiGraphics graphics, int contentLeft) {
		var layouts = BlockeraCoreServices.hudLayouts();
		drawFeature(graphics, contentLeft, top + 78, right - 24,
			Component.translatable("blockera.hud.profile"),
			Component.literal(layouts.activeProfile()), ThemeTokens.ACCENT);
		drawFeature(graphics, contentLeft, top + 145, right - 24,
			Component.translatable("blockera.widget.fps"),
			Component.translatable(layouts.settings("blockera:fps").enabled
				? "blockera.state.enabled" : "blockera.state.disabled"), ThemeTokens.SUCCESS);
		UiText.draw(graphics, Component.translatable("blockera.hud.editor_in_progress"), contentLeft, top + 224,
			ThemeTokens.MUTED);
	}

	private void renderHitboxes(GuiGraphics graphics, int contentLeft) {
		int y = top + 78;
		for (HitboxCategory category : HitboxCategory.values()) {
			HitboxStyle style = BlockeraCoreServices.hitboxes().config().style(category);
			drawFeature(graphics, contentLeft, y, right - 24,
				Component.translatable("blockera.hitbox." + category.name().toLowerCase(Locale.ROOT)),
				Component.literal(style.color() + " · " + Math.round(style.opacity() * 100) + "%"),
				style.enabled() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
			y += 62;
		}
		drawFeature(graphics, contentLeft, top + 264, right - 24,
			Component.translatable("blockera.hitbox.line_width"),
			Component.literal(String.format(Locale.ROOT, "%.2f px",
				BlockeraCoreServices.hitboxes().config().lineWidth())), ThemeTokens.ACCENT);
	}

	private void renderModules(GuiGraphics graphics, int contentLeft) {
		int y = top + 112;
		String query = moduleQuery.trim().toLowerCase(Locale.ROOT);
		for (Component module : new Component[] {
			Component.translatable("blockera.control.hud"),
			Component.translatable("blockera.control.chat"),
			Component.translatable("blockera.control.hitboxes")
		}) {
			if (!query.isEmpty() && !module.getString().toLowerCase(Locale.ROOT).contains(query)) continue;
			drawFeature(graphics, contentLeft, y, right - 24, module,
				Component.translatable("blockera.module.builtin"),
				BlockeraCoreServices.visualsEnabled() ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
			y += 60;
		}
	}

	private Component hitboxEnabledLabel(HitboxCategory category) {
		return Component.translatable(BlockeraCoreServices.hitboxes().config().style(category).enabled()
			? "blockera.state.enabled" : "blockera.state.disabled");
	}

	private void drawFeature(GuiGraphics graphics, int cardLeft, int cardTop, int cardRight,
		Component title, Component value, int valueColor) {
		BlockeraDraw.card(graphics, cardLeft, cardTop, cardRight, cardTop + 52, false);
		UiText.drawSemibold(graphics, title, cardLeft + 12, cardTop + 10, ThemeTokens.TEXT);
		UiText.draw(graphics, value, cardLeft + 12, cardTop + 31, valueColor);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return parent != null && parent.isPauseScreen();
	}
}
