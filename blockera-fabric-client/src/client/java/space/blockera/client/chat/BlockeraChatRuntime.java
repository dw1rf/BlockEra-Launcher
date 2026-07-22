package space.blockera.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import space.blockera.client.BlockeraCoreServices;

import java.time.Instant;
import java.util.List;

/** Session-only bridge between local filter routing and Minecraft's native chat renderer. */
public final class BlockeraChatRuntime {
	private static final BlockeraChatRuntime INSTANCE = new BlockeraChatRuntime();
	private ChatRouter<ChatMessagePayload> router;
	private boolean replaying;
	private boolean rebuilding;

	private BlockeraChatRuntime() {
	}

	public static BlockeraChatRuntime instance() { return INSTANCE; }
	public boolean replaying() { return replaying; }
	public boolean rebuilding() { return rebuilding; }

	public synchronized void initialize() {
		if (router == null) router = new ChatRouter<>(config());
	}

	public ChatConfig config() { return BlockeraCoreServices.chat().config(); }

	public synchronized RouteDecision route(ChatMessagePayload payload) {
		initialize();
		ChatRoutingResult<ChatMessagePayload> result = router.route(
			payload, payload.component().getString(), Instant.now()
		);
		return new RouteDecision(result, result.tabIds().contains(config().activeTab()), decorate(result));
	}

	public synchronized void switchTab(String tabId) {
		config().tab(tabId);
		config().setActiveTab(tabId);
		BlockeraCoreServices.chat().save();
		rebuildNativeChat();
	}

	public synchronized void refreshConfiguration() {
		initialize();
		BlockeraCoreServices.chat().save();
		router.reconfigure();
		rebuildNativeChat();
	}

	public synchronized void clearIfAllowed() {
		initialize();
		router.clear();
	}

	public synchronized List<ChatRoutingResult<ChatMessagePayload>> history(String tabId) {
		initialize();
		return router.history(tabId);
	}

	public void withReplay(Runnable action) {
		boolean previous = replaying;
		replaying = true;
		try {
			action.run();
		} finally {
			replaying = previous;
		}
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
				withReplay(() -> chat.addMessage(decorate(result), payload.signature(), payload.tag()));
			}
		} finally {
			rebuilding = false;
		}
	}

	private static Component decorate(ChatRoutingResult<ChatMessagePayload> result) {
		MutableComponent decorated = Component.empty();
		if (!result.matchedFilters().isEmpty()) {
			int color = result.matchedFilters().getFirst().color();
			decorated.append(Component.literal("┃ ").withStyle(style -> style.withColor(color)));
		}
		return decorated.append(result.message().component().copy());
	}

	public record RouteDecision(ChatRoutingResult<ChatMessagePayload> result, boolean visible, Component decorated) {
	}
}
