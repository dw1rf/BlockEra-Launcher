package space.blockera.core.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Session-only bridge between safe routing rules and Minecraft's native chat renderer/history. */
public final class BlockeraChatRuntime {
	private static final BlockeraChatRuntime INSTANCE = new BlockeraChatRuntime();
	private final ChatConfigStore store = ChatConfigStore.instance();
	private ChatRouter<ChatMessagePayload> router = new ChatRouter<>(ChatConfig.defaults());
	private boolean replaying;
	private boolean rebuilding;

	private BlockeraChatRuntime() {
	}

	public static BlockeraChatRuntime instance() { return INSTANCE; }
	public ChatConfig config() { return store.config(); }
	public boolean replaying() { return replaying; }
	public boolean rebuilding() { return rebuilding; }

	public synchronized void reload() {
		router = new ChatRouter<>(store.config());
	}

	public synchronized RouteDecision route(ChatMessagePayload payload) {
		Instant receivedAt = Instant.now();
		ChatRoutingResult<ChatMessagePayload> result = router.route(payload, payload.component().getString(), receivedAt);
		boolean visible = result.tabIds().contains(config().activeTab());
		return new RouteDecision(result, visible, decorate(result));
	}

	public synchronized void switchTab(String tabId) {
		config().tab(tabId);
		config().setActiveTab(tabId);
		store.save();
		rebuildNativeChat();
	}

	public synchronized void refreshConfiguration() {
		store.save();
		router.reconfigure();
		rebuildNativeChat();
	}

	public synchronized void clear() { router.clear(); }

	/** Returns a bounded tail for read-only detached tab panels without changing the active native chat. */
	public synchronized List<Component> history(String tabId, int limit) {
		List<ChatRoutingResult<ChatMessagePayload>> history = router.history(tabId);
		int from = Math.max(0, history.size() - Math.max(0, limit));
		List<Component> result = new ArrayList<>(history.size() - from);
		for (int index = from; index < history.size(); index++) result.add(decorate(history.get(index)));
		return List.copyOf(result);
	}

	public void withReplay(Runnable action) {
		boolean previous = replaying;
		replaying = true;
		try { action.run(); } finally { replaying = previous; }
	}

	public void notifyMention(RouteDecision decision) {
		if (!decision.result().sound() || Minecraft.getInstance().player == null) return;
		Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_PLING, 0.65F, 1.35F);
	}

	private void rebuildNativeChat() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui == null) return;
		ChatComponent chat = minecraft.gui.getChat();
		rebuilding = true;
		try {
			chat.clearMessages(false);
			for (ChatRoutingResult<ChatMessagePayload> result : router.history(config().activeTab())) {
				ChatMessagePayload payload = result.message();
				Component decorated = decorate(result);
				withReplay(() -> chat.addMessage(decorated, payload.signature(), payload.tag()));
			}
		} finally {
			rebuilding = false;
		}
	}

	private Component decorate(ChatRoutingResult<ChatMessagePayload> result) {
		MutableComponent decorated = Component.empty();
		if (!result.matchedFilters().isEmpty()) {
			int rgb = result.matchedFilters().get(0).color() & 0xFFFFFF;
			String marker = result.mention() ? "@ " : "┃ ";
			decorated.append(Component.literal(marker).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
		}
		String prefix = ChatTimestampFormatter.prefix(result.receivedAt(), ZoneId.systemDefault(), config());
		if (!prefix.isEmpty()) decorated.append(Component.literal(prefix).withStyle(ChatFormatting.DARK_GRAY));
		decorated.append(result.message().component().copy());
		return decorated;
	}

	public record RouteDecision(ChatRoutingResult<ChatMessagePayload> result, boolean visible, Component decorated) {
	}
}
