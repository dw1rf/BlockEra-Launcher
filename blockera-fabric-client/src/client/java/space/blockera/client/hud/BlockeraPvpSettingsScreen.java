package space.blockera.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.client.ui.BlockeraButton;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

/** Focused settings for the fair-play PvP HUD. */
public final class BlockeraPvpSettingsScreen extends Screen {
	private static final String PVP_ID = "blockera:pvp_hud";
	private final Screen parent;
	private final HudLayoutStore layouts;
	private int left;
	private int top;
	private int right;
	private int bottom;

	public BlockeraPvpSettingsScreen(Screen parent, HudLayoutStore layouts) {
		super(Component.translatable("blockera.pvp.title"));
		this.parent = parent;
		this.layouts = layouts;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(650, width - 28);
		int panelHeight = Math.min(430, height - 28);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		int buttonX = right - 142;
		int y = top + 76;
		addToggle(buttonX, y, null);
		addToggle(buttonX, y + 48, "show_model");
		addToggle(buttonX, y + 96, "show_health_bar");
		addToggle(buttonX, y + 144, "show_armor");
		addToggle(buttonX, y + 192, "show_cps");
		addToggle(buttonX, y + 240, "show_combo");
		addRenderableWidget(new BlockeraButton(left + 18, bottom - 44, 176, 28,
			Component.translatable("blockera.hud.editor.open"),
			button -> minecraft.setScreen(new BlockeraHudEditorScreen(this, layouts)), true));
		addRenderableWidget(new BlockeraButton(right - 122, bottom - 44, 104, 28,
			Component.translatable("gui.done"), button -> onClose(), true));
	}

	private void addToggle(int x, int y, String option) {
		boolean enabled = option == null ? settings().enabled : option(option);
		addRenderableWidget(new BlockeraButton(x, y, 124, 26,
			Component.translatable(enabled ? "blockera.state.enabled" : "blockera.state.disabled"),
			button -> {
				if (option == null) settings().enabled = !settings().enabled;
				else settings().setBooleanOption(PVP_ID, option, !option(option));
				layouts.save();
				rebuildWidgets();
			}, enabled));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
		BlockeraDraw.panel(graphics, left, top, right, bottom);
		UiText.drawSemibold(graphics, getTitle(), left + 20, top + 18, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.translatable("blockera.pvp.subtitle"), left + 20, top + 38,
			ThemeTokens.MUTED);
		int cardRight = right - 18;
		int y = top + 70;
		drawOption(graphics, y, "blockera.pvp.enabled", settings().enabled);
		drawOption(graphics, y + 48, "blockera.pvp.model", option("show_model"));
		drawOption(graphics, y + 96, "blockera.pvp.health", option("show_health_bar"));
		drawOption(graphics, y + 144, "blockera.pvp.armor", option("show_armor"));
		drawOption(graphics, y + 192, "blockera.pvp.cps", option("show_cps"));
		drawOption(graphics, y + 240, "blockera.pvp.combo", option("show_combo"));
		graphics.fill(left + 18, bottom - 57, cardRight, bottom - 56, ThemeTokens.BORDER);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawOption(GuiGraphics graphics, int y, String key, boolean enabled) {
		BlockeraDraw.roundedRect(graphics, left + 18, y, right - 18, y + 36,
			ThemeTokens.RADIUS, enabled ? 0xC6283339 : ThemeTokens.CARD);
		UiText.drawSemibold(graphics, Component.translatable(key), left + 30, y + 13,
			enabled ? ThemeTokens.TEXT : ThemeTokens.MUTED);
	}

	private boolean option(String key) {
		return settings().booleanOption(PVP_ID, key, true);
	}

	private HudWidgetSettings settings() {
		return layouts.settings(PVP_ID);
	}

	@Override
	public void onClose() {
		layouts.save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return parent != null && parent.isPauseScreen();
	}
}
