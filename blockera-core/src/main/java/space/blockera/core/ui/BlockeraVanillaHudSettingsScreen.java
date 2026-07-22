package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.core.hud.HudAnchor;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.hud.VanillaHudSettings;

/** Full geometry and visibility settings for an allow-listed vanilla HUD group. */
public class BlockeraVanillaHudSettingsScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	protected final Screen parent;
	protected final VanillaHudElement element;
	protected final HudLayoutStore store = HudLayoutStore.instance();
	protected int left;
	protected int top;
	protected int right;
	protected int bottom;

	public BlockeraVanillaHudSettingsScreen(Screen parent, VanillaHudElement element, Component title) {
		super(title);
		this.parent = parent;
		this.element = element;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(620, width - 24);
		int panelHeight = Math.min(350, height - 24);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		addRenderableWidget(new BlockeraButton(left + 14, top + 14, 82, 24,
				Component.translatable("blockera.common.back"), button -> onClose()));
		addRenderableWidget(new BlockeraButton(right - 118, top + 14, 104, 24,
				stateLabel(), button -> { settings().enabled = !settings().enabled; button.setMessage(stateLabel()); save(); }, true));
		int rowTop = top + 104;
		addRenderableWidget(new BlockeraButton(left + 198, rowTop + 8, 34, 22,
				Component.literal("−"), button -> changeScale(-0.05F)));
		addRenderableWidget(new BlockeraButton(left + 294, rowTop + 8, 34, 22,
				Component.literal("+"), button -> changeScale(0.05F)));
		addRenderableWidget(new BlockeraButton(left + 14, bottom - 38, 110, 24,
				Component.translatable("blockera.common.reset"), button -> { store.resetVanilla(element.id()); save(); rebuildWidgets(); }));
		addRenderableWidget(new BlockeraButton(right - 114, bottom - 38, 100, 24,
				Component.translatable("blockera.common.done"), button -> onClose(), true));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, THEME.gameBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, 14, 0xC40E111A, THEME.borderHoverArgb());
		UiFont.drawSemibold(poseStack, title, left + 112, top + 21, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.vanilla_settings.subtitle"),
				left + 112, top + 36, THEME.textMutedArgb());
		int rowTop = top + 68;
		row(poseStack, rowTop, "blockera.vanilla_settings.position",
				Component.literal(settings().anchor.name() + "  " + settings().offsetX + ", " + settings().offsetY), mouseX, mouseY);
		rowTop += 44;
		row(poseStack, rowTop, "blockera.hud.editor.scale",
				Component.literal(Math.round(settings().scale * 100.0F) + "%"), mouseX, mouseY);
		rowTop += 44;
		row(poseStack, rowTop, "blockera.widget_settings.anchor",
				Component.translatable("blockera.hud.anchor." + settings().anchor.name().toLowerCase()), mouseX, mouseY);
		rowTop += 44;
		row(poseStack, rowTop, "blockera.widget_settings.locked",
				Component.translatable(settings().locked ? "blockera.state.enabled" : "blockera.state.disabled"), mouseX, mouseY);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	protected void row(PoseStack poseStack, int rowTop, String key, Component value, int mouseX, int mouseY) {
		int rowLeft = left + 14;
		int rowRight = right - 14;
		boolean hovered = mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowTop + 36;
		BlockeraDraw.glassPanel(poseStack, rowLeft, rowTop, rowRight, rowTop + 36, 7,
				hovered ? THEME.cardHoverArgb() : THEME.glassCardArgb(), THEME.borderArgb());
		UiFont.drawSemibold(poseStack, Component.translatable(key), rowLeft + 11, rowTop + 10, THEME.textPrimaryArgb());
		int valueWidth = UiFont.width(value);
		UiFont.drawSmall(poseStack, value, rowRight - valueWidth - 11, rowTop + 13, THEME.textMutedArgb());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int rowTop = top + 68;
			if (inside(mouseX, mouseY, left + 14, rowTop, right - 14, rowTop + 36)) {
				cycleAnchor(); return true;
			}
			rowTop += 88;
			if (inside(mouseX, mouseY, left + 14, rowTop, right - 14, rowTop + 36)) {
				cycleAnchor(); return true;
			}
			rowTop += 44;
			if (inside(mouseX, mouseY, left + 14, rowTop, right - 14, rowTop + 36)) {
				settings().locked = !settings().locked; save(); return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	protected final VanillaHudSettings settings() { return store.vanillaSettings(element.id()); }
	protected final void save() { store.save(); }
	private Component stateLabel() { return Component.translatable(settings().enabled ? "blockera.state.enabled" : "blockera.state.disabled"); }
	private void changeScale(float delta) { settings().scale = Math.max(0.5F, Math.min(2.0F, settings().scale + delta)); save(); }
	private void cycleAnchor() {
		HudAnchor[] anchors = HudAnchor.values();
		settings().anchor = anchors[(settings().anchor.ordinal() + 1) % anchors.length];
		save();
	}
	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	@Override public void onClose() { save(); minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
