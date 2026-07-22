package space.blockera.core.chat;

import net.minecraft.client.Minecraft;

/** Applies validated Blockera chat display values to Minecraft's native renderer. */
public final class ChatVanillaOptions {
	private ChatVanillaOptions() { }
	public static void apply() {
		Minecraft minecraft = Minecraft.getInstance();
		ChatConfig config = ChatConfigStore.instance().config();
		minecraft.options.chatWidth().set(unit((config.width() - 40.0D) / 280.0D));
		minecraft.options.chatHeightFocused().set(unit((config.openHeight() - 20.0D) / 160.0D));
		minecraft.options.chatHeightUnfocused().set(unit((config.closedHeight() - 20.0D) / 160.0D));
		minecraft.options.chatOpacity().set((double) config.textOpacity());
		minecraft.options.textBackgroundOpacity().set((double) config.backgroundOpacity());
		minecraft.options.chatLineSpacing().set(unit(config.lineSpacing()));
		minecraft.options.chatDelay().set(Math.max(0.0D, Math.min(6.0D, config.messageDelaySeconds())));
		minecraft.options.save();
		if (minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
	}
	private static double unit(double value) { return Math.max(0.0D, Math.min(1.0D, value)); }
}
