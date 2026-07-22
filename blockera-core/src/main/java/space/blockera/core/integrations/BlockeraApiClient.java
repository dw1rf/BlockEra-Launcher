package space.blockera.core.integrations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import space.blockera.core.config.RuntimeConfig;

/** Transport shell for future website/server contracts. No credential is compiled into the mod. */
public final class BlockeraApiClient {
	private final RuntimeConfig config;
	private final HttpClient client;
	private final Supplier<Optional<String>> accessToken;

	public BlockeraApiClient(RuntimeConfig config, Supplier<Optional<String>> accessToken) {
		this.config = Objects.requireNonNull(config, "config");
		this.accessToken = Objects.requireNonNull(accessToken, "accessToken");
		this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	public CompletableFuture<HttpResponse<String>> get(String relativePath) {
		URI base = config.apiBaseUrl().orElseThrow(() -> new IllegalStateException("Blockera API is not configured"));
		HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(relativePath)).GET();
		accessToken.get().ifPresent(token -> request.header("Authorization", "Bearer " + token));
		return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	public CompletableFuture<WebSocket> connectWebSocket(WebSocket.Listener listener) {
		URI endpoint = config.websocketUrl().orElseThrow(() -> new IllegalStateException("Blockera WebSocket is not configured"));
		WebSocket.Builder socket = client.newWebSocketBuilder();
		accessToken.get().ifPresent(token -> socket.header("Authorization", "Bearer " + token));
		return socket.buildAsync(endpoint, listener);
	}
}
