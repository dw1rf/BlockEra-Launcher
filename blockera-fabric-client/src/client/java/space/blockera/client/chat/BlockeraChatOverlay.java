package space.blockera.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.client.ui.BlockeraDraw;
import space.blockera.client.ui.ThemeTokens;
import space.blockera.client.ui.UiText;

/** LabyMod-style header, tabs and direct manipulation for the focused chat. */
public final class BlockeraChatOverlay {
	private static final int GAP = 2;
	private static final int SETTINGS_WIDTH = 22;
	private static final int DRAG_WIDTH = 22;

	private BlockeraChatOverlay() {
	}

	public static void render(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		ChatLayout.Bounds chat = ChatLayout.bounds(screenWidth, screenHeight);
		ChatLayout.Bounds header = ChatLayout.headerBounds(screenWidth, screenHeight);
		BlockeraDraw.roundedBorder(graphics, header.left(), header.top(), header.right(), header.bottom(),
			ThemeTokens.RADIUS, ThemeTokens.BORDER, ThemeTokens.PANEL);
		int y = header.top();
		int x = header.left();
		UiText.drawCentered(graphics, Component.literal("::"), x + DRAG_WIDTH / 2, y + 5, ThemeTokens.DIM);
		x += DRAG_WIDTH;

		ChatConfig config = BlockeraChatRuntime.instance().config();
		int settingsX = header.right() - SETTINGS_WIDTH;
		int slotWidth = slotWidth(config, x, settingsX);
		for (ChatTab tab : config.tabs()) {
			int tabWidth = Math.min(tabWidth(tab), slotWidth);
			if (x + tabWidth > settingsX) break;
			boolean hovered = inside(mouseX, mouseY, x, y, x + tabWidth, y + ChatLayout.TAB_HEIGHT);
			boolean active = tab.id().equals(config.activeTab());
			int fill = active ? ThemeTokens.SELECTION : hovered ? ThemeTokens.CARD_HOVER : 0x00000000;
			BlockeraDraw.roundedRect(graphics, x, y + 1, x + tabWidth, y + ChatLayout.TAB_HEIGHT,
				ThemeTokens.RADIUS, fill);
			graphics.fill(x, y + ChatLayout.TAB_HEIGHT - 1, x + tabWidth, y + ChatLayout.TAB_HEIGHT,
				active ? tab.color() : 0x00000000);
			UiText.drawCentered(graphics, title(tab), x + tabWidth / 2, y + 5,
				active ? ThemeTokens.TEXT : ThemeTokens.MUTED);
			x += tabWidth + GAP;
		}

		boolean settingsHovered = inside(mouseX, mouseY, settingsX, y, header.right(), header.bottom());
		BlockeraDraw.roundedRect(graphics, settingsX, y + 1, header.right(), header.bottom(),
			ThemeTokens.RADIUS, settingsHovered ? ThemeTokens.CARD_HOVER : 0x00000000);
		UiText.drawCentered(graphics, Component.literal("..."), settingsX + SETTINGS_WIDTH / 2, y + 5,
			ThemeTokens.MUTED);

		graphics.fill(chat.right() - 12, chat.bottom() - 2, chat.right(), chat.bottom(), ThemeTokens.ACCENT);
		graphics.fill(chat.right() - 2, chat.bottom() - 12, chat.right(), chat.bottom(), ThemeTokens.ACCENT);
	}

	public static boolean mouseClicked(Screen parent, double mouseX, double mouseY,
		int screenWidth, int screenHeight) {
		if (DetachedChatPanels.mouseClicked(mouseX, mouseY, screenWidth, screenHeight)) return true;
		ChatLayout.Bounds header = ChatLayout.headerBounds(screenWidth, screenHeight);
		int y = header.top();
		int x = header.left() + DRAG_WIDTH;
		ChatConfig config = BlockeraChatRuntime.instance().config();
		int settingsX = header.right() - SETTINGS_WIDTH;
		int slotWidth = slotWidth(config, x, settingsX);
		for (ChatTab tab : config.tabs()) {
			int tabWidth = Math.min(tabWidth(tab), slotWidth);
			if (x + tabWidth > settingsX) break;
			if (inside(mouseX, mouseY, x, y, x + tabWidth, y + ChatLayout.TAB_HEIGHT)) {
				BlockeraChatRuntime.instance().switchTab(tab.id());
				return true;
			}
			x += tabWidth + GAP;
		}
		if (inside(mouseX, mouseY, settingsX, y, header.right(), header.bottom())) {
			Minecraft.getInstance().setScreen(new BlockeraChatSettingsScreen(parent));
			return true;
		}
		return ChatInteractionController.begin(mouseX, mouseY, screenWidth, screenHeight);
	}

	public static boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		return DetachedChatPanels.mouseDragged(mouseX, mouseY, screenWidth, screenHeight)
			|| ChatInteractionController.drag(mouseX, mouseY, screenWidth, screenHeight);
	}

	public static boolean mouseReleased() {
		return DetachedChatPanels.mouseReleased()
			|| ChatInteractionController.release(Minecraft.getInstance());
	}

	private static int slotWidth(ChatConfig config, int left, int right) {
		int count = Math.max(1, config.tabs().size());
		return Math.max(34, (right - left - GAP * count) / count);
	}

	private static int tabWidth(ChatTab tab) {
		int text = Minecraft.getInstance().font.width(UiText.regular(title(tab)));
		return Math.max(44, Math.min(112, text + 16));
	}

	private static Component title(ChatTab tab) {
		return ChatTab.ALL_ID.equals(tab.id())
			? Component.translatable("blockera.chat.tab.all")
			: Component.literal(tab.name());
	}

	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}
}
