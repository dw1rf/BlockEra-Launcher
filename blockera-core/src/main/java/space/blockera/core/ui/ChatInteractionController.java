package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatConfig;
import space.blockera.core.chat.ChatConfigStore;
import space.blockera.core.chat.ChatTab;
import space.blockera.core.chat.ChatTimestampMode;
import space.blockera.core.chat.ChatVanillaOptions;
import space.blockera.core.hud.HudAnchor;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.hud.HudPoint;
import space.blockera.core.hud.VanillaHudElement;
import space.blockera.core.hud.VanillaHudSettings;

/** Non-modal interaction layer hosted by the open native ChatScreen. */
public final class ChatInteractionController {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final int POPUP_WIDTH = 288;
	private static final int POPUP_HEIGHT = 244;
	private static final int HANDLE_HEIGHT = 12;
	private static final int RESIZE_SIZE = 12;
	private static boolean popup;
	private static final UiAnimation popupMotion = new UiAnimation(0.0F);
	private static int popupX;
	private static int popupY;
	private static Page page = Page.APPEARANCE;
	private static Mode mode = Mode.NONE;
	private static double pressX;
	private static double pressY;
	private static int startLeft;
	private static int startTop;
	private static int startWidth;
	private static int startHeight;

	private enum Page { APPEARANCE, POSITION, TABS, FILTERS }
	private enum Mode { NONE, DRAG, RESIZE }

	private ChatInteractionController() { }

	public static void render(PoseStack poseStack, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		Bounds bounds = bounds(screenWidth, screenHeight);
		boolean hoverHandle = inside(mouseX, mouseY, bounds.left, bounds.top, bounds.right, bounds.top + HANDLE_HEIGHT);
		if (hoverHandle || mode == Mode.DRAG) {
			BlockeraDraw.roundedRect(poseStack, bounds.left, bounds.top, Math.min(bounds.right, bounds.left + 72),
					bounds.top + 6, 3, 0xA09B7BFF);
		}
		BlockeraDraw.roundedRect(poseStack, bounds.right - RESIZE_SIZE, bounds.bottom - RESIZE_SIZE,
				bounds.right, bounds.bottom, 3, 0x909B7BFF);
		float popupVisibility = popupMotion.update(popup ? 1.0F : 0.0F, UiMotionTokens.POPUP_MILLIS);
		if (!popup && popupVisibility <= 0.001F) return;
		int right = popupX + Math.min(POPUP_WIDTH, screenWidth - 16);
		int bottom = popupY + Math.min(POPUP_HEIGHT, screenHeight - 16);
		BlockeraDraw.glassPanel(poseStack, popupX, popupY, right, bottom, 10, 0xEA12141D, THEME.borderHoverArgb());
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.chat.popup.title"), popupX + 12, popupY + 11, THEME.textPrimaryArgb());
		renderTabs(poseStack, mouseX, mouseY, right);
		renderPage(poseStack, mouseX, mouseY, right, bottom);
	}

	private static void renderTabs(PoseStack poseStack, int mouseX, int mouseY, int right) {
		int x = popupX + 10;
		for (Page value : Page.values()) {
			int w = Math.max(54, (right - popupX - 24) / 4);
			boolean hover = inside(mouseX, mouseY, x, popupY + 30, x + w, popupY + 50);
			BlockeraDraw.roundedRect(poseStack, x, popupY + 30, x + w, popupY + 50, 5,
					page == value ? THEME.accentSoftArgb() : hover ? THEME.cardHoverArgb() : THEME.glassCardArgb());
			UiFont.drawCentered(poseStack, Component.translatable("blockera.chat.popup." + value.name().toLowerCase()),
					x + w / 2.0F, popupY + 36, page == value ? THEME.textPrimaryArgb() : THEME.textMutedArgb());
			x += w + 1;
		}
	}

