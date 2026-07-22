package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatConfig;
import space.blockera.core.chat.ChatConfigStore;
import space.blockera.core.chat.ChatTab;

import java.util.List;

/** Independent, draggable first-party views for filtered chat tabs. */
public final class DetachedChatPanels {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final int HEADER_HEIGHT = 18;
	private static final int LINE_HEIGHT = 10;
	private static String draggingId;
	private static int pressX;
	private static int pressY;
	private static int startX;
	private static int startY;

	private DetachedChatPanels() { }

	public static void render(PoseStack poseStack, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		ChatConfig config = BlockeraChatRuntime.instance().config();
		for (ChatTab tab : config.tabs()) {
			if (!tab.detached() || ChatTab.ALL_ID.equals(tab.id())) continue;
			Bounds bounds = bounds(tab, screenWidth, screenHeight);
			boolean headerHovered = inside(mouseX, mouseY, bounds.left, bounds.top, bounds.right,
					bounds.top + HEADER_HEIGHT);
			BlockeraDraw.glassPanel(poseStack, bounds.left, bounds.top, bounds.right, bounds.bottom, 7,
					0xC812141D, headerHovered || tab.id().equals(draggingId) ? THEME.borderHoverArgb() : THEME.borderArgb());
			BlockeraDraw.roundedRect(poseStack, bounds.left + 1, bounds.top + 1, bounds.right - 1,
					bounds.top + HEADER_HEIGHT, 6, 0xD8000000 | (tab.color() & 0x00FFFFFF));
			UiFont.drawSemibold(poseStack, Component.literal(tab.name()), bounds.left + 8, bounds.top + 5,
					THEME.textPrimaryArgb());
			UiFont.drawSmall(poseStack, Component.literal("×"), bounds.right - 13, bounds.top + 5,
					headerHovered ? THEME.textPrimaryArgb() : THEME.textMutedArgb());

			int availableLines = Math.max(1, (bounds.height() - HEADER_HEIGHT - 7) / LINE_HEIGHT);
			List<Component> messages = BlockeraChatRuntime.instance().history(tab.id(), availableLines);
			int y = bounds.bottom - 6 - messages.size() * LINE_HEIGHT;
			for (Component message : messages) {
				UiFont.drawSmall(poseStack, UiFont.ellipsize(message, bounds.width() - 12, false),
						bounds.left + 6, y, 0xFFF8F7FF);
				y += LINE_HEIGHT;
			}
		}
	}

	public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
		if (button != 0) return false;
		List<ChatTab> tabs = BlockeraChatRuntime.instance().config().tabs();
		for (int index = tabs.size() - 1; index >= 0; index--) {
			ChatTab tab = tabs.get(index);
			if (!tab.detached()) continue;
			Bounds bounds = bounds(tab, screenWidth, screenHeight);
			if (!inside(mouseX, mouseY, bounds.left, bounds.top, bounds.right, bounds.top + HEADER_HEIGHT)) continue;
			if (mouseX >= bounds.right - 20) {
				tab.setDetached(false);
				ChatConfigStore.instance().save();
				return true;
			}
			draggingId = tab.id();
			pressX = (int) Math.round(mouseX);
			pressY = (int) Math.round(mouseY);
			startX = bounds.left;
			startY = bounds.top;
			return true;
		}
		return false;
	}

	public static boolean mouseDragged(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
		if (button != 0 || draggingId == null) return false;
		ChatTab tab = BlockeraChatRuntime.instance().config().tab(draggingId);
		int maximumX = Math.max(0, screenWidth - tab.detachedWidth());
		int maximumY = Math.max(0, screenHeight - tab.detachedHeight());
		int x = snap(clamp(startX + (int) Math.round(mouseX) - pressX, 0, maximumX));
		int y = snap(clamp(startY + (int) Math.round(mouseY) - pressY, 0, maximumY));
		tab.setDetachedPosition(x, y);
		return true;
	}

	public static boolean mouseReleased(int button) {
		if (button != 0 || draggingId == null) return false;
		draggingId = null;
		ChatConfigStore.instance().save();
		return true;
	}

	private static Bounds bounds(ChatTab tab, int screenWidth, int screenHeight) {
		int width = Math.min(tab.detachedWidth(), Math.max(120, screenWidth));
		int height = Math.min(tab.detachedHeight(), Math.max(56, screenHeight));
		int left = clamp(tab.detachedX(), 0, Math.max(0, screenWidth - width));
		int top = clamp(tab.detachedY(), 0, Math.max(0, screenHeight - height));
		return new Bounds(left, top, left + width, top + height);
	}

	private static int snap(int value) { return Math.round(value / 8.0F) * 8; }
	private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}
	private record Bounds(int left, int top, int right, int bottom) {
		int width() { return right - left; }
		int height() { return bottom - top; }
	}
}
