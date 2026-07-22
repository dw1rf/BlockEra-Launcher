package space.blockera.client.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public record ChatMessagePayload(Component component, MessageSignature signature, GuiMessageTag tag) {
}
