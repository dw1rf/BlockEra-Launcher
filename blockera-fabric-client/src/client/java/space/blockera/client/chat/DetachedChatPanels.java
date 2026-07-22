package space.blockera.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import space.blockera.client.BlockeraCoreServices;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standalone filter chats. Every filter window has its own persistent free-form geometry. */
public final class DetachedChatPanels {
	private static final int HEADER_HEIGHT = 18;
	private static final int RESIZE_SIZE = 12;
	private static final int PADDING = 5;
	private static String activeTabId;
	private static Mode mode = Mode.NONE;
	private static double pressX;
	private static double pressY;
	private static int startLeft;
	private static int startTop;
	private static int startWidth;
	private static int startHeight;

	private enum Mode { NONE, DRAG, RESIZE }

	private DetachedChatPanels() {
	}

	public static void render(GuiGraphics graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui == null || minecraft.options.hideGui) return;
		boolean focused = minecraft.screen instanceof ChatScreen;
		int mouseX = focused ? (int) Math.round(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow())) : -1;
		int mouseY = focused ? (int) Math.round(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())) : -1;
		int index = 0;
		for (ChatTab tab : BlockeraChatRuntime.instance().config().tabs()) {
			if (!tab.detached()) continue;
			Bounds bounds = bounds(tab, index++, graphics.guiWidth(), graphics.guiHeight());
			renderPanel(graphics, tab, bounds, mouseX, mouseY, focused);
		}
	}

	private static void renderPanel(GuiGraphics graphics, ChatTab tab, Bounds bounds,
		int mouseX, int mouseY, boolean focused) {
		boolean hovered = inside(mouseX, mouseY, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
		int panel = focused || hovered ? ThemeTokens.PANEL : alpha(ThemeTokens.PANEL_SOFT, 0.78F);
		if (tab.background()) {
			BlockeraDraw.roundedBorder(graphics, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
				ThemeTokens.RADIUS, focused ? ThemeTokens.BORDER_STRONG : ThemeTokens.BORDER, panel);
		}
		if (tab.background() || focused || hovered) {
			BlockeraDraw.roundedRect(graphics, bounds.left(), bounds.top(), bounds.right(),
				bounds.top() + HEADER_HEIGHT, ThemeTokens.RADIUS,
				focused ? ThemeTokens.CARD : tab.background() ? 0x8F25272A : 0x6625272A);
		}
		graphics.fill(bounds.left(), bounds.top(), bounds.left() + 3, bounds.top() + HEADER_HEIGHT, tab.color());
		UiText.drawSemibold(graphics, Component.literal(tab.name()), bounds.left() + 9, bounds.top() + 5,
			ThemeTokens.TEXT);
		if (focused) {
			UiText.draw(graphics, Component.literal("::"), bounds.right() - 18, bounds.top() + 5, ThemeTokens.DIM);
			graphics.fill(bounds.right() - RESIZE_SIZE, bounds.bottom() - 2, bounds.right(), bounds.bottom(),
				ThemeTokens.ACCENT);
			graphics.fill(bounds.right() - 2, bounds.bottom() - RESIZE_SIZE, bounds.right(), bounds.bottom(),
				ThemeTokens.ACCENT);
		}
		renderMessages(graphics, tab, bounds);
	}

	private static void renderMessages(GuiGraphics graphics, ChatTab tab, Bounds bounds) {
		Font font = Minecraft.getInstance().font;
		int left = bounds.left() + PADDING;
		int top = bounds.top() + HEADER_HEIGHT + PADDING;
		int right = bounds.right() - PADDING;
		int bottom = bounds.bottom() - PADDING;
		if (right <= left || bottom <= top) return;
		graphics.enableScissor(left, top, right, bottom);
		int y = bottom - font.lineHeight;
		List<ChatRoutingResult<ChatMessagePayload>> history = BlockeraChatRuntime.instance().history(tab.id());
		for (int messageIndex = history.size() - 1; messageIndex >= 0 && y >= top; messageIndex--) {
			List<FormattedCharSequence> lines = font.split(history.get(messageIndex).message().component(), right - left);
			for (int lineIndex = lines.size() - 1; lineIndex >= 0 && y >= top; lineIndex--) {
				graphics.drawString(font, lines.get(lineIndex), left, y, 0xFFFFFFFF, true);
				y -= font.lineHeight;
			}
		}
		graphics.disableScissor();
	}

	public static boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		List<ChatTab> tabs = new ArrayList<>(BlockeraChatRuntime.instance().config().tabs());
		Collections.reverse(tabs);
		for (ChatTab tab : tabs) {
			if (!tab.detached()) continue;
			int originalIndex = detachedIndex(tab.id());
			Bounds bounds = bounds(tab, originalIndex, screenWidth, screenHeight);
			if (inside(mouseX, mouseY, bounds.right() - RESIZE_SIZE, bounds.bottom() - RESIZE_SIZE,
				bounds.right(), bounds.bottom())) {
				begin(tab, Mode.RESIZE, bounds, mouseX, mouseY);
				return true;
			}
			if (inside(mouseX, mouseY, bounds.left(), bounds.top(), bounds.right(),
				bounds.top() + HEADER_HEIGHT)) {
				begin(tab, Mode.DRAG, bounds, mouseX, mouseY);
				return true;
			}
		}
		return false;
	}

	public static boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		if (mode == Mode.NONE || activeTabId == null) return false;
		ChatTab tab = BlockeraChatRuntime.instance().config().tab(activeTabId);
		if (mode == Mode.DRAG) {
			int left = clamp(startLeft + (int) Math.round(mouseX - pressX), 0,
				Math.max(0, screenWidth - startWidth));
			int top = clamp(startTop + (int) Math.round(mouseY - pressY), 0,
				Math.max(0, screenHeight - startHeight));
			tab.setWindowBounds(left, top, startWidth, startHeight);
		} else {
			int width = clamp(startWidth + (int) Math.round(mouseX - pressX), 140,
				Math.max(140, screenWidth - startLeft));
			int height = clamp(startHeight + (int) Math.round(mouseY - pressY), 70,
				Math.max(70, screenHeight - startTop));
			tab.setWindowBounds(startLeft, startTop, width, height);
		}
		return true;
	}

	public static boolean mouseReleased() {
		if (mode == Mode.NONE) return false;
		mode = Mode.NONE;
		activeTabId = null;
		BlockeraCoreServices.chat().save();
		return true;
	}

	private static void begin(ChatTab tab, Mode next, Bounds bounds, double mouseX, double mouseY) {
		activeTabId = tab.id();
		mode = next;
		pressX = mouseX;
		pressY = mouseY;
		startLeft = bounds.left();
		startTop = bounds.top();
		startWidth = bounds.width();
		startHeight = bounds.height();
		tab.setWindowBounds(startLeft, startTop, startWidth, startHeight);
	}

	private static Bounds bounds(ChatTab tab, int index, int screenWidth, int screenHeight) {
		int width = Math.min(tab.windowWidth(), Math.max(140, screenWidth));
		int height = Math.min(tab.windowHeight(), Math.max(70, screenHeight));
		int defaultLeft = Math.max(0, screenWidth - width - 8 - index * 18);
		int defaultTop = Math.min(Math.max(8, 36 + index * 26), Math.max(0, screenHeight - height));
		int left = clamp(tab.windowLeft() < 0 ? defaultLeft : tab.windowLeft(), 0,
			Math.max(0, screenWidth - width));
		int top = clamp(tab.windowTop() < 0 ? defaultTop : tab.windowTop(), 0,
			Math.max(0, screenHeight - height));
		return new Bounds(left, top, left + width, top + height);
	}

	private static int detachedIndex(String tabId) {
		int index = 0;
		for (ChatTab tab : BlockeraChatRuntime.instance().config().tabs()) {
			if (!tab.detached()) continue;
			if (tab.id().equals(tabId)) return index;
			index++;
		}
		return index;
	}

	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int alpha(int argb, float opacity) {
		int sourceAlpha = argb >>> 24;
		int resultAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * opacity)));
		return resultAlpha << 24 | argb & 0x00FFFFFF;
	}

	private record Bounds(int left, int top, int right, int bottom) {
		int width() { return right - left; }
		int height() { return bottom - top; }
	}
}
