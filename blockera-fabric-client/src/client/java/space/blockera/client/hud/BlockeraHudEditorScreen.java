package space.blockera.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import space.blockera.client.hud.editor.HudEditorLayout;
import space.blockera.client.hud.editor.HudPreviewTransform;
import space.blockera.client.hud.editor.HudRect;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Three-panel, virtual-coordinate HUD editor shared by every public Blockera Core build. */
public final class BlockeraHudEditorScreen extends Screen {
	private static final int ROW_HEIGHT = 30;
	private static final int ROW_GAP = 5;
	private static final int GRID = 8;
	private static final int RESIZE_HANDLE = 10;
	private final Screen parent;
	private final HudLayoutStore layouts;
	private HudEditorLayout editor;
	private HudPreviewTransform preview;
	private String selectedId = "blockera:fps";
	private int libraryScroll;
	private Mode mode = Mode.NONE;
	private double dragOffsetX;
	private double dragOffsetY;
	private float resizeStartScale;
	private double resizeStartDistance;

	private enum Mode { NONE, DRAG, RESIZE }

	public BlockeraHudEditorScreen(Screen parent, HudLayoutStore layouts) {
		super(Component.translatable("blockera.hud.editor.title"));
		this.parent = parent;
		this.layouts = layouts;
	}

