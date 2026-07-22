package space.blockera.core.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Original rich Minecraft chat payload retained for native replay when switching tabs. */
public record ChatMessagePayload(Component component, MessageSignature signature, GuiMessageTag tag) {
}