	private static void renderPage(PoseStack poseStack, int mouseX, int mouseY, int right, int bottom) {
		ChatConfig config = ChatConfigStore.instance().config();
		int y = popupY + 60;
		if (page == Page.APPEARANCE) {
			row(poseStack, y, "blockera.chat.text_opacity", Math.round(config.textOpacity() * 100) + "%", mouseX, mouseY, right); y += 32;
			row(poseStack, y, "blockera.chat.background_opacity", Math.round(config.backgroundOpacity() * 100) + "%", mouseX, mouseY, right); y += 32;
			row(poseStack, y, "blockera.chat.timestamp", config.timestampMode().name(), mouseX, mouseY, right);
		} else if (page == Page.POSITION) {
			VanillaHudSettings settings = settings();
			row(poseStack, y, "blockera.vanilla_settings.position", settings.anchor.name(), mouseX, mouseY, right); y += 32;
			row(poseStack, y, "blockera.chat.width", config.width() + " px", mouseX, mouseY, right); y += 32;
			row(poseStack, y, "blockera.chat.open_height", config.openHeight() + " px", mouseX, mouseY, right); y += 32;
			row(poseStack, y, "blockera.widget_settings.locked", settings.locked ? "ON" : "OFF", mouseX, mouseY, right);
		} else if (page == Page.TABS) {
			for (ChatTab tab : config.tabs()) {
				if (y + 28 > bottom - 10) break;
				String value = ChatTab.ALL_ID.equals(tab.id()) ? tab.matchMode().name()
						: tab.matchMode().name() + " · " + (tab.detached()
						? Component.translatable("blockera.chat.detached").getString()
						: Component.translatable("blockera.chat.attached").getString());
				row(poseStack, y, tab.name(), value, mouseX, mouseY, right); y += 30;
			}
		} else {
			config.filters().stream().limit(5).forEach(filter -> { });
			for (var filter : config.filters()) {
				if (y + 28 > bottom - 10) break;
				row(poseStack, y, filter.name(), filter.enabled() ? "ON" : "OFF", mouseX, mouseY, right); y += 30;
			}
			if (config.filters().isEmpty()) UiFont.drawSmall(poseStack, Component.translatable("blockera.chat.popup.no_filters"), popupX + 12, y, THEME.textMutedArgb());
		}
	}

	private static void row(PoseStack poseStack, int y, String key, String value, int mouseX, int mouseY, int right) {
		boolean hover = inside(mouseX, mouseY, popupX + 10, y, right - 10, y + 27);
		BlockeraDraw.roundedRect(poseStack, popupX + 10, y, right - 10, y + 27, 6, hover ? THEME.cardHoverArgb() : THEME.glassCardArgb());
		Component label = key.startsWith("blockera.") ? Component.translatable(key) : Component.literal(key);
		UiFont.drawSmall(poseStack, label, popupX + 18, y + 9, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.literal(value), right - UiFont.width(Component.literal(value)) - 18, y + 9, THEME.textMutedArgb());
	}

