package space.blockera.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import space.blockera.client.BlockeraCoreServices;

/** Direct manipulation for the open chat: header drag and lower-right resize with the left mouse button. */
public final class ChatInteractionController {
	private static final int RESIZE_SIZE = 12;
	private static Mode mode = Mode.NONE;
	private static double pressX;
	private static double pressY;
	private static int startLeft;
	private static int startTop;
	private static int startWidth;
	private static int startHeight;

	private enum Mode { NONE, DRAG, RESIZE }

	private ChatInteractionController() {
	}

	public static boolean begin(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		ChatLayout.Bounds bounds = ChatLayout.bounds(screenWidth, screenHeight);
		if (inside(mouseX, mouseY, bounds.right() - RESIZE_SIZE, bounds.bottom() - RESIZE_SIZE,
			bounds.right(), bounds.bottom())) {
			start(Mode.RESIZE, mouseX, mouseY, bounds);
			return true;
		}
		ChatLayout.Bounds header = ChatLayout.headerBounds(screenWidth, screenHeight);
		if (inside(mouseX, mouseY, header.left(), header.top(), header.right(), header.bottom())) {
			start(Mode.DRAG, mouseX, mouseY, bounds);
			return true;
		}
		return false;
	}

	public static void tick(Minecraft minecraft) {
		if (mode == Mode.NONE) return;
		if (!(minecraft.screen instanceof ChatScreen) || !minecraft.mouseHandler.isLeftPressed()) {
			finish(minecraft);
			return;
		}
		var window = minecraft.getWindow();
		update(minecraft.mouseHandler.getScaledXPos(window), minecraft.mouseHandler.getScaledYPos(window),
			window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}

	public static boolean drag(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		if (mode == Mode.NONE) return false;
		update(mouseX, mouseY, screenWidth, screenHeight);
		return true;
	}

	public static boolean release(Minecraft minecraft) {
		if (mode == Mode.NONE) return false;
		finish(minecraft);
		return true;
	}

	private static void update(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		ChatConfig config = BlockeraCoreServices.chat().config();
		if (mode == Mode.DRAG) {
			int width = Math.min(config.width(), screenWidth);
			int height = Math.min(config.focusedHeight(), screenHeight);
			int left = clamp(startLeft + (int) Math.round(mouseX - pressX), 0,
				Math.max(0, screenWidth - width));
			int top = clamp(startTop + (int) Math.round(mouseY - pressY), 0,
				Math.max(0, screenHeight - height));
			config.setPosition(left, screenHeight - (top + height));
		} else {
			int width = clamp(startWidth + (int) Math.round(mouseX - pressX), ChatConfig.MIN_WIDTH,
				Math.max(ChatConfig.MIN_WIDTH, screenWidth - startLeft));
			int height = clamp(startHeight + (int) Math.round(mouseY - pressY), ChatConfig.MIN_HEIGHT,
				Math.max(ChatConfig.MIN_HEIGHT, screenHeight - startTop));
			config.setWidth(width);
			config.setFocusedHeight(height);
			config.setUnfocusedHeight(height);
			config.setPosition(startLeft, screenHeight - (startTop + height));
			Minecraft.getInstance().gui.getChat().rescaleChat();
		}
	}

	public static boolean active() { return mode != Mode.NONE; }

	private static void start(Mode next, double x, double y, ChatLayout.Bounds bounds) {
		mode = next;
		pressX = x;
		pressY = y;
		startLeft = bounds.left();
		startTop = bounds.top();
		startWidth = bounds.width();
		startHeight = bounds.height();
	}

	private static void finish(Minecraft minecraft) {
		mode = Mode.NONE;
		ChatLayout.clampToScreen(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		BlockeraCoreServices.chat().save();
		minecraft.gui.getChat().rescaleChat();
	}

	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