	@Override
	protected void init() {
		editor = HudEditorLayout.calculate(width, height);
		preview = HudPreviewTransform.fit(editor.viewport(), width, height);
		repairEnabledWidgetPositions();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
		renderHeader(graphics);
		renderLibrary(graphics, mouseX, mouseY);
		renderViewport(graphics);
		renderInspector(graphics, mouseX, mouseY);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void renderHeader(GuiGraphics graphics) {
		HudRect header = editor.header();
		graphics.fill(header.x(), header.y(), header.right(), header.bottom(), ThemeTokens.PANEL);
		graphics.fill(0, header.bottom() - 1, width, header.bottom(), ThemeTokens.BORDER);
		UiText.drawSemibold(graphics, Component.translatable("blockera.hud.editor.title"), 16, 17, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.literal(layouts.activeProfile()), 190, 19, ThemeTokens.ACCENT);
		button(graphics, width - 188, 13, 78, 30, Component.translatable("blockera.hud.editor.reset"), false);
		button(graphics, width - 102, 13, 86, 30, Component.translatable("gui.done"), true);
	}

	private void renderLibrary(GuiGraphics graphics, int mouseX, int mouseY) {
		HudRect area = editor.library();
		BlockeraDraw.panel(graphics, area.x(), area.y(), area.right(), area.bottom());
		UiText.drawSemibold(graphics, Component.translatable("blockera.hud.editor.library"), area.x() + 12,
			area.y() + 12, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.translatable("blockera.hud.editor.library_hint"), area.x() + 12,
			area.y() + 29, ThemeTokens.MUTED);
		graphics.enableScissor(area.x() + 5, area.y() + 48, area.right() - 5, area.bottom() - 6);
		List<HudWidgetMetadata> widgets = BuiltinHudCatalog.widgets();
		int y = area.y() + 52 - libraryScroll;
		for (HudWidgetMetadata widget : widgets) {
			if (y + ROW_HEIGHT >= area.y() + 48 && y <= area.bottom()) {
				HudWidgetSettings settings = layouts.settings(widget.id());
				boolean selected = widget.id().equals(selectedId);
				int fill = selected ? ThemeTokens.SELECTION : ThemeTokens.CARD;
				BlockeraDraw.roundedRect(graphics, area.x() + 8, y, area.right() - 8, y + ROW_HEIGHT,
					ThemeTokens.RADIUS, fill);
				graphics.fill(area.x() + 8, y, area.x() + 11, y + ROW_HEIGHT,
					settings.enabled ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
				UiText.draw(graphics, Component.literal(displayName(widget.id())), area.x() + 18, y + 10,
					selected ? ThemeTokens.TEXT : ThemeTokens.MUTED);
				UiText.drawSemibold(graphics, Component.literal(settings.enabled ? "●" : "○"),
					area.right() - 25, y + 10, settings.enabled ? ThemeTokens.SUCCESS : ThemeTokens.MUTED);
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();
	}

	private void renderViewport(GuiGraphics graphics) {
		HudRect area = editor.viewport();
		BlockeraDraw.panel(graphics, area.x(), area.y(), area.right(), area.bottom());
		HudRect content = preview.contentBounds();
		graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
		graphics.fill(content.x(), content.y(), content.right(), content.bottom(), ThemeTokens.PANEL_SOFT);
		renderGrid(graphics, content);
		for (HudWidgetMetadata widget : BuiltinHudCatalog.widgets()) {
			HudWidgetSettings settings = layouts.settings(widget.id());
			if (!settings.enabled) {
				continue;
			}
			renderPreviewWidget(graphics, widget.id(), settings, widget.id().equals(selectedId));
		}
		graphics.disableScissor();
	}

	private void renderGrid(GuiGraphics graphics, HudRect content) {
		int step = Math.max(4, (int) Math.round(GRID * preview.scale()));
		for (int x = content.x(); x < content.right(); x += step) {
			graphics.fill(x, content.y(), x + 1, content.bottom(), 0x18FFFFFF);
		}
		for (int y = content.y(); y < content.bottom(); y += step) {
			graphics.fill(content.x(), y, content.right(), y + 1, 0x18FFFFFF);
		}
		int centerX = content.x() + content.width() / 2;
		int centerY = content.y() + content.height() / 2;
		graphics.fill(centerX, content.y(), centerX + 1, content.bottom(), 0x70168ED1);
		graphics.fill(content.x(), centerY, content.right(), centerY + 1, 0x70168ED1);
	}

	private void renderPreviewWidget(GuiGraphics graphics, String id, HudWidgetSettings settings, boolean selected) {
		int widgetWidth = virtualWidth(id);
		int widgetHeight = virtualHeight(id);
		int scaledWidth = Math.round(widgetWidth * settings.scale);
		int scaledHeight = Math.round(widgetHeight * settings.scale);
		HudPoint point = settings.anchor.resolve(width, height, scaledWidth, scaledHeight,
			settings.offsetX, settings.offsetY);
		var draw = preview.toPreview(point.x(), point.y());
		float scale = (float) (preview.scale() * settings.scale);
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) draw.x(), (float) draw.y());
		graphics.pose().scale(scale, scale);
		int background = settings.background ? alpha(ThemeTokens.PANEL, settings.opacity) : 0x30333538;
		graphics.fill(0, 0, widgetWidth, widgetHeight, background);
		graphics.fill(0, 0, 2, widgetHeight, selected ? ThemeTokens.ACCENT : ThemeTokens.BORDER);
		UiText.drawSemibold(graphics, Component.literal(displayName(id)), 7, 6,
			alpha(ThemeTokens.MUTED, settings.opacity));
		UiText.draw(graphics, Component.literal(previewValue(id)), 7, Math.min(widgetHeight - 12, 20),
			alpha(ThemeTokens.TEXT, settings.opacity));
		if (selected) {
			graphics.renderOutline(0, 0, widgetWidth, widgetHeight, ThemeTokens.ACCENT);
			graphics.fill(widgetWidth - RESIZE_HANDLE, widgetHeight - 2, widgetWidth, widgetHeight,
				ThemeTokens.ACCENT);
			graphics.fill(widgetWidth - 2, widgetHeight - RESIZE_HANDLE, widgetWidth, widgetHeight,
				ThemeTokens.ACCENT);
		}
		graphics.pose().popMatrix();
		if (id.equals("blockera:player_model") && minecraft.player != null) {
			int left = (int) Math.round(draw.x());
			int top = (int) Math.round(draw.y());
			int right = left + Math.max(1, Math.round(scaledWidth * (float) preview.scale()));
			int bottom = top + Math.max(1, Math.round(scaledHeight * (float) preview.scale()));
			int modelScale = Math.max(10, Math.round(42.0F * scale));
			InventoryScreen.renderEntityInInventoryFollowsMouse(
				graphics, left, top, right, bottom, modelScale, 0.0F,
				(left + right) / 2.0F, (top + bottom) / 2.0F, minecraft.player
			);
		}
	}

	private void renderInspector(GuiGraphics graphics, int mouseX, int mouseY) {
		HudRect area = editor.inspector();
		BlockeraDraw.panel(graphics, area.x(), area.y(), area.right(), area.bottom());
		HudWidgetSettings settings = layouts.settings(selectedId);
		int x = area.x() + 14;
		int contentWidth = area.width() - 28;
		UiText.drawSemibold(graphics, Component.literal(displayName(selectedId)), x, area.y() + 14, ThemeTokens.TEXT);
		UiText.draw(graphics, Component.literal(BuiltinHudCatalog.require(selectedId).category().name()), x,
			area.y() + 32, ThemeTokens.MUTED);
		int y = area.y() + 58;
		property(graphics, x, y, contentWidth, Component.translatable("blockera.hud.editor.visible"),
			Component.translatable(settings.enabled ? "blockera.state.enabled" : "blockera.state.disabled"));
		property(graphics, x, y + 50, contentWidth, Component.translatable("blockera.hud.editor.anchor"),
			Component.literal(settings.anchor.name()));
		property(graphics, x, y + 100, contentWidth, Component.translatable("blockera.hud.editor.scale"),
			Component.literal(Math.round(settings.scale * 100) + "%"));
		button(graphics, x, y + 144, 42, 26, Component.literal("−"), false);
		button(graphics, x + contentWidth - 42, y + 144, 42, 26, Component.literal("+"), false);
		property(graphics, x, y + 180, contentWidth, Component.translatable("blockera.hud.editor.opacity"),
			Component.literal(Math.round(settings.opacity * 100) + "%"));
		button(graphics, x, y + 224, 42, 26, Component.literal("−"), false);
		button(graphics, x + contentWidth - 42, y + 224, 42, 26, Component.literal("+"), false);
		button(graphics, x, area.bottom() - 78, contentWidth, 28,
			Component.translatable(settings.background ? "blockera.hud.editor.background_on"
				: "blockera.hud.editor.background_off"), false);
		button(graphics, x, area.bottom() - 42, contentWidth, 28,
			Component.translatable("blockera.hud.editor.remove"), false);
	}

	private void property(GuiGraphics graphics, int x, int y, int width, Component label, Component value) {
		BlockeraDraw.roundedRect(graphics, x, y, x + width, y + 40, ThemeTokens.RADIUS, ThemeTokens.CARD);
		UiText.drawSemibold(graphics, label, x + 9, y + 8, ThemeTokens.TEXT);
		UiText.draw(graphics, value, x + 9, y + 24, ThemeTokens.MUTED);
	}

	private void button(GuiGraphics graphics, int x, int y, int width, int height, Component label, boolean accent) {
		BlockeraDraw.roundedBorder(graphics, x, y, x + width, y + height, ThemeTokens.RADIUS,
			accent ? ThemeTokens.ACCENT_HOVER : ThemeTokens.BORDER,
			accent ? ThemeTokens.ACCENT : ThemeTokens.CARD);
		int textX = x + Math.max(5, (width - minecraft.font.width(UiText.regular(label))) / 2);
		UiText.draw(graphics, label, textX, y + (height - 9) / 2, accent ? 0xFFFFFFFF : ThemeTokens.TEXT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() == 0) {
			if (inside(mouseX, mouseY, width - 102, 13, 86, 30)) {
				onClose();
				return true;
			}
			if (inside(mouseX, mouseY, width - 188, 13, 78, 30)) {
				layouts.resetActiveProfile();
				return true;
			}
			if (selectLibraryRow(mouseX, mouseY)) {
				return true;
			}
			if (handleInspectorClick(mouseX, mouseY)) {
				return true;
			}
			String hit = hitResizeHandle(mouseX, mouseY);
			if (hit != null) {
				selectedId = hit;
				HudPoint point = widgetPoint(hit);
				var screen = preview.toScreen(mouseX, mouseY);
				resizeStartScale = layouts.settings(hit).scale;
				resizeStartDistance = Math.max(1.0D,
					Math.hypot(screen.x() - point.x(), screen.y() - point.y()));
				mode = Mode.RESIZE;
				return true;
			}
			hit = hitWidget(mouseX, mouseY);
			if (hit != null) {
				selectedId = hit;
				HudPoint point = widgetPoint(hit);
				var screen = preview.toScreen(mouseX, mouseY);
				dragOffsetX = screen.x() - point.x();
				dragOffsetY = screen.y() - point.y();
				mode = Mode.DRAG;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (mode == Mode.NONE || event.button() != 0) {
			return super.mouseDragged(event, deltaX, deltaY);
		}
		var screen = preview.toScreen(event.x(), event.y());
		HudWidgetSettings settings = layouts.settings(selectedId);
		if (mode == Mode.RESIZE) {
			HudPoint point = widgetPoint(selectedId);
			double distance = Math.max(1.0D, Math.hypot(screen.x() - point.x(), screen.y() - point.y()));
			settings.scale = clamp((float) (resizeStartScale * distance / resizeStartDistance), 0.5F, 2.0F);
			settings.scale = Math.round(settings.scale * 20.0F) / 20.0F;
			moveWidgetTo(settings, point.x(), point.y());
		} else {
			int widgetWidth = Math.round(virtualWidth(selectedId) * settings.scale);
			int widgetHeight = Math.round(virtualHeight(selectedId) * settings.scale);
			int desiredX = clamp((int) Math.round(screen.x() - dragOffsetX), 0, width - widgetWidth);
			int desiredY = clamp((int) Math.round(screen.y() - dragOffsetY), 0, height - widgetHeight);
			moveWidgetTo(settings, snap(desiredX), snap(desiredY));
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (mode != Mode.NONE && event.button() == 0) {
			mode = Mode.NONE;
			layouts.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (editor.library().contains(mouseX, mouseY)) {
			int content = BuiltinHudCatalog.widgets().size() * (ROW_HEIGHT + ROW_GAP);
			int visible = editor.library().height() - 58;
			libraryScroll = clamp(libraryScroll - (int) Math.round(verticalAmount * 28), 0,
				Math.max(0, content - visible));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private boolean selectLibraryRow(double mouseX, double mouseY) {
		HudRect area = editor.library();
		if (!area.contains(mouseX, mouseY) || mouseY < area.y() + 48) {
			return false;
		}
		int index = (int) ((mouseY - (area.y() + 52) + libraryScroll) / (ROW_HEIGHT + ROW_GAP));
		if (index < 0 || index >= BuiltinHudCatalog.widgets().size()) {
			return false;
		}
		HudWidgetMetadata selected = BuiltinHudCatalog.widgets().get(index);
		selectedId = selected.id();
		HudWidgetSettings settings = layouts.settings(selectedId);
		settings.enabled = true;
		layouts.save();
		return true;
	}

	private boolean handleInspectorClick(double mouseX, double mouseY) {
		HudRect area = editor.inspector();
		if (!area.contains(mouseX, mouseY)) {
			return false;
		}
		HudWidgetSettings settings = layouts.settings(selectedId);
		int x = area.x() + 14;
		int contentWidth = area.width() - 28;
		int y = area.y() + 58;
		if (inside(mouseX, mouseY, x, y, contentWidth, 40)) {
			settings.enabled = !settings.enabled;
		} else if (inside(mouseX, mouseY, x, y + 50, contentWidth, 40)) {
			cycleAnchor(settings);
		} else if (inside(mouseX, mouseY, x, y + 144, 42, 26)) {
			settings.scale = clamp(settings.scale - 0.1F, 0.5F, 2.0F);
		} else if (inside(mouseX, mouseY, x + contentWidth - 42, y + 144, 42, 26)) {
			settings.scale = clamp(settings.scale + 0.1F, 0.5F, 2.0F);
		} else if (inside(mouseX, mouseY, x, y + 224, 42, 26)) {
			settings.opacity = clamp(settings.opacity - 0.1F, 0.1F, 1.0F);
		} else if (inside(mouseX, mouseY, x + contentWidth - 42, y + 224, 42, 26)) {
			settings.opacity = clamp(settings.opacity + 0.1F, 0.1F, 1.0F);
		} else if (inside(mouseX, mouseY, x, area.bottom() - 78, contentWidth, 28)) {
			settings.background = !settings.background;
		} else if (inside(mouseX, mouseY, x, area.bottom() - 42, contentWidth, 28)) {
			settings.enabled = false;
		} else {
			return false;
		}
		layouts.save();
		return true;
	}

	private String hitWidget(double mouseX, double mouseY) {
		if (!preview.contentBounds().contains(mouseX, mouseY)) {
			return null;
		}
		var point = preview.toScreen(mouseX, mouseY);
		List<HudWidgetMetadata> reverse = new ArrayList<>(BuiltinHudCatalog.widgets());
		for (int index = reverse.size() - 1; index >= 0; index--) {
			String id = reverse.get(index).id();
			HudWidgetSettings settings = layouts.settings(id);
			if (!settings.enabled) {
				continue;
			}
			HudPoint origin = widgetPoint(id);
			int w = Math.round(virtualWidth(id) * settings.scale);
			int h = Math.round(virtualHeight(id) * settings.scale);
			if (inside(point.x(), point.y(), origin.x(), origin.y(), w, h)) {
				return id;
			}
		}
		return null;
	}

	private String hitResizeHandle(double mouseX, double mouseY) {
		if (!preview.contentBounds().contains(mouseX, mouseY)) return null;
		var point = preview.toScreen(mouseX, mouseY);
		List<HudWidgetMetadata> reverse = new ArrayList<>(BuiltinHudCatalog.widgets());
		for (int index = reverse.size() - 1; index >= 0; index--) {
			String id = reverse.get(index).id();
			HudWidgetSettings settings = layouts.settings(id);
			if (!settings.enabled || !id.equals(selectedId)) continue;
			HudPoint origin = widgetPoint(id);
			int w = Math.round(virtualWidth(id) * settings.scale);
			int h = Math.round(virtualHeight(id) * settings.scale);
			int handle = Math.max(6, Math.round(RESIZE_HANDLE * settings.scale));
			if (inside(point.x(), point.y(), origin.x() + w - handle, origin.y() + h - handle,
				handle, handle)) return id;
		}
		return null;
	}

	private void cycleAnchor(HudWidgetSettings settings) {
		int widgetWidth = Math.round(virtualWidth(selectedId) * settings.scale);
		int widgetHeight = Math.round(virtualHeight(selectedId) * settings.scale);
		HudPoint current = settings.anchor.resolve(width, height, widgetWidth, widgetHeight,
			settings.offsetX, settings.offsetY);
		HudAnchor[] anchors = HudAnchor.values();
		settings.anchor = anchors[(settings.anchor.ordinal() + 1) % anchors.length];
		moveWidgetTo(settings, current.x(), current.y());
	}

	private void repairEnabledWidgetPositions() {
		boolean changed = false;
		for (HudWidgetMetadata widget : BuiltinHudCatalog.widgets()) {
			HudWidgetSettings settings = layouts.settings(widget.id());
			if (!settings.enabled) continue;
			int widgetWidth = Math.round(virtualWidth(widget.id()) * settings.scale);
			int widgetHeight = Math.round(virtualHeight(widget.id()) * settings.scale);
			HudPoint current = settings.anchor.resolve(width, height, widgetWidth, widgetHeight,
				settings.offsetX, settings.offsetY);
			int repairedX = clamp(current.x(), 0, Math.max(0, width - widgetWidth));
			int repairedY = clamp(current.y(), 0, Math.max(0, height - widgetHeight));
			if (repairedX != current.x() || repairedY != current.y()) {
				moveWidgetTo(widget.id(), settings, repairedX, repairedY);
				changed = true;
			}
		}
		if (changed) layouts.save();
	}

	private void moveWidgetTo(HudWidgetSettings settings, int x, int y) {
		moveWidgetTo(selectedId, settings, x, y);
	}

	private void moveWidgetTo(String id, HudWidgetSettings settings, int x, int y) {
		int widgetWidth = Math.round(virtualWidth(id) * settings.scale);
		int widgetHeight = Math.round(virtualHeight(id) * settings.scale);
		HudPoint offsets = settings.anchor.offsetsForPosition(width, height, widgetWidth, widgetHeight, x, y);
		settings.offsetX = offsets.x();
		settings.offsetY = offsets.y();
	}

	private HudPoint widgetPoint(String id) {
		HudWidgetSettings settings = layouts.settings(id);
		return settings.anchor.resolve(width, height,
			Math.round(virtualWidth(id) * settings.scale), Math.round(virtualHeight(id) * settings.scale),
			settings.offsetX, settings.offsetY);
	}

	private static int virtualWidth(String id) {
		return id.equals("blockera:player_model") ? 72 : id.equals("blockera:pvp_hud") ? 180 : 112;
	}

	private static int virtualHeight(String id) {
		return id.equals("blockera:player_model") ? 118 : id.equals("blockera:pvp_hud") ? 74 : 34;
	}

	private static String previewValue(String id) {
		return switch (id) {
			case "blockera:fps" -> "144";
			case "blockera:coordinates" -> "128 64 −256";
			case "blockera:ping" -> "42 ms";
			case "blockera:player_count" -> "18";
			case "blockera:clock", "blockera:real_time" -> "12:45";
			default -> "—";
		};
	}

	private static String displayName(String id) {
		if (BuiltinHudCatalog.isAllowed(id)) {
			return Component.translatable(BuiltinHudCatalog.require(id).translationKey()).getString();
		}
		String value = id.substring(id.indexOf(':') + 1).replace('_', ' ');
		return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
	}

	private static boolean inside(double x, double y, int left, int top, int width, int height) {
		return x >= left && x < left + width && y >= top && y < top + height;
	}

	private static int snap(int value) {
		return Math.round(value / (float) GRID) * GRID;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int alpha(int argb, float opacity) {
		int sourceAlpha = argb >>> 24;
		int resultAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * opacity)));
		return resultAlpha << 24 | argb & 0x00FFFFFF;
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
