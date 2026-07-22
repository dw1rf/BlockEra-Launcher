package space.blockera.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import space.blockera.client.BlockeraCoreServices;

public final class ChatLayout {
	public static final int TAB_HEIGHT = 18;
	public static final int HEADER_HEIGHT = 20;
	public static final int INPUT_GAP = 18;

	private ChatLayout() {
	}

	public static Bounds bounds(int screenWidth, int screenHeight) {
		ChatConfig config = BlockeraCoreServices.chat().config();
		boolean focused = Minecraft.getInstance().screen instanceof ChatScreen;
		int height = focused ? config.focusedHeight() : config.unfocusedHeight();
		int width = Math.min(config.width(), Math.max(ChatConfig.MIN_WIDTH, screenWidth));
		int left = clamp(config.left(), 0, Math.max(0, screenWidth - width));
		int bottom = clamp(screenHeight - config.bottomMargin(), height, screenHeight);
		return new Bounds(left, bottom - height, left + width, bottom);
	}

	public static void clampToScreen(int screenWidth, int screenHeight) {
		ChatConfig config = BlockeraCoreServices.chat().config();
		Bounds bounds = bounds(screenWidth, screenHeight);
		config.setPosition(bounds.left(), screenHeight - bounds.bottom());
	}

	public static Bounds headerBounds(int screenWidth, int screenHeight) {
		Bounds chat = bounds(screenWidth, screenHeight);
		int top = Math.max(0, chat.top() - HEADER_HEIGHT);
		int bottom = Math.min(chat.bottom(), top + HEADER_HEIGHT);
		return new Bounds(chat.left(), top, chat.right(), bottom);
	}

	public static int toVanillaMouseX(int mouseX, int screenWidth, int screenHeight) {
		return mouseX - bounds(screenWidth, screenHeight).left();
	}

	public static int toVanillaMouseY(int mouseY, int screenWidth, int screenHeight) {
		Bounds bounds = bounds(screenWidth, screenHeight);
		return mouseY - (bounds.bottom() - (screenHeight - 40));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public record Bounds(int left, int top, int right, int bottom) {
		public int width() { return right - left; }
		public int height() { return bottom - top; }
	}
}