	public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
		if (popup && button == 0) {
			if (!inside(mouseX, mouseY, popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT)) { popup = false; return false; }
			int tabY = popupY + 30;
			if (mouseY >= tabY && mouseY < tabY + 20) {
				int index = Math.max(0, Math.min(3, ((int) mouseX - popupX - 10) / Math.max(1, (POPUP_WIDTH - 24) / 4 + 1)));
				page = Page.values()[index]; return true;
			}
			if (handlePopupRow(mouseX, mouseY)) return true;
			return true;
		}
		Bounds bounds = bounds(screenWidth, screenHeight);
		if (button == 1 && inside(mouseX, mouseY, bounds.left, bounds.top - 22, bounds.right, bounds.bottom)) {
			popupX = clamp((int) mouseX, 8, Math.max(8, screenWidth - POPUP_WIDTH - 8));
			popupY = clamp((int) mouseY, 8, Math.max(8, screenHeight - POPUP_HEIGHT - 8));
			popup = true; return true;
		}
		if (button != 0 || settings().locked) return false;
		if (inside(mouseX, mouseY, bounds.right - RESIZE_SIZE, bounds.bottom - RESIZE_SIZE, bounds.right, bounds.bottom)) {
			start(Mode.RESIZE, mouseX, mouseY, bounds); return true;
		}
		if (inside(mouseX, mouseY, bounds.left, bounds.top, Math.min(bounds.right, bounds.left + 96), bounds.top + HANDLE_HEIGHT)) {
			start(Mode.DRAG, mouseX, mouseY, bounds); return true;
		}
		return false;
	}

	private static boolean handlePopupRow(double mouseX, double mouseY) {
		int row = ((int) mouseY - popupY - 60) / 32;
		if (row < 0 || mouseX < popupX + 10 || mouseX > popupX + POPUP_WIDTH - 10) return false;
		ChatConfig config = ChatConfigStore.instance().config();
		if (page == Page.APPEARANCE) {
			if (row == 0) config.setTextOpacity(step(config.textOpacity()));
			else if (row == 1) config.setBackgroundOpacity(step(config.backgroundOpacity()));
			else if (row == 2) {
				ChatTimestampMode[] modes = ChatTimestampMode.values();
				config.setTimestampMode(modes[(config.timestampMode().ordinal() + 1) % modes.length]);
			} else return false;
			saveChat(); return true;
		}
		if (page == Page.POSITION) {
			if (row == 0) {
				HudAnchor[] anchors = HudAnchor.values(); settings().anchor = anchors[(settings().anchor.ordinal() + 1) % anchors.length]; saveHud();
			} else if (row == 3) { settings().locked = !settings().locked; saveHud(); }
			else return false;
			return true;
		}
		if (page == Page.TABS && row < config.tabs().size()) {
			ChatTab tab = config.tabs().get(row);
			if (!ChatTab.ALL_ID.equals(tab.id())) {
				tab.setDetached(!tab.detached());
				if (tab.detached() && tab.id().equals(config.activeTab())) config.setActiveTab(ChatTab.ALL_ID);
			}
			saveChat(); return true;
		}
		if (page == Page.FILTERS && row < config.filters().size()) {
			var filter = config.filters().get(row); filter.setEnabled(!filter.enabled()); saveChat(); return true;
		}
		return false;
	}

	private static void start(Mode next, double x, double y, Bounds bounds) {
		mode = next; pressX = x; pressY = y; startLeft = bounds.left; startTop = bounds.top;
		startWidth = ChatConfigStore.instance().config().width(); startHeight = ChatConfigStore.instance().config().openHeight();
	}

	public static boolean mouseDragged(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
		if (button != 0 || mode == Mode.NONE) return false;
		if (mode == Mode.DRAG) {
			int x = clamp(startLeft + (int) Math.round(mouseX - pressX), 0, Math.max(0, screenWidth - bounds(screenWidth, screenHeight).width()));
			int y = clamp(startTop + (int) Math.round(mouseY - pressY), 0, Math.max(0, screenHeight - bounds(screenWidth, screenHeight).height()));
			x = Math.round(x / 8.0F) * 8; y = Math.round(y / 8.0F) * 8;
			setAbsolute(x, y, screenWidth, screenHeight);
		} else {
			ChatConfig config = ChatConfigStore.instance().config();
			config.setWidth(clamp(startWidth + (int) Math.round((mouseX - pressX) / settings().scale), 80, Math.max(80, screenWidth - 16)));
			config.setOpenHeight(clamp(startHeight + (int) Math.round((mouseY - pressY) / settings().scale), 40, Math.max(40, screenHeight - 40)));
			ChatVanillaOptions.apply();
		}
		return true;
	}

	public static boolean mouseReleased(int button) {
		if (button != 0 || mode == Mode.NONE) return false;
		if (mode == Mode.RESIZE) saveChat(); else saveHud();
		mode = Mode.NONE; return true;
	}

	public static boolean escape() {
		if (!popup) return false;
		popup = false; return true;
	}

	private static Bounds bounds(int screenWidth, int screenHeight) {
		ChatConfig config = ChatConfigStore.instance().config();
		VanillaHudSettings settings = settings();
		HudPoint target = settings.anchor.resolve(screenWidth, screenHeight, VanillaHudElement.CHAT.width(),
				VanillaHudElement.CHAT.height(), settings.scale, settings.offsetX, settings.offsetY);
		int width = Math.round(config.width() * settings.scale);
		int height = Math.round(config.openHeight() * settings.scale);
		int bottom = target.y() + Math.round(VanillaHudElement.CHAT.height() * settings.scale);
		return new Bounds(target.x(), bottom - height, target.x() + width, bottom);
	}

	private static void setAbsolute(int left, int top, int screenWidth, int screenHeight) {
		VanillaHudSettings settings = settings();
		Bounds current = bounds(screenWidth, screenHeight);
		int targetY = top + current.height() - Math.round(VanillaHudElement.CHAT.height() * settings.scale);
		HudPoint offsets = settings.anchor.offsetsFor(left, targetY, screenWidth, screenHeight,
				VanillaHudElement.CHAT.width(), VanillaHudElement.CHAT.height(), settings.scale);
		settings.offsetX = offsets.x(); settings.offsetY = offsets.y();
	}

	private static VanillaHudSettings settings() { return HudLayoutStore.instance().vanillaSettings(VanillaHudElement.CHAT.id()); }
	private static void saveHud() { HudLayoutStore.instance().save(); }
	private static void saveChat() { ChatConfigStore.instance().save(); BlockeraChatRuntime.instance().refreshConfiguration(); ChatVanillaOptions.apply(); }
	private static float step(float value) { return value >= 0.95F ? 0.1F : Math.min(1.0F, value + 0.1F); }
	private static boolean inside(double x, double y, int left, int top, int right, int bottom) { return x >= left && x < right && y >= top && y < bottom; }
	private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
	private record Bounds(int left, int top, int right, int bottom) { int width() { return right - left; } int height() { return bottom - top; } }
}
