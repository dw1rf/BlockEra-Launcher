package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import space.blockera.core.api.Widget;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.hud.BlockeraHudOverlay;
import space.blockera.core.hud.BuiltinHudWidgets;
import space.blockera.core.hud.HudAnchor;
import space.blockera.core.hud.HudCategory;
import space.blockera.core.hud.HudCollisionManager;
import space.blockera.core.hud.HudDataSnapshot;
import space.blockera.core.hud.HudHistoryManager;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.HudPoint;
import space.blockera.core.hud.HudSafeAreaManager;
import space.blockera.core.hud.HudSelectionManager;
import space.blockera.core.hud.HudSnapManager;
import space.blockera.core.hud.HudWidget;
import space.blockera.core.hud.HudWidgetBounds;
import space.blockera.core.hud.HudWidgetSettings;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.hud.VanillaHudSettings;
import space.blockera.core.ui.components.SearchField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Direct-manipulation HUD editor rendered over the live game at exact runtime coordinates. */
public final class HudEditorScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final int TOOLBAR_TOP = 8;
	private static final int TOOLBAR_HEIGHT = 24;
	private static final int LIBRARY_WIDTH = 214;
	private static final int INSPECTOR_WIDTH = 232;
	private static final int PANEL_GAP = 10;
	private static final int GRID_MINOR = 8;
	private static final int GRID_MAJOR = 32;
	private static final int SNAP_THRESHOLD = 6;
	private static final int CONTEXT_WIDTH = 138;
	private static final int CONTEXT_ROW_HEIGHT = 18;
	private static final int INSPECTOR_ROW_TOP = 38;
	private static final int INSPECTOR_ROW_STEP = 24;
	private static final String[] COLOR_PRESETS = {"#9C98AE", "#F8F7FF", "#9B7BFF", "#60D6A7", "#F0C66B", "#EE00F5"};
	private static final String VANILLA_PREFIX = "vanilla:";
	private static final String[] CONTEXT_ACTIONS = {
			"configure", "hide", "lock", "duplicate", "reset_scale", "reset_position", "front", "back", "remove"
	};

	private final Screen parent;
	private final HudLayoutStore store = HudLayoutStore.instance();
	private final HudSelectionManager selection = new HudSelectionManager();
	private final HudHistoryManager history = new HudHistoryManager();
	private final HudCollisionManager collisionManager = new HudCollisionManager();
	private final HudSnapManager snapManager = new HudSnapManager();
	private final HudSafeAreaManager safeAreaManager = new HudSafeAreaManager();
	private final Map<String, HudPoint> dragOrigins = new LinkedHashMap<>();
	private final Map<String, HudWidgetBounds> bounds = new LinkedHashMap<>();
	private List<HudCollisionManager.Collision> collisions = List.of();
	private List<HudSafeAreaManager.SafeArea> safeAreas = List.of();
	private SearchField search;
	private HudCategory category;
	private boolean libraryOpen;
	private boolean gridVisible = true;
	private boolean gridSnap = true;
	private boolean smartSnap = true;
	private boolean safeAreasVisible;
	private boolean pendingDrag;
	private boolean dragging;
	private double pressX;
	private double pressY;
	private String dragSnapshot;
	private String libraryPressedType;
	private Integer verticalGuide;
	private Integer horizontalGuide;
	private int contextX = -1;
	private int contextY = -1;
	private int libraryScroll;
	private String contextId;
	private boolean detailsOpen;
	private long lastClickAt;
	private String lastClickId;
	private HudDataSnapshot liveData = HudDataSnapshot.preview();
	private boolean previewCaptured;
	private HudPreviewTransform previewTransform;

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("blockera.hud.editor.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		libraryOpen = true;
		if (width < 650) {
			initCompactToolbar();
			initLibrary();
			refreshDiagnostics();
			return;
		}
		int x = 10;
		addToolbarButton(x, 84, "blockera.hud.editor.add_widget", button -> {
			libraryOpen = !libraryOpen;
			rebuildWidgets();
		});
		x += 88;
		addToolbarButton(x, 42, "blockera.hud.editor.undo", button -> undo()); x += 46;
		addToolbarButton(x, 42, "blockera.hud.editor.redo", button -> redo()); x += 46;
		addToolbarButton(x, 54, "blockera.hud.editor.grid", button -> gridVisible = !gridVisible); x += 58;
		addToolbarButton(x, 58, "blockera.hud.editor.snap", button -> smartSnap = !smartSnap); x += 62;
		addToolbarButton(x, 76, "blockera.hud.editor.safe_areas", button -> safeAreasVisible = !safeAreasVisible); x += 80;
		addToolbarButton(x, 94, "blockera.hud.editor.profile", button -> cycleProfile());
		addRenderableWidget(new BlockeraButton(Math.max(x + 98, width - 142), TOOLBAR_TOP, 58, TOOLBAR_HEIGHT,
				Component.translatable("blockera.common.reset"), button -> mutate(store::resetActiveProfile)));
		addRenderableWidget(new BlockeraButton(width - 78, TOOLBAR_TOP, 68, TOOLBAR_HEIGHT,
				Component.translatable("blockera.common.done"), button -> saveAndClose(), true));

		initLibrary();
		refreshDiagnostics();
	}

	private void initCompactToolbar() {
		int x = 8;
		addToolbarButton(x, 32, "blockera.hud.editor.add_short", button -> {
			libraryOpen = !libraryOpen;
			rebuildWidgets();
		}); x += 35;
		addToolbarButton(x, 32, "blockera.hud.editor.undo_short", button -> undo()); x += 35;
		addToolbarButton(x, 32, "blockera.hud.editor.redo_short", button -> redo()); x += 35;
		addToolbarButton(x, 40, "blockera.hud.editor.grid_short", button -> gridVisible = !gridVisible); x += 43;
		addToolbarButton(x, 40, "blockera.hud.editor.snap_short", button -> smartSnap = !smartSnap); x += 43;
		addToolbarButton(x, 40, "blockera.hud.editor.safe_short", button -> safeAreasVisible = !safeAreasVisible); x += 43;
		addToolbarButton(x, 46, "blockera.hud.editor.profile_short", button -> cycleProfile()); x += 49;
		addToolbarButton(x, 40, "blockera.common.reset_short", button -> mutate(store::resetActiveProfile));
		addRenderableWidget(new BlockeraButton(width - 76, TOOLBAR_TOP, 68, TOOLBAR_HEIGHT,
				Component.translatable("blockera.common.done"), button -> saveAndClose(), true));
	}

	private void initLibrary() {
		if (libraryOpen) {
			int left = 8;
			search = addRenderableWidget(new SearchField(left + 10, 48, LIBRARY_WIDTH - 20, 20, THEME));
			addRenderableWidget(new BlockeraButton(left + 10, 73, LIBRARY_WIDTH - 20, 20, categoryLabel(), button -> {
				cycleCategory();
				button.setMessage(categoryLabel());
			}));
		} else {
			search = null;
		}
	}

	private void addToolbarButton(int x, int buttonWidth, String key, BlockeraButton.OnPress action) {
		addRenderableWidget(new BlockeraButton(x, TOOLBAR_TOP, buttonWidth, TOOLBAR_HEIGHT,
				Component.translatable(key), action));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, 0xA00D0D14);
		updateLiveData();
		previewTransform = calculatePreview();
		beginPreviewScissor(previewTransform);
		poseStack.pushPose();
		poseStack.translate(previewTransform.left(), previewTransform.top(), 0.0D);
		poseStack.scale(previewTransform.scale(), previewTransform.scale(), 1.0F);
		if (gridVisible) renderGrid(poseStack);
		if (safeAreasVisible) renderSafeAreas(poseStack);
		renderHud(poseStack);
		HudPoint virtualMouse = previewTransform.toScreen(mouseX, mouseY);
		renderDiagnostics(poseStack, virtualMouse.x(), virtualMouse.y());
		poseStack.popPose();
		RenderSystem.disableScissor();
		renderPreviewFrame(poseStack);
		renderToolbarBackdrop(poseStack);
		if (libraryOpen) renderLibrary(poseStack, mouseX, mouseY);
		if (!selection.isEmpty()) renderSelectionPanel(poseStack);
		if (contextId != null) renderContextMenu(poseStack, mouseX, mouseY);
		if (!ClientConfig.HUD_EDITOR_HINT_SEEN.get()) renderFirstUseHint(poseStack);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void updateLiveData() {
		if (previewCaptured) return;
		liveData = minecraft.player == null ? HudDataSnapshot.preview() : HudDataSnapshot.capture(minecraft);
		previewCaptured = true;
		refreshBounds();
	}

	private HudPreviewTransform calculatePreview() {
		int left = LIBRARY_WIDTH + 8 + PANEL_GAP;
		int inspector = width >= 900 ? INSPECTOR_WIDTH + PANEL_GAP + 8 : 8;
		int areaWidth = Math.max(80, width - left - inspector);
		int areaTop = 42;
		int areaHeight = Math.max(60, height - areaTop - 10);
		return HudPreviewTransform.fit(left, areaTop, areaWidth, areaHeight, width, height);
	}

	private void beginPreviewScissor(HudPreviewTransform transform) {
		double scale = minecraft.getWindow().getGuiScale();
		int framebufferHeight = minecraft.getWindow().getHeight();
		RenderSystem.enableScissor((int) Math.floor(transform.left() * scale),
				framebufferHeight - (int) Math.ceil((transform.top() + transform.height()) * scale),
				(int) Math.ceil(transform.width() * scale), (int) Math.ceil(transform.height() * scale));
	}

	private void renderPreviewFrame(PoseStack poseStack) {
		if (previewTransform == null) return;
		HudWidgetBounds frame = new HudWidgetBounds(previewTransform.left(), previewTransform.top(),
				previewTransform.left() + previewTransform.width(), previewTransform.top() + previewTransform.height());
		drawOutline(poseStack, frame, THEME.borderHoverArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.editor.viewport"),
				previewTransform.left() + 8, previewTransform.top() + 7, THEME.textMutedArgb());
	}

	private void renderGrid(PoseStack poseStack) {
		for (int x = GRID_MINOR; x < width; x += GRID_MINOR) {
			int color = x % GRID_MAJOR == 0 ? 0x169B7BFF : 0x079B7BFF;
			GuiComponent.fill(poseStack, x, 0, x + 1, height, color);
		}
		for (int y = GRID_MINOR; y < height; y += GRID_MINOR) {
			int color = y % GRID_MAJOR == 0 ? 0x169B7BFF : 0x079B7BFF;
			GuiComponent.fill(poseStack, 0, y, width, y + 1, color);
		}
		GuiComponent.fill(poseStack, width / 2, 0, width / 2 + 1, height, 0x489B7BFF);
		GuiComponent.fill(poseStack, 0, height / 2, width, height / 2 + 1, 0x489B7BFF);
	}

	private void renderSafeAreas(PoseStack poseStack) {
		for (HudSafeAreaManager.SafeArea area : safeAreas) {
			HudWidgetBounds zone = area.bounds();
			GuiComponent.fill(poseStack, zone.left(), zone.top(), zone.right(), zone.bottom(), 0x1059D69A);
			drawOutline(poseStack, zone, 0x3859D69A);
		}
	}

	private void renderHud(PoseStack poseStack) {
		List<Map.Entry<String, HudWidgetSettings>> entries = new ArrayList<>(store.snapshot().entrySet());
		entries.sort(Comparator.comparingInt(entry -> entry.getValue().zIndex));
		for (Map.Entry<String, HudWidgetSettings> entry : entries) {
			HudWidgetSettings settings = store.settings(entry.getKey());
			if (!settings.enabled) continue;
			HudWidget widget = widgetFor(entry.getKey());
			BlockeraHudOverlay.renderWidget(poseStack, liveData, widget, settings, width, height, false);
		}
		for (VanillaHudElement element : VanillaHudElement.values()) {
			VanillaHudSettings settings = store.vanillaSettings(element.id());
			HudPoint point = settings.anchor.resolve(width, height, element.width(), element.height(), settings.scale,
					settings.offsetX, settings.offsetY);
			poseStack.pushPose();
			poseStack.translate(point.x(), point.y(), 0.0D);
			poseStack.scale(settings.scale, settings.scale, 1.0F);
			int fill = settings.enabled ? 0x70141720 : 0x22141720;
			BlockeraDraw.glassPanel(poseStack, 0, 0, element.width(), element.height(), 5, fill,
					settings.enabled ? THEME.borderArgb() : 0x609B7BFF);
			UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.vanilla." + element.id()), 5, 5,
					settings.enabled ? THEME.textMutedArgb() : THEME.textDisabledArgb());
			poseStack.popPose();
		}
	}

	private void renderDiagnostics(PoseStack poseStack, int mouseX, int mouseY) {
		String hovered = hitAt(mouseX, mouseY);
		Set<String> colliding = collidingIds();
		for (Map.Entry<String, HudWidgetBounds> entry : bounds.entrySet()) {
			String id = entry.getKey();
			HudWidgetBounds box = entry.getValue();
			if (colliding.contains(id) && (dragging || selection.contains(id))) {
				drawOutline(poseStack, box, THEME.warningArgb());
			}
			if (selection.contains(id)) {
				drawOutline(poseStack, box, THEME.accentArgb());
				drawAnchorPoint(poseStack, id, box);
				if (isLocked(id)) UiFont.drawSmall(poseStack, Component.literal("L"), box.right() - 8, box.top() + 2, THEME.warningArgb());
			} else if (id.equals(hovered)) drawOutline(poseStack, box, THEME.borderHoverArgb());
		}
		for (HudCollisionManager.Collision collision : collisions) {
			if (!dragging && !selection.contains(collision.firstId()) && !selection.contains(collision.secondId())) continue;
			HudWidgetBounds overlap = collision.intersection();
			GuiComponent.fill(poseStack, overlap.left(), overlap.top(), overlap.right(), overlap.bottom(), 0x30F0C66B);
		}
		if (verticalGuide != null) GuiComponent.fill(poseStack, verticalGuide, 0, verticalGuide + 1, height, 0xC09B7BFF);
		if (horizontalGuide != null) GuiComponent.fill(poseStack, 0, horizontalGuide, width, horizontalGuide + 1, 0xC09B7BFF);
	}

	private void renderToolbarBackdrop(PoseStack poseStack) {
		BlockeraDraw.glassPanel(poseStack, 6, 4, width - 6, TOOLBAR_TOP + TOOLBAR_HEIGHT + 4,
				THEME.buttonRadius(), 0xC812141D, THEME.borderArgb());
		if (width >= 650) {
			UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.profile." + store.activeProfile()),
					Math.min(width - 220, 552), 16, THEME.accentArgb());
		}
	}

	private void renderLibrary(PoseStack poseStack, int mouseX, int mouseY) {
		int left = 8;
		BlockeraDraw.glassPanel(poseStack, left, 40, left + LIBRARY_WIDTH, height - 8, THEME.panelRadius(),
				THEME.glassPanelArgb(), THEME.borderArgb());
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.hud.editor.library"), left + 12, 101, THEME.textPrimaryArgb());
		List<HudEditorItem> cards = libraryItems();
		int top = 119;
		for (int index = 0; index < cards.size(); index++) {
			if (index < libraryScroll) continue;
			HudEditorItem item = cards.get(index);
			int y = top + (index - libraryScroll) * 27;
			if (y + 23 > height - 8) break;
			boolean hovered = mouseX >= left + 10 && mouseX <= left + LIBRARY_WIDTH - 10 && mouseY >= y && mouseY <= y + 23;
			BlockeraDraw.glassPanel(poseStack, left + 10, y, left + LIBRARY_WIDTH - 10, y + 23, 5,
					hovered ? THEME.cardHoverArgb() : THEME.glassCardArgb(), THEME.borderArgb());
			UiFont.draw(poseStack, item.title(), left + 20, y + 7, THEME.textPrimaryArgb());
			UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.editor.add"), left + LIBRARY_WIDTH - 48, y + 8, THEME.accentArgb());
		}
	}

	private void renderSelectionPanel(PoseStack poseStack) {
		int panelWidth = INSPECTOR_WIDTH;
		int left = width - panelWidth - 8;
		int top = 40;
		BlockeraDraw.glassPanel(poseStack, left, top, left + panelWidth, height - 8, 10, 0xD8141720, THEME.borderHoverArgb());
		String primary = selection.primary();
		Component title = selection.size() > 1
				? Component.translatable("blockera.hud.editor.selected_count", selection.size()) : itemFor(primary).title();
		UiFont.drawSemibold(poseStack, UiFont.ellipsize(title, panelWidth - 20, true), left + 10, top + 14, THEME.textPrimaryArgb());
		if (selection.size() == 1) {
			int row = 0;
			inspectorRow(poseStack, left, top, row++, "blockera.hud.editor.scale", Math.round(scale(primary) * 100) + "%", false);
			inspectorRow(poseStack, left, top, row++, "blockera.widget_settings.anchor",
					Component.translatable("blockera.hud.anchor." + anchor(primary).name().toLowerCase(Locale.ROOT)).getString(), false);
			if (!isVanilla(primary)) {
				HudWidgetSettings settings = store.settings(primary);
				inspectorRow(poseStack, left, top, row++, "blockera.hud.editor.opacity", Math.round(settings.opacity * 100) + "%", false);
				inspectorRow(poseStack, left, top, row++, "blockera.hud.editor.background", state(settings.background), false);
				inspectorRow(poseStack, left, top, row++, "blockera.widget_settings.show_label", state(settings.showLabel), false);
				inspectorRow(poseStack, left, top, row++, "blockera.widget_settings.show_icon", state(settings.showIcon), false);
				inspectorRow(poseStack, left, top, row++, "blockera.widget_settings.compact", state(settings.compact), false);
				inspectorRow(poseStack, left, top, row++, "blockera.widget_settings.orientation", settings.orientation, false);
				inspectorRow(poseStack, left, top, row++, "blockera.hud.editor.label_color", settings.labelColor, false);
				inspectorRow(poseStack, left, top, row++, "blockera.hud.editor.value_color", settings.valueColor, false);
			} else {
				inspectorRow(poseStack, left, top, row++, "blockera.common.enabled", state(enabled(primary)), false);
			}
			inspectorRow(poseStack, left, top, row++, isLocked(primary) ? "blockera.hud.context.unlock" : "blockera.hud.context.lock", "", false);
			inspectorRow(poseStack, left, top, row, "blockera.hud.context.remove", "", true);
		}
		if (!collisions.isEmpty()) UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.editor.overlap"), left + 10, height - 24, THEME.warningArgb());
	}

	private void inspectorRow(PoseStack poseStack, int panelLeft, int panelTop, int row, String key, String value, boolean danger) {
		int y = panelTop + INSPECTOR_ROW_TOP + row * INSPECTOR_ROW_STEP;
		if (y + 20 >= height - 10) return;
		BlockeraDraw.roundedRect(poseStack, panelLeft + 10, y, panelLeft + INSPECTOR_WIDTH - 10, y + 20, 5,
				danger ? 0x502F1720 : THEME.glassCardArgb());
		UiFont.drawSmall(poseStack, Component.translatable(key), panelLeft + 16, y + 7,
				danger ? THEME.dangerArgb() : THEME.textPrimaryArgb());
		if (!value.isEmpty()) UiFont.drawSmall(poseStack, Component.literal(value),
				panelLeft + INSPECTOR_WIDTH - 16 - UiFont.width(Component.literal(value)), y + 7, THEME.textMutedArgb());
	}

	private void renderDetailsPanel(PoseStack poseStack) {
		String id = selection.primary();
		HudWidgetBounds box = bounds.get(id);
		if (box == null) return;
		int panelWidth = 220;
		int left = Math.max(8, Math.min(width - panelWidth - 8, box.centerX() - panelWidth / 2));
		int top = Math.max(42, box.top() - 82);
		BlockeraDraw.glassPanel(poseStack, left, top, left + panelWidth, top + 72, 8, 0xD2191D28, THEME.borderHoverArgb());
		UiFont.drawSemibold(poseStack, itemFor(id).title(), left + 10, top + 9, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.editor.scale_value", Math.round(scale(id) * 100)), left + 10, top + 29, THEME.textMutedArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.editor.anchor_value", anchor(id).name()), left + 10, top + 45, THEME.textMutedArgb());
		if (!isVanilla(id)) UiFont.drawSmall(poseStack,
				Component.translatable("blockera.hud.editor.opacity_value", Math.round(store.settings(id).opacity * 100)),
				left + 116, top + 29, THEME.textMutedArgb());
	}

	private void renderContextMenu(PoseStack poseStack, int mouseX, int mouseY) {
		int bottom = contextY + CONTEXT_ACTIONS.length * CONTEXT_ROW_HEIGHT + 4;
		BlockeraDraw.glassPanel(poseStack, contextX, contextY, contextX + CONTEXT_WIDTH, bottom, 7,
				0xDE191D28, THEME.borderHoverArgb());
		for (int index = 0; index < CONTEXT_ACTIONS.length; index++) {
			int y = contextY + 2 + index * CONTEXT_ROW_HEIGHT;
			boolean hovered = mouseX >= contextX && mouseX <= contextX + CONTEXT_WIDTH && mouseY >= y && mouseY < y + CONTEXT_ROW_HEIGHT;
			if (hovered) BlockeraDraw.roundedRect(poseStack, contextX + 3, y, contextX + CONTEXT_WIDTH - 3, y + CONTEXT_ROW_HEIGHT - 1, 4, THEME.cardHoverArgb());
			String action = CONTEXT_ACTIONS[index];
			if ("lock".equals(action) && isLocked(contextId)) action = "unlock";
			int color = "remove".equals(action) ? THEME.dangerArgb() : THEME.textPrimaryArgb();
			UiFont.drawSmall(poseStack, Component.translatable("blockera.hud.context." + action), contextX + 9, y + 6, color);
		}
	}

	private void renderFirstUseHint(PoseStack poseStack) {
		Component hint = Component.translatable("blockera.hud.editor.hint");
		int hintWidth = Math.min(width - 24, UiFont.width(hint) + 24);
		int left = (width - hintWidth) / 2;
		BlockeraDraw.glassPanel(poseStack, left, 42, left + hintWidth, 70, 8, 0xD0191D28, THEME.accentArgb());
		UiFont.drawCentered(poseStack, UiFont.ellipsize(hint, hintWidth - 20, false), width / 2.0F, 52, THEME.textPrimaryArgb());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) { acknowledgeHint(); return true; }
		acknowledgeHint();
		if (contextId != null) {
			if (insideContext(mouseX, mouseY)) {
				int row = ((int) mouseY - contextY - 2) / CONTEXT_ROW_HEIGHT;
				if (row >= 0 && row < CONTEXT_ACTIONS.length) runContextAction(CONTEXT_ACTIONS[row]);
				return true;
			}
			closeContext();
		}
		if (button == 0 && clickSelectionPanel(mouseX, mouseY)) return true;
		if (libraryOpen && insideLibraryCards(mouseX, mouseY)) {
			HudEditorItem card = libraryCardAt(mouseY);
			if (card != null && button == 0) {
				libraryPressedType = card.typeId();
				pressX = mouseX; pressY = mouseY;
				return true;
			}
		}
		if (previewTransform == null || !previewTransform.contains(mouseX, mouseY)) {
			if (button == 0) selection.clear();
			return false;
		}
		HudPoint virtual = previewTransform.toScreen((float) mouseX, (float) mouseY);
		String hit = hitAt(virtual.x(), virtual.y());
		if (button == 1 && hit != null) {
			if (!selection.contains(hit)) selection.selectOnly(hit);
			openContext(hit, (int) mouseX, (int) mouseY);
			return true;
		}
		if (button != 0) return false;
		if (hit == null) {
			selection.clear();
			detailsOpen = false;
			return true;
		}
		long now = System.currentTimeMillis();
		if (hit.equals(lastClickId) && now - lastClickAt <= 320L) {
			selection.selectOnly(hit);
			openConfigureScreen(hit);
			lastClickAt = 0L;
			return true;
		}
		lastClickAt = now;
		lastClickId = hit;
		if (hasShiftDown()) selection.toggle(hit); else if (!selection.contains(hit)) selection.selectOnly(hit);
		if (selection.contains(hit) && !isLocked(hit)) prepareDrag(virtual.x(), virtual.y());
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		if (libraryPressedType != null) return true;
		if (!pendingDrag && !dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		if (previewTransform == null) return true;
		HudPoint virtual = previewTransform.toScreen((float) mouseX, (float) mouseY);
		double physicalDistance = Math.hypot(virtual.x() - pressX, virtual.y() - pressY) * minecraft.getWindow().getGuiScale();
		if (!dragging && physicalDistance < 4.0D) return true;
		if (!dragging) {
			dragging = true;
			history.record(dragSnapshot);
		}
		moveSelection((int) Math.round(virtual.x() - pressX), (int) Math.round(virtual.y() - pressY), !hasAltDown());
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && libraryPressedType != null) {
			String type = libraryPressedType;
			libraryPressedType = null;
			if (previewTransform != null && previewTransform.contains(mouseX, mouseY)) {
				HudPoint virtual = previewTransform.toScreen((float) mouseX, (float) mouseY);
				addWidget(type, virtual.x(), virtual.y());
			}
			return true;
		}
		if (button == 0 && (pendingDrag || dragging)) {
			boolean moved = dragging;
			pendingDrag = false;
			dragging = false;
			verticalGuide = null;
			horizontalGuide = null;
			if (moved) {
				adoptNearestAnchors();
				store.save();
				refreshDiagnostics();
			}
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (libraryOpen && inside(mouseX, mouseY, 8, 40, 8 + LIBRARY_WIDTH, height - 8)) {
			int visible = Math.max(1, (height - 127) / 27);
			libraryScroll = clamp(libraryScroll - (int) Math.signum(delta), 0,
					Math.max(0, libraryItems().size() - visible));
			return true;
		}
		if (hasControlDown() && !selection.isEmpty()) {
			float step = hasShiftDown() ? 0.02F : 0.1F;
			mutate(() -> selection.all().forEach(id -> setScale(id, scale(id) + (delta > 0 ? step : -step))));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (contextId != null) { closeContext(); return true; }
			if (detailsOpen) { detailsOpen = false; return true; }
			if (!selection.isEmpty()) { selection.clear(); return true; }
			if (libraryOpen) { libraryOpen = false; rebuildWidgets(); return true; }
			onClose(); return true;
		}
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
			if (hasShiftDown()) redo(); else undo();
			return true;
		}
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_A) { selectAll(); return true; }
		if (hasControlDown() && keyCode == GLFW.GLFW_KEY_D) { duplicateSelection(); return true; }
		if (keyCode == GLFW.GLFW_KEY_DELETE) { removeSelection(); return true; }
		if (keyCode == GLFW.GLFW_KEY_L) { toggleLockSelection(); return true; }
		if (keyCode == GLFW.GLFW_KEY_G) { gridSnap = !gridSnap; return true; }
		if (keyCode >= GLFW.GLFW_KEY_RIGHT && keyCode <= GLFW.GLFW_KEY_UP && !selection.isEmpty()) {
			int amount = hasShiftDown() ? 8 : 1;
			int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -amount : keyCode == GLFW.GLFW_KEY_RIGHT ? amount : 0;
			int dy = keyCode == GLFW.GLFW_KEY_UP ? -amount : keyCode == GLFW.GLFW_KEY_DOWN ? amount : 0;
			mutate(() -> nudgeSelection(dx, dy));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void prepareDrag(double mouseX, double mouseY) {
		pendingDrag = true;
		dragging = false;
		pressX = mouseX;
		pressY = mouseY;
		dragSnapshot = store.json();
		dragOrigins.clear();
		for (String id : selection.all()) if (!isLocked(id)) {
			HudWidgetBounds box = bounds.get(id);
			if (box != null) dragOrigins.put(id, new HudPoint(box.left(), box.top()));
		}
	}

	private void moveSelection(int dx, int dy, boolean snapping) {
		String primary = selection.primary();
		HudPoint primaryOrigin = dragOrigins.get(primary);
		if (primaryOrigin == null) return;
		HudWidgetBounds primaryBounds = bounds.get(primary);
		int x = primaryOrigin.x() + dx;
		int y = primaryOrigin.y() + dy;
		List<HudWidgetBounds> targets = new ArrayList<>();
		for (Map.Entry<String, HudWidgetBounds> entry : bounds.entrySet()) {
			if (!selection.contains(entry.getKey())) targets.add(entry.getValue());
		}
		if (safeAreasVisible) for (HudSafeAreaManager.SafeArea area : safeAreas) targets.add(area.bounds());
		HudSnapManager.Result snapped = snapManager.snap(x, y, primaryBounds.width(), primaryBounds.height(), width, height,
				targets, snapping && smartSnap, snapping && gridSnap, SNAP_THRESHOLD);
		verticalGuide = snapped.verticalGuide();
		horizontalGuide = snapped.horizontalGuide();
		int adjustedDx = snapped.x() - primaryOrigin.x();
		int adjustedDy = snapped.y() - primaryOrigin.y();
		for (Map.Entry<String, HudPoint> entry : dragOrigins.entrySet()) {
			HudWidgetBounds box = bounds.get(entry.getKey());
			int targetX = clamp(entry.getValue().x() + adjustedDx, 0, Math.max(0, width - box.width()));
			int targetY = clamp(entry.getValue().y() + adjustedDy, 0, Math.max(0, height - box.height()));
			setAbsolute(entry.getKey(), targetX, targetY);
		}
		refreshBounds();
	}

	private void adoptNearestAnchors() {
		for (String id : dragOrigins.keySet()) {
			HudWidgetBounds box = bounds.get(id);
			if (box == null) continue;
			HudAnchor nearest = HudAnchor.nearest(box.centerX(), box.centerY(), width, height);
			setAnchor(id, nearest);
			setAbsolute(id, box.left(), box.top());
		}
	}

	private void addWidget(String typeId, int mouseX, int mouseY) {
		String before = store.json();
		if (isVanilla(typeId)) {
			VanillaHudElement element = vanillaElement(typeId);
			store.vanillaSettings(element.id()).enabled = true;
			setAbsolute(typeId, clamp(mouseX - element.width() / 2, 0, width - element.width()),
					clamp(mouseY - element.height() / 2, 0, height - element.height()));
			selection.selectOnly(typeId); history.record(before); store.save(); refreshDiagnostics(); return;
		}
		String id = typeId;
		if (store.snapshot().containsKey(typeId) && store.settings(typeId).enabled) id = store.duplicateWidget(typeId);
		else store.settings(typeId).enabled = true;
		HudWidget widget = widgetFor(id);
		int x = mouseX;
		int y = mouseY;
		setAbsolute(id, clamp(x - widget.width(liveData) / 2, 0, width - widget.width(liveData)),
				clamp(y - widget.height(liveData) / 2, 40, height - widget.height(liveData)));
		store.settings(id).zIndex = store.highestZIndex() + 1;
		selection.selectOnly(id);
		history.record(before);
		store.save();
		refreshDiagnostics();
	}

	private void runContextAction(String action) {
		String id = contextId;
		closeContext();
		switch (action) {
			case "configure" -> openConfigureScreen(id);
			case "hide" -> mutate(() -> setEnabled(id, false));
			case "lock" -> mutate(() -> setLocked(id, !isLocked(id)));
			case "duplicate" -> { if (!isVanilla(id)) duplicateSelection(); }
			case "reset_scale" -> mutate(() -> setScale(id, 1.0F));
			case "reset_position" -> mutate(() -> resetPosition(id));
			case "front" -> mutate(() -> setZIndex(id, store.highestZIndex() + 1));
			case "back" -> mutate(() -> setZIndex(id, store.lowestZIndex() - 1));
			case "remove" -> removeSelection();
			default -> { }
		}
	}

	private void openConfigureScreen(String id) {
		if (configureMode(id) == ConfigureMode.WIDGET) {
			detailsOpen = false;
			return;
		}
		Screen screen = createConfigureScreen(this, id, itemFor(id).title());
		if (screen == null) {
			detailsOpen = true;
			return;
		}
		detailsOpen = false;
		minecraft.setScreen(screen);
	}

	static Screen createConfigureScreen(Screen parent, String id, Component title) {
		ConfigureMode mode = configureMode(id);
		if (mode == ConfigureMode.WIDGET) return new BlockeraWidgetSettingsScreen(parent, id, title);
		VanillaHudElement element = vanillaElement(id);
		if (mode == ConfigureMode.CHAT) return new BlockeraChatSettingsScreen(parent, element, title);
		return new BlockeraVanillaHudSettingsScreen(parent, element, title);
	}

	static ConfigureMode configureMode(String id) {
		if (!isVanilla(id)) return ConfigureMode.WIDGET;
		return (VANILLA_PREFIX + VanillaHudElement.CHAT.id()).equals(id) ? ConfigureMode.CHAT : ConfigureMode.VANILLA;
	}

	enum ConfigureMode { WIDGET, VANILLA, CHAT }

	private void mutate(Runnable change) {
		String before = store.json();
		change.run();
		if (!before.equals(store.json())) history.record(before);
		store.save();
		refreshDiagnostics();
	}

	private void undo() { restore(history.undo(store.json())); }
	private void redo() { restore(history.redo(store.json())); }
	private void restore(String snapshot) {
		if (snapshot == null) return;
		store.restoreJson(snapshot);
		store.save();
		selection.clear();
		refreshDiagnostics();
	}

	private void selectAll() {
		selection.clear();
		for (String id : bounds.keySet()) selection.toggle(id);
	}

	private void duplicateSelection() {
		if (selection.isEmpty()) return;
		mutate(() -> {
			List<String> duplicates = new ArrayList<>();
			for (String id : selection.all()) if (!isVanilla(id)) duplicates.add(store.duplicateWidget(id));
			selection.clear();
			for (String duplicate : duplicates) selection.toggle(duplicate);
		});
	}

	private void removeSelection() {
		if (selection.isEmpty()) return;
		mutate(() -> {
			for (String id : selection.all()) {
				if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).enabled = false;
				else store.removeWidget(id);
			}
			selection.clear();
		});
	}

	private void toggleLockSelection() {
		if (selection.isEmpty()) return;
		boolean lock = selection.all().stream().anyMatch(id -> !isLocked(id));
		mutate(() -> selection.all().forEach(id -> setLocked(id, lock)));
	}

	private void nudgeSelection(int dx, int dy) {
		for (String id : selection.all()) {
			if (isLocked(id)) continue;
			HudWidgetBounds box = bounds.get(id);
			if (box != null) setAbsolute(id, box.left() + dx, box.top() + dy);
		}
	}

	private void autoFix() {
		mutate(() -> {
			for (String id : selection.all()) {
				HudWidgetBounds moving = bounds.get(id);
				if (moving == null) continue;
				List<HudWidgetBounds> occupied = bounds.entrySet().stream()
						.filter(entry -> !entry.getKey().equals(id)).map(Map.Entry::getValue).toList();
				HudPoint free = collisionManager.nearestFree(moving, occupied, width, height, GRID_MINOR);
				setAbsolute(id, free.x(), free.y());
			}
		});
	}

	private void refreshDiagnostics() {
		safeAreas = safeAreaManager.calculate(width, height, store);
		refreshBounds();
		collisions = collisionManager.detect(bounds);
	}

	private void refreshBounds() {
		bounds.clear();
		for (Map.Entry<String, HudWidgetSettings> entry : store.snapshot().entrySet()) {
			HudWidgetSettings settings = store.settings(entry.getKey());
			if (!settings.enabled) continue;
			HudWidget widget = widgetFor(entry.getKey());
			int naturalWidth = widget.width(liveData);
			int naturalHeight = widget.height(liveData);
			if (settings.width < 0) settings.width = naturalWidth;
			if (settings.height < 0) settings.height = naturalHeight;
			HudPoint point = BlockeraHudOverlay.position(liveData, widget, settings, width, height);
			bounds.put(entry.getKey(), new HudWidgetBounds(point.x(), point.y(),
					point.x() + Math.round(naturalWidth * settings.scale),
					point.y() + Math.round(naturalHeight * settings.scale)));
		}
		for (VanillaHudElement element : VanillaHudElement.values()) {
			VanillaHudSettings settings = store.vanillaSettings(element.id());
			if (settings.width < 0) settings.width = element.width();
			if (settings.height < 0) settings.height = element.height();
			HudPoint point = settings.anchor.resolve(width, height, element.width(), element.height(), settings.scale,
					settings.offsetX, settings.offsetY);
			bounds.put(vanillaId(element), new HudWidgetBounds(point.x(), point.y(),
					point.x() + Math.round(element.width() * settings.scale),
					point.y() + Math.round(element.height() * settings.scale)));
		}
	}

	private String hitAt(double x, double y) {
		return bounds.entrySet().stream().filter(entry -> entry.getValue().contains(x, y))
				.max(Comparator.comparingInt(entry -> zIndex(entry.getKey()))).map(Map.Entry::getKey).orElse(null);
	}

	private List<HudEditorItem> libraryItems() {
		String query = search == null ? "" : search.value().trim().toLowerCase(Locale.ROOT);
		List<HudEditorItem> result = new ArrayList<>();
		for (Widget entry : BuiltinHudWidgets.registry().all()) {
			HudWidget widget = (HudWidget) entry;
			if ((category == null || widget.category() == category)
					&& (query.isEmpty() || widget.title().getString().toLowerCase(Locale.ROOT).contains(query))) {
				result.add(new HudEditorItem(widget.id(), widget.id(), widget.title(), widget.category(),
						widget.width(liveData), widget.height(liveData), false));
			}
		}
		if (category == null || category == HudCategory.PLAYER) {
			for (VanillaHudElement element : VanillaHudElement.values()) {
				Component title = Component.translatable("blockera.hud.vanilla." + element.id());
				if (query.isEmpty() || title.getString().toLowerCase(Locale.ROOT).contains(query)) {
					result.add(new HudEditorItem(vanillaId(element), vanillaId(element), title, HudCategory.PLAYER,
							element.width(), element.height(), true));
				}
			}
		}
		return result;
	}

	private HudEditorItem libraryCardAt(double mouseY) {
		int index = libraryScroll + ((int) mouseY - 119) / 27;
		List<HudEditorItem> cards = libraryItems();
		return index >= 0 && index < cards.size() ? cards.get(index) : null;
	}

	private boolean insideLibraryCards(double mouseX, double mouseY) {
		return mouseX >= 18 && mouseX <= 8 + LIBRARY_WIDTH - 10 && mouseY >= 119 && mouseY < height - 8;
	}

	private HudEditorItem itemFor(String id) {
		if (isVanilla(id)) {
			VanillaHudElement element = vanillaElement(id);
			return new HudEditorItem(id, id, Component.translatable("blockera.hud.vanilla." + element.id()),
					HudCategory.PLAYER, element.width(), element.height(), true);
		}
		HudWidget widget = widgetFor(id);
		return new HudEditorItem(id, HudLayoutStore.baseWidgetId(id), widget.title(), widget.category(),
				widget.width(liveData), widget.height(liveData), false);
	}

	private HudWidget widgetFor(String id) {
		return (HudWidget) BuiltinHudWidgets.registry().get(HudLayoutStore.baseWidgetId(id));
	}

	private void setAbsolute(String id, int x, int y) {
		HudEditorItem item = itemFor(id);
		HudPoint offsets = anchor(id).offsetsFor(x, y, width, height, item.width(), item.height(), scale(id));
		if (isVanilla(id)) {
			VanillaHudSettings settings = store.vanillaSettings(vanillaElement(id).id());
			settings.offsetX = offsets.x(); settings.offsetY = offsets.y();
		} else {
			HudWidgetSettings settings = store.settings(id);
			settings.offsetX = offsets.x(); settings.offsetY = offsets.y();
		}
	}

	private void setAnchor(String id, HudAnchor anchor) {
		if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).anchor = anchor;
		else store.settings(id).anchor = anchor;
	}

	private HudAnchor anchor(String id) {
		return isVanilla(id) ? store.vanillaSettings(vanillaElement(id).id()).anchor : store.settings(id).anchor;
	}

	private float scale(String id) {
		return isVanilla(id) ? store.vanillaSettings(vanillaElement(id).id()).scale : store.settings(id).scale;
	}

	private void setScale(String id, float value) {
		float clamped = Math.max(0.5F, Math.min(2.0F, Math.round(value * 100.0F) / 100.0F));
		if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).scale = clamped;
		else store.settings(id).scale = clamped;
	}

	private boolean isLocked(String id) {
		return isVanilla(id) ? store.vanillaSettings(vanillaElement(id).id()).locked : store.settings(id).locked;
	}

	private void setLocked(String id, boolean value) {
		if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).locked = value;
		else store.settings(id).locked = value;
	}

	private void setEnabled(String id, boolean value) {
		if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).enabled = value;
		else store.settings(id).enabled = value;
	}

	private boolean enabled(String id) {
		return isVanilla(id) ? store.vanillaSettings(vanillaElement(id).id()).enabled : store.settings(id).enabled;
	}

	private int zIndex(String id) {
		return isVanilla(id) ? store.vanillaSettings(vanillaElement(id).id()).zIndex : store.settings(id).zIndex;
	}

	private void setZIndex(String id, int value) {
		if (isVanilla(id)) store.vanillaSettings(vanillaElement(id).id()).zIndex = value;
		else store.settings(id).zIndex = value;
	}

	private void resetPosition(String id) {
		HudWidgetBounds current = bounds.get(id);
		if (isVanilla(id)) store.resetVanilla(vanillaElement(id).id()); else store.resetWidget(id);
		if (current != null && id.contains("#")) setAbsolute(id, current.left(), current.top());
	}

	private void cycleProfile() {
		List<String> profiles = store.profileNames();
		int next = (profiles.indexOf(store.activeProfile()) + 1) % profiles.size();
		store.setActiveProfile(profiles.get(next));
		selection.clear();
		store.save();
		refreshDiagnostics();
	}

	private void cycleCategory() {
		if (category == null) category = HudCategory.values()[0];
		else category = category.ordinal() + 1 < HudCategory.values().length ? HudCategory.values()[category.ordinal() + 1] : null;
	}

	private Component categoryLabel() {
		return category == null ? Component.translatable("blockera.hud.category.all") : Component.translatable(category.translationKey());
	}

	private void openContext(String id, int x, int y) {
		contextId = id;
		contextX = Math.max(4, Math.min(width - CONTEXT_WIDTH - 4, x));
		contextY = Math.max(40, Math.min(height - CONTEXT_ACTIONS.length * CONTEXT_ROW_HEIGHT - 8, y));
	}

	private boolean clickSelectionPanel(double mouseX, double mouseY) {
		if (selection.isEmpty()) return false;
		int left = width - INSPECTOR_WIDTH - 8;
		if (mouseX < left || mouseX > width - 8 || mouseY < 40 || mouseY > height - 8) return false;
		if (selection.size() != 1) return true;
		String id = selection.primary();
		int relative = (int) mouseY - (40 + INSPECTOR_ROW_TOP);
		if (relative < 0 || relative % INSPECTOR_ROW_STEP >= 20) return true;
		int row = relative / INSPECTOR_ROW_STEP;
		if (row == 0) { mutate(() -> setScale(id, scale(id) >= 2.0F ? 0.5F : scale(id) + 0.1F)); return true; }
		if (row == 1) { mutate(() -> cycleAnchor(id)); return true; }
		if (isVanilla(id)) {
			if (row == 2) mutate(() -> setEnabled(id, !enabled(id)));
			else if (row == 3) toggleLockSelection();
			else if (row == 4) removeSelection();
			return true;
		}
		HudWidgetSettings settings = store.settings(id);
		switch (row) {
			case 2 -> mutate(() -> settings.opacity = settings.opacity >= 0.99F ? 0.1F : Math.min(1.0F, settings.opacity + 0.1F));
			case 3 -> mutate(() -> settings.background = !settings.background);
			case 4 -> mutate(() -> settings.showLabel = !settings.showLabel);
			case 5 -> mutate(() -> settings.showIcon = !settings.showIcon);
			case 6 -> mutate(() -> settings.compact = !settings.compact);
			case 7 -> mutate(() -> settings.orientation = "horizontal".equals(settings.orientation) ? "vertical" : "horizontal");
			case 8 -> mutate(() -> settings.labelColor = nextColor(settings.labelColor));
			case 9 -> mutate(() -> settings.valueColor = nextColor(settings.valueColor));
			case 10 -> toggleLockSelection();
			case 11 -> removeSelection();
			default -> { }
		}
		return true;
	}

	private void cycleAnchor(String id) {
		HudWidgetBounds current = bounds.get(id);
		HudAnchor[] anchors = HudAnchor.values();
		setAnchor(id, anchors[(anchor(id).ordinal() + 1) % anchors.length]);
		if (current != null) setAbsolute(id, current.left(), current.top());
	}

	private static String nextColor(String current) {
		for (int index = 0; index < COLOR_PRESETS.length; index++) {
			if (COLOR_PRESETS[index].equalsIgnoreCase(current)) return COLOR_PRESETS[(index + 1) % COLOR_PRESETS.length];
		}
		return COLOR_PRESETS[0];
	}

	private static String state(boolean value) {
		return Component.translatable(value ? "blockera.state.enabled" : "blockera.state.disabled").getString();
	}

	private void closeContext() { contextId = null; contextX = -1; contextY = -1; }
	private boolean insideContext(double x, double y) {
		return x >= contextX && x <= contextX + CONTEXT_WIDTH && y >= contextY
				&& y <= contextY + CONTEXT_ACTIONS.length * CONTEXT_ROW_HEIGHT + 4;
	}

	private Set<String> collidingIds() {
		java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
		for (HudCollisionManager.Collision collision : collisions) {
			result.add(collision.firstId()); result.add(collision.secondId());
		}
		return result;
	}

	private void drawAnchorPoint(PoseStack poseStack, String id, HudWidgetBounds box) {
		HudPoint point = anchor(id).resolve(width, height, 0, 0, 1.0F, 0, 0);
		int x = clamp(point.x(), box.left(), box.right());
		int y = clamp(point.y(), box.top(), box.bottom());
		BlockeraDraw.roundedRect(poseStack, x - 2, y - 2, x + 3, y + 3, 2, THEME.accentArgb());
	}

	private static void drawOutline(PoseStack poseStack, HudWidgetBounds box, int color) {
		GuiComponent.fill(poseStack, box.left(), box.top(), box.right(), box.top() + 1, color);
		GuiComponent.fill(poseStack, box.left(), box.bottom() - 1, box.right(), box.bottom(), color);
		GuiComponent.fill(poseStack, box.left(), box.top(), box.left() + 1, box.bottom(), color);
		GuiComponent.fill(poseStack, box.right() - 1, box.top(), box.right(), box.bottom(), color);
	}

	private void drawChip(PoseStack poseStack, int x, int y, int chipWidth, String key, boolean accent) {
		BlockeraDraw.roundedRect(poseStack, x, y, x + chipWidth, y + 21, 5, accent ? THEME.accentArgb() : THEME.cardArgb());
		UiFont.drawCentered(poseStack, UiFont.ellipsize(Component.translatable(key), chipWidth - 8, accent),
				x + chipWidth / 2.0F, y + 7, accent ? THEME.textPrimaryArgb() : THEME.textMutedArgb());
	}

	private void acknowledgeHint() {
		if (!ClientConfig.HUD_EDITOR_HINT_SEEN.get()) {
			ClientConfig.HUD_EDITOR_HINT_SEEN.set(true);
			ClientConfig.HUD_EDITOR_HINT_SEEN.save();
		}
	}

	private static boolean isVanilla(String id) { return id != null && id.startsWith(VANILLA_PREFIX); }
	private static VanillaHudElement vanillaElement(String id) { return VanillaHudElement.byId(id.substring(VANILLA_PREFIX.length())); }
	private static String vanillaId(VanillaHudElement element) { return VANILLA_PREFIX + element.id(); }
	private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private void saveAndClose() { store.save(); minecraft.setScreen(parent); }
	@Override public void onClose() { saveAndClose(); }
	@Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
