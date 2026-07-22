package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatConfig;
import space.blockera.core.chat.ChatTab;

/** Compact first-party tab strip rendered above the native focused chat. */
public final class BlockeraChatTabs {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final int HEIGHT = 18;
	private static final int GAP = 3;

	private BlockeraChatTabs() {
	}

	public static void render(PoseStack poseStack, int screenHeight, int mouseX, int mouseY) {
		ChatConfig config = BlockeraChatRuntime.instance().config();
		int x = 2;
		int y = top(screenHeight);
		for (ChatTab tab : config.tabs()) {
			if (tab.detached()) continue;
			int width = tabWidth(tab);
			boolean active = tab.id().equals(config.activeTab());
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
			int activeColor = 0xCC000000 | (tab.color() & 0x00FFFFFF);
			BlockeraDraw.roundedRect(poseStack, x, y, x + width, y + HEIGHT, 5,
					active ? activeColor : hovered ? THEME.cardHoverArgb() : 0xC012141D);
			UiFont.drawCentered(poseStack, title(tab), x + width / 2.0F, y + 5,
					active ? THEME.textPrimaryArgb() : THEME.textMutedArgb());
			x += width + GAP;
		}
	}

	public static boolean mouseClicked(int screenHeight, double mouseX, double mouseY) {
		ChatConfig config = BlockeraChatRuntime.instance().config();
		int x = 2;
		int y = top(screenHeight);
		for (ChatTab tab : config.tabs()) {
			if (tab.detached()) continue;
			int width = tabWidth(tab);
			if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT) {
				BlockeraChatRuntime.instance().switchTab(tab.id());
				return true;
			}
			x += width + GAP;
		}
		return false;
	}

	private static int top(int screenHeight) {
		Minecraft minecraft = Minecraft.getInstance();
		return Math.max(2, screenHeight - minecraft.gui.getChat().getHeight() - HEIGHT - 30);
	}

	private static int tabWidth(ChatTab tab) { return Math.max(42, Math.min(112, UiFont.width(title(tab)) + 18)); }
	private static Component title(ChatTab tab) {
		return ChatTab.ALL_ID.equals(tab.id()) ? Component.translatable("blockera.chat.tab.all") : Component.literal(tab.name());
	}
}
