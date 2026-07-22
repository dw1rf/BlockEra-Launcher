package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.core.api.Widget;
import space.blockera.core.hud.BuiltinHudWidgets;
import space.blockera.core.hud.BuiltinWidgetIds;
import space.blockera.core.hud.HudAnchor;
import space.blockera.core.hud.HudDataSnapshot;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.HudWidget;
import space.blockera.core.hud.HudWidgetSettings;

/** Full widget customization surface with fixed chrome and a scrollable content viewport. */
public final class BlockeraWidgetSettingsScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final int[] COLORS = {
			0xFFF8F7FF, 0xFFA4A7B3, 0xFF8D68FF, 0xFFEE00F5,
			0xFF59D69A, 0xFFF0C66B, 0xFFE27D8D, 0xFF68B5FF,
			0xFFFFFFFF, 0xFF11131A, 0xFFE53935, 0xFF43A047
	};
	private static final int COLUMN_GAP = 16;
	private final Screen parent;
	private final String widgetId;
	private final Component widgetTitle;
	private final HudLayoutStore store = HudLayoutStore.instance();
	private WidgetSettingsLayout layout;
	private int scrollOffset;
	private ColorTarget colorTarget;

	private enum ColorTarget { TITLE, VALUE }

	private record Rect(int left, int top, int right, int bottom) {
		boolean contains(double x, double y) {
			return x >= left && x < right && y >= top && y < bottom;
		}
	}

	private record ContentGeometry(
			Rect preview, int layoutSectionTop, Rect scale, Rect opacity, Rect anchor, Rect locked,
			int colorsSectionTop, Rect background, Rect labelColor, Rect valueColor,
			int commonSectionTop, Rect showLabel, Rect showIcon, Rect compact, Rect orientation,
			int clockSectionTop, Rect realTime, Rect worldTime, Rect seconds, Rect hourFormat) {
	}

	public BlockeraWidgetSettingsScreen(Screen parent, String widgetId, String titleKey) {
		this(parent, widgetId, Component.translatable(titleKey));
	}

	public BlockeraWidgetSettingsScreen(Screen parent, String widgetId, Component widgetTitle) {
		super(widgetTitle);
		this.parent = parent;
		this.widgetId = widgetId;
		this.widgetTitle = widgetTitle;
	}

	@Override
	protected void init() {
		layout = WidgetSettingsLayout.calculate(width, height, 5 + (isClock() ? 4 : 0));
		scrollOffset = layout.clampScroll(scrollOffset);
		int left = layout.left();
		int top = layout.top();
		int right = layout.right();
		if (layout.compactHeader()) {
			addRenderableWidget(new BlockeraButton(left + 14, top + 9, 70, 24,
					Component.translatable("blockera.common.back"), button -> onClose()));
			int controlsTop = top + 43;
			addRenderableWidget(new BlockeraButton(left + 14, controlsTop, 74, 22,
					stateLabel(), button -> toggleEnabled(button)));
			addRenderableWidget(new BlockeraButton(left + 96, controlsTop, Math.max(70, right - left - 154), 22,
					Component.translatable("blockera.hud.configure"),
					button -> minecraft.setScreen(new HudEditorScreen(this)), true));
			addRenderableWidget(new BlockeraButton(right - 50, controlsTop, 36, 22,
					Component.literal("L"), button -> toggleLocked()));
		} else {
			addRenderableWidget(new BlockeraButton(left + 14, top + 13, 82, 24,
					Component.translatable("blockera.common.back"), button -> onClose()));
			addRenderableWidget(new BlockeraButton(right - 268, top + 13, 74, 24,
					stateLabel(), button -> toggleEnabled(button)));
			addRenderableWidget(new BlockeraButton(right - 186, top + 13, 122, 24,
					Component.translatable("blockera.hud.configure"),
					button -> minecraft.setScreen(new HudEditorScreen(this)), true));
			addRenderableWidget(new BlockeraButton(right - 54, top + 13, 40, 24,
					Component.literal("L"), button -> toggleLocked()));
		}
		addRenderableWidget(new BlockeraButton(left + 14, layout.footerTop() + 10, 110, 24,
				Component.translatable("blockera.common.reset"), button -> resetWidget()));
		addRenderableWidget(new BlockeraButton(right - 114, layout.footerTop() + 10, 100, 24,
				Component.translatable("blockera.common.done"), button -> onClose(), true));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, THEME.menuBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, layout.left(), layout.top(), layout.right(), layout.bottom(),
				14, 0xC40E111A, THEME.borderHoverArgb());
		renderHeader(poseStack);
		UiScissor.enable(layout.left() + 1, layout.contentTop(), layout.right() - 1, layout.contentBottom());
		ContentGeometry geometry = contentGeometry();
		renderPreview(poseStack, geometry.preview());
		renderRows(poseStack, geometry, mouseX, mouseY);
		if (colorTarget != null) renderColorPopover(poseStack, colorPopoverBounds(geometry));
		UiScissor.disable();
		GuiComponent.fill(poseStack, layout.left() + 1, layout.headerBottom() - 1,
				layout.right() - 1, layout.headerBottom(), THEME.borderArgb());
		GuiComponent.fill(poseStack, layout.left() + 1, layout.footerTop(),
				layout.right() - 1, layout.footerTop() + 1, THEME.borderArgb());
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void renderHeader(PoseStack poseStack) {
		int textLeft = layout.left() + (layout.compactHeader() ? 94 : 112);
		int textRight = layout.compactHeader() ? layout.right() - 14 : layout.right() - 280;
		Component title = UiFont.ellipsize(widgetTitle, Math.max(30, textRight - textLeft), true);
		UiFont.drawSemibold(poseStack, title, textLeft, layout.top() + 18, THEME.textPrimaryArgb());
		if (!layout.compactHeader()) {
			Component subtitle = UiFont.ellipsize(Component.translatable("blockera.widget_settings.subtitle"),
					Math.max(30, textRight - textLeft), false);
			UiFont.drawSmall(poseStack, subtitle, textLeft, layout.top() + 32, THEME.textMutedArgb());
		}
	}

	private void renderPreview(PoseStack poseStack, Rect preview) {
		BlockeraDraw.glassPanel(poseStack, preview.left(), preview.top(), preview.right(), preview.bottom(),
				8, 0x96303030, THEME.borderArgb());
		UiFont.draw(poseStack, Component.translatable("blockera.widget_settings.preview"),
				preview.left() + 12, preview.top() + 12, THEME.textPrimaryArgb());
		Widget raw = BuiltinHudWidgets.registry().get(HudLayoutStore.baseWidgetId(widgetId));
		if (!(raw instanceof HudWidget widget)) return;
		HudDataSnapshot data = HudDataSnapshot.preview();
		float scale = settings().scale;
		float previewX = (preview.left() + preview.right() - widget.width(data) * scale) * 0.5F;
		float previewY = (preview.top() + preview.bottom() - widget.height(data) * scale) * 0.5F + 8.0F;
		UiScissor.enable(preview.left() + 1, preview.top() + 1, preview.right() - 1, preview.bottom() - 1);
		poseStack.pushPose();
		poseStack.translate(previewX, previewY, 0);
		poseStack.scale(scale, scale, 1);
		widget.render(poseStack, data, 0, 0, settings(), true);
		poseStack.popPose();
		UiScissor.disable();
	}

	private void renderRows(PoseStack poseStack, ContentGeometry geometry, int mouseX, int mouseY) {
		section(poseStack, "blockera.widget_settings.layout", geometry.layoutSectionTop());
		stepperRow(poseStack, geometry.scale(), "blockera.hud.editor.scale",
				Math.round(settings().scale * 100) + "%", mouseX, mouseY);
		stepperRow(poseStack, geometry.opacity(), "blockera.hud.editor.opacity",
				Math.round(settings().opacity * 100) + "%", mouseX, mouseY);
		row(poseStack, geometry.anchor(), "blockera.widget_settings.anchor",
				Component.translatable("blockera.hud.anchor." + settings().anchor.name().toLowerCase()), mouseX, mouseY);
		row(poseStack, geometry.locked(), "blockera.widget_settings.locked",
				Component.translatable(settings().locked ? "blockera.state.enabled" : "blockera.state.disabled"), mouseX, mouseY);
		section(poseStack, "blockera.widget_settings.colors", geometry.colorsSectionTop());
		row(poseStack, geometry.background(), "blockera.hud.editor.background",
				Component.translatable(settings().background ? "blockera.state.enabled" : "blockera.state.disabled"), mouseX, mouseY);
		colorRow(poseStack, geometry.labelColor(), "blockera.hud.editor.label_color", settings().labelColor, mouseX, mouseY);
		colorRow(poseStack, geometry.valueColor(), "blockera.hud.editor.value_color", settings().valueColor, mouseX, mouseY);
		section(poseStack, "blockera.widget_settings.common", geometry.commonSectionTop());
		row(poseStack, geometry.showLabel(), "blockera.widget_settings.show_label", state(settings().showLabel), mouseX, mouseY);
		row(poseStack, geometry.showIcon(), "blockera.widget_settings.show_icon", state(settings().showIcon), mouseX, mouseY);
		row(poseStack, geometry.compact(), "blockera.widget_settings.compact", state(settings().compact), mouseX, mouseY);
		row(poseStack, geometry.orientation(), "blockera.widget_settings.orientation", Component.literal(settings().orientation), mouseX, mouseY);
		if (isClock()) {
			section(poseStack, "blockera.clock.settings", geometry.clockSectionTop());
			boolean real = settings().clock.showRealTime;
			boolean world = settings().clock.showWorldTime;
			row(poseStack, geometry.realTime(), "blockera.clock.real_time", state(real), mouseX, mouseY);
			row(poseStack, geometry.worldTime(), "blockera.clock.world_time", state(world), mouseX, mouseY);
			row(poseStack, geometry.seconds(), "blockera.clock.seconds", state(settings().clock.showSeconds), mouseX, mouseY);
			row(poseStack, geometry.hourFormat(), "blockera.clock.hour_format",
					Component.literal(settings().clock.use24Hour ? "24h" : "12h"), mouseX, mouseY);
		}
	}

	private void section(PoseStack poseStack, String key, int y) {
		int left = layout.left() + WidgetSettingsLayout.CONTENT_PADDING;
		int right = layout.right() - WidgetSettingsLayout.CONTENT_PADDING;
		UiFont.draw(poseStack, Component.translatable(key), left, y, THEME.textPrimaryArgb());
		GuiComponent.fill(poseStack, left + 88, y + 7, right, y + 8, THEME.borderArgb());
	}

	private void stepperRow(PoseStack poseStack, Rect bounds, String key, String value, int mouseX, int mouseY) {
		row(poseStack, bounds, key, Component.empty(), mouseX, mouseY);
		Rect minus = minusBounds(bounds);
		Rect plus = plusBounds(bounds);
		BlockeraDraw.glassPanel(poseStack, minus.left(), minus.top(), minus.right(), minus.bottom(),
				6, THEME.surfaceElevatedArgb(), THEME.borderArgb());
		BlockeraDraw.glassPanel(poseStack, plus.left(), plus.top(), plus.right(), plus.bottom(),
				6, THEME.surfaceElevatedArgb(), THEME.borderArgb());
		UiFont.drawCentered(poseStack, Component.literal("−"), (minus.left() + minus.right()) * 0.5F,
				minus.top() + 6, THEME.textPrimaryArgb());
		UiFont.drawCentered(poseStack, Component.literal(value), bounds.right() - 76,
				bounds.top() + 10, THEME.textPrimaryArgb());
		UiFont.drawCentered(poseStack, Component.literal("+"), (plus.left() + plus.right()) * 0.5F,
				plus.top() + 6, THEME.textPrimaryArgb());
	}

	private void row(PoseStack poseStack, Rect bounds, String key, Component value, int mouseX, int mouseY) {
		boolean hovered = bounds.contains(mouseX, mouseY);
		BlockeraDraw.glassPanel(poseStack, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 7,
				hovered ? THEME.cardHoverArgb() : THEME.glassCardArgb(), THEME.borderArgb());
		Component label = UiFont.ellipsize(Component.translatable(key), Math.max(24, bounds.right() - bounds.left() - 130), true);
		UiFont.drawSemibold(poseStack, label, bounds.left() + 10, bounds.top() + 9, THEME.textPrimaryArgb());
		if (!value.getString().isEmpty()) {
			int valueWidth = Math.min(140, UiFont.width(value) + 10);
			UiFont.drawSmall(poseStack, value, bounds.right() - valueWidth, bounds.top() + 19, THEME.textMutedArgb());
		}
	}

	private void colorRow(PoseStack poseStack, Rect bounds, String key, String value, int mouseX, int mouseY) {
		row(poseStack, bounds, key, Component.empty(), mouseX, mouseY);
		BlockeraDraw.roundedRect(poseStack, bounds.right() - 56, bounds.top() + 9,
				bounds.right() - 18, bounds.top() + 25, 6, parseColor(value));
	}

	private void renderColorPopover(PoseStack poseStack, Rect popover) {
		BlockeraDraw.glassPanel(poseStack, popover.left(), popover.top(), popover.right(), popover.bottom(),
				8, THEME.surfaceElevatedArgb(), THEME.borderHoverArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.widget_settings.pick_color"),
				popover.left() + 10, popover.top() + 9, THEME.textPrimaryArgb());
		for (int index = 0; index < COLORS.length; index++) {
			Rect swatch = colorSwatch(popover, index);
			BlockeraDraw.roundedRect(poseStack, swatch.left(), swatch.top(), swatch.right(), swatch.bottom(),
					5, COLORS[index]);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseY >= layout.contentTop() && mouseY < layout.contentBottom()
				&& handleContentClick(mouseX, mouseY)) return true;
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean handleContentClick(double mouseX, double mouseY) {
		ContentGeometry geometry = contentGeometry();
		if (colorTarget != null) return pickColor(mouseX, mouseY, colorPopoverBounds(geometry));
		if (handleStepperClick(mouseX, mouseY, geometry.scale(), true)) return true;
		if (handleStepperClick(mouseX, mouseY, geometry.opacity(), false)) return true;
		if (geometry.anchor().contains(mouseX, mouseY)) { cycleAnchor(); return true; }
		if (geometry.locked().contains(mouseX, mouseY)) { toggleLocked(); return true; }
		if (geometry.background().contains(mouseX, mouseY)) {
			settings().background = !settings().background;
			save();
			return true;
		}
		if (geometry.labelColor().contains(mouseX, mouseY)) { colorTarget = ColorTarget.TITLE; return true; }
		if (geometry.valueColor().contains(mouseX, mouseY)) { colorTarget = ColorTarget.VALUE; return true; }
		if (geometry.showLabel().contains(mouseX, mouseY)) { settings().showLabel = !settings().showLabel; save(); return true; }
		if (geometry.showIcon().contains(mouseX, mouseY)) { settings().showIcon = !settings().showIcon; save(); return true; }
		if (geometry.compact().contains(mouseX, mouseY)) { settings().compact = !settings().compact; save(); return true; }
		if (geometry.orientation().contains(mouseX, mouseY)) {
			settings().orientation = settings().orientation.equals("horizontal") ? "vertical" : "horizontal"; save(); return true;
		}
		if (isClock()) {
			if (geometry.realTime().contains(mouseX, mouseY)) {
				if (settings().clock.showWorldTime) settings().clock.showRealTime = !settings().clock.showRealTime;
				save(); return true;
			}
			if (geometry.worldTime().contains(mouseX, mouseY)) {
				if (settings().clock.showRealTime) settings().clock.showWorldTime = !settings().clock.showWorldTime;
				save(); return true;
			}
			if (geometry.seconds().contains(mouseX, mouseY)) {
				settings().clock.showSeconds = !settings().clock.showSeconds; save(); return true;
			}
			if (geometry.hourFormat().contains(mouseX, mouseY)) {
				settings().clock.use24Hour = !settings().clock.use24Hour; save(); return true;
			}
		}
		return false;
	}

	private boolean handleStepperClick(double mouseX, double mouseY, Rect row, boolean scale) {
		if (minusBounds(row).contains(mouseX, mouseY)) {
			if (scale) changeScale(-0.05F); else changeOpacity(-5);
			return true;
		}
		if (plusBounds(row).contains(mouseX, mouseY)) {
			if (scale) changeScale(0.05F); else changeOpacity(5);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (mouseX >= layout.left() && mouseX < layout.right()
				&& mouseY >= layout.contentTop() && mouseY < layout.contentBottom() && layout.maxScroll() > 0) {
			scrollOffset = layout.clampScroll(scrollOffset - (int) Math.round(delta * 30));
			colorTarget = null;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	private ContentGeometry contentGeometry() {
		int left = layout.left() + WidgetSettingsLayout.CONTENT_PADDING;
		int right = layout.right() - WidgetSettingsLayout.CONTENT_PADDING;
		int y = layout.contentTop() + WidgetSettingsLayout.CONTENT_PADDING - scrollOffset;
		Rect preview = new Rect(left, y, right, y + WidgetSettingsLayout.PREVIEW_HEIGHT);
		int sectionTop = preview.bottom() + WidgetSettingsLayout.SECTION_GAP;
		int firstRow = sectionTop + WidgetSettingsLayout.SECTION_LABEL_HEIGHT;
		Rect scale;
		Rect opacity;
		Rect anchor;
		Rect locked;
		int colorsTop;
		if (layout.columns() == 2) {
			int columnWidth = (right - left - COLUMN_GAP) / 2;
			int secondLeft = left + columnWidth + COLUMN_GAP;
			scale = rowRect(left, firstRow, left + columnWidth);
			anchor = rowRect(secondLeft, firstRow, right);
			int secondRow = firstRow + WidgetSettingsLayout.ROW_HEIGHT + WidgetSettingsLayout.ROW_GAP;
			opacity = rowRect(left, secondRow, left + columnWidth);
			locked = rowRect(secondLeft, secondRow, right);
			colorsTop = secondRow + WidgetSettingsLayout.ROW_HEIGHT + WidgetSettingsLayout.SECTION_GAP;
		} else {
			scale = rowRect(left, firstRow, right);
			opacity = nextRow(scale, left, right);
			anchor = nextRow(opacity, left, right);
			locked = nextRow(anchor, left, right);
			colorsTop = locked.bottom() + WidgetSettingsLayout.SECTION_GAP;
		}
		int colorsFirstRow = colorsTop + WidgetSettingsLayout.SECTION_LABEL_HEIGHT;
		Rect background;
		Rect labelColor;
		Rect valueColor;
		if (layout.columns() == 2) {
			int columnWidth = (right - left - COLUMN_GAP) / 2;
			int secondLeft = left + columnWidth + COLUMN_GAP;
			background = rowRect(left, colorsFirstRow, left + columnWidth);
			labelColor = rowRect(secondLeft, colorsFirstRow, right);
			valueColor = nextRow(background, left, left + columnWidth);
		} else {
			background = rowRect(left, colorsFirstRow, right);
			labelColor = nextRow(background, left, right);
			valueColor = nextRow(labelColor, left, right);
		}
		int lastColorBottom = Math.max(background.bottom(), Math.max(labelColor.bottom(), valueColor.bottom()));
		int commonTop = lastColorBottom + WidgetSettingsLayout.SECTION_GAP;
		int commonFirstRow = commonTop + WidgetSettingsLayout.SECTION_LABEL_HEIGHT;
		Rect showLabel;
		Rect showIcon;
		Rect compact;
		Rect orientation;
		if (layout.columns() == 2) {
			int columnWidth = (right - left - COLUMN_GAP) / 2;
			int secondLeft = left + columnWidth + COLUMN_GAP;
			showLabel = rowRect(left, commonFirstRow, left + columnWidth);
			showIcon = rowRect(secondLeft, commonFirstRow, right);
			compact = nextRow(showLabel, left, left + columnWidth);
			orientation = nextRow(showIcon, secondLeft, right);
		} else {
			showLabel = rowRect(left, commonFirstRow, right);
			showIcon = nextRow(showLabel, left, right);
			compact = nextRow(showIcon, left, right);
			orientation = nextRow(compact, left, right);
		}
		int commonBottom = Math.max(compact.bottom(), orientation.bottom());
		int clockTop = commonBottom + WidgetSettingsLayout.SECTION_GAP;
		Rect realTime = null;
		Rect worldTime = null;
		Rect seconds = null;
		Rect hourFormat = null;
		if (isClock()) {
			int clockFirstRow = clockTop + WidgetSettingsLayout.SECTION_LABEL_HEIGHT;
			if (layout.columns() == 2) {
				int columnWidth = (right - left - COLUMN_GAP) / 2;
				int secondLeft = left + columnWidth + COLUMN_GAP;
				realTime = rowRect(left, clockFirstRow, left + columnWidth);
				worldTime = rowRect(secondLeft, clockFirstRow, right);
				seconds = nextRow(realTime, left, left + columnWidth);
				hourFormat = nextRow(worldTime, secondLeft, right);
			} else {
				realTime = rowRect(left, clockFirstRow, right);
				worldTime = nextRow(realTime, left, right);
				seconds = nextRow(worldTime, left, right);
				hourFormat = nextRow(seconds, left, right);
			}
		}
		return new ContentGeometry(preview, sectionTop, scale, opacity, anchor, locked,
				colorsTop, background, labelColor, valueColor, commonTop, showLabel, showIcon, compact, orientation,
				clockTop, realTime, worldTime, seconds, hourFormat);
	}

	private Rect colorPopoverBounds(ContentGeometry geometry) {
		Rect source = colorTarget == ColorTarget.TITLE ? geometry.labelColor() : geometry.valueColor();
		int popRight = Math.min(layout.right() - 14, source.right());
		int popLeft = Math.max(layout.left() + 14, popRight - 224);
		int popTop = Math.max(layout.contentTop() + 4,
				Math.min(layout.contentBottom() - 82, source.top() - 82));
		return new Rect(popLeft, popTop, popRight, popTop + 78);
	}

	private boolean pickColor(double mouseX, double mouseY, Rect popover) {
		for (int index = 0; index < COLORS.length; index++) {
			if (colorSwatch(popover, index).contains(mouseX, mouseY)) {
				String value = String.format("#%06X", COLORS[index] & 0xFFFFFF);
				if (colorTarget == ColorTarget.TITLE) settings().labelColor = value;
				else settings().valueColor = value;
				colorTarget = null;
				save();
				return true;
			}
		}
		colorTarget = null;
		return true;
	}

	private static Rect colorSwatch(Rect popover, int index) {
		int x = popover.left() + 10 + (index % 6) * 34;
		int y = popover.top() + 30 + (index / 6) * 22;
		return new Rect(x, y, x + 22, y + 14);
	}

	private static Rect rowRect(int left, int top, int right) {
		return new Rect(left, top, right, top + WidgetSettingsLayout.ROW_HEIGHT);
	}

	private static Rect nextRow(Rect previous, int left, int right) {
		return rowRect(left, previous.bottom() + WidgetSettingsLayout.ROW_GAP, right);
	}

	private static Rect minusBounds(Rect row) {
		return new Rect(row.right() - 140, row.top() + 7, row.right() - 112, row.bottom() - 7);
	}

	private static Rect plusBounds(Rect row) {
		return new Rect(row.right() - 34, row.top() + 7, row.right() - 6, row.bottom() - 7);
	}

	private void toggleEnabled(BlockeraButton button) {
		settings().enabled = !settings().enabled;
		button.setMessage(stateLabel());
		save();
	}

	private void toggleLocked() {
		settings().locked = !settings().locked;
		save();
	}

	private void resetWidget() {
		store.resetWidget(widgetId);
		save();
		rebuildWidgets();
	}

	private void cycleAnchor() {
		HudAnchor[] anchors = HudAnchor.values();
		settings().anchor = anchors[(settings().anchor.ordinal() + 1) % anchors.length];
		save();
	}

	private void changeScale(float delta) {
		settings().scale = Math.max(0.5F, Math.min(2.0F, settings().scale + delta));
		save();
	}

	private void changeOpacity(int delta) {
		settings().opacity = Math.max(0.0F, Math.min(1.0F, settings().opacity + delta / 100.0F));
		save();
	}

	private HudWidgetSettings settings() { return store.settings(widgetId); }
	private boolean isClock() { return BuiltinWidgetIds.CLOCK.equals(HudLayoutStore.baseWidgetId(widgetId)); }
	private static Component state(boolean enabled) {
		return Component.translatable(enabled ? "blockera.state.enabled" : "blockera.state.disabled");
	}
	private Component stateLabel() { return Component.translatable(settings().enabled ? "blockera.state.enabled" : "blockera.state.disabled"); }
	private void save() { store.save(); }

	private static int parseColor(String value) {
		try {
			return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
		} catch (RuntimeException ignored) {
			return 0xFFFFFFFF;
		}
	}

	@Override public void onClose() { save(); minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
